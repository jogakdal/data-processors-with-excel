package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.CellArea
import io.github.jogakdal.tbeg.engine.core.CellCoord
import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.core.RowRange
import io.github.jogakdal.tbeg.exception.TemplateProcessingException
import org.apache.poi.ss.util.CellRangeAddress

/**
 * 밀림 계산에 참여하는 템플릿 요소
 *
 * 밀림의 원인은 확장 요소(repeat)의 크기 변화이고,
 * 전파는 넓은 요소(병합 셀, bundle)를 통해 교차 열 그룹으로 전달된다.
 * 단일 셀을 통한 체이닝은 직접 참조와 수학적으로 동일하므로 추적하지 않는다.
 */
sealed class TemplateElement {
    abstract val templateArea: CellArea

    /** 확장 요소 (repeat) -- 데이터에 의해 크기가 변한다 */
    data class Expandable(
        override val templateArea: CellArea,
        val region: RepeatRegionSpec,
        val itemCount: Int
    ) : TemplateElement()

    /** 고정 크기 요소 (병합 셀 등) -- 크기 불변, 여러 셀에 걸침 */
    data class Fixed(
        override val templateArea: CellArea
    ) : TemplateElement()

    /** Bundle 요소 -- 내부 밀림 정책 적용 후 크기가 결정된다 */
    data class Bundle(
        override val templateArea: CellArea,
        val innerElements: List<TemplateElement>,
        val internalExpansion: Int  // 내부 총 확장량
    ) : TemplateElement()
}

/**
 * 위치가 확정된 요소 -- 체이닝 계산 결과
 */
data class ResolvedElement(
    val templateStartRow: Int,
    val templateEndRow: Int,
    val templateColRange: IntRange,
    val finalStartRow: Int,
    val renderingEndRow: Int,
    val source: TemplateElement
)

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
 * 템플릿 요소의 최종 위치를 체이닝 알고리즘으로 계산한다.
 *
 * **체이닝 원칙** (DEVELOPMENT.md 3.2절):
 * - 위의 요소가 밀리면 그 아래의 모든 요소도 밀린다
 * - 밀림의 전파는 넓은 요소(병합 셀, bundle)를 통해 교차 열로 전달된다
 * - gap 열(repeat에 속하지 않는 열)의 정적 셀은 밀리지 않는다
 *   (넓은 요소에 의해 연결되지 않는 한)
 *
 * **핵심 원칙**:
 * - 위치 계산 시 collection의 size만 필요 (lazy loading 호환)
 * - 실제 데이터 접근은 셀에 값을 쓸 때 한 번만 수행
 */
class PositionCalculator(
    private val repeatRegions: List<RepeatRegionSpec>,
    private val collectionSizes: CollectionSizes,
    private val templateLastRow: Int = -1,
    private val mergedRegions: List<CellRangeAddress> = emptyList(),
    private val bundleRegions: List<BundleRegionSpec> = emptyList()
) {
    /**
     * repeat 확장 정보
     *
     * @property region 원본 repeat 영역 명세
     * @property finalStartRow 확장 시작 행 (체이닝으로 결정)
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

    /** calculate()가 아직 수행되지 않았으면 실행한다 */
    private fun ensureCalculated() { if (!calculated) calculate() }

    /** 열별 해결된 요소 리스트 (체이닝 상위 탐색용) */
    private val resolvedByColumn = mutableMapOf<Int, MutableList<ResolvedElement>>()

    /**
     * 모든 요소의 위치를 체이닝 알고리즘으로 계산한다.
     *
     * 1. 요소 수집 (repeat, 병합 셀, bundle)
     * 2. templateStartRow 순 정렬
     * 3. 순차 체이닝: 각 요소의 열 범위에서 상위 요소를 찾아 MAX
     *
     * @return 계산된 repeat 확장 정보 리스트
     */
    fun calculate(): List<RepeatExpansion> {
        if (calculated) return expansions.toList()

        // 1. 요소 수집
        val elements = collectElements()

        // 2. templateStartRow 순 정렬
        val sorted = elements.sortedWith(compareBy({ it.templateArea.start.row }, { it.templateArea.start.col }))

        // 3. 순차 체이닝 계산 (위 -> 아래)
        for (element in sorted) {
            val colRange = element.templateArea.start.col..element.templateArea.end.col
            var maxFinalStart = element.templateArea.start.row  // 기본값 (밀리지 않음)

            for (col in colRange) {
                val parent = findNearestAbove(col, element.templateArea.start.row)
                if (parent != null) {
                    val gap = element.templateArea.start.row - parent.templateEndRow - 1
                    maxFinalStart = maxOf(maxFinalStart, parent.renderingEndRow + gap + 1)
                }
            }

            val renderingEndRow = calculateRenderingEnd(element, maxFinalStart)

            val resolved = ResolvedElement(
                templateStartRow = element.templateArea.start.row,
                templateEndRow = element.templateArea.end.row,
                templateColRange = colRange,
                finalStartRow = maxFinalStart,
                renderingEndRow = renderingEndRow,
                source = element
            )

            // 열별 인덱스에 추가 (후속 요소의 상위 탐색용)
            for (col in colRange) {
                resolvedByColumn.getOrPut(col) { mutableListOf() }.add(resolved)
            }

            // RepeatExpansion 갱신 (기존 API 호환)
            when (element) {
                is TemplateElement.Expandable -> {
                    val (rowExp, colExp) = calculateExpansionAmount(element.region, element.itemCount)
                    val colOffset = calculateAffectedColOffset(element.region, expansions)
                    expansions.add(RepeatExpansion(
                        element.region, maxFinalStart,
                        element.region.area.start.col + colOffset,
                        rowExp, colExp, maxOf(1, element.itemCount)
                    ))
                }
                is TemplateElement.Bundle -> {
                    // bundle 내부 repeat: expansion + resolvedByColumn에 추가
                    for (inner in element.innerElements.filterIsInstance<TemplateElement.Expandable>()) {
                        val innerOffset = inner.templateArea.start.row - element.templateArea.start.row
                        val innerFinalStartRow = maxFinalStart + innerOffset
                        val (rowExp, colExp) = calculateExpansionAmount(inner.region, inner.itemCount)
                        expansions.add(RepeatExpansion(
                            inner.region, innerFinalStartRow,
                            inner.region.area.start.col, rowExp, colExp,
                            maxOf(1, inner.itemCount)
                        ))
                        // resolvedByColumn에도 추가 (역변환 시 필요)
                        val innerColRange = inner.templateArea.start.col..inner.templateArea.end.col
                        val innerResolved = ResolvedElement(
                            templateStartRow = inner.templateArea.start.row,
                            templateEndRow = inner.templateArea.end.row,
                            templateColRange = innerColRange,
                            finalStartRow = innerFinalStartRow,
                            renderingEndRow = calculateRenderingEnd(inner, innerFinalStartRow),
                            source = inner
                        )
                        for (c in innerColRange) {
                            resolvedByColumn.getOrPut(c) { mutableListOf() }.add(innerResolved)
                        }
                    }
                }
                is TemplateElement.Fixed -> { /* Fixed 요소는 expansion 불필요 */ }
            }
        }

        // RIGHT repeat은 행 체이닝에 참여하지 않지만 expansion 정보가 필요
        for (region in repeatRegions.filter { it.direction == RepeatDirection.RIGHT }) {
            val itemCount = maxOf(1, collectionSizes[region.collection] ?: 0)
            // 행 위치: 체이닝으로 계산 (상위 요소에 의한 밀림 반영)
            val startCol = region.area.start.col
            val containing = resolvedByColumn[startCol]
                ?.find { region.area.start.row in it.templateStartRow..it.templateEndRow }
            val parent = findNearestAbove(startCol, region.area.start.row)
            val finalStartRow = when {
                containing != null ->
                    containing.finalStartRow + (region.area.start.row - containing.templateStartRow)
                parent != null ->
                    parent.renderingEndRow + (region.area.start.row - parent.templateEndRow - 1) + 1
                else -> region.area.start.row
            }
            val (rowExp, colExp) = calculateExpansionAmount(region, itemCount)
            val colOffset = calculateAffectedColOffset(region, expansions)
            expansions.add(RepeatExpansion(
                region, finalStartRow,
                region.area.start.col + colOffset,
                rowExp, colExp, itemCount
            ))
        }

        calculated = true
        return expansions.toList()
    }

    /**
     * 특정 템플릿 셀 좌표가 최종적으로 어디로 이동하는지 계산한다.
     *
     * @param template 템플릿 셀 좌표 (0-based)
     * @return 최종 위치 CellCoord
     */
    fun getFinalPosition(template: CellCoord) = getFinalPosition(template.row, template.col)

    /**
     * 특정 템플릿 위치(행, 열)가 최종적으로 어디로 이동하는지 계산한다.
     *
     * 체이닝 기반: 해당 열에서 가장 가까운 상위 해결 요소의 renderingEnd + gap
     *
     * @param templateRow 템플릿 행 인덱스 (0-based)
     * @param templateCol 템플릿 열 인덱스 (0-based)
     * @return 최종 위치 CellCoord
     */
    fun getFinalPosition(templateRow: Int, templateCol: Int): CellCoord {
        ensureCalculated()

        // 1. 해당 열에서 templateRow를 포함하는 해결된 요소 확인
        val containing = resolvedByColumn[templateCol]
            ?.find { templateRow in it.templateStartRow..it.templateEndRow }

        val finalRow = if (containing != null) {
            // 요소 내부 오프셋 적용 (bundle은 내부 repeat 확장량 반영)
            containing.finalStartRow + (templateRow - containing.templateStartRow) +
                internalExpansionAbove(containing, templateCol, templateRow)
        } else {
            // 2. 가장 가까운 상위 요소 기준
            val parent = findNearestAbove(templateCol, templateRow)
            if (parent != null) {
                val gap = templateRow - parent.templateEndRow - 1
                parent.renderingEndRow + gap + 1
            } else templateRow
        }

        // 열 방향 (RIGHT repeat) -- 같은 colRange의 expansion은 MAX만 취한다
        val colOffset = expansions
            .filter { isAffectedByRepeatCol(templateRow, templateCol, it) }
            .groupBy { it.region.area.colRange }
            .values.sumOf { group -> group.maxOf { it.colExpansion } }

        return CellCoord(finalRow, templateCol + colOffset)
    }

    /**
     * 특정 템플릿 범위가 최종적으로 어떻게 변하는지 계산한다.
     *
     * 범위에 포함된 모든 열의 오프셋 중 최대값을 적용한다.
     */
    fun getFinalRange(
        templateFirstRow: Int,
        templateLastRow: Int,
        templateFirstCol: Int,
        templateLastCol: Int
    ): CellRangeAddress {
        var maxRowOffset = 0
        for (col in templateFirstCol..templateLastCol) {
            val rowOffset = getFinalPosition(templateFirstRow, col).row - templateFirstRow
            maxRowOffset = maxOf(maxRowOffset, rowOffset)
        }

        var maxColOffset = 0
        for (row in templateFirstRow..templateLastRow) {
            val colOffset = getFinalPosition(row, templateFirstCol).col - templateFirstCol
            maxColOffset = maxOf(maxColOffset, colOffset)
        }

        return CellRangeAddress(
            templateFirstRow + maxRowOffset,
            templateLastRow + maxRowOffset,
            templateFirstCol + maxColOffset,
            templateLastCol + maxColOffset
        )
    }

    fun getFinalRange(start: CellCoord, end: CellCoord) =
        getFinalRange(start.row, end.row, start.col, end.col)

    fun getFinalRange(range: CellRangeAddress) =
        getFinalRange(range.firstRow, range.lastRow, range.firstColumn, range.lastColumn)

    /**
     * 특정 repeat 영역 내에서 특정 아이템의 행 시작 위치를 계산한다.
     */
    fun getRowForRepeatItem(expansion: RepeatExpansion, itemIndex: Int, templateRowOffset: Int = 0) =
        expansion.finalStartRow +
                (itemIndex * expansion.region.area.rowRange.count) + templateRowOffset

    /**
     * 특정 repeat 영역 내에서 특정 아이템의 열 시작 위치를 계산한다.
     */
    fun getColForRepeatItem(expansion: RepeatExpansion, itemIndex: Int, templateColOffset: Int = 0) =
        expansion.finalStartCol +
                (itemIndex * expansion.region.area.colRange.count) + templateColOffset

    fun getExpansionFor(collectionName: String): RepeatExpansion? =
        expansions.also { ensureCalculated() }.find { it.region.collection == collectionName }

    fun getExpansionForRegion(collectionName: String, startRow: Int, startCol: Int): RepeatExpansion? =
        expansions.also { ensureCalculated() }.find {
            it.region.collection == collectionName &&
                it.region.area.start.row == startRow &&
                it.region.area.start.col == startCol
        }

    fun getExpansions(): List<RepeatExpansion> = expansions.also { ensureCalculated() }.toList()

    /**
     * 특정 템플릿 셀이 emptyRange 영역에 속하는지 확인한다.
     */
    fun isInEmptyRange(templateRow: Int, templateCol: Int): Boolean {
        ensureCalculated()
        return repeatRegions
            .mapNotNull { it.emptyRange }
            .filter { it.sheetName == null }
            .any { templateRow in it.rowRange && templateCol in it.colRange }
    }

    /**
     * 전체 출력 행 수를 계산한다.
     */
    fun getTotalRows(): Int {
        ensureCalculated()

        val maxTemplateRow = if (templateLastRow >= 0) templateLastRow
        else repeatRegions.maxOfOrNull { it.area.end.row } ?: 0

        // 같은 행의 repeat들은 MAX, 다른 행은 SUM
        val totalRowExpansion = expansions
            .filter { it.region.direction == RepeatDirection.DOWN }
            .groupBy { it.region.area.start.row }
            .values
            .sumOf { group -> group.maxOf { it.rowExpansion } }

        return maxTemplateRow + totalRowExpansion + 1
    }

    /**
     * 실제 출력 행에 대한 정보를 반환한다.
     */
    fun getRowInfo(actualRow: Int): RowInfo {
        ensureCalculated()

        for (expansion in expansions.reversed()) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue

            val templateRowCount = expansion.region.area.rowRange.count
            val effectiveItemCount = maxOf(1, collectionSizes[expansion.region.collection] ?: 0)
            val repeatRows = RowRange(
                expansion.finalStartRow,
                expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1
            )

            if (actualRow in repeatRows) {
                val rowWithinRepeat = actualRow - expansion.finalStartRow
                return RowInfo.Repeat(
                    expansion.region,
                    rowWithinRepeat / templateRowCount,
                    rowWithinRepeat % templateRowCount
                )
            }
        }

        return RowInfo.Static(reverseCalculateTemplateRow(actualRow))
    }

    /**
     * 특정 열에서 실제 출력 행에 대한 정보를 반환한다.
     *
     * 체이닝에서는 열마다 밀림량이 다를 수 있으므로,
     * 해당 열의 repeat만 고려하여 정확한 정보를 반환한다.
     */
    fun getRowInfoForColumn(actualRow: Int, column: Int): RowInfo {
        ensureCalculated()

        for (expansion in expansions.reversed()) {
            if (expansion.region.direction != RepeatDirection.DOWN) continue
            if (column !in expansion.region.area.colRange) continue

            val templateRowCount = expansion.region.area.rowRange.count
            val effectiveItemCount = maxOf(1, collectionSizes[expansion.region.collection] ?: 0)
            val repeatRows = RowRange(
                expansion.finalStartRow,
                expansion.finalStartRow + (templateRowCount * effectiveItemCount) - 1
            )

            if (actualRow in repeatRows) {
                val rowWithinRepeat = actualRow - expansion.finalStartRow
                return RowInfo.Repeat(
                    expansion.region,
                    rowWithinRepeat / templateRowCount,
                    rowWithinRepeat % templateRowCount
                )
            }
        }

        return RowInfo.Static(reverseCalculateTemplateRowForColumn(actualRow, column))
    }

    // ========== Private: 요소 수집 ==========

    /** 밀림 계산에 참여할 요소를 수집한다 */
    private fun collectElements(): MutableList<TemplateElement> {
        val elements = mutableListOf<TemplateElement>()

        // DOWN repeat -> Expandable
        for (region in repeatRegions) {
            if (region.direction == RepeatDirection.DOWN) {
                elements.add(TemplateElement.Expandable(
                    region.area, region, collectionSizes[region.collection] ?: 0
                ))
            }
        }

        // repeat/bundle 밖의 여러 열에 걸치는 병합 셀 -> Fixed
        for (merged in mergedRegions) {
            if (merged.firstColumn == merged.lastColumn) continue  // 단일 열은 전파 불필요
            val mergedArea = CellArea(merged.firstRow, merged.firstColumn, merged.lastRow, merged.lastColumn)
            if (repeatRegions.any { it.area.contains(mergedArea) }) continue
            if (bundleRegions.any { it.area.contains(mergedArea) }) continue
            elements.add(TemplateElement.Fixed(mergedArea))
        }

        // bundle -> Bundle (내부 요소를 대표)
        for (bundle in bundleRegions) {
            val innerElements = elements.filter { bundle.area.contains(it.templateArea) }
            val internalExpansion = innerElements.filterIsInstance<TemplateElement.Expandable>()
                .sumOf { calculateExpansionAmount(it.region, it.itemCount).first }
            elements.add(TemplateElement.Bundle(bundle.area, innerElements, internalExpansion))
            elements.removeAll(innerElements.toSet())
        }

        return elements
    }

    // ========== Private: 체이닝 헬퍼 ==========

    /**
     * bundle 내부에서 templateRow 위에 있는 내부 repeat의 총 확장량을 계산한다.
     * (getFinalPosition 순방향용 -- 템플릿 좌표 기준)
     */
    private fun internalExpansionAbove(containing: ResolvedElement, col: Int, templateRow: Int): Int {
        if (containing.source !is TemplateElement.Bundle) return 0
        return resolvedByColumn[col]
            ?.filter {
                it.source is TemplateElement.Expandable &&
                    it.templateStartRow >= containing.templateStartRow &&
                    it.templateEndRow < templateRow
            }
            ?.sumOf { it.renderingEndRow - it.finalStartRow - (it.templateEndRow - it.templateStartRow) }
            ?: 0
    }

    /**
     * bundle 내부에서 actualRow 위에 있는 내부 repeat의 총 확장량을 계산한다.
     * (reverseCalculateTemplateRowForColumn 역변환용 -- 렌더링 좌표 기준)
     */
    private fun internalExpansionAboveRendering(containing: ResolvedElement, col: Int, actualRow: Int): Int =
        resolvedByColumn[col]
            ?.filter {
                it.source is TemplateElement.Expandable &&
                    it.finalStartRow >= containing.finalStartRow &&
                    it.renderingEndRow < actualRow
            }
            ?.sumOf { it.renderingEndRow - it.finalStartRow - (it.templateEndRow - it.templateStartRow) }
            ?: 0

    /** 특정 열에서 targetStartRow 바로 위의 가장 가까운 해결된 요소를 찾는다 */
    private fun findNearestAbove(col: Int, targetStartRow: Int): ResolvedElement? =
        resolvedByColumn[col]
            ?.filter { it.templateEndRow < targetStartRow }
            ?.maxByOrNull { it.templateEndRow }

    /** 요소의 렌더링 끝 행 계산 */
    private fun calculateRenderingEnd(element: TemplateElement, finalStartRow: Int): Int {
        val templateHeight = element.templateArea.end.row - element.templateArea.start.row
        return when (element) {
            is TemplateElement.Expandable -> {
                val effectiveItemCount = maxOf(1, element.itemCount)
                finalStartRow + (effectiveItemCount * element.region.area.rowRange.count) - 1
            }
            is TemplateElement.Fixed -> finalStartRow + templateHeight
            is TemplateElement.Bundle -> finalStartRow + templateHeight + element.internalExpansion
        }
    }

    // ========== Private: 확장량 / 오프셋 계산 ==========

    /** repeat 영역의 확장량 계산 */
    private fun calculateExpansionAmount(region: RepeatRegionSpec, itemCount: Int) =
        maxOf(1, itemCount).let { effectiveItemCount ->
            when (region.direction) {
                RepeatDirection.DOWN -> maxOf(0, (effectiveItemCount - 1) * region.area.rowRange.count) to 0
                RepeatDirection.RIGHT -> 0 to maxOf(0, (effectiveItemCount - 1) * region.area.colRange.count)
            }
        }

    /** RIGHT repeat에 의한 열 오프셋 계산 */
    private fun calculateAffectedColOffset(region: RepeatRegionSpec, previousExpansions: List<RepeatExpansion>) =
        previousExpansions
            .filter { it.region.direction == RepeatDirection.RIGHT }
            .filter { region.area.overlapsRows(it.region.area) && region.area.start.col > it.region.area.end.col }
            .groupBy { it.region.area.colRange }
            .values.sumOf { group -> group.maxOf { it.colExpansion } }

    /** 열 방향 밀림 여부 (직접 겹침 + 간접 겹침 + 번들) */
    private fun isAffectedByRepeatCol(templateRow: Int, templateCol: Int, expansion: RepeatExpansion): Boolean {
        if (expansion.region.direction != RepeatDirection.RIGHT || expansion.colExpansion <= 0) return false
        if (templateCol <= expansion.region.area.end.col) return false
        // 직접 겹침: templateRow가 expansion의 행 범위 내
        if (templateRow in expansion.region.area.rowRange) return true
        // 간접 겹침: templateRow가 다른 RIGHT repeat(other) 안에 있고,
        // templateCol이 other의 오른쪽이며, other가 expansion과 행 범위가 겹기면
        // expansion의 열 확장이 other를 통해 전파된다
        if (expansions.any { other ->
                other !== expansion &&
                    other.region.direction == RepeatDirection.RIGHT &&
                    templateRow in other.region.area.rowRange &&
                    templateCol > other.region.area.end.col &&
                    other.region.area.overlapsRows(expansion.region.area)
            }) return true
        // 번들 겹침: templateRow/Col이 번들 영역 안에 있고,
        // 그 번들의 행 범위가 expansion과 겹치면 번들 내 셀도 밀린다
        return bundleRegions.any { bundle ->
            templateRow in bundle.area.rowRange &&
                templateCol in bundle.area.colRange &&
                bundle.area.overlapsRows(expansion.region.area)
        }
    }

    // ========== Private: 역변환 ==========

    /** 특정 열에서 실제 출력 행을 템플릿 행으로 역변환한다 (체이닝 기반) */
    private fun reverseCalculateTemplateRowForColumn(actualRow: Int, column: Int): Int {
        // 1. bundle 렌더링 범위 내부 확인 (bundle 내부 정적 행 처리)
        val containingBundle = resolvedByColumn[column]
            ?.find { actualRow in it.finalStartRow..it.renderingEndRow && it.source is TemplateElement.Bundle }

        if (containingBundle != null) {
            val internalExp = internalExpansionAboveRendering(containingBundle, column, actualRow)
            return containingBundle.templateStartRow + (actualRow - containingBundle.finalStartRow) - internalExp
        }

        // 2. 가장 가까운 상위 요소 기준
        val parent = resolvedByColumn[column]
            ?.filter { it.renderingEndRow < actualRow }
            ?.maxByOrNull { it.renderingEndRow }

        val candidateRow = if (parent != null) {
            actualRow - (parent.renderingEndRow - parent.templateEndRow)
        } else actualRow

        // 3. dead zone 처리: 후보 template row가 밀린 요소의 원래 범위에 해당하지만
        //    actualRow가 그 요소의 렌더링 시작 전이면 -> 해당 요소 직전 행으로 보정.
        //    연쇄적 dead zone(Fixed 뒤에 Bundle 등)도 반복 처리한다.
        var result = candidateRow
        while (true) {
            val owning = resolvedByColumn[column]
                ?.find { result in it.templateStartRow..it.templateEndRow && actualRow < it.finalStartRow }
                ?: break
            result = owning.templateStartRow - 1
        }
        return result
    }

    /** 열 무관 역변환 (모든 repeat의 MAX 기반) */
    private fun reverseCalculateTemplateRow(actualRow: Int): Int {
        val passed = expansions
            .filter { it.region.direction == RepeatDirection.DOWN }
            .filter { expansion ->
                val effectiveItemCount = maxOf(1, collectionSizes[expansion.region.collection] ?: 0)
                val repeatEnd = expansion.finalStartRow + (expansion.region.area.rowRange.count * effectiveItemCount) - 1
                actualRow > repeatEnd
            }

        val totalExpansion = passed
            .groupBy { it.region.area.start.row }
            .values
            .sumOf { group -> group.maxOf { it.rowExpansion } }

        return actualRow - totalExpansion
    }

    companion object {
        /**
         * repeat 영역 간 범위 겹침을 검사한다.
         *
         * @throws TemplateProcessingException repeat 영역이 겹치는 경우
         */
        fun validateNoOverlap(regions: List<RepeatRegionSpec>) {
            for (i in regions.indices) {
                for (j in i + 1 until regions.size) {
                    if (regions[i].area.overlaps(regions[j].area)) {
                        throw TemplateProcessingException(
                            errorType = TemplateProcessingException.ErrorType.INVALID_PARAMETER_VALUE,
                            details = "Repeat regions overlap: " +
                                "${regions[i].collection}(${regions[i].direction}, rows ${regions[i].area.start.row + 1}-" +
                                "${regions[i].area.end.row + 1}, cols ${regions[i].area.start.col + 1}-${regions[i].area.end.col + 1}), " +
                                "${regions[j].collection}(${regions[j].direction}, rows ${regions[j].area.start.row + 1}-" +
                                "${regions[j].area.end.row + 1}, cols ${regions[j].area.start.col + 1}-${regions[j].area.end.col + 1})"
                        )
                    }
                }
            }
        }

        /** 데이터에서 collection 크기 추출 */
        fun extractCollectionSizes(data: Map<String, Any>, repeatRegions: List<RepeatRegionSpec>): CollectionSizes =
            CollectionSizes(repeatRegions.associate { region ->
                region.collection to when (val items = data[region.collection]) {
                    is List<*> -> items.size
                    is Collection<*> -> items.size
                    is Iterable<*> -> items.count()
                    else -> 0
                }
            })
    }
}
