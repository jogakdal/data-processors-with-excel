package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.rendering.PositionCalculator
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import io.github.jogakdal.tbeg.engine.rendering.RepeatRegionSpec
import io.github.jogakdal.tbeg.engine.rendering.RowInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * PositionCalculator 유닛 테스트
 *
 * 특히 getTotalRows(), getRowInfo() 메서드를 검증합니다.
 */
@DisplayName("PositionCalculator 테스트")
class PositionCalculatorTest {

    @Nested
    @DisplayName("getTotalRows() 테스트")
    inner class GetTotalRowsTest {

        @Test
        @DisplayName("repeat 영역이 없을 때 0을 반환한다")
        fun noRepeatRegions() {
            val calculator = PositionCalculator(
                repeatRegions = emptyList(),
                collectionSizes = emptyMap()
            )
            calculator.calculate()

            assertEquals(1, calculator.getTotalRows())
        }

        @Test
        @DisplayName("단일 DOWN repeat 영역의 총 행 수를 계산한다")
        fun singleDownRepeat() {
            // 템플릿: 5-6행에 2행짜리 repeat (0-indexed: 4-5)
            // 아이템 3개 -> 2행 × 3개 = 6행
            // 마지막 행: 5 + (3-1)*2 = 9 -> 총 10행 (0-9)
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)
            )
            calculator.calculate()

            // 템플릿 마지막 행(5) + 확장량(4) + 1 = 10
            assertEquals(10, calculator.getTotalRows())
        }

        @Test
        @DisplayName("아이템이 1개일 때 확장이 없다")
        fun singleItem() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 1)
            )
            calculator.calculate()

            // 아이템 1개 -> 확장 없음
            // 템플릿 마지막 행(5) + 확장량(0) + 1 = 6
            assertEquals(6, calculator.getTotalRows())
        }

        @Test
        @DisplayName("아이템이 0개일 때 확장이 없다")
        fun zeroItems() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 0)
            )
            calculator.calculate()

            // 아이템 0개 -> 확장 없음
            assertEquals(6, calculator.getTotalRows())
        }
    }

    @Nested
    @DisplayName("getRowInfo() 테스트")
    inner class GetRowInfoTest {

        @Test
        @DisplayName("정적 행은 Static으로 반환된다")
        fun staticRow() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,  // 5행 (1-indexed)
                    endRow = 5,    // 6행 (1-indexed)
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)
            )
            calculator.calculate()

            // 0-3행은 repeat 영역 앞의 정적 행
            for (row in 0..3) {
                val rowInfo = calculator.getRowInfo(row)
                assertTrue(rowInfo is RowInfo.Static, "행 $row 는 정적 행이어야 함")
                assertEquals(row, (rowInfo as RowInfo.Static).templateRowIndex)
            }
        }

        @Test
        @DisplayName("반복 행은 Repeat으로 반환되고 올바른 itemIndex와 templateRowOffset을 가진다")
        fun repeatRow() {
            // 템플릿 4-5행 (2행짜리 repeat), 아이템 3개
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)
            )
            calculator.calculate()

            // 실제 행 4: 첫 번째 아이템, 첫 번째 템플릿 행
            val row4 = calculator.getRowInfo(4)
            assertTrue(row4 is RowInfo.Repeat)
            assertEquals(0, (row4 as RowInfo.Repeat).itemIndex)
            assertEquals(0, row4.templateRowOffset)

            // 실제 행 5: 첫 번째 아이템, 두 번째 템플릿 행
            val row5 = calculator.getRowInfo(5)
            assertTrue(row5 is RowInfo.Repeat)
            assertEquals(0, (row5 as RowInfo.Repeat).itemIndex)
            assertEquals(1, row5.templateRowOffset)

            // 실제 행 6: 두 번째 아이템, 첫 번째 템플릿 행
            val row6 = calculator.getRowInfo(6)
            assertTrue(row6 is RowInfo.Repeat)
            assertEquals(1, (row6 as RowInfo.Repeat).itemIndex)
            assertEquals(0, row6.templateRowOffset)

            // 실제 행 7: 두 번째 아이템, 두 번째 템플릿 행
            val row7 = calculator.getRowInfo(7)
            assertTrue(row7 is RowInfo.Repeat)
            assertEquals(1, (row7 as RowInfo.Repeat).itemIndex)
            assertEquals(1, row7.templateRowOffset)

            // 실제 행 8: 세 번째 아이템, 첫 번째 템플릿 행
            val row8 = calculator.getRowInfo(8)
            assertTrue(row8 is RowInfo.Repeat)
            assertEquals(2, (row8 as RowInfo.Repeat).itemIndex)
            assertEquals(0, row8.templateRowOffset)

            // 실제 행 9: 세 번째 아이템, 두 번째 템플릿 행
            val row9 = calculator.getRowInfo(9)
            assertTrue(row9 is RowInfo.Repeat)
            assertEquals(2, (row9 as RowInfo.Repeat).itemIndex)
            assertEquals(1, row9.templateRowOffset)
        }

        @Test
        @DisplayName("반복 영역 이후의 정적 행은 올바른 템플릿 행 인덱스를 가진다")
        fun staticRowAfterRepeat() {
            // 템플릿: 행 4-5가 repeat, 행 6-7은 정적
            // 아이템 3개 -> 확장량 4행
            // 실제 행 10-11은 템플릿 행 6-7에 해당
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)
            )
            calculator.calculate()

            // 실제 행 10은 템플릿 행 6에 해당 (repeat 이후)
            val row10 = calculator.getRowInfo(10)
            assertTrue(row10 is RowInfo.Static, "행 10은 정적 행이어야 함")
            assertEquals(6, (row10 as RowInfo.Static).templateRowIndex)
        }

        @Test
        @DisplayName("단일 행 repeat에서 올바른 정보를 반환한다")
        fun singleRowRepeat() {
            // 템플릿 5행만 repeat (1행짜리)
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 5,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 4)
            )
            calculator.calculate()

            // 실제 행 5-8은 각각 아이템 0-3에 해당
            for (itemIdx in 0..3) {
                val row = calculator.getRowInfo(5 + itemIdx)
                assertTrue(row is RowInfo.Repeat)
                assertEquals(itemIdx, (row as RowInfo.Repeat).itemIndex)
                assertEquals(0, row.templateRowOffset) // 단일 행이므로 항상 0
            }
        }
    }

    @Nested
    @DisplayName("다중 repeat 영역 테스트")
    inner class MultipleRepeatRegionsTest {

        @Test
        @DisplayName("두 개의 연속된 DOWN repeat 영역을 올바르게 처리한다")
        fun twoConsecutiveDownRepeats() {
            // 템플릿 행 2-3: 첫 번째 repeat (employees)
            // 템플릿 행 5-6: 두 번째 repeat (departments)
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "employees",
                    variable = "emp",
                    startRow = 2,
                    endRow = 3,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                ),
                RepeatRegionSpec(
                    collection = "departments",
                    variable = "dept",
                    startRow = 5,
                    endRow = 6,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf(
                    "employees" to 2,    // 2행 × 2 = 4행 (확장량 2)
                    "departments" to 3   // 2행 × 3 = 6행 (확장량 4)
                )
            )
            calculator.calculate()

            // 첫 번째 repeat (행 2-5): employees
            val row2 = calculator.getRowInfo(2)
            assertTrue(row2 is RowInfo.Repeat)
            assertEquals("employees", (row2 as RowInfo.Repeat).repeatRegion.collection)
            assertEquals(0, row2.itemIndex)

            val row4 = calculator.getRowInfo(4)
            assertTrue(row4 is RowInfo.Repeat)
            assertEquals("employees", (row4 as RowInfo.Repeat).repeatRegion.collection)
            assertEquals(1, row4.itemIndex)

            // 행 6은 정적 (템플릿 행 4)
            val row6 = calculator.getRowInfo(6)
            assertTrue(row6 is RowInfo.Static)

            // 두 번째 repeat (행 7-12): departments
            // 첫 번째 repeat의 확장으로 2행 밀림: 5+2=7
            val row7 = calculator.getRowInfo(7)
            assertTrue(row7 is RowInfo.Repeat)
            assertEquals("departments", (row7 as RowInfo.Repeat).repeatRegion.collection)
            assertEquals(0, row7.itemIndex)
        }
    }

    @Nested
    @DisplayName("getFinalPosition() 테스트")
    inner class GetFinalPositionTest {

        @Test
        @DisplayName("repeat 영역 이후의 행이 올바르게 밀린다")
        fun rowsAfterRepeatAreShifted() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)  // 확장량: (3-1) × 2 = 4
            )
            calculator.calculate()

            // 템플릿 행 6 (repeat 이후) -> 실제 행 10 (6 + 4)
            val (actualRow, actualCol) = calculator.getFinalPosition(6, 0)
            assertEquals(10, actualRow)
            assertEquals(0, actualCol)

            // 템플릿 행 10 -> 실제 행 14
            val (actualRow2, _) = calculator.getFinalPosition(10, 0)
            assertEquals(14, actualRow2)
        }

        @Test
        @DisplayName("repeat 영역 이전의 행은 변경되지 않는다")
        fun rowsBeforeRepeatUnchanged() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    startRow = 4,
                    endRow = 5,
                    startCol = 0,
                    endCol = 2,
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = mapOf("items" to 3)
            )
            calculator.calculate()

            // 템플릿 행 0-3은 변경 없음
            for (row in 0..3) {
                val (actualRow, _) = calculator.getFinalPosition(row, 0)
                assertEquals(row, actualRow)
            }
        }
    }
}
