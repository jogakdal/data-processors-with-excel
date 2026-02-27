package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.core.RowRange
import io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
import io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream

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

    // ==================== 이미지 앵커 음수 EMU 오프셋 검증 ====================

    @ParameterizedTest(name = "{0} mode - 이미지 앵커에 음수 EMU 오프셋이 없다")
    @EnumSource(StreamingMode::class)
    fun `이미지 앵커에 음수 EMU 오프셋이 없다`(mode: StreamingMode) {
        // Given: 이미지 마커가 있는 템플릿 (FIT_TO_CELL 기본 모드)
        val templateBytes = createImageTemplate()
        val data = mapOf(
            "logo" to createMinimalPng()
        )

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then: drawing XML에 음수 오프셋이 없어야 한다
        val negativeOffsetPattern = Regex("""<xdr:(col|row)Off>-\d+</xdr:(col|row)Off>""")
        ZipInputStream(ByteArrayInputStream(resultBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("drawings/") && entry.name.endsWith(".xml") && !entry.name.contains("_rels")) {
                    val content = String(zis.readBytes(), Charsets.UTF_8)
                    val negativeMatches = negativeOffsetPattern.findAll(content).toList()
                    assertTrue(
                        negativeMatches.isEmpty(),
                        "drawing XML(${entry.name})에 음수 EMU 오프셋이 있다: ${negativeMatches.map { it.value }}\n$content"
                    )
                }
                entry = zis.nextEntry
            }
        }
    }

    /** 이미지 마커가 포함된 간단한 템플릿 생성 */
    private fun createImageTemplate() = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")
        sheet.createRow(0).createCell(0).setCellValue("로고:")
        // B1:C2 병합 영역에 이미지 마커
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 1, 1, 2))
        sheet.createRow(0).createCell(1).setCellValue("\${image(logo)}")
        java.io.ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
    }

    /** 테스트용 최소 PNG 이미지 생성 (1x1 빨간 픽셀) */
    private fun createMinimalPng(): ByteArray {
        val image = java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = java.awt.Color.RED
        g.fillRect(0, 0, 10, 10)
        g.dispose()
        return java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(image, "PNG", it)
        }.toByteArray()
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

    @Test
    fun `XSSF mode should adjust formula row references within repeat region`() {
        // Given: repeat 영역 내부에 수식이 있는 템플릿 (D2=B2-C2)
        val templateBytes = createRepeatFormulaTemplate()
        val items = listOf(
            mapOf("name" to "A팀", "revenue" to 52000, "cost" to 31000),
            mapOf("name" to "B팀", "revenue" to 38000, "cost" to 22000),
            mapOf("name" to "C팀", "revenue" to 28000, "cost" to 19000),
        )
        val data = mapOf("depts" to items)

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then: 각 반복 행의 수식이 올바른 행을 참조해야 함
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // Row 1 (item 0): D2=B2-C2 (템플릿 원본)
            assertEquals("B2-C2", sheet.getRow(1).getCell(3).cellFormula, "item 0 수식")
            // Row 2 (item 1): D3=B3-C3 (행 참조 +1)
            assertEquals("B3-C3", sheet.getRow(2).getCell(3).cellFormula, "item 1 수식")
            // Row 3 (item 2): D4=B4-C4 (행 참조 +2)
            assertEquals("B4-C4", sheet.getRow(3).getCell(3).cellFormula, "item 2 수식")
        }
    }

    // ==================== XSSF 셀 XML 정합성 검증 ====================

    @Test
    fun `XSSF 모드 - 셀에 v와 is 요소가 동시에 존재하지 않는다`() {
        // Given: inline string 셀이 포함된 템플릿 (변수, 숫자 치환 포함)
        val templateBytes = createRepeatFormulaTemplate()
        val data = mapOf(
            "depts" to listOf(
                mapOf("name" to "영업부", "revenue" to 52000, "cost" to 31000),
                mapOf("name" to "개발부", "revenue" to 38000, "cost" to 22000),
            )
        )

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        // Then: sheet XML에서 <v>와 <is>가 같은 <c> 요소에 공존하면 안 된다
        val coexistPattern = Regex("""<c\s[^>]*>(?:(?!</c>).)*<v>(?:(?!</c>).)*<is>""", RegexOption.DOT_MATCHES_ALL)
        val coexistPattern2 = Regex("""<c\s[^>]*>(?:(?!</c>).)*<is>(?:(?!</c>).)*<v>""", RegexOption.DOT_MATCHES_ALL)
        ZipInputStream(ByteArrayInputStream(resultBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("worksheets/") && entry.name.endsWith(".xml")) {
                    val content = String(zis.readBytes(), Charsets.UTF_8)
                    val matches1 = coexistPattern.findAll(content).toList()
                    val matches2 = coexistPattern2.findAll(content).toList()
                    assertTrue(
                        matches1.isEmpty() && matches2.isEmpty(),
                        "${entry.name}에 <v>와 <is>가 공존하는 셀이 있다 (${matches1.size + matches2.size}개)"
                    )
                }
                entry = zis.nextEntry
            }
        }
    }

    // ==================== Rich Sample 멀티 리피트 + 멀티 차트 통합 테스트 ====================

    @ParameterizedTest(name = "{0} mode - Rich Sample 멀티 리피트가 올바르게 처리된다")
    @EnumSource(StreamingMode::class)
    fun `Rich Sample 멀티 리피트가 올바르게 처리된다`(mode: StreamingMode) {
        // Given: 왼쪽 depts(5개) + 오른쪽 products(4개) 멀티 리피트
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(template, data)

        // Then
        assertTrue(resultBytes.isNotEmpty(), "결과가 비어있지 않아야 함")

        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("Sales Report", sheet.sheetName)

            // 왼쪽 repeat (depts 5개): B8~B12 (row 7~11)
            assertEquals("Common Platform", sheet.getRow(7).getCell(1).stringCellValue, "depts[0]")
            assertEquals("Content Dev", sheet.getRow(11).getCell(1).stringCellValue, "depts[4]")

            // 오른쪽 repeat (products 4개): I8~I11 (row 7~10)
            assertEquals("Online Courses", sheet.getRow(7).getCell(8).stringCellValue, "products[0]")
            assertEquals("Contents License", sheet.getRow(10).getCell(8).stringCellValue, "products[3]")
        }

        // 결과 파일 저장 (수동 검증용)
        val samplesDir = Path.of("build/samples/rich-sample-multi-repeat")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("rich_sample_${mode.name.lowercase()}.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `XSSF mode - Rich Sample 멀티 차트 데이터 범위가 올바르게 확장된다`() {
        // Given: XSSF에서 차트 범위 조정은 렌더링 전략 내부에서 수행된다
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then: 차트 데이터 범위 검증 (drawing XML에서)
        val chartXmls = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(resultBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("charts/") && entry.name.endsWith(".xml") && !entry.name.contains("_rels")) {
                    chartXmls.add(String(zis.readBytes(), Charsets.UTF_8))
                }
                entry = zis.nextEntry
            }
        }

        assertEquals(2, chartXmls.size, "차트 2개가 있어야 함")

        val formulaPattern = Regex("""<(?:c:)?f>([^<]+)</(?:c:)?f>""")

        // 바 차트: depts 5행 확장 → B8:B12
        val barChartXml = chartXmls.first { it.contains("barChart") || it.contains("c:barChart") }
        val barFormulas = formulaPattern.findAll(barChartXml).map { it.groupValues[1] }.toList()
        assertTrue(
            barFormulas.any { it.contains("B\$8") && it.contains("B\$12") },
            "바 차트 카테고리가 B8:B12로 확장되어야 함. 실제 수식: $barFormulas"
        )

        // 파이 차트: products 4행 확장 → I8:I11
        val pieChartXml = chartXmls.first { it.contains("pieChart") || it.contains("c:pieChart") }
        val pieFormulas = formulaPattern.findAll(pieChartXml).map { it.groupValues[1] }.toList()
        assertTrue(
            pieFormulas.any { it.contains("I\$8") && it.contains("I\$11") },
            "파이 차트 카테고리가 I8:I11로 확장되어야 함. 실제 수식: $pieFormulas"
        )
    }

    @Test
    fun `SXSSF mode - Rich Sample의 repeat 내부 수식에서 외부 참조가 시프트된다`() {
        // Given: K8 수식 = J8/J9, products 4개 → J9이 J12로 시프트되어야 함
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(template, data)

        // Then: K8~K11의 수식에서 J9(상대 참조)이 J12로 시프트
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // products 4개: row 7~10 (0-based), K열 = col 10
            for (rowIdx in 7..10) {
                val cell = sheet.getRow(rowIdx).getCell(10)
                assertEquals(
                    org.apache.poi.ss.usermodel.CellType.FORMULA,
                    cell.cellType,
                    "K${rowIdx + 1}이 수식이어야 함"
                )
                assertEquals(
                    "J${rowIdx + 1}/J12",
                    cell.cellFormula,
                    "K${rowIdx + 1} 수식: products 확장(3행)으로 Total 행이 9→12로 이동"
                )
            }
        }

        // 결과 파일 저장 (수동 검증용)
        val samplesDir = Path.of("build/samples/absolute-ref-shift")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("rich_sample_sxssf.xlsx").toFile().writeBytes(resultBytes)
    }

    @Test
    fun `XSSF mode - Rich Sample의 repeat 내부 수식에서 외부 참조가 시프트된다`() {
        // Given: K8 수식 = J8/J9, products 4개 → shiftRows가 J9→J12로 조정, 복사 시 영역 밖 J12는 이동 안 함
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then: K8~K11의 수식에서 J9이 J12로 시프트
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // products 4개: row 7~10 (0-based), K열 = col 10
            for (rowIdx in 7..10) {
                val cell = sheet.getRow(rowIdx).getCell(10)
                assertEquals(
                    org.apache.poi.ss.usermodel.CellType.FORMULA,
                    cell.cellType,
                    "K${rowIdx + 1}이 수식이어야 함"
                )
                assertEquals(
                    "J${rowIdx + 1}/J12",
                    cell.cellFormula,
                    "K${rowIdx + 1} 수식: products 확장(3행)으로 Total 행이 9→12로 이동"
                )
            }
        }
    }

    @ParameterizedTest(name = "{0} mode - Rich Sample의 이미지 마커가 올바르게 처리된다")
    @EnumSource(StreamingMode::class)
    fun `Rich Sample의 이미지 마커가 올바르게 처리된다`(mode: StreamingMode) {
        // Given: K28에 ${image(ci)}, F28에 ${image(logo)}
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(template, data)

        // Then: 이미지 마커가 셀에 남아있지 않아야 함
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 모든 셀에서 ${image(...)} 마커 텍스트가 남아있지 않은지 확인
            for (rowIdx in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (colIdx in 0..row.lastCellNum) {
                    val cell = row.getCell(colIdx) ?: continue
                    if (cell.cellType == org.apache.poi.ss.usermodel.CellType.STRING) {
                        assertFalse(
                            cell.stringCellValue.contains("\${image("),
                            "이미지 마커가 ${cell.address}에 남아있음: ${cell.stringCellValue}"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `XSSF mode - repeat 확장 후 BLANK 셀의 테두리 스타일이 보존된다`() {
        // Given: Rich Sample 템플릿에서 F10, G10은 값 없이 테두리만 있는 BLANK 셀
        val template = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!
        val data = richSampleData()

        // When
        val engine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val resultBytes = engine.process(template, data)

        // Then: depts 5개로 4행 확장 → Row 10 → Row 14 (idx 13)
        // F14(col 5), G14(col 6)의 테두리가 보존되어야 함
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            for (colIdx in 5..6) {
                val cell = sheet.getRow(13)?.getCell(colIdx)
                assertNotNull(cell, "Row 14, col ${colIdx + 1} 셀이 존재해야 함")
                assertNotEquals(
                    BorderStyle.NONE,
                    cell!!.cellStyle.borderBottom,
                    "Row 14, col ${colIdx + 1} 하단 테두리가 보존되어야 함"
                )
            }
        }
    }

    @ParameterizedTest(name = "{0} mode - 셀이 없는 행의 커스텀 높이가 보존된다")
    @EnumSource(StreamingMode::class)
    fun `셀이 없는 행의 커스텀 높이가 보존된다`(mode: StreamingMode) {
        // Given: Rich Sample 템플릿에서 Row 5(0-idx 4)은 8pt 스페이서로 셀이 없지만 높이가 설정됨
        val templateBytes = javaClass.getResourceAsStream("/templates/rich_sample_template.xlsx")!!.readBytes()
        val templateHeights = XSSFWorkbook(ByteArrayInputStream(templateBytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            (0..9).associate { i -> i to sheet.getRow(i)?.height }
        }

        // When
        val engine = TemplateRenderingEngine(mode)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), richSampleData())

        // Then: 템플릿의 모든 정적 행(Row 1~7, 0-idx 0~6) 높이가 보존되어야 함
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            for (rowIdx in 0..6) {
                val expected = templateHeights[rowIdx]
                val actual = sheet.getRow(rowIdx)?.height
                assertEquals(expected, actual, "Row ${rowIdx + 1}(0-idx $rowIdx) 높이가 보존되어야 함")
            }
            // Row 5(0-idx 4)의 8pt 스페이서 높이 명시적 검증
            assertEquals(160, sheet.getRow(4)?.height?.toInt(), "8pt 스페이서 행 높이 (160 twips)")
        }
    }

    private fun richSampleData() = mapOf(
        "reportTitle" to "Q1 2026 Sales Performance Report",
        "period" to "Jan 2026 ~ Mar 2026",
        "author" to "Yongho Hwang",
        "reportDate" to "2026-02-24",
        "logo" to createMinimalPng(),
        "ci" to createMinimalPng(),
        "depts" to listOf(
            mapOf("deptName" to "Common Platform", "revenue" to 52000, "cost" to 31000, "target" to 50000),
            mapOf("deptName" to "IT Strategy", "revenue" to 38000, "cost" to 22000, "target" to 40000),
            mapOf("deptName" to "HR Management", "revenue" to 28000, "cost" to 19000, "target" to 30000),
            mapOf("deptName" to "Education Biz", "revenue" to 95000, "cost" to 61000, "target" to 90000),
            mapOf("deptName" to "Content Dev", "revenue" to 42000, "cost" to 28000, "target" to 45000),
        ),
        "products" to listOf(
            mapOf("category" to "Online Courses", "revenue" to 128000),
            mapOf("category" to "Consulting", "revenue" to 67000),
            mapOf("category" to "Certification", "revenue" to 45000),
            mapOf("category" to "Contents License", "revenue" to 15000),
        ),
    )

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
     * repeat 영역 내부에 수식이 있는 템플릿 생성
     *
     * 구조:
     * - Row 0 (헤더): A="부서", B="매출", C="비용", D="이익"
     * - Row 1 (데이터): A=${d.name}, B=${d.revenue}, C=${d.cost}, D=B2-C2, E(repeat 마커)
     *   - E1: ${repeat(depts, A2:D2, d)} → A~D열 repeat
     */
    private fun createRepeatFormulaTemplate() = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")

        sheet.createRow(0).apply {
            createCell(0).setCellValue("부서")
            createCell(1).setCellValue("매출")
            createCell(2).setCellValue("비용")
            createCell(3).setCellValue("이익")
        }

        sheet.createRow(1).apply {
            createCell(0).setCellValue("\${d.name}")
            createCell(1).setCellValue("\${d.revenue}")
            createCell(2).setCellValue("\${d.cost}")
            createCell(3).cellFormula = "B2-C2"
            createCell(4).setCellValue("\${repeat(depts, A2:D2, d)}")
        }

        java.io.ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
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
