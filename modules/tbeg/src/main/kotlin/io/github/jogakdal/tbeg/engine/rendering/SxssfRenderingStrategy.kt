package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.engine.core.*
import io.github.jogakdal.tbeg.exception.FormulaExpansionException
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.SXSSFFormulaEvaluator
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * SXSSF 기반 스트리밍 렌더링 전략.
 *
 * 특징:
 * - 명세 기반 순차 생성
 * - PositionCalculator를 사용하여 repeat 확장 위치 계산
 * - 100행 버퍼로 메모리 효율적
 * - 수식 재계산은 Excel이 파일 열 때 수행
 *
 * 장점:
 * - 대용량 데이터 처리에 적합
 * - 메모리 사용량 최소화
 *
 * 단점:
 * - 이미 작성된 행 접근 불가
 * - 차트/피벗 테이블은 별도 처리 필요
 */
internal class SxssfRenderingStrategy : AbstractRenderingStrategy() {
    override val name: String = "SXSSF"

    // 템플릿에서 추출한 스타일을 SXSSF 워크북에 매핑
    private var styleMap: Map<Short, CellStyle> = emptyMap()

    // ========== 추상 메서드 구현 ==========

    override fun <T> withWorkbook(
        templateBytes: ByteArray,
        block: (workbook: Workbook, xssfWorkbook: XSSFWorkbook) -> T
    ): T {
        // dxfs 스타일 유지를 위해 템플릿 워크북을 닫지 않고 재사용
        val templateWorkbook = XSSFWorkbook(ByteArrayInputStream(templateBytes))

        templateWorkbook.use { templateWorkbook ->
            return SXSSFWorkbook(templateWorkbook, 100).use { workbook ->
                block(workbook, templateWorkbook)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    override fun beforeProcessSheets(
        workbook: Workbook,
        blueprint: WorkbookSpec,
        data: Map<String, Any>,
        context: RenderingContext
    ) {
        val sxssfWorkbook = workbook as SXSSFWorkbook
        val xssfWorkbook = sxssfWorkbook.xssfWorkbook

        // 스타일 추출 및 매핑
        val styleRegistry = extractStyles(xssfWorkbook)
        styleMap = styleRegistry.mapValues { (_, style) ->
            xssfWorkbook.getCellStyleAt(style.index.toInt())
        }

        // 시트 내용 클리어 (새로 생성)
        clearSheetContents(xssfWorkbook)
    }

    override fun processSheet(
        sheet: Sheet,
        sheetIndex: Int,
        blueprint: SheetSpec,
        data: Map<String, Any>,
        imageLocations: MutableList<ImageLocation>,
        context: RenderingContext
    ) {
        val sxssfSheet = sheet as SXSSFSheet
        processSheetSxssf(sxssfSheet, sheetIndex, blueprint, data, imageLocations, context)
    }

    @Suppress("UNUSED_PARAMETER")
    override fun afterProcessSheets(workbook: Workbook, context: RenderingContext) {
        val sxssfWorkbook = workbook as SXSSFWorkbook

        // 수식 평가 및 calcChain 정리
        evaluateFormulasAndClearCalcChain(sxssfWorkbook)

        // 모든 시트에 수식 재계산 플래그 설정
        repeat(sxssfWorkbook.numberOfSheets) { i ->
            sxssfWorkbook.getSheetAt(i).forceFormulaRecalculation = true
        }

        // 파일 열 때 첫 번째 시트 A1 셀에 포커스
        sxssfWorkbook.setInitialView()
    }

    override fun finalizeWorkbook(workbook: Workbook): ByteArray =
        ByteArrayOutputStream().apply { workbook.write(this) }.toByteArray().removeAbsPath()

    // ========== SXSSF 특화 로직 ==========

    /** SXSSF에서 수식 평가 및 calcChain 정리 */
    private fun evaluateFormulasAndClearCalcChain(workbook: SXSSFWorkbook) {
        runCatching { SXSSFFormulaEvaluator.evaluateAllFormulaCells(workbook, false) }
        clearCalcChain(workbook.xssfWorkbook)
    }

    private fun clearSheetContents(workbook: XSSFWorkbook) {
        clearCalcChain(workbook)

        repeat(workbook.numberOfSheets) { i ->
            val sheet = workbook.getSheetAt(i)
            // 행 제거
            (sheet.lastRowNum downTo 0).forEach { rowIdx ->
                sheet.getRow(rowIdx)?.let { sheet.removeRow(it) }
            }
            // 병합 영역 제거
            repeat(sheet.numMergedRegions) { sheet.removeMergedRegion(0) }
            // 조건부 서식 제거
            val scf = sheet.sheetConditionalFormatting
            repeat(scf.numConditionalFormattings) { scf.removeConditionalFormatting(0) }
        }
    }

    private fun extractStyles(workbook: XSSFWorkbook): Map<Short, XSSFCellStyle> =
        (0 until workbook.numCellStyles).associate { i ->
            i.toShort() to workbook.getCellStyleAt(i) as XSSFCellStyle
        }

    // ========== 헬퍼 함수 ==========

    /** 아이템 데이터 맵 생성 (item이 null이면 원본 data 반환) */
    private fun createItemData(data: Map<String, Any>, itemVariable: String, item: Any?) =
        if (item != null) data + (itemVariable to item) else data

    /** 셀 생성 및 스타일 적용 */
    private fun Row.createCellWithStyle(colIndex: Int, styleIndex: Short): Cell =
        createCell(colIndex).apply { styleMap[styleIndex]?.let { cellStyle = it } }

    /** 수식 확장 시 255개 초과 검증 (초과하면 예외 발생) */
    private fun validateFormulaExpansion(
        itemCount: Int,
        isSeq: Boolean,
        sheetName: String,
        cellRef: String,
        formula: String
    ) {
        if (!isSeq && itemCount > 255) {
            throw FormulaExpansionException(sheetName, cellRef, formula)
        }
    }

    private fun processSheetSxssf(
        sheet: SXSSFSheet,
        sheetIndex: Int,
        blueprint: SheetSpec,
        data: Map<String, Any>,
        imageLocations: MutableList<ImageLocation>,
        context: RenderingContext
    ) {
        blueprint.columnWidths.forEach { (col, width) -> sheet.setColumnWidth(col, width) }
        context.sheetLayoutApplier.applyHeaderFooter(
            sheet.workbook as SXSSFWorkbook, sheetIndex, blueprint.headerFooter, data, context.evaluateText
        )
        context.sheetLayoutApplier.applyPrintSetup(sheet, blueprint.printSetup)

        // repeat 영역 추출
        val repeatRows = blueprint.rows.filterIsInstance<RowSpec.RepeatRow>()
        val repeatRegions = repeatRows.map { row ->
            RepeatRegionSpec(
                row.collectionName, row.itemVariable,
                row.templateRowIndex, row.repeatEndRowIndex,
                row.repeatStartCol, row.repeatEndCol, row.direction,
                row.emptyRangeSpec
            )
        }

        // repeat 영역 겹침 검증
        PositionCalculator.validateNoOverlap(repeatRegions)

        // collection 크기 추출 및 PositionCalculator 생성
        // 스트리밍 모드에서는 context.collectionSizes 사용, 아니면 data에서 추출
        val collectionSizes = if (context.streamingDataSource != null) {
            context.collectionSizes
        } else {
            PositionCalculator.extractCollectionSizes(data, repeatRegions)
        }
        // 템플릿의 마지막 행 인덱스 계산 (blueprint.rows에서)
        val templateLastRow = blueprint.rows.maxOfOrNull { it.templateRowIndex } ?: 0
        val calculator = PositionCalculator(repeatRegions, collectionSizes, templateLastRow)
        calculator.calculate()

        val repeatRegionsMap = repeatRows.associateBy { it.templateRowIndex }

        // 스트리밍 모드 분기
        val hasRightRepeat = repeatRegions.any { it.direction == RepeatDirection.RIGHT }
        val effectiveData = if (context.streamingDataSource != null && hasRightRepeat) {
            buildRightRepeatData(data, repeatRegions, context)
        } else data

        val sxssfWorkbook = sheet.workbook as SXSSFWorkbook
        val xssfWorkbook = sxssfWorkbook.xssfWorkbook
        val ctx = RowWriteContext(
            sheet, sheetIndex, blueprint, effectiveData, styleMap, repeatRegionsMap, imageLocations, context, calculator, xssfWorkbook
        )

        if (context.streamingDataSource != null && !hasRightRepeat) {
            processSheetWithStreaming(ctx)
        } else {
            processSheetWithPendingRows(ctx)
        }

        // 전체 행 오프셋 계산 (병합 영역, 조건부 서식용)
        val maxRowOffset = calculator.getExpansions()
            .filter { it.region.direction == RepeatDirection.DOWN }
            .sumOf { it.rowExpansion }

        // 병합 영역 설정 - PositionCalculator 사용
        context.sheetLayoutApplier.applyMergedRegionsWithCalculator(sheet, blueprint.mergedRegions, calculator)

        // 조건부 서식 적용
        context.sheetLayoutApplier.applyConditionalFormattings(
            sheet, blueprint.conditionalFormattings, repeatRegionsMap, data, maxRowOffset, collectionSizes
        )

        // 빈 컬렉션의 emptyRange 조건부 서식 적용
        context.sheetLayoutApplier.applyEmptyRangeConditionalFormattings(
            sheet, repeatRegionsMap, collectionSizes, calculator
        )
    }

    /**
     * pendingRows 방식: 모든 셀 정보를 수집한 후 행 순서로 출력
     *
     * RIGHT 방향 repeat(가로 확장)이 있는 경우 사용
     */
    private fun processSheetWithPendingRows(ctx: RowWriteContext) {
        val pendingRows = TreeMap<Int, MutableList<PendingCell>>()
        val processedRepeatRows = mutableSetOf<Int>()

        // 모든 행 정보 수집
        for (rowSpec in ctx.blueprint.rows) {
            when (rowSpec) {
                is RowSpec.StaticRow -> writeCellsAtCalculatedPositions(ctx, rowSpec, pendingRows)

                is RowSpec.RepeatRow -> {
                    if (rowSpec.templateRowIndex in processedRepeatRows) continue

                    val items = ctx.data[rowSpec.collectionName] as? Collection<*> ?: emptyList<Any>()

                    when (rowSpec.direction) {
                        RepeatDirection.DOWN -> collectDownRepeatCells(ctx, rowSpec, items, pendingRows)
                        RepeatDirection.RIGHT -> {
                            val (actualRow, _) = ctx.calculator.getFinalPosition(
                                rowSpec.templateRowIndex, rowSpec.repeatStartCol
                            )
                            writeRowSxssfWithRightExpansion(ctx, actualRow, rowSpec, items)
                        }
                    }

                    (rowSpec.templateRowIndex..rowSpec.repeatEndRowIndex).forEach { processedRepeatRows.add(it) }
                }

                is RowSpec.RepeatContinuation -> Unit
            }
        }

        // 행 인덱스 순서대로 셀 작성
        writePendingCells(ctx, pendingRows)

        // emptyRange 병합 영역 적용
        ctx.emptyRangeMergedRegions.forEach { ctx.sheet.addMergedRegion(it) }
    }

    /** DOWN 방향 반복 영역의 셀을 pendingRows에 수집한다 */
    private fun collectDownRepeatCells(
        ctx: RowWriteContext,
        repeatRow: RowSpec.RepeatRow,
        items: Collection<*>,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>
    ) {
        val templateRowCount = repeatRow.repeatEndRowIndex - repeatRow.templateRowIndex + 1
        val expansion = ctx.calculator.getExpansionForRegion(
            repeatRow.collectionName, repeatRow.templateRowIndex, repeatRow.repeatStartCol
        )
        val totalRepeatOffset = expansion?.rowExpansion ?: ((items.size * templateRowCount) - templateRowCount)
        val repeatColRange = repeatRow.repeatStartCol..repeatRow.repeatEndCol

        // repeat 범위 밖의 셀을 먼저 수집 (이미지 마커 등)
        collectNonRepeatCells(ctx, repeatRow, repeatColRange, pendingRows)

        // 빈 컬렉션이고 emptyRangeContent가 있으면 그 내용을 수집
        if (items.isEmpty() && repeatRow.emptyRangeContent != null) {
            collectEmptyRangeContentCells(ctx, repeatRow, pendingRows)
            return
        }

        // 빈 컬렉션이면 null 아이템 추가 (기존 동작)
        val effectiveItems: Collection<Any?> = items.ifEmpty { listOf(null) }

        effectiveItems.forEachIndexed { itemIdx, item ->
            val itemData = createItemData(ctx.data, repeatRow.itemVariable, item)

            for (templateOffset in 0 until templateRowCount) {
                val templateRowIdx = repeatRow.templateRowIndex + templateOffset
                val currentRowBp = ctx.blueprint.rows.find { it.templateRowIndex == templateRowIdx } ?: continue

                val actualRow = expansion?.let {
                    ctx.calculator.getRowForRepeatItem(it, itemIdx, templateOffset)
                } ?: (repeatRow.templateRowIndex + (itemIdx * templateRowCount) + templateOffset)

                // repeat 범위 내 셀만 수집 (범위 밖은 이미 collectNonRepeatCells에서 수집됨)
                currentRowBp.cells
                    .filter { it.columnIndex in repeatColRange }
                    .forEach { cellSpec ->
                        pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                            PendingCell(
                                columnIndex = cellSpec.columnIndex,
                                styleIndex = cellSpec.styleIndex,
                                content = cellSpec.content,
                                height = currentRowBp.height,
                                templateRowIndex = currentRowBp.templateRowIndex,
                                rowSpec = currentRowBp,
                                itemData = itemData,
                                repeatIndex = itemIdx * templateRowCount + templateOffset,
                                repeatItemIndex = itemIdx,
                                totalRowOffset = totalRepeatOffset,
                                repeatRow = repeatRow
                            )
                        )
                    }
            }
        }
    }

    /** repeat 범위 밖의 셀을 pendingRows에 수집한다 (이미지 마커 등) */
    private fun collectNonRepeatCells(
        ctx: RowWriteContext,
        repeatRow: RowSpec.RepeatRow,
        repeatColRange: IntRange,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>
    ) {
        val templateRowCount = repeatRow.repeatEndRowIndex - repeatRow.templateRowIndex + 1

        for (templateOffset in 0 until templateRowCount) {
            val templateRowIdx = repeatRow.templateRowIndex + templateOffset
            val currentRowBp = ctx.blueprint.rows.find { it.templateRowIndex == templateRowIdx } ?: continue

            val actualRow = ctx.calculator.getFinalPosition(templateRowIdx, 0).first

            currentRowBp.cells
                .filter { it.columnIndex !in repeatColRange }
                .forEach { cellSpec ->
                    pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                        PendingCell(
                            columnIndex = cellSpec.columnIndex,
                            styleIndex = cellSpec.styleIndex,
                            content = cellSpec.content,
                            height = currentRowBp.height,
                            templateRowIndex = currentRowBp.templateRowIndex,
                            rowSpec = currentRowBp,
                            itemData = ctx.data,
                            repeatIndex = 0,
                            repeatItemIndex = 0,
                            totalRowOffset = 0,
                            repeatRow = null
                        )
                    )
                }
        }
    }

    /** 빈 컬렉션일 때 emptyRangeContent 셀을 pendingRows에 수집한다 */
    private fun collectEmptyRangeContentCells(
        ctx: RowWriteContext,
        repeatRow: RowSpec.RepeatRow,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>
    ) {
        val emptyRangeContent = repeatRow.emptyRangeContent ?: return
        val templateRowCount = repeatRow.repeatEndRowIndex - repeatRow.templateRowIndex + 1
        val templateColCount = repeatRow.repeatEndCol - repeatRow.repeatStartCol + 1
        val expansion = ctx.calculator.getExpansionForRegion(
            repeatRow.collectionName, repeatRow.templateRowIndex, repeatRow.repeatStartCol
        )
        val actualStartRow = expansion?.finalStartRow ?: repeatRow.templateRowIndex

        // 단일 셀이고 repeat 영역이 더 크면 병합 (병합 영역 등록, 실제 적용은 나중에)
        if (emptyRangeContent.isSingleCell && (templateRowCount > 1 || templateColCount > 1)) {
            val snapshot = emptyRangeContent.cells[0][0]
            pendingRows.getOrPut(actualStartRow) { mutableListOf() }.add(
                PendingCell(
                    columnIndex = repeatRow.repeatStartCol,
                    styleIndex = snapshot.styleIndex,
                    content = snapshot.toContent(),
                    height = emptyRangeContent.rowHeights.getOrNull(0),
                    templateRowIndex = repeatRow.templateRowIndex,
                    rowSpec = RowSpec.StaticRow(repeatRow.templateRowIndex, null, emptyList()),
                    itemData = ctx.data
                )
            )
            // 병합 영역 등록
            ctx.emptyRangeMergedRegions.add(CellRangeAddress(
                actualStartRow, actualStartRow + templateRowCount - 1,
                repeatRow.repeatStartCol, repeatRow.repeatEndCol
            ))
            return
        }

        val rowsToWrite = minOf(templateRowCount, emptyRangeContent.rowCount)
        val colsToWrite = minOf(templateColCount, emptyRangeContent.colCount)

        for (rowOffset in 0 until rowsToWrite) {
            val actualRow = actualStartRow + rowOffset
            val rowHeight = emptyRangeContent.rowHeights.getOrNull(rowOffset)

            for (colOffset in 0 until colsToWrite) {
                val colIndex = repeatRow.repeatStartCol + colOffset
                val snapshot = emptyRangeContent.cells.getOrNull(rowOffset)?.getOrNull(colOffset) ?: continue

                pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                    PendingCell(
                        columnIndex = colIndex,
                        styleIndex = snapshot.styleIndex,
                        content = snapshot.toContent(),
                        height = rowHeight,
                        templateRowIndex = repeatRow.templateRowIndex + rowOffset,
                        rowSpec = RowSpec.StaticRow(repeatRow.templateRowIndex + rowOffset, rowHeight, emptyList()),
                        itemData = ctx.data
                    )
                )
            }
        }
    }


    /** 수집된 pendingRows를 행 순서대로 작성한다 */
    private fun writePendingCells(ctx: RowWriteContext, pendingRows: Map<Int, List<PendingCell>>) {
        val sortedRows = pendingRows.toSortedMap()

        for ((actualRow, cells) in sortedRows) {
            val row = ctx.sheet.getRow(actualRow) ?: ctx.sheet.createRow(actualRow)
            cells.firstOrNull()?.height?.let { row.height = it }

            for (pendingCell in cells) {
                // emptyRange 영역의 셀은 빈 셀 + 기본 스타일로 출력
                val isEmptyRangeCell = ctx.calculator.isInEmptyRange(
                    pendingCell.templateRowIndex, pendingCell.columnIndex
                )

                if (isEmptyRangeCell) {
                    val cell = row.createCell(pendingCell.columnIndex)
                    cell.setBlank()
                    // 스타일 설정 안 함 (기본 스타일 유지)
                } else {
                    val cell = row.createCellWithStyle(pendingCell.columnIndex, pendingCell.styleIndex)
                    val repeatInfo = RepeatInfo(
                        pendingCell.repeatIndex, pendingCell.totalRowOffset, pendingCell.repeatItemIndex
                    )
                    processCellContentSxssfWithCalculator(
                        ctx, cell, pendingCell.content, pendingCell.itemData ?: ctx.data,
                        repeatInfo, pendingCell.rowSpec, pendingCell.columnIndex, actualRow
                    )
                }
            }
        }
    }

    /**
     * 스트리밍 방식: 행 순서대로 즉시 출력하면서 Iterator 순차 소비
     *
     * - 현재 아이템만 메모리에 유지
     * - 전체 컬렉션을 메모리에 올리지 않음
     * - 같은 행에 repeat 셀과 static 셀이 혼재할 수 있음 (열 그룹별 처리)
     */
    private fun processSheetWithStreaming(ctx: RowWriteContext) {
        val state = StreamingState(ctx.context.streamingDataSource!!)
        val totalRows = ctx.calculator.getTotalRows()

        for (actualRow in 0 until totalRows) {
            val row = ctx.sheet.createRow(actualRow)
            val repeatHeight = writeRepeatCellsForRow(ctx, row, actualRow, state)
            val staticHeight = writeStaticCellsForRow(ctx, row, actualRow)
            maxOf(repeatHeight ?: 0, staticHeight ?: 0).takeIf { it > 0 }?.let { row.height = it }
        }

        state.checkRemainingItems()
    }

    /** repeat 영역의 셀을 현재 행에 작성하고 행 높이 반환 */
    private fun writeRepeatCellsForRow(
        ctx: RowWriteContext,
        row: Row,
        actualRow: Int,
        state: StreamingState
    ): Short? {
        var rowHeight: Short? = null

        for ((_, repeatRow) in ctx.repeatRegions) {
            val expansion = ctx.calculator.getExpansionForRegion(
                repeatRow.collectionName, repeatRow.templateRowIndex, repeatRow.repeatStartCol
            ) ?: continue

            val templateRowCount = repeatRow.repeatEndRowIndex - repeatRow.templateRowIndex + 1
            val repeatStartRow = expansion.finalStartRow

            // 빈 컬렉션 + emptyRangeContent가 있는 경우
            // expansion.itemCount는 최소 1이므로, 원래 컬렉션 크기를 직접 확인
            val originalItemCount = ctx.context.collectionSizes[repeatRow.collectionName] ?: 0
            if (originalItemCount == 0 && repeatRow.emptyRangeContent != null) {
                val result = writeEmptyRangeForStreaming(
                    ctx, row, actualRow, repeatRow, repeatStartRow, templateRowCount, rowHeight
                )
                rowHeight = result.rowHeight
                continue
            }

            val repeatEndRow = repeatStartRow + (templateRowCount * maxOf(expansion.itemCount, 1)) - 1

            if (actualRow !in repeatStartRow..repeatEndRow) continue

            val rowWithinRepeat = actualRow - repeatStartRow
            val itemIndex = rowWithinRepeat / templateRowCount
            val templateRowOffset = rowWithinRepeat % templateRowCount

            val repeatKey = StreamingDataSource.RepeatKey(
                ctx.sheetIndex, repeatRow.collectionName, repeatRow.templateRowIndex, repeatRow.repeatStartCol
            )
            val item = state.getItemForRow(repeatKey, templateRowOffset, itemIndex)
            val itemData = createItemData(ctx.data, repeatRow.itemVariable, item)

            val templateRowForCells = repeatRow.templateRowIndex + templateRowOffset
            val rowSpec = ctx.blueprint.rows.find { it.templateRowIndex == templateRowForCells } ?: continue

            rowSpec.height?.let { h -> rowHeight = maxOf(rowHeight ?: 0, h) }

            val repeatColRange = repeatRow.repeatStartCol..repeatRow.repeatEndCol
            val repeatInfo = RepeatInfo(
                index = itemIndex * templateRowCount + templateRowOffset,
                totalRowOffset = expansion.rowExpansion,
                itemIndex = itemIndex
            )

            // repeat 범위 내 셀 처리
            rowSpec.cells
                .filter { it.columnIndex in repeatColRange }
                .forEach { cellSpec ->
                    val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                    processCellContentSxssfWithCalculator(
                        ctx, cell, cellSpec.content, itemData, repeatInfo, rowSpec, cellSpec.columnIndex, actualRow
                    )
                }

            // repeat 범위 밖의 셀은 첫 번째 아이템의 첫 번째 행에서만 처리 (이미지 마커 등)
            if (itemIndex == 0 && templateRowOffset == 0) {
                rowSpec.cells
                    .filter { it.columnIndex !in repeatColRange }
                    .forEach { cellSpec ->
                        val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                        processCellContentSxssfWithCalculator(
                            ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE, rowSpec, cellSpec.columnIndex, actualRow
                        )
                    }
            }
        }

        return rowHeight
    }

    /** 스트리밍 모드에서 빈 컬렉션의 emptyRange 처리 결과 */
    private data class EmptyRangeResult(
        val shouldContinue: Boolean,
        val rowHeight: Short?
    )

    /** 스트리밍 모드에서 빈 컬렉션의 emptyRange 내용을 작성한다 */
    private fun writeEmptyRangeForStreaming(
        ctx: RowWriteContext,
        row: Row,
        actualRow: Int,
        repeatRow: RowSpec.RepeatRow,
        repeatStartRow: Int,
        templateRowCount: Int,
        currentRowHeight: Short?
    ): EmptyRangeResult {
        val emptyRangeContent = repeatRow.emptyRangeContent ?: return EmptyRangeResult(true, currentRowHeight)
        val templateColCount = repeatRow.repeatEndCol - repeatRow.repeatStartCol + 1
        val repeatColRange = repeatRow.repeatStartCol..repeatRow.repeatEndCol
        var rowHeight = currentRowHeight

        // 첫 번째 행에서 repeat 범위 밖의 셀 처리 (이미지 마커 등)
        if (actualRow == repeatStartRow) {
            ctx.blueprint.rows.find { it.templateRowIndex == repeatRow.templateRowIndex }
                ?.cells
                ?.filter { it.columnIndex !in repeatColRange }
                ?.forEach { cellSpec ->
                    val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                    processCellContentSxssfWithCalculator(
                        ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE,
                        ctx.blueprint.rows.find { it.templateRowIndex == repeatRow.templateRowIndex }!!,
                        cellSpec.columnIndex, actualRow
                    )
                }
        }

        // 단일 셀이고 repeat 영역이 더 크면 병합
        if (emptyRangeContent.isSingleCell && (templateRowCount > 1 || templateColCount > 1)) {
            if (actualRow == repeatStartRow) {
                val snapshot = emptyRangeContent.cells[0][0]
                val cell = row.createCellWithStyle(repeatRow.repeatStartCol, snapshot.styleIndex)
                writeCellFromSnapshot(cell, snapshot, ctx.xssfWorkbook)

                ctx.sheet.addMergedRegion(CellRangeAddress(
                    repeatStartRow, repeatStartRow + templateRowCount - 1,
                    repeatRow.repeatStartCol, repeatRow.repeatEndCol
                ))
            }
            return EmptyRangeResult(true, rowHeight)
        }

        val rowsToWrite = minOf(templateRowCount, emptyRangeContent.rowCount)
        if (actualRow !in repeatStartRow until (repeatStartRow + rowsToWrite)) {
            return EmptyRangeResult(true, rowHeight)
        }

        val rowOffset = actualRow - repeatStartRow
        val colsToWrite = minOf(templateColCount, emptyRangeContent.colCount)

        emptyRangeContent.rowHeights.getOrNull(rowOffset)?.let { h ->
            rowHeight = maxOf(rowHeight ?: 0, h)
        }

        for (colOffset in 0 until colsToWrite) {
            val colIndex = repeatRow.repeatStartCol + colOffset
            val snapshot = emptyRangeContent.cells.getOrNull(rowOffset)?.getOrNull(colOffset) ?: continue
            val cell = row.createCellWithStyle(colIndex, snapshot.styleIndex)
            writeCellFromSnapshot(cell, snapshot, ctx.xssfWorkbook)
        }

        return EmptyRangeResult(true, rowHeight)
    }

    // 숫자 형식 스타일 캐시 (emptyRange 숫자 셀용)
    private val numberStyleCache = mutableMapOf<String, XSSFCellStyle>()

    /**
     * CellSnapshot 내용을 셀에 작성한다.
     * 숫자 셀이고 원본 스타일의 dataFormat=0(General)인 경우,
     * Excel 내장 숫자 형식(인덱스 3 또는 4)을 적용한다.
     */
    private fun writeCellFromSnapshot(cell: Cell, snapshot: CellSnapshot, xssfWorkbook: XSSFWorkbook) {
        // 숫자 셀이고 원본 스타일의 dataFormat=0인 경우 숫자 형식 적용
        if (snapshot.cellType == CellType.NUMERIC) {
            val originalStyle = styleMap[snapshot.styleIndex] as? XSSFCellStyle
            if (originalStyle != null && originalStyle.dataFormat.toInt() == 0) {
                val value = snapshot.value as? Double ?: 0.0
                val isInteger = value == value.toLong().toDouble()
                cell.cellStyle = getOrCreateNumberStyle(xssfWorkbook, originalStyle, isInteger)
            }
        }

        when (snapshot.cellType) {
            CellType.STRING -> cell.setCellValue(snapshot.value as? String ?: "")
            CellType.NUMERIC -> cell.setCellValue(snapshot.value as? Double ?: 0.0)
            CellType.BOOLEAN -> cell.setCellValue(snapshot.value as? Boolean ?: false)
            CellType.FORMULA -> snapshot.formula?.let { cell.cellFormula = it }
            else -> cell.setBlank()
        }
    }

    /**
     * 원본 스타일을 복제하고 Excel 내장 숫자 형식을 적용한 스타일을 반환한다.
     */
    private fun getOrCreateNumberStyle(
        workbook: XSSFWorkbook,
        originalStyle: XSSFCellStyle,
        isInteger: Boolean
    ) = getOrCreateNumberStyle(numberStyleCache, workbook, originalStyle, isInteger)

    /** 정적 행의 셀을 현재 행에 작성하고 행 높이 반환 */
    private fun writeStaticCellsForRow(
        ctx: RowWriteContext,
        row: Row,
        actualRow: Int
    ): Short? {
        var rowHeight: Short? = null

        for (rowSpec in ctx.blueprint.rows) {
            if (rowSpec is RowSpec.RepeatRow || rowSpec is RowSpec.RepeatContinuation) continue

            for (cellSpec in rowSpec.cells) {
                // repeat 범위에 속하는 셀은 건너뜀
                val belongsToRepeat = ctx.repeatRegions.values.any { repeatRow ->
                    cellSpec.columnIndex in repeatRow.repeatStartCol..repeatRow.repeatEndCol &&
                        rowSpec.templateRowIndex in repeatRow.templateRowIndex..repeatRow.repeatEndRowIndex
                }
                if (belongsToRepeat) continue

                val rowInfo = ctx.calculator.getRowInfoForColumn(actualRow, cellSpec.columnIndex)
                if (rowInfo is RowInfo.Static && rowInfo.templateRowIndex == rowSpec.templateRowIndex) {
                    if (row.getCell(cellSpec.columnIndex) == null) {
                        rowSpec.height?.let { h -> rowHeight = maxOf(rowHeight ?: 0, h) }

                        // emptyRange 영역의 셀은 빈 셀 + 기본 스타일로 처리
                        if (ctx.calculator.isInEmptyRange(rowSpec.templateRowIndex, cellSpec.columnIndex)) {
                            val cell = row.createCell(cellSpec.columnIndex)
                            cell.setBlank()
                            // 스타일 설정 안 함 (기본 스타일 유지)
                        } else {
                            val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                            processCellContentSxssfWithCalculator(
                                ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE,
                                rowSpec, cellSpec.columnIndex, actualRow
                            )
                        }
                    }
                }
            }
        }

        return rowHeight
    }

    /**
     * RIGHT 방향 repeat이 있는 시트에서 사용할 data 맵을 구성한다.
     *
     * 스트리밍 모드에서 RIGHT 방향 repeat은 pendingRows 방식으로 처리되므로,
     * DataProvider에서 컬렉션을 가져와 List로 변환하여 data에 추가한다.
     */
    private fun buildRightRepeatData(
        data: Map<String, Any>,
        repeatRegions: List<RepeatRegionSpec>,
        context: RenderingContext
    ): Map<String, Any> {
        val dataProvider = context.streamingDataSource?.let { getDataProviderFromStreamingDataSource(it) }
            ?: return data

        val result = data.toMutableMap()
        val collectionNames = repeatRegions.map { it.collection }.toSet()

        collectionNames
            .filterNot { result.containsKey(it) }
            .forEach { name ->
                dataProvider.getItems(name)?.asSequence()?.toList()?.let { result[name] = it }
            }

        return result
    }

    /** StreamingDataSource에서 DataProvider를 가져온다 (리플렉션 사용) */
    private fun getDataProviderFromStreamingDataSource(streamingDataSource: StreamingDataSource) = runCatching {
        StreamingDataSource::class.java.getDeclaredField("dataProvider")
            .apply { isAccessible = true }
            .get(streamingDataSource) as? ExcelDataProvider
    }.getOrNull()

    private data class RowWriteContext(
        val sheet: SXSSFSheet,
        val sheetIndex: Int,
        val blueprint: SheetSpec,
        val data: Map<String, Any>,
        val styleMap: Map<Short, CellStyle>,
        val repeatRegions: Map<Int, RowSpec.RepeatRow>,
        val imageLocations: MutableList<ImageLocation>,
        val context: RenderingContext,
        val calculator: PositionCalculator,
        val xssfWorkbook: XSSFWorkbook,
        val emptyRangeMergedRegions: MutableList<CellRangeAddress> = mutableListOf()
    )

    /** 반복 처리 관련 파라미터 */
    private data class RepeatInfo(
        val index: Int = 0,
        val totalRowOffset: Int = 0,
        val itemIndex: Int = 0
    ) {
        companion object {
            val NONE = RepeatInfo()
        }
    }

    /** 스트리밍 모드의 상태 관리 */
    private class StreamingState(
        val dataSource: StreamingDataSource,
        val currentItemsByRepeat: MutableMap<StreamingDataSource.RepeatKey, Any?> = mutableMapOf(),
        val lastAdvancedIndex: MutableMap<StreamingDataSource.RepeatKey, Int> = mutableMapOf()
    ) {
        /** 다음 아이템으로 진행하거나 현재 아이템 반환 */
        fun getItemForRow(
            repeatKey: StreamingDataSource.RepeatKey,
            templateRowOffset: Int,
            itemIndex: Int
        ): Any? {
            val lastIdx = lastAdvancedIndex[repeatKey] ?: -1
            return if (templateRowOffset == 0 && itemIndex > lastIdx) {
                val newItem = dataSource.advanceToNextItem(repeatKey)
                currentItemsByRepeat[repeatKey] = newItem
                lastAdvancedIndex[repeatKey] = itemIndex
                newItem
            } else {
                currentItemsByRepeat[repeatKey] ?: dataSource.getCurrentItem(repeatKey)
            }
        }

        /** 모든 repeat 영역의 남은 아이템 검증 */
        fun checkRemainingItems() {
            currentItemsByRepeat.keys.forEach { dataSource.checkRemainingItems(it) }
        }
    }

    /**
     * StaticRow의 각 셀을 PositionCalculator로 계산된 위치에 수집한다.
     *
     * SXSSF는 순차적 행 작성만 지원하므로, 셀 정보만 수집하고
     * 실제 작성은 processSheetWithPendingRows의 정렬된 루프에서 수행된다.
     * emptyRange 영역의 셀 처리는 writePendingCells()에서 셀 단위로 수행된다.
     */
    private fun writeCellsAtCalculatedPositions(
        ctx: RowWriteContext,
        rowSpec: RowSpec.StaticRow,
        pendingCells: MutableMap<Int, MutableList<PendingCell>>
    ) {
        rowSpec.cells.forEach { cellSpec ->
            val (actualRow, _) = ctx.calculator.getFinalPosition(rowSpec.templateRowIndex, cellSpec.columnIndex)
            pendingCells.getOrPut(actualRow) { mutableListOf() }.add(
                PendingCell(
                    columnIndex = cellSpec.columnIndex,
                    styleIndex = cellSpec.styleIndex,
                    content = cellSpec.content,
                    height = rowSpec.height,
                    templateRowIndex = rowSpec.templateRowIndex,
                    rowSpec = rowSpec
                )
            )
        }
    }

    /**
     * 대기 중인 셀 정보 (SXSSF 순차 작성용)
     */
    private data class PendingCell(
        val columnIndex: Int,
        val styleIndex: Short,
        val content: CellContent,
        val height: Short?,
        val templateRowIndex: Int,
        val rowSpec: RowSpec,
        val itemData: Map<String, Any>? = null,
        val repeatIndex: Int = 0,
        val repeatItemIndex: Int = 0,
        val totalRowOffset: Int = 0,
        val repeatRow: RowSpec.RepeatRow? = null
    )

    /** SXSSF용 셀 내용 처리 (PositionCalculator 기반) */
    private fun processCellContentSxssfWithCalculator(
        ctx: RowWriteContext,
        cell: Cell,
        content: CellContent,
        data: Map<String, Any>,
        repeatInfo: RepeatInfo,
        rowSpec: RowSpec,
        columnIndex: Int,
        actualRowIndex: Int
    ) = when (content) {
        is CellContent.Formula -> processFormulaSxssfWithCalculator(
            ctx, cell, content, repeatInfo.index, rowSpec, columnIndex, actualRowIndex
        )

        is CellContent.FormulaWithVariables -> {
            var formula = ctx.context.evaluateText(content.formula, data)
            if (repeatInfo.index > 0) formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatInfo.index)
            cell.cellFormula = formula
        }

        is CellContent.ImageMarker -> processImageMarkerWithCalculator(
            cell, content, ctx.sheetIndex, ctx.imageLocations, repeatInfo.itemIndex, ctx.calculator
        )

        else -> processCellContent(
            cell, content, data, ctx.sheetIndex, ctx.imageLocations, ctx.context,
            repeatInfo.totalRowOffset, 0, repeatInfo.itemIndex
        )
    }

    /** SXSSF에서 이미지 마커를 처리한다 (PositionCalculator 기반) */
    private fun processImageMarkerWithCalculator(
        cell: Cell,
        content: CellContent.ImageMarker,
        sheetIndex: Int,
        imageLocations: MutableList<ImageLocation>,
        repeatItemIndex: Int,
        calculator: PositionCalculator
    ) {
        // position이 지정된 이미지는 첫 번째 반복에서만 처리 (중복 방지)
        if (content.position != null && repeatItemIndex > 0) {
            cell.setBlank()
            return
        }

        imageLocations.add(
            ImageLocation(
                sheetIndex = sheetIndex,
                imageName = content.imageName,
                position = content.position?.let { adjustPositionWithCalculator(it, calculator) },
                markerRowIndex = cell.rowIndex,
                markerColIndex = cell.columnIndex,
                sizeSpec = content.sizeSpec
            )
        )
        cell.setBlank()
    }

    /** PositionCalculator를 사용하여 위치/범위 문자열을 조정한다 */
    private fun adjustPositionWithCalculator(position: String, calculator: PositionCalculator): String =
        if (position.contains(":")) {
            val (start, end) = position.split(":")
            val (startRow, startCol) = parseCellRef(start)
            val (endRow, endCol) = parseCellRef(end)
            val f = calculator.getFinalRange(startRow, endRow, startCol, endCol)
            toRangeRef(f.firstRow, f.firstColumn, f.lastRow, f.lastColumn)
        } else {
            adjustCellRefWithCalculator(position, calculator)
        }

    /** PositionCalculator를 사용하여 셀 참조를 조정한다 */
    private fun adjustCellRefWithCalculator(ref: String, calculator: PositionCalculator): String {
        val (row, col) = parseCellRef(ref)
        val (newRow, newCol) = calculator.getFinalPosition(row, col)
        return toCellRef(newRow, newCol)
    }

    /** SXSSF에서 수식을 처리한다 (PositionCalculator 기반) */
    private fun processFormulaSxssfWithCalculator(
        ctx: RowWriteContext,
        cell: Cell,
        content: CellContent.Formula,
        repeatIndex: Int,
        rowSpec: RowSpec,
        columnIndex: Int,
        actualRowIndex: Int
    ) {
        var formula = content.formula

        if (repeatIndex > 0) {
            formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatIndex)
        }

        if (rowSpec is RowSpec.StaticRow) {
            formula = FormulaAdjuster.adjustWithPositionCalculator(formula, ctx.calculator)
            formula = expandFormulaRanges(ctx, formula, columnIndex, actualRowIndex, content.formula)
        }

        runCatching { cell.cellFormula = formula }
            .onFailure { runCatching { cell.cellFormula = content.formula } }
    }

    /** SUM 등 집계 함수의 범위를 반복 영역에 맞게 확장한다 */
    private fun expandFormulaRanges(
        ctx: RowWriteContext,
        formula: String,
        columnIndex: Int,
        actualRowIndex: Int,
        originalFormula: String
    ): String {
        var result = formula

        for (repeatRegion in ctx.repeatRegions.values) {
            val itemCount = ctx.context.collectionSizes[repeatRegion.collectionName]
                ?: (ctx.data[repeatRegion.collectionName] as? Collection<*>)?.size
                ?: continue

            val expansion = ctx.calculator.getExpansionForRegion(
                repeatRegion.collectionName, repeatRegion.templateRowIndex, repeatRegion.repeatStartCol
            ) ?: continue

            if (itemCount > 1) {
                val (expanded, isSeq) = FormulaAdjuster.expandToRangeWithCalculator(result, expansion, itemCount)
                if (expanded != result) {
                    validateFormulaExpansion(
                        itemCount, isSeq, ctx.sheet.sheetName,
                        toCellRef(actualRowIndex, columnIndex), originalFormula
                    )
                    result = expanded
                }
            }
        }

        return result
    }

    /** RIGHT 방향 반복 영역을 처리한다 (가로 확장) */
    private fun writeRowSxssfWithRightExpansion(
        ctx: RowWriteContext,
        startRowIndex: Int,
        repeatRow: RowSpec.RepeatRow,
        items: Collection<*>
    ) {
        val expansion = ctx.calculator.getExpansionForRegion(
            repeatRow.collectionName, repeatRow.templateRowIndex, repeatRow.repeatStartCol
        )
        val templateColCount = repeatRow.repeatEndCol - repeatRow.repeatStartCol + 1
        val templateRowCount = repeatRow.repeatEndRowIndex - repeatRow.templateRowIndex + 1
        val colShiftAmount = (items.size - 1) * templateColCount
        val repeatColRange = repeatRow.repeatStartCol..repeatRow.repeatEndCol

        for (rowOffset in 0 until templateRowCount) {
            val templateRowIdx = repeatRow.templateRowIndex + rowOffset
            val currentRowIndex = startRowIndex + rowOffset

            val rowSpec = ctx.blueprint.rows.find { it.templateRowIndex == templateRowIdx }
            val cellSpecs = rowSpec?.cells ?: continue

            val row = ctx.sheet.createRow(currentRowIndex)
            rowSpec.height?.let { row.height = it }

            // 반복 영역 밖의 셀들
            cellSpecs.filter { it.columnIndex !in repeatColRange }.forEach { cellSpec ->
                val actualColIndex = if (cellSpec.columnIndex > repeatRow.repeatEndCol) {
                    cellSpec.columnIndex + colShiftAmount
                } else {
                    cellSpec.columnIndex
                }
                val cell = row.createCellWithStyle(actualColIndex, cellSpec.styleIndex)
                val adjustedContent = adjustContentForRightExpansion(
                    cellSpec, repeatRow, items, colShiftAmount, ctx.sheet, currentRowIndex, actualColIndex, ctx.calculator
                )
                writeCellContentSxssf(ctx, cell, adjustedContent, colOffset = colShiftAmount)
            }

            // 각 아이템에 대해 열 방향으로 확장
            items.forEachIndexed { itemIdx, item ->
                val itemData = createItemData(ctx.data, repeatRow.itemVariable, item)

                cellSpecs.filter { it.columnIndex in repeatColRange }.forEach { cellSpec ->
                    val templateColOffset = cellSpec.columnIndex - repeatRow.repeatStartCol
                    val targetColIdx = expansion?.let {
                        ctx.calculator.getColForRepeatItem(it, itemIdx, templateColOffset)
                    } ?: (repeatRow.repeatStartCol + (itemIdx * templateColCount) + templateColOffset)

                    val cell = row.createCellWithStyle(targetColIdx, cellSpec.styleIndex)
                    writeCellContentSxssf(ctx, cell, cellSpec.content, itemData, colShiftAmount, itemIdx)
                }
            }
        }
    }

    /** 오른쪽 확장 시 수식을 조정한다 */
    private fun adjustContentForRightExpansion(
        cellSpec: CellSpec,
        repeatRow: RowSpec.RepeatRow,
        items: Collection<*>,
        colShiftAmount: Int,
        sheet: SXSSFSheet,
        currentRowIndex: Int,
        actualColIndex: Int,
        calculator: PositionCalculator
    ): CellContent {
        val content = cellSpec.content
        if (content !is CellContent.Formula) return content

        var formula = content.formula

        // 열 이동에 따른 수식 참조 조정
        if (colShiftAmount > 0) {
            formula = FormulaAdjuster.adjustForColumnExpansion(formula, repeatRow.repeatEndCol + 1, colShiftAmount)
        }

        // 반복 영역 오른쪽 수식의 범위 확장
        if (cellSpec.columnIndex > repeatRow.repeatEndCol && items.size > 1) {
            calculator.getExpansionFor(repeatRow.collectionName)?.let { expansion ->
                val (expandedFormula, isSeq) = FormulaAdjuster.expandToRangeWithCalculator(
                    formula, expansion, items.size
                )
                if (expandedFormula != formula) {
                    validateFormulaExpansion(
                        items.size, isSeq, sheet.sheetName,
                        toCellRef(currentRowIndex, actualColIndex), formula
                    )
                    formula = expandedFormula
                }
            }
        }

        return CellContent.Formula(formula)
    }

    private fun writeCellContentSxssf(
        ctx: RowWriteContext,
        cell: Cell,
        content: CellContent,
        data: Map<String, Any>? = null,
        colOffset: Int = 0,
        repeatItemIndex: Int = 0
    ) {
        val effectiveData = data ?: ctx.data
        when (content) {
            is CellContent.Formula -> cell.cellFormula = content.formula
            is CellContent.FormulaWithVariables ->
                cell.cellFormula = ctx.context.evaluateText(content.formula, effectiveData)
            is CellContent.ImageMarker ->
                processImageMarker(cell, content, ctx.sheetIndex, ctx.imageLocations, 0, colOffset, repeatItemIndex)
            else -> processCellContent(
                cell, content, effectiveData, ctx.sheetIndex, ctx.imageLocations, ctx.context,
                0, colOffset, repeatItemIndex
            )
        }
    }
}
