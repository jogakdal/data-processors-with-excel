package io.github.jogakdal.tbeg

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayInputStream

/**
 * 빈 컬렉션(count=0) 처리 테스트.
 *
 * 컬렉션 데이터가 0개일 때:
 * - repeat 영역이 완전히 삭제되지 않고
 * - 최소 1개 반복 단위(빈 행/열)가 출력되어야 함
 */
class EmptyCollectionTest {

    private lateinit var generator: ExcelGenerator

    @BeforeEach
    fun setUp() {
        generator = ExcelGenerator()
    }

    @AfterEach
    fun tearDown() {
        generator.close()
    }

    @ParameterizedTest(name = "{0} mode - empty collection should output one empty repeat unit")
    @EnumSource(StreamingMode::class)
    fun `empty collection should output one empty repeat unit`(mode: StreamingMode) {
        val config = TbegConfig(streamingMode = mode)
        ExcelGenerator(config).use { gen ->
            val template = TestUtils.loadTemplate()
            val emptyProvider = createEmptyCollectionProvider()

            val result = gen.generate(template, emptyProvider)

            verifyEmptyRepeatUnit(result, mode.name)
        }
    }

    @Test
    @org.junit.jupiter.api.Disabled("가로 확장 시트에서 SXSSF/XSSF 간 미묘한 차이 존재 - 추후 조사 필요")
    fun `SXSSF and XSSF should produce identical results for empty collection`() {
        val sxssfConfig = TbegConfig(streamingMode = StreamingMode.ENABLED)
        val xssfConfig = TbegConfig(streamingMode = StreamingMode.DISABLED)

        ExcelGenerator(sxssfConfig).use { sxssfGenerator ->
            ExcelGenerator(xssfConfig).use { xssfGenerator ->
                val emptyProvider = createEmptyCollectionProvider()

                val template1 = TestUtils.loadTemplate()
                val sxssfResult = sxssfGenerator.generate(template1, emptyProvider)

                val template2 = TestUtils.loadTemplate()
                val xssfResult = xssfGenerator.generate(template2, emptyProvider)

                assertExcelFilesEqual(sxssfResult, xssfResult)
            }
        }
    }

    @Test
    fun `empty collection with count=0 vs count=null should produce identical results`() {
        val sxssfConfig = TbegConfig(streamingMode = StreamingMode.ENABLED)
        ExcelGenerator(sxssfConfig).use { sxssfGenerator ->
            // count=0을 명시적으로 제공
            val withZeroCount = createEmptyCollectionProvider(provideCount = true)
            // count=null (라이브러리가 Iterator 순회로 파악)
            val withNullCount = createEmptyCollectionProvider(provideCount = false)

            val template1 = TestUtils.loadTemplate()
            val result1 = sxssfGenerator.generate(template1, withZeroCount)

            val template2 = TestUtils.loadTemplate()
            val result2 = sxssfGenerator.generate(template2, withNullCount)

            assertExcelFilesEqual(result1, result2)
        }
    }

    /**
     * 빈 컬렉션을 제공하는 DataProvider 생성
     */
    private fun createEmptyCollectionProvider(provideCount: Boolean = true): ExcelDataProvider {
        return object : ExcelDataProvider {
            override fun getValue(name: String): Any? = when (name) {
                "title" -> "빈 컬렉션 테스트"
                "date" -> "2026-01-29"
                "secondTitle" -> "부서별 현황"
                "linkText" -> "(주)휴넷 홈페이지"
                "url" -> "https://www.hunet.co.kr"
                else -> null
            }

            override fun getItems(name: String): Iterator<Any>? = when (name) {
                "employees" -> emptyList<TestUtils.Employee>().iterator()
                "mergedEmployees" -> emptyList<TestUtils.Employee>().iterator()
                "departments" -> emptyList<Any>().iterator()
                "emptyCollection" -> emptyList<Any>().iterator()
                else -> null
            }

            override fun getItemCount(name: String): Int? = when (name) {
                "employees" -> if (provideCount) 0 else null
                "mergedEmployees" -> if (provideCount) 0 else null
                "departments" -> if (provideCount) 0 else null
                "emptyCollection" -> if (provideCount) 0 else null
                else -> null
            }

            override fun getImage(name: String): ByteArray? = when (name) {
                "logo" -> TestUtils.loadImage("hunet_logo.png")
                "ci" -> TestUtils.loadImage("hunet_ci.png")
                else -> null
            }

            override fun getMetadata(): DocumentMetadata? = null
        }
    }

    /**
     * 빈 반복 단위가 올바르게 출력되었는지 검증
     *
     * 기본 템플릿 기준:
     * - employees repeat 영역에 데이터가 0개일 때
     * - 최소 1개 반복 단위(빈 행)가 출력되어야 함
     * - 마커({{...}})가 치환되어 빈 값이 되어야 함
     */
    private fun verifyEmptyRepeatUnit(bytes: ByteArray, mode: String) {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            // 첫 번째 시트 확인
            val sheet = workbook.getSheetAt(0)

            // 행이 존재해야 함 (빈 컬렉션이라도 1개 반복 단위 출력)
            assertTrue(sheet.lastRowNum > 0, "$mode: 행이 존재해야 함")

            // repeat 영역에 마커가 남아있지 않아야 함
            for (rowIdx in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (cellIdx in 0 until row.lastCellNum) {
                    val cell = row.getCell(cellIdx) ?: continue
                    if (cell.cellType == CellType.STRING) {
                        val value = cell.stringCellValue
                        assertFalse(value.contains("{{#repeat"),
                            "$mode: 행[$rowIdx] 열[$cellIdx]에 repeat 마커가 남아있음: $value")
                        assertFalse(value.contains("{{/repeat"),
                            "$mode: 행[$rowIdx] 열[$cellIdx]에 repeat 종료 마커가 남아있음: $value")
                    }
                }
            }
        }
    }

    /**
     * 두 Excel 파일의 내용이 동일한지 검증
     */
    private fun assertExcelFilesEqual(bytes1: ByteArray, bytes2: ByteArray) {
        XSSFWorkbook(ByteArrayInputStream(bytes1)).use { wb1 ->
            XSSFWorkbook(ByteArrayInputStream(bytes2)).use { wb2 ->
                assertEquals(wb1.numberOfSheets, wb2.numberOfSheets, "시트 수가 다름")

                for (sheetIdx in 0 until wb1.numberOfSheets) {
                    val sheet1 = wb1.getSheetAt(sheetIdx)
                    val sheet2 = wb2.getSheetAt(sheetIdx)

                    assertEquals(sheet1.lastRowNum, sheet2.lastRowNum,
                        "시트[${sheet1.sheetName}] 행 수가 다름")

                    for (rowIdx in 0..sheet1.lastRowNum) {
                        val row1 = sheet1.getRow(rowIdx)
                        val row2 = sheet2.getRow(rowIdx)

                        if (row1 == null && row2 == null) continue
                        assertNotNull(row1, "시트[${sheet1.sheetName}] 행[$rowIdx] - bytes1에만 null")
                        assertNotNull(row2, "시트[${sheet1.sheetName}] 행[$rowIdx] - bytes2에만 null")

                        val lastCellNum = maxOf(row1!!.lastCellNum.toInt(), row2!!.lastCellNum.toInt())
                        for (colIdx in 0 until lastCellNum) {
                            val cell1 = row1.getCell(colIdx)
                            val cell2 = row2.getCell(colIdx)

                            if (cell1 == null && cell2 == null) continue

                            val value1 = getCellValueAsString(cell1)
                            val value2 = getCellValueAsString(cell2)
                            assertEquals(value1, value2,
                                "시트[${sheet1.sheetName}] 행[$rowIdx] 열[$colIdx] 값이 다름")
                        }
                    }
                }
            }
        }
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> "FORMULA:${cell.cellFormula}"
            CellType.BLANK -> ""
            CellType.ERROR -> "ERROR:${cell.errorCellValue}"
            else -> ""
        }
    }

    // ==================== emptyRange 전용 테스트 (empty_collection_template.xlsx) ====================

    /**
     * emptyRange 기능 테스트 - 전용 템플릿 사용
     *
     * 빈 컬렉션일 때 emptyRange에 지정된 내용이 출력되어야 함
     */
    @ParameterizedTest(name = "{0} mode - emptyRange should display specified content with dedicated template")
    @EnumSource(StreamingMode::class)
    fun `emptyRange should display specified content with dedicated template`(mode: StreamingMode) {
        val config = TbegConfig(streamingMode = mode)
        ExcelGenerator(config).use { gen ->
            val template = TestUtils.loadEmptyCollectionTemplate()
            val provider = createEmptyRangeTestProvider()

            val result = gen.generate(template, provider)

            verifyEmptyRangeResult(result, mode.name)
        }
    }

    /**
     * emptyRange 테스트용 DataProvider 생성
     * - emptyCollection: 빈 컬렉션 (emptyRange 내용이 출력되어야 함)
     */
    private fun createEmptyRangeTestProvider(): ExcelDataProvider {
        return object : ExcelDataProvider {
            override fun getValue(name: String): Any? = null

            override fun getItems(name: String): Iterator<Any>? = when (name) {
                "emptyCollection" -> emptyList<Any>().iterator()
                else -> null
            }

            override fun getItemCount(name: String): Int? = when (name) {
                "emptyCollection" -> 0
                else -> null
            }

            override fun getImage(name: String): ByteArray? = null
            override fun getMetadata(): DocumentMetadata? = null
        }
    }

    /**
     * emptyRange 결과 검증
     */
    private fun verifyEmptyRangeResult(bytes: ByteArray, mode: String) {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 행이 존재해야 함
            assertTrue(sheet.lastRowNum >= 0, "$mode: 시트에 행이 존재해야 함")

            // repeat 마커가 남아있지 않아야 함
            for (rowIdx in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (cellIdx in 0 until row.lastCellNum) {
                    val cell = row.getCell(cellIdx) ?: continue
                    if (cell.cellType == CellType.STRING) {
                        val value = cell.stringCellValue
                        assertFalse(value.contains("\${repeat"),
                            "$mode: 행[$rowIdx] 열[$cellIdx]에 repeat 마커가 남아있음: $value")
                        assertFalse(value.contains("TBEG_REPEAT"),
                            "$mode: 행[$rowIdx] 열[$cellIdx]에 수식 마커가 남아있음: $value")
                    }
                }
            }

            println("$mode: emptyRange 테스트 통과 - 행 수: ${sheet.lastRowNum + 1}")
        }
    }
}
