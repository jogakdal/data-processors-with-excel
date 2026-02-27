package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.detectImageTypeForPoi
import io.github.jogakdal.tbeg.engine.core.parseCellRef
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * 이미지 삽입기 - 셀 위치에 이미지 삽입
 *
 * 셀의 alignment 설정에 따라 이미지 배치 위치를 자동 조정한다.
 * - FIT_TO_CELL: alignment 무시 (셀 전체 채움)
 * - ORIGINAL / 지정 크기: 양축 alignment 적용
 * - 비율 유지 (fitToWidth): Y축 alignment만 적용
 * - 비율 유지 (fitToHeight): X축 alignment만 적용
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
        markerMergedRegion: CellRangeAddress? = null,
        hAlign: HorizontalAlignment = HorizontalAlignment.GENERAL,
        vAlign: VerticalAlignment = VerticalAlignment.TOP
    ) {
        when {
            // position이 null - 마커 셀 위치에 삽입 (전달된 alignment 사용)
            position == null -> {
                insertImage(workbook, sheet, imageBytes, markerRowIndex, markerColIndex, sizeSpec, markerMergedRegion, hAlign, vAlign)
            }
            // position이 범위 - 범위 전체에 맞춤 (FIT_TO_CELL 강제, alignment 불필요)
            position.contains(":") -> {
                val (startRef, endRef) = position.split(":")
                val (startRow, startCol) = parseCellRef(startRef)
                val (endRow, endCol) = parseCellRef(endRef)
                val rangeAddress = CellRangeAddress(startRow, endRow, startCol, endCol)
                insertImage(workbook, sheet, imageBytes, startRow, startCol, ImageSizeSpec.FIT_TO_CELL, rangeAddress)
            }
            // position이 단일 셀 - target 셀에서 alignment 읽기
            else -> {
                val (targetRow, targetCol) = parseCellRef(position)
                val targetMergedRegion = findMergedRegion(sheet, targetRow, targetCol)
                val (targetHAlign, targetVAlign) = readAlignment(sheet, targetRow, targetCol)
                insertImage(workbook, sheet, imageBytes, targetRow, targetCol, sizeSpec, targetMergedRegion, targetHAlign, targetVAlign)
            }
        }
    }

    /** 셀의 수평/수직 정렬 설정을 읽는다 */
    private fun readAlignment(sheet: Sheet, rowIndex: Int, colIndex: Int): Pair<HorizontalAlignment, VerticalAlignment> {
        val style = sheet.getRow(rowIndex)?.getCell(colIndex)?.cellStyle
        return (style?.alignment ?: HorizontalAlignment.GENERAL) to
                (style?.verticalAlignment ?: VerticalAlignment.TOP)
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
     * @param hAlign 수평 정렬 (FIT_TO_CELL에서는 무시됨)
     * @param vAlign 수직 정렬 (FIT_TO_CELL에서는 무시됨)
     */
    fun insertImage(
        workbook: Workbook,
        sheet: Sheet,
        imageBytes: ByteArray,
        rowIndex: Int,
        colIndex: Int,
        sizeSpec: ImageSizeSpec,
        mergedRegion: CellRangeAddress? = null,
        hAlign: HorizontalAlignment = HorizontalAlignment.GENERAL,
        vAlign: VerticalAlignment = VerticalAlignment.TOP
    ) {
        val imageTypeStr = imageBytes.detectImageTypeForPoi()
        val pictureType = IMAGE_TYPE_MAP[imageTypeStr]
            ?: throw IllegalArgumentException("지원하지 않는 이미지 형식: $imageTypeStr")

        val pictureIdx = workbook.addPicture(imageBytes, pictureType)
        val drawing = sheet.createDrawingPatriarch()

        if (sizeSpec == ImageSizeSpec.FIT_TO_CELL) {
            val anchor = createAnchorWithMargin(sheet, rowIndex, colIndex, mergedRegion)
            drawing.createPicture(anchor, pictureIdx)
        } else {
            val (finalWidth, finalHeight) = calculateTargetSize(imageBytes, sizeSpec, sheet, colIndex, rowIndex, mergedRegion)
            insertWithOneCellAnchor(
                workbook, sheet, pictureIdx,
                rowIndex, colIndex, mergedRegion,
                finalWidth, finalHeight, hAlign, vAlign
            )
        }
    }

    /**
     * 이미지의 최종 픽셀 크기를 계산한다.
     * FIT_TO_CELL 이외의 모든 sizeSpec에 대해 동작한다.
     */
    private fun calculateTargetSize(
        imageBytes: ByteArray,
        sizeSpec: ImageSizeSpec,
        sheet: Sheet,
        colIndex: Int,
        rowIndex: Int,
        mergedRegion: CellRangeAddress?
    ): Pair<Double, Double> {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))
            ?: throw IllegalArgumentException("이미지를 읽을 수 없습니다.")
        val originalWidth = image.width.toDouble()
        val originalHeight = image.height.toDouble()
        val marginPx = IMAGE_MARGIN_PX * 2.0

        return when {
            sizeSpec == ImageSizeSpec.ORIGINAL -> originalWidth to originalHeight

            // fitToWidth (0:-1): 셀 너비에 맞추고 높이는 비율 유지
            sizeSpec.width == 0 && sizeSpec.height < 0 -> {
                val cellWidthPx = getCellWidthInPixels(sheet, colIndex, mergedRegion) - marginPx
                val targetHeight = cellWidthPx * (originalHeight / originalWidth)
                cellWidthPx to targetHeight
            }

            // fitToHeight (-1:0): 셀 높이에 맞추고 너비는 비율 유지
            sizeSpec.width < 0 && sizeSpec.height == 0 -> {
                val cellHeightPx = getCellHeightInPixels(sheet, rowIndex, mergedRegion) - marginPx
                val targetWidth = cellHeightPx * (originalWidth / originalHeight)
                targetWidth to cellHeightPx
            }

            // 지정 크기 (양수 픽셀, 혼합)
            else -> {
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

                when {
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
            }
        }
    }

    /**
     * oneCellAnchor로 이미지를 삽입한다.
     *
     * twoCellAnchor(from~to 두 셀 좌표 기반)는 셀 내 위치에 따라 Excel 렌더링이 달라지는 문제가 있다.
     * oneCellAnchor는 시작 위치(from) + 절대 크기(extent)로 이미지를 배치하므로
     * alignment에 관계없이 일관된 크기로 렌더링된다.
     */
    private fun insertWithOneCellAnchor(
        workbook: Workbook,
        sheet: Sheet,
        pictureIdx: Int,
        rowIndex: Int,
        colIndex: Int,
        mergedRegion: CellRangeAddress?,
        imageWidthPx: Double,
        imageHeightPx: Double,
        hAlign: HorizontalAlignment,
        vAlign: VerticalAlignment
    ) {
        val startCol = mergedRegion?.firstColumn ?: colIndex
        val startRow = mergedRegion?.firstRow ?: rowIndex
        val endCol = mergedRegion?.lastColumn ?: colIndex
        val endRow = mergedRegion?.lastRow ?: rowIndex
        val margin = IMAGE_MARGIN_PX.toDouble()
        val imageWidthEmu = (imageWidthPx * EMU_PER_PIXEL).toLong()
        val imageHeightEmu = (imageHeightPx * EMU_PER_PIXEL).toLong()
        val marginEmu = IMAGE_MARGIN_EMU.toLong()

        // 수평 위치: RIGHT는 endCol+1 기준 음수 오프셋으로 정확한 위치 계산
        // (ST_Coordinate 타입은 음수를 허용하며, POI 열 너비 근사에 의존하지 않아 정확도가 높다)
        val (fromCol, fromColOff) = when (hAlign) {
            HorizontalAlignment.RIGHT ->
                (endCol + 1) to -(imageWidthEmu + marginEmu)
            HorizontalAlignment.CENTER, HorizontalAlignment.CENTER_SELECTION -> {
                val cellWidthPx = getCellWidthInPixels(sheet, colIndex, mergedRegion)
                val offset = (cellWidthPx - imageWidthPx) / 2
                resolveColPosition(sheet, startCol, offset).let { (col, emu) -> col to emu.toLong() }
            }
            else -> resolveColPosition(sheet, startCol, margin).let { (col, emu) -> col to emu.toLong() }
        }

        // 수직 위치: BOTTOM은 endRow+1 기준 음수 오프셋으로 정확한 위치 계산
        val (fromRow, fromRowOff) = when (vAlign) {
            VerticalAlignment.BOTTOM ->
                (endRow + 1) to -(imageHeightEmu + marginEmu)
            VerticalAlignment.CENTER -> {
                val cellHeightPx = getCellHeightInPixels(sheet, rowIndex, mergedRegion)
                val offset = (cellHeightPx - imageHeightPx) / 2
                resolveRowPosition(sheet, startRow, offset).let { (row, emu) -> row to emu.toLong() }
            }
            else -> resolveRowPosition(sheet, startRow, margin).let { (row, emu) -> row to emu.toLong() }
        }

        // XSSFDrawing 획득 (SXSSF 호환)
        val xssfDrawing = resolveXssfDrawing(workbook, sheet)

        // 표준 API로 임시 이미지 생성 (drawing↔image PackageRelationship 설정 목적)
        val tempPicture = xssfDrawing.createPicture(
            XSSFClientAnchor(0, 0, 0, 0, 0, 0, 0, 0), pictureIdx
        )
        val ctPictureCopy = tempPicture.getCTPicture().copy()

        // 임시 twoCellAnchor 제거
        val ctDrawing = xssfDrawing.getCTDrawing()
        ctDrawing.removeTwoCellAnchor(ctDrawing.sizeOfTwoCellAnchorArray() - 1)

        // oneCellAnchor 생성: from(위치) + ext(절대 크기)
        ctDrawing.addNewOneCellAnchor().apply {
            addNewFrom().apply {
                col = fromCol
                colOff = fromColOff
                row = fromRow
                rowOff = fromRowOff
            }
            addNewExt().apply {
                cx = imageWidthEmu
                cy = imageHeightEmu
            }
            addNewPic().set(ctPictureCopy)
            pic.spPr.xfrm.apply {
                off.x = 0
                off.y = 0
                ext.cx = imageWidthEmu
                ext.cy = imageHeightEmu
            }
            addNewClientData()
        }
    }

    /** SXSSF/XSSF 공통으로 XSSFDrawing을 획득한다 */
    private fun resolveXssfDrawing(workbook: Workbook, sheet: Sheet): XSSFDrawing =
        when (val drawing = sheet.createDrawingPatriarch()) {
            is XSSFDrawing -> drawing
            else -> {
                val xssfWorkbook = (workbook as? SXSSFWorkbook)?.xssfWorkbook
                    ?: throw IllegalStateException("XSSFDrawing을 획득할 수 없습니다: ${workbook::class.simpleName}")
                xssfWorkbook.getSheet(sheet.sheetName).createDrawingPatriarch() as XSSFDrawing
            }
        }

    /**
     * 기준 열에서 픽셀 오프셋 위치를 (열 인덱스, EMU 오프셋)으로 변환한다.
     * 양수면 오른쪽, 음수면 왼쪽으로 진행한다.
     * 시트 경계(0열)에 도달하면 정지한다.
     */
    private fun resolveColPosition(sheet: Sheet, refCol: Int, offsetPx: Double): Pair<Int, Int> {
        if (offsetPx >= 0) {
            var remaining = offsetPx
            var col = refCol
            while (remaining > 0 && col - refCol < 200) {
                val colWidth = sheet.getColumnWidthInPixels(col).toDouble()
                if (remaining <= colWidth) {
                    val emu = (remaining * EMU_PER_PIXEL).toInt()
                    val maxEmu = (colWidth * EMU_PER_PIXEL).toInt() - 1
                    return col to minOf(emu, maxEmu)
                }
                remaining -= colWidth
                col++
            }
            return col to 0
        } else {
            var remaining = -offsetPx
            var col = refCol
            while (col > 0 && remaining > 0) {
                col--
                val colWidth = sheet.getColumnWidthInPixels(col).toDouble()
                if (remaining <= colWidth) return col to ((colWidth - remaining) * EMU_PER_PIXEL).toInt()
                remaining -= colWidth
            }
            return 0 to 0
        }
    }

    /**
     * 기준 행에서 픽셀 오프셋 위치를 (행 인덱스, EMU 오프셋)으로 변환한다.
     * 양수면 아래쪽, 음수면 위쪽으로 진행한다.
     * 시트 경계(0행)에 도달하면 정지한다.
     */
    private fun resolveRowPosition(sheet: Sheet, refRow: Int, offsetPx: Double): Pair<Int, Int> {
        if (offsetPx >= 0) {
            var remaining = offsetPx
            var row = refRow
            while (remaining > 0 && row - refRow < 200) {
                val rowHeight = getRowHeightInPixels(sheet, row)
                if (remaining <= rowHeight) {
                    val emu = (remaining * EMU_PER_PIXEL).toInt()
                    val maxEmu = (rowHeight * EMU_PER_PIXEL).toInt() - 1
                    return row to minOf(emu, maxEmu)
                }
                remaining -= rowHeight
                row++
            }
            return row to 0
        } else {
            var remaining = -offsetPx
            var row = refRow
            while (row > 0 && remaining > 0) {
                row--
                val rowHeight = getRowHeightInPixels(sheet, row)
                if (remaining <= rowHeight) return row to ((rowHeight - remaining) * EMU_PER_PIXEL).toInt()
                remaining -= rowHeight
            }
            return 0 to 0
        }
    }

    /** 마진이 적용된 앵커 생성 (FIT_TO_CELL 전용) */
    private fun createAnchorWithMargin(sheet: Sheet, rowIndex: Int, colIndex: Int, mergedRegion: CellRangeAddress?): XSSFClientAnchor {
        val endCol = mergedRegion?.lastColumn ?: colIndex
        val endRow = mergedRegion?.lastRow ?: rowIndex
        val endColWidthEmu = (sheet.getColumnWidthInPixels(endCol) * EMU_PER_PIXEL).toInt()
        val endRowHeightEmu = (getRowHeightInPixels(sheet, endRow) * EMU_PER_PIXEL).toInt()

        return XSSFClientAnchor(
            IMAGE_MARGIN_EMU, IMAGE_MARGIN_EMU,
            maxOf(endColWidthEmu - IMAGE_MARGIN_EMU, 0), maxOf(endRowHeightEmu - IMAGE_MARGIN_EMU, 0),
            mergedRegion?.firstColumn ?: colIndex,
            mergedRegion?.firstRow ?: rowIndex,
            endCol,
            endRow
        ).apply {
            anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        }
    }

    private fun getCellWidthInPixels(sheet: Sheet, colIndex: Int, mergedRegion: CellRangeAddress?) =
        ((mergedRegion?.firstColumn ?: colIndex)..(mergedRegion?.lastColumn ?: colIndex))
            .sumOf { sheet.getColumnWidthInPixels(it).toDouble() }

    private fun getCellHeightInPixels(sheet: Sheet, rowIndex: Int, mergedRegion: CellRangeAddress?) =
        ((mergedRegion?.firstRow ?: rowIndex)..(mergedRegion?.lastRow ?: rowIndex))
            .sumOf { getRowHeightInPixels(sheet, it) }

    private fun getRowHeightInPixels(sheet: Sheet, rowIndex: Int) =
        pointsToPixels(
            sheet.getRow(rowIndex)?.takeIf { it.height.toInt() != -1 }?.heightInPoints?.toDouble()
                ?: sheet.defaultRowHeightInPoints.toDouble()
        )
}
