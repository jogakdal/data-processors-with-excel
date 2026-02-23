@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * repeat 행의 비반복 열에 있는 수식 셀이 올바르게 조정되는지 테스트
 *
 * Copilot PR 코멘트 #1~#3에서 지적된 isStaticRow=false 관련 시나리오:
 * - repeat 영역과 같은 행에 있지만 repeat 범위 밖의 수식 셀
 * - 수식이 repeat 영역을 참조하면 범위 확장이 필요하다
 *
 * 관련 코드:
 * - SxssfRenderingStrategy.collectNonRepeatCells (라인 381)
 * - SxssfRenderingStrategy.writeRepeatCellsForRow (라인 577)
 * - SxssfRenderingStrategy.writeEmptyRangeForStreaming (라인 615)
 */
@DisplayName("repeat 행의 비반복 수식 셀 조정 테스트")
class NonRepeatFormulaCellTest {

    private val employees = listOf(
        mapOf("name" to "김철수", "score" to 90),
        mapOf("name" to "이영희", "score" to 85),
        mapOf("name" to "박민수", "score" to 78),
        mapOf("name" to "최지현", "score" to 92),
        mapOf("name" to "정수민", "score" to 88)
    )
    private val data = mapOf("employees" to employees)

    @Nested
    @DisplayName("단일 repeat + 같은 행 수식")
    inner class SingleRepeatWithFormula {

        /**
         * 템플릿 구조:
         * - Row 0 (헤더): A="이름", B="점수"
         * - Row 1 (데이터): A=${e.name}, B=${e.score}, C=${repeat(employees, A2:B2, e)}, D=SUM(B2:B2)
         *
         * 5명 데이터 후 기대 결과:
         * - Row 0: 헤더
         * - Row 1~5: 직원 데이터
         * - D1: =SUM(B2:B6) (범위 확장)
         */
        private fun createTemplate() = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")

            sheet.createRow(0).apply {
                createCell(0).setCellValue("이름")
                createCell(1).setCellValue("점수")
            }

            sheet.createRow(1).apply {
                createCell(0).setCellValue("\${e.name}")
                createCell(1).setCellValue("\${e.score}")
                createCell(2).setCellValue("\${repeat(employees, A2:B2, e)}")
                createCell(3).cellFormula = "SUM(B2:B2)"
            }

            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        @ParameterizedTest(name = "{0} 모드")
        @EnumSource(StreamingMode::class)
        @DisplayName("repeat 범위 밖 수식의 범위가 확장되어야 한다")
        fun formulaRangeExpansion(mode: StreamingMode) {
            val engine = TemplateRenderingEngine(mode)
            val result = engine.process(ByteArrayInputStream(createTemplate()), data)

            val formula = extractFormula(result, 1, 3) // D2 (0-indexed: row 1, col 3)

            println("[$mode] D2 수식: $formula")

            // 5명 데이터이므로 B2:B2 → B2:B6 확장
            assertEquals("SUM(B2:B6)", formula, "[$mode] repeat 영역을 참조하는 수식의 범위가 확장되어야 한다")
        }
    }

    @Nested
    @DisplayName("다중 repeat + 사이에 수식")
    inner class MultiRepeatWithFormulaBetween {

        /**
         * 템플릿 구조:
         * - Row 0 (헤더): A="이름", B="점수", D="이름2", E="점수2"
         * - Row 1 (데이터): A=${e1.name}, B=${e1.score}, C=SUM(B2:B2),
         *                    D=${e2.name}, E=${e2.score}
         *                    F=${repeat(employees, A2:B2, e1)}, G=${repeat(employees, D2:E2, e2)}
         *
         * 수식(C2)은 두 repeat 영역 사이에 위치한다.
         */
        private fun createTemplate() = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")

            sheet.createRow(0).apply {
                createCell(0).setCellValue("이름")
                createCell(1).setCellValue("점수")
                createCell(3).setCellValue("이름2")
                createCell(4).setCellValue("점수2")
            }

            sheet.createRow(1).apply {
                createCell(0).setCellValue("\${e1.name}")
                createCell(1).setCellValue("\${e1.score}")
                createCell(2).cellFormula = "SUM(B2:B2)"
                createCell(3).setCellValue("\${e2.name}")
                createCell(4).setCellValue("\${e2.score}")
                createCell(5).setCellValue("\${repeat(employees, A2:B2, e1)}")
                createCell(6).setCellValue("\${repeat(employees, D2:E2, e2)}")
            }

            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        @ParameterizedTest(name = "{0} 모드")
        @EnumSource(StreamingMode::class)
        @DisplayName("두 repeat 사이의 수식 범위가 확장되어야 한다")
        fun formulaBetweenRepeats(mode: StreamingMode) {
            val engine = TemplateRenderingEngine(mode)
            val result = engine.process(ByteArrayInputStream(createTemplate()), data)

            val formula = extractFormula(result, 1, 2) // C2 (0-indexed: row 1, col 2)

            println("[$mode] C2 수식: $formula")

            assertEquals("SUM(B2:B6)", formula, "[$mode] repeat 영역을 참조하는 수식의 범위가 확장되어야 한다")
        }
    }

    @Nested
    @DisplayName("수식이 repeat 영역 아래 행에 있는 경우 (기존 동작 확인)")
    inner class FormulaInStaticRowBelowRepeat {

        /**
         * 템플릿 구조 (기존 ForwardReferenceTest와 유사):
         * - Row 0: ${repeat(employees, A2:B2, e)}
         * - Row 1: A=${e.name}, B=${e.score}
         * - Row 2: A="합계", B=SUM(B2:B2)
         *
         * 5명 데이터 후:
         * - Row 6: B7=SUM(B2:B6)
         */
        private fun createTemplate() = XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Sheet1")

            sheet.createRow(0).createCell(0).setCellValue("\${repeat(employees, A2:B2, e)}")

            sheet.createRow(1).apply {
                createCell(0).setCellValue("\${e.name}")
                createCell(1).setCellValue("\${e.score}")
            }

            sheet.createRow(2).apply {
                createCell(0).setCellValue("합계")
                createCell(1).cellFormula = "SUM(B2:B2)"
            }

            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

        @ParameterizedTest(name = "{0} 모드")
        @EnumSource(StreamingMode::class)
        @DisplayName("정적 행의 수식 범위가 올바르게 확장된다 (기존 동작)")
        fun staticRowFormula(mode: StreamingMode) {
            val engine = TemplateRenderingEngine(mode)
            val result = engine.process(ByteArrayInputStream(createTemplate()), data)

            // 5명 데이터: 마커1행 + 데이터5행 + 합계1행 = 7행 → 합계는 row 6 (0-indexed)
            val formula = extractFormula(result, 6, 1) // B7 (0-indexed: row 6, col 1)

            println("[$mode] B7 수식: $formula")

            assertEquals("SUM(B2:B6)", formula, "[$mode] 정적 행의 수식 범위가 확장되어야 한다")
        }
    }

    private fun extractFormula(bytes: ByteArray, rowIdx: Int, colIdx: Int): String? =
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val cell = workbook.getSheetAt(0).getRow(rowIdx)?.getCell(colIdx)
            if (cell?.cellType == CellType.FORMULA) cell.cellFormula else null
        }
}
