package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.*

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

    /** 시트 참조에서 시트 이름 추출 (예: "Sheet1!" → "Sheet1", "'Data Sheet'!" → "Data Sheet") */
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

    /** 시트 참조에서 시트 이름 추출 (예: "Sheet1!" → "Sheet1", "'Data Sheet'!" → "Data Sheet") */
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

    // ========== 순환 참조 방지 헬퍼 ==========

    /** 이동 후 최종 위치 계산 (0-based 인덱스 기준) */
    private fun calculateShiftedPosition(position: Int, shiftAmount: Int, shiftStart: Int) =
        if (shiftAmount > 0 && shiftStart >= 0 && position >= shiftStart) position + shiftAmount else position

    /** 순환 참조 여부 체크 (0-based 인덱스 기준) */
    private fun isCircularRef(row: Int, col: Int, formulaCell: CellCoord) =
        formulaCell.row >= 0 && formulaCell.col >= 0 && row == formulaCell.row && col == formulaCell.col

    /** 순환 참조 방지를 위한 최대 위치 계산 (0-based 인덱스 기준) */
    private fun calculateMaxPosition(formulaCellPos: Int, shiftAmount: Int, shiftStart: Int) =
        if (shiftAmount > 0 && shiftStart >= 0 && (formulaCellPos - shiftAmount) >= shiftStart)
            formulaCellPos - shiftAmount else formulaCellPos

    /**
     * 반복 처리로 인한 행 오프셋에 따라 수식 내 셀 참조 조정
     *
     * @param formula 원본 수식
     * @param repeatRows 반복 영역 행 범위 (0-based)
     * @param rowOffset 행 오프셋 (실제 데이터 수 - 템플릿 행 수)
     * @return 조정된 수식
     *
     * 예: =SUM(C6:C6), repeatRows=RowRange(5,5), rowOffset=2 -> =SUM(C6:C8)
     */
    fun adjustForRowExpansion(
        formula: String,
        repeatRows: RowRange,
        rowOffset: Int
    ) = if (rowOffset == 0) formula
    else adjustSingleReferences(
        adjustRangeReferences(formula, repeatRows, rowOffset), repeatRows, rowOffset
    )

    /** 범위 참조 조정 (예: C6:C6 -> C6:C8) - 범위의 끝 셀이 반복 영역에 포함되면 확장 */
    private fun adjustRangeReferences(
        formula: String,
        repeatRows: RowRange,
        rowOffset: Int
    ) = RANGE_CAPTURE_PATTERN.replace(formula) { match ->
        val range = match.toRangeRef()

        // 다른 시트 참조면 조정하지 않음
        if (range.hasSheetRef) {
            match.value
        } else {
            // 끝 행이 반복 영역에 포함되고 절대 참조가 아닌 경우 확장
            val adjustedEndRow = if (!range.end.isRowAbsolute && (range.end.row - 1) in repeatRows) {
                range.end.row + rowOffset
            } else {
                range.end.row
            }

            // 시작 행도 반복 영역 이후면 조정
            val adjustedStartRow = if (!range.start.isRowAbsolute && (range.start.row - 1) > repeatRows.end) {
                range.start.row + rowOffset
            } else {
                range.start.row
            }

            range.format(newStartRow = adjustedStartRow, newEndRow = adjustedEndRow)
        }
    }

    /** 단일 셀 참조 조정 (반복 영역 이후의 참조만) */
    private fun adjustSingleReferences(formula: String, repeatRows: RowRange, rowOffset: Int) =
        findRangePositions(formula).let { ranges ->
            CELL_REF_PATTERN.replace(formula) { match ->
                if (isPartOfRange(match, ranges)) match.value
                else match.toCellRef().let { ref ->
                    // 다른 시트 참조면 조정하지 않음
                    if (ref.hasSheetRef) match.value
                    // 절대 행 참조($)는 조정하지 않음, 반복 영역 이후의 행만 조정
                    else if (!ref.isRowAbsolute && (ref.row - 1) > repeatRows.end) ref.format(newRow = ref.row + rowOffset)
                    else match.value
                }
            }
        }

    /**
     * 현재 행 위치에서의 수식을 반복 인덱스에 맞게 조정
     *
     * 반복 영역 내 수식이 각 반복 항목에 맞게 조정됨
     *
     * @param formula 원본 수식
     * @param repeatIndex 반복 인덱스 (0부터 시작)
     * @return 조정된 수식
     */
    fun adjustForRepeatIndex(formula: String, repeatIndex: Int) =
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
                        else format(newRow = row + repeatIndex)
                    }
                }
            }
        }

    /**
     * 열 확장으로 인한 수식 내 열 참조 조정
     *
     * 지정된 시작 열 이후의 열 참조를 shiftAmount만큼 오른쪽으로 이동
     *
     * @param formula 원본 수식
     * @param startCol 이동 시작 열 (0-based, 이 열 이상의 참조가 조정됨)
     * @param shiftAmount 이동할 열 수
     * @return 조정된 수식
     *
     * 예: =SUM(B7), startCol=2 (C열), shiftAmount=4 -> =SUM(B7) (B는 1이므로 변경 없음)
     * 예: =SUM(F7), startCol=2 (C열), shiftAmount=4 -> =SUM(J7) (F=5 >= 2, 5+4=9 -> J)
     */
    fun adjustForColumnExpansion(formula: String, startCol: Int, shiftAmount: Int) =
        if (shiftAmount == 0) formula
        else findRangePositions(formula).let { ranges ->
            CELL_REF_PATTERN.replace(formula) { match ->
                if (isPartOfRange(match, ranges)) match.value
                else match.toCellRef().let { ref ->
                    // 다른 시트 참조 또는 절대 열 참조면 조정하지 않음
                    if (ref.hasSheetRef || ref.isColAbsolute) match.value
                    else {
                        val colIndex = toColumnIndex(ref.col)
                        if (colIndex >= startCol) ref.format(newCol = toColumnLetter(colIndex + shiftAmount))
                        else match.value
                    }
                }
            }
        }

    /**
     * 반복 영역 내 단일 셀 참조를 행 방향(DOWN)으로 범위 확장
     *
     * DOWN 방향 반복 영역 외부의 수식에서 반복 영역 내 셀을 참조하는 경우,
     * 해당 참조를 행 방향으로 확장된 범위로 변환한다.
     *
     * @param formula 원본 수식
     * @param repeatRows 반복 영역 행 범위 (0-based)
     * @param itemCount 반복 아이템 수
     * @param formulaCell 수식이 위치한 셀의 좌표 (0-based, 순환 참조 방지용)
     * @param rowShiftAmount 확장 후 adjustForRowExpansion에서 이동될 행 수 (순환 참조 방지용)
     * @param rowShiftStartRow 행 이동이 시작되는 행 인덱스 (0-based, repeatRows.end + 1)
     * @return 확장된 수식과 비연속 참조 여부
     *
     * 예 (1행 템플릿):
     *   =SUM(B8), repeatRows=RowRange(7,7), itemCount=3
     *   -> =SUM(B8:B10) (행 방향 연속 범위)
     *
     * 예 (2행 템플릿):
     *   =SUM(B8), repeatRows=RowRange(6,7), itemCount=3
     *   -> =SUM(B8,B10,B12) (행 방향 비연속 - B8은 템플릿 2번째 행, 각 아이템마다 +2)
     *
     * 순환 참조 방지:
     *   수식이 B15에 있고 =SUM(B5)를 =SUM(B5:B20)로 확장하면 B15가 범위에 포함됨
     *   -> formulaCell=CellCoord(14,1)을 전달하면 =SUM(B5:B14)로 제한하여 순환 참조 방지
     */
    fun expandSingleRefToRowRange(
        formula: String,
        repeatRows: RowRange,
        itemCount: Int,
        formulaCell: CellCoord = CellCoord(-1, -1),
        rowShiftAmount: Int = 0,
        rowShiftStartRow: Int = -1
    ): FormulaExpansionResult {
        if (itemCount <= 1) return FormulaExpansionResult(formula, false)

        var isSequential = true
        val ranges = findRangePositions(formula)

        val result = CELL_REF_PATTERN.replace(formula) { match ->
            if (isPartOfRange(match, ranges)) {
                match.value
            } else {
                val ref = match.toCellRef()

                // 다른 시트 참조면 확장하지 않음
                if (ref.hasSheetRef) {
                    match.value
                } else {
                    val rowIndex = ref.row - 1
                    val colIndex = toColumnIndex(ref.col)

                    if (rowIndex !in repeatRows || ref.isRowAbsolute) {
                        match.value
                    } else if (repeatRows.count == 1) {
                        // 1행 템플릿: 연속 범위로 확장
                        var endRowIndex = rowIndex + (itemCount - 1)

                        // 순환 참조 방지: 같은 열이고 참조가 수식 셀보다 위에 있는 경우
                        if (colIndex == formulaCell.col && rowIndex < formulaCell.row) {
                            val finalEndRowIndex = calculateShiftedPosition(endRowIndex, rowShiftAmount, rowShiftStartRow)
                            if (finalEndRowIndex >= formulaCell.row) {
                                val maxRowIndex = calculateMaxPosition(formulaCell.row, rowShiftAmount, rowShiftStartRow)
                                endRowIndex = minOf(endRowIndex, maxRowIndex - 1)
                            }
                        }

                        // 시작이 끝보다 크거나 같으면 확장 의미 없음
                        if (endRowIndex <= rowIndex) {
                            match.value
                        } else {
                            "${ref.colAbs}${ref.col}${ref.rowAbs}${ref.row}:" +
                                    "${ref.colAbs}${ref.col}${ref.rowAbs}${endRowIndex + 1}"
                        }
                    } else {
                        // 다중 행 템플릿: 비연속 셀 나열
                        isSequential = false
                        val cells = (0 until itemCount).mapNotNull { idx ->
                            val newRowIndex = rowIndex + (idx * repeatRows.count)
                            val finalRowIndex = calculateShiftedPosition(newRowIndex, rowShiftAmount, rowShiftStartRow)

                            // 순환 참조 방지: 이동 후 수식 셀과 같은 위치면 제외
                            if (isCircularRef(finalRowIndex, colIndex, formulaCell)) null
                            else "${ref.colAbs}${ref.col}${ref.rowAbs}${newRowIndex + 1}"
                        }
                        if (cells.isEmpty()) match.value else cells.joinToString(",")
                    }
                }
            }
        }

        return FormulaExpansionResult(result, isSequential)
    }

    /**
     * 반복 영역 내 단일 셀 참조를 열 방향으로 범위/목록으로 확장
     *
     * RIGHT 방향 반복 영역 외부의 수식에서 반복 영역 내 셀을 참조하는 경우,
     * 해당 참조를 확장된 범위로 변환한다.
     *
     * @param formula 원본 수식
     * @param repeatCols 반복 영역 열 범위 (0-based)
     * @param repeatRows 반복 영역 행 범위 (0-based)
     * @param itemCount 반복 아이템 수
     * @param formulaCell 수식이 위치한 셀의 좌표 (0-based, 순환 참조 방지용)
     * @param colShiftAmount 확장 후 adjustForColumnExpansion에서 이동될 열 수 (순환 참조 방지용)
     * @param colShiftStartCol 열 이동이 시작되는 열 인덱스 (0-based, repeatCols.end + 1)
     * @return 확장된 수식과 비연속 참조 여부
     *
     * 예 (1열 템플릿):
     *   =SUM(B7), repeatCols=ColRange(1,1), itemCount=3
     *   -> =SUM(B7:D7) (연속 범위)
     *
     * 예 (2열 템플릿):
     *   =SUM(B7), repeatCols=ColRange(1,2), repeatRows=RowRange(6,7), itemCount=3
     *   -> =SUM(B7,D7,F7) (비연속 - B는 템플릿 1번째 열, 각 아이템마다 +2)
     *
     * 순환 참조 방지:
     *   수식이 F7에 있고 =SUM(B7)을 =SUM(B7:GR7)로 확장하면 F7이 범위에 포함됨
     *   -> formulaCell=CellCoord(6,5)를 전달하면 =SUM(B7:E7)로 제한하여 순환 참조 방지
     *
     * 열 이동 고려:
     *   확장된 범위의 끝 열이 colShiftStartCol 이상이면, colShiftAmount만큼 이동됨
     *   이동 후 범위가 수식 셀을 포함하면 범위를 제한함
     */
    fun expandSingleRefToColumnRange(
        formula: String,
        repeatCols: ColRange,
        repeatRows: RowRange,
        itemCount: Int,
        formulaCell: CellCoord = CellCoord(-1, -1),
        colShiftAmount: Int = 0,
        colShiftStartCol: Int = -1
    ): FormulaExpansionResult {
        if (itemCount <= 1) return FormulaExpansionResult(formula, false)

        var isSequential = true
        val ranges = findRangePositions(formula)

        val result = CELL_REF_PATTERN.replace(formula) { match ->
            if (isPartOfRange(match, ranges)) {
                match.value
            } else {
                val ref = match.toCellRef()

                // 다른 시트 참조면 확장하지 않음
                if (ref.hasSheetRef) {
                    match.value
                } else {
                    val rowIndex = ref.row - 1
                    val colIndex = toColumnIndex(ref.col)

                    // 반복 영역 내의 셀인지 확인 (열과 행 모두 반복 영역 내에 있어야 함)
                    if (colIndex !in repeatCols || rowIndex !in repeatRows) {
                        match.value
                    } else if (ref.isColAbsolute) {
                        match.value
                    } else if (repeatCols.count == 1) {
                        // 1열 템플릿: 연속 범위로 확장
                        var endColIndex = colIndex + (itemCount - 1)

                        // 순환 참조 방지: 같은 행이고 참조가 수식 셀보다 왼쪽에 있는 경우
                        if (rowIndex == formulaCell.row && colIndex < formulaCell.col) {
                            val finalEndColIndex = calculateShiftedPosition(endColIndex, colShiftAmount, colShiftStartCol)
                            if (finalEndColIndex >= formulaCell.col) {
                                val maxColIndex = calculateMaxPosition(formulaCell.col, colShiftAmount, colShiftStartCol)
                                endColIndex = minOf(endColIndex, maxColIndex - 1)
                            }
                        }

                        // 시작이 끝보다 크거나 같으면 확장 의미 없음
                        if (endColIndex <= colIndex) {
                            match.value
                        } else {
                            "${ref.colAbs}${ref.col}${ref.rowAbs}${ref.row}:${ref.colAbs}${toColumnLetter(endColIndex)}" +
                                    "${ref.rowAbs}${ref.row}"
                        }
                    } else {
                        // 다중 열 템플릿: 비연속 셀 나열
                        isSequential = false
                        val cells = (0 until itemCount).mapNotNull { idx ->
                            val newColIndex = colIndex + (idx * repeatCols.count)
                            val finalColIndex = calculateShiftedPosition(newColIndex, colShiftAmount, colShiftStartCol)

                            // 순환 참조 방지: 이동 후 수식 셀과 같은 위치면 제외
                            if (isCircularRef(rowIndex, finalColIndex, formulaCell)) null
                            else "${ref.colAbs}${toColumnLetter(newColIndex)}${ref.rowAbs}${ref.row}"
                        }
                        if (cells.isEmpty()) match.value else cells.joinToString(",")
                    }
                }
            }
        }

        return FormulaExpansionResult(result, isSequential)
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
     * 예: B8:B8 → B8, Sheet1!C3:C3 → Sheet1!C3
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
     * - 단일 셀 참조: B3 → B3:B7
     * - 범위 참조: B3:B3 → B3:B7 (끝 셀이 repeat 영역 내에 있으면 확장)
     * - 다른 시트 참조: Sheet2!B3:B3 → Sheet2!B3:B5 (해당 시트의 확장 정보가 있으면 적용)
     *
     * 제외 케이스:
     * - 절대 참조: $B$3, B$3 (행 절대), $B3 (열 절대)
     *
     * @param formula 원본 수식
     * @param expansion 대상 repeat 확장 정보
     * @param itemCount 반복 아이템 수
     * @param otherSheetExpansions 다른 시트의 확장 정보 (시트 이름 → 확장 정보)
     * @return 확장된 수식과 비연속 참조 여부
     */
    fun expandToRangeWithCalculator(
        formula: String,
        expansion: PositionCalculator.RepeatExpansion,
        itemCount: Int,
        otherSheetExpansions: Map<String, SheetExpansionInfo> = emptyMap()
    ): FormulaExpansionResult {
        if (itemCount <= 1 && otherSheetExpansions.isEmpty()) return FormulaExpansionResult(formula, false)

        // 0. 단일 셀 범위를 단일 셀 참조로 정규화 (B8:B8 → B8)
        val normalizedFormula = normalizeSingleCellRanges(formula)

        val region = expansion.region
        val templateRowCount = region.area.rowRange.count
        val templateColCount = region.area.colRange.count

        var isSequential = true

        // 1. 먼저 범위 참조 처리 (B3:B5 → B3:B9)
        var result = expandRangeReferencesWithCalculator(
            normalizedFormula, expansion, itemCount, region, templateRowCount, templateColCount, otherSheetExpansions
        )

        // 2. 단일 셀 참조 처리 (B3 → B3:B7)
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
     * 범위 참조를 확장한다 (B3:B3 → B3:B7).
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
