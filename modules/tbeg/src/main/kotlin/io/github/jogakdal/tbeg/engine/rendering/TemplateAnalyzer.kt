package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.internal.commonLogger
import io.github.jogakdal.tbeg.engine.core.*
import io.github.jogakdal.tbeg.engine.core.CellArea
import io.github.jogakdal.tbeg.engine.rendering.parser.UnifiedMarkerParser
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.AreaReference
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

/**
 * 템플릿 분석기 - 템플릿을 분석하여 워크북 명세 생성
 */
class TemplateAnalyzer {

    companion object {
        private val LOG by commonLogger()
    }

    /**
     * 템플릿을 분석하여 워크북 명세 생성
     */
    fun analyze(template: InputStream) =
        XSSFWorkbook(template).use(::analyzeWorkbook)

    /**
     * 워크북과 함께 분석 (워크북 재사용 시)
     */
    fun analyzeFromWorkbook(workbook: XSSFWorkbook) = analyzeWorkbook(workbook)

    private fun analyzeWorkbook(workbook: XSSFWorkbook): WorkbookSpec {
        // Phase 1: 모든 시트에서 repeat 마커 수집
        val allRegions = (0 until workbook.numberOfSheets).flatMap { index ->
            collectRepeatRegions(workbook, workbook.getSheetAt(index))
        }

        // Phase 2: 중복 제거 (같은 컬렉션 + 같은 대상 시트 + 같은 영역)
        val dedupedBySheet = deduplicateRepeatRegions(allRegions)

        // Phase 3: SheetSpec 생성
        val sheets = (0 until workbook.numberOfSheets).map { index ->
            val sheet = workbook.getSheetAt(index)
            analyzeSheet(workbook, sheet, index, dedupedBySheet[sheet.sheetName] ?: emptyList())
        }

        // Phase 4: 셀 단위 범위 마커 중복 제거 (image 등)
        return WorkbookSpec(sheets = deduplicateCellMarkers(sheets))
    }

    private fun analyzeSheet(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        sheetIndex: Int,
        rawRepeatRegions: List<RepeatRegionSpec>
    ): SheetSpec {
        val repeatRegions = rawRepeatRegions.map { region ->
            // emptyRange가 있으면 해당 셀 내용을 미리 읽어둠
            val emptyRangeContent = region.emptyRange?.let { readEmptyRangeContent(workbook, sheet, it) }
            region.copy(emptyRangeContent = emptyRangeContent)
        }

        return SheetSpec(
            sheetName = sheet.sheetName,
            sheetIndex = sheetIndex,
            rows = buildRowSpecs(sheet, repeatRegions),
            mergedRegions = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) },
            columnWidths = (0..sheet.maxColumnIndex()).associateWith { sheet.getColumnWidth(it) },
            defaultRowHeight = sheet.defaultRowHeight,
            headerFooter = extractHeaderFooter(sheet),
            printSetup = extractPrintSetup(sheet),
            conditionalFormattings = extractConditionalFormattings(sheet),
            repeatRegions = repeatRegions
        )
    }

    /**
     * 헤더/푸터 정보 추출
     *
     * 주의: XSSFSheet의 getOddHeader(), getEvenHeader() 등의 메서드는
     * 해당 요소가 없을 때 새로 생성하므로, ctWorksheet를 직접 사용하여
     * 부작용 없이 읽기만 수행한다.
     */
    private fun extractHeaderFooter(sheet: Sheet): HeaderFooterSpec? {
        val xssfSheet = sheet as? XSSFSheet ?: return null
        val ctHf = xssfSheet.ctWorksheet?.headerFooter ?: return null

        val oddHeader = ctHf.oddHeader?.takeIf { it.isNotEmpty() }
        val oddFooter = ctHf.oddFooter?.takeIf { it.isNotEmpty() }
        val evenHeader = ctHf.evenHeader?.takeIf { it.isNotEmpty() }
        val evenFooter = ctHf.evenFooter?.takeIf { it.isNotEmpty() }
        val firstHeader = ctHf.firstHeader?.takeIf { it.isNotEmpty() }
        val firstFooter = ctHf.firstFooter?.takeIf { it.isNotEmpty() }

        if (listOf(oddHeader, oddFooter, evenHeader, evenFooter, firstHeader, firstFooter).all { it == null }) {
            return null
        }

        // 헤더/푸터 문자열에서 L/C/R 섹션 추출 헬퍼
        fun String?.section(s: Char) = parseHeaderFooterSection(this, s)

        return HeaderFooterSpec(
            leftHeader = oddHeader.section('L'),
            centerHeader = oddHeader.section('C'),
            rightHeader = oddHeader.section('R'),
            leftFooter = oddFooter.section('L'),
            centerFooter = oddFooter.section('C'),
            rightFooter = oddFooter.section('R'),
            differentFirst = ctHf.differentFirst,
            differentOddEven = ctHf.differentOddEven,
            scaleWithDoc = ctHf.isSetScaleWithDoc && ctHf.scaleWithDoc,
            alignWithMargins = ctHf.isSetAlignWithMargins && ctHf.alignWithMargins,
            firstLeftHeader = firstHeader.section('L'),
            firstCenterHeader = firstHeader.section('C'),
            firstRightHeader = firstHeader.section('R'),
            firstLeftFooter = firstFooter.section('L'),
            firstCenterFooter = firstFooter.section('C'),
            firstRightFooter = firstFooter.section('R'),
            evenLeftHeader = evenHeader.section('L'),
            evenCenterHeader = evenHeader.section('C'),
            evenRightHeader = evenHeader.section('R'),
            evenLeftFooter = evenFooter.section('L'),
            evenCenterFooter = evenFooter.section('C'),
            evenRightFooter = evenFooter.section('R')
        )
    }

    /**
     * 헤더/푸터 문자열에서 특정 섹션(L/C/R) 추출
     * Excel 형식: "&L왼쪽텍스트&C중앙텍스트&R오른쪽텍스트"
     */
    private fun parseHeaderFooterSection(hf: String?, section: Char): String? {
        if (hf.isNullOrEmpty()) return null

        val marker = "&$section"
        val startIdx = hf.indexOf(marker, ignoreCase = true)
        if (startIdx < 0) return null

        val contentStart = startIdx + 2
        if (contentStart >= hf.length) return null

        val nextSectionIdx = listOf('L', 'C', 'R')
            .filter { it != section }
            .mapNotNull { hf.indexOf("&$it", contentStart, ignoreCase = true).takeIf { idx -> idx >= 0 } }
            .minOrNull() ?: hf.length

        return hf.substring(contentStart, nextSectionIdx).takeIf { it.isNotEmpty() }
    }

    /**
     * 인쇄 설정 추출
     */
    private fun extractPrintSetup(sheet: Sheet) =
        sheet.printSetup?.let { ps ->
            PrintSetupSpec(
                paperSize = ps.paperSize,
                landscape = ps.landscape,
                fitWidth = ps.fitWidth,
                fitHeight = ps.fitHeight,
                scale = ps.scale,
                headerMargin = ps.headerMargin,
                footerMargin = ps.footerMargin
            )
        }

    /**
     * 조건부 서식 정보 추출 (SXSSF 모드용)
     *
     * 템플릿의 조건부 서식을 명세에 저장하여 SXSSF 모드에서 복원할 수 있도록 한다.
     */
    private fun extractConditionalFormattings(sheet: Sheet) =
        extractConditionalFormattingsCore(sheet as? XSSFSheet)

    /**
     * 조건부 서식 추출 핵심 로직
     *
     * @param sheet 대상 XSSFSheet (null이면 빈 리스트 반환)
     * @param rangeFilter 범위 필터 (null이면 모든 범위 포함)
     * @param transformRange 범위 변환 함수 (null이면 원본 유지)
     */
    private fun extractConditionalFormattingsCore(
        sheet: XSSFSheet?,
        rangeFilter: ((CellRangeAddress) -> Boolean)? = null,
        transformRange: ((CellRangeAddress) -> CellRangeAddress)? = null
    ): List<ConditionalFormattingSpec> {
        if (sheet == null) return emptyList()

        val scf = sheet.sheetConditionalFormatting
        val count = scf.numConditionalFormattings

        if (count == 0) return emptyList()

        return (0 until count).mapNotNull { i ->
            val cf = scf.getConditionalFormattingAt(i) ?: return@mapNotNull null
            val ranges = cf.formattingRanges.toList()
            if (ranges.isEmpty()) return@mapNotNull null

            // 범위 필터링 적용
            val filteredRanges = rangeFilter?.let { filter -> ranges.filter(filter) } ?: ranges
            if (filteredRanges.isEmpty()) return@mapNotNull null

            val rules = (0 until cf.numberOfRules).mapNotNull { ruleIndex ->
                val rule = cf.getRule(ruleIndex) ?: return@mapNotNull null

                ConditionalFormattingRuleSpec(
                    conditionType = rule.conditionType ?: ConditionType.CELL_VALUE_IS,
                    comparisonOperator = rule.comparisonOperation,
                    formula1 = rule.formula1,
                    formula2 = rule.formula2,
                    dxfId = ConditionalFormattingUtils.extractDxfId(rule),
                    priority = rule.priority,
                    stopIfTrue = rule.stopIfTrue
                )
            }

            if (rules.isEmpty()) return@mapNotNull null

            // 범위 변환 적용
            val finalRanges = transformRange?.let { transform -> filteredRanges.map(transform) } ?: filteredRanges

            ConditionalFormattingSpec(
                ranges = finalRanges,
                rules = rules
            )
        }
    }

    /**
     * 시트 내 repeat 마커를 수집하고, 범위의 대상 시트 정보를 함께 반환
     */
    private fun collectRepeatRegions(workbook: Workbook, sheet: Sheet): List<RepeatRegionWithContext> =
        buildList {
            sheet.forEach { row ->
                row.forEach { cell ->
                    val (text, isFormula) = when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue to false
                        CellType.FORMULA -> cell.cellFormula to true
                        else -> return@forEach
                    }
                    text ?: return@forEach

                    val content = UnifiedMarkerParser.parse(text, isFormula, null)
                    if (content is CellContent.RepeatMarker) {
                        val (sheetRef, _) = extractSheetReference(content.range)
                        add(
                            RepeatRegionWithContext(
                                region = createRepeatRegionSpec(workbook, content),
                                sourceSheetName = sheet.sheetName,
                                targetSheetName = sheetRef ?: sheet.sheetName
                            )
                        )
                    }
                }
            }
        }

    /**
     * 같은 컬렉션과 같은 대상 범위(시트+영역)를 가진 중복 repeat 마커를 감지하여
     * 경고 로그 출력 후 마지막 마커만 유지한다.
     *
     * @return 마커가 위치한 시트 이름 → 중복 제거된 RepeatRegionSpec 리스트
     */
    private fun deduplicateRepeatRegions(
        allRegions: List<RepeatRegionWithContext>
    ): Map<String, List<RepeatRegionSpec>> =
        allRegions.groupBy { Triple(it.region.collection, it.targetSheetName, it.region.area) }
            .values
            .map { duplicates ->
                if (duplicates.size > 1) {
                    val first = duplicates.first()
                    LOG.warn(
                        "같은 컬렉션('${first.region.collection}')과 같은 범위('${first.targetSheetName}'!" +
                            "${first.region.area.start.toCellRefString()}:${first.region.area.end.toCellRefString()})를 가진 " +
                            "repeat 마커가 ${duplicates.size}개 있습니다. 마지막 마커만 사용합니다."
                    )
                }
                duplicates.last()
            }
            .groupBy({ it.sourceSheetName }, { it.region })

    private fun createRepeatRegionSpec(workbook: Workbook, marker: CellContent.RepeatMarker) = RepeatRegionSpec(
        collection = marker.collection,
        variable = marker.variable,
        area = parseRange(workbook, marker.range),
        direction = marker.direction,
        emptyRange = marker.emptyRange?.let { parseEmptyRangeSpec(workbook, it) }
    )

    /**
     * empty 범위 문자열을 EmptyRangeSpec으로 파싱
     */
    private fun parseEmptyRangeSpec(workbook: Workbook, rangeStr: String): EmptyRangeSpec {
        val (sheetName, rangeWithoutSheet) = extractSheetReference(rangeStr)
        val range = parseRange(workbook, rangeWithoutSheet)
        return EmptyRangeSpec(sheetName, range.rowRange, range.colRange)
    }

    /**
     * 범위 문자열 파싱 (시트 참조는 미리 제거된 상태)
     *
     * @param workbook Named Range 조회용 워크북
     * @param range 셀 범위("A6:C8", "$A$6:$C$8"), 단일 셀("H10"), 또는 Named Range("DataRange")
     * @return CellArea
     */
    private fun parseRange(workbook: Workbook, range: String): CellArea {
        // 시트 참조가 포함된 경우 제거 (parseEmptyRangeSpec에서 이미 처리하지만 안전을 위해)
        val (_, rangeWithoutSheet) = extractSheetReference(range)

        // 콜론이 있으면 범위 셀 참조
        if (":" in rangeWithoutSheet) {
            val (startRef, endRef) = rangeWithoutSheet.split(":")
            return CellArea(parseCellRef(startRef), parseCellRef(endRef))
        }

        // 단일 셀 참조 패턴 체크 (A1, $A$1, H10 등)
        val singleCellPattern = Regex("""^\$?[A-Za-z]+\$?\d+$""")
        if (singleCellPattern.matches(rangeWithoutSheet)) {
            return parseCellRef(rangeWithoutSheet).let { CellArea(it, it) }
        }

        // Named Range 조회
        val namedRange = workbook.getName(rangeWithoutSheet)
            ?: throw IllegalArgumentException("Named Range를 찾을 수 없습니다: $rangeWithoutSheet")

        val formula = namedRange.refersToFormula
        // 참조가 깨진 경우(#REF!) 처리
        if (formula.contains("#REF!")) {
            throw IllegalArgumentException("Named Range '$rangeWithoutSheet'의 참조가 유효하지 않습니다: $formula")
        }

        val areaRef = AreaReference(formula, workbook.spreadsheetVersion)
        return CellArea(
            CellCoord(areaRef.firstCell.row, areaRef.firstCell.col.toInt()),
            CellCoord(areaRef.lastCell.row, areaRef.lastCell.col.toInt())
        )
    }

    /**
     * 행별 명세 생성
     */
    private fun buildRowSpecs(sheet: Sheet, repeatRegions: List<RepeatRegionSpec>): List<RowSpec> =
        (0..sheet.lastRowNum).map { rowIndex ->
            val row = sheet.getRow(rowIndex)
            val repeatVars = repeatRegions
                .filter { rowIndex in it.area.rowRange }
                .map { it.variable }.toSet().takeIf { it.isNotEmpty() }
            RowSpec(rowIndex, row?.height, buildCellSpecs(row, repeatVars))
        }

    /**
     * empty 범위의 셀 내용을 미리 읽어 EmptyRangeContent로 반환
     */
    private fun readEmptyRangeContent(
        workbook: Workbook,
        currentSheet: Sheet,
        spec: EmptyRangeSpec
    ): EmptyRangeContent {
        // 시트 결정: sheetName이 null이면 현재 시트, 아니면 해당 이름의 시트
        val targetSheet = spec.sheetName?.let { workbook.getSheet(it) } ?: currentSheet

        val cells = mutableListOf<List<CellSnapshot>>()
        val rowHeights = mutableListOf<Short?>()

        for (rowIdx in spec.rowRange) {
            val row = targetSheet.getRow(rowIdx)
            rowHeights.add(row?.height)

            val rowCells = mutableListOf<CellSnapshot>()
            for (colIdx in spec.colRange) {
                val cell = row?.getCell(colIdx)
                rowCells.add(createCellSnapshot(cell))
            }
            cells.add(rowCells)
        }

        // 병합 영역 수집 (emptyRange 내에 있는 것만, 상대 좌표로 변환)
        val mergedRegions = (0 until targetSheet.numMergedRegions)
            .map { targetSheet.getMergedRegion(it) }
            .filter { region ->
                region.firstRow in spec.rowRange && region.lastRow in spec.rowRange &&
                region.firstColumn in spec.colRange && region.lastColumn in spec.colRange
            }
            .map { region ->
                // 상대 좌표로 변환 (emptyRange 시작 기준)
                CellRangeAddress(
                    region.firstRow - spec.rowRange.start,
                    region.lastRow - spec.rowRange.start,
                    region.firstColumn - spec.colRange.start,
                    region.lastColumn - spec.colRange.start
                )
            }

        // 조건부 서식 수집 (emptyRange 영역과 겹치는 것만, 상대 좌표로 변환)
        val conditionalFormattings = extractConditionalFormattingsForRange(targetSheet, spec)

        return EmptyRangeContent(cells, mergedRegions, rowHeights, conditionalFormattings)
    }

    /**
     * 지정된 범위와 겹치는 조건부 서식을 추출한다 (상대 좌표로 변환)
     */
    private fun extractConditionalFormattingsForRange(
        sheet: Sheet,
        spec: EmptyRangeSpec
    ): List<ConditionalFormattingSpec> {
        val specRange = CellRangeAddress(
            spec.rowRange.start, spec.rowRange.end, spec.colRange.start, spec.colRange.end
        )

        return extractConditionalFormattingsCore(
            sheet = sheet as? XSSFSheet,
            rangeFilter = { range -> rangesOverlap(range, specRange) },
            transformRange = { range ->
                // 상대 좌표로 변환 (emptyRange 시작 기준, 범위 클립)
                CellRangeAddress(
                    maxOf(range.firstRow, spec.rowRange.start) - spec.rowRange.start,
                    minOf(range.lastRow, spec.rowRange.end) - spec.rowRange.start,
                    maxOf(range.firstColumn, spec.colRange.start) - spec.colRange.start,
                    minOf(range.lastColumn, spec.colRange.end) - spec.colRange.start
                )
            }
        )
    }

    /** 두 범위가 겹치는지 확인 */
    private fun rangesOverlap(a: CellRangeAddress, b: CellRangeAddress): Boolean =
        a.firstRow <= b.lastRow && a.lastRow >= b.firstRow &&
        a.firstColumn <= b.lastColumn && a.lastColumn >= b.firstColumn

    /**
     * 셀 스냅샷 생성
     */
    private fun createCellSnapshot(cell: Cell?): CellSnapshot {
        if (cell == null) {
            return CellSnapshot(
                value = null,
                cellType = CellType.BLANK,
                styleIndex = 0,
                formula = null
            )
        }

        val (value, formula) = when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue to null
            CellType.NUMERIC -> cell.numericCellValue to null
            CellType.BOOLEAN -> cell.booleanCellValue to null
            CellType.FORMULA -> null to cell.cellFormula
            CellType.BLANK -> null to null
            else -> null to null
        }

        return CellSnapshot(
            value = value,
            cellType = cell.cellType,
            styleIndex = cell.cellStyle?.index ?: 0,
            formula = formula
        )
    }

    private fun buildCellSpecs(row: Row?, repeatItemVariables: Set<String>?) =
        row?.mapNotNull { cell ->
            CellSpec(
                columnIndex = cell.columnIndex,
                styleIndex = cell.cellStyle?.index ?: 0,
                content = analyzeCellContent(cell, repeatItemVariables)
            )
        } ?: emptyList()

    /**
     * 셀 내용 분석 - UnifiedMarkerParser에 위임
     */
    private fun analyzeCellContent(cell: Cell, repeatItemVariables: Set<String>?) =
        when (cell.cellType) {
            CellType.BLANK -> CellContent.Empty
            CellType.BOOLEAN -> CellContent.StaticBoolean(cell.booleanCellValue)
            CellType.NUMERIC -> CellContent.StaticNumber(cell.numericCellValue)
            CellType.FORMULA -> UnifiedMarkerParser.parse(cell.cellFormula, isFormula = true, repeatItemVariables)
            CellType.STRING -> UnifiedMarkerParser.parse(cell.stringCellValue, isFormula = false, repeatItemVariables)
            else -> CellContent.Empty
        }

    private fun Sheet.maxColumnIndex(): Int =
        maxOfOrNull { row -> row.lastCellNum.toInt() } ?: 0

    // ==================== 셀 마커 중복 제거 ====================

    /**
     * 범위를 취급하는 셀 마커(image 등)의 중복을 제거한다.
     * 같은 중복 키를 가진 마커가 여러 개 있으면 경고 로그를 출력하고 마지막 마커만 유지한다.
     */
    private fun deduplicateCellMarkers(sheets: List<SheetSpec>): List<SheetSpec> {
        val markers = buildList {
            sheets.forEachIndexed { sheetIdx, sheet ->
                sheet.rows.forEach { row ->
                    row.cells.forEach { cell ->
                        cellMarkerDedupKey(cell.content, sheet.sheetName)?.let { (key, desc) ->
                            add(CellMarkerInfo(sheetIdx, row.templateRowIndex, cell.columnIndex, key, desc))
                        }
                    }
                }
            }
        }

        val toRemove = mutableSetOf<Triple<Int, Int, Int>>()

        markers.groupBy { it.dedupKey }
            .filterValues { it.size > 1 }
            .forEach { (_, duplicates) ->
                LOG.warn(
                    "${duplicates.first().description}가 ${duplicates.size}개 있습니다. 마지막 마커만 사용합니다."
                )
                duplicates.dropLast(1).forEach {
                    toRemove.add(Triple(it.sheetIndex, it.templateRowIndex, it.columnIndex))
                }
            }

        if (toRemove.isEmpty()) return sheets

        return sheets.mapIndexed { sheetIdx, sheet ->
            sheet.copy(rows = sheet.rows.map { row ->
                row.copy(cells = row.cells.map { cell ->
                    if (Triple(sheetIdx, row.templateRowIndex, cell.columnIndex) in toRemove) {
                        cell.copy(content = CellContent.Empty)
                    } else cell
                })
            })
        }
    }

    /**
     * 셀 마커에서 중복 키와 설명을 추출한다.
     * null이면 중복 체크 대상이 아니다.
     */
    private fun cellMarkerDedupKey(content: CellContent, sheetName: String): Pair<String, String>? =
        when (content) {
            is CellContent.ImageMarker -> content.position?.let { pos ->
                val (sheetRef, posWithoutSheet) = extractSheetReference(pos)
                val targetSheet = sheetRef ?: sheetName
                val key = "image:${content.imageName}:$targetSheet!$posWithoutSheet:${content.sizeSpec}"
                val desc = "같은 이름('${content.imageName}')과 같은 위치('$targetSheet'!$posWithoutSheet)와 같은 크기를 가진 image 마커"
                key to desc
            }
            // 향후 다른 범위 기반 마커 추가 가능
            else -> null
        }

    // ==================== 내부 데이터 클래스 ====================

    /** repeat 영역 중복 감지용 내부 컨텍스트 */
    private data class RepeatRegionWithContext(
        val region: RepeatRegionSpec,
        val sourceSheetName: String,
        val targetSheetName: String
    )

    /** 셀 마커 중복 감지용 위치 정보 */
    private data class CellMarkerInfo(
        val sheetIndex: Int,
        val templateRowIndex: Int,
        val columnIndex: Int,
        val dedupKey: String,
        val description: String
    )
}
