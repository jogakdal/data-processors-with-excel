package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.toColumnIndex
import io.github.jogakdal.tbeg.engine.core.toColumnLetter
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFChart
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.openxmlformats.schemas.drawingml.x2006.chart.*

/**
 * repeat 확장에 따른 차트 데이터 범위 자동 조정
 *
 * 차트 시리즈의 셀 참조(`<c:f>` 태그)를 repeat 확장 결과에 맞게 갱신한다.
 * XSSF/SXSSF 양쪽 모드에서 공용으로 사용한다.
 */
internal object ChartRangeAdjuster {

    /**
     * repeat 확장 정보
     *
     * @property templateStartRow repeat 영역 시작 행 (0-based)
     * @property templateEndRow repeat 영역 끝 행 (0-based)
     * @property templateStartCol repeat 영역 시작 열 (0-based)
     * @property templateEndCol repeat 영역 끝 열 (0-based)
     * @property itemCount 실제 아이템 수
     * @property direction 확장 방향
     */
    data class RepeatExpansionInfo(
        val templateStartRow: Int,
        val templateEndRow: Int,
        val templateStartCol: Int,
        val templateEndCol: Int,
        val itemCount: Int,
        val direction: RepeatDirection
    ) {
        val templateRowCount get() = templateEndRow - templateStartRow + 1
        val templateColCount get() = templateEndCol - templateStartCol + 1
    }

    /** RepeatRegionSpec에서 RepeatExpansionInfo를 생성한다. */
    fun RepeatRegionSpec.toExpansionInfo(itemCount: Int) = RepeatExpansionInfo(
        templateStartRow = area.start.row,
        templateEndRow = area.end.row,
        templateStartCol = area.start.col,
        templateEndCol = area.end.col,
        itemCount = itemCount.coerceAtLeast(1),
        direction = direction
    )

    // 시트 참조 패턴: Sheet1! 또는 'Sheet Name'!
    private const val SHEET_REF_PART = """(?:(?:'(?:[^']|'')*'|[A-Za-z0-9_\uAC00-\uD7A3]+)!)?"""

    /** `<c:f>` 또는 `<f>` 태그 내 수식 참조 패턴 (네임스페이스 프리픽스 유무 모두 매칭) */
    private val CHART_FORMULA_PATTERN = Regex(
        """<(c:)?f>([^<]+)</(c:)?f>"""
    )

    /** 범위 참조 패턴: Sheet1!$A$1:$A$10 등 */
    private val RANGE_REF_PATTERN = Regex(
        """($SHEET_REF_PART)\$?([A-Z]+)\$?(\d+):\$?([A-Z]+)\$?(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** 단일 셀 참조 패턴: Sheet1!$A$3 등 (범위가 아닌 것만 매칭) */
    private val SINGLE_CELL_REF_PATTERN = Regex(
        """($SHEET_REF_PART)\$?([A-Z]+)\$?(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** 시트 참조에서 시트 이름 추출 */
    private fun extractSheetName(sheetRef: String): String? =
        if (sheetRef.isEmpty()) null
        else sheetRef.dropLast(1).let { name ->
            if (name.startsWith("'") && name.endsWith("'"))
                name.drop(1).dropLast(1).replace("''", "'")
            else name
        }

    // ========== XSSF 전용: POI API로 차트 범위 직접 수정 ==========

    /**
     * XSSF 시트의 모든 차트 범위를 repeat 확장에 맞게 조정한다.
     */
    fun adjustChartsInSheet(sheet: XSSFSheet, sheetName: String, expansions: List<RepeatExpansionInfo>) {
        if (expansions.isEmpty()) return
        val drawing = sheet.drawingPatriarch as? XSSFDrawing ?: return

        for (chart in drawing.charts) {
            adjustChart(chart, sheetName, expansions)
        }
    }

    /**
     * XSSF 시트의 모든 드로잉 앵커 위치를 repeat 확장에 맞게 조정한다.
     *
     * 이 시점에서는 TBEG 이미지가 아직 삽입되지 않았으므로
     * 모든 앵커(차트, 도형 등)를 안전하게 시프트할 수 있다.
     */
    fun adjustAnchorsInSheet(sheet: XSSFSheet, expansions: List<RepeatExpansionInfo>) {
        if (expansions.isEmpty()) return
        val drawing = sheet.drawingPatriarch as? XSSFDrawing ?: return

        for (shape in drawing) {
            val anchor = shape.anchor as? XSSFClientAnchor ?: continue
            // twoCellAnchor만 처리 (oneCellAnchor 등은 to 마커가 없어 NPE 발생)
            runCatching {
                val originalColRange = anchor.col1.toInt()..anchor.col2.toInt()
                anchor.row1 = shiftRow(anchor.row1, originalColRange, expansions)
                anchor.row2 = shiftRow(anchor.row2, originalColRange, expansions)
                anchor.setCol1(shiftCol(anchor.col1.toInt(), anchor.row1..anchor.row2, expansions))
                anchor.setCol2(shiftCol(anchor.col2.toInt(), anchor.row1..anchor.row2, expansions))
            }
        }
    }

    /**
     * 단일 행 좌표를 repeat 확장에 따라 시프트한다 (0-based).
     *
     * repeat 영역 **뒤에** 위치한 좌표만 시프트 대상이다.
     * 같은 행 범위의 독립 repeat(멀티 리피트)는 그룹화하여 max 확장량을 사용한다.
     * 앵커의 열 범위와 겹치는 repeat만 고려하여, 열이 다른 repeat의 확장량은 무시한다.
     */
    fun shiftRow(row: Int, anchorColRange: IntRange, expansions: List<RepeatExpansionInfo>): Int {
        var cumulativeOffset = 0
        // 앵커의 열 범위와 겹치는 repeat만 대상
        expansions.filter { it.direction == RepeatDirection.DOWN }
            .filter { anchorColRange.last >= it.templateStartCol && anchorColRange.first <= it.templateEndCol }
            .groupBy { it.templateStartRow to it.templateEndRow }
            .toSortedMap(compareBy { it.first })
            .forEach { (_, group) ->
                val maxExpansion = group.maxOf { (it.itemCount - 1) * it.templateRowCount }
                if (row > group.first().templateEndRow) {
                    cumulativeOffset += maxExpansion
                }
            }
        return row + cumulativeOffset
    }

    /**
     * 단일 열 좌표를 RIGHT 방향 repeat 확장에 따라 시프트한다 (0-based).
     *
     * 앵커의 행 범위가 repeat 영역과 겹칠 때만 적용한다.
     */
    fun shiftCol(col: Int, anchorRowRange: IntRange, expansions: List<RepeatExpansionInfo>): Int {
        var cumulativeOffset = 0
        for (exp in expansions.filter { it.direction == RepeatDirection.RIGHT }) {
            // 행 범위가 repeat 영역과 겹치지 않으면 건너뜀
            if (anchorRowRange.last < exp.templateStartRow || anchorRowRange.first > exp.templateEndRow) continue
            val expansionAmount = (exp.itemCount - 1) * exp.templateColCount
            if (col > exp.templateEndCol) {
                cumulativeOffset += expansionAmount
            }
        }
        return col + cumulativeOffset
    }

    private fun adjustChart(chart: XSSFChart, sheetName: String, expansions: List<RepeatExpansionInfo>) {
        val plotArea = chart.ctChart?.plotArea ?: return

        // 모든 차트 타입의 시리즈를 순회
        collectAllSeriesFormulas(plotArea).forEach { formulaSetter ->
            formulaSetter.adjust(sheetName, expansions)
        }
    }

    /**
     * 모든 차트 타입에서 수식 참조를 수집한다.
     * 각 시리즈의 cat(카테고리), val(값), xVal, yVal, bubbleSize 참조를 포함한다.
     */
    private fun collectAllSeriesFormulas(plotArea: CTPlotArea): List<FormulaSetter> = buildList {
        // Bar chart
        plotArea.barChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Line chart
        plotArea.lineChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Area chart
        plotArea.areaChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Pie chart
        plotArea.pieChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Scatter chart
        plotArea.scatterChartList?.forEach { chart ->
            chart.serList?.forEach { ser ->
                addScatterSeriesFormulas(ser.xVal, ser.yVal, this)
            }
        }
        // Doughnut chart
        plotArea.doughnutChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Radar chart
        plotArea.radarChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Bubble chart
        plotArea.bubbleChartList?.forEach { chart ->
            chart.serList?.forEach { ser ->
                addScatterSeriesFormulas(ser.xVal, ser.yVal, this)
                ser.bubbleSize?.numRef?.let { numRef ->
                    add(FormulaSetter(numRef::getF, numRef::setF))
                }
            }
        }
        // Bar3D chart
        plotArea.bar3DChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Line3D chart
        plotArea.line3DChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Area3D chart
        plotArea.area3DChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Pie3D chart
        plotArea.pie3DChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Stock chart
        plotArea.stockChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Surface chart
        plotArea.surfaceChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // Surface3D chart
        plotArea.surface3DChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
        // OfPie chart
        plotArea.ofPieChartList?.forEach { chart ->
            chart.serList?.forEach { ser -> addSeriesFormulas(ser.cat, ser.`val`, this) }
        }
    }

    /** 일반 시리즈 (cat/val) 수식 추가 */
    private fun addSeriesFormulas(
        cat: CTAxDataSource?,
        value: CTNumDataSource?,
        list: MutableList<FormulaSetter>
    ) {
        cat?.strRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
        cat?.numRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
        value?.numRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
    }

    /** Scatter/Bubble 시리즈 (xVal/yVal) 수식 추가 */
    private fun addScatterSeriesFormulas(
        xVal: CTAxDataSource?,
        yVal: CTNumDataSource?,
        list: MutableList<FormulaSetter>
    ) {
        xVal?.strRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
        xVal?.numRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
        yVal?.numRef?.let { list.add(FormulaSetter(it::getF, it::setF)) }
    }

    /**
     * 수식 getter/setter 래퍼 - 차트 시리즈의 개별 수식 참조
     */
    private class FormulaSetter(
        private val getter: () -> String?,
        private val setter: (String) -> Unit
    ) {
        fun adjust(sheetName: String, expansions: List<RepeatExpansionInfo>) {
            val formula = getter() ?: return
            val adjusted = adjustFormula(formula, sheetName, expansions)
            if (adjusted != formula) setter(adjusted)
        }
    }

    // ========== SXSSF 전용: 차트 XML 문자열 내 수식 조정 ==========

    /**
     * 차트 XML 문자열 내 `<c:f>` 참조를 repeat 확장에 맞게 조정한다.
     *
     * @param chartXml 차트 XML 문자열
     * @param sheetName 현재 시트 이름
     * @param expansions repeat 확장 정보 목록
     * @return 조정된 차트 XML 문자열
     */
    fun adjustChartXml(chartXml: String, sheetName: String, expansions: List<RepeatExpansionInfo>): String {
        if (expansions.isEmpty()) return chartXml

        return CHART_FORMULA_PATTERN.replace(chartXml) { match ->
            val prefix = match.groupValues[1]  // "c:" 또는 ""
            val formula = match.groupValues[2]
            val adjusted = adjustFormula(formula, sheetName, expansions)
            "<${prefix}f>$adjusted</${prefix}f>"
        }
    }

    // ========== 공통 수식 조정 로직 ==========

    /**
     * 차트 수식 내 범위 참조를 조정한다.
     *
     * 조정 규칙 (DOWN 방향):
     * - 범위 시작 행이 repeat 영역 뒤면 → expansionAmount만큼 시프트
     * - 범위 끝 행이 repeat 영역 안이면 → 확장 (itemCount * templateRowCount - 1)
     * - 범위 끝 행이 repeat 영역 뒤면 → expansionAmount만큼 시프트
     *
     * expansions는 **템플릿 행 기준으로 정렬**되어 있어야 하며,
     * 누적 오프셋을 적용하여 다중 repeat를 처리한다.
     */
    internal fun adjustFormula(
        formula: String,
        currentSheetName: String,
        expansions: List<RepeatExpansionInfo>
    ): String {
        // 1단계: 범위 참조 조정
        var result = RANGE_REF_PATTERN.replace(formula) { match ->
            val refSheetRef = match.groupValues[1]
            val refSheetName = extractSheetName(refSheetRef)

            if (refSheetName != null && refSheetName != currentSheetName) {
                match.value
            } else {
                val startCol = match.groupValues[2].uppercase()
                val startRow = match.groupValues[3].toInt()
                val endCol = match.groupValues[4].uppercase()
                val endRow = match.groupValues[5].toInt()

                val (adjustedStartRow, adjustedEndRow) = adjustRowRange(startRow, endRow, expansions)
                val (adjustedStartColIdx, adjustedEndColIdx) = adjustColRange(startCol, endCol, startRow, endRow, expansions)

                val adjustedStartCol = adjustedStartColIdx.toColumnLetter()
                val adjustedEndCol = adjustedEndColIdx.toColumnLetter()
                "${refSheetRef}\$$adjustedStartCol\$$adjustedStartRow:\$$adjustedEndCol\$$adjustedEndRow"
            }
        }

        // 결과에서 범위 위치 추출 (단일 셀 매칭에서 제외하기 위함)
        val rangePositions = RANGE_REF_PATTERN.findAll(result).map { it.range }.toList()

        // 2단계: 단일 셀 참조를 repeat 영역에 따라 범위로 확장
        result = SINGLE_CELL_REF_PATTERN.replace(result) { match ->
            // 이미 범위 참조의 일부이면 건너뜀
            if (rangePositions.any { match.range.first >= it.first && match.range.last <= it.last }) {
                match.value
            } else {
                val refSheetRef = match.groupValues[1]
                val refSheetName = extractSheetName(refSheetRef)

                if (refSheetName != null && refSheetName != currentSheetName) {
                    match.value
                } else {
                    val col = match.groupValues[2].uppercase()
                    val row = match.groupValues[3].toInt()  // 1-based

                    expandSingleCellToRange(refSheetRef, col, row, expansions) ?: match.value
                }
            }
        }

        return result
    }

    /**
     * 단일 셀 참조가 repeat 영역 안에 있으면 범위로 확장한다.
     * POI가 단일 행 범위를 단일 셀 참조로 축약하기 때문에 필요하다.
     *
     * @return 확장된 범위 문자열, 또는 확장 불필요하면 null
     */
    private fun expandSingleCellToRange(
        sheetRef: String,
        col: String,
        row: Int,
        expansions: List<RepeatExpansionInfo>
    ): String? {
        val colIdx = col.toColumnIndex()
        for (exp in expansions.filter { it.direction == RepeatDirection.DOWN }) {
            val templateStartRow1 = exp.templateStartRow + 1
            val templateEndRow1 = exp.templateEndRow + 1

            // 행과 열 모두 repeat 영역 안에 있어야 해당 확장을 적용한다
            if (row in templateStartRow1..templateEndRow1
                && colIdx in exp.templateStartCol..exp.templateEndCol
                && exp.itemCount > 1) {
                val endRow = templateStartRow1 + (exp.itemCount * exp.templateRowCount) - 1
                return "$sheetRef\$$col\$$row:\$$col\$$endRow"
            }
        }

        for (exp in expansions.filter { it.direction == RepeatDirection.RIGHT }) {
            val templateStartRow1 = exp.templateStartRow + 1
            val templateEndRow1 = exp.templateEndRow + 1
            if (row in templateStartRow1..templateEndRow1
                && colIdx in exp.templateStartCol..exp.templateEndCol
                && exp.itemCount > 1) {
                val endColIdx = exp.templateStartCol + (exp.itemCount * exp.templateColCount) - 1
                return "$sheetRef\$$col\$$row:\$${endColIdx.toColumnLetter()}\$$row"
            }
        }

        return null
    }

    /**
     * DOWN 방향 repeat에 대해 행 범위를 조정한다.
     * 같은 행 범위의 독립 repeat(멀티 리피트)는 그룹화하여 max 확장량을 사용한다.
     *
     * @return Pair(adjustedStartRow, adjustedEndRow) (1-based)
     */
    private fun adjustRowRange(
        startRow: Int,
        endRow: Int,
        expansions: List<RepeatExpansionInfo>
    ): Pair<Int, Int> {
        var adjustedStart = startRow
        var adjustedEnd = endRow
        var cumulativeOffset = 0

        // 같은 행 범위의 repeat를 그룹화
        val grouped = expansions.filter { it.direction == RepeatDirection.DOWN }
            .groupBy { it.templateStartRow to it.templateEndRow }
            .toSortedMap(compareBy { it.first })

        for ((_, group) in grouped) {
            val templateStartRow1 = group.first().templateStartRow + 1  // 0-based → 1-based
            val templateEndRow1 = group.first().templateEndRow + 1

            val expansionAmount = group.maxOf { (it.itemCount - 1) * it.templateRowCount }

            // 시작 행 조정
            if (startRow > templateEndRow1) {
                // repeat 영역 뒤의 시작점 → 누적 오프셋 + 이번 확장
                adjustedStart = startRow + cumulativeOffset + expansionAmount
            } else {
                adjustedStart = startRow + cumulativeOffset
            }

            // 끝 행 조정
            val maxExpandedTotal = group.maxOf { it.itemCount * it.templateRowCount }
            if (endRow in templateStartRow1..templateEndRow1) {
                // repeat 영역 안의 끝점 → 확장
                adjustedEnd = templateStartRow1 + cumulativeOffset + maxExpandedTotal - 1
            } else if (endRow > templateEndRow1) {
                // repeat 영역 뒤의 끝점 → 시프트
                adjustedEnd = endRow + cumulativeOffset + expansionAmount
            } else {
                adjustedEnd = endRow + cumulativeOffset
            }

            cumulativeOffset += expansionAmount
        }

        return adjustedStart to adjustedEnd
    }

    /**
     * RIGHT 방향 repeat에 대해 열 범위를 조정한다.
     * 같은 열 범위의 독립 repeat(멀티 리피트)는 그룹화하여 max 확장량을 사용한다.
     *
     * @return Pair(adjustedStartColIndex, adjustedEndColIndex) (0-based)
     */
    private fun adjustColRange(
        startCol: String,
        endCol: String,
        startRow: Int,
        endRow: Int,
        expansions: List<RepeatExpansionInfo>
    ): Pair<Int, Int> {
        val startColIdx = startCol.toColumnIndex()
        val endColIdx = endCol.toColumnIndex()
        var adjustedStartIdx = startColIdx
        var adjustedEndIdx = endColIdx
        var cumulativeOffset = 0

        // 같은 열 범위의 repeat를 그룹화하고, 열 순서로 정렬
        val grouped = expansions.filter { it.direction == RepeatDirection.RIGHT }
            .filter { exp ->
                val templateStartRow1 = exp.templateStartRow + 1
                val templateEndRow1 = exp.templateEndRow + 1
                !(startRow > templateEndRow1 || endRow < templateStartRow1)
            }
            .groupBy { it.templateStartCol to it.templateEndCol }
            .toSortedMap(compareBy { it.first })

        for ((_, group) in grouped) {
            val templateStartCol = group.first().templateStartCol
            val templateEndCol = group.first().templateEndCol

            val expansionAmount = group.maxOf { (it.itemCount - 1) * it.templateColCount }

            // 시작 열 조정
            if (startColIdx > templateEndCol) {
                adjustedStartIdx = startColIdx + cumulativeOffset + expansionAmount
            } else {
                adjustedStartIdx = startColIdx + cumulativeOffset
            }

            // 끝 열 조정
            val maxExpandedTotal = group.maxOf { it.itemCount * it.templateColCount }
            if (endColIdx in templateStartCol..templateEndCol) {
                adjustedEndIdx = templateStartCol + cumulativeOffset + maxExpandedTotal - 1
            } else if (endColIdx > templateEndCol) {
                adjustedEndIdx = endColIdx + cumulativeOffset + expansionAmount
            } else {
                adjustedEndIdx = endColIdx + cumulativeOffset
            }

            cumulativeOffset += expansionAmount
        }

        return adjustedStartIdx to adjustedEndIdx
    }
}
