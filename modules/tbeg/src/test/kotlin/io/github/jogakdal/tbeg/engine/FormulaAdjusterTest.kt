package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.core.CellArea
import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.rendering.FormulaAdjuster
import io.github.jogakdal.tbeg.engine.rendering.PositionCalculator
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import io.github.jogakdal.tbeg.engine.rendering.RepeatRegionSpec
import io.github.jogakdal.tbeg.exception.TemplateProcessingException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * FormulaAdjuster.adjustRefsOutsideRepeat 단위 테스트
 *
 * 시나리오: products repeat (I7:K7 = 0-based row 6, col 8~10), 4개 아이템 → 3행 확장
 * Row 8(0-based 7)이 Row 11(0-based 10)로 이동
 */
class FormulaAdjusterTest {

    private val region = RepeatRegionSpec(
        collection = "products",
        variable = "p",
        area = CellArea(6, 8, 6, 10),
        direction = RepeatDirection.DOWN
    )
    private val calculator = PositionCalculator(
        listOf(region), CollectionSizes(mapOf("products" to 4))
    ).apply { calculate() }
    private val repeatArea = region.area

    @Test
    fun `행 절대 참조가 repeat 영역 밖이면 시프트된다`() {
        // J$8 → J$11 (0-based row 7 → 10, 1-based: 11)
        assertEquals(
            "J\$11",
            FormulaAdjuster.adjustRefsOutsideRepeat("J\$8", repeatArea, calculator)
        )
    }

    @Test
    fun `행 상대 참조도 시프트된다`() {
        // J8 → J11 (영역 밖 참조는 절대/상대 무관하게 시프트)
        assertEquals(
            "J11",
            FormulaAdjuster.adjustRefsOutsideRepeat("J8", repeatArea, calculator)
        )
    }

    @Test
    fun `repeat 영역 안의 절대 참조는 건너뛴다`() {
        // J$7 → 그대로 (row-1=6 in RowRange(6,6))
        assertEquals(
            "J\$7",
            FormulaAdjuster.adjustRefsOutsideRepeat("J\$7", repeatArea, calculator)
        )
    }

    @Test
    fun `완전 절대 참조도 행이 시프트된다`() {
        // $J$8 → $J$11 (행 삽입이므로 열 절대 여부와 무관하게 행 시프트)
        assertEquals(
            "\$J\$11",
            FormulaAdjuster.adjustRefsOutsideRepeat("\$J\$8", repeatArea, calculator)
        )
    }

    @Test
    fun `범위 참조가 모두 영역 밖이면 시프트된다`() {
        // $J$8:$J$10 → $J$11:$J$13 (row 7→10, row 9→12)
        assertEquals(
            "\$J\$11:\$J\$13",
            FormulaAdjuster.adjustRefsOutsideRepeat("\$J\$8:\$J\$10", repeatArea, calculator)
        )
    }

    @Test
    fun `범위가 repeat 영역 안팎에 걸치면 에러`() {
        // J$7:J$8 → start(row-1=6)는 안, end(row-1=7)는 밖
        assertThrows(TemplateProcessingException::class.java) {
            FormulaAdjuster.adjustRefsOutsideRepeat("J\$7:J\$8", repeatArea, calculator)
        }
    }

    @Test
    fun `다른 시트 참조는 건너뛴다`() {
        assertEquals(
            "Sheet2!J\$8",
            FormulaAdjuster.adjustRefsOutsideRepeat("Sheet2!J\$8", repeatArea, calculator)
        )
    }

    @Test
    fun `복합 수식에서 영역 밖 참조가 시프트된다`() {
        // J7(영역 안→유지) / J$8(영역 밖→시프트) + SUM(C$8:C$8)(C열은 repeat colRange 밖이라 실제 위치 변화 없음)
        assertEquals(
            "J7/J\$11+SUM(C\$8:C\$8)",
            FormulaAdjuster.adjustRefsOutsideRepeat(
                "J7/J\$8+SUM(C\$8:C\$8)", repeatArea, calculator
            )
        )
    }

    @Test
    fun `상대 참조와 절대 참조가 동일하게 시프트된다`() {
        // J8(상대)과 J$8(절대) 모두 영역 밖이므로 동일하게 J11/J$11로 시프트
        val relativeResult = FormulaAdjuster.adjustRefsOutsideRepeat("J8", repeatArea, calculator)
        val absoluteResult = FormulaAdjuster.adjustRefsOutsideRepeat("J\$8", repeatArea, calculator)
        assertEquals("J11", relativeResult)
        assertEquals("J\$11", absoluteResult)
        // $기호만 다르고 행 번호는 동일
        assertEquals(
            relativeResult.filter { it.isDigit() },
            absoluteResult.filter { it.isDigit() }
        )
    }

    @Test
    fun `repeat 영역 위의 절대 참조는 변하지 않는다`() {
        // J$6 → 그대로 (row-1=5, repeat 위이므로 시프트 없음)
        assertEquals(
            "J\$6",
            FormulaAdjuster.adjustRefsOutsideRepeat("J\$6", repeatArea, calculator)
        )
    }

    @Test
    fun `repeat 영역을 감싸는 범위는 올바르게 확장된다`() {
        // J$6:J$8 → J$6(위→유지):J$11(아래→시프트)
        // 이 경우 start(row-1=5)는 밖, end(row-1=7)도 밖
        assertEquals(
            "J\$6:J\$11",
            FormulaAdjuster.adjustRefsOutsideRepeat("J\$6:J\$8", repeatArea, calculator)
        )
    }
}
