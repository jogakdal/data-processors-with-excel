package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.core.RowRange
import io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
import io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayInputStream
import java.nio.file.Path

/**
 * TemplateRenderingEngine 테스트
 */
class TemplateRenderingEngineTest {

    @Test
    fun `XSSF mode - simple variable substitution`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/no_pivot_template.xlsx")!!
        val data = mapOf(
            "title" to "PoC 테스트",
            "date" to "2026-01-19",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            )
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then
        assertTrue(resultBytes.isNotEmpty(), "결과 바이트 배열이 비어있지 않아야 함")

        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertNotNull(sheet)
            assertTrue(sheet.lastRowNum >= 0, "시트에 행이 있어야 함")
        }
    }

    @Test
    fun `SXSSF mode - simple variable substitution`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/no_pivot_template.xlsx")!!
        val data = mapOf(
            "title" to "스트리밍 PoC 테스트",
            "date" to "2026-01-19",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            )
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(template, data)

        // Then
        assertTrue(resultBytes.isNotEmpty(), "결과 바이트 배열이 비어있지 않아야 함")

        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertNotNull(sheet)
            assertTrue(sheet.lastRowNum >= 0, "시트에 행이 있어야 함")
        }
    }

    @Test
    fun `FormulaAdjuster - range reference expansion`() {
        // Given
        val formula = "SUM(C6:C6)"
        val repeatStartRow = 5
        val repeatEndRow = 5
        val rowOffset = 2

        // When
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, RowRange(repeatStartRow, repeatEndRow), rowOffset)

        // Then
        assertEquals("SUM(C6:C8)", adjusted, "범위 참조가 확장되어야 함")
    }

    @Test
    fun `FormulaAdjuster - single reference after repeat region`() {
        // Given
        val formula = "A10+B10"
        val repeatEndRow = 5
        val rowOffset = 2

        // When
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, RowRange(0, repeatEndRow), rowOffset)

        // Then
        assertEquals("A12+B12", adjusted, "반복 영역 이후 참조가 조정되어야 함")
    }

    @Test
    fun `FormulaAdjuster - absolute reference should not change`() {
        // Given
        val formula = "SUM(\$C\$6:C6)"
        val repeatStartRow = 5
        val repeatEndRow = 5
        val rowOffset = 2

        // When
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, RowRange(repeatStartRow, repeatEndRow), rowOffset)

        // Then
        assertEquals("SUM(\$C\$6:C8)", adjusted, "절대 참조는 변경되지 않아야 함")
    }

    @Test
    fun `FormulaAdjuster - repeat index adjustment`() {
        // Given
        val formula = "A6*B6"

        // When & Then
        assertEquals("A6*B6", FormulaAdjuster.adjustForRepeatIndex(formula, 0))
        assertEquals("A7*B7", FormulaAdjuster.adjustForRepeatIndex(formula, 1))
        assertEquals("A8*B8", FormulaAdjuster.adjustForRepeatIndex(formula, 2))
    }

    @Test
    fun `TemplateAnalyzer - parse repeat marker`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/no_pivot_template.xlsx")!!

        // When
        val analyzer = TemplateAnalyzer()
        val blueprint = analyzer.analyze(template)

        // Then
        assertNotNull(blueprint)
        assertTrue(blueprint.sheets.isNotEmpty())

        val firstSheet = blueprint.sheets[0]
        assertTrue(firstSheet.repeatRegions.isNotEmpty(), "반복 영역이 있어야 함")
    }

    @Test
    fun `save output files for manual verification`(@TempDir tempDir: Path) {
        // Given
        val data = mapOf(
            "title" to "PoC 검증용 파일",
            "date" to "2026-01-19",
            "linkText" to "휴넷 홈페이지",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            )
        )

        val samplesDir = Path.of("build/samples/poc")
        java.nio.file.Files.createDirectories(samplesDir)

        // XSSF 모드
        val xssfEngine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val xssfBytes = xssfEngine.process(
            javaClass.getResourceAsStream("/templates/no_pivot_template.xlsx")!!,
            data
        )
        samplesDir.resolve("poc_xssf.xlsx").toFile().writeBytes(xssfBytes)
        assertTrue(xssfBytes.isNotEmpty())

        // SXSSF 모드
        val sxssfEngine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val sxssfBytes = sxssfEngine.process(
            javaClass.getResourceAsStream("/templates/no_pivot_template.xlsx")!!,
            data
        )
        samplesDir.resolve("poc_sxssf.xlsx").toFile().writeBytes(sxssfBytes)
        assertTrue(sxssfBytes.isNotEmpty())
    }

    @Test
    fun `multi-row template with cell styles should copy styles to all repeated rows`() {
        // Given
        val data = mapOf(
            "title" to "스타일 복사 테스트",
            "date" to "2026-01-20",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            )
        )

        val samplesDir = Path.of("build/samples/style-test")
        java.nio.file.Files.createDirectories(samplesDir)

        // XSSF 모드 테스트
        val xssfEngine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val xssfBytes = xssfEngine.process(
            javaClass.getResourceAsStream("/templates/template.xlsx")!!,
            data
        )
        samplesDir.resolve("style_test_xssf.xlsx").toFile().writeBytes(xssfBytes)
        assertTrue(xssfBytes.isNotEmpty())

        // SXSSF 모드 테스트
        val sxssfEngine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val sxssfBytes = sxssfEngine.process(
            javaClass.getResourceAsStream("/templates/template.xlsx")!!,
            data
        )
        samplesDir.resolve("style_test_sxssf.xlsx").toFile().writeBytes(sxssfBytes)
        assertTrue(sxssfBytes.isNotEmpty())
    }

    @Test
    fun `analyze third sheet structure from template`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!

        // When
        val analyzer = TemplateAnalyzer()
        val blueprint = analyzer.analyze(template)

        // Then
        assertTrue(blueprint.sheets.size >= 3, "최소 3개의 시트가 있어야 함")

        val thirdSheet = blueprint.sheets[2]
        assertTrue(thirdSheet.rows.isNotEmpty(), "행이 있어야 함")

        assertTrue(thirdSheet.repeatRegions.isNotEmpty(), "반복 영역이 있어야 함")
    }

    @Test
    fun `check template original styles`() {
        // Given
        val templateBytes = javaClass.getResourceAsStream("/templates/template.xlsx")!!.readBytes()

        // When & Then
        XSSFWorkbook(ByteArrayInputStream(templateBytes)).use { workbook ->
            assertTrue(workbook.numberOfSheets >= 3, "최소 3개의 시트가 있어야 함")

            val sheet = workbook.getSheetAt(2)
            assertNotNull(sheet)

            // 스타일이 있는 셀이 존재하는지 확인
            var hasStyledCells = false
            for (rowIndex in 0..15) {
                val row = sheet.getRow(rowIndex) ?: continue
                for (colIndex in 0..3) {
                    val cell = row.getCell(colIndex) ?: continue
                    if (cell.cellStyle.index > 0) {
                        hasStyledCells = true
                        break
                    }
                }
                if (hasStyledCells) break
            }
            assertTrue(hasStyledCells, "스타일이 적용된 셀이 있어야 함")
        }
    }

    @Test
    fun `SXSSF mode should expand conditional formatting for repeat regions`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!
        val employees = listOf(
            mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
            mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
            mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
        )
        val data = mapOf(
            "title" to "조건부 서식 테스트",
            "date" to "2026-01-20",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to employees,
            "mergedEmployees" to employees
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(template, data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            if (workbook.numberOfSheets >= 3) {
                val sheet = workbook.getSheetAt(2)
                val scf = sheet.sheetConditionalFormatting

                assertTrue(scf.numConditionalFormattings > 0, "조건부 서식이 있어야 함")

                for (i in 0 until scf.numConditionalFormattings) {
                    val cf = scf.getConditionalFormattingAt(i)
                    assertTrue(cf.numberOfRules > 0, "조건부 서식 규칙이 있어야 함")
                }
            }
        }

        // 결과 파일 저장 (수동 검증용)
        val samplesDir = Path.of("build/samples/conditional-formatting")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("sxssf_cf_test.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `XSSF mode should expand conditional formatting for repeat regions`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!
        val employees = listOf(
            mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
            mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
            mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
        )
        val data = mapOf(
            "title" to "조건부 서식 테스트 (XSSF)",
            "date" to "2026-01-20",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to employees,
            "mergedEmployees" to employees
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            if (workbook.numberOfSheets >= 3) {
                val sheet = workbook.getSheetAt(2)
                val scf = sheet.sheetConditionalFormatting

                assertTrue(scf.numConditionalFormattings > 0, "조건부 서식이 있어야 함")
            }
        }

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/conditional-formatting")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("xssf_cf_test.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `XSSF mode should expand formula references for repeat regions`() {
        // Given: 3번째 시트(셀병합)에 =SUM(B8) 수식이 있고, 반복 영역이 A7:B8
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!
        val employees = listOf(
            mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
            mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
            mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
        )
        val data = mapOf(
            "title" to "수식 확장 테스트",
            "date" to "2026-01-21",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to employees,
            // 셀병합 시트는 mergedEmployees 컬렉션 사용
            "mergedEmployees" to employees
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then: 수식이 확장되어야 함
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            assertTrue(workbook.numberOfSheets >= 3, "3개 이상의 시트가 있어야 함")

            val sheet = workbook.getSheetAt(2)
            // 반복 영역(A7:B8, 2행) × 3개 데이터 = 6행 확장
            // 원래 B11 -> B13으로 이동 (4행 추가)
            // 수식은 =SUM(B8,B10,B12) 형태로 확장 (비연속)

            // 수식 셀 찾기 (급여 합계 행)
            var formulaCell: org.apache.poi.ss.usermodel.Cell? = null
            for (rowIdx in 10..15) {
                val row = sheet.getRow(rowIdx) ?: continue
                val cell = row.getCell(1) // B열
                if (cell?.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    formulaCell = cell
                    break
                }
            }

            assertNotNull(formulaCell, "수식 셀이 있어야 함")
            val formula = formulaCell!!.cellFormula
            // 수식이 확장되었는지 확인 (B8만 참조하지 않고 여러 셀 참조)
            assertTrue(
                formula.contains(",") || formula.contains(":"),
                "수식이 범위 또는 다중 셀로 확장되어야 함: $formula"
            )
        }

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/formula-expansion")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("xssf_formula_test.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `SXSSF mode should expand formula references for repeat regions`() {
        // Given
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!
        val employees = listOf(
            mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
            mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
            mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
        )
        val data = mapOf(
            "title" to "수식 확장 테스트 (SXSSF)",
            "date" to "2026-01-21",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to employees,
            // 셀병합 시트는 mergedEmployees 컬렉션 사용
            "mergedEmployees" to employees
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(template, data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            assertTrue(workbook.numberOfSheets >= 3, "3개 이상의 시트가 있어야 함")

            val sheet = workbook.getSheetAt(2)

            // 수식 셀 찾기
            var formulaCell: org.apache.poi.ss.usermodel.Cell? = null
            for (rowIdx in 10..15) {
                val row = sheet.getRow(rowIdx) ?: continue
                val cell = row.getCell(1) // B열
                if (cell?.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                    formulaCell = cell
                    break
                }
            }

            assertNotNull(formulaCell, "수식 셀이 있어야 함")
            val formula = formulaCell!!.cellFormula
            assertTrue(
                formula.contains(",") || formula.contains(":"),
                "수식이 범위 또는 다중 셀로 확장되어야 함: $formula"
            )
        }

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/formula-expansion")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("sxssf_formula_test.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `TemplateAnalyzer - parse size marker`() {
        // Given: 임시 템플릿 생성 (size 마커 포함)
        val templateBytes = createTemplateWithSizeMarker()

        // When
        val analyzer = TemplateAnalyzer()
        val blueprint = analyzer.analyze(ByteArrayInputStream(templateBytes))

        // Then
        assertNotNull(blueprint)
        val firstSheet = blueprint.sheets[0]

        // SizeMarker가 분석되었는지 확인
        val sizeMarkerCells = firstSheet.rows.flatMap { it.cells }
            .filter { it.content is io.github.jogakdal.tbeg.engine.rendering.CellContent.SizeMarker }
        assertTrue(sizeMarkerCells.isNotEmpty(), "SizeMarker 셀이 있어야 함")

        val sizeMarker = sizeMarkerCells[0].content as io.github.jogakdal.tbeg.engine.rendering.CellContent.SizeMarker
        assertEquals("employees", sizeMarker.collectionName, "컬렉션 이름이 employees여야 함")
    }

    @Test
    fun `XSSF mode - size marker substitution`() {
        // Given: size 마커가 포함된 템플릿 생성
        val templateBytes = createTemplateWithSizeMarker()
        val data = mapOf(
            "employees" to listOf(
                mapOf("name" to "황용호"),
                mapOf("name" to "홍용호"),
                mapOf("name" to "한용호")
            )
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val cell = sheet.getRow(0).getCell(0)

            // ${size(employees)}명 -> 3명
            assertEquals("3명", cell.stringCellValue, "size 마커가 컬렉션 크기로 치환되어야 함")
        }
    }

    @Test
    fun `SXSSF mode - size marker substitution`() {
        // Given
        val templateBytes = createTemplateWithSizeMarker()
        val data = mapOf(
            "employees" to listOf(
                mapOf("name" to "황용호"),
                mapOf("name" to "홍용호"),
                mapOf("name" to "한용호"),
                mapOf("name" to "하용호"),
                mapOf("name" to "화용호")
            )
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val cell = sheet.getRow(0).getCell(0)

            // ${size(employees)}명 -> 5명
            assertEquals("5명", cell.stringCellValue, "size 마커가 컬렉션 크기로 치환되어야 함")
        }
    }

    @Test
    fun `XSSF mode - row height should be preserved after column-range shift`() {
        // Given: "멀티 반복" 시트(인덱스 5)의 행 8(0-based: 7)에 커스텀 높이 설정
        val templateBytes = javaClass.getResourceAsStream("/templates/template.xlsx")!!.readBytes()
        val customHeight = (30.0 * 20).toInt().toShort() // 30pt (POI 단위: 1/20pt)

        val modifiedTemplate = XSSFWorkbook(ByteArrayInputStream(templateBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(5) // "멀티 반복" 시트
            assertEquals("멀티 반복", sheet.sheetName, "시트 이름 확인")

            // 행 8(0-based: 7)에 커스텀 높이 설정
            val row = sheet.getRow(7) ?: sheet.createRow(7)
            row.height = customHeight

            java.io.ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        val data = mapOf(
            "title" to "행 높이 보존 테스트",
            "date" to "2026-02-10",
            "secondTitle" to "부서 목록",
            "linkText" to "테스트 링크",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            ),
            "departments" to listOf(
                mapOf("name" to "공통플랫폼팀", "members" to 15, "office" to "814호"),
                mapOf("name" to "IT전략기획팀", "members" to 8, "office" to "801호"),
                mapOf("name" to "인재경영실", "members" to 5, "office" to "813호")
            )
        )

        // When: XSSF 모드로 렌더링
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(ByteArrayInputStream(modifiedTemplate), data)

        // Then: employees 3건 → 2행 확장 → 행 8(0-based:7)이 행 10(0-based:9)으로 이동
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(5)
            val shiftedRow = sheet.getRow(9) // 0-based: 행 10 = 인덱스 9
            assertNotNull(shiftedRow, "이동된 행이 존재해야 함")
            assertEquals(
                customHeight, shiftedRow.height,
                "이동된 행의 높이(${shiftedRow.height})가 원본 커스텀 높이($customHeight)와 일치해야 함"
            )
        }
    }

    // ==================== 같은 행의 다중 독립 repeat 영역 테스트 ====================

    data class EventType(val id: Int, val tenantId: String, val name: String, val description: String)
    data class Language(val code: String, val displayName: String)

    private val eventTypes = listOf(
        EventType(1, "TENANT_A", "SIGNUP", "회원가입 이벤트"),
        EventType(2, "TENANT_A", "LOGIN", "로그인 이벤트"),
        EventType(3, "TENANT_A", "PURCHASE", "구매 이벤트"),
        EventType(4, "TENANT_B", "COURSE_START", "학습 시작"),
        EventType(5, "TENANT_B", "COURSE_COMPLETE", "학습 완료"),
    )

    private val languages = listOf(
        Language("ko", "한국어"),
        Language("en", "English"),
        Language("ja", "日本語"),
        Language("zh", "中文"),
    )

    @ParameterizedTest(name = "{0} mode - 같은 행의 다중 독립 repeat 영역이 올바르게 렌더링된다")
    @EnumSource(StreamingMode::class)
    fun `같은 행의 다중 독립 repeat 영역이 올바르게 렌더링된다`(mode: StreamingMode) {
        // Given: 같은 2행에 eventTypes(A~D열)와 languages(F~G열) 두 repeat 영역이 있는 템플릿
        val templateBytes = createMultiRepeatTemplate()
        val data = mapOf(
            "eventTypes" to eventTypes,
            "languages" to languages,
        )

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 행 수 검증: 헤더 1행 + max(eventTypes 5개, languages 4개) = 6행
            val expectedRows = 1 + maxOf(eventTypes.size, languages.size)
            assertTrue(
                sheet.lastRowNum + 1 >= expectedRows,
                "시트는 최소 $expectedRows 행이어야 한다 (실제: ${sheet.lastRowNum + 1})"
            )

            // eventTypes 데이터 검증 (A~D열, 2행부터)
            eventTypes.forEachIndexed { idx, et ->
                val row = sheet.getRow(idx + 1)
                assertNotNull(row, "eventTypes[$idx] 행이 존재해야 한다")
                assertEquals(et.id.toDouble(), row.getCell(0).numericCellValue, "eventTypes[$idx].id")
                assertEquals(et.tenantId, row.getCell(1).stringCellValue, "eventTypes[$idx].tenantId")
                assertEquals(et.name, row.getCell(2).stringCellValue, "eventTypes[$idx].name")
                assertEquals(et.description, row.getCell(3).stringCellValue, "eventTypes[$idx].description")
            }

            // languages 데이터 검증 (F~G열, 2행부터)
            languages.forEachIndexed { idx, la ->
                val row = sheet.getRow(idx + 1)
                assertNotNull(row, "languages[$idx] 행이 존재해야 한다")
                assertEquals(la.code, row.getCell(5).stringCellValue, "languages[$idx].code")
                assertEquals(la.displayName, row.getCell(6).stringCellValue, "languages[$idx].displayName")
            }

            // 마커가 남아있지 않은지 확인
            for (rowIdx in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (cellIdx in 0..row.lastCellNum) {
                    val cellValue = runCatching { row.getCell(cellIdx)?.stringCellValue }.getOrNull() ?: continue
                    assertFalse(
                        cellValue.contains("\${repeat(") || cellValue.contains("\${et.") || cellValue.contains("\${la."),
                        "마커가 남아있으면 안 된다: [$rowIdx, $cellIdx] = $cellValue"
                    )
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} mode - 같은 행에 같은 컬렉션의 독립 repeat 영역이 올바르게 렌더링된다")
    @EnumSource(StreamingMode::class)
    fun `같은 행에 같은 컬렉션의 독립 repeat 영역이 올바르게 렌더링된다`(mode: StreamingMode) {
        // Given: 같은 행에 employees 컬렉션을 A~B열과 D~E열에서 각각 repeat
        val employees = listOf(
            mapOf("name" to "황용호", "position" to "부장"),
            mapOf("name" to "홍용호", "position" to "과장"),
            mapOf("name" to "한용호", "position" to "대리"),
        )
        val templateBytes = createSameCollectionMultiRepeatTemplate()
        val data = mapOf("employees" to employees)

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 헤더 1행 + employees 3건 = 4행
            assertTrue(
                sheet.lastRowNum + 1 >= 4,
                "시트는 최소 4행이어야 한다 (실제: ${sheet.lastRowNum + 1})"
            )

            // 좌측 repeat (A~B열): name, position
            employees.forEachIndexed { idx, emp ->
                val row = sheet.getRow(idx + 1)
                assertNotNull(row, "employees[$idx] 행이 존재해야 한다")
                assertEquals(emp["name"], row.getCell(0).stringCellValue, "좌측[$idx].name")
                assertEquals(emp["position"], row.getCell(1).stringCellValue, "좌측[$idx].position")
            }

            // 우측 repeat (D~E열): position, name (역순)
            employees.forEachIndexed { idx, emp ->
                val row = sheet.getRow(idx + 1)
                assertEquals(emp["position"], row.getCell(3).stringCellValue, "우측[$idx].position")
                assertEquals(emp["name"], row.getCell(4).stringCellValue, "우측[$idx].name")
            }
        }
    }

    // ==================== 헬퍼 메서드 ====================

    /**
     * size 마커가 포함된 템플릿 생성
     */
    private fun createTemplateWithSizeMarker(): ByteArray {
        return XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")
            val row = sheet.createRow(0)
            row.createCell(0).setCellValue("\${size(employees)}명")

            java.io.ByteArrayOutputStream().also { out ->
                workbook.write(out)
            }.toByteArray()
        }
    }

    /**
     * 같은 행에 같은 컬렉션(employees)을 두 번 repeat하는 템플릿 생성
     *
     * 구조:
     * - Row 0 (헤더): A="이름", B="직급", C(빈열), D="직급", E="이름"
     * - Row 1 (데이터): A=${e1.name}, B=${e1.position}, C(repeat 마커), D=${e2.position}, E=${e2.name}, F(repeat 마커)
     *   - C1: ${repeat(employees, A2:B2, e1)} → A~B열 repeat
     *   - F1: ${repeat(employees, D2:E2, e2)} → D~E열 repeat
     */
    private fun createSameCollectionMultiRepeatTemplate() = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")

        sheet.createRow(0).apply {
            createCell(0).setCellValue("이름")
            createCell(1).setCellValue("직급")
            createCell(3).setCellValue("직급")
            createCell(4).setCellValue("이름")
        }

        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${e1.name}")
            createCell(1).setCellValue("\${e1.position}")
            createCell(2).setCellValue("\${repeat(employees, A2:B2, e1)}")
            createCell(3).setCellValue("\${e2.position}")
            createCell(4).setCellValue("\${e2.name}")
            createCell(5).setCellValue("\${repeat(employees, D2:E2, e2)}")
        }

        java.io.ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
    }

    /**
     * 같은 행에 두 개의 독립 repeat 영역이 있는 템플릿 생성
     *
     * 구조:
     * - Row 0 (헤더): A="ID", B="Tenant", C="Name", D="Desc", E(repeat 마커), F="Code", G="Display", H(repeat 마커)
     * - Row 1 (데이터): A=${et.id}, B=${et.tenantId}, C=${et.name}, D=${et.description}, F=${la.code}, G=${la.displayName}
     *   - E1: ${repeat(eventTypes, A2:D2, et)} → A~D열 repeat
     *   - H1: ${repeat(languages, F2:G2, la)} → F~G열 repeat
     */
    private fun createMultiRepeatTemplate() = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")

        // 헤더 행 (0-based row 0)
        sheet.createRow(0).apply {
            createCell(0).setCellValue("ID")
            createCell(1).setCellValue("Tenant")
            createCell(2).setCellValue("Name")
            createCell(3).setCellValue("Description")
            createCell(5).setCellValue("Code")
            createCell(6).setCellValue("Display Name")
        }

        // 데이터 행 + repeat 마커 (0-based row 1)
        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${et.id}")
            createCell(1).setCellValue("\${et.tenantId}")
            createCell(2).setCellValue("\${et.name}")
            createCell(3).setCellValue("\${et.description}")
            createCell(4).setCellValue("\${repeat(eventTypes, A2:D2, et)}")
            createCell(5).setCellValue("\${la.code}")
            createCell(6).setCellValue("\${la.displayName}")
            createCell(7).setCellValue("\${repeat(languages, F2:G2, la)}")
        }

        java.io.ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
    }
}
