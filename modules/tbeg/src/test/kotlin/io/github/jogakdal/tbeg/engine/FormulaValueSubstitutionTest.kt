@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 변수형 마커에 "="로 시작하는 값을 바인딩하면 Excel 수식으로 처리되는지 테스트
 */
@DisplayName("변수형 마커 수식 치환 테스트")
class FormulaValueSubstitutionTest {

    @Nested
    @DisplayName("단순 변수 마커")
    inner class SimpleVariableTest {

        /**
         * 템플릿: A1="${formula}"
         * 데이터: "formula" to "=SUM(B1:B5)"
         * 기대: A1이 FORMULA 타입, cellFormula = "SUM(B1:B5)"
         */
        @Test
        @DisplayName("=로 시작하는 문자열은 수식으로 설정되어야 한다")
        fun formulaValueBecomesFormula() {
            val template = createSimpleTemplate("\${formula}")
            val data = mapOf("formula" to "=SUM(B1:B5)")

            val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
                assertEquals(CellType.FORMULA, cell.cellType, "셀 타입이 FORMULA여야 한다")
                assertEquals("SUM(B1:B5)", cell.cellFormula, "= 접두어가 제거된 수식이어야 한다")
            }
        }

        /**
         * 템플릿: A1="${value}"
         * 데이터: "value" to "일반 텍스트"
         * 기대: A1이 STRING 타입
         */
        @Test
        @DisplayName("=로 시작하지 않는 문자열은 텍스트로 유지되어야 한다")
        fun normalValueRemainsText() {
            val template = createSimpleTemplate("\${value}")
            val data = mapOf("value" to "일반 텍스트")

            val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
                assertEquals(CellType.STRING, cell.cellType)
                assertEquals("일반 텍스트", cell.stringCellValue)
            }
        }

        /**
         * 다양한 수식 패턴 테스트
         */
        @Test
        @DisplayName("다양한 수식 패턴이 올바르게 처리되어야 한다")
        fun variousFormulaPatterns() {
            val formulas = listOf(
                "=AVERAGE(A1:A10)" to "AVERAGE(A1:A10)",
                "=IF(A1>0,\"양수\",\"음수\")" to "IF(A1>0,\"양수\",\"음수\")",
                // POI가 시트명을 대문자로 정규화하므로 Sheet2 -> SHEET2
                "=VLOOKUP(A1,Sheet2!A:B,2,FALSE)" to "VLOOKUP(A1,SHEET2!A:B,2,FALSE)",
            )

            for ((input, expectedFormula) in formulas) {
                val template = createSimpleTemplate("\${formula}")
                val data = mapOf("formula" to input)

                val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

                XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                    val cell = workbook.getSheetAt(0).getRow(0).getCell(0)
                    assertEquals(CellType.FORMULA, cell.cellType, "수식 타입이어야 한다: $input")
                    assertEquals(expectedFormula, cell.cellFormula, "수식 내용: $input")
                }
            }
        }
    }

    @Nested
    @DisplayName("repeat 내 아이템 필드")
    inner class RepeatItemFieldTest {

        /**
         * 템플릿:
         * - Row 0: A=${item.name}, B=${item.formula}, C=${repeat(items, A1:B1, item)}
         *
         * 데이터: items = [{ name: "합계", formula: "=SUM(D1:D10)" }]
         * 기대: B1이 FORMULA 타입 (D열은 repeat 영역 밖이므로 범위 교차 없음)
         */
        @Test
        @DisplayName("repeat 아이템 필드에서도 =로 시작하면 수식으로 처리되어야 한다")
        fun repeatItemFormulaValue() {
            val template = XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Sheet1")
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("\${item.name}")
                    createCell(1).setCellValue("\${item.formula}")
                    createCell(2).setCellValue("\${repeat(items, A1:B1, item)}")
                }
                ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
            }

            val data = mapOf(
                "items" to listOf(
                    mapOf("name" to "합계행", "formula" to "=D1*100"),
                    mapOf("name" to "평균행", "formula" to "=D1+50")
                )
            )

            val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val sheet = workbook.getSheetAt(0)

                // 첫 번째 행 (repeatIndex=0): D1 -> 시프트 없음
                val cell0 = sheet.getRow(0).getCell(1)
                assertEquals(CellType.FORMULA, cell0.cellType, "첫 번째 행 B열이 수식이어야 한다")
                assertEquals("D1*100", cell0.cellFormula)

                // 두 번째 행 (repeatIndex=1): D1 -> D2 (영역 밖 참조 시프트)
                val cell1 = sheet.getRow(1).getCell(1)
                assertEquals(CellType.FORMULA, cell1.cellType, "두 번째 행 B열이 수식이어야 한다")
                assertEquals("D2+50", cell1.cellFormula)
            }
        }
    }

    @Nested
    @DisplayName("수식 범위 자동 조정")
    inner class FormulaRangeAdjustmentTest {

        /**
         * 템플릿 구조:
         * - Row 0: A="이름", B="점수"
         * - Row 1: A=${e.name}, B=${e.score}, C=${repeat(employees, A2:B2, e)}
         * - Row 2: A=${sumFormula} (변수형 마커에 =SUM(B2:B2) 바인딩)
         *
         * 3명 데이터 후 기대 결과:
         * - Row 0: 헤더
         * - Row 1~3: 직원 데이터
         * - Row 4: A에 =SUM(B2:B4) (범위 확장)
         */
        @Test
        @DisplayName("변수형 마커로 치환된 수식의 범위가 repeat 확장에 맞게 조정되어야 한다")
        fun formulaRangeExpandsWithRepeat() {
            val template = XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Sheet1")
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("이름")
                    createCell(1).setCellValue("점수")
                }
                sheet.createRow(1).apply {
                    createCell(0).setCellValue("\${e.name}")
                    createCell(1).setCellValue("\${e.score}")
                    createCell(2).setCellValue("\${repeat(employees, A2:B2, e)}")
                }
                sheet.createRow(2).apply {
                    createCell(0).setCellValue("\${sumFormula}")
                }
                ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
            }

            val data = mapOf(
                "employees" to listOf(
                    mapOf("name" to "김철수", "score" to 90),
                    mapOf("name" to "이영희", "score" to 85),
                    mapOf("name" to "박민수", "score" to 78)
                ),
                "sumFormula" to "=SUM(B2:B2)"
            )

            val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                // repeat 3개 아이템: Row 1~3 데이터, sumFormula는 Row 4 (0-indexed)
                val cell = sheet.getRow(4).getCell(0)
                assertEquals(CellType.FORMULA, cell.cellType, "수식 타입이어야 한다")
                assertEquals("SUM(B2:B4)", cell.cellFormula, "repeat 확장에 맞게 범위가 조정되어야 한다")
            }
        }

        /**
         * repeat 내 아이템 필드에서 수식을 바인딩하는 경우 (영역 밖 참조 시프트)
         *
         * 템플릿 구조:
         * - Row 0: A=${item.label}, B=${item.formula}, C=${repeat(items, A1:B1, item)}
         *
         * D열은 repeat 영역(A:B) 밖이므로 adjustRefsOutsideRepeat에 의해 시프트
         *
         * 3개 아이템 후 기대 결과:
         * - Row 0 (repeatIndex=0): =SUM(D1:D1) -> 시프트 없음
         * - Row 1 (repeatIndex=1): =SUM(D1:D1) -> =SUM(D2:D2) (영역 밖 참조 + repeatIndex 시프트)
         * - Row 2 (repeatIndex=2): =SUM(D1:D1) -> =SUM(D3:D3)
         */
        @Test
        @DisplayName("repeat 내 아이템 필드의 수식이 반복 인덱스에 따라 행 시프트되어야 한다")
        fun itemFieldFormulaAdjustsForRepeatIndex() {
            val template = XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Sheet1")
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("\${item.label}")
                    createCell(1).setCellValue("\${item.formula}")
                    createCell(2).setCellValue("\${repeat(items, A1:B1, item)}")
                }
                ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
            }

            val data = mapOf(
                "items" to listOf(
                    mapOf("label" to "행1", "formula" to "=D1*2"),
                    mapOf("label" to "행2", "formula" to "=D1*2"),
                    mapOf("label" to "행3", "formula" to "=D1*2")
                )
            )

            val result = TemplateRenderingEngine().process(ByteArrayInputStream(template), data)

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val sheet = workbook.getSheetAt(0)

                // Row 0 (repeatIndex=0): D1 영역 밖이지만 시프트 0
                assertEquals("D1*2", sheet.getRow(0).getCell(1).cellFormula)
                // Row 1 (repeatIndex=1): 영역 밖 참조 D1 -> D2
                assertEquals("D2*2", sheet.getRow(1).getCell(1).cellFormula)
                // Row 2 (repeatIndex=2): 영역 밖 참조 D1 -> D3
                assertEquals("D3*2", sheet.getRow(2).getCell(1).cellFormula)
            }
        }
    }

    private fun createSimpleTemplate(cellValue: String) = XSSFWorkbook().use { workbook ->
        workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue(cellValue)
        ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
    }
}
