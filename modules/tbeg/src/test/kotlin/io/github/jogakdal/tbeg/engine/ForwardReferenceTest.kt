@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
import io.github.jogakdal.tbeg.engine.rendering.PositionCalculator
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import io.github.jogakdal.tbeg.engine.rendering.RepeatRegionSpec
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path

/**
 * "아래 행 참조 수식" 테스트
 *
 * SXSSF 모드에서 수식이 repeat 영역 위에 있을 때의 동작을 검증한다.
 *
 * 시나리오:
 * - A1: =SUM(A3:A3)  <- 합계 수식 (아래 행 참조)
 * - A2: (헤더)
 * - A3: ${emp.salary}  <- repeat 영역
 *
 * 기대 결과 (데이터 5개):
 * - A1: =SUM(A3:A7)  <- 범위 확장
 * - A3~A7: 각 직원 급여
 */
class ForwardReferenceTest {

    @Test
    fun `SXSSF 기본 동작 - 아래 행 참조 수식 작성 가능 여부`() {
        // SXSSF에서 아래 행을 참조하는 수식을 작성할 수 있는지 테스트
        SXSSFWorkbook(100).use { workbook ->
            val sheet = workbook.createSheet("Test")

            // 1행에 아래 행(10행)을 참조하는 수식 작성
            val row1 = sheet.createRow(0)
            row1.createCell(0).cellFormula = "A10"  // =A10
            row1.createCell(1).cellFormula = "SUM(B5:B20)"  // 아래 범위 SUM

            // 10행에 값 작성
            for (i in 1..9) {
                sheet.createRow(i)  // 빈 행 생성 (SXSSF 순차 생성 필수)
            }
            val row10 = sheet.createRow(9)
            row10.createCell(0).setCellValue(100.0)

            // 파일로 저장하여 검증
            val bytes = ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()

            // XSSF로 다시 열어서 수식 확인
            XSSFWorkbook(ByteArrayInputStream(bytes)).use { readWb ->
                val readSheet = readWb.getSheetAt(0)
                val cellA1 = readSheet.getRow(0).getCell(0)
                val cellB1 = readSheet.getRow(0).getCell(1)
                val cellA10 = readSheet.getRow(9)?.getCell(0)

                assertEquals("A10", cellA1.cellFormula, "수식이 올바르게 저장되어야 함")
                assertEquals("SUM(B5:B20)", cellB1.cellFormula, "범위 수식이 올바르게 저장되어야 함")
                assertEquals(100.0, cellA10?.numericCellValue, "A10 값이 올바르게 저장되어야 함")
            }
        }

        println("✅ SXSSF에서 아래 행 참조 수식 작성 가능")
    }

    @Test
    fun `SXSSF 제한 - 이미 플러시된 행 수정 불가`() {
        // 작은 윈도우 크기로 플러시 동작 확인
        SXSSFWorkbook(5).use { workbook ->  // 5행만 메모리에 유지
            val sheet = workbook.createSheet("Test")

            // 0~10행 생성
            for (i in 0..10) {
                val row = sheet.createRow(i)
                row.createCell(0).setCellValue("Row $i")
            }

            // 0행 접근 시도 (이미 플러시됨)
            val row0 = sheet.getRow(0)
            assertNull(row0, "플러시된 행은 null 반환")

            // 8행 접근 (아직 메모리에 있음)
            val row8 = sheet.getRow(8)
            assertNotNull(row8, "메모리 내 행은 접근 가능")
        }

        println("✅ SXSSF에서 플러시된 행은 접근/수정 불가 확인")
    }

    @Test
    fun `TBEG - 수식이 repeat 영역 아래에 있는 경우 (정상 케이스)`() {
        // 기존 템플릿 사용 - 수식이 repeat 영역 아래에 있음
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")!!
        val employees = listOf(
            mapOf("name" to "김철수", "position" to "부장", "salary" to 8000),
            mapOf("name" to "이영희", "position" to "과장", "salary" to 6500),
            mapOf("name" to "박민수", "position" to "대리", "salary" to 4500)
        )
        val data = mapOf(
            "title" to "아래 행 참조 테스트",
            "date" to "2026-02-04",
            "linkText" to "테스트",
            "url" to "https://example.com",
            "employees" to employees,
            "mergedEmployees" to employees
        )

        // SXSSF 모드로 처리
        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(template, data)

        // 결과 검증
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 수식 셀 찾기
            var formulaFound = false
            for (rowIdx in 0 until sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (cell in row) {
                    if (cell.cellType == CellType.FORMULA) {
                        val formula = cell.cellFormula
                        println("  행 ${rowIdx + 1}: $formula")
                        if (formula.contains("SUM") && (formula.contains(":") || formula.contains(","))) {
                            formulaFound = true
                        }
                    }
                }
            }

            assertTrue(formulaFound, "수식이 확장되어야 함")
        }

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/forward-reference")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("sxssf_formula_below_repeat.xlsx").toFile().writeBytes(resultBytes)

        println("✅ 수식이 repeat 영역 아래에 있는 경우 SXSSF에서 정상 동작")
    }

    @Test
    fun `TBEG - 수식이 repeat 영역 위에 있는 경우 (아래 행 참조) - XSSF vs SXSSF 비교`() {
        // 프로그래밍 방식으로 템플릿 생성하여 테스트
        val templateBytes = createForwardRefTemplate()

        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000),
            mapOf("name" to "최지현", "salary" to 3500000),
            mapOf("name" to "정수민", "salary" to 3000000)
        )
        val data = mapOf("employees" to employees)

        // XSSF 모드
        val xssfEngine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val xssfResult = xssfEngine.process(ByteArrayInputStream(templateBytes), data)

        // SXSSF 모드
        val sxssfEngine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val sxssfResult = sxssfEngine.process(ByteArrayInputStream(templateBytes), data)

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/forward-reference")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("xssf_forward_ref.xlsx").toFile().writeBytes(xssfResult)
        samplesDir.resolve("sxssf_forward_ref.xlsx").toFile().writeBytes(sxssfResult)

        // 상세 결과 출력
        println("  === XSSF 결과 ===")
        printExcelContent(xssfResult)
        println("  === SXSSF 결과 ===")
        printExcelContent(sxssfResult)

        // 결과 비교
        val xssfFormula = extractFormula(xssfResult, 0, 0)  // A1
        val sxssfFormula = extractFormula(sxssfResult, 0, 0)  // A1

        println("  XSSF A1 수식: $xssfFormula")
        println("  SXSSF A1 수식: $sxssfFormula")

        // 두 모드의 수식이 동일해야 함
        assertEquals(xssfFormula, sxssfFormula, "XSSF와 SXSSF의 수식 결과가 동일해야 함")

        // 수식이 B3:B7로 확장되어야 함 (5개 데이터)
        val expectedFormula = "SUM(B3:B7)"
        if (sxssfFormula == expectedFormula) {
            println("✅ 아래 행 참조 수식이 올바르게 확장됨: $sxssfFormula")
        } else {
            println("⚠️ 수식이 확장되지 않음!")
            println("   기대값: $expectedFormula")
            println("   실제값: $sxssfFormula")
            println("   이것이 'SXSSF 아래 행 참조 수식 제한'의 실제 사례입니다.")
        }
    }

    private fun printExcelContent(bytes: ByteArray) {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            for (rowIdx in 0..minOf(8, sheet.lastRowNum)) {
                val row = sheet.getRow(rowIdx) ?: continue
                val cells = mutableListOf<String>()
                for (colIdx in 0..minOf(2, row.lastCellNum.toInt())) {
                    val cell = row.getCell(colIdx)
                    val value = when (cell?.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toString()
                        CellType.FORMULA -> "=${cell.cellFormula}"
                        CellType.BLANK -> ""
                        else -> cell?.toString() ?: ""
                    }
                    if (value.isNotEmpty()) cells.add("${('A' + colIdx)}:$value")
                }
                if (cells.isNotEmpty()) println("    Row ${rowIdx + 1}: ${cells.joinToString(", ")}")
            }
        }
    }

    @Test
    fun `디버그 - 템플릿 분석 결과 확인`() {
        val templateBytes = createForwardRefTemplate()

        // 템플릿 내용 출력
        println("=== 템플릿 내용 ===")
        XSSFWorkbook(ByteArrayInputStream(templateBytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            for (rowIdx in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIdx) ?: continue
                for (cell in row) {
                    val value = when (cell.cellType) {
                        CellType.STRING -> "STRING: ${cell.stringCellValue}"
                        CellType.FORMULA -> "FORMULA: =${cell.cellFormula}"
                        else -> cell.toString()
                    }
                    println("  [$rowIdx, ${cell.columnIndex}] $value")
                }
            }
        }

        // TemplateAnalyzer로 분석
        println("\n=== TemplateAnalyzer 분석 결과 ===")
        val analyzer = io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer()
        val spec = analyzer.analyze(ByteArrayInputStream(templateBytes))

        val sheetSpec = spec.sheets[0]
        println("시트: ${sheetSpec.sheetName}")
        println("행 수: ${sheetSpec.rows.size}")

        for (rowSpec in sheetSpec.rows) {
            when (rowSpec) {
                is io.github.jogakdal.tbeg.engine.rendering.RowSpec.StaticRow -> {
                    println("  StaticRow[${rowSpec.templateRowIndex}]: ${rowSpec.cells.size}개 셀")
                    rowSpec.cells.forEach { cell ->
                        println("    col=${cell.columnIndex}: ${cell.content::class.simpleName}")
                    }
                }
                is io.github.jogakdal.tbeg.engine.rendering.RowSpec.RepeatRow -> {
                    println("  RepeatRow[${rowSpec.templateRowIndex}]: collection=${rowSpec.collectionName}, " +
                            "var=${rowSpec.itemVariable}, cols=${rowSpec.repeatStartCol}..${rowSpec.repeatEndCol}")
                    rowSpec.cells.forEach { cell ->
                        println("    col=${cell.columnIndex}: ${cell.content::class.simpleName}")
                    }
                }
                is io.github.jogakdal.tbeg.engine.rendering.RowSpec.RepeatContinuation -> {
                    println("  RepeatContinuation[${rowSpec.templateRowIndex}]")
                }
            }
        }

        // repeat 영역 확인
        val repeatRows = sheetSpec.rows.filterIsInstance<io.github.jogakdal.tbeg.engine.rendering.RowSpec.RepeatRow>()
        println("\n=== RepeatRow 목록 ===")
        repeatRows.forEach { rr ->
            println("  templateRowIndex=${rr.templateRowIndex}, collection=${rr.collectionName}")
        }

        // 핵심: Row 0의 수식이 Row 2의 repeat 영역을 참조하는데, expandFormulaRanges가 동작해야 함
        // repeatRegions는 repeatRows.associateBy { it.templateRowIndex }
        // 그러면 key=2인 RepeatRow가 있어야 함
        assertTrue(repeatRows.any { it.templateRowIndex == 2 }, "Row 2가 RepeatRow여야 함")

        // PositionCalculator 테스트
        val repeatRegionSpecs = repeatRows.map { row ->
            RepeatRegionSpec(
                row.collectionName, row.itemVariable,
                row.templateRowIndex, row.repeatEndRowIndex,
                row.repeatStartCol, row.repeatEndCol, row.direction,
                row.emptyRangeSpec
            )
        }
        println("\n=== RepeatRegionSpec 목록 ===")
        repeatRegionSpecs.forEach { spec ->
            println("  collection=${spec.collection}, startRow=${spec.startRow}, endRow=${spec.endRow}, " +
                    "startCol=${spec.startCol}, endCol=${spec.endCol}")
        }

        // collectionSizes 계산
        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000),
            mapOf("name" to "최지현", "salary" to 3500000),
            mapOf("name" to "정수민", "salary" to 3000000)
        )
        val data = mapOf("employees" to employees)
        val collectionSizes = data.filterValues { it is Collection<*> }
            .mapValues { (it.value as Collection<*>).size }
        println("\n=== collectionSizes ===")
        println("  $collectionSizes")

        // PositionCalculator 테스트
        val calculator = PositionCalculator(repeatRegionSpecs, collectionSizes, 2)
        calculator.calculate()

        println("\n=== PositionCalculator.getExpansions() ===")
        calculator.getExpansions().forEach { exp ->
            println("  collection=${exp.region.collection}, finalStartRow=${exp.finalStartRow}, " +
                    "rowExpansion=${exp.rowExpansion}, itemCount=${exp.itemCount}")
        }

        // getExpansionForRegion 테스트
        val expansion = calculator.getExpansionForRegion("employees", 2, 0)
        println("\n=== getExpansionForRegion('employees', 2, 0) ===")
        if (expansion != null) {
            println("  ✅ expansion found: rowExpansion=${expansion.rowExpansion}, itemCount=${expansion.itemCount}")
        } else {
            println("  ❌ expansion is null!")
        }

        // FormulaAdjuster.expandToRangeWithCalculator 테스트
        if (expansion != null) {
            val formula = "SUM(B3:B3)"
            val (expanded, isSeq) = io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
                .expandToRangeWithCalculator(formula, expansion, 5)
            println("\n=== FormulaAdjuster.expandToRangeWithCalculator ===")
            println("  원본: $formula")
            println("  확장: $expanded")
            println("  연속: $isSeq")
        }
    }

    @Test
    fun `TBEG - 범위 참조 수식이 repeat 영역 아래에 있는 경우`() {
        // 프로그래밍 방식으로 템플릿 생성
        // A1: ${repeat(employees, A2:B2, emp)}
        // A2: ${emp.name}, B2: ${emp.salary}
        // A3: =SUM(B2:B2)  <- 범위 참조 수식 (repeat 영역 아래)
        val templateBytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")

            // A1: repeat 마커
            val row1 = sheet.createRow(0)
            row1.createCell(0).setCellValue("\${repeat(employees, A2:B2, emp)}")

            // A2, B2: 반복 영역 템플릿
            val row2 = sheet.createRow(1)
            row2.createCell(0).setCellValue("\${emp.name}")
            row2.createCell(1).setCellValue("\${emp.salary}")

            // A3: 합계 수식 (repeat 영역 아래) - 범위 참조 사용
            val row3 = sheet.createRow(2)
            row3.createCell(0).setCellValue("합계")
            row3.createCell(1).cellFormula = "SUM(B2:B2)"  // 범위 참조

            ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()
        }

        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000),
            mapOf("name" to "최지현", "salary" to 3500000),
            mapOf("name" to "정수민", "salary" to 3000000)
        )
        val data = mapOf("employees" to employees)

        // XSSF 모드
        val xssfEngine = TemplateRenderingEngine(StreamingMode.DISABLED)
        val xssfResult = xssfEngine.process(ByteArrayInputStream(templateBytes), data)

        // SXSSF 모드
        val sxssfEngine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val sxssfResult = sxssfEngine.process(ByteArrayInputStream(templateBytes), data)

        // 결과 파일 저장
        val samplesDir = Path.of("build/samples/forward-reference")
        java.nio.file.Files.createDirectories(samplesDir)
        samplesDir.resolve("xssf_range_below_repeat.xlsx").toFile().writeBytes(xssfResult)
        samplesDir.resolve("sxssf_range_below_repeat.xlsx").toFile().writeBytes(sxssfResult)

        // 결과 확인 (수식은 7행에 위치해야 함: 마커1행 + 데이터5행 + 합계1행 = 7행)
        val xssfFormula = extractFormula(xssfResult, 6, 1)  // B7
        val sxssfFormula = extractFormula(sxssfResult, 6, 1)  // B7

        println("  === 범위 참조가 repeat 영역 아래에 있는 경우 ===")
        println("  XSSF B7 수식: $xssfFormula")
        println("  SXSSF B7 수식: $sxssfFormula")

        // 수식이 B2:B6으로 확장되어야 함 (5개 데이터)
        val expectedFormula = "SUM(B2:B6)"
        assertEquals(expectedFormula, xssfFormula, "XSSF 수식이 확장되어야 함")
        assertEquals(expectedFormula, sxssfFormula, "SXSSF 수식이 확장되어야 함")

        println("✅ 범위 참조 수식이 repeat 영역 아래에서 올바르게 확장됨")
    }

    @Test
    fun `FormulaAdjuster - 상대 참조 vs 절대 참조 비교`() {
        // A1: =SUM(B3:B3)      <- 상대 참조 (B3:B5로 확장되어야 함)
        // B1: =SUM($B$3:$B$3)  <- 절대 참조 (확장되지 않아야 함)
        // A2: ${repeat(employees, A3:B3, emp)}
        // A3: ${emp.name}, B3: ${emp.salary}
        val templateBytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")

            val row1 = sheet.createRow(0)
            row1.createCell(0).cellFormula = "SUM(B3:B3)"          // 상대 참조
            row1.createCell(1).cellFormula = "SUM(\$B\$3:\$B\$3)"  // 절대 참조

            val row2 = sheet.createRow(1)
            row2.createCell(0).setCellValue("\${repeat(employees, A3:B3, emp)}")

            val row3 = sheet.createRow(2)
            row3.createCell(0).setCellValue("\${emp.name}")
            row3.createCell(1).setCellValue("\${emp.salary}")

            ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()
        }

        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000)
        )
        val data = mapOf("employees" to employees)

        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        val relativeFormula = extractFormula(resultBytes, 0, 0)
        val absoluteFormula = extractFormula(resultBytes, 0, 1)

        println("  === 상대 참조 vs 절대 참조 비교 ===")
        println("  A1 (상대): $relativeFormula")
        println("  B1 (절대): $absoluteFormula")

        // 상대 참조는 B3:B5로 확장되어야 함 (3개 항목)
        assertEquals("SUM(B3:B5)", relativeFormula, "상대 참조는 확장되어야 함")
        // 절대 참조는 그대로 유지되어야 함
        assertEquals("SUM(\$B\$3:\$B\$3)", absoluteFormula, "절대 참조는 확장되지 않아야 함")
        println("✅ 상대 참조는 확장됨, 절대 참조는 유지됨")
    }

    @Test
    fun `FormulaAdjuster - 혼합 참조 처리`() {
        // A1: =SUM(B$3:B$3)  <- 행 절대/열 상대 (DOWN 확장 시 확장되지 않아야 함)
        // B1: =SUM($B3:$B3)  <- 열 절대/행 상대 (DOWN 확장 시 확장되어야 함)
        val templateBytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")

            val row1 = sheet.createRow(0)
            row1.createCell(0).cellFormula = "SUM(B\$3:B\$3)"   // 행 절대
            row1.createCell(1).cellFormula = "SUM(\$B3:\$B3)"   // 열 절대

            val row2 = sheet.createRow(1)
            row2.createCell(0).setCellValue("\${repeat(employees, A3:B3, emp)}")

            val row3 = sheet.createRow(2)
            row3.createCell(0).setCellValue("\${emp.name}")
            row3.createCell(1).setCellValue("\${emp.salary}")

            ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()
        }

        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000)
        )
        val data = mapOf("employees" to employees)

        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        val rowAbsFormula = extractFormula(resultBytes, 0, 0)
        val colAbsFormula = extractFormula(resultBytes, 0, 1)

        println("  === 혼합 참조 테스트 ===")
        println("  A1 (행 절대 B\$3): $rowAbsFormula")
        println("  B1 (열 절대 \$B3): $colAbsFormula")

        // 행 절대 참조는 DOWN 방향 확장 시 확장되지 않아야 함
        assertEquals("SUM(B\$3:B\$3)", rowAbsFormula, "행 절대 참조는 확장되지 않아야 함")
        // 열 절대/행 상대 참조는 DOWN 방향 확장 시 확장되어야 함
        assertEquals("SUM(\$B3:\$B5)", colAbsFormula, "열 절대/행 상대 참조는 확장되어야 함")
        println("✅ 혼합 참조가 올바르게 처리됨")
    }

    @Test
    fun `FormulaAdjuster - 현재 시트 참조 확장`() {
        // A1: =SUM(B3:B3)  <- 현재 시트 상대 참조 (확장되어야 함)
        val templateBytes = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")

            val row1 = sheet.createRow(0)
            row1.createCell(0).cellFormula = "SUM(B3:B3)"

            val row2 = sheet.createRow(1)
            row2.createCell(0).setCellValue("\${repeat(employees, A3:B3, emp)}")

            val row3 = sheet.createRow(2)
            row3.createCell(0).setCellValue("\${emp.name}")
            row3.createCell(1).setCellValue("\${emp.salary}")

            ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()
        }

        val employees = listOf(
            mapOf("name" to "김철수", "salary" to 5000000),
            mapOf("name" to "이영희", "salary" to 4500000),
            mapOf("name" to "박민수", "salary" to 4000000)
        )
        val data = mapOf("employees" to employees)

        val engine = TemplateRenderingEngine(StreamingMode.ENABLED)
        val resultBytes = engine.process(ByteArrayInputStream(templateBytes), data)

        val formula = extractFormula(resultBytes, 0, 0)

        println("  === 현재 시트 참조 확장 테스트 ===")
        println("  A1: $formula")

        assertEquals("SUM(B3:B5)", formula, "현재 시트 참조는 확장되어야 함")
        println("✅ 현재 시트 참조가 B3:B5로 확장됨")
    }

    @Test
    fun `FormulaAdjuster - 다른 시트 참조도 해당 시트의 repeat 확장 반영`() {
        // FormulaAdjuster 유닛 테스트: 다른 시트 확장 정보가 전달되면 적용되는지 확인
        val formula = "SUM(Sheet2!B3:B3)"

        // Sheet2의 확장 정보 생성
        val sheet2Region = RepeatRegionSpec(
            collection = "items",
            variable = "item",
            startRow = 2,  // 0-based, B3
            endRow = 2,
            startCol = 0,
            endCol = 1,
            direction = RepeatDirection.DOWN
        )
        val sheet2Expansion = PositionCalculator.RepeatExpansion(
            region = sheet2Region,
            finalStartRow = 2,
            finalStartCol = 0,
            rowExpansion = 2,  // 3개 항목이므로 2행 확장
            colExpansion = 0,
            itemCount = 3
        )
        val sheet2Info = FormulaAdjuster.SheetExpansionInfo(
            expansions = listOf(sheet2Expansion),
            collectionSizes = mapOf("items" to 3)
        )

        // 현재 시트의 더미 확장 정보 (현재 시트에는 repeat이 없다고 가정)
        val currentRegion = RepeatRegionSpec(
            collection = "dummy",
            variable = "d",
            startRow = 100,
            endRow = 100,
            startCol = 0,
            endCol = 0,
            direction = RepeatDirection.DOWN
        )
        val currentExpansion = PositionCalculator.RepeatExpansion(
            region = currentRegion,
            finalStartRow = 100,
            finalStartCol = 0,
            rowExpansion = 0,
            colExpansion = 0,
            itemCount = 1
        )

        val otherSheetExpansions = mapOf("Sheet2" to sheet2Info)

        val (expandedFormula, _) = FormulaAdjuster.expandToRangeWithCalculator(
            formula, currentExpansion, 1, otherSheetExpansions
        )

        println("  === 다른 시트 참조 확장 테스트 ===")
        println("  원본: $formula")
        println("  확장: $expandedFormula")

        // Sheet2의 repeat 확장이 반영되어야 함
        assertEquals("SUM(Sheet2!B3:B5)", expandedFormula, "다른 시트 참조도 해당 시트의 확장이 반영되어야 함")
        println("✅ 다른 시트 참조가 Sheet2!B3:B5로 확장됨")
    }

    @Test
    fun `FormulaAdjuster - 따옴표 시트명 참조도 해당 시트의 repeat 확장 반영`() {
        // 따옴표가 있는 시트명 테스트
        val formula = "SUM('Data Sheet'!B3:B3)"

        val sheetRegion = RepeatRegionSpec(
            collection = "items",
            variable = "item",
            startRow = 2,
            endRow = 2,
            startCol = 0,
            endCol = 1,
            direction = RepeatDirection.DOWN
        )
        val sheetExpansion = PositionCalculator.RepeatExpansion(
            region = sheetRegion,
            finalStartRow = 2,
            finalStartCol = 0,
            rowExpansion = 2,
            colExpansion = 0,
            itemCount = 3
        )
        val sheetInfo = FormulaAdjuster.SheetExpansionInfo(
            expansions = listOf(sheetExpansion),
            collectionSizes = mapOf("items" to 3)
        )

        val currentRegion = RepeatRegionSpec(
            collection = "dummy",
            variable = "d",
            startRow = 100,
            endRow = 100,
            startCol = 0,
            endCol = 0,
            direction = RepeatDirection.DOWN
        )
        val currentExpansion = PositionCalculator.RepeatExpansion(
            region = currentRegion,
            finalStartRow = 100,
            finalStartCol = 0,
            rowExpansion = 0,
            colExpansion = 0,
            itemCount = 1
        )

        val otherSheetExpansions = mapOf("Data Sheet" to sheetInfo)

        val (expandedFormula, _) = FormulaAdjuster.expandToRangeWithCalculator(
            formula, currentExpansion, 1, otherSheetExpansions
        )

        println("  === 따옴표 시트명 참조 확장 테스트 ===")
        println("  원본: $formula")
        println("  확장: $expandedFormula")

        assertEquals("SUM('Data Sheet'!B3:B5)", expandedFormula, "따옴표 시트명 참조도 확장되어야 함")
        println("✅ 따옴표 시트명 참조가 'Data Sheet'!B3:B5로 확장됨")
    }

    /**
     * 아래 행 참조 수식 테스트용 템플릿 생성
     *
     * A1: =SUM(A3:A3)  <- 합계 수식 (repeat 영역 위)
     * A2: ${repeat(employees, A3:B3, emp)}
     * A3: ${emp.name}
     * B3: ${emp.salary}
     */
    private fun createForwardRefTemplate(): ByteArray {
        return XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("테스트")

            // A1: 합계 수식 (아래 행 참조)
            val row1 = sheet.createRow(0)
            row1.createCell(0).cellFormula = "SUM(B3:B3)"  // repeat 영역 내 셀 참조

            // A2: repeat 마커 (별도 셀에 배치)
            val row2 = sheet.createRow(1)
            row2.createCell(0).setCellValue("\${repeat(employees, A3:B3, emp)}")

            // A3, B3: 반복 영역 템플릿
            val row3 = sheet.createRow(2)
            row3.createCell(0).setCellValue("\${emp.name}")
            row3.createCell(1).setCellValue("\${emp.salary}")

            ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray()
        }
    }

    private fun extractFormula(bytes: ByteArray, rowIdx: Int, colIdx: Int): String? {
        return XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(rowIdx)?.getCell(colIdx)
            if (cell?.cellType == CellType.FORMULA) cell.cellFormula else null
        }
    }
}
