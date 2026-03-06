package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.ExcelGenerator
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 자동 셀 병합 (${merge(...)}) 기능 테스트
 */
class CellMergeTest {

    private lateinit var generator: ExcelGenerator

    @BeforeEach
    fun setUp() {
        generator = ExcelGenerator()
    }

    @AfterEach
    fun tearDown() {
        generator.close()
    }

    // ========== 테스트 데이터 클래스 ==========

    data class Employee(val dept: String?, val team: String?, val name: String, val rank: String)

    // ========== 1. 기본 merge -- 단일 열 연속 같은 값 병합 ==========

    @Test
    fun `basic merge - same values merged vertically`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("영업부", "영업2팀", "이영희", "과장"),
                Employee("개발부", "개발1팀", "박민수", "사원")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)

            // A열(부서): "영업부" 3행 병합, "개발부" 1행
            val mergedRegions = sheet.mergedRegions
            val colAMerges = mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }

            // 영업부가 3행에 걸쳐 병합되어야 함 (행 1~3)
            assertTrue(colAMerges.any { it.firstRow == 1 && it.lastRow == 3 },
                "영업부 3행 병합 영역이 있어야 합니다. 실제 병합: $colAMerges")

            // 개발부는 1행만이므로 병합 없음
            assertFalse(colAMerges.any { it.firstRow == 4 },
                "개발부 1행은 병합되지 않아야 합니다")

            // 첫 번째 셀에 값이 있는지 확인
            assertEquals("영업부", sheet.getRow(1).getCell(0).stringCellValue)
            // 병합된 셀은 비어있어야 함
            val mergedCell = sheet.getRow(2)?.getCell(0)
            assertTrue(mergedCell == null || mergedCell.stringCellValue.isNullOrEmpty(),
                "병합된 셀은 비어있어야 합니다")
        }
    }

    // ========== 2. 다중 열 merge -- 부서 + 팀 다중 레벨 병합 ==========

    @Test
    fun `multi-level merge - department and team`() {
        val template = createMultiLevelMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("영업부", "영업2팀", "이영희", "과장"),
                Employee("개발부", "개발1팀", "박민수", "사원")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val mergedRegions = sheet.mergedRegions

            // A열(부서): 영업부 3행 병합
            val colAMerges = mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }
            assertTrue(colAMerges.any { it.firstRow == 1 && it.lastRow == 3 },
                "영업부 3행 병합이 있어야 합니다. 실제: $colAMerges")

            // B열(팀): 영업1팀 2행 병합, 영업2팀 1행 (병합 없음)
            val colBMerges = mergedRegions.filter { it.firstColumn == 1 && it.lastColumn == 1 }
            assertTrue(colBMerges.any { it.firstRow == 1 && it.lastRow == 2 },
                "영업1팀 2행 병합이 있어야 합니다. 실제: $colBMerges")
        }
    }

    // ========== 3. 빈 컬렉션 -- merge가 있는 repeat의 빈 컬렉션 처리 ==========

    @Test
    fun `empty collection with merge marker`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to emptyList<Employee>()
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            // 빈 컬렉션이므로 merge 병합 영역이 없어야 함
            val autoMerges = sheet.mergedRegions.filter {
                it.firstColumn == 0 && it.lastColumn == 0 && it.firstRow >= 1
            }
            assertTrue(autoMerges.isEmpty(), "빈 컬렉션에서는 merge 병합이 없어야 합니다")
        }
    }

    // ========== 4. 단일 아이템 -- 1개 아이템이면 병합 없음 ==========

    @Test
    fun `single item should not create merge`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val autoMerges = sheet.mergedRegions.filter {
                it.firstColumn == 0 && it.lastColumn == 0 && it.firstRow >= 1
            }
            assertTrue(autoMerges.isEmpty(), "단일 아이템에서는 merge 병합이 없어야 합니다")
        }
    }

    // ========== 5. 모든 값이 같은 경우 -- 전체 병합 ==========

    @Test
    fun `all same values should merge all`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("영업부", "영업1팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val colAMerges = sheet.mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }

            // 전체 3행이 하나로 병합
            assertTrue(colAMerges.any { it.firstRow == 1 && it.lastRow == 3 },
                "모든 값이 같으면 전체 병합되어야 합니다. 실제: $colAMerges")
        }
    }

    // ========== 6. 모든 값이 다른 경우 -- 병합 없음 ==========

    @Test
    fun `all different values should not merge`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("개발부", "개발1팀", "김철수", "대리"),
                Employee("인사부", "인사팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val colAMerges = sheet.mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }
            assertTrue(colAMerges.isEmpty(), "모든 값이 다르면 병합이 없어야 합니다. 실제: $colAMerges")
        }
    }

    // ========== 7. null 값 -- 병합하지 않음 ==========

    @Test
    fun `null values should not be merged`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee(null, null, "홍길동", "사원"),
                Employee(null, null, "김철수", "대리"),
                Employee("개발부", "개발1팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val colAMerges = sheet.mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }
            // null 값끼리는 병합하지 않음
            assertTrue(colAMerges.isEmpty(),
                "null 값은 병합되지 않아야 합니다. 실제: $colAMerges")
        }
    }

    // ========== 8. merge 결과 검증 ==========

    @Test
    fun `merge should produce correct merge regions`() {
        val template = createBasicMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("개발부", "개발1팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)
        val merges = extractMergeRegions(bytes)

        // A열(부서) 병합 영역 확인 (col A, row >= 1)
        val autoMerges = merges.filter { it.firstColumn == 0 && it.firstRow >= 1 }
            .sortedBy { it.firstRow }

        // 영업부 2행 병합이 있어야 함
        assertTrue(autoMerges.isNotEmpty(), "merge 영역이 존재해야 합니다")
        assertTrue(autoMerges.any { it.firstRow == 1 && it.lastRow == 2 },
            "영업부 2행 병합이 있어야 합니다. 실제: $autoMerges")
    }

    // ========== 9. 수식 마커 형태 (=TBEG_MERGE) ==========

    @Test
    fun `formula-style merge marker should work`() {
        val template = createFormulaMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("개발부", "개발1팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val colAMerges = sheet.mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }

            assertTrue(colAMerges.any { it.firstRow == 1 && it.lastRow == 2 },
                "수식 형태 merge도 병합되어야 합니다. 실제: $colAMerges")
        }
    }

    // ========== 10. 다중 행 repeat에서의 merge ==========

    @Test
    fun `merge in multi-row repeat`() {
        val template = createMultiRowRepeatMergeTemplate()
        val data = mapOf<String, Any>(
            "employees" to listOf(
                Employee("영업부", "영업1팀", "홍길동", "사원"),
                Employee("영업부", "영업1팀", "김철수", "대리"),
                Employee("개발부", "개발1팀", "이영희", "과장")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val colAMerges = sheet.mergedRegions.filter { it.firstColumn == 0 && it.lastColumn == 0 }

            // 다중 행 repeat (2행씩)이므로 영업부는 4행(행1~4) 병합
            assertTrue(colAMerges.any { it.firstRow == 1 && it.lastRow == 4 },
                "다중 행 repeat에서 영업부 4행 병합이 있어야 합니다. 실제: $colAMerges")
        }
    }

    // ========== 11. RIGHT 방향 repeat에서의 가로 병합 ==========

    @Test
    fun `merge in RIGHT repeat should merge horizontally`() {
        val template = createRightRepeatMergeTemplate()
        val data = mapOf<String, Any>(
            "items" to listOf(
                mapOf("category" to "과일", "name" to "사과"),
                mapOf("category" to "과일", "name" to "배"),
                mapOf("category" to "채소", "name" to "당근")
            )
        )

        val bytes = generator.generate(ByteArrayInputStream(template), data)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            val mergedRegions = sheet.mergedRegions

            // 행 1에서 "과일"이 2열 병합
            val row1Merges = mergedRegions.filter { it.firstRow == 1 && it.lastRow == 1 }
            assertTrue(row1Merges.any { it.firstColumn == 0 && it.lastColumn == 1 },
                "RIGHT repeat에서 '과일' 2열 병합이 있어야 합니다. 실제: $row1Merges")
        }
    }

    // ========== 템플릿 생성 헬퍼 ==========

    /**
     * 기본 merge 템플릿:
     * Row 0: 부서 | 이름 | 직급 (헤더)
     * Row 1: ${merge(emp.dept)} | ${emp.name} | ${emp.rank} (repeat 영역)
     * + repeat 마커: ${repeat(employees, A2:C2, emp)}는 별도 셀(D1)에
     */
    private fun createBasicMergeTemplate(): ByteArray = createTemplate { wb, sheet ->
        // 헤더
        sheet.createRow(0).apply {
            createCell(0).setCellValue("부서")
            createCell(1).setCellValue("이름")
            createCell(2).setCellValue("직급")
            createCell(3).setCellValue("\${repeat(employees, A2:C2, emp)}")
        }
        // 데이터 행 (repeat 영역)
        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${merge(emp.dept)}")
            createCell(1).setCellValue("\${emp.name}")
            createCell(2).setCellValue("\${emp.rank}")
        }
    }

    /**
     * 다중 레벨 merge 템플릿 (부서 + 팀):
     * Row 0: 부서 | 팀 | 이름 (헤더)
     * Row 1: ${merge(emp.dept)} | ${merge(emp.team)} | ${emp.name}
     */
    private fun createMultiLevelMergeTemplate(): ByteArray = createTemplate { wb, sheet ->
        sheet.createRow(0).apply {
            createCell(0).setCellValue("부서")
            createCell(1).setCellValue("팀")
            createCell(2).setCellValue("이름")
            createCell(3).setCellValue("\${repeat(employees, A2:C2, emp)}")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${merge(emp.dept)}")
            createCell(1).setCellValue("\${merge(emp.team)}")
            createCell(2).setCellValue("\${emp.name}")
        }
    }

    /**
     * 수식 형태 merge 템플릿:
     * =TBEG_MERGE(emp.dept) 형태 사용
     */
    private fun createFormulaMergeTemplate(): ByteArray = createTemplate { wb, sheet ->
        sheet.createRow(0).apply {
            createCell(0).setCellValue("부서")
            createCell(1).setCellValue("이름")
            createCell(2).setCellValue("\${repeat(employees, A2:B2, emp)}")
        }
        sheet.createRow(1).apply {
            // POI는 TBEG_MERGE를 실제 수식으로 파싱할 수 없으므로 ctCell XML로 직접 설정
            (createCell(0) as XSSFCell).ctCell.apply {
                f = org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCellFormula.Factory.newInstance()
                f.stringValue = "TBEG_MERGE(emp.dept)"
            }
            createCell(1).setCellValue("\${emp.name}")
        }
    }

    /**
     * 다중 행 repeat merge 템플릿:
     * repeat 영역이 2행 (A2:C3)
     */
    private fun createMultiRowRepeatMergeTemplate(): ByteArray = createTemplate { wb, sheet ->
        sheet.createRow(0).apply {
            createCell(0).setCellValue("부서")
            createCell(1).setCellValue("이름")
            createCell(2).setCellValue("직급")
            createCell(3).setCellValue("\${repeat(employees, A2:C3, emp)}")
        }
        // 첫 번째 행: merge + 이름
        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${merge(emp.dept)}")
            createCell(1).setCellValue("\${emp.name}")
            createCell(2).setCellValue("\${emp.rank}")
        }
        // 두 번째 행: merge 연속 (같은 부서면 계속 병합)
        sheet.createRow(2).apply {
            createCell(0).setCellValue("\${merge(emp.dept)}")
        }
    }

    /**
     * RIGHT repeat merge 템플릿:
     * Row 0: ${repeat(items, A2:A2, item, RIGHT)} (마커)
     * Row 1: ${merge(item.category)} (가로 확장 + 가로 병합)
     * Row 2: ${item.name}
     */
    private fun createRightRepeatMergeTemplate(): ByteArray = createTemplate { wb, sheet ->
        sheet.createRow(0).apply {
            createCell(0).setCellValue("\${repeat(items, A2:A3, item, RIGHT)}")
        }
        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${merge(item.category)}")
        }
        sheet.createRow(2).apply {
            createCell(0).setCellValue("\${item.name}")
        }
    }

    // ========== 유틸리티 ==========

    private fun createTemplate(block: (XSSFWorkbook, org.apache.poi.ss.usermodel.Sheet) -> Unit): ByteArray =
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Sheet1")
            block(wb, sheet)
            ByteArrayOutputStream().apply { wb.write(this) }.toByteArray()
        }

    private fun extractMergeRegions(bytes: ByteArray): List<CellRangeAddress> =
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            wb.getSheetAt(0).mergedRegions.toList()
        }
}
