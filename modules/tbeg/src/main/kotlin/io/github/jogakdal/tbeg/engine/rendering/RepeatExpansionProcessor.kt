package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.RowRange
import io.github.jogakdal.tbeg.exception.FormulaExpansionException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFSheet

/**
 * 셀 값을 다른 셀에서 복사한다.
 */
private fun Cell.copyValueFrom(source: Cell) {
    when (source.cellType) {
        CellType.STRING -> setCellValue(source.stringCellValue)
        CellType.NUMERIC -> setCellValue(source.numericCellValue)
        CellType.BOOLEAN -> setCellValue(source.booleanCellValue)
        CellType.FORMULA -> cellFormula = source.cellFormula
        CellType.BLANK -> setBlank()
        else -> { }
    }
}

/**
 * 반복 영역 확장 처리를 담당하는 프로세서.
 *
 * TemplateRenderingEngine에서 반복(repeat) 영역의 행/열 확장 및
 * 관련 셀 복사, 병합 영역, 조건부 서식, 수식 확장을 처리한다.
 *
 * 처리 대상:
 * - DOWN 방향 반복: 행 삽입, 행 복사, 병합 영역 복제
 * - RIGHT 방향 반복: 열 이동, 열 복사
 * - 조건부 서식 범위 확장
 * - 반복 영역 참조 수식 확장
 */
internal class RepeatExpansionProcessor {
    /**
     * RIGHT 방향 확장 - 템플릿 열을 오른쪽으로 복사
     * 확장 시 반복 영역 오른쪽의 기존 셀들을 오른쪽으로 이동
     */
    fun expandColumnsRight(
        sheet: XSSFSheet,
        region: RepeatRegionSpec,
        itemCount: Int
    ) {
        val colsToInsert = (itemCount - 1) * region.area.colRange.count

        if (colsToInsert <= 0) return

        shiftColumnsRight(
            sheet, region.area.end.col + 1, colsToInsert,
            region.area.rowRange
        )

        region.area.rowRange
            .mapNotNull(sheet::getRow)
            .forEach { row ->
                val templateCells = region.area.colRange
                    .mapNotNull(row::getCell)
                    .withIndex()

                (1 until itemCount).forEach { itemIdx ->
                    templateCells.forEach { (templateOffset, templateCell) ->
                        val newColIdx = region.area.start.col + (itemIdx * region.area.colRange.count) + templateOffset
                        row.createCell(newColIdx).apply {
                            cellStyle = templateCell.cellStyle
                            copyValueFrom(templateCell)
                        }
                    }
                }
            }
    }

    /**
     * 지정된 행 범위 내에서 지정된 열부터 오른쪽의 셀을 오른쪽으로 이동
     */
    fun shiftColumnsRight(
        sheet: XSSFSheet,
        startCol: Int,
        shiftAmount: Int,
        rowRange: RowRange
    ) {
        for (rowIdx in rowRange.start..rowRange.end) {
            val row = sheet.getRow(rowIdx) ?: continue
            // 오른쪽에서 왼쪽으로 처리하여 덮어쓰기 방지
            val cellsToMove = row.toList().filter { it.columnIndex >= startCol }
                .sortedByDescending { it.columnIndex }

            for (cell in cellsToMove) {
                val newColIdx = cell.columnIndex + shiftAmount
                val newCell = row.createCell(newColIdx)
                newCell.cellStyle = sheet.workbook.getCellStyleAt(cell.cellStyle.index.toInt())

                when (cell.cellType) {
                    CellType.STRING -> newCell.setCellValue(cell.stringCellValue)
                    CellType.NUMERIC -> newCell.setCellValue(cell.numericCellValue)
                    CellType.BOOLEAN -> newCell.setCellValue(cell.booleanCellValue)
                    CellType.FORMULA -> {
                        newCell.cellFormula = FormulaAdjuster.adjustForColumnExpansion(
                            cell.cellFormula, startCol, shiftAmount
                        )
                    }
                    CellType.BLANK -> newCell.setBlank()
                    else -> {}
                }

                row.removeCell(cell)
            }
        }
    }

    /**
     * 다중 행 반복 템플릿의 병합 영역을 각 아이템에 대해 복제
     */
    fun copyMergedRegionsForRepeat(
        sheet: XSSFSheet,
        region: RepeatRegionSpec,
        itemCount: Int,
        templateMergedRegions: List<CellRangeAddress>
    ) {
        // 반복 영역 내 병합 영역 찾기
        val repeatMergedRegions = templateMergedRegions.filter { merged ->
            merged.firstRow in region.area.rowRange &&
                merged.lastRow in region.area.rowRange &&
                merged.firstColumn in region.area.colRange &&
                merged.lastColumn in region.area.colRange
        }

        // 각 추가 아이템에 대해 병합 영역 복제
        for (itemIdx in 1 until itemCount) {
            val rowOffset = itemIdx * region.area.rowRange.count
            for (templateRegion in repeatMergedRegions) {
                val newRegion = CellRangeAddress(
                    templateRegion.firstRow + rowOffset,
                    templateRegion.lastRow + rowOffset,
                    templateRegion.firstColumn,
                    templateRegion.lastColumn
                )
                // 이미 병합된 영역이면 무시
                runCatching { sheet.addMergedRegion(newRegion) }
            }
        }
    }

    /**
     * 조건부 서식 범위를 반복 영역에 맞게 확장 (XSSF 모드)
     *
     * 템플릿의 반복 영역에 조건부 서식이 있으면, 각 반복 아이템에 대해
     * 조건부 서식을 복제한다.
     */
    fun expandConditionalFormattingForRepeat(
        sheet: XSSFSheet,
        region: RepeatRegionSpec,
        itemCount: Int
    ) {
        if (itemCount <= 1) return

        val scf = sheet.sheetConditionalFormatting
        val cfCount = scf.numConditionalFormattings

        // 반복 영역과 겹치는 조건부 서식 찾기 및 확장
        for (i in 0 until cfCount) {
            val cf = scf.getConditionalFormattingAt(i) ?: continue
            val ranges = cf.formattingRanges

            // 반복 영역 내의 범위만 필터
            val repeatRanges = ranges.filter { range ->
                range.firstRow in region.area.rowRange &&
                        range.lastRow in region.area.rowRange &&
                        range.firstColumn in region.area.colRange &&
                        (region.area.end.col == Int.MAX_VALUE || range.lastColumn in region.area.colRange)
            }

            if (repeatRanges.isEmpty()) continue

            // 규칙 복사
            val rules = (0 until cf.numberOfRules).mapNotNull { cf.getRule(it) }.toTypedArray()
            if (rules.isEmpty()) continue

            // 각 추가 아이템에 대해 새 범위 생성 및 추가
            for (itemIdx in 1 until itemCount) {
                val rowOffset = itemIdx * region.area.rowRange.count
                val newRanges = repeatRanges.map { range ->
                    CellRangeAddress(
                        range.firstRow + rowOffset,
                        range.lastRow + rowOffset,
                        range.firstColumn,
                        range.lastColumn
                    )
                }.toTypedArray()

                scf.addConditionalFormatting(newRanges, rules)
            }
        }
    }

    /**
     * 반복 영역 이후 행의 수식에서 반복 영역 내 셀 참조를 범위로 확장 (XSSF 모드)
     *
     * 예: 반복 영역 A7:B8, 데이터 3개
     * - B11의 `=SUM(B8)` -> B13으로 이동 (shiftRows에 의해)
     * - 수식을 `=SUM(B8,B10,B12)`로 확장 (2행 템플릿이므로 비연속)
     */
    fun expandFormulasAfterRepeat(
        sheet: XSSFSheet,
        region: RepeatRegionSpec,
        itemCount: Int,
        rowsInserted: Int
    ) {
        if (itemCount <= 1 || rowsInserted <= 0) return

        // 반복 영역 이후의 행에서 수식 셀 찾기
        for (rowIdx in (region.area.end.row + rowsInserted + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            row.forEach { cell ->
                if (cell.cellType == CellType.FORMULA) {
                    val originalFormula = cell.cellFormula
                    val (expandedFormula, isSequential) = FormulaAdjuster.expandSingleRefToRowRange(
                        originalFormula,
                        region.area.rowRange,
                        itemCount
                    )

                    if (expandedFormula != originalFormula) {
                        // 비연속 참조이고 인자 수가 255개를 초과하면 경고
                        if (!isSequential && itemCount > 255) {
                            throw FormulaExpansionException(
                                sheetName = sheet.sheetName,
                                cellRef = cell.address.formatAsString(),
                                formula = originalFormula
                            )
                        }
                        cell.cellFormula = expandedFormula
                    }
                }
            }
        }
    }

    /**
     * RIGHT 방향 반복 영역 오른쪽의 수식에서 반복 영역 내 셀 참조를 범위로 확장 (XSSF 모드)
     *
     * 예: 반복 영역 B7:C8 (2열×2행), 데이터 3개
     * - G7의 `=SUM(B7)` 수식
     * - 수식을 `=SUM(B7,D7,F7)`로 확장 (2열 템플릿이므로 비연속)
     */
    fun expandFormulasAfterRightRepeat(
        sheet: XSSFSheet,
        region: RepeatRegionSpec,
        itemCount: Int,
        colsInserted: Int
    ) {
        if (itemCount <= 1) return

        // 반복 영역의 행 범위 내에서 수식 셀 찾기
        for (rowIdx in region.area.rowRange) {
            val row = sheet.getRow(rowIdx) ?: continue
            row.forEach { cell ->
                // 반복 영역 오른쪽에 있는 수식 셀만 처리
                if (cell.columnIndex > region.area.end.col + colsInserted && cell.cellType == CellType.FORMULA) {
                    val originalFormula = cell.cellFormula

                    // XSSF 모드에서 확장된 데이터 열(startCol~endCol + colsInserted)은
                    // shiftColumnsRight 이후에 생성되므로 추가 열 이동이 필요 없음.
                    // 순환 참조도 발생하지 않음: 확장 범위(열 1~itemCount*templateColCount)가
                    // 수식 셀 위치(newColStart 이상)를 포함하지 않음.
                    val (expandedFormula, isSequential) = FormulaAdjuster.expandSingleRefToColumnRange(
                        originalFormula,
                        region.area.colRange,
                        region.area.rowRange,
                        itemCount
                    )

                    if (expandedFormula != originalFormula) {
                        // 비연속 참조이고 인자 수가 255개를 초과하면 오류
                        if (!isSequential && itemCount > 255) {
                            throw FormulaExpansionException(
                                sheetName = sheet.sheetName,
                                cellRef = cell.address.formatAsString(),
                                formula = originalFormula
                            )
                        }
                        cell.cellFormula = expandedFormula
                    }
                }
            }
        }
    }
}
