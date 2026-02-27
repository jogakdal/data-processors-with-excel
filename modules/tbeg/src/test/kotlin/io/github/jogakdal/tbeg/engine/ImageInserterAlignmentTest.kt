package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.rendering.ImageInserter
import io.github.jogakdal.tbeg.engine.rendering.ImageSizeSpec
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * ImageInserter의 셀 alignment 기반 이미지 위치 조정 테스트
 *
 * ORIGINAL/지정 크기/비율 유지 모드는 oneCellAnchor(from + extent)로 삽입되고,
 * FIT_TO_CELL만 twoCellAnchor(from + to)로 삽입된다.
 */
class ImageInserterAlignmentTest {

    private val inserter = ImageInserter()
    private val EMU_PER_PIXEL = 9525L
    private val MARGIN_PX = 1L

    /** 테스트용 PNG 이미지 생성 */
    private fun createPng(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()
    }

    /** 셀의 크기와 alignment를 설정한 워크북 생성 */
    private fun createWorkbook(
        colWidthUnit: Int = 20 * 256,
        rowHeightPoints: Float = 50f,
        hAlign: HorizontalAlignment = HorizontalAlignment.GENERAL,
        vAlign: VerticalAlignment = VerticalAlignment.TOP,
        targetRow: Int = 0,
        targetCol: Int = 0
    ): XSSFWorkbook = XSSFWorkbook().also { workbook ->
        val sheet = workbook.createSheet("Sheet1")
        sheet.setColumnWidth(targetCol, colWidthUnit)
        val row = sheet.createRow(targetRow)
        row.heightInPoints = rowHeightPoints
        row.createCell(targetCol).cellStyle = workbook.createCellStyle().apply {
            alignment = hAlign
            verticalAlignment = vAlign
        }
    }

    /** oneCellAnchor 정보 */
    private data class OneCellAnchorInfo(
        val col: Int, val colOff: Long,
        val row: Int, val rowOff: Long,
        val cx: Long, val cy: Long
    )

    /** 삽입된 이미지의 oneCellAnchor 추출 */
    private fun getOneCellAnchor(workbook: XSSFWorkbook): OneCellAnchorInfo {
        val drawing = workbook.getSheetAt(0).createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.getCTDrawing().oneCellAnchorList.first()
        val from = anchor.from
        val ext = anchor.ext
        return OneCellAnchorInfo(
            col = from.col, colOff = from.colOff as Long,
            row = from.row, rowOff = from.rowOff as Long,
            cx = ext.cx, cy = ext.cy
        )
    }

    /** 삽입된 이미지의 twoCellAnchor 추출 (FIT_TO_CELL 전용) */
    private fun getTwoCellAnchor(workbook: XSSFWorkbook): XSSFClientAnchor {
        val drawing = workbook.getSheetAt(0).createDrawingPatriarch() as XSSFDrawing
        return drawing.shapes.filterIsInstance<XSSFPicture>().first().clientAnchor as XSSFClientAnchor
    }

    /** 행 높이를 픽셀로 변환 (96 DPI) */
    private fun pointsToPixels(points: Double) = points * 96.0 / 72.0

    // ── 1. 기본 동작 ──

    @Test
    fun `GENERAL과 TOP에서 좌상단 마진 위치에 배치된다`() {
        val workbook = createWorkbook()
        val sheet = workbook.getSheetAt(0)

        inserter.insertImage(workbook, sheet, createPng(20, 20), 0, 0, ImageSizeSpec.ORIGINAL)

        val anchor = getOneCellAnchor(workbook)
        assertEquals(0, anchor.col)
        assertEquals(0, anchor.row)
        assertEquals(MARGIN_PX * EMU_PER_PIXEL, anchor.colOff)
        assertEquals(MARGIN_PX * EMU_PER_PIXEL, anchor.rowOff)
        assertEquals(20 * EMU_PER_PIXEL, anchor.cx)
        assertEquals(20 * EMU_PER_PIXEL, anchor.cy)
    }

    // ── 2. RIGHT + BOTTOM ──

    @Test
    fun `RIGHT BOTTOM 정렬에서 다음 열과 행 기준 음수 오프셋으로 배치된다`() {
        val workbook = createWorkbook(hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM)
        val sheet = workbook.getSheetAt(0)

        inserter.insertImage(
            workbook, sheet, createPng(20, 20), 0, 0, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM
        )

        val anchor = getOneCellAnchor(workbook)
        // RIGHT: endCol+1 기준 음수 오프셋 = -(이미지너비 + 마진)
        assertEquals(1, anchor.col, "다음 열(endCol+1) 기준")
        assertEquals(-(20 * EMU_PER_PIXEL + MARGIN_PX * EMU_PER_PIXEL), anchor.colOff)
        // BOTTOM: endRow+1 기준 음수 오프셋 = -(이미지높이 + 마진)
        assertEquals(1, anchor.row, "다음 행(endRow+1) 기준")
        assertEquals(-(20 * EMU_PER_PIXEL + MARGIN_PX * EMU_PER_PIXEL), anchor.rowOff)
        assertEquals(20 * EMU_PER_PIXEL, anchor.cx, "이미지 너비가 원본과 동일해야 함")
        assertEquals(20 * EMU_PER_PIXEL, anchor.cy, "이미지 높이가 원본과 동일해야 함")
    }

    // ── 3. CENTER + CENTER ──

    @Test
    fun `CENTER 정렬에서 이미지 중앙이 셀 중앙에 위치한다`() {
        val workbook = createWorkbook(hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER)
        val sheet = workbook.getSheetAt(0)
        val cellWidthPx = sheet.getColumnWidthInPixels(0).toDouble()
        val cellHeightPx = pointsToPixels(sheet.getRow(0).heightInPoints.toDouble())

        inserter.insertImage(
            workbook, sheet, createPng(20, 20), 0, 0, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER
        )

        val anchor = getOneCellAnchor(workbook)
        assertEquals(0, anchor.col)
        assertEquals(0, anchor.row)
        assertEquals(((cellWidthPx - 20.0) / 2 * EMU_PER_PIXEL).toLong(), anchor.colOff)
        assertEquals(((cellHeightPx - 20.0) / 2 * EMU_PER_PIXEL).toLong(), anchor.rowOff)
    }

    // ── 4. FIT_TO_CELL은 alignment 무시 (twoCellAnchor 유지) ──

    @Test
    fun `FIT_TO_CELL은 alignment 설정과 무관하게 셀 전체를 채운다`() {
        val workbook = createWorkbook(hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM)
        val sheet = workbook.getSheetAt(0)

        inserter.insertImage(
            workbook, sheet, createPng(20, 20), 0, 0, ImageSizeSpec.FIT_TO_CELL,
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM
        )

        val anchor = getTwoCellAnchor(workbook)
        assertEquals((MARGIN_PX * EMU_PER_PIXEL).toInt(), anchor.dx1, "FIT_TO_CELL에서 dx1은 마진이어야 함")
        assertEquals((MARGIN_PX * EMU_PER_PIXEL).toInt(), anchor.dy1, "FIT_TO_CELL에서 dy1은 마진이어야 함")
    }

    // ── 5. fitToWidth: vAlign만 효과 ──

    @Test
    fun `fitToWidth 모드에서 vAlign만 효과가 있다`() {
        val workbook = createWorkbook(rowHeightPoints = 100f, hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.BOTTOM)
        val sheet = workbook.getSheetAt(0)

        inserter.insertImage(
            workbook, sheet, createPng(100, 50), 0, 0, ImageSizeSpec(0, -1),
            hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.BOTTOM
        )

        val anchor = getOneCellAnchor(workbook)
        val cellWidthPx = sheet.getColumnWidthInPixels(0).toDouble()
        val imageWidthPx = cellWidthPx - MARGIN_PX * 2.0
        // 너비를 채우므로 hAlign(CENTER)은 마진과 동일
        assertEquals(((cellWidthPx - imageWidthPx) / 2 * EMU_PER_PIXEL).toLong(), anchor.colOff,
            "fitToWidth에서 hAlign은 실질적으로 무시됨")
        // vAlign BOTTOM → 다음 행 기준 음수 오프셋
        assertEquals(1, anchor.row, "BOTTOM: 다음 행(endRow+1) 기준")
        assertTrue(anchor.rowOff < 0, "vAlign BOTTOM이 적용되어 음수 오프셋이어야 함")
    }

    // ── 6. fitToHeight: hAlign만 효과 ──

    @Test
    fun `fitToHeight 모드에서 hAlign만 효과가 있다`() {
        val workbook = createWorkbook(colWidthUnit = 50 * 256, rowHeightPoints = 30f,
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.CENTER)
        val sheet = workbook.getSheetAt(0)

        inserter.insertImage(
            workbook, sheet, createPng(50, 100), 0, 0, ImageSizeSpec(-1, 0),
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.CENTER
        )

        val anchor = getOneCellAnchor(workbook)
        val cellHeightPx = pointsToPixels(sheet.getRow(0).heightInPoints.toDouble())
        val imageHeightPx = cellHeightPx - MARGIN_PX * 2.0
        // 높이를 채우므로 vAlign(CENTER)은 마진과 동일
        assertEquals(((cellHeightPx - imageHeightPx) / 2 * EMU_PER_PIXEL).toLong(), anchor.rowOff,
            "fitToHeight에서 vAlign은 실질적으로 무시됨")
        // hAlign RIGHT → 다음 열 기준 음수 오프셋
        assertEquals(1, anchor.col, "RIGHT: 다음 열(endCol+1) 기준")
        assertTrue(anchor.colOff < 0, "hAlign RIGHT가 적용되어 음수 오프셋이어야 함")
    }

    // ── 7. 이미지 > 셀 (RIGHT) ──

    @Test
    fun `이미지가 셀보다 클 때 RIGHT 정렬에서 다음 열 기준 큰 음수 오프셋이 적용된다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        // 각 열 너비를 좁게 설정 (약 35px)
        for (col in 0..10) sheet.setColumnWidth(col, 5 * 256)
        val row = sheet.createRow(0)
        row.heightInPoints = 15f
        val targetCol = 5
        row.createCell(targetCol)

        inserter.insertImage(
            workbook, sheet, createPng(200, 100), 0, targetCol, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM
        )

        val anchor = getOneCellAnchor(workbook)
        // RIGHT: endCol+1 기준 음수 오프셋 (이미지가 셀보다 크므로 여러 열을 덮음)
        assertEquals(targetCol + 1, anchor.col, "다음 열(endCol+1) 기준")
        assertEquals(-(200 * EMU_PER_PIXEL + MARGIN_PX * EMU_PER_PIXEL), anchor.colOff,
            "음수 오프셋 = -(이미지너비 + 마진)")
    }

    // ── 8. 이미지 > 셀 (CENTER) ──

    @Test
    fun `이미지가 셀보다 클 때 CENTER 정렬에서 이미지가 셀 양쪽으로 나간다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        for (col in 0..10) sheet.setColumnWidth(col, 5 * 256)
        val row = sheet.createRow(0)
        row.heightInPoints = 15f
        val targetCol = 5
        row.createCell(targetCol)

        inserter.insertImage(
            workbook, sheet, createPng(200, 100), 0, targetCol, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER
        )

        val anchor = getOneCellAnchor(workbook)
        assertTrue(anchor.col < targetCol, "이미지 시작이 target 열보다 왼쪽이어야 함 (col=${anchor.col}, target=$targetCol)")
    }

    // ── 9. 시트 경계 (음수 오프셋 방식) ──

    @Test
    fun `시트 경계(0열 0행)에서 RIGHT BOTTOM은 음수 오프셋으로 정상 처리된다`() {
        val workbook = createWorkbook(colWidthUnit = 5 * 256, rowHeightPoints = 15f)
        val sheet = workbook.getSheetAt(0)

        // 0열 0행에 셀보다 큰 이미지를 RIGHT/BOTTOM으로 삽입
        inserter.insertImage(
            workbook, sheet, createPng(200, 100), 0, 0, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.RIGHT, vAlign = VerticalAlignment.BOTTOM
        )

        val anchor = getOneCellAnchor(workbook)
        // RIGHT: endCol(0)+1=1 기준 음수 오프셋
        assertEquals(1, anchor.col, "다음 열(1) 기준")
        assertTrue(anchor.colOff < 0, "음수 오프셋")
        // BOTTOM: endRow(0)+1=1 기준 음수 오프셋
        assertEquals(1, anchor.row, "다음 행(1) 기준")
        assertTrue(anchor.rowOff < 0, "음수 오프셋")
        // 크기는 원본과 동일
        assertEquals(200 * EMU_PER_PIXEL, anchor.cx)
        assertEquals(100 * EMU_PER_PIXEL, anchor.cy)
    }

    // ── 추가: insertImageWithPosition에 전달된 alignment가 적용된다 ──

    @Test
    fun `insertImageWithPosition에 전달된 alignment가 올바르게 적용된다`() {
        val workbook = createWorkbook()
        val sheet = workbook.getSheetAt(0)
        val cellWidthPx = sheet.getColumnWidthInPixels(0).toDouble()
        val cellHeightPx = pointsToPixels(sheet.getRow(0).heightInPoints.toDouble())

        // alignment를 외부에서 전달 (SXSSF 안전: processImageMarker에서 미리 읽어 전달)
        inserter.insertImageWithPosition(
            workbook, sheet, createPng(20, 20), null, 0, 0, ImageSizeSpec.ORIGINAL,
            hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER
        )

        val anchor = getOneCellAnchor(workbook)
        assertEquals(((cellWidthPx - 20.0) / 2 * EMU_PER_PIXEL).toLong(), anchor.colOff,
            "전달된 hAlign CENTER가 적용되어야 함")
        assertEquals(((cellHeightPx - 20.0) / 2 * EMU_PER_PIXEL).toLong(), anchor.rowOff,
            "전달된 vAlign CENTER가 적용되어야 함")
    }

    // ── 추가: 지정 크기 모드에서 alignment 동작 ──

    @Test
    fun `지정 크기 모드에서 CENTER 정렬이 적용된다`() {
        val workbook = createWorkbook(hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER)
        val sheet = workbook.getSheetAt(0)
        val cellWidthPx = sheet.getColumnWidthInPixels(0).toDouble()
        val cellHeightPx = pointsToPixels(sheet.getRow(0).heightInPoints.toDouble())
        val targetWidth = 30
        val targetHeight = 25

        inserter.insertImage(
            workbook, sheet, createPng(100, 80), 0, 0, ImageSizeSpec(targetWidth, targetHeight),
            hAlign = HorizontalAlignment.CENTER, vAlign = VerticalAlignment.CENTER
        )

        val anchor = getOneCellAnchor(workbook)
        assertEquals(((cellWidthPx - targetWidth) / 2 * EMU_PER_PIXEL).toLong(), anchor.colOff)
        assertEquals(((cellHeightPx - targetHeight) / 2 * EMU_PER_PIXEL).toLong(), anchor.rowOff)
        assertEquals(targetWidth * EMU_PER_PIXEL, anchor.cx)
        assertEquals(targetHeight * EMU_PER_PIXEL, anchor.cy)
    }
}
