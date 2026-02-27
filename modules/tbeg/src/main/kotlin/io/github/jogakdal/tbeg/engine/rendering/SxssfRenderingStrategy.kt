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
        // <dxfs> 스타일 유지를 위해 템플릿 워크북을 닫지 않고 재사용
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
        val xssfWorkbook = (workbook as SXSSFWorkbook).xssfWorkbook

        // 스타일 추출 및 매핑
        styleMap = extractStyles(xssfWorkbook).mapValues { (_, style) ->
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
        isSequential: Boolean,
        sheetName: String,
        cellRef: String,
        formula: String
    ) {
        if (!isSequential && itemCount > 255) {
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

        val repeatRegions = blueprint.repeatRegions

        // repeat 영역 겹침 검증
        PositionCalculator.validateNoOverlap(repeatRegions)

        // collection 크기 추출 및 PositionCalculator 생성
        val collectionSizes = if (context.streamingDataSource != null) {
            context.collectionSizes
        } else {
            PositionCalculator.extractCollectionSizes(data, repeatRegions)
        }
        val templateLastRow = blueprint.rows.maxOfOrNull { it.templateRowIndex } ?: 0
        val calculator = PositionCalculator(repeatRegions, collectionSizes, templateLastRow)
        calculator.calculate()

        // 같은 행에 여러 RepeatRegion이 있을 수 있으므로 groupBy
        val regionsByStartRow = repeatRegions.groupBy { it.area.start.row }

        // 스트리밍 모드 분기
        val hasRightRepeat = repeatRegions.any { it.direction == RepeatDirection.RIGHT }
        val effectiveData = if (context.streamingDataSource != null && hasRightRepeat) {
            buildRightRepeatData(data, repeatRegions, context)
        } else data

        val ctx = RowWriteContext(
            sheet, sheetIndex, blueprint, effectiveData, styleMap, regionsByStartRow,
            imageLocations, context, calculator, (sheet.workbook as SXSSFWorkbook).xssfWorkbook
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
            sheet, blueprint.conditionalFormattings, blueprint.repeatRegions, data, maxRowOffset, collectionSizes
        )

        // 빈 컬렉션의 emptyRange 조건부 서식 적용
        context.sheetLayoutApplier.applyEmptyRangeConditionalFormattings(
            sheet, blueprint.repeatRegions, collectionSizes, calculator
        )

        // 차트 범위 조정을 위한 repeat 확장 정보 수집 (ChartRestoreProcessor에서 사용)
        if (repeatRegions.isNotEmpty()) {
            context.repeatExpansionInfos[sheet.sheetName] = with(ChartRangeAdjuster) {
                repeatRegions.map { region ->
                    region.toExpansionInfo(collectionSizes[region.collection] ?: 1)
                }.sortedBy { it.templateStartRow }
            }
        }
    }

    /**
     * pendingRows 방식: 모든 셀 정보를 수집한 후 행 순서로 출력
     *
     * RIGHT 방향 repeat(가로 확장)이 있는 경우 사용
     */
    private fun processSheetWithPendingRows(ctx: RowWriteContext) {
        val pendingRows = TreeMap<Int, MutableList<PendingCell>>()
        // 셀이 없지만 높이가 설정된 행의 높이 맵 (actualRow → height)
        val rowHeights = mutableMapOf<Int, Short>()
        // 이미 처리된 repeat 시작 행 추적
        val processedRepeatStartRows = mutableSetOf<Int>()

        // 모든 행 정보 수집
        for (rowSpec in ctx.blueprint.rows) {
            val templateRow = rowSpec.templateRowIndex
            val regions = ctx.regionsByStartRow[templateRow]

            when {
                // repeat 영역 시작 행
                regions != null && processedRepeatStartRows.add(templateRow) -> {
                    var isFirstRepeatInRow = true

                    for (region in regions) {
                        val items = ctx.data[region.collection] as? Collection<*> ?: emptyList<Any>()

                        val allRepeatColRanges = ctx.regionsByStartRow[region.area.start.row]
                            ?.map { it.area.colRange }
                            ?: listOf(region.area.colRange)

                        when (region.direction) {
                            RepeatDirection.DOWN -> collectDownRepeatCells(
                                ctx, region, items, pendingRows, allRepeatColRanges, isFirstRepeatInRow
                            )
                            RepeatDirection.RIGHT -> {
                                val actualRow = ctx.calculator.getFinalPosition(
                                    region.area.start.row, region.area.start.col
                                ).row
                                writeRowSxssfWithRightExpansion(ctx, actualRow, region, items)
                            }
                        }

                        isFirstRepeatInRow = false
                    }
                }

                // repeat 영역 내부 행 (continuation) → 건너뜀
                templateRow in ctx.repeatRowIndices -> Unit

                // 정적 행
                else -> {
                    writeCellsAtCalculatedPositions(ctx, rowSpec, pendingRows)
                    // 셀이 없어도 높이가 있는 행은 rowHeights에 수집
                    rowSpec.height?.let { h ->
                        val actualRow = ctx.calculator.getFinalPosition(templateRow, 0).row
                        rowHeights[actualRow] = h
                    }
                }
            }
        }

        // 행 인덱스 순서대로 셀 작성
        writePendingCells(ctx, pendingRows, rowHeights)

        // emptyRange 병합 영역 적용
        ctx.emptyRangeMergedRegions.forEach { ctx.sheet.addMergedRegion(it) }
    }

    /**
     * DOWN 방향 반복 영역의 셀을 pendingRows에 수집한다
     *
     * @param allRepeatColRanges 같은 행의 모든 repeat 열 범위 (non-repeat 셀 판별용)
     * @param isFirstRepeatInRow 같은 행의 첫 번째 repeat인지 여부 (non-repeat 셀은 첫 번째에서만 수집)
     */
    private fun collectDownRepeatCells(
        ctx: RowWriteContext,
        region: RepeatRegionSpec,
        items: Collection<*>,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>,
        allRepeatColRanges: List<ColRange> = listOf(region.area.colRange),
        isFirstRepeatInRow: Boolean = true
    ) {
        val templateRowCount = region.area.rowRange.count
        val expansion = ctx.calculator.getExpansionForRegion(
            region.collection, region.area.start.row, region.area.start.col
        )
        val totalRepeatOffset = expansion?.rowExpansion ?: ((items.size * templateRowCount) - templateRowCount)

        // repeat 범위 밖의 셀은 같은 행의 첫 번째에서만 수집 (이미지 마커 등)
        if (isFirstRepeatInRow) {
            collectNonRepeatCells(ctx, region, allRepeatColRanges, pendingRows)
        }

        // 빈 컬렉션이고 emptyRangeContent가 있으면 그 내용을 수집
        if (items.isEmpty() && region.emptyRangeContent != null) {
            collectEmptyRangeContentCells(ctx, region, pendingRows)
            return
        }

        // 빈 컬렉션이면 null 아이템 추가 (기존 동작)
        val effectiveItems: Collection<Any?> = items.ifEmpty { listOf(null) }

        effectiveItems.forEachIndexed { itemIdx, item ->
            val itemData = createItemData(ctx.data, region.variable, item)

            for (templateOffset in 0 until templateRowCount) {
                val templateRowIdx = region.area.start.row + templateOffset
                val currentRowBp = ctx.blueprint.rowsByTemplateIndex[templateRowIdx] ?: continue

                val actualRow = expansion?.let {
                    ctx.calculator.getRowForRepeatItem(it, itemIdx, templateOffset)
                } ?: (region.area.start.row + (itemIdx * templateRowCount) + templateOffset)

                // repeat 범위 내 셀만 수집
                currentRowBp.cells
                    .filter { it.columnIndex in region.area.colRange }
                    .forEach { cellSpec ->
                        pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                            PendingCell(
                                columnIndex = cellSpec.columnIndex,
                                styleIndex = cellSpec.styleIndex,
                                content = cellSpec.content,
                                height = currentRowBp.height,
                                templateRowIndex = currentRowBp.templateRowIndex,
                                isStaticRow = false,
                                itemData = itemData,
                                repeatIndex = itemIdx * templateRowCount + templateOffset,
                                repeatItemIndex = itemIdx,
                                totalRowOffset = totalRepeatOffset
                            )
                        )
                    }
            }
        }
    }

    /**
     * repeat 범위 밖의 셀을 pendingRows에 수집한다 (이미지 마커 등)
     *
     * @param allRepeatColRanges 같은 행의 모든 repeat 열 범위 (이 범위에 속하는 셀은 건너뜀)
     */
    private fun collectNonRepeatCells(
        ctx: RowWriteContext,
        region: RepeatRegionSpec,
        allRepeatColRanges: List<ColRange>,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>
    ) {
        for (templateOffset in 0 until region.area.rowRange.count) {
            val templateRowIdx = region.area.start.row + templateOffset
            val currentRowBp = ctx.blueprint.rowsByTemplateIndex[templateRowIdx] ?: continue

            val actualRow = ctx.calculator.getFinalPosition(templateRowIdx, 0).row

            currentRowBp.cells
                .filter { spec -> allRepeatColRanges.none { spec.columnIndex in it } }
                .forEach { cellSpec ->
                    pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                        PendingCell(
                            columnIndex = cellSpec.columnIndex,
                            styleIndex = cellSpec.styleIndex,
                            content = cellSpec.content,
                            height = currentRowBp.height,
                            templateRowIndex = currentRowBp.templateRowIndex,
                            isStaticRow = true,
                            itemData = ctx.data
                        )
                    )
                }
        }
    }

    /** 빈 컬렉션일 때 emptyRangeContent 셀을 pendingRows에 수집한다 */
    private fun collectEmptyRangeContentCells(
        ctx: RowWriteContext,
        region: RepeatRegionSpec,
        pendingRows: MutableMap<Int, MutableList<PendingCell>>
    ) {
        val emptyRangeContent = region.emptyRangeContent ?: return
        val templateRowCount = region.area.rowRange.count
        val expansion = ctx.calculator.getExpansionForRegion(
            region.collection, region.area.start.row, region.area.start.col
        )
        val actualStartRow = expansion?.finalStartRow ?: region.area.start.row

        // 단일 셀이고 repeat 영역이 더 크면 병합 (병합 영역 등록, 실제 적용은 나중에)
        if (emptyRangeContent.isSingleCell && (templateRowCount > 1 || region.area.colRange.count > 1)) {
            val snapshot = emptyRangeContent.cells[0][0]
            pendingRows.getOrPut(actualStartRow) { mutableListOf() }.add(
                PendingCell(
                    columnIndex = region.area.start.col,
                    styleIndex = snapshot.styleIndex,
                    content = snapshot.toContent(),
                    height = emptyRangeContent.rowHeights.getOrNull(0),
                    templateRowIndex = region.area.start.row,
                    isStaticRow = true,
                    itemData = ctx.data
                )
            )
            ctx.emptyRangeMergedRegions.add(CellRangeAddress(
                actualStartRow, actualStartRow + templateRowCount - 1,
                region.area.start.col, region.area.end.col
            ))
            return
        }

        val rowsToWrite = minOf(templateRowCount, emptyRangeContent.rowCount)
        val colsToWrite = minOf(region.area.colRange.count, emptyRangeContent.colCount)

        for (rowOffset in 0 until rowsToWrite) {
            val actualRow = actualStartRow + rowOffset
            val rowHeight = emptyRangeContent.rowHeights.getOrNull(rowOffset)

            for (colOffset in 0 until colsToWrite) {
                val colIndex = region.area.start.col + colOffset
                val snapshot = emptyRangeContent.cells.getOrNull(rowOffset)?.getOrNull(colOffset) ?: continue

                pendingRows.getOrPut(actualRow) { mutableListOf() }.add(
                    PendingCell(
                        columnIndex = colIndex,
                        styleIndex = snapshot.styleIndex,
                        content = snapshot.toContent(),
                        height = rowHeight,
                        templateRowIndex = region.area.start.row + rowOffset,
                        isStaticRow = true,
                        itemData = ctx.data
                    )
                )
            }
        }
    }


    /**
     * 수집된 pendingRows를 행 순서대로 작성한다 (TreeMap이므로 이미 정렬됨)
     *
     * @param rowHeights 셀이 없지만 높이가 설정된 정적 행의 높이 맵 (actualRow → height)
     */
    private fun writePendingCells(
        ctx: RowWriteContext,
        pendingRows: TreeMap<Int, MutableList<PendingCell>>,
        rowHeights: Map<Int, Short> = emptyMap()
    ) {
        // 셀이 없지만 높이가 있는 행도 pendingRows에 등록
        for (actualRow in rowHeights.keys) {
            pendingRows.getOrPut(actualRow) { mutableListOf() }
        }

        for ((actualRow, cells) in pendingRows) {
            val row = ctx.sheet.getRow(actualRow) ?: ctx.sheet.createRow(actualRow)
            (cells.firstOrNull()?.height ?: rowHeights[actualRow])?.let { row.height = it }

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
                        repeatInfo, pendingCell.isStaticRow, pendingCell.columnIndex, actualRow
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
        // non-repeat 셀은 같은 행의 첫 번째 repeat에서만 처리하기 위해 추적
        val processedNonRepeatRows = mutableSetOf<Int>()

        for ((_, regionList) in ctx.regionsByStartRow) {
            for (region in regionList) {
            val expansion = ctx.calculator.getExpansionForRegion(
                region.collection, region.area.start.row, region.area.start.col
            ) ?: continue

            val templateRowCount = region.area.rowRange.count
            val repeatStartRow = expansion.finalStartRow

            // 빈 컬렉션 + emptyRangeContent가 있는 경우
            val originalItemCount = ctx.context.collectionSizes[region.collection] ?: 0
            if (originalItemCount == 0 && region.emptyRangeContent != null) {
                val result = writeEmptyRangeForStreaming(
                    ctx, row, actualRow, region, repeatStartRow, templateRowCount, rowHeight
                )
                rowHeight = result.rowHeight
                continue
            }

            val repeatRows = RowRange(repeatStartRow, repeatStartRow + (templateRowCount * maxOf(expansion.itemCount, 1)) - 1)

            if (actualRow !in repeatRows) continue

            val rowWithinRepeat = actualRow - repeatStartRow
            val itemIndex = rowWithinRepeat / templateRowCount
            val templateRowOffset = rowWithinRepeat % templateRowCount

            val repeatKey = StreamingDataSource.RepeatKey(
                ctx.sheetIndex, region.collection, region.area.start.row, region.area.start.col
            )
            val item = state.getItemForRow(repeatKey, templateRowOffset, itemIndex)
            val itemData = createItemData(ctx.data, region.variable, item)

            val templateRowForCells = region.area.start.row + templateRowOffset
            val rowSpec = ctx.blueprint.rowsByTemplateIndex[templateRowForCells] ?: continue

            rowSpec.height?.let { h -> rowHeight = maxOf(rowHeight ?: 0, h) }

            val repeatInfo = RepeatInfo(
                index = itemIndex * templateRowCount + templateRowOffset,
                totalRowOffset = expansion.rowExpansion,
                itemIndex = itemIndex
            )

            // repeat 범위 내 셀 처리
            rowSpec.cells
                .filter { it.columnIndex in region.area.colRange }
                .forEach { cellSpec ->
                    val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                    processCellContentSxssfWithCalculator(
                        ctx, cell, cellSpec.content, itemData, repeatInfo, false, cellSpec.columnIndex, actualRow
                    )
                }

            // repeat 범위 밖의 셀은 같은 행의 첫 번째 repeat의 첫 아이템 첫 행에서만 처리
            if (itemIndex == 0 && templateRowOffset == 0 && processedNonRepeatRows.add(region.area.start.row)) {
                val allRepeatColRanges = ctx.regionsByStartRow[region.area.start.row]
                    ?.map { it.area.colRange }
                    ?: listOf(region.area.colRange)

                rowSpec.cells
                    .filter { spec -> allRepeatColRanges.none { spec.columnIndex in it } }
                    .forEach { cellSpec ->
                        val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                        processCellContentSxssfWithCalculator(
                            ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE, true, cellSpec.columnIndex, actualRow
                        )
                    }
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
        region: RepeatRegionSpec,
        repeatStartRow: Int,
        templateRowCount: Int,
        currentRowHeight: Short?
    ): EmptyRangeResult {
        val emptyRangeContent = region.emptyRangeContent ?: return EmptyRangeResult(true, currentRowHeight)
        var rowHeight = currentRowHeight

        // 첫 번째 행에서 repeat 범위 밖의 셀 처리 (이미지 마커 등)
        if (actualRow == repeatStartRow) {
            val rowSpec = ctx.blueprint.rowsByTemplateIndex[region.area.start.row]
            rowSpec?.cells
                ?.filter { it.columnIndex !in region.area.colRange }
                ?.forEach { cellSpec ->
                    val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                    processCellContentSxssfWithCalculator(
                        ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE,
                        true, cellSpec.columnIndex, actualRow
                    )
                }
        }

        // 단일 셀이고 repeat 영역이 더 크면 병합
        if (emptyRangeContent.isSingleCell && (templateRowCount > 1 || region.area.colRange.count > 1)) {
            if (actualRow == repeatStartRow) {
                val snapshot = emptyRangeContent.cells[0][0]
                val cell = row.createCellWithStyle(region.area.start.col, snapshot.styleIndex)
                writeCellFromSnapshot(cell, snapshot, ctx.xssfWorkbook)

                ctx.sheet.addMergedRegion(CellRangeAddress(
                    repeatStartRow, repeatStartRow + templateRowCount - 1,
                    region.area.start.col, region.area.end.col
                ))
            }
            return EmptyRangeResult(true, rowHeight)
        }

        val rowsToWrite = minOf(templateRowCount, emptyRangeContent.rowCount)
        if (actualRow !in repeatStartRow until (repeatStartRow + rowsToWrite)) {
            return EmptyRangeResult(true, rowHeight)
        }

        val rowOffset = actualRow - repeatStartRow
        val colsToWrite = minOf(region.area.colRange.count, emptyRangeContent.colCount)

        emptyRangeContent.rowHeights.getOrNull(rowOffset)?.let { h ->
            rowHeight = maxOf(rowHeight ?: 0, h)
        }

        for (colOffset in 0 until colsToWrite) {
            val colIndex = region.area.start.col + colOffset
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

        for ((rowSpec, staticCells) in ctx.staticRowsWithCells) {
            // 셀이 없지만 높이가 있는 행 처리
            if (staticCells.isEmpty()) {
                if (rowSpec.height != null) {
                    val rowInfo = ctx.calculator.getRowInfoForColumn(actualRow, 0)
                    if (rowInfo is RowInfo.Static && rowInfo.templateRowIndex == rowSpec.templateRowIndex) {
                        rowHeight = maxOf(rowHeight ?: 0, rowSpec.height)
                    }
                }
                continue
            }

            for (cellSpec in staticCells) {
                val rowInfo = ctx.calculator.getRowInfoForColumn(actualRow, cellSpec.columnIndex)
                if (rowInfo is RowInfo.Static && rowInfo.templateRowIndex == rowSpec.templateRowIndex) {
                    if (row.getCell(cellSpec.columnIndex) == null) {
                        rowSpec.height?.let { h -> rowHeight = maxOf(rowHeight ?: 0, h) }

                        // emptyRange 영역의 셀은 빈 셀 + 기본 스타일로 처리
                        if (ctx.calculator.isInEmptyRange(rowSpec.templateRowIndex, cellSpec.columnIndex)) {
                            val cell = row.createCell(cellSpec.columnIndex)
                            cell.setBlank()
                        } else {
                            val cell = row.createCellWithStyle(cellSpec.columnIndex, cellSpec.styleIndex)
                            processCellContentSxssfWithCalculator(
                                ctx, cell, cellSpec.content, ctx.data, RepeatInfo.NONE,
                                true, cellSpec.columnIndex, actualRow
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
        /** 같은 행에 여러 RepeatRegion이 있을 수 있으므로 startRow 기준 groupBy */
        val regionsByStartRow: Map<Int, List<RepeatRegionSpec>>,
        val imageLocations: MutableList<ImageLocation>,
        val context: RenderingContext,
        val calculator: PositionCalculator,
        val xssfWorkbook: XSSFWorkbook,
        val emptyRangeMergedRegions: MutableList<CellRangeAddress> = mutableListOf()
    ) {
        /** repeat 영역에 속한 행 인덱스 (건너뛰기용, 캐시) */
        val repeatRowIndices: Set<Int> by lazy {
            blueprint.repeatRegions.flatMap { it.area.rowRange }.toSet()
        }

        /** 정적 행 + repeat 영역에 속하지 않는 셀 사전 필터링 (높이 전용 행도 포함) */
        val staticRowsWithCells: List<Pair<RowSpec, List<CellSpec>>> by lazy {
            blueprint.rows
                .filter { it.templateRowIndex !in repeatRowIndices }
                .map { rowSpec ->
                    rowSpec to rowSpec.cells.filter { cellSpec ->
                        blueprint.repeatRegions.none { region ->
                            cellSpec.columnIndex in region.area.colRange &&
                                rowSpec.templateRowIndex in region.area.rowRange
                        }
                    }
                }
                .filter { (rowSpec, cells) -> cells.isNotEmpty() || rowSpec.height != null }
        }
    }

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
        rowSpec: RowSpec,
        pendingCells: MutableMap<Int, MutableList<PendingCell>>
    ) {
        rowSpec.cells.forEach { cellSpec ->
            val actualRow = ctx.calculator.getFinalPosition(rowSpec.templateRowIndex, cellSpec.columnIndex).row
            pendingCells.getOrPut(actualRow) { mutableListOf() }.add(
                PendingCell(
                    columnIndex = cellSpec.columnIndex,
                    styleIndex = cellSpec.styleIndex,
                    content = cellSpec.content,
                    height = rowSpec.height,
                    templateRowIndex = rowSpec.templateRowIndex,
                    isStaticRow = true
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
        val isStaticRow: Boolean = true,
        val itemData: Map<String, Any>? = null,
        val repeatIndex: Int = 0,
        val repeatItemIndex: Int = 0,
        val totalRowOffset: Int = 0
    )

    /** SXSSF용 셀 내용 처리 (PositionCalculator 기반) */
    private fun processCellContentSxssfWithCalculator(
        ctx: RowWriteContext,
        cell: Cell,
        content: CellContent,
        data: Map<String, Any>,
        repeatInfo: RepeatInfo,
        isStaticRow: Boolean,
        columnIndex: Int,
        actualRowIndex: Int
    ) = when (content) {
        is CellContent.Formula -> processFormulaSxssfWithCalculator(
            ctx, cell, content, repeatInfo.index, isStaticRow, columnIndex, actualRowIndex
        )

        is CellContent.FormulaWithVariables -> {
            var formula = ctx.context.evaluateText(content.formula, data)
            if (isStaticRow) {
                if (repeatInfo.index > 0) formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatInfo.index)
            } else {
                val rowInfo = ctx.calculator.getRowInfoForColumn(actualRowIndex, columnIndex)
                if (rowInfo is RowInfo.Repeat) {
                    formula = FormulaAdjuster.adjustRefsOutsideRepeat(
                        formula, rowInfo.repeatRegion.area, ctx.calculator
                    )
                    if (repeatInfo.index > 0) {
                        formula = FormulaAdjuster.adjustForRepeatIndex(
                            formula, repeatInfo.index, rowInfo.repeatRegion.area
                        )
                    }
                } else if (repeatInfo.index > 0) {
                    formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatInfo.index)
                }
            }
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
                sizeSpec = content.sizeSpec,
                hAlign = cell.cellStyle?.alignment ?: HorizontalAlignment.GENERAL,
                vAlign = cell.cellStyle?.verticalAlignment ?: VerticalAlignment.TOP
            )
        )
        cell.setBlank()
    }

    /** PositionCalculator를 사용하여 위치/범위 문자열을 조정한다 */
    private fun adjustPositionWithCalculator(position: String, calculator: PositionCalculator): String =
        if (position.contains(":")) {
            val (start, end) = position.split(":")
            val f = calculator.getFinalRange(parseCellRef(start), parseCellRef(end))
            toRangeRef(f.firstRow, f.firstColumn, f.lastRow, f.lastColumn)
        } else {
            adjustCellRefWithCalculator(position, calculator)
        }

    /** PositionCalculator를 사용하여 셀 참조를 조정한다 */
    private fun adjustCellRefWithCalculator(ref: String, calculator: PositionCalculator) =
        calculator.getFinalPosition(parseCellRef(ref)).toCellRefString()

    /** SXSSF에서 수식을 처리한다 (PositionCalculator 기반) */
    private fun processFormulaSxssfWithCalculator(
        ctx: RowWriteContext,
        cell: Cell,
        content: CellContent.Formula,
        repeatIndex: Int,
        isStaticRow: Boolean,
        columnIndex: Int,
        actualRowIndex: Int
    ) {
        var formula = content.formula

        if (isStaticRow) {
            if (repeatIndex > 0) {
                formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatIndex)
            }
            formula = FormulaAdjuster.adjustWithPositionCalculator(formula, ctx.calculator)
            formula = expandFormulaRanges(ctx, formula, columnIndex, actualRowIndex, content.formula)
        } else {
            val rowInfo = ctx.calculator.getRowInfoForColumn(actualRowIndex, columnIndex)
            if (rowInfo is RowInfo.Repeat) {
                // 순서 중요: 영역 밖 시프트를 먼저 적용해야 원본 수식에서 정확하게 영역 안/밖 판단 가능
                formula = FormulaAdjuster.adjustRefsOutsideRepeat(
                    formula, rowInfo.repeatRegion.area, ctx.calculator
                )
                if (repeatIndex > 0) {
                    formula = FormulaAdjuster.adjustForRepeatIndex(
                        formula, repeatIndex, rowInfo.repeatRegion.area
                    )
                }
            } else if (repeatIndex > 0) {
                formula = FormulaAdjuster.adjustForRepeatIndex(formula, repeatIndex)
            }
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

        for (region in ctx.blueprint.repeatRegions) {
            val itemCount = ctx.context.collectionSizes[region.collection]
                ?: (ctx.data[region.collection] as? Collection<*>)?.size
                ?: continue

            val expansion = ctx.calculator.getExpansionForRegion(
                region.collection, region.area.start.row, region.area.start.col
            ) ?: continue

            if (itemCount > 1) {
                val (expanded, isSequential) = FormulaAdjuster.expandToRangeWithCalculator(result, expansion, itemCount)
                if (expanded != result) {
                    validateFormulaExpansion(
                        itemCount, isSequential, ctx.sheet.sheetName,
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
        region: RepeatRegionSpec,
        items: Collection<*>
    ) {
        val expansion = ctx.calculator.getExpansionForRegion(
            region.collection, region.area.start.row, region.area.start.col
        )
        val colShiftAmount = (items.size - 1) * region.area.colRange.count

        for (rowOffset in 0 until region.area.rowRange.count) {
            val templateRowIdx = region.area.start.row + rowOffset
            val currentRowIndex = startRowIndex + rowOffset

            val rowSpec = ctx.blueprint.rowsByTemplateIndex[templateRowIdx]
            val cellSpecs = rowSpec?.cells ?: continue

            val row = ctx.sheet.createRow(currentRowIndex)
            rowSpec.height?.let { row.height = it }

            // 반복 영역 밖의 셀들
            cellSpecs.filter { it.columnIndex !in region.area.colRange }.forEach { cellSpec ->
                val actualColIndex = if (cellSpec.columnIndex > region.area.end.col) {
                    cellSpec.columnIndex + colShiftAmount
                } else {
                    cellSpec.columnIndex
                }
                val cell = row.createCellWithStyle(actualColIndex, cellSpec.styleIndex)
                val adjustedContent = adjustContentForRightExpansion(
                    cellSpec, region, items, colShiftAmount, ctx.sheet, currentRowIndex, actualColIndex, ctx.calculator
                )
                writeCellContentSxssf(ctx, cell, adjustedContent, colOffset = colShiftAmount)
            }

            // 각 아이템에 대해 열 방향으로 확장
            items.forEachIndexed { itemIdx, item ->
                val itemData = createItemData(ctx.data, region.variable, item)

                cellSpecs.filter { it.columnIndex in region.area.colRange }.forEach { cellSpec ->
                    val templateColOffset = cellSpec.columnIndex - region.area.start.col
                    val targetColIdx = expansion?.let {
                        ctx.calculator.getColForRepeatItem(it, itemIdx, templateColOffset)
                    } ?: (region.area.start.col + (itemIdx * region.area.colRange.count) + templateColOffset)

                    val cell = row.createCellWithStyle(targetColIdx, cellSpec.styleIndex)
                    writeCellContentSxssf(ctx, cell, cellSpec.content, itemData, colShiftAmount, itemIdx)
                }
            }
        }
    }

    /** 오른쪽 확장 시 수식을 조정한다 */
    private fun adjustContentForRightExpansion(
        cellSpec: CellSpec,
        region: RepeatRegionSpec,
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
            formula = FormulaAdjuster.adjustForColumnExpansion(formula, region.area.end.col + 1, colShiftAmount)
        }

        // 반복 영역 오른쪽 수식의 범위 확장
        if (cellSpec.columnIndex > region.area.end.col && items.size > 1) {
            calculator.getExpansionFor(region.collection)?.let { expansion ->
                val (expandedFormula, isSequential) = FormulaAdjuster.expandToRangeWithCalculator(
                    formula, expansion, items.size
                )
                if (expandedFormula != formula) {
                    validateFormulaExpansion(
                        items.size, isSequential, sheet.sheetName,
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
