package io.github.jogakdal.excelart

import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

/**
 * 이미지 파일을 Excel 셀 배경색으로 변환하여 "도트 그림"을 생성한다.
 * SXSSF 스트리밍 모드를 사용하여 메모리 효율적으로 처리한다.
 */
object ImageToExcel {

    private const val MAX_COLUMNS = 16_384
    private const val MAX_ROWS = 1_048_576

    // Excel 열 너비 단위: 1/256 문자폭. 기본 폰트 기준 1 문자 ≈ 7px
    // 1px = 256/7 ≈ 36.6 단위
    // 1pt = 1.333px = 1.333 * 256/7 ≈ 48.8 단위
    private const val COLUMN_WIDTH_UNITS_PER_POINT = 48.8

    // SXSSF 스트리밍 윈도우 (메모리에 유지할 행 수)
    private const val STREAMING_WINDOW = 100

    /**
     * 이미지를 Excel 파일로 변환한다.
     * @param imageFile 입력 이미지 파일 (PNG, JPG 등)
     * @param outputFile 출력 .xlsx 파일
     * @param maxColors 색상 양자화 상한 (기본 256, 최대 64000)
     * @param pixelSize 셀 크기 (포인트, 기본 1pt)
     * @param startCell 시작 셀 주소 (기본 "A1", 예: "B2", "C5")
     */
    fun convert(
        imageFile: File,
        outputFile: File,
        maxColors: Int = 256,
        pixelSize: Float = 1f,
        startCell: String = "A1"
    ) {
        val (rowOffset, colOffset) = parseCellAddress(startCell)
        require(imageFile.exists()) { "이미지 파일을 찾을 수 없습니다: ${imageFile.absolutePath}" }
        require(maxColors in 1..64000) { "maxColors는 1~64000 사이여야 합니다: $maxColors" }

        val originalImage = ImageIO.read(imageFile)
            ?: throw IllegalArgumentException("이미지를 읽을 수 없습니다: ${imageFile.name}")

        // 해상도 제한 적용 (오프셋 고려)
        val image = resizeIfNeeded(originalImage, rowOffset, colOffset)
        val width = image.width
        val height = image.height

        progress("이미지 로드 완료: ${width}x${height}")

        // 1. 색상 양자화
        progress("색상 양자화 중... (최대 ${maxColors}색)")
        val quantizedPixels = ColorQuantizer.quantize(image, maxColors)
        val uniqueColors = quantizedPixels.flatMap { it.toList() }.toSet().size
        progress("색상 양자화 완료: ${uniqueColors}색")

        // 2. 병합 영역 계산
        progress("셀 병합 최적화 중...")
        val mergeRegions = CellMergeOptimizer.findMergeRegions(quantizedPixels)
        val mergedCellCount = mergeRegions.sumOf { it.cellCount }
        progress("셀 병합 완료: ${mergeRegions.size}개 영역 (${mergedCellCount}/${width * height} 셀)")

        // 3. 병합 영역에 포함된 셀 추적 (좌측 상단 제외)
        val mergedCells = buildMergedCellSet(mergeRegions)

        // 4. Excel 생성 (SXSSF 스트리밍 모드)
        progress("Excel 생성 중... (0%)")
        SXSSFWorkbook(STREAMING_WINDOW).use { workbook ->
            val sheet = workbook.createSheet("Image")

            // 이미지 영역 열만 축소 (여백 열은 기본 너비 유지)
            val columnWidth = (pixelSize * COLUMN_WIDTH_UNITS_PER_POINT).toInt().coerceAtLeast(1)
            for (col in colOffset until colOffset + width) {
                sheet.setColumnWidth(col, columnWidth)
            }

            // 스타일 캐시
            val styleCache = mutableMapOf<Int, XSSFCellStyle>()
            val xssfWorkbook = workbook.xssfWorkbook

            fun getOrCreateStyle(rgb: Int): XSSFCellStyle =
                styleCache.getOrPut(rgb) {
                    (xssfWorkbook.createCellStyle() as XSSFCellStyle).apply {
                        setFillForegroundColor(
                            XSSFColor(
                                byteArrayOf(
                                    ((rgb shr 16) and 0xFF).toByte(),
                                    ((rgb shr 8) and 0xFF).toByte(),
                                    (rgb and 0xFF).toByte()
                                )
                            )
                        )
                        fillPattern = FillPatternType.SOLID_FOREGROUND
                    }
                }

            // 여백 행 생성 (기본 높이 유지)
            for (r in 0 until rowOffset) {
                sheet.createRow(r)
            }

            // 이미지 행 생성 및 셀 배경색 적용 (순차 기록)
            var lastPercent = -1
            for (row in 0 until height) {
                val excelRow = sheet.createRow(row + rowOffset)
                excelRow.heightInPoints = pixelSize

                for (col in 0 until width) {
                    if (cellKey(row, col) in mergedCells) continue

                    val cell = excelRow.createCell(col + colOffset)
                    cell.cellStyle = getOrCreateStyle(quantizedPixels[row][col])
                }

                val percent = (row + 1) * 100 / height
                if (percent != lastPercent && percent % 5 == 0) {
                    progress("Excel 생성 중... (${percent}%)")
                    lastPercent = percent
                }
            }

            // 병합 영역 적용 (오프셋 반영)
            val totalRegions = mergeRegions.size
            var lastMergePercent = -1
            mergeRegions.forEachIndexed { i, region ->
                sheet.addMergedRegion(
                    CellRangeAddress(
                        region.startRow + rowOffset,
                        region.endRow + rowOffset,
                        region.startCol + colOffset,
                        region.endCol + colOffset
                    )
                )
                val percent = (i + 1) * 100 / totalRegions
                if (percent != lastMergePercent && percent % 5 == 0) {
                    progress("병합 영역 적용 중... (${percent}%)")
                    lastMergePercent = percent
                }
            }

            // 파일 저장
            progress("파일 저장 중...")
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { workbook.write(it) }
        }

        println()
        println("✓ 변환 완료: ${width}x${height} (${uniqueColors}색, 병합 ${mergeRegions.size}개)")
        println("  출력: ${outputFile.absolutePath}")
    }

    private var lastProgressPrefix: String? = null

    private fun progress(message: String) {
        // 퍼센트 부분을 제거한 prefix로 같은 종류의 메시지인지 판별
        val prefix = message.replace(Regex("\\(\\d+%\\)"), "").trim()

        if (lastProgressPrefix != null && lastProgressPrefix != prefix) {
            // 이전 메시지와 다른 종류 → 줄바꿈 후 새 줄에 출력
            println()
        }
        lastProgressPrefix = prefix

        print("\r\u001B[K$message")
        System.out.flush()
    }

    private fun buildMergedCellSet(mergeRegions: List<CellMergeOptimizer.MergeRegion>): Set<Long> {
        val set = mutableSetOf<Long>()
        for (region in mergeRegions) {
            for (r in region.startRow..region.endRow) {
                for (c in region.startCol..region.endCol) {
                    if (r != region.startRow || c != region.startCol) {
                        set.add(cellKey(r, c))
                    }
                }
            }
        }
        return set
    }

    private fun parseCellAddress(cellAddress: String): Pair<Int, Int> {
        val upper = cellAddress.uppercase()
        val colPart = upper.takeWhile { it.isLetter() }
        val rowPart = upper.dropWhile { it.isLetter() }

        require(colPart.isNotEmpty() && rowPart.isNotEmpty()) {
            "잘못된 셀 주소: $cellAddress (예: A1, B2, C5)"
        }

        val col = colPart.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
        val row = rowPart.toInt() - 1

        require(row in 0 until MAX_ROWS && col in 0 until MAX_COLUMNS) {
            "셀 주소가 Excel 범위를 초과합니다: $cellAddress"
        }

        return row to col
    }

    private fun resizeIfNeeded(image: BufferedImage, rowOffset: Int, colOffset: Int): BufferedImage {
        val width = image.width
        val height = image.height
        val maxW = MAX_COLUMNS - colOffset
        val maxH = MAX_ROWS - rowOffset

        if (width <= maxW && height <= maxH) return image

        val scale = minOf(maxW.toDouble() / width, maxH.toDouble() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val resized = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        g.dispose()

        progress("⚠ 이미지 리사이즈: ${width}x${height} → ${newWidth}x${newHeight}")
        return resized
    }

    private fun cellKey(row: Int, col: Int): Long = row.toLong() * MAX_COLUMNS + col
}
