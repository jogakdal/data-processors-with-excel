@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * NumberFormatProcessor가 수식 셀에도 숫자 서식을 적용하는지 테스트
 */
@DisplayName("NumberFormatProcessor 수식 셀 숫자 서식 테스트")
class NumberFormatProcessorTest {

    private val emptyDataProvider = object : ExcelDataProvider {
        override fun getValue(name: String): Any? = null
        override fun getItems(name: String): Iterator<Any>? = null
        override fun getImage(name: String): ByteArray? = null
    }

    private fun createContext(bytes: ByteArray) = ProcessingContext(
        templateBytes = bytes,
        dataProvider = emptyDataProvider,
        config = TbegConfig(),
        metadata = null
    ).also { it.resultBytes = bytes }

    @Test
    @DisplayName("수식 셀에 General 서식이면 숫자 서식이 적용되어야 한다")
    fun formulaCellGetsNumberFormat() {
        val bytes = createWorkbookBytes {
            createRow(0).createCell(0).cellFormula = "SUM(B1:B5)"
        }

        val result = NumberFormatProcessor().process(createContext(bytes))

        XSSFWorkbook(ByteArrayInputStream(result.resultBytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
            assertEquals(CellType.FORMULA, cell.cellType)
            // 정수 포맷(#,##0, 인덱스 3)이 적용되어야 한다
            assertEquals(3, cell.cellStyle.dataFormat.toInt(), "수식 셀에 정수 숫자 서식이 적용되어야 한다")
        }
    }

    @Test
    @DisplayName("수식 셀의 정렬은 GENERAL을 유지해야 한다")
    fun formulaCellKeepsGeneralAlignment() {
        val bytes = createWorkbookBytes {
            createRow(0).createCell(0).cellFormula = "AVERAGE(B1:B5)"
        }

        val result = NumberFormatProcessor().process(createContext(bytes))

        XSSFWorkbook(ByteArrayInputStream(result.resultBytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
            assertEquals(HorizontalAlignment.GENERAL, cell.cellStyle.alignment, "정렬은 GENERAL이어야 한다")
        }
    }

    @Test
    @DisplayName("이미 숫자 서식이 있는 수식 셀은 변경되지 않아야 한다")
    fun formulaCellWithExistingFormatUnchanged() {
        val bytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")
            val percentStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("0.00%")
            }
            sheet.createRow(0).createCell(0).apply {
                cellFormula = "A2/B2"
                cellStyle = percentStyle
            }
            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        val result = NumberFormatProcessor().process(createContext(bytes))

        XSSFWorkbook(ByteArrayInputStream(result.resultBytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
            val formatString = workbook.createDataFormat().getFormat(cell.cellStyle.dataFormat)
            assertEquals("0.00%", formatString, "기존 퍼센트 서식이 유지되어야 한다")
        }
    }

    @Test
    @DisplayName("숫자 셀은 기존과 동일하게 숫자 서식과 오른쪽 정렬이 적용되어야 한다")
    fun numericCellBehaviorUnchanged() {
        val bytes = createWorkbookBytes {
            createRow(0).apply {
                createCell(0).setCellValue(15000.0)
                createCell(1).setCellValue(3.14)
            }
        }

        val result = NumberFormatProcessor().process(createContext(bytes))

        XSSFWorkbook(ByteArrayInputStream(result.resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            val intCell = sheet.getRow(0).getCell(0)
            assertTrue(intCell.cellStyle.dataFormat.toInt() != 0, "정수 셀에 숫자 서식 적용")
            assertEquals(HorizontalAlignment.RIGHT, intCell.cellStyle.alignment, "정수 셀 오른쪽 정렬")

            val decCell = sheet.getRow(0).getCell(1)
            assertTrue(decCell.cellStyle.dataFormat.toInt() != 0, "소수 셀에 숫자 서식 적용")
            assertEquals(HorizontalAlignment.RIGHT, decCell.cellStyle.alignment, "소수 셀 오른쪽 정렬")
        }
    }

    @Test
    @DisplayName("텍스트 결과 수식 셀에도 숫자 서식이 적용되지만 실제 표시에는 영향 없어야 한다")
    fun textFormulaGetsNumberFormatSafely() {
        val bytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Hello")
                createCell(1).setCellValue("World")
            }
            // 텍스트 결과 수식
            sheet.createRow(1).createCell(0).cellFormula = "CONCATENATE(A1,\" \",B1)"
            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        val result = NumberFormatProcessor().process(createContext(bytes))

        XSSFWorkbook(ByteArrayInputStream(result.resultBytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(1).getCell(0)
            assertEquals(CellType.FORMULA, cell.cellType)
            // 숫자 서식이 적용되지만, 텍스트 결과에는 영향을 미치지 않으므로 안전하다
            assertEquals(3, cell.cellStyle.dataFormat.toInt(), "수식 셀에 숫자 서식이 적용된다")

            // 수식을 평가하면 텍스트 결과이며, 숫자 서식은 텍스트 값에 영향을 미치지 않는다
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            val evaluated = evaluator.evaluate(cell)
            assertEquals(CellType.STRING, evaluated.cellType, "수식 결과는 텍스트 타입이다")
            assertEquals("Hello World", evaluated.stringValue, "텍스트 결과가 올바르게 반환된다")
        }
    }

    private fun createWorkbookBytes(sheetBlock: org.apache.poi.ss.usermodel.Sheet.() -> Unit) =
        XSSFWorkbook().use { workbook ->
            workbook.createSheet("Sheet1").sheetBlock()
            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }
}
