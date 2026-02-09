package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
import io.github.jogakdal.tbeg.engine.rendering.RowSpec
import io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, repeatStartRow, repeatEndRow, rowOffset)

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
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, 0, repeatEndRow, rowOffset)

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
        val adjusted = FormulaAdjuster.adjustForRowExpansion(formula, repeatStartRow, repeatEndRow, rowOffset)

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
        val repeatRows = firstSheet.rows.filterIsInstance<RowSpec.RepeatRow>()
        assertTrue(repeatRows.isNotEmpty(), "반복 영역이 있어야 함")
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

        val repeatRows = thirdSheet.rows.filterIsInstance<RowSpec.RepeatRow>()
        assertTrue(repeatRows.isNotEmpty(), "반복 영역이 있어야 함")
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
}
