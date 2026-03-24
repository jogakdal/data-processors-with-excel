package io.github.jogakdal.excelart

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

class ImageToExcelTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `기본값 A1 - 이미지가 A1부터 시작`() {
        val image = createSolidImage(4, 4, Color.RED)
        val imageFile = writePng(image, "red.png")
        val outputFile = File(tempDir, "red.xlsx")

        ImageToExcel.convert(imageFile, outputFile)

        XSSFWorkbook(FileInputStream(outputFile)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // A1 (row=0, col=0)에 빨간 배경색
            val cell = sheet.getRow(0).getCell(0)
            assertNotNull(cell)
            assertColorEquals(Color.RED, cell.cellStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle)
        }
    }

    @Test
    fun `startCell B2 - 이미지가 B2부터 시작`() {
        val image = createSolidImage(3, 3, Color.GREEN)
        val imageFile = writePng(image, "green.png")
        val outputFile = File(tempDir, "green.xlsx")

        ImageToExcel.convert(imageFile, outputFile, startCell = "B2")

        XSSFWorkbook(FileInputStream(outputFile)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // A1, B1, A2 모두 비어 있음
            assertNull(sheet.getRow(0)?.getCell(0)) // A1
            assertNull(sheet.getRow(0)?.getCell(1)) // B1
            assertNull(sheet.getRow(1)?.getCell(0)) // A2

            // B2 (row=1, col=1)에 이미지 시작
            val cell = sheet.getRow(1).getCell(1)
            assertNotNull(cell)
            assertColorEquals(Color.GREEN, cell.cellStyle as org.apache.poi.xssf.usermodel.XSSFCellStyle)
        }
    }

    @Test
    fun `startCell C3 - 병합 영역 오프셋 검증`() {
        val image = createSolidImage(5, 5, Color.BLUE)
        val imageFile = writePng(image, "blue.png")
        val outputFile = File(tempDir, "blue.xlsx")

        ImageToExcel.convert(imageFile, outputFile, startCell = "C3")

        XSSFWorkbook(FileInputStream(outputFile)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            // 5x5 단색 → 1개 병합 영역 (C3:G7, row 2~6, col 2~6)
            assertEquals(1, sheet.numMergedRegions)

            val region = sheet.getMergedRegion(0)
            assertEquals(2, region.firstRow)
            assertEquals(2, region.firstColumn)
            assertEquals(6, region.lastRow)
            assertEquals(6, region.lastColumn)
        }
    }

    @Test
    fun `단색 이미지는 전체가 하나의 병합 영역`() {
        val image = createSolidImage(10, 10, Color.BLUE)
        val imageFile = writePng(image, "blue.png")
        val outputFile = File(tempDir, "blue.xlsx")

        ImageToExcel.convert(imageFile, outputFile)

        XSSFWorkbook(FileInputStream(outputFile)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals(1, sheet.numMergedRegions)

            val region = sheet.getMergedRegion(0)
            assertEquals(0, region.firstRow)    // A1 시작 (기본값)
            assertEquals(0, region.firstColumn)
            assertEquals(9, region.lastRow)
            assertEquals(9, region.lastColumn)
        }
    }

    @Test
    fun `2색 이미지 - 색상 양자화 및 병합`() {
        val image = BufferedImage(10, 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 2) {
            for (x in 0 until 5) image.setRGB(x, y, Color.RED.rgb)
            for (x in 5 until 10) image.setRGB(x, y, Color.BLUE.rgb)
        }

        val imageFile = writePng(image, "two_colors.png")
        val outputFile = File(tempDir, "two_colors.xlsx")

        ImageToExcel.convert(imageFile, outputFile, maxColors = 2)

        XSSFWorkbook(FileInputStream(outputFile)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals(2, sheet.numMergedRegions)
        }
    }

    @Test
    fun `ColorQuantizer - 색상 수 제한`() {
        val image = BufferedImage(100, 10, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 10) {
            for (x in 0 until 100) {
                image.setRGB(x, y, Color(x * 2, y * 25, 128).rgb)
            }
        }

        val result = ColorQuantizer.quantize(image, 16)
        val uniqueColors = result.flatMap { it.toList() }.toSet()
        assertTrue(uniqueColors.size <= 16, "양자화 후 색상 수: ${uniqueColors.size}")
    }

    @Test
    fun `CellMergeOptimizer - 체커보드 패턴은 병합 없음`() {
        val pixels = arrayOf(
            intArrayOf(0xFF0000, 0x0000FF),
            intArrayOf(0x0000FF, 0xFF0000)
        )

        val regions = CellMergeOptimizer.findMergeRegions(pixels)
        assertTrue(regions.isEmpty(), "체커보드 패턴은 병합 영역이 없어야 함")
    }

    // --- 헬퍼 ---

    private fun createSolidImage(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        return image
    }

    private fun writePng(image: BufferedImage, name: String): File {
        val file = File(tempDir, name)
        ImageIO.write(image, "png", file)
        return file
    }

    private fun assertColorEquals(expected: Color, style: org.apache.poi.xssf.usermodel.XSSFCellStyle) {
        val color = style.fillForegroundXSSFColor
        assertNotNull(color)
        val rgb = color.rgb
        assertEquals(expected.red.toByte(), rgb[0])
        assertEquals(expected.green.toByte(), rgb[1])
        assertEquals(expected.blue.toByte(), rgb[2])
    }
}
