package io.github.jogakdal.tbeg

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Path

/**
 * count 제공 여부에 따른 결과 파일 동일성 검증 테스트.
 *
 * DataProvider에서:
 * - getItemCount()가 정확한 값을 반환하는 경우
 * - getItemCount()가 null을 반환하는 경우 (Iterator 순회로 count 파악)
 *
 * 두 경우의 결과 파일이 완벽히 동일해야 합니다.
 */
class CountProviderConsistencyTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var generator: ExcelGenerator

    @BeforeEach
    fun setUp() {
        generator = ExcelGenerator()
    }

    @AfterEach
    fun tearDown() {
        generator.close()
    }

    @Test
    fun `SXSSF mode - count provided vs count null should produce identical results`() {
        val employeeCount = 100
        val mergedEmployeeCount = 50

        // 1. count를 제공하는 DataProvider
        val withCountProvider = createDataProviderWithCount(employeeCount, mergedEmployeeCount)

        // 2. count를 제공하지 않는 DataProvider (null 반환)
        val withoutCountProvider = createDataProviderWithoutCount(employeeCount, mergedEmployeeCount)

        // SXSSF 모드로 생성
        val sxssfConfig = TbegConfig(streamingMode = StreamingMode.ENABLED)
        ExcelGenerator(sxssfConfig).use { sxssfGenerator ->
            val template1 = TestUtils.loadTemplate()
            val bytesWithCount = sxssfGenerator.generate(template1, withCountProvider)

            val template2 = TestUtils.loadTemplate()
            val bytesWithoutCount = sxssfGenerator.generate(template2, withoutCountProvider)

            // 두 결과 비교
            assertExcelFilesEqual(bytesWithCount, bytesWithoutCount, "SXSSF 모드")
        }
    }

    @Test
    fun `XSSF mode - count provided vs count null should produce identical results`() {
        val employeeCount = 100
        val mergedEmployeeCount = 50

        // 1. count를 제공하는 DataProvider
        val withCountProvider = createDataProviderWithCount(employeeCount, mergedEmployeeCount)

        // 2. count를 제공하지 않는 DataProvider (null 반환)
        val withoutCountProvider = createDataProviderWithoutCount(employeeCount, mergedEmployeeCount)

        // XSSF 모드로 생성
        val xssfConfig = TbegConfig(streamingMode = StreamingMode.DISABLED)
        ExcelGenerator(xssfConfig).use { xssfGenerator ->
            val template1 = TestUtils.loadTemplate()
            val bytesWithCount = xssfGenerator.generate(template1, withCountProvider)

            val template2 = TestUtils.loadTemplate()
            val bytesWithoutCount = xssfGenerator.generate(template2, withoutCountProvider)

            // 두 결과 비교
            assertExcelFilesEqual(bytesWithCount, bytesWithoutCount, "XSSF 모드")
        }
    }

    /**
     * count를 제공하는 DataProvider 생성
     */
    private fun createDataProviderWithCount(employeeCount: Int, mergedEmployeeCount: Int): ExcelDataProvider {
        return object : ExcelDataProvider {
            override fun getValue(name: String): Any? = when (name) {
                "title" -> "Count 제공 테스트"
                "date" -> "2026-01-29"
                "secondTitle" -> "부서별 현황"
                "linkText" -> "(주)휴넷 홈페이지"
                "url" -> "https://www.hunet.co.kr"
                else -> null
            }

            override fun getItems(name: String): Iterator<Any>? = when (name) {
                "employees" -> TestUtils.generateEmployees(employeeCount).iterator()
                "mergedEmployees" -> TestUtils.generateEmployees(mergedEmployeeCount).iterator()
                "departments" -> listOf(
                    TestUtils.Department("공통플랫폼팀", 15, "814호"),
                    TestUtils.Department("IT전략기획팀", 8, "801호"),
                    TestUtils.Department("인재경영실", 5, "813호")
                ).iterator()
                else -> null
            }

            // count를 정확히 제공
            override fun getItemCount(name: String): Int? = when (name) {
                "employees" -> employeeCount
                "mergedEmployees" -> mergedEmployeeCount
                "departments" -> 3
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
     * count를 제공하지 않는 DataProvider 생성 (getItemCount가 null 반환)
     */
    private fun createDataProviderWithoutCount(employeeCount: Int, mergedEmployeeCount: Int): ExcelDataProvider {
        return object : ExcelDataProvider {
            override fun getValue(name: String): Any? = when (name) {
                "title" -> "Count 제공 테스트"
                "date" -> "2026-01-29"
                "secondTitle" -> "부서별 현황"
                "linkText" -> "(주)휴넷 홈페이지"
                "url" -> "https://www.hunet.co.kr"
                else -> null
            }

            override fun getItems(name: String): Iterator<Any>? = when (name) {
                "employees" -> TestUtils.generateEmployees(employeeCount).iterator()
                "mergedEmployees" -> TestUtils.generateEmployees(mergedEmployeeCount).iterator()
                "departments" -> listOf(
                    TestUtils.Department("공통플랫폼팀", 15, "814호"),
                    TestUtils.Department("IT전략기획팀", 8, "801호"),
                    TestUtils.Department("인재경영실", 5, "813호")
                ).iterator()
                else -> null
            }

            // count를 null로 반환 (라이브러리가 Iterator 순회로 파악)
            override fun getItemCount(name: String): Int? = null

            override fun getImage(name: String): ByteArray? = when (name) {
                "logo" -> TestUtils.loadImage("hunet_logo.png")
                "ci" -> TestUtils.loadImage("hunet_ci.png")
                else -> null
            }

            override fun getMetadata(): DocumentMetadata? = null
        }
    }

    /**
     * 두 Excel 파일의 내용이 동일한지 검증
     */
    private fun assertExcelFilesEqual(bytes1: ByteArray, bytes2: ByteArray, mode: String) {
        XSSFWorkbook(ByteArrayInputStream(bytes1)).use { wb1 ->
            XSSFWorkbook(ByteArrayInputStream(bytes2)).use { wb2 ->
                // 시트 수 비교
                assertEquals(wb1.numberOfSheets, wb2.numberOfSheets, "$mode: 시트 수가 다름")

                for (sheetIdx in 0 until wb1.numberOfSheets) {
                    val sheet1 = wb1.getSheetAt(sheetIdx)
                    val sheet2 = wb2.getSheetAt(sheetIdx)

                    assertEquals(sheet1.sheetName, sheet2.sheetName, "$mode: 시트[$sheetIdx] 이름이 다름")

                    // 행 수 비교
                    assertEquals(
                        sheet1.lastRowNum,
                        sheet2.lastRowNum,
                        "$mode: 시트[${sheet1.sheetName}] 행 수가 다름"
                    )

                    // 각 행의 셀 비교
                    for (rowIdx in 0..sheet1.lastRowNum) {
                        val row1 = sheet1.getRow(rowIdx)
                        val row2 = sheet2.getRow(rowIdx)

                        if (row1 == null && row2 == null) continue
                        assertNotNull(row1, "$mode: 시트[${sheet1.sheetName}] 행[$rowIdx] - bytes1에만 null")
                        assertNotNull(row2, "$mode: 시트[${sheet1.sheetName}] 행[$rowIdx] - bytes2에만 null")

                        val lastCellNum = maxOf(row1!!.lastCellNum.toInt(), row2!!.lastCellNum.toInt())
                        for (colIdx in 0 until lastCellNum) {
                            val cell1 = row1.getCell(colIdx)
                            val cell2 = row2.getCell(colIdx)

                            val cellRef = "${('A' + colIdx)}${rowIdx + 1}"

                            // 둘 다 null이면 OK
                            if (cell1 == null && cell2 == null) continue

                            // 하나만 null이면 실패
                            if (cell1 == null || cell2 == null) {
                                fail<Unit>("$mode: 시트[${sheet1.sheetName}] 셀[$cellRef] - 하나만 null (cell1=${cell1}, cell2=${cell2})")
                            }

                            // 셀 타입 비교
                            assertEquals(
                                cell1!!.cellType,
                                cell2!!.cellType,
                                "$mode: 시트[${sheet1.sheetName}] 셀[$cellRef] 타입이 다름"
                            )

                            // 셀 값 비교
                            val value1 = getCellValueAsString(cell1)
                            val value2 = getCellValueAsString(cell2)
                            assertEquals(
                                value1,
                                value2,
                                "$mode: 시트[${sheet1.sheetName}] 셀[$cellRef] 값이 다름"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell): String {
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> "FORMULA:${cell.cellFormula}"
            org.apache.poi.ss.usermodel.CellType.BLANK -> ""
            org.apache.poi.ss.usermodel.CellType.ERROR -> "ERROR:${cell.errorCellValue}"
            else -> ""
        }
    }

}
