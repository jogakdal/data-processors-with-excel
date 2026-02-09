package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.exception.TemplateProcessingException
import org.apache.poi.ss.util.CellRangeAddress

/**
 * 실제 출력 행의 정보
 *
 * 스트리밍 모드에서 각 행이 어떤 유형인지 식별하는 데 사용한다.
 */
sealed class RowInfo {
    /**
     * 정적 행 (repeat에 속하지 않은 행)
     *
     * @property templateRowIndex 템플릿에서의 행 인덱스
     */
    data class Static(val templateRowIndex: Int) : RowInfo()

    /**
     * 반복 행 (repeat 영역에 속한 행)
     *
     * @property repeatRegion repeat 영역 명세
     * @property itemIndex 아이템 인덱스 (0-based)
     * @property templateRowOffset 템플릿 행 내 오프셋 (multi-row repeat의 경우)
     */
    data class Repeat(
        val repeatRegion: RepeatRegionSpec,
        val itemIndex: Int,
        val templateRowOffset: Int
    ) : RowInfo()
}

/**
 * 템플릿 요소의 최종 위치를 계산한다.
 *
 * repeat 확장에 따른 모든 요소의 위치 변화를 추적하고,
 * 각 요소의 최종 행/열 위치를 계산한다.
 *
 * **핵심 원칙**:
 * - 위치 계산 시 collection의 size만 필요 (lazy loading 호환)
 * - 실제 데이터 접근은 셀에 값을 쓸 때 한 번만 수행
 *
 * **위치 결정 규칙 (모든 요소에 동일 적용)**:
 * 1. 어느 repeat에도 영향받지 않는 요소: 템플릿 위치 그대로 고정
 * 2. 하나의 repeat에만 영향받는 요소: 그 repeat 확장만큼 밀림
 * 3. 두 개 이상의 repeat에 영향받는 요소: 가장 많이 밀리는 위치로 이동
 */
class PositionCalculator(
    private val repeatRegions: List<RepeatRegionSpec>,
    private val collectionSizes: Map<String, Int>,
    private val templateLastRow: Int = -1  // 템플릿의 마지막 행 인덱스 (-1이면 repeatRegions에서 계산)
) {
    /**
     * repeat 확장 정보
     *
     * @property region 원본 repeat 영역 명세
     * @property finalStartRow 확장 시작 행 (이전 repeat 영향 반영)
     * @property finalStartCol 확장 시작 열
     * @property rowExpansion 행 확장량 (DOWN 방향)
     * @property colExpansion 열 확장량 (RIGHT 방향)
     * @property itemCount 반복 아이템 수
     */
    data class RepeatExpansion(
        val region: RepeatRegionSpec,
        val finalStartRow: Int,
        val finalStartCol: Int,
        val rowExpansion: Int,
        val colExpansion: Int,
        val itemCount: Int
    )

    private val expansions = mutableListOf<RepeatExpansion>()
    private var calculated = false

    /**
     * 모든 repeat의 확장 정보를 계산한다.
     * repeat 간 의존성을 고려하여 순차적으로 처리한다.
     *
     * @return 계산된 확장 정보 리스트 (위->아래, 왼쪽->오른쪽 순서)
     */
    fun calculate(): List<RepeatExpansion> {
        if (calculated) return expansions.toList()

        // repeat을 위치순으로 정렬 (위 -> 아래, 왼쪽 -> 오른쪽)
        val sorted = repeatRegions.sortedWith(compareBy({ it.startRow }, { it.startCol }))

        // 각 repeat 처리
        for (region in sorted) {
            // 이 repeat이 받는 영향 계산 (이전 repeat들에 의해)
            val affectedRowOffset = calculateAffectedRowOffset(region, expansions)
            val affectedColOffset = calculateAffectedColOffset(region, expansions)

            // 최종 시작 위치
            val finalStartRow = region.startRow + affectedRowOffset
            val finalStartCol = region.startCol + affectedColOffset

            // 확장량 계산 (size만 사용 - 데이터 접근 불필요)
            val itemCount = collectionSizes[region.collection] ?: 0
            // 데이터가 0개여도 최소 1개 반복 단위를 출력
            val effectiveItemCount = maxOf(1, itemCount)
            val (rowExp, colExp) = calculateExpansionAmount(region, itemCount)

            expansions.add(RepeatExpansion(region, finalStartRow, finalStartCol, rowExp, colExp, effectiveItemCount))
        }

        calculated = true
        return expansions.toList()
    }

    /**
     * 특정 템플릿 위치(행, 열)가 최종적으로 어디로 이동하는지 계산한다.
     *
     * @param templateRow 템플릿 행 인덱스 (0-based)
     * @param templateCol 템플릿 열 인덱스 (0-based)
     * @return (최종 행, 최종 열) 쌍
     */
    fun getFinalPosition(templateRow: Int, templateCol: Int): Pair<Int, Int> {
        if (!calculated) calculate()

        var rowOffset = 0
        var colOffset = 0

        for (expansion in expansions) {
            // 이 요소가 해당 repeat에 영향받는지 확인
            val affectsRow = isAffectedByRepeatRow(templateRow, templateCol, expansion)
            val affectsCol = isAffectedByRepeatCol(templateRow, templateCol, expansion)

            // 여러 repeat 아래에 있는 요소는 모든 오프셋이 누적됨
            if (affectsRow) {
                rowOffset += expansion.rowExpansion
            }
            if (affectsCol) {
                colOffset += expansion.colExpansion
            }
        }

        return (templateRow + rowOffset) to (templateCol + colOffset)
    }

    /**
     * 특정 템플릿 범위가 최종적으로 어떻게 변하는지 계산한다.
     *
     * 병합 셀이나 범위의 경우, 포함된 모든 열의 오프셋 중 최대값을 적용한다.
     * 예: A열이 4행, B열이 2행 밀릴 때 A10:B10 -> A14:B14 (max 적용)
     *
     * @param templateFirstRow 템플릿 시작 행 (0-based)
     * @param templateLastRow 템플릿 끝 행 (0-based)
     * @param templateFirstCol 템플릿 시작 열 (0-based)
     * @param templateLastCol 템플릿 끝 열 (0-based)
     * @return 최종 CellRangeAddress
     */
    fun getFinalRange(
        templateFirstRow: Int,
        templateLastRow: Int,
        templateFirstCol: Int,
        templateLastCol: Int
    ): CellRangeAddress {
        // 범위에 포함된 모든 열에 대해 행 오프셋을 계산하고 최대값 선택
        var maxRowOffset = 0
        for (col in templateFirstCol..templateLastCol) {
            val (finalRow, _) = getFinalPosition(templateFirstRow, col)
            val rowOffset = finalRow - templateFirstRow
            maxRowOffset = maxOf(maxRowOffset, rowOffset)
        }

        // 범위에 포함된 모든 행에 대해 열 오프셋을 계산하고 최대값 선택
        var maxColOffset = 0
        for (row in templateFirstRow..templateLastRow) {
            val (_, finalCol) = getFinalPosition(row, templateFirstCol)
            val colOffset = finalCol - templateFirstCol
            maxColOffset = maxOf(maxColOffset, colOffset)
        }

        return CellRangeAddress(
            templateFirstRow + maxRowOffset,
            templateLastRow + maxRowOffset,
            templateFirstCol + maxColOffset,
            templateLastCol + maxColOffset
        )
    }

    /**
     * 특정 repeat 영역 내에서 특정 아이템 인덱스의 행 시작 위치를 계산한다.
     *
     * @param expansion repeat 확장 정보
     * @param itemIndex 아이템 인덱스 (0-based)
     * @param templateRowOffset 템플릿 내 행 오프셋 (0 = 첫 행)
     * @return 최종 행 인덱스
     */
    fun getRowForRepeatItem(expansion: RepeatExpansion, itemIndex: Int, templateRowOffset: Int = 0) =
        expansion.finalStartRow +
                (itemIndex * (expansion.region.endRow - expansion.region.startRow + 1)) + templateRowOffset

    /**
     * 특정 repeat 영역 내에서 특정 아이템 인덱스의 열 시작 위치를 계산한다.
     *
     * @param expansion repeat 확장 정보
     * @param itemIndex 아이템 인덱스 (0-based)
     * @param templateColOffset 템플릿 내 열 오프셋 (0 = 첫 열)
     * @return 최종 열 인덱스
     */
    fun getColForRepeatItem(expansion: RepeatExpansion, itemIndex: Int, templateColOffset: Int = 0) =
        expansion.finalStartCol +
                (itemIndex * (expansion.region.endCol - expansion.region.startCol + 1)) + templateColOffset

    /**
     * 특정 collection의 확장 정보를 반환한다.
     *
     * 주의: 같은 collection이 여러 위치에서 사용되면 첫 번째 것만 반환된다.
     * 정확한 expansion을 찾으려면 [getExpansionForRegion] 사용.
     */
    fun getExpansionFor(collectionName: String): RepeatExpansion? =
        expansions.also { if (!calculated) calculate() }.find { it.region.collection == collectionName }

    /**
     * 특정 repeat 영역의 확장 정보를 반환한다.
     *
     * 같은 collection이 여러 위치에서 사용될 때 정확한 expansion을 찾기 위해 사용한다.
     *
     * @param collectionName collection 이름
     * @param startRow repeat 시작 행 (템플릿 기준)
     * @param startCol repeat 시작 열 (템플릿 기준)
     * @return 해당 영역의 확장 정보, 없으면 null
     */
    fun getExpansionForRegion(collectionName: String, startRow: Int, startCol: Int): RepeatExpansion? =
        expansions.also { if (!calculated) calculate() }.find {
            it.region.collection == collectionName &&
                it.region.startRow == startRow &&
                it.region.startCol == startCol
        }

    /**
     * 모든 확장 정보를 반환한다.
     */
    fun getExpansions(): List<RepeatExpansion> = expansions.also { if (!calculated) calculate() }.toList()

    /**
     * 특정 템플릿 셀이 emptyRange 영역에 속하는지 확인한다.
     *
     * emptyRange는 셀 범위이므로 행 전체가 아닌 특정 셀만 확인한다.
     *
     * @param templateRow 템플릿 행 인덱스 (0-based)
     * @param templateCol 템플릿 열 인덱스 (0-based)
     * @return emptyRange 영역에 속하면 true
     */
    fun isInEmptyRange(templateRow: Int, templateCol: Int): Boolean {
        if (!calculated) calculate()

        return repeatRegions
            .mapNotNull { it.emptyRange }
            .filter { it.sheetName == null }  // 같은 시트의 emptyRange만
            .any { range ->
                templateRow in range.startRow..range.endRow &&
                templateCol in range.startCol..range.endCol
            }
    }

    /**
     * 전체 출력 행 수를 계산한다.
     *
     * 템플릿의 마지막 행 + 모든 DOWN 방향 repeat의 확장량
     */
    fun getTotalRows(): Int {
        if (!calculated) calculate()

        // 템플릿의 마지막 행 찾기
        // templateLastRow가 설정되어 있으면 사용, 아니면 repeatRegions에서 계산
        val maxTemplateRow = if (templateLastRow >= 0) {
            templateLastRow
        } else {
            repeatRegions.maxOfOrNull { it.endRow } ?: 0
        }

        // 전체 행 확장량 계산
        val totalRowExpansion = expansions
            .filter { it.region.direction == RepeatDirection.DOWN }
            .sumOf { it.rowExpansion }

        return maxTemplateRow + totalRowExpansion + 1
    }

    /**
     * 실제 출력 행에 대한 정보를 반환한다.
     *
     * @param actualRow 실제 출력 행 인덱스 (0-based)
     * @return 해당 행이 정적 행인지, 어느 repeat의 몇 번째 아이템인지 정보
     */
    fun getRowInfo(actualRow: Int): RowInfo {
        if (!calculated) calculate()

        // DOWN 방향 repeat 확장을 역순으로 탐색 (가장 최근 확장이 현재 행에 영향)
        for (expansion in expansions.reversed()) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue

            val templateRowCount = expansion.region.endRow - expansion.region.startRow + 1
            val itemCount = collectionSizes[expansion.region.collection] ?: 0
            // 데이터가 0개여도 최소 1개 행(빈 행)을 출력
            val effectiveItemCount = maxOf(1, itemCount)

            // 이 repeat가 차지하는 실제 행 범위
            val repeatStartRow = expansion.finalStartRow
            val repeatEndRow = expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1

            if (actualRow in repeatStartRow..repeatEndRow) {
                // 이 행은 repeat 내부에 있음
                val rowWithinRepeat = actualRow - repeatStartRow
                val itemIndex = rowWithinRepeat / templateRowCount
                val templateRowOffset = rowWithinRepeat % templateRowCount

                return RowInfo.Repeat(
                    repeatRegion = expansion.region,
                    itemIndex = itemIndex,
                    templateRowOffset = templateRowOffset
                )
            }
        }

        // repeat에 해당하지 않으면 정적 행
        // 실제 행을 템플릿 행으로 역변환
        return RowInfo.Static(reverseCalculateTemplateRow(actualRow))
    }

    /**
     * 특정 열에서 실제 출력 행에 대한 정보를 반환한다.
     *
     * 같은 actualRow에서 열 그룹에 따라 다른 정보를 반환할 수 있다.
     * 예: A-C열은 repeat 아이템, F-H열은 정적 행
     *
     * @param actualRow 실제 출력 행 인덱스 (0-based)
     * @param column 열 인덱스 (0-based)
     * @return 해당 열에서의 행 정보
     */
    fun getRowInfoForColumn(actualRow: Int, column: Int): RowInfo {
        if (!calculated) calculate()

        // DOWN 방향 repeat 확장을 역순으로 탐색
        for (expansion in expansions.reversed()) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue

            // 이 열이 해당 repeat의 열 범위에 있는지 확인
            if (column !in expansion.region.startCol..expansion.region.endCol) continue

            val templateRowCount = expansion.region.endRow - expansion.region.startRow + 1
            val itemCount = collectionSizes[expansion.region.collection] ?: 0
            // 데이터가 0개여도 최소 1개 행(빈 행)을 출력
            val effectiveItemCount = maxOf(1, itemCount)

            // 이 repeat가 차지하는 실제 행 범위
            val repeatStartRow = expansion.finalStartRow
            val repeatEndRow = expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1

            if (actualRow in repeatStartRow..repeatEndRow) {
                val rowWithinRepeat = actualRow - repeatStartRow
                val itemIndex = rowWithinRepeat / templateRowCount
                val templateRowOffset = rowWithinRepeat % templateRowCount

                return RowInfo.Repeat(
                    repeatRegion = expansion.region,
                    itemIndex = itemIndex,
                    templateRowOffset = templateRowOffset
                )
            }
        }

        // 해당 열에서 repeat에 해당하지 않으면 정적 행
        return RowInfo.Static(reverseCalculateTemplateRowForColumn(actualRow, column))
    }

    /**
     * 특정 열에서 실제 출력 행을 템플릿 행으로 역변환한다.
     *
     * 열 그룹에 따라 다른 오프셋이 적용될 수 있다.
     */
    private fun reverseCalculateTemplateRowForColumn(actualRow: Int, column: Int): Int {
        var templateRow = actualRow

        for (expansion in expansions) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue

            // 이 열이 해당 repeat의 열 범위에 있는지 확인
            if (column !in expansion.region.startCol..expansion.region.endCol) continue

            val itemCount = collectionSizes[expansion.region.collection] ?: 0
            // 데이터가 0개여도 최소 1개 행(빈 행)을 출력
            val effectiveItemCount = maxOf(1, itemCount)

            val templateRowCount = expansion.region.endRow - expansion.region.startRow + 1
            val repeatEndRow = expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1

            // 이 repeat 영역 이후의 행이면 확장량만큼 빼기
            if (actualRow > repeatEndRow) {
                templateRow -= expansion.rowExpansion
            }
        }

        return templateRow
    }

    /**
     * 실제 출력 행을 템플릿 행으로 역변환한다.
     */
    private fun reverseCalculateTemplateRow(actualRow: Int): Int {
        var templateRow = actualRow

        // 위에서 아래로 처리하면서 오프셋 제거
        for (expansion in expansions) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue

            val itemCount = collectionSizes[expansion.region.collection] ?: 0
            // 데이터가 0개여도 최소 1개 반복 단위(빈 행들)를 출력
            val effectiveItemCount = maxOf(1, itemCount)

            val templateRowCount = expansion.region.endRow - expansion.region.startRow + 1
            val repeatEndRow = expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1

            // 이 repeat 영역 이후의 행이면 확장량만큼 빼기
            if (actualRow > repeatEndRow) {
                templateRow -= expansion.rowExpansion
            }
        }

        return templateRow
    }

    // ========== Private Helper Methods ==========

    /** repeat 영역의 확장량 계산 (데이터가 0개여도 최소 1개 반복 단위 출력) */
    private fun calculateExpansionAmount(region: RepeatRegionSpec, itemCount: Int) =
        maxOf(1, itemCount).let { effectiveItemCount ->
            when (region.direction) {
                RepeatDirection.DOWN -> {
                    val templateRowCount = region.endRow - region.startRow + 1
                    maxOf(0, (effectiveItemCount - 1) * templateRowCount) to 0
                }
                RepeatDirection.RIGHT -> {
                    val templateColCount = region.endCol - region.startCol + 1
                    0 to maxOf(0, (effectiveItemCount - 1) * templateColCount)
                }
            }
        }

    /** 특정 repeat가 이전 repeat들에 의해 받는 행 오프셋 계산 (이전 repeat의 열 범위와 겹치고 아래에 있으면 영향받음) */
    private fun calculateAffectedRowOffset(region: RepeatRegionSpec, previousExpansions: List<RepeatExpansion>) =
        previousExpansions
            .filter { it.region.direction == RepeatDirection.DOWN }
            .filter { region.overlapsColumns(it.region) && region.startRow > it.region.endRow }
            .sumOf { it.rowExpansion }

    /** 특정 repeat가 이전 repeat들에 의해 받는 열 오프셋 계산 (이전 repeat의 행 범위와 겹치고 오른쪽에 있으면 영향받음) */
    private fun calculateAffectedColOffset(region: RepeatRegionSpec, previousExpansions: List<RepeatExpansion>) =
        previousExpansions
            .filter { it.region.direction == RepeatDirection.RIGHT }
            .filter { region.overlapsRows(it.region) && region.startCol > it.region.endCol }
            .sumOf { it.colExpansion }

    /** 요소가 특정 repeat 확장에 의해 행 방향으로 영향받는지 확인 (repeat 아래에 있고 열 범위가 겹치면 영향받음) */
    private fun isAffectedByRepeatRow(templateRow: Int, templateCol: Int, expansion: RepeatExpansion) =
        expansion.region.direction == RepeatDirection.DOWN &&
            expansion.rowExpansion > 0 &&
            templateRow > expansion.region.endRow &&
            templateCol in expansion.region.startCol..expansion.region.endCol

    /** 요소가 특정 repeat 확장에 의해 열 방향으로 영향받는지 확인 (repeat 오른쪽에 있고 행 범위가 겹치면 영향받음) */
    private fun isAffectedByRepeatCol(templateRow: Int, templateCol: Int, expansion: RepeatExpansion) =
        expansion.region.direction == RepeatDirection.RIGHT &&
            expansion.colExpansion > 0 &&
            templateCol > expansion.region.endCol &&
            templateRow in expansion.region.startRow..expansion.region.endRow

    companion object {
        /**
         * repeat 영역 간 범위 겹침을 검사한다.
         * 어떤 두 repeat이든 2D 범위가 겹치면 예외 발생
         *
         * @throws TemplateProcessingException repeat 영역이 겹치는 경우
         */
        fun validateNoOverlap(regions: List<RepeatRegionSpec>) {
            for (i in regions.indices) {
                for (j in i + 1 until regions.size) {
                    if (regions[i].overlaps(regions[j])) {
                        throw TemplateProcessingException(
                            errorType = TemplateProcessingException.ErrorType.INVALID_PARAMETER_VALUE,
                            details = "repeat 영역이 겹칩니다: " +
                                "${regions[i].collection}(${regions[i].direction}, 행 ${regions[i].startRow + 1}-" +
                                "${regions[i].endRow + 1}, 열 ${regions[i].startCol + 1}-${regions[i].endCol + 1}), " +
                                "${regions[j].collection}(${regions[j].direction}, 행 ${regions[j].startRow + 1}-" +
                                "${regions[j].endRow + 1}, 열 ${regions[j].startCol + 1}-${regions[j].endCol + 1})"
                        )
                    }
                }
            }
        }

        /** 데이터에서 collection 크기 추출 */
        fun extractCollectionSizes(data: Map<String, Any>, repeatRegions: List<RepeatRegionSpec>) =
            repeatRegions.associate { region ->
                region.collection to when (val items = data[region.collection]) {
                    is List<*> -> items.size
                    is Collection<*> -> items.size
                    is Iterable<*> -> items.count()
                    else -> 0
                }
            }
    }
}
