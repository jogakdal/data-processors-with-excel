package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.detectImageTypeForPoi
import io.github.jogakdal.tbeg.engine.core.parseCellRef
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Picture
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 이미지 삽입기 - 셀 위치에 이미지 삽입
 */
class ImageInserter {

    companion object {
        private val IMAGE_TYPE_MAP = mapOf(
            "PNG" to Workbook.PICTURE_TYPE_PNG,
            "JPEG" to Workbook.PICTURE_TYPE_JPEG,
            "JPG" to Workbook.PICTURE_TYPE_JPEG,
            "DIB" to Workbook.PICTURE_TYPE_DIB,
            "EMF" to Workbook.PICTURE_TYPE_EMF,
            "WMF" to Workbook.PICTURE_TYPE_WMF
        )

        /** 1 픽셀 = 9525 EMU (96 DPI 기준) */
        private const val EMU_PER_PIXEL = 9525

        /** 셀 테두리가 보이도록 적용하는 기본 마진 */
        private const val IMAGE_MARGIN_PX = 1
        private const val IMAGE_MARGIN_EMU = IMAGE_MARGIN_PX * EMU_PER_PIXEL

        /** 1인치 = 72포인트 */
        private const val POINTS_PER_INCH = 72

        /** 1인치 = 96픽셀 (96 DPI 기준) */
        private const val PIXELS_PER_INCH = 96

        /** 포인트를 픽셀로 변환 */
        private fun pointsToPixels(points: Double) = points * PIXELS_PER_INCH / POINTS_PER_INCH
    }

    /**
     * 위치/범위 문자열 기반 이미지 삽입
     *
     * @param position 위치 문자열 - null(마커 셀), 단일 셀(B5), 또는 범위(B5:D10)
     * @param markerRowIndex 마커 셀 행 (position이 null일 때 사용)
     * @param markerColIndex 마커 셀 열 (position이 null일 때 사용)
     * @param sizeSpec 크기 명세 - 범위 지정 시 무시되고 범위 크기에 맞춤
     * @param markerMergedRegion 마커 셀의 병합 영역
     */
    fun insertImageWithPosition(
        workbook: Workbook,
        sheet: Sheet,
        imageBytes: ByteArray,
        position: String?,
        markerRowIndex: Int,
        markerColIndex: Int,
        sizeSpec: ImageSizeSpec,
        markerMergedRegion: CellRangeAddress? = null
    ) {
        when {
            // position이 null - 마커 셀 위치에 삽입
            position == null -> {
                insertImage(workbook, sheet, imageBytes, markerRowIndex, markerColIndex, sizeSpec, markerMergedRegion)
            }
            // position이 범위 - 범위 전체에 맞춤
            position.contains(":") -> {
                val (startRef, endRef) = position.split(":")
                val (startRow, startCol) = parseCellRef(startRef)
                val (endRow, endCol) = parseCellRef(endRef)
                val rangeAddress = CellRangeAddress(startRow, endRow, startCol, endCol)
                // 범위 지정 시 sizeSpec 무시하고 범위 크기에 맞춤
                insertImage(workbook, sheet, imageBytes, startRow, startCol, ImageSizeSpec.FIT_TO_CELL, rangeAddress)
            }
            // position이 단일 셀
            else -> {
                val (targetRow, targetCol) = parseCellRef(position)
                // 지정 위치의 병합 영역 확인
                val targetMergedRegion = findMergedRegion(sheet, targetRow, targetCol)
                insertImage(workbook, sheet, imageBytes, targetRow, targetCol, sizeSpec, targetMergedRegion)
            }
        }
    }

    /** 병합 영역 찾기 */
    private fun findMergedRegion(sheet: Sheet, rowIndex: Int, colIndex: Int) =
        (0 until sheet.numMergedRegions)
            .map { sheet.getMergedRegion(it) }
            .firstOrNull { it.isInRange(rowIndex, colIndex) }

    /**
     * 이미지 삽입
     *
     * @param sizeSpec 크기 명세 (기본값: 셀 크기에 맞춤)
     * @param mergedRegion 병합 영역 (크기 계산에 사용)
     */
    fun insertImage(
        workbook: Workbook,
        sheet: Sheet,
        imageBytes: ByteArray,
        rowIndex: Int,
        colIndex: Int,
        sizeSpec: ImageSizeSpec,
        mergedRegion: CellRangeAddress? = null
    ) {
        val imageTypeStr = imageBytes.detectImageTypeForPoi()
        val pictureType = IMAGE_TYPE_MAP[imageTypeStr]
            ?: throw IllegalArgumentException("지원하지 않는 이미지 형식: $imageTypeStr")

        val pictureIdx = workbook.addPicture(imageBytes, pictureType)
        val drawing = sheet.createDrawingPatriarch()

        when {
            sizeSpec == ImageSizeSpec.FIT_TO_CELL -> {
                val anchor = createAnchorWithMargin(rowIndex, colIndex, mergedRegion)
                drawing.createPicture(anchor, pictureIdx)
            }
            sizeSpec == ImageSizeSpec.ORIGINAL -> {
                val anchor = createAnchorForOriginalSize(sheet, imageBytes, rowIndex, colIndex)
                drawing.createPicture(anchor, pictureIdx)
            }
            sizeSpec.width == 0 && sizeSpec.height < 0 -> {  // 0:-1
                val anchor = createAnchorWithAspectRatio(
                    sheet, imageBytes, rowIndex, colIndex, mergedRegion, fitToWidth = true
                )
                drawing.createPicture(anchor, pictureIdx)
            }
            sizeSpec.width < 0 && sizeSpec.height == 0 -> {  // -1:0
                val anchor = createAnchorWithAspectRatio(
                    sheet, imageBytes, rowIndex, colIndex, mergedRegion, fitToWidth = false
                )
                drawing.createPicture(anchor, pictureIdx)
            }
            else -> {
                val anchor = createAnchor(workbook, rowIndex, colIndex, mergedRegion)
                val picture = drawing.createPicture(anchor, pictureIdx)
                picture.resize()
                val scale = calculateScale(picture, sheet, rowIndex, colIndex, sizeSpec, mergedRegion)
                picture.resize(scale.first, scale.second)
            }
        }
    }

    private fun calculateScale(
        picture: Picture,
        sheet: Sheet,
        rowIndex: Int,
        colIndex: Int,
        sizeSpec: ImageSizeSpec,
        mergedRegion: CellRangeAddress?
    ): Pair<Double, Double> {
        val imageDim = picture.imageDimension
        val originalWidth = imageDim.width.toDouble()
        val originalHeight = imageDim.height.toDouble()

        val cellWidthPx = getCellWidthInPixels(sheet, colIndex, mergedRegion)
        val cellHeightPx = getCellHeightInPixels(sheet, rowIndex, mergedRegion)

        val targetWidth = when {
            sizeSpec.width > 0 -> sizeSpec.width.toDouble()
            sizeSpec.width == 0 -> cellWidthPx
            else -> -1.0
        }

        val targetHeight = when {
            sizeSpec.height > 0 -> sizeSpec.height.toDouble()
            sizeSpec.height == 0 -> cellHeightPx
            else -> -1.0
        }

        val (finalWidth, finalHeight) = when {
            targetWidth < 0 && targetHeight < 0 -> originalWidth to originalHeight
            targetWidth < 0 -> {
                val ratio = targetHeight / originalHeight
                (originalWidth * ratio) to targetHeight
            }
            targetHeight < 0 -> {
                val ratio = targetWidth / originalWidth
                targetWidth to (originalHeight * ratio)
            }
            else -> targetWidth to targetHeight
        }

        return (finalWidth / originalWidth) to (finalHeight / originalHeight)
    }

    private fun getCellWidthInPixels(sheet: Sheet, colIndex: Int, mergedRegion: CellRangeAddress?) =
        ((mergedRegion?.firstColumn ?: colIndex)..(mergedRegion?.lastColumn ?: colIndex))
            .sumOf { sheet.getColumnWidthInPixels(it).toDouble() }

    private fun getCellHeightInPixels(sheet: Sheet, rowIndex: Int, mergedRegion: CellRangeAddress?) =
        ((mergedRegion?.firstRow ?: rowIndex)..(mergedRegion?.lastRow ?: rowIndex))
            .sumOf { getRowHeightInPixels(sheet, it) }

    private fun createAnchor(workbook: Workbook, rowIndex: Int, colIndex: Int, mergedRegion: CellRangeAddress?) =
        // POI 버그: col1/col2 getter(short)/setter(int) 타입 불일치
        // 제보함: https://bz.apache.org/bugzilla/show_bug.cgi?id=69935
        workbook.creationHelper.createClientAnchor().apply {
            setCol1(mergedRegion?.firstColumn ?: colIndex)
            row1 = mergedRegion?.firstRow ?: rowIndex
            setCol2((mergedRegion?.lastColumn ?: colIndex) + 1)
            row2 = (mergedRegion?.lastRow ?: rowIndex) + 1
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }

    /** 원본 크기 이미지용 앵커 생성 (EMU 단위 정밀 계산) */
    private fun createAnchorForOriginalSize(
        sheet: Sheet,
        imageBytes: ByteArray,
        rowIndex: Int,
        colIndex: Int
    ): XSSFClientAnchor {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw IllegalArgumentException("이미지를 읽을 수 없습니다.")
        val imageWidthPx = image.width.toDouble()
        val imageHeightPx = image.height.toDouble()

        val (endCol, dx2) = calculateEndColWithEmu(
            sheet, colIndex, imageWidthPx + IMAGE_MARGIN_PX
        )
        val (endRow, dy2) = calculateEndRowWithEmu(
            sheet, rowIndex, imageHeightPx + IMAGE_MARGIN_PX
        )

        return XSSFClientAnchor(
            IMAGE_MARGIN_EMU, IMAGE_MARGIN_EMU,  // 시작 마진
            dx2, dy2,  // 끝점
            colIndex, rowIndex,
            endCol, endRow
        ).apply {
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }
    }

    /** 마진이 적용된 앵커 생성 (dx2/dy2 음수 = 끝점에서 안쪽으로) */
    private fun createAnchorWithMargin(rowIndex: Int, colIndex: Int, mergedRegion: CellRangeAddress?) =
        XSSFClientAnchor(
            IMAGE_MARGIN_EMU, IMAGE_MARGIN_EMU,
            -IMAGE_MARGIN_EMU, -IMAGE_MARGIN_EMU,
            mergedRegion?.firstColumn ?: colIndex,
            mergedRegion?.firstRow ?: rowIndex,
            (mergedRegion?.lastColumn ?: colIndex) + 1,
            (mergedRegion?.lastRow ?: rowIndex) + 1
        ).apply {
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }

    /**
     * 비율 유지 앵커 생성 (EMU 단위 정밀 계산)
     * @param fitToWidth true면 셀 너비 기준, false면 셀 높이 기준
     */
    private fun createAnchorWithAspectRatio(
        sheet: Sheet,
        imageBytes: ByteArray,
        rowIndex: Int,
        colIndex: Int,
        mergedRegion: CellRangeAddress?,
        fitToWidth: Boolean
    ): ClientAnchor {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw IllegalArgumentException("이미지를 읽을 수 없습니다.")
        val imageWidth = image.width.toDouble()
        val imageHeight = image.height.toDouble()
        val aspectRatio = imageHeight / imageWidth

        val startCol = mergedRegion?.firstColumn ?: colIndex
        val endCol = mergedRegion?.lastColumn ?: colIndex
        val startRow = mergedRegion?.firstRow ?: rowIndex
        val endRow = mergedRegion?.lastRow ?: rowIndex
        val marginPx = IMAGE_MARGIN_PX * 2

        return if (fitToWidth) {
            val cellWidthPx = getCellWidthInPixels(sheet, colIndex, mergedRegion) - marginPx
            val targetHeightPx = cellWidthPx * aspectRatio
            val (targetEndRow, remainingHeightEmu) = calculateEndRowWithEmu(
                sheet, startRow, targetHeightPx + IMAGE_MARGIN_PX
            )

            XSSFClientAnchor(
                IMAGE_MARGIN_EMU, IMAGE_MARGIN_EMU,
                -IMAGE_MARGIN_EMU, remainingHeightEmu,
                startCol, startRow,
                endCol + 1, targetEndRow
            ).apply {
                anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
            }
        } else {
            val cellHeightPx = getCellHeightInPixels(sheet, rowIndex, mergedRegion) - marginPx
            val targetWidthPx = cellHeightPx / aspectRatio
            val (targetEndCol, remainingWidthEmu) = calculateEndColWithEmu(
                sheet, startCol, targetWidthPx + IMAGE_MARGIN_PX
            )

            XSSFClientAnchor(
                IMAGE_MARGIN_EMU, IMAGE_MARGIN_EMU,
                remainingWidthEmu, -IMAGE_MARGIN_EMU,
                startCol, startRow,
                targetEndCol, endRow + 1
            ).apply {
                anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
            }
        }
    }

    /** @return Pair(끝 행 인덱스, 끝 행 내 EMU 오프셋) */
    private fun calculateEndRowWithEmu(
        sheet: Sheet,
        startRow: Int,
        targetHeightPx: Double
    ): Pair<Int, Int> {
        var accumulatedHeight = -IMAGE_MARGIN_PX.toDouble()
        var currentRow = startRow

        while (accumulatedHeight < targetHeightPx && currentRow - startRow < 100) {
            val rowHeightPx = getRowHeightInPixels(sheet, currentRow)
            if (accumulatedHeight + rowHeightPx > targetHeightPx) {
                val remainingPx = targetHeightPx - accumulatedHeight
                return currentRow to (remainingPx * EMU_PER_PIXEL).toInt()
            }
            accumulatedHeight += rowHeightPx
            currentRow++
        }

        return currentRow to 0
    }

    /** @return Pair(끝 열 인덱스, 끝 열 내 EMU 오프셋) */
    private fun calculateEndColWithEmu(
        sheet: Sheet,
        startCol: Int,
        targetWidthPx: Double
    ): Pair<Int, Int> {
        var accumulatedWidth = -IMAGE_MARGIN_PX.toDouble()
        var currentCol = startCol

        while (accumulatedWidth < targetWidthPx && currentCol - startCol < 100) {
            val colWidthPx = sheet.getColumnWidthInPixels(currentCol).toDouble()
            if (accumulatedWidth + colWidthPx > targetWidthPx) {
                val remainingPx = targetWidthPx - accumulatedWidth
                return currentCol to (remainingPx * EMU_PER_PIXEL).toInt()
            }
            accumulatedWidth += colWidthPx
            currentCol++
        }

        return currentCol to 0
    }

    private fun getRowHeightInPixels(sheet: Sheet, rowIndex: Int) =
        pointsToPixels(
            sheet.getRow(rowIndex)?.takeIf { it.height.toInt() != -1 }?.heightInPoints?.toDouble()
                ?: sheet.defaultRowHeightInPoints.toDouble()
        )
}
