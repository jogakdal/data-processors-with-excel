package io.github.jogakdal.tbeg.engine.preprocessing

import io.github.jogakdal.tbeg.internal.commonLogger
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerValidationException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFSheet

/**
 * 요소 이동 유틸리티.
 *
 * 열/행을 직접 삭제하는 대신, 셀/서식/병합 영역 등 개별 요소를
 * 스냅샷으로 수집한 뒤 조정된 위치에 복원하는 방식으로 동작한다.
 *
 * 이 방식은 요소를 원자적 단위로 이동하므로, in-place 조작 시
 * 발생할 수 있는 데이터 손실이나 서식 깨짐 문제를 방지한다.
 */
object ElementShifter {

    private val LOG by commonLogger()

    /**
     * 지정된 열 범위의 요소를 제거하고, 오른쪽 요소들을 왼쪽으로 이동한다.
     *
     * @param sheet 대상 시트
     * @param deletedColStart 제거할 열 시작 인덱스 (0-based)
     * @param deletedColEnd 제거할 열 끝 인덱스 (0-based, inclusive)
     * @param affectedRowStart 영향 행 시작 인덱스
     * @param affectedRowEnd 영향 행 끝 인덱스
     * @param affectedColEnd 물리적 셀 이동의 열 경계 (이 열까지만 이동, 그 오른쪽은 유지)
     */
    fun shiftColumnsLeft(
        sheet: Sheet,
        deletedColStart: Int,
        deletedColEnd: Int,
        affectedRowStart: Int = 0,
        affectedRowEnd: Int = sheet.lastRowNum,
        affectedColEnd: Int = Int.MAX_VALUE
    ) {
        val shiftCount = deletedColEnd - deletedColStart + 1
        val colLimit = affectedColEnd + 1  // exclusive upper bound

        // 1. 이동 대상 셀 스냅샷 수집 (영향 범위 내, 제거 열 오른쪽)
        val snapshots = mutableListOf<CellSnapshot>()
        for (rowIdx in affectedRowStart..affectedRowEnd) {
            val row = sheet.getRow(rowIdx) ?: continue
            val end = minOf(row.lastCellNum.toInt().coerceAtLeast(0), colLimit)
            for (colIdx in (deletedColEnd + 1) until end) {
                val cell = row.getCell(colIdx) ?: continue
                snapshots += CellSnapshot.capture(cell)
            }
        }

        // 2. 병합 영역 전체 수집
        val mergedRegions = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) }

        // 3. 열 너비 수집 (제거 열 오른쪽, 영향 범위 내)
        val widthEnd = minOf(MAX_COL, affectedColEnd)
        val columnWidths = ((deletedColEnd + 1)..widthEnd).associate { it to sheet.getColumnWidth(it) }

        // 4. 영향 범위 내에서 제거 대상~영향 열 끝까지 셀 제거
        for (rowIdx in affectedRowStart..affectedRowEnd) {
            val row = sheet.getRow(rowIdx) ?: continue
            val end = minOf(row.lastCellNum.toInt().coerceAtLeast(0), colLimit)
            for (colIdx in deletedColStart until end) {
                row.getCell(colIdx)?.let { row.removeCell(it) }
            }
        }

        // 5. 병합 영역 전체 제거 후 조정하여 재등록
        while (sheet.numMergedRegions > 0) sheet.removeMergedRegion(0)
        mergedRegions.forEach { merged ->
            val intersectsAffectedRows = merged.lastRow >= affectedRowStart && merged.firstRow <= affectedRowEnd
            val intersectsAffectedCols = merged.firstColumn <= affectedColEnd && merged.lastColumn >= deletedColStart

            if (!intersectsAffectedRows || !intersectsAffectedCols) {
                sheet.addMergedRegion(merged)
                return@forEach
            }

            val adjusted = when {
                merged.firstColumn >= deletedColStart && merged.lastColumn <= deletedColEnd -> null
                merged.firstColumn > deletedColEnd -> CellRangeAddress(
                    merged.firstRow, merged.lastRow,
                    merged.firstColumn - shiftCount, merged.lastColumn - shiftCount
                )
                merged.lastColumn < deletedColStart -> merged
                else -> CellRangeAddress(
                    merged.firstRow, merged.lastRow,
                    merged.firstColumn,
                    (merged.lastColumn - shiftCount).coerceAtLeast(merged.firstColumn)
                )
            }
            adjusted?.let { sheet.addMergedRegion(it) }
        }

        // 6. 스냅샷 셀 복원 (이동된 위치)
        snapshots.forEach { it.restoreTo(sheet, it.rowIndex, it.colIndex - shiftCount) }

        // 7. 열 너비 복원
        columnWidths.forEach { (col, width) -> sheet.setColumnWidth(col - shiftCount, width) }

        // 8. 수식/마커 범위 참조 일괄 조정 (열 경계 안의 참조만 시프트)
        adjustAllColumnReferences(sheet, deletedColStart, shiftCount, affectedColEnd)

        // 9. 테이블(표) 참조 범위 조정 (열 경계 안의 테이블만)
        adjustTablesForColumnShift(sheet, deletedColStart, deletedColEnd, shiftCount, affectedColEnd)
    }

    /**
     * 지정된 행 범위의 요소를 제거하고, 아래 요소들을 위로 이동한다.
     *
     * RIGHT repeat용으로, 지정된 열 범위 내에서만 행을 처리한다.
     *
     * @param sheet 대상 시트
     * @param deletedRowStart 제거할 행 시작 인덱스 (0-based)
     * @param deletedRowEnd 제거할 행 끝 인덱스 (0-based, inclusive)
     * @param colStart 처리할 열 시작 인덱스
     * @param colEnd 처리할 열 끝 인덱스
     */
    fun shiftRowsUp(sheet: Sheet, deletedRowStart: Int, deletedRowEnd: Int, colStart: Int, colEnd: Int) {
        val shiftCount = deletedRowEnd - deletedRowStart + 1

        // 1. 이동 대상 셀 스냅샷 수집 (제거 행 아래의 셀)
        val snapshots = mutableListOf<CellSnapshot>()
        for (rowIdx in (deletedRowEnd + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            for (colIdx in colStart..colEnd) {
                val cell = row.getCell(colIdx) ?: continue
                snapshots += CellSnapshot.capture(cell)
            }
        }

        // 2. 병합 영역 전체 수집
        val mergedRegions = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) }

        // 3. 제거 대상~끝까지 해당 열 범위의 셀 제거
        for (rowIdx in deletedRowStart..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            for (colIdx in colStart..colEnd) {
                row.getCell(colIdx)?.let { row.removeCell(it) }
            }
        }

        // 4. 병합 영역 전체 제거 후 조정하여 재등록
        while (sheet.numMergedRegions > 0) sheet.removeMergedRegion(0)
        mergedRegions.forEach { merged ->
            val adjusted = when {
                // 제거 행 범위에 완전히 포함 -> 삭제
                merged.firstRow >= deletedRowStart && merged.lastRow <= deletedRowEnd &&
                    merged.firstColumn >= colStart && merged.lastColumn <= colEnd -> null
                // 제거 행 범위 아래 -> 위로 이동
                merged.firstRow > deletedRowEnd -> CellRangeAddress(
                    merged.firstRow - shiftCount, merged.lastRow - shiftCount,
                    merged.firstColumn, merged.lastColumn
                )
                // 제거 행 범위 위 -> 그대로 유지
                merged.lastRow < deletedRowStart -> merged
                // 부분 겹침 -> 범위 축소
                else -> CellRangeAddress(
                    merged.firstRow,
                    (merged.lastRow - shiftCount).coerceAtLeast(merged.firstRow),
                    merged.firstColumn, merged.lastColumn
                )
            }
            adjusted?.let { sheet.addMergedRegion(it) }
        }

        // 5. 스냅샷 셀 복원 (이동된 위치)
        snapshots.forEach { it.restoreTo(sheet, it.rowIndex - shiftCount, it.colIndex) }

        // 6. 전체 셀의 수식/마커 범위 참조 일괄 조정
        adjustAllRowReferences(sheet, deletedRowStart, shiftCount)

        // 7. 테이블(표) 참조 범위 조정
        adjustTablesForRowShift(sheet, deletedRowStart, deletedRowEnd, shiftCount)
    }

    // ─── 수식/마커 범위 참조 조정 ────────────────────────────────

    private fun adjustAllColumnReferences(
        sheet: Sheet, deletedColStart: Int, shiftCount: Int, affectedColEnd: Int = Int.MAX_VALUE
    ) {
        val deletedRange = deletedColStart..(deletedColStart + shiftCount - 1)

        // 시트 전체를 순회한다. repeat 영역 외부의 수식도 삭제된 열 참조를 반영해야 한다.
        // 단, 열 경계(affectedColEnd) 밖의 참조는 시프트하지 않는다.
        sheet.forEach { row ->
            row.forEach { cell ->
                when (cell.cellType) {
                    CellType.FORMULA -> try {
                        if (allColumnRefsInRange(cell.cellFormula, deletedRange)) {
                            val cellRef = "${columnIndexToLetters(cell.columnIndex)}${cell.rowIndex + 1}"
                            LOG.warn("All references in formula '{}' (cell {}) are within deleted columns; clearing cell.",
                                cell.cellFormula, cellRef)
                            cell.setBlank()
                            return@forEach
                        }
                        val adjusted = adjustFormulaColumnRefs(cell.cellFormula, deletedColStart, shiftCount, affectedColEnd)
                        if (adjusted != cell.cellFormula) {
                            (cell as XSSFCell).setFormulaRaw(adjusted)
                        }
                    } catch (_: Exception) { /* 2nd pass에서 재처리 */ }
                    CellType.STRING -> {
                        validateMarkerColumnRanges(cell.stringCellValue, deletedRange)
                        val adjusted = adjustMarkerColumnRefs(cell.stringCellValue, deletedColStart, shiftCount, affectedColEnd)
                        if (adjusted != cell.stringCellValue) cell.setCellValue(adjusted)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun adjustAllRowReferences(sheet: Sheet, deletedRowStart: Int, shiftCount: Int) {
        // 행 번호는 1-based로 저장되므로 범위도 1-based
        val deletedRange = (deletedRowStart + 1)..(deletedRowStart + shiftCount)

        sheet.forEach { row ->
            row.forEach { cell ->
                when (cell.cellType) {
                    CellType.FORMULA -> try {
                        if (allRowRefsInRange(cell.cellFormula, deletedRange)) {
                            val cellRef = "${columnIndexToLetters(cell.columnIndex)}${cell.rowIndex + 1}"
                            LOG.warn("All references in formula '{}' (cell {}) are within deleted rows; clearing cell.",
                                cell.cellFormula, cellRef)
                            cell.setBlank()
                            return@forEach
                        }
                        val adjusted = adjustFormulaRowRefs(cell.cellFormula, deletedRowStart, shiftCount)
                        if (adjusted != cell.cellFormula) {
                            (cell as XSSFCell).setFormulaRaw(adjusted)
                        }
                    } catch (_: Exception) { /* 2nd pass에서 재처리 */ }
                    CellType.STRING -> {
                        validateMarkerRowRanges(cell.stringCellValue, deletedRange)
                        val adjusted = adjustMarkerRowRefs(cell.stringCellValue, deletedRowStart, shiftCount)
                        if (adjusted != cell.stringCellValue) cell.setCellValue(adjusted)
                    }
                    else -> {}
                }
            }
        }
    }

    // ─── 수식 참조 조정 (열) ──────────────────────────────────────

    internal fun adjustFormulaColumnRefs(
        formula: String, deletedColStart: Int, shiftCount: Int, affectedColEnd: Int = Int.MAX_VALUE
    ): String =
        CELL_REF_PATTERN.replace(formula) { match ->
            val colAbsolute = match.groupValues[1]
            val colLetters = match.groupValues[2]
            val rowAbsolute = match.groupValues[3]
            val rowNum = match.groupValues[4]
            val colIndex = columnLettersToIndex(colLetters)
            if (colIndex in (deletedColStart + shiftCount)..affectedColEnd) {
                "$colAbsolute${columnIndexToLetters(colIndex - shiftCount)}$rowAbsolute$rowNum"
            } else {
                match.value
            }
        }

    // ─── 수식 참조 조정 (행) ──────────────────────────────────────

    internal fun adjustFormulaRowRefs(formula: String, deletedRowStart: Int, shiftCount: Int): String =
        CELL_REF_PATTERN.replace(formula) { match ->
            val colAbsolute = match.groupValues[1]
            val colLetters = match.groupValues[2]
            val rowAbsolute = match.groupValues[3]
            val rowNum = match.groupValues[4].toInt()
            // Excel 행 번호는 1-based, deletedRowStart는 0-based
            if (rowNum > deletedRowStart + shiftCount) {
                "$colAbsolute$colLetters$rowAbsolute${rowNum - shiftCount}"
            } else {
                match.value
            }
        }

    // ─── 마커 셀/범위 참조 조정 ──────────────────────────────────

    private fun adjustMarkerColumnRefs(
        text: String, deletedColStart: Int, shiftCount: Int, affectedColEnd: Int = Int.MAX_VALUE
    ): String =
        MARKER_BLOCK_PATTERN.replace(text) { markerMatch ->
            CELL_OR_RANGE_PATTERN.replace(markerMatch.value) { match ->
                val startCol = columnLettersToIndex(match.groupValues[1])
                val startRow = match.groupValues[2]
                val shiftableRange = (deletedColStart + shiftCount)..affectedColEnd
                val newStartCol = if (startCol in shiftableRange) startCol - shiftCount else startCol

                if (match.groupValues[3].isNotEmpty()) {
                    val endCol = columnLettersToIndex(match.groupValues[3])
                    val endRow = match.groupValues[4]
                    val newEndCol = if (endCol in shiftableRange) endCol - shiftCount else endCol
                    "${columnIndexToLetters(newStartCol)}$startRow:${columnIndexToLetters(newEndCol)}$endRow"
                } else {
                    "${columnIndexToLetters(newStartCol)}$startRow"
                }
            }
        }

    private fun adjustMarkerRowRefs(text: String, deletedRowStart: Int, shiftCount: Int): String =
        MARKER_BLOCK_PATTERN.replace(text) { markerMatch ->
            CELL_OR_RANGE_PATTERN.replace(markerMatch.value) { match ->
                val startCol = match.groupValues[1]
                val startRow = match.groupValues[2].toInt()
                val newStartRow = if (startRow > deletedRowStart + shiftCount) startRow - shiftCount else startRow

                if (match.groupValues[3].isNotEmpty()) {
                    val endCol = match.groupValues[3]
                    val endRow = match.groupValues[4].toInt()
                    val newEndRow = if (endRow > deletedRowStart + shiftCount) endRow - shiftCount else endRow
                    "$startCol$newStartRow:$endCol$newEndRow"
                } else {
                    "$startCol$newStartRow"
                }
            }
        }

    // ─── 테이블(표) 참조 조정 ─────────────────────────────────────

    private fun adjustTablesForColumnShift(
        sheet: Sheet, deletedColStart: Int, deletedColEnd: Int, shiftCount: Int,
        affectedColEnd: Int = Int.MAX_VALUE
    ) {
        val xssfSheet = sheet as? XSSFSheet ?: return
        xssfSheet.tables.forEach { table ->
            val ref = table.ctTable.ref ?: return@forEach
            val range = CellRangeAddress.valueOf(ref)
            // 열 경계 밖의 테이블은 조정하지 않는다
            if (range.firstColumn > affectedColEnd) return@forEach
            val adjusted = when {
                range.firstColumn >= deletedColStart && range.lastColumn <= deletedColEnd -> return@forEach
                range.firstColumn > deletedColEnd -> CellRangeAddress(
                    range.firstRow, range.lastRow,
                    range.firstColumn - shiftCount, range.lastColumn - shiftCount
                )
                range.lastColumn < deletedColStart -> return@forEach
                else -> {
                    val overlapStart = maxOf(range.firstColumn, deletedColStart)
                    val overlapEnd = minOf(range.lastColumn, deletedColEnd)
                    val overlapCount = overlapEnd - overlapStart + 1

                    val colOffset = overlapStart - range.firstColumn
                    for (i in (colOffset + overlapCount - 1) downTo colOffset) {
                        if (i < table.ctTable.tableColumns.count) {
                            table.ctTable.tableColumns.removeTableColumn(i)
                        }
                    }

                    CellRangeAddress(
                        range.firstRow, range.lastRow,
                        range.firstColumn, range.lastColumn - shiftCount
                    )
                }
            }
            table.ctTable.ref = adjusted.formatAsString()
            table.ctTable.autoFilter?.ref = adjusted.formatAsString()
        }
    }

    private fun adjustTablesForRowShift(sheet: Sheet, deletedRowStart: Int, deletedRowEnd: Int, shiftCount: Int) {
        val xssfSheet = sheet as? XSSFSheet ?: return
        xssfSheet.tables.forEach { table ->
            val ref = table.ctTable.ref ?: return@forEach
            val range = CellRangeAddress.valueOf(ref)
            val adjusted = when {
                range.firstRow >= deletedRowStart && range.lastRow <= deletedRowEnd -> return@forEach
                range.firstRow > deletedRowEnd -> CellRangeAddress(
                    range.firstRow - shiftCount, range.lastRow - shiftCount,
                    range.firstColumn, range.lastColumn
                )
                range.lastRow < deletedRowStart -> return@forEach
                else -> CellRangeAddress(
                    range.firstRow,
                    (range.lastRow - shiftCount).coerceAtLeast(range.firstRow),
                    range.firstColumn, range.lastColumn
                )
            }
            table.ctTable.ref = adjusted.formatAsString()
            table.ctTable.autoFilter?.ref = adjusted.formatAsString()
        }
    }

    // ─── 삭제 대상 참조 검증 ──────────────────────────────────────

    /** 수식의 모든 셀 참조가 삭제 대상 열 범위에 해당하는지 확인한다. */
    private fun allColumnRefsInRange(formula: String, deletedCols: IntRange): Boolean {
        val refs = CELL_REF_PATTERN.findAll(formula).toList()
        return refs.isNotEmpty() && refs.all { columnLettersToIndex(it.groupValues[2]) in deletedCols }
    }

    /** 수식의 모든 셀 참조가 삭제 대상 행 범위에 해당하는지 확인한다. (행 번호는 1-based) */
    private fun allRowRefsInRange(formula: String, deletedRows: IntRange): Boolean {
        val refs = CELL_REF_PATTERN.findAll(formula).toList()
        return refs.isNotEmpty() && refs.all { it.groupValues[4].toIntOrNull()?.let { row -> row in deletedRows } == true }
    }

    /** 마커의 범위 참조가 삭제 대상 열에 완전히 포함되면 예외를 발생시킨다. */
    private fun validateMarkerColumnRanges(text: String, deletedCols: IntRange) {
        MARKER_BLOCK_PATTERN.findAll(text).forEach { markerMatch ->
            CELL_OR_RANGE_PATTERN.findAll(markerMatch.value).forEach { match ->
                val startCol = columnLettersToIndex(match.groupValues[1])
                val endCol = if (match.groupValues[3].isNotEmpty()) columnLettersToIndex(match.groupValues[3]) else startCol
                if (startCol in deletedCols && endCol in deletedCols) {
                    throw MarkerValidationException(
                        "Range of marker '${markerMatch.value}' is entirely within deleted columns" +
                        " (${columnIndexToLetters(deletedCols.first)}:${columnIndexToLetters(deletedCols.last)})." +
                        " Please check the hideable configuration in the template."
                    )
                }
            }
        }
    }

    /** 마커의 범위 참조가 삭제 대상 행에 완전히 포함되면 예외를 발생시킨다. */
    private fun validateMarkerRowRanges(text: String, deletedRows: IntRange) {
        MARKER_BLOCK_PATTERN.findAll(text).forEach { markerMatch ->
            CELL_OR_RANGE_PATTERN.findAll(markerMatch.value).forEach { match ->
                val startRow = match.groupValues[2].toIntOrNull() ?: return@forEach
                val endRow = if (match.groupValues[4].isNotEmpty()) match.groupValues[4].toIntOrNull() ?: return@forEach else startRow
                if (startRow in deletedRows && endRow in deletedRows) {
                    throw MarkerValidationException(
                        "Range of marker '${markerMatch.value}' is entirely within deleted rows" +
                        " (${deletedRows.first}:${deletedRows.last})." +
                        " Please check the hideable configuration in the template."
                    )
                }
            }
        }
    }

    // ─── 유틸리티 ────────────────────────────────────────────────

    internal fun columnLettersToIndex(letters: String): Int {
        var result = 0
        letters.forEach { c -> result = result * 26 + (c - 'A' + 1) }
        return result - 1
    }

    internal fun columnIndexToLetters(index: Int): String {
        val sb = StringBuilder()
        var idx = index
        while (idx >= 0) {
            sb.insert(0, ('A' + idx % 26))
            idx = idx / 26 - 1
        }
        return sb.toString()
    }

    private val CELL_REF_PATTERN = Regex("""(\$?)([A-Z]+)(\$?)(\d+)""")
    private val MARKER_BLOCK_PATTERN = Regex("""\$\{[^}]+\}""")
    private val CELL_OR_RANGE_PATTERN = Regex("""([A-Z]+)(\d+)(?::([A-Z]+)(\d+))?""")
    private const val MAX_COL = 16383  // XLSX 최대 열 인덱스 (XFD, 0-based)
}

/**
 * 셀의 전체 상태를 캡처하는 스냅샷.
 *
 * 값, 타입, 수식, 서식을 모두 보존하여 다른 위치에 복원할 수 있다.
 */
internal data class CellSnapshot(
    val rowIndex: Int,
    val colIndex: Int,
    val cellType: CellType,
    val stringValue: String?,
    val numericValue: Double,
    val booleanValue: Boolean,
    val formula: String?,
    val style: CellStyle?
) {
    companion object {
        fun capture(cell: Cell) = CellSnapshot(
            rowIndex = cell.rowIndex,
            colIndex = cell.columnIndex,
            cellType = cell.cellType,
            stringValue = if (cell.cellType == CellType.STRING) cell.stringCellValue else null,
            numericValue = if (cell.cellType == CellType.NUMERIC) cell.numericCellValue else 0.0,
            booleanValue = if (cell.cellType == CellType.BOOLEAN) cell.booleanCellValue else false,
            formula = if (cell.cellType == CellType.FORMULA) cell.cellFormula else null,
            style = cell.cellStyle
        )
    }

    fun restoreTo(sheet: Sheet, targetRow: Int, targetCol: Int) {
        val row = sheet.getRow(targetRow) ?: sheet.createRow(targetRow)
        val cell = row.createCell(targetCol)
        when (cellType) {
            CellType.STRING -> cell.setCellValue(stringValue)
            CellType.NUMERIC -> cell.setCellValue(numericValue)
            CellType.BOOLEAN -> cell.setCellValue(booleanValue)
            CellType.FORMULA -> (cell as XSSFCell).setFormulaRaw(formula!!)
            CellType.BLANK -> cell.setBlank()
            else -> cell.setBlank()
        }
        style?.let { cell.cellStyle = it }
    }
}
