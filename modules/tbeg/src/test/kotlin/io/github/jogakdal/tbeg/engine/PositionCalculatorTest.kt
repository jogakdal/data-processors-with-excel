package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.core.CellCoord
import io.github.jogakdal.tbeg.engine.core.CellArea
import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.rendering.*
import org.apache.poi.ss.util.CellRangeAddress
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * PositionCalculator 유닛 테스트
 *
 * 체이닝 기반 밀려남 알고리즘을 검증합니다.
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
                collectionSizes = CollectionSizes.EMPTY
            )
            calculator.calculate()

            assertEquals(1, calculator.getTotalRows())
        }

        @Test
        @DisplayName("단일 DOWN repeat 영역의 총 행 수를 계산한다")
        fun singleDownRepeat() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
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
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 1)
            )
            calculator.calculate()

            assertEquals(6, calculator.getTotalRows())
        }

        @Test
        @DisplayName("아이템이 0개일 때 확장이 없다")
        fun zeroItems() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 0)
            )
            calculator.calculate()

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
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
            )
            calculator.calculate()

            for (row in 0..3) {
                val rowInfo = calculator.getRowInfo(row)
                assertTrue(rowInfo is RowInfo.Static, "행 $row 는 정적 행이어야 함")
                assertEquals(row, (rowInfo as RowInfo.Static).templateRowIndex)
            }
        }

        @Test
        @DisplayName("반복 행은 Repeat으로 반환되고 올바른 itemIndex와 templateRowOffset을 가진다")
        fun repeatRow() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
            )
            calculator.calculate()

            val row4 = calculator.getRowInfo(4)
            assertTrue(row4 is RowInfo.Repeat)
            assertEquals(0, (row4 as RowInfo.Repeat).itemIndex)
            assertEquals(0, row4.templateRowOffset)

            val row5 = calculator.getRowInfo(5)
            assertTrue(row5 is RowInfo.Repeat)
            assertEquals(0, (row5 as RowInfo.Repeat).itemIndex)
            assertEquals(1, row5.templateRowOffset)

            val row6 = calculator.getRowInfo(6)
            assertTrue(row6 is RowInfo.Repeat)
            assertEquals(1, (row6 as RowInfo.Repeat).itemIndex)
            assertEquals(0, row6.templateRowOffset)

            val row9 = calculator.getRowInfo(9)
            assertTrue(row9 is RowInfo.Repeat)
            assertEquals(2, (row9 as RowInfo.Repeat).itemIndex)
            assertEquals(1, row9.templateRowOffset)
        }

        @Test
        @DisplayName("반복 영역 이후의 정적 행은 올바른 템플릿 행 인덱스를 가진다")
        fun staticRowAfterRepeat() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
            )
            calculator.calculate()

            val row10 = calculator.getRowInfo(10)
            assertTrue(row10 is RowInfo.Static, "행 10은 정적 행이어야 함")
            assertEquals(6, (row10 as RowInfo.Static).templateRowIndex)
        }

        @Test
        @DisplayName("단일 행 repeat에서 올바른 정보를 반환한다")
        fun singleRowRepeat() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "items",
                    variable = "item",
                    area = CellArea(CellCoord(5, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 4)
            )
            calculator.calculate()

            for (itemIdx in 0..3) {
                val row = calculator.getRowInfo(5 + itemIdx)
                assertTrue(row is RowInfo.Repeat)
                assertEquals(itemIdx, (row as RowInfo.Repeat).itemIndex)
                assertEquals(0, row.templateRowOffset)
            }
        }
    }

    @Nested
    @DisplayName("다중 repeat 영역 테스트")
    inner class MultipleRepeatRegionsTest {

        @Test
        @DisplayName("두 개의 연속된 DOWN repeat 영역을 올바르게 처리한다")
        fun twoConsecutiveDownRepeats() {
            val regions = listOf(
                RepeatRegionSpec(
                    collection = "employees",
                    variable = "emp",
                    area = CellArea(CellCoord(2, 0), CellCoord(3, 2)),
                    direction = RepeatDirection.DOWN
                ),
                RepeatRegionSpec(
                    collection = "departments",
                    variable = "dept",
                    area = CellArea(CellCoord(5, 0), CellCoord(6, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of(
                    "employees" to 2,
                    "departments" to 3
                )
            )
            calculator.calculate()

            val row2 = calculator.getRowInfo(2)
            assertTrue(row2 is RowInfo.Repeat)
            assertEquals("employees", (row2 as RowInfo.Repeat).repeatRegion.collection)
            assertEquals(0, row2.itemIndex)

            val row4 = calculator.getRowInfo(4)
            assertTrue(row4 is RowInfo.Repeat)
            assertEquals("employees", (row4 as RowInfo.Repeat).repeatRegion.collection)
            assertEquals(1, row4.itemIndex)

            val row6 = calculator.getRowInfo(6)
            assertTrue(row6 is RowInfo.Static)

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
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
            )
            calculator.calculate()

            // 템플릿 행 6 -> 실제 행 10 (6 + 4)
            val (actualRow, actualCol) = calculator.getFinalPosition(6, 0)
            assertEquals(10, actualRow)
            assertEquals(0, actualCol)

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
                    area = CellArea(CellCoord(4, 0), CellCoord(5, 2)),
                    direction = RepeatDirection.DOWN
                )
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3)
            )
            calculator.calculate()

            for (row in 0..3) {
                val (actualRow, _) = calculator.getFinalPosition(row, 0)
                assertEquals(row, actualRow)
            }
        }
    }

    @Nested
    @DisplayName("체이닝 기반 밀려남 테스트")
    inner class ChainingTest {

        /**
         * Rich Sample과 유사한 레이아웃:
         * - depts: row 6, cols 1-6, 5개 -> rowExpansion = 4
         * - products: row 6, cols 8-10, 4개 -> rowExpansion = 3
         * - employees: row 30, cols 1-10, 11개 -> rowExpansion = 10
         */
        private fun createRichSampleRegions() = listOf(
            RepeatRegionSpec("depts", "dept", CellArea(CellCoord(6, 1), CellCoord(6, 6)), RepeatDirection.DOWN),
            RepeatRegionSpec("products", "prod", CellArea(CellCoord(6, 8), CellCoord(6, 10)), RepeatDirection.DOWN),
            RepeatRegionSpec("employees", "emp", CellArea(CellCoord(30, 1), CellCoord(30, 10)), RepeatDirection.DOWN)
        )

        private val sizes = CollectionSizes.of("depts" to 5, "products" to 4, "employees" to 11)

        @Test
        @DisplayName("같은 행의 repeat들이 MAX로 처리된다")
        fun sameRowRepeatsUseMax() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes
            )
            calculator.calculate()

            // employees의 finalStartRow: max(depts +4, products +3) = 4 -> 30 + 4 = 34
            val employeesExpansion = calculator.getExpansionFor("employees")!!
            assertEquals(34, employeesExpansion.finalStartRow)
        }

        @Test
        @DisplayName("gap 열의 정적 셀은 밀리지 않는다 (병합 셀 없을 때)")
        fun gapColumnNotShifted() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            // 행 7 (repeat 바로 아래)
            // depts 열(col 1): 11 (7 + 4)
            assertEquals(11, calculator.getFinalPosition(7, 1).row, "depts 열")
            // products 열(col 8): 10 (7 + 3)
            assertEquals(10, calculator.getFinalPosition(7, 8).row, "products 열")
            // gap 열(col 7): 7 (밀리지 않음!)
            assertEquals(7, calculator.getFinalPosition(7, 7).row, "gap 열은 밀리지 않음")
        }

        @Test
        @DisplayName("병합 셀을 통한 교차 열 밀림 전파")
        fun mergedCellPropagation() {
            // 행 7에 A:K 병합 셀 추가 (col 0-10)
            val mergedRegions = listOf(
                CellRangeAddress(7, 7, 0, 10)
            )

            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35,
                mergedRegions = mergedRegions
            )
            calculator.calculate()

            // 병합 셀이 모든 열을 커버하므로 MAX(depts 4, products 3) = 4
            // 병합 셀의 최종 위치 = 7 + 4 = 11
            // 모든 열에서 동일
            val posCol1 = calculator.getFinalPosition(7, 1)
            val posCol7 = calculator.getFinalPosition(7, 7)
            val posCol8 = calculator.getFinalPosition(7, 8)

            assertEquals(11, posCol1.row, "depts 열 (병합 셀 전파)")
            assertEquals(11, posCol7.row, "gap 열 (병합 셀 전파)")
            assertEquals(11, posCol8.row, "products 열 (병합 셀 전파)")
        }

        @Test
        @DisplayName("전체 폭 repeat 아래 정적 행은 모든 열에서 동일하게 밀린다")
        fun fullWidthRepeatBelowUniform() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            // employees(col 1-10, row 30) 아래의 정적 행(row 31)
            // employees는 모든 열을 커버하므로 모든 열에서 동일
            val posCol1 = calculator.getFinalPosition(31, 1)
            val posCol7 = calculator.getFinalPosition(31, 7)
            val posCol8 = calculator.getFinalPosition(31, 8)

            assertEquals(posCol1.row, posCol7.row, "employees 아래 gap 열")
            assertEquals(posCol1.row, posCol8.row, "employees 아래 products 열")
            assertEquals(45, posCol1.row, "employees 아래 행 위치")
        }

        @Test
        @DisplayName("getTotalRows가 MAX 기반으로 계산된다")
        fun totalRowsUsesMax() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            // templateLastRow(35) + max(4,3) + 10 + 1 = 50
            assertEquals(50, calculator.getTotalRows())
        }

        @Test
        @DisplayName("dead zone에서 shorter repeat 열은 정적 행으로 역변환된다")
        fun deadZoneReturnsStaticForShorterRepeatColumn() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            val info = calculator.getRowInfoForColumn(10, 8)
            assertTrue(info is RowInfo.Static, "dead zone의 shorter repeat 열은 정적 행")
            assertEquals(7, (info as RowInfo.Static).templateRowIndex, "products Total 행으로 역변환")
        }

        @Test
        @DisplayName("dead zone 다음 행에서 중복 Total이 발생하지 않는다")
        fun noDuplicateTotalAfterDeadZone() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            val info = calculator.getRowInfoForColumn(11, 8)
            assertTrue(info is RowInfo.Static, "정적 행이어야 함")
            assertEquals(8, (info as RowInfo.Static).templateRowIndex, "Total 다음 행으로 역변환되어 중복 방지")
        }

        @Test
        @DisplayName("employees 아래 행이 모든 열에서 동일한 템플릿 행으로 역변환된다")
        fun reverseCalculationUniformBelowFullWidthRepeat() {
            val calculator = PositionCalculator(
                repeatRegions = createRichSampleRegions(),
                collectionSizes = sizes,
                templateLastRow = 35
            )
            calculator.calculate()

            val infoCol1 = calculator.getRowInfoForColumn(45, 1)
            val infoCol8 = calculator.getRowInfoForColumn(45, 8)

            assertTrue(infoCol1 is RowInfo.Static)
            assertTrue(infoCol8 is RowInfo.Static)
            assertEquals(
                (infoCol1 as RowInfo.Static).templateRowIndex,
                (infoCol8 as RowInfo.Static).templateRowIndex,
                "employees 아래 행은 모든 열에서 동일한 템플릿 행"
            )
            assertEquals(31, infoCol1.templateRowIndex)
        }
    }

    @Nested
    @DisplayName("bundle 기본 동작 테스트")
    inner class BundleTest {

        @Test
        @DisplayName("bundle 내부 repeat은 외부에 단일 요소로 참여한다")
        fun bundleActsAsSingleElement() {
            // bundle(A1:D10) 안에 repeat(A5:D5, 3개)
            val regions = listOf(
                RepeatRegionSpec("items", "item", CellArea(CellCoord(4, 0), CellCoord(4, 3)), RepeatDirection.DOWN)
            )
            val bundles = listOf(
                BundleRegionSpec(CellArea(CellCoord(0, 0), CellCoord(9, 3)))
            )

            val calculator = PositionCalculator(
                repeatRegions = regions,
                collectionSizes = CollectionSizes.of("items" to 3),
                templateLastRow = 15,
                bundleRegions = bundles
            )
            calculator.calculate()

            // bundle 내부 repeat의 expansion = (3-1)*1 = 2
            // bundle의 renderingEnd = bundle.finalStart(0) + (9-0) + 2 = 11
            // bundle 아래 행(row 10): 10이 bundle의 templateEndRow(9)보다 크므로
            // 상위 = bundle(renderingEnd=11), gap = 10-9-1 = 0, candidate = 11+0+1 = 12
            val pos = calculator.getFinalPosition(10, 0)
            assertEquals(12, pos.row, "bundle 아래 행은 내부 확장량만큼 밀린다")
        }
    }
}
