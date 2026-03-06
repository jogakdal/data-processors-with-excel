package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.*
import io.github.jogakdal.tbeg.exception.TemplateProcessingException

/**
 * 셀 참조 정보를 담는 데이터 클래스
 *
 * @param sheetRef 시트 참조 (예: "Sheet1!", "'Sheet Name'!", 없으면 빈 문자열)
 * @param colAbs 열 절대 참조 기호 ("$" 또는 "")
 * @param col 열 문자 (예: "A", "BC")
 * @param rowAbs 행 절대 참조 기호 ("$" 또는 "")
 * @param row 행 번호 (1-based)
 */
private data class CellRef(
    val sheetRef: String,
    val colAbs: String,
    val col: String,
    val rowAbs: String,
    val row: Int
) {
    val isRowAbsolute get() = rowAbs == "$"
    val isColAbsolute get() = colAbs == "$"
    val hasSheetRef get() = sheetRef.isNotEmpty()

    /** 시트 참조에서 시트 이름 추출 (예: "Sheet1!" -> "Sheet1", "'Data Sheet'!" -> "Data Sheet") */
    val sheetName: String?
        get() = if (!hasSheetRef) null
        else sheetRef.dropLast(1).let { name ->
            if (name.startsWith("'") && name.endsWith("'"))
                name.drop(1).dropLast(1).replace("''", "'")
            else name
        }

    fun format(newRow: Int = row, newCol: String = col) = "$sheetRef$colAbs$newCol$rowAbs$newRow"
}

/** MatchResult를 CellRef로 변환 (CELL_REF_PATTERN용) */
private fun MatchResult.toCellRef() = CellRef(
    sheetRef = groupValues[1],
    colAbs = groupValues[2],
    col = groupValues[3].uppercase(),
    rowAbs = groupValues[4],
    row = groupValues[5].toInt()
)

/**
 * 범위 참조 정보를 담는 데이터 클래스
 *
 * @param sheetRef 시트 참조 (범위 전체에 적용, 예: "Sheet1!")
 * @param start 시작 셀 참조
 * @param end 끝 셀 참조
 */
private data class RangeRef(
    val sheetRef: String,
    val start: CellRef,
    val end: CellRef
) {
    val hasSheetRef get() = sheetRef.isNotEmpty()

    /** 시트 참조에서 시트 이름 추출 (예: "Sheet1!" -> "Sheet1", "'Data Sheet'!" -> "Data Sheet") */
    val sheetName: String?
        get() = if (!hasSheetRef) null
        else sheetRef.dropLast(1).let { name ->
            if (name.startsWith("'") && name.endsWith("'"))
                name.drop(1).dropLast(1).replace("''", "'")
            else name
        }

    fun format(
        newStartRow: Int = start.row,
        newStartCol: String = start.col,
        newEndRow: Int = end.row,
        newEndCol: String = end.col
    ) = "$sheetRef${start.colAbs}$newStartCol${start.rowAbs}$newStartRow:${end.colAbs}$newEndCol${end.rowAbs}$newEndRow"
}

/** MatchResult를 RangeRef로 변환 (RANGE_CAPTURE_PATTERN용) */
private fun MatchResult.toRangeRef() = RangeRef(
    sheetRef = groupValues[1],
    start = CellRef("", groupValues[2], groupValues[3].uppercase(), groupValues[4], groupValues[5].toInt()),
    end = CellRef("", groupValues[6], groupValues[7].uppercase(), groupValues[8], groupValues[9].toInt())
)

/**
 * 수식 참조 조정기 - 반복 처리로 인한 행 오프셋에 따라 수식 내 셀 참조 조정
 *
 * 스트리밍 모드에서는 POI의 shiftRows()를 사용할 수 없으므로
 * 수식 참조를 직접 조정해야 한다.
 *
 * 지원하는 참조 형식:
 * - 단일 셀: A1, $A$1, $A1, A$1
 * - 시트 참조: Sheet1!A1, 'Sheet Name'!A1
 * - 범위: A1:B10, $A$1:$B$10
 * - 시트 참조 범위: Sheet1!A1:B10, 'Sheet Name'!A1:B10
 *
 * 처리 원칙:
 * - 절대 참조($)가 있는 행/열은 조정하지 않음
 * - 다른 시트 참조는 조정하지 않음 (현재 시트의 repeat 영역과 무관)
 */
object FormulaAdjuster {
    // 시트 참조 패턴: Sheet1! 또는 'Sheet Name'! (작은따옴표 내 ''로 이스케이프된 ' 포함)
    private const val SHEET_REF_PART = """(?:(?:'(?:[^']|'')*'|[A-Za-z0-9_]+)!)?"""

    /** 셀 참조 패턴: A1, $A$1, Sheet1!A1, 'Sheet Name'!$A$1 등 */
    private val CELL_REF_PATTERN = Regex(
        """($SHEET_REF_PART)(\$?)([A-Z]+)(\$?)(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** 범위 참조 패턴: A1:B10, Sheet1!A1:B10 (시트 참조는 범위 전체에 적용) */
    private val RANGE_PATTERN = Regex(
        """$SHEET_REF_PART\$?[A-Z]+\$?\d+:\$?[A-Z]+\$?\d+""",
        RegexOption.IGNORE_CASE
    )

    /** 범위 참조 패턴 - 그룹 캡처 버전 (시트참조, 시작셀, 끝셀 분리) */
    private val RANGE_CAPTURE_PATTERN = Regex(
        """($SHEET_REF_PART)(\$?)([A-Z]+)(\$?)(\d+):(\$?)([A-Z]+)(\$?)(\d+)""",
        RegexOption.IGNORE_CASE
    )

    /** 매치가 범위 내에 포함되는지 확인 */
    private fun isPartOfRange(match: MatchResult, ranges: List<IntRange>) =
        ranges.any { match.range.first >= it.first && match.range.last <= it.last }

    /** 수식에서 범위 참조 위치 목록 추출 */
    private fun findRangePositions(formula: String) = RANGE_PATTERN.findAll(formula).map { it.range }.toList()


    /**
     * 현재 행 위치에서의 수식을 반복 인덱스에 맞게 조정
     *
     * 반복 영역 내 수식이 각 반복 항목에 맞게 조정됨
     *
     * @param formula 원본 수식
     * @param repeatIndex 반복 인덱스 (0부터 시작)
     * @param repeatArea repeat 영역 (0-based). null이 아니면 영역 밖 참조는 건너뜀 (절대/상대 무관)
     * @return 조정된 수식
     */
    fun adjustForRepeatIndex(formula: String, repeatIndex: Int, repeatArea: CellArea? = null) =
        if (repeatIndex == 0) formula
        else findRangePositions(formula).let { ranges ->
            CELL_REF_PATTERN.replace(formula) { match ->
                if (isPartOfRange(match, ranges)) {
                    // 범위 참조의 일부면 별도 처리 필요 없음 (범위 전체가 처리됨)
                    match.value
                } else {
                    match.toCellRef().run {
                        // 다른 시트 참조 또는 절대 행 참조면 조정하지 않음
                        if (hasSheetRef || isRowAbsolute) match.value
                        // repeatArea가 주어지면 영역 밖 참조는 건너뜀 (복사 이동 방지)
                        else if (repeatArea != null && (row - 1) !in repeatArea.rowRange) match.value
                        else format(newRow = row + repeatIndex)
                    }
                }
            }
        }

    /**
     * RIGHT repeat에서 반복 인덱스에 따라 수식 내 열 참조를 시프트한다.
     *
     * [adjustForRepeatIndex]의 열 방향 버전으로, RIGHT 방향 반복의 각 아이템에
     * 복제된 수식의 열 참조를 올바른 위치로 조정한다.
     *
     * @param formula 원본 수식
     * @param columnShift 열 이동량 (itemIdx x colRangeCount)
     * @param repeatArea repeat 영역 (0-based). null이 아니면 영역 밖 참조는 건너뜀
     * @param outOfAreaShift repeat 영역 오른쪽에 있는 참조의 시프트량.
     *   repeat 확장으로 영역 오른쪽 셀이 밀리므로 해당 참조도 조정해야 한다.
     * @return 조정된 수식
     *
     * 예: =C9-C10, columnShift=1, repeatArea=C8:C13
     *   -> =D9-D10 (C는 repeat 영역 내이므로 시프트)
     *
     * 예: =C18/D18, columnShift=0, repeatArea=C17:C19, outOfAreaShift=3
     *   -> =C18/G18 (D는 repeat 영역 오른쪽이므로 outOfAreaShift 적용)
     */
    fun adjustForRepeatColumnIndex(
        formula: String,
        columnShift: Int,
        repeatArea: CellArea? = null,
        outOfAreaShift: Int = 0
    ): String {
        if (columnShift == 0 && outOfAreaShift == 0) return formula
        return findRangePositions(formula).let { ranges ->
            CELL_REF_PATTERN.replace(formula) { match ->
                if (isPartOfRange(match, ranges)) {
                    match.value
                } else {
                    match.toCellRef().run {
                        // 다른 시트 참조 또는 절대 열 참조면 조정하지 않음
                        if (hasSheetRef || isColAbsolute) match.value
                        else {
                            val colIndex = toColumnIndex(col)
                            if (repeatArea != null &&
                                (colIndex !in repeatArea.colRange || (row - 1) !in repeatArea.rowRange)
                            ) {
                                // 영역 밖 참조: 오른쪽이면 outOfAreaShift 적용
                                if (outOfAreaShift > 0 && colIndex > repeatArea.colRange.end)
                                    format(newCol = toColumnLetter(colIndex + outOfAreaShift))
                                else match.value
                            } else format(newCol = toColumnLetter(colIndex + columnShift))
                        }
                    }
                }
            }
        }
    }

    /**
     * RIGHT repeat의 non-repeat 셀 수식을 단일 패스로 조정한다.
     *
     * 순차 파이프라인(shift -> expand)의 좌표계 충돌 문제를 회피하기 위해,
     * 각 참조를 **템플릿 위치** 기준으로 한 번에 처리한다:
     * - RIGHT repeat 영역 내부 -> 열 방향으로 범위 확장
     * - 그 외 -> getFinalPosition으로 최종 위치 시프트
     *
     * @param formula 원본 수식 (템플릿 좌표 기준)
     * @param calculator 위치 계산기
     * @param repeatRegions repeat 영역 목록
     * @param expansionProvider region -> (expansion, itemCount) 제공자
     */
    fun adjustFormulaForRightNonRepeat(
        formula: String,
        calculator: PositionCalculator,
        repeatRegions: List<RepeatRegionSpec>,
        expansionProvider: (RepeatRegionSpec) -> Pair<PositionCalculator.RepeatExpansion, Int>?
    ): String {
        val normalizedFormula = normalizeSingleCellRanges(formula)

        fun findContainingRightRepeat(rowIdx: Int, colIdx: Int) =
            repeatRegions.find { it.direction == RepeatDirection.RIGHT &&
                rowIdx in it.area.rowRange && colIdx in it.area.colRange }

        fun isInDownRepeat(rowIdx: Int, colIdx: Int) =
            repeatRegions.any { it.direction == RepeatDirection.DOWN &&
                rowIdx in it.area.rowRange && colIdx in it.area.colRange }

        // 1. 범위 참조 처리
        var result = RANGE_CAPTURE_PATTERN.replace(normalizedFormula) { match ->
            val range = match.toRangeRef()
            if (range.hasSheetRef) return@replace match.value

            val startRow = range.start.row - 1
            val startCol = toColumnIndex(range.start.col)
            val endRow = range.end.row - 1
            val endCol = toColumnIndex(range.end.col)

            val startRepeat = findContainingRightRepeat(startRow, startCol)
            val endRepeat = findContainingRightRepeat(endRow, endCol)

            when {
                // 양 끝 모두 같은 RIGHT repeat -> 열 방향 확장
                startRepeat != null && startRepeat == endRepeat && !range.start.isColAbsolute -> {
                    val (expansion, itemCount) = expansionProvider(startRepeat)
                        ?: return@replace match.value
                    if (itemCount <= 1) return@replace match.value

                    val colCount = startRepeat.area.colRange.count
                    val newStartCol = expansion.finalStartCol + (startCol - startRepeat.area.start.col)
                    val newEndCol = expansion.finalStartCol + ((itemCount - 1) * colCount) +
                        (endCol - startRepeat.area.start.col)
                    val newStartRow = expansion.finalStartRow + (startRow - startRepeat.area.start.row) + 1
                    val newEndRow = expansion.finalStartRow + (endRow - startRepeat.area.start.row) + 1

                    range.format(
                        newStartRow = newStartRow, newStartCol = toColumnLetter(newStartCol),
                        newEndRow = newEndRow, newEndCol = toColumnLetter(newEndCol)
                    )
                }
                // 양 끝 모두 repeat 외부 -> 최종 위치로 시프트
                startRepeat == null && endRepeat == null &&
                    !isInDownRepeat(startRow, startCol) && !isInDownRepeat(endRow, endCol) -> {
                    val (r1, c1) = calculator.getFinalPosition(startRow, startCol)
                    val (r2, c2) = calculator.getFinalPosition(endRow, endCol)
                    range.format(
                        newStartRow = if (range.start.isRowAbsolute) range.start.row else r1 + 1,
                        newStartCol = if (range.start.isColAbsolute) range.start.col else toColumnLetter(c1),
                        newEndRow = if (range.end.isRowAbsolute) range.end.row else r2 + 1,
                        newEndCol = if (range.end.isColAbsolute) range.end.col else toColumnLetter(c2)
                    )
                }
                else -> match.value
            }
        }

        // 2. 단일 셀 참조 처리
        val newRanges = findRangePositions(result)

        result = CELL_REF_PATTERN.replace(result) { match ->
            if (isPartOfRange(match, newRanges)) return@replace match.value

            val ref = match.toCellRef()
            if (ref.hasSheetRef || (ref.isRowAbsolute && ref.isColAbsolute)) return@replace match.value

            val rowIdx = ref.row - 1
            val colIdx = toColumnIndex(ref.col)
            val containingRepeat = findContainingRightRepeat(rowIdx, colIdx)

            when {
                // RIGHT repeat 영역 내부 -> 열 방향 확장
                containingRepeat != null && !ref.isColAbsolute -> {
                    val (expansion, itemCount) = expansionProvider(containingRepeat)
                        ?: return@replace match.value
                    if (itemCount <= 1) return@replace match.value

                    val colCount = containingRepeat.area.colRange.count
                    val relRow = expansion.finalStartRow + (rowIdx - containingRepeat.area.start.row) + 1
                    val startCol = expansion.finalStartCol + (colIdx - containingRepeat.area.start.col)
                    val endCol = startCol + ((itemCount - 1) * colCount)
                    "${ref.colAbs}${toColumnLetter(startCol)}${ref.rowAbs}$relRow:" +
                        "${ref.colAbs}${toColumnLetter(endCol)}${ref.rowAbs}$relRow"
                }
                // DOWN repeat 영역 내부 -> 건너뜀 (별도 처리 대상)
                isInDownRepeat(rowIdx, colIdx) -> match.value
                // repeat 외부 -> 최종 위치로 시프트
                else -> {
                    val (newRow, newCol) = calculator.getFinalPosition(rowIdx, colIdx)
                    ref.format(
                        newRow = if (ref.isRowAbsolute) ref.row else newRow + 1,
                        newCol = if (ref.isColAbsolute) ref.col else toColumnLetter(newCol)
                    )
                }
            }
        }

        return result
    }

    // ========== PositionCalculator 연동 메서드 ==========

    /**
     * PositionCalculator를 사용하여 수식 내 셀 참조의 위치를 조정한다.
     *
     * 모든 셀 참조가 PositionCalculator의 getFinalPosition()을 통해
     * 최종 위치로 변환된다.
     *
     * @param formula 원본 수식
     * @param calculator 위치 계산기
     * @return 조정된 수식
     */
    fun adjustWithPositionCalculator(formula: String, calculator: PositionCalculator): String {
        // 범위 참조 먼저 처리
        var result = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
            val range = match.toRangeRef()

            // 다른 시트 참조면 조정하지 않음
            if (range.hasSheetRef) {
                match.value
            } else {
                // 절대 참조 여부에 따라 위치 계산
                val (newStartRow, newStartCol) = if (range.start.isRowAbsolute && range.start.isColAbsolute) {
                    range.start.row to toColumnIndex(range.start.col)
                } else {
                    val (r, c) = calculator.getFinalPosition(range.start.row - 1, toColumnIndex(range.start.col))
                    val finalRow = if (range.start.isRowAbsolute) range.start.row else r + 1
                    val finalCol = if (range.start.isColAbsolute) toColumnIndex(range.start.col) else c
                    finalRow to finalCol
                }

                val (newEndRow, newEndCol) = if (range.end.isRowAbsolute && range.end.isColAbsolute) {
                    range.end.row to toColumnIndex(range.end.col)
                } else {
                    val (r, c) = calculator.getFinalPosition(range.end.row - 1, toColumnIndex(range.end.col))
                    val finalRow = if (range.end.isRowAbsolute) range.end.row else r + 1
                    val finalCol = if (range.end.isColAbsolute) toColumnIndex(range.end.col) else c
                    finalRow to finalCol
                }

                range.format(
                    newStartRow = newStartRow,
                    newStartCol = toColumnLetter(newStartCol),
                    newEndRow = newEndRow,
                    newEndCol = toColumnLetter(newEndCol)
                )
            }
        }

        // 단일 셀 참조 처리 (범위 외부만)
        val newRanges = findRangePositions(result)

        result = CELL_REF_PATTERN.replace(result) { match ->
            if (isPartOfRange(match, newRanges)) {
                match.value
            } else {
                val ref = match.toCellRef()

                // 다른 시트 참조면 조정하지 않음
                if (ref.hasSheetRef) {
                    match.value
                } else if (ref.isRowAbsolute && ref.isColAbsolute) {
                    match.value
                } else {
                    val (newRow, newCol) = calculator.getFinalPosition(ref.row - 1, toColumnIndex(ref.col))
                    val finalRow = if (ref.isRowAbsolute) ref.row else newRow + 1
                    val finalCol = if (ref.isColAbsolute) ref.col else toColumnLetter(newCol)
                    "${ref.colAbs}$finalCol${ref.rowAbs}$finalRow"
                }
            }
        }

        return result
    }

    /**
     * PositionCalculator를 사용하여 수식 내 셀 참조의 위치를 조정하되,
     * repeat 영역 내부의 참조는 건너뛴다.
     *
     * 정적 행의 수식 처리에 사용한다.
     * repeat 영역 내 참조는 나중에 expandToRangeWithCalculator로 범위 확장되므로,
     * 여기서 이동시키면 template 좌표 기준 확장이 실패한다.
     *
     * @param formula 원본 수식
     * @param calculator 위치 계산기
     * @param repeatRegions repeat 영역 목록
     * @return 조정된 수식
     */
    fun adjustWithPositionCalculatorSkippingRepeatAreas(
        formula: String,
        calculator: PositionCalculator,
        repeatRegions: List<RepeatRegionSpec>
    ): String {
        /** 참조의 템플릿 위치가 DOWN repeat 영역 내부(행/열 모두 범위 안)인지 확인 */
        fun isInsideRepeatArea(rowIndex: Int, colIndex: Int) =
            repeatRegions.any { region ->
                region.direction == RepeatDirection.DOWN &&
                    rowIndex in region.area.rowRange &&
                    colIndex in region.area.colRange
            }

        // 범위 참조 먼저 처리
        var result = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
            val range = match.toRangeRef()

            if (range.hasSheetRef) {
                match.value
            } else {
                val startRowIndex = range.start.row - 1
                val startColIndex = toColumnIndex(range.start.col)
                val endRowIndex = range.end.row - 1
                val endColIndex = toColumnIndex(range.end.col)

                // 시작 또는 끝이 repeat 영역 내부이면 건너뜀
                if (isInsideRepeatArea(startRowIndex, startColIndex) ||
                    isInsideRepeatArea(endRowIndex, endColIndex)
                ) {
                    match.value
                } else {
                    val (newStartRow, newStartCol) = if (range.start.isRowAbsolute && range.start.isColAbsolute) {
                        range.start.row to startColIndex
                    } else {
                        val (r, c) = calculator.getFinalPosition(startRowIndex, startColIndex)
                        val finalRow = if (range.start.isRowAbsolute) range.start.row else r + 1
                        val finalCol = if (range.start.isColAbsolute) startColIndex else c
                        finalRow to finalCol
                    }

                    val (newEndRow, newEndCol) = if (range.end.isRowAbsolute && range.end.isColAbsolute) {
                        range.end.row to endColIndex
                    } else {
                        val (r, c) = calculator.getFinalPosition(endRowIndex, endColIndex)
                        val finalRow = if (range.end.isRowAbsolute) range.end.row else r + 1
                        val finalCol = if (range.end.isColAbsolute) endColIndex else c
                        finalRow to finalCol
                    }

                    range.format(
                        newStartRow = newStartRow,
                        newStartCol = toColumnLetter(newStartCol),
                        newEndRow = newEndRow,
                        newEndCol = toColumnLetter(newEndCol)
                    )
                }
            }
        }

        // 단일 셀 참조 처리 (범위 외부만)
        val newRanges = findRangePositions(result)

        result = CELL_REF_PATTERN.replace(result) { match ->
            if (isPartOfRange(match, newRanges)) {
                match.value
            } else {
                val ref = match.toCellRef()

                if (ref.hasSheetRef) {
                    match.value
                } else if (ref.isRowAbsolute && ref.isColAbsolute) {
                    match.value
                } else {
                    val rowIndex = ref.row - 1
                    val colIndex = toColumnIndex(ref.col)

                    // repeat 영역 내부의 참조는 건너뜀
                    if (isInsideRepeatArea(rowIndex, colIndex)) {
                        match.value
                    } else {
                        val (newRow, newCol) = calculator.getFinalPosition(rowIndex, colIndex)
                        val finalRow = if (ref.isRowAbsolute) ref.row else newRow + 1
                        val finalCol = if (ref.isColAbsolute) ref.col else toColumnLetter(newCol)
                        "${ref.colAbs}$finalCol${ref.rowAbs}$finalRow"
                    }
                }
            }
        }

        return result
    }

    /**
     * 시트별 확장 정보를 담는 데이터 클래스
     *
     * @param expansions 해당 시트의 repeat 확장 정보 목록
     * @param collectionSizes 컬렉션별 아이템 수
     */
    data class SheetExpansionInfo(
        val expansions: List<PositionCalculator.RepeatExpansion>,
        val collectionSizes: CollectionSizes
    )

    /**
     * 완전 상대 참조인 단일 셀 범위를 단일 셀 참조로 정규화한다.
     *
     * 예: B8:B8 -> B8, Sheet1!C3:C3 -> Sheet1!C3
     *
     * 정규화하지 않는 경우:
     * - 절대 참조 ($B$3:$B$3, B$3:B$3, $B3:$B3) - 기존 확장 로직에서 의도적으로 다르게 처리
     *
     * 이 정규화를 통해 완전 상대 참조 단일 셀 범위가 범위 확장 로직에서 연속 범위로 처리되는 것을 방지하고,
     * 대신 단일 셀 참조로 처리되어 templateRowCount > 1인 경우 비연속 셀 나열(B8,B10,B12) 형태로 확장된다.
     */
    private fun normalizeSingleCellRanges(formula: String) = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
        val range = match.toRangeRef()
        // 시작 셀과 끝 셀이 동일하고, 완전 상대 참조인 경우에만 단일 셀로 변환
        if (range.start.col.equals(range.end.col, ignoreCase = true) &&
            range.start.row == range.end.row &&
            !range.start.isColAbsolute && !range.start.isRowAbsolute &&
            !range.end.isColAbsolute && !range.end.isRowAbsolute
        ) {
            // 단일 셀 참조로 변환: [시트참조]열행
            "${range.sheetRef}${range.start.col}${range.start.row}"
        } else {
            match.value
        }
    }

    /**
     * repeat 영역 내 셀 참조를 범위로 확장한다 (PositionCalculator 사용).
     *
     * repeat 영역 외부의 수식에서 반복 영역 내 셀을 참조하는 경우,
     * 해당 참조를 확장된 범위로 변환한다.
     *
     * 지원 케이스:
     * - 단일 셀 참조: B3 -> B3:B7
     * - 범위 참조: B3:B3 -> B3:B7 (끝 셀이 repeat 영역 내에 있으면 확장)
     * - 다른 시트 참조: Sheet2!B3:B3 -> Sheet2!B3:B5 (해당 시트의 확장 정보가 있으면 적용)
     *
     * 제외 케이스:
     * - 절대 참조: $B$3, B$3 (행 절대), $B3 (열 절대)
     *
     * @param formula 원본 수식
     * @param expansion 대상 repeat 확장 정보
     * @param itemCount 반복 아이템 수
     * @param otherSheetExpansions 다른 시트의 확장 정보 (시트 이름 -> 확장 정보)
     * @return 확장된 수식과 비연속 참조 여부
     */
    fun expandToRangeWithCalculator(
        formula: String,
        expansion: PositionCalculator.RepeatExpansion,
        itemCount: Int,
        otherSheetExpansions: Map<String, SheetExpansionInfo> = emptyMap()
    ): FormulaExpansionResult {
        if (itemCount <= 1 && otherSheetExpansions.isEmpty()) return FormulaExpansionResult(formula, false)

        // 0. 단일 셀 범위를 단일 셀 참조로 정규화 (B8:B8 -> B8)
        val normalizedFormula = normalizeSingleCellRanges(formula)

        val region = expansion.region
        val templateRowCount = region.area.rowRange.count
        val templateColCount = region.area.colRange.count

        var isSequential = true

        // 1. 먼저 범위 참조 처리 (B3:B5 -> B3:B9)
        var result = expandRangeReferencesWithCalculator(
            normalizedFormula, expansion, itemCount, region, templateRowCount, templateColCount, otherSheetExpansions
        )

        // 2. 단일 셀 참조 처리 (B3 -> B3:B7)
        val newRanges = findRangePositions(result)

        result = CELL_REF_PATTERN.replace(result) { match ->
            if (isPartOfRange(match, newRanges)) {
                match.value
            } else {
                val ref = match.toCellRef()
                val rowIndex = ref.row - 1
                val colIndex = toColumnIndex(ref.col)

                // 다른 시트 참조인 경우
                if (ref.hasSheetRef) {
                    val sheetName = ref.sheetName
                    val sheetInfo = sheetName?.let { otherSheetExpansions[it] }
                    if (sheetInfo == null) {
                        match.value
                    } else {
                        expandCellRefForOtherSheet(ref, rowIndex, colIndex, sheetInfo) ?: match.value
                    }
                } else {
                    // 현재 시트 참조
                    if (itemCount <= 1) {
                        match.value
                    } else if (rowIndex !in region.area.rowRange || colIndex !in region.area.colRange) {
                        match.value
                    } else if (ref.isRowAbsolute && region.direction == RepeatDirection.DOWN) {
                        match.value
                    } else if (ref.isColAbsolute && region.direction == RepeatDirection.RIGHT) {
                        match.value
                    } else {
                        when (region.direction) {
                            RepeatDirection.DOWN -> {
                                if (templateRowCount == 1) {
                                    val startRow = expansion.finalStartRow + (rowIndex - region.area.start.row) + 1
                                    val endRow = startRow + (itemCount - 1)
                                    val col = toColumnLetter(colIndex)
                                    "${ref.colAbs}$col${ref.rowAbs}$startRow:${ref.colAbs}$col${ref.rowAbs}$endRow"
                                } else {
                                    isSequential = false
                                    val relativeRow = rowIndex - region.area.start.row
                                    val cells = (0 until itemCount).map { idx ->
                                        val newRow = expansion.finalStartRow + (idx * templateRowCount) + relativeRow + 1
                                        val col = toColumnLetter(colIndex)
                                        "${ref.colAbs}$col${ref.rowAbs}$newRow"
                                    }
                                    cells.joinToString(",")
                                }
                            }
                            RepeatDirection.RIGHT -> {
                                if (templateColCount == 1) {
                                    val startCol = expansion.finalStartCol + (colIndex - region.area.start.col)
                                    val endCol = startCol + (itemCount - 1)
                                    val row = ref.row
                                    "${ref.colAbs}${toColumnLetter(startCol)}${ref.rowAbs}$row:${ref.colAbs}${toColumnLetter(endCol)}${ref.rowAbs}$row"
                                } else {
                                    isSequential = false
                                    val relativeCol = colIndex - region.area.start.col
                                    val cells = (0 until itemCount).map { idx ->
                                        val newCol = expansion.finalStartCol + (idx * templateColCount) + relativeCol
                                        "${ref.colAbs}${toColumnLetter(newCol)}${ref.rowAbs}${ref.row}"
                                    }
                                    cells.joinToString(",")
                                }
                            }
                        }
                    }
                }
            }
        }

        return FormulaExpansionResult(result, isSequential)
    }

    /** 다른 시트의 단일 셀 참조를 확장 (해당 시트의 repeat 영역에 포함되면 범위로 확장) */
    private fun expandCellRefForOtherSheet(
        ref: CellRef,
        rowIndex: Int,
        colIndex: Int,
        sheetInfo: SheetExpansionInfo
    ): String? {
        // 해당 시트의 모든 확장 정보를 확인하여 참조가 repeat 영역에 포함되는지 확인
        for (expansion in sheetInfo.expansions) {
            val region = expansion.region
            val itemCount = sheetInfo.collectionSizes[region.collection] ?: continue
            if (itemCount <= 1) continue

            // 참조가 repeat 영역에 포함되는지 확인
            if (rowIndex !in region.area.rowRange || colIndex !in region.area.colRange) {
                continue
            }

            val templateRowCount = region.area.rowRange.count
            val templateColCount = region.area.colRange.count

            when (region.direction) {
                RepeatDirection.DOWN -> {
                    if (ref.isRowAbsolute) continue
                    return if (templateRowCount == 1) {
                        val startRow = expansion.finalStartRow + (rowIndex - region.area.start.row) + 1
                        val endRow = startRow + (itemCount - 1)
                        val col = toColumnLetter(colIndex)
                        "${ref.sheetRef}${ref.colAbs}$col${ref.rowAbs}$startRow:${ref.colAbs}$col${ref.rowAbs}$endRow"
                    } else {
                        val relativeRow = rowIndex - region.area.start.row
                        val cells = (0 until itemCount).map { idx ->
                            val newRow = expansion.finalStartRow + (idx * templateRowCount) + relativeRow + 1
                            val col = toColumnLetter(colIndex)
                            "${ref.sheetRef}${ref.colAbs}$col${ref.rowAbs}$newRow"
                        }
                        cells.joinToString(",")
                    }
                }
                RepeatDirection.RIGHT -> {
                    if (ref.isColAbsolute) continue
                    return if (templateColCount == 1) {
                        val startCol = expansion.finalStartCol + (colIndex - region.area.start.col)
                        val endCol = startCol + (itemCount - 1)
                        "${ref.sheetRef}${ref.colAbs}${toColumnLetter(startCol)}${ref.rowAbs}${ref.row}:${ref.colAbs}${toColumnLetter(endCol)}${ref.rowAbs}${ref.row}"
                    } else {
                        val relativeCol = colIndex - region.area.start.col
                        val cells = (0 until itemCount).map { idx ->
                            val newCol = expansion.finalStartCol + (idx * templateColCount) + relativeCol
                            "${ref.sheetRef}${ref.colAbs}${toColumnLetter(newCol)}${ref.rowAbs}${ref.row}"
                        }
                        cells.joinToString(",")
                    }
                }
            }
        }
        return null
    }

    /**
     * 범위 참조를 확장한다 (B3:B3 -> B3:B7).
     *
     * 범위의 끝 셀이 repeat 영역 내에 있으면 확장한다.
     * 다른 시트 참조도 해당 시트의 확장 정보가 있으면 적용한다.
     */
    private fun expandRangeReferencesWithCalculator(
        formula: String,
        expansion: PositionCalculator.RepeatExpansion,
        itemCount: Int,
        region: RepeatRegionSpec,
        templateRowCount: Int,
        templateColCount: Int,
        otherSheetExpansions: Map<String, SheetExpansionInfo> = emptyMap()
    ): String = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
        val range = match.toRangeRef()
        val endRowIndex = range.end.row - 1
        val endColIndex = toColumnIndex(range.end.col)

        // 다른 시트 참조인 경우
        if (range.hasSheetRef) {
            val sheetName = range.sheetName
            val sheetInfo = sheetName?.let { otherSheetExpansions[it] }
            if (sheetInfo == null) {
                match.value
            } else {
                expandRangeRefForOtherSheet(range, endRowIndex, endColIndex, sheetInfo) ?: match.value
            }
        } else {
            // 현재 시트 참조
            if (itemCount <= 1) {
                match.value
            } else {
                // 끝 셀이 repeat 영역 내에 있는지 확인
                val endInRegion = endRowIndex in region.area.rowRange &&
                    endColIndex in region.area.colRange

                if (!endInRegion) {
                    match.value
                } else {
                    when (region.direction) {
                        RepeatDirection.DOWN -> {
                            if (range.end.isRowAbsolute) {
                                match.value
                            } else if (templateRowCount == 1) {
                                val newEndRow = expansion.finalStartRow + (endRowIndex - region.area.start.row) + itemCount
                                range.format(newEndRow = newEndRow)
                            } else {
                                val relativeRow = endRowIndex - region.area.start.row
                                val newEndRow = expansion.finalStartRow + ((itemCount - 1) * templateRowCount) + relativeRow + 1
                                range.format(newEndRow = newEndRow)
                            }
                        }
                        RepeatDirection.RIGHT -> {
                            if (range.end.isColAbsolute) {
                                match.value
                            } else if (templateColCount == 1) {
                                val newEndCol = expansion.finalStartCol + (endColIndex - region.area.start.col) + itemCount - 1
                                range.format(newEndCol = toColumnLetter(newEndCol))
                            } else {
                                val relativeCol = endColIndex - region.area.start.col
                                val newEndCol = expansion.finalStartCol + ((itemCount - 1) * templateColCount) + relativeCol
                                range.format(newEndCol = toColumnLetter(newEndCol))
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== repeat 영역 밖 참조 시프트 ==========

    /**
     * repeat 영역 내부 수식에서 영역 밖을 가리키는 참조에
     * repeat 확장에 의한 행 시프트를 적용한다.
     *
     * Excel에서 행 삽입 시 참조가 조정되는 것과 동일한 동작.
     * 절대/상대 여부와 무관하게 영역 밖 참조는 모두 시프트된다.
     * adjustForRepeatIndex(복사 동작) **이전에** 호출해야 한다.
     * (원본 수식에서 영역 안/밖을 정확히 판단하기 위함)
     *
     * @param formula 원본 수식 (adjustForRepeatIndex 적용 전)
     * @param repeatArea 현재 repeat 영역 (0-based)
     * @param calculator 위치 계산기
     * @return 조정된 수식
     * @throws TemplateProcessingException 범위 참조가 영역 안팎에 걸치는 경우
     */
    fun adjustRefsOutsideRepeat(
        formula: String,
        repeatArea: CellArea,
        calculator: PositionCalculator
    ): String {
        // 1. 범위 참조 처리
        var result = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
            val range = match.toRangeRef()

            if (range.hasSheetRef) {
                match.value
            } else {
                val startInside = (range.start.row - 1) in repeatArea.rowRange
                val endInside = (range.end.row - 1) in repeatArea.rowRange

                when {
                    startInside && endInside -> match.value
                    startInside != endInside -> throw TemplateProcessingException(
                        errorType = TemplateProcessingException.ErrorType.INVALID_PARAMETER_VALUE,
                        details = "Range reference '${match.value}' spans across the repeat region boundary (rows ${repeatArea.start.row + 1}~${repeatArea.end.row + 1})."
                    )
                    else -> {
                        // 둘 다 영역 밖 -> 시프트 (절대/상대 무관)
                        val newStartRow = calculator.getFinalPosition(
                            range.start.row - 1, toColumnIndex(range.start.col)
                        ).row + 1
                        val newEndRow = calculator.getFinalPosition(
                            range.end.row - 1, toColumnIndex(range.end.col)
                        ).row + 1

                        if (newStartRow == range.start.row && newEndRow == range.end.row) match.value
                        else range.format(newStartRow = newStartRow, newEndRow = newEndRow)
                    }
                }
            }
        }

        // 2. 단일 셀 참조 처리 (범위 외부만)
        val newRanges = findRangePositions(result)

        result = CELL_REF_PATTERN.replace(result) { match ->
            if (isPartOfRange(match, newRanges)) {
                match.value
            } else {
                val ref = match.toCellRef()
                when {
                    ref.hasSheetRef -> match.value
                    (ref.row - 1) in repeatArea.rowRange -> match.value  // 영역 안
                    else -> {
                        // 영역 밖 -> getFinalPosition으로 시프트 (절대/상대 무관)
                        val finalRow = calculator.getFinalPosition(ref.row - 1, toColumnIndex(ref.col)).row + 1
                        if (finalRow == ref.row) match.value
                        else ref.format(newRow = finalRow)
                    }
                }
            }
        }

        return result
    }

    /** 다른 시트의 범위 참조를 확장 (끝 셀이 해당 시트의 repeat 영역에 포함되면 확장) */
    private fun expandRangeRefForOtherSheet(
        range: RangeRef,
        endRowIndex: Int,
        endColIndex: Int,
        sheetInfo: SheetExpansionInfo
    ): String? {
        for (expansion in sheetInfo.expansions) {
            val region = expansion.region
            val itemCount = sheetInfo.collectionSizes[region.collection] ?: continue
            if (itemCount <= 1) continue

            // 끝 셀이 repeat 영역에 포함되는지 확인
            val endInRegion = endRowIndex in region.area.rowRange &&
                endColIndex in region.area.colRange

            if (!endInRegion) continue

            val templateRowCount = region.area.rowRange.count
            val templateColCount = region.area.colRange.count

            when (region.direction) {
                RepeatDirection.DOWN -> {
                    if (range.end.isRowAbsolute) continue
                    return if (templateRowCount == 1) {
                        val newEndRow = expansion.finalStartRow + (endRowIndex - region.area.start.row) + itemCount
                        range.format(newEndRow = newEndRow)
                    } else {
                        val relativeRow = endRowIndex - region.area.start.row
                        val newEndRow = expansion.finalStartRow + ((itemCount - 1) * templateRowCount) + relativeRow + 1
                        range.format(newEndRow = newEndRow)
                    }
                }
                RepeatDirection.RIGHT -> {
                    if (range.end.isColAbsolute) continue
                    return if (templateColCount == 1) {
                        val newEndCol = expansion.finalStartCol + (endColIndex - region.area.start.col) + itemCount - 1
                        range.format(newEndCol = toColumnLetter(newEndCol))
                    } else {
                        val relativeCol = endColIndex - region.area.start.col
                        val newEndCol = expansion.finalStartCol + ((itemCount - 1) * templateColCount) + relativeCol
                        range.format(newEndCol = toColumnLetter(newEndCol))
                    }
                }
            }
        }
        return null
    }
}
