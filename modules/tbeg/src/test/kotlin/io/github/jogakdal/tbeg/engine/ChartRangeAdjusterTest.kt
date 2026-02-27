package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.core.toColumnIndex
import io.github.jogakdal.tbeg.engine.core.toColumnLetter
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster.RepeatExpansionInfo
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ChartRangeAdjusterTest {

    // ========== 헬퍼 ==========

    private fun downExpansion(
        startRow: Int, endRow: Int, itemCount: Int,
        startCol: Int = 0, endCol: Int = 10
    ) = RepeatExpansionInfo(
        templateStartRow = startRow,
        templateEndRow = endRow,
        templateStartCol = startCol,
        templateEndCol = endCol,
        itemCount = itemCount,
        direction = RepeatDirection.DOWN
    )

    private fun rightExpansion(
        startRow: Int, endRow: Int, startCol: Int, endCol: Int, itemCount: Int
    ) = RepeatExpansionInfo(
        templateStartRow = startRow,
        templateEndRow = endRow,
        templateStartCol = startCol,
        templateEndCol = endCol,
        itemCount = itemCount,
        direction = RepeatDirection.RIGHT
    )

    // ========== adjustFormula 테스트 ==========

    @Nested
    inner class AdjustFormula {

        @Test
        fun `단일 repeat, 범위 끝이 repeat 영역 안에 있으면 확장한다`() {
            // repeat: rows 6-6 (0-based), 5개 아이템
            // 차트 수식: Sheet1!$A$7:$A$7 (1-based) → 범위 끝 7은 7..7 안에 있음
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$7:\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            // 확장: 7 + (5*1) - 1 = 11
            assertEquals("Sheet1!\$A\$7:\$A\$11", result)
        }

        @Test
        fun `단일 repeat, 범위가 repeat 영역 전체를 포함하면 끝만 확장한다`() {
            // repeat: rows 6-8 (0-based, 3행), 4개 아이템
            // 차트 수식: Sheet1!$B$5:$B$9 (1-based, rows 5-9) → 끝 행 9는 7..9 안에 있음
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$B\$5:\$B\$9",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 8, itemCount = 4))
            )
            // 시작: 5 (repeat 영역 앞이므로 유지)
            // 끝: 7 + (4*3) - 1 = 18
            assertEquals("Sheet1!\$B\$5:\$B\$18", result)
        }

        @Test
        fun `범위가 repeat 영역 뒤에 있으면 시프트한다`() {
            // repeat: rows 6-6 (0-based), 5개 아이템 → expansionAmount = 4
            // 차트 수식: Sheet1!$A$10:$A$15 (1-based) → 둘 다 repeat 뒤
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$10:\$A\$15",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            // 시작: 10 + 4 = 14, 끝: 15 + 4 = 19
            assertEquals("Sheet1!\$A\$14:\$A\$19", result)
        }

        @Test
        fun `범위가 repeat 영역 앞에 있으면 변경하지 않는다`() {
            // repeat: rows 10-10 (0-based), 3개 아이템
            // 차트 수식: Sheet1!$A$1:$A$5 → 둘 다 repeat 앞
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$1:\$A\$5",
                "Sheet1",
                listOf(downExpansion(startRow = 10, endRow = 10, itemCount = 3))
            )
            assertEquals("Sheet1!\$A\$1:\$A\$5", result)
        }

        @Test
        fun `다른 시트 참조는 조정하지 않는다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet2!\$A\$7:\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            assertEquals("Sheet2!\$A\$7:\$A\$7", result)
        }

        @Test
        fun `시트 참조 없는 수식도 같은 시트로 취급하여 조정한다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "\$A\$7:\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            assertEquals("\$A\$7:\$A\$11", result)
        }

        @Test
        fun `다중 repeat, 누적 오프셋이 적용된다`() {
            // repeat1: rows 3-3 (0-based), 3개 아이템 → expansion = 2
            // repeat2: rows 6-6 (0-based), 4개 아이템 → expansion = 3
            // 차트 수식: Sheet1!$A$8:$A$8 → repeat2 끝 안에 있음
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$8:\$A\$12",
                "Sheet1",
                listOf(
                    downExpansion(startRow = 3, endRow = 3, itemCount = 3),
                    downExpansion(startRow = 6, endRow = 6, itemCount = 4)
                )
            )
            // repeat1 후: 시작 8 > 4(templateEndRow1=4) → start = 8 + 2 = 10
            //              끝 12 > 4 → end = 12 + 2 = 14
            // repeat2 후: 시작 8 > 7(templateEndRow1=7) → start = 8 + 2 + 3 = 13
            //              끝 12 > 7 → end = 12 + 2 + 3 = 17
            assertEquals("Sheet1!\$A\$13:\$A\$17", result)
        }

        @Test
        fun `다중 시리즈 수식이 모두 조정된다`() {
            // repeat: rows 6-6 (0-based), 5개 아이템
            val expansions = listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))

            // category 참조
            val catResult = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$7:\$A\$7", "Sheet1", expansions
            )
            assertEquals("Sheet1!\$A\$7:\$A\$11", catResult)

            // value 참조
            val valResult = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$B\$7:\$B\$7", "Sheet1", expansions
            )
            assertEquals("Sheet1!\$B\$7:\$B\$11", valResult)
        }

        @Test
        fun `작은따옴표로 감싼 시트 이름도 처리한다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "'My Sheet'!\$A\$7:\$A\$7",
                "My Sheet",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            assertEquals("'My Sheet'!\$A\$7:\$A\$11", result)
        }

        @Test
        fun `아이템이 1개이면 변경하지 않는다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$7:\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 1))
            )
            assertEquals("Sheet1!\$A\$7:\$A\$7", result)
        }

        @Test
        fun `단일 셀 참조가 repeat 영역 안에 있으면 범위로 확장한다`() {
            // repeat: rows 6-6 (0-based), 5개 아이템
            // 차트 수식: Sheet1!$A$7 (단일 셀, 1-based)
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            assertEquals("Sheet1!\$A\$7:\$A\$11", result)
        }

        @Test
        fun `단일 셀 참조가 repeat 영역 밖이면 변경하지 않는다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$5",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )
            assertEquals("Sheet1!\$A\$5", result)
        }

        @Test
        fun `단일 셀 참조 아이템이 1개이면 변경하지 않는다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$7",
                "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 1))
            )
            assertEquals("Sheet1!\$A\$7", result)
        }

        @Test
        fun `다중 행 템플릿 repeat도 정확히 확장한다`() {
            // repeat: rows 5-7 (0-based, 3행), 3개 아이템
            // 차트 수식: Sheet1!$A$6:$A$8 (1-based, rows 6-8)
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$A\$6:\$A\$8",
                "Sheet1",
                listOf(downExpansion(startRow = 5, endRow = 7, itemCount = 3))
            )
            // 끝 행 8은 6..8 안에 있음 → 확장: 6 + (3*3) - 1 = 14
            assertEquals("Sheet1!\$A\$6:\$A\$14", result)
        }
    }

    // ========== adjustChartXml 테스트 ==========

    @Nested
    inner class AdjustChartXml {

        @Test
        fun `차트 XML 내 여러 수식이 모두 조정된다`() {
            val xml = """
                <c:chart>
                  <c:ser>
                    <c:cat><c:strRef><c:f>Sheet1!${'$'}A${'$'}7:${'$'}A${'$'}7</c:f></c:strRef></c:cat>
                    <c:val><c:numRef><c:f>Sheet1!${'$'}B${'$'}7:${'$'}B${'$'}7</c:f></c:numRef></c:val>
                  </c:ser>
                  <c:ser>
                    <c:val><c:numRef><c:f>Sheet1!${'$'}C${'$'}7:${'$'}C${'$'}7</c:f></c:numRef></c:val>
                  </c:ser>
                </c:chart>
            """.trimIndent()

            val result = ChartRangeAdjuster.adjustChartXml(
                xml, "Sheet1",
                listOf(downExpansion(startRow = 6, endRow = 6, itemCount = 5))
            )

            assert(result.contains("Sheet1!\$A\$7:\$A\$11"))
            assert(result.contains("Sheet1!\$B\$7:\$B\$11"))
            assert(result.contains("Sheet1!\$C\$7:\$C\$11"))
        }

        @Test
        fun `확장 정보가 비어있으면 원본 반환`() {
            val xml = "<c:f>Sheet1!\$A\$1:\$A\$5</c:f>"
            assertEquals(xml, ChartRangeAdjuster.adjustChartXml(xml, "Sheet1", emptyList()))
        }
    }

    // ========== RIGHT 방향 테스트 ==========

    @Nested
    inner class RightDirection {

        @Test
        fun `RIGHT 방향 repeat 시 열 범위가 확장된다`() {
            // repeat: cols 1-2 (B-C, 0-based), rows 5-5, 3개 아이템
            // 차트 수식: Sheet1!$B$6:$C$6 → 끝 열 C(2)가 1..2 안에 있음
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$B\$6:\$C\$6",
                "Sheet1",
                listOf(rightExpansion(startRow = 5, endRow = 5, startCol = 1, endCol = 2, itemCount = 3))
            )
            // 끝 열: 1 + (3*2) - 1 = 6 → G
            assertEquals("Sheet1!\$B\$6:\$G\$6", result)
        }

        @Test
        fun `RIGHT 방향, 행 범위가 repeat과 겹치지 않으면 변경하지 않는다`() {
            val result = ChartRangeAdjuster.adjustFormula(
                "Sheet1!\$B\$1:\$C\$3",
                "Sheet1",
                listOf(rightExpansion(startRow = 5, endRow = 5, startCol = 1, endCol = 2, itemCount = 3))
            )
            assertEquals("Sheet1!\$B\$1:\$C\$3", result)
        }
    }

    // ========== shiftRow 테스트 ==========

    /** 모든 열과 겹치는 전체 범위 (기존 동작 호환) */
    private val fullColRange = 0..Int.MAX_VALUE

    @Nested
    inner class ShiftRow {

        @Test
        fun `repeat 영역 뒤의 행이 시프트된다`() {
            // repeat: rows 3-5 (0-based, 3행), 4개 아이템 → expansion = 9
            // row 10 > 5(templateEndRow) → 시프트
            assertEquals(19, ChartRangeAdjuster.shiftRow(
                10, fullColRange, listOf(downExpansion(startRow = 3, endRow = 5, itemCount = 4))
            ))
        }

        @Test
        fun `repeat 영역 앞의 행은 변경되지 않는다`() {
            assertEquals(2, ChartRangeAdjuster.shiftRow(
                2, fullColRange, listOf(downExpansion(startRow = 3, endRow = 5, itemCount = 4))
            ))
        }

        @Test
        fun `repeat 영역 경계(templateEndRow)의 행은 변경되지 않는다`() {
            // row == templateEndRow → 시프트 안 됨 (repeat 영역 끝은 아직 포함)
            assertEquals(5, ChartRangeAdjuster.shiftRow(
                5, fullColRange, listOf(downExpansion(startRow = 3, endRow = 5, itemCount = 4))
            ))
        }

        @Test
        fun `다중 repeat 누적 오프셋`() {
            // repeat1: rows 2-2, 3개 → expansion = 2
            // repeat2: rows 5-5, 4개 → expansion = 3
            // row 10 > 2 → +2, row 10 > 5 → +3, total = +5
            assertEquals(15, ChartRangeAdjuster.shiftRow(
                10, fullColRange, listOf(
                    downExpansion(startRow = 2, endRow = 2, itemCount = 3),
                    downExpansion(startRow = 5, endRow = 5, itemCount = 4)
                )
            ))
        }

        @Test
        fun `아이템 1개면 오프셋 없음`() {
            assertEquals(10, ChartRangeAdjuster.shiftRow(
                10, fullColRange, listOf(downExpansion(startRow = 3, endRow = 5, itemCount = 1))
            ))
        }

        @Test
        fun `빈 expansion 리스트면 원래 값 반환`() {
            assertEquals(10, ChartRangeAdjuster.shiftRow(10, fullColRange, emptyList()))
        }
    }

    // ========== shiftRow 열 범위 필터링 테스트 ==========

    @Nested
    inner class ShiftRowWithColRange {

        @Test
        fun `멀티 리피트에서 열 범위에 해당하는 repeat만 적용된다`() {
            // depts: B~G (cols 1-6), 5개 아이템 → 4행 확장
            // products: I~K (cols 8-10), 4개 아이템 → 3행 확장
            val expansions = listOf(
                downExpansion(startRow = 5, endRow = 5, itemCount = 5, startCol = 1, endCol = 6),
                downExpansion(startRow = 5, endRow = 5, itemCount = 4, startCol = 8, endCol = 10)
            )

            // 바 차트 앵커 (B~G 영역, cols 1-6) → depts의 4행 확장만 적용
            assertEquals(14, ChartRangeAdjuster.shiftRow(10, 1..6, expansions))

            // 파이 차트 앵커 (I~K 영역, cols 8-10) → products의 3행 확장만 적용
            assertEquals(13, ChartRangeAdjuster.shiftRow(10, 8..10, expansions))

            // 전체 범위 앵커 (B~K, cols 1-10) → max(4, 3) = 4행 시프트
            assertEquals(14, ChartRangeAdjuster.shiftRow(10, 1..10, expansions))
        }

        @Test
        fun `단일 repeat이면 열 범위와 겹치기만 하면 동일 시프트`() {
            val expansions = listOf(
                downExpansion(startRow = 5, endRow = 5, itemCount = 5, startCol = 1, endCol = 6)
            )

            // repeat 영역 내 앵커
            assertEquals(14, ChartRangeAdjuster.shiftRow(10, 1..6, expansions))
            // 부분 겹침
            assertEquals(14, ChartRangeAdjuster.shiftRow(10, 3..8, expansions))
            // 한 열만 겹침
            assertEquals(14, ChartRangeAdjuster.shiftRow(10, 6..10, expansions))
        }

        @Test
        fun `열 범위가 겹치지 않는 앵커는 시프트되지 않는다`() {
            val expansions = listOf(
                downExpansion(startRow = 5, endRow = 5, itemCount = 5, startCol = 1, endCol = 6)
            )

            // repeat 영역(1-6) 밖의 앵커(cols 8-10)
            assertEquals(10, ChartRangeAdjuster.shiftRow(10, 8..10, expansions))
        }
    }

    // ========== shiftCol 테스트 ==========

    @Nested
    inner class ShiftCol {

        @Test
        fun `RIGHT 방향 repeat 뒤의 열이 시프트된다`() {
            // repeat: cols 1-2, rows 3-3, 3개 → expansion = 4
            // col 5 > 2(templateEndCol) + 행 범위 겹침 → 시프트
            assertEquals(9, ChartRangeAdjuster.shiftCol(
                5, 3..10,
                listOf(rightExpansion(startRow = 3, endRow = 3, startCol = 1, endCol = 2, itemCount = 3))
            ))
        }

        @Test
        fun `행 범위가 repeat 영역과 겹치지 않으면 변경 없음`() {
            assertEquals(5, ChartRangeAdjuster.shiftCol(
                5, 10..20,
                listOf(rightExpansion(startRow = 3, endRow = 3, startCol = 1, endCol = 2, itemCount = 3))
            ))
        }

        @Test
        fun `repeat 영역 앞의 열은 변경되지 않는다`() {
            assertEquals(0, ChartRangeAdjuster.shiftCol(
                0, 3..10,
                listOf(rightExpansion(startRow = 3, endRow = 3, startCol = 1, endCol = 2, itemCount = 3))
            ))
        }
    }

    // ========== adjustAnchorsInSheet 테스트 ==========

    @Nested
    inner class AdjustAnchorsInSheet {

        @Test
        fun `차트 앵커 행이 repeat 확장에 따라 시프트된다`() {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Sheet1")

            // 차트 생성: anchor from row 10, to row 25
            val drawing = sheet.createDrawingPatriarch()
            val anchor = drawing.createAnchor(0, 0, 0, 0, 0, 10, 10, 25)
            val chart = drawing.createChart(anchor)

            // 빈 차트 데이터 plot (최소 유효 차트)
            val catAxis = chart.createCategoryAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.BOTTOM)
            val valAxis = chart.createValueAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.LEFT)
            val data = chart.createData(
                org.apache.poi.xddf.usermodel.chart.ChartTypes.BAR, catAxis, valAxis
            )
            chart.plot(data)

            // repeat: rows 2-5 (0-based), 5개 아이템 → expansion = (5-1)*4 = 16
            val expansions = listOf(downExpansion(startRow = 2, endRow = 5, itemCount = 5))

            ChartRangeAdjuster.adjustAnchorsInSheet(sheet, expansions)

            // anchor row1: 10 > 5 → 10 + 16 = 26
            // anchor row2: 25 > 5 → 25 + 16 = 41
            val resultAnchor = drawing.first().anchor as org.apache.poi.xssf.usermodel.XSSFClientAnchor
            assertEquals(26, resultAnchor.row1, "anchor row1이 시프트되어야 한다")
            assertEquals(41, resultAnchor.row2, "anchor row2가 시프트되어야 한다")

            workbook.close()
        }

        @Test
        fun `repeat 영역 앞의 앵커는 변경되지 않는다`() {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Sheet1")

            val drawing = sheet.createDrawingPatriarch()
            val anchor = drawing.createAnchor(0, 0, 0, 0, 0, 0, 5, 3)
            val chart = drawing.createChart(anchor)
            val catAxis = chart.createCategoryAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.BOTTOM)
            val valAxis = chart.createValueAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.LEFT)
            chart.plot(chart.createData(org.apache.poi.xddf.usermodel.chart.ChartTypes.BAR, catAxis, valAxis))

            // repeat: rows 10-12
            val expansions = listOf(downExpansion(startRow = 10, endRow = 12, itemCount = 5))
            ChartRangeAdjuster.adjustAnchorsInSheet(sheet, expansions)

            val resultAnchor = drawing.first().anchor as org.apache.poi.xssf.usermodel.XSSFClientAnchor
            assertEquals(0, resultAnchor.row1)
            assertEquals(3, resultAnchor.row2)

            workbook.close()
        }
    }

    // ========== ExcelUtils 열 인덱스 변환 확인 (ChartRangeAdjuster가 사용하는 함수) ==========

    @Nested
    inner class ColIndexConversion {

        @Test
        fun `toColumnIndex 변환`() {
            assertEquals(0, "A".toColumnIndex())
            assertEquals(1, "B".toColumnIndex())
            assertEquals(25, "Z".toColumnIndex())
            assertEquals(26, "AA".toColumnIndex())
            assertEquals(27, "AB".toColumnIndex())
        }

        @Test
        fun `toColumnLetter 변환`() {
            assertEquals("A", 0.toColumnLetter())
            assertEquals("B", 1.toColumnLetter())
            assertEquals("Z", 25.toColumnLetter())
            assertEquals("AA", 26.toColumnLetter())
            assertEquals("AB", 27.toColumnLetter())
        }

        @Test
        fun `왕복 변환 일관성`() {
            for (i in 0..100) {
                assertEquals(i, i.toColumnLetter().toColumnIndex())
            }
        }
    }
}
