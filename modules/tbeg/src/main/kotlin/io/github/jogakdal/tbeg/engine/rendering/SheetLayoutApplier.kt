package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.core.ConditionalFormattingUtils
import org.apache.poi.ss.usermodel.ConditionalFormattingRule
import org.apache.poi.ss.usermodel.ConditionType
import org.apache.poi.ss.usermodel.Footer
import org.apache.poi.ss.usermodel.Header
import org.apache.poi.ss.usermodel.SheetConditionalFormatting
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.streaming.SXSSFSheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook

/**
 * 시트 레이아웃 적용을 담당하는 프로세서.
 *
 * SXSSF 모드에서 병합 영역, 조건부 서식, 헤더/푸터, 인쇄 설정 등
 * 시트 레이아웃 관련 설정을 적용한다.
 *
 * 처리 대상:
 * - 병합 영역 (반복 영역 내 복제 포함)
 * - 조건부 서식 (반복 영역 내 복제 포함)
 * - 헤더/푸터 (변수 치환 포함)
 * - 인쇄 설정
 */
internal class SheetLayoutApplier {
    /**
     * 조건부 서식 적용 (SXSSF 모드용)
     *
     * 템플릿의 조건부 서식을 SXSSF 시트에 적용한다.
     * 반복 영역에 있는 조건부 서식은 각 반복 아이템에 대해 복제된다.
     *
     * @param collectionSizes 컬렉션 크기 맵 (스트리밍 모드에서 data에 컬렉션이 없을 때 사용)
     */
    fun applyConditionalFormattings(
        sheet: SXSSFSheet,
        conditionalFormattings: List<ConditionalFormattingSpec>,
        repeatRegions: List<RepeatRegionSpec>,
        data: Map<String, Any>,
        totalRowOffset: Int,
        collectionSizes: CollectionSizes = CollectionSizes.EMPTY
    ) {
        if (conditionalFormattings.isEmpty()) return

        val xssfSheet = (sheet.workbook as SXSSFWorkbook).xssfWorkbook.getSheetAt(sheet.workbook.getSheetIndex(sheet))
        val scf = xssfSheet.sheetConditionalFormatting
        val maxRepeatEndRow = repeatRegions.maxOfOrNull { it.area.end.row } ?: -1

        for (cfInfo in conditionalFormattings) {
            val allRanges = cfInfo.ranges.flatMap { range ->
                expandRangeForConditionalFormatting(
                    range, repeatRegions, data, collectionSizes, totalRowOffset, maxRepeatEndRow
                )
            }

            if (allRanges.isEmpty()) continue

            val rules = cfInfo.rules.mapNotNull { createConditionalFormattingRule(scf, it) }.toTypedArray()
            if (rules.isNotEmpty()) {
                scf.addConditionalFormatting(allRanges.toTypedArray(), rules)
            }
        }
    }

    /** 조건부 서식 범위를 반복 영역에 맞게 확장 */
    private fun expandRangeForConditionalFormatting(
        range: CellRangeAddress,
        repeatRegions: List<RepeatRegionSpec>,
        data: Map<String, Any>,
        collectionSizes: CollectionSizes,
        totalRowOffset: Int,
        maxRepeatEndRow: Int
    ): List<CellRangeAddress> {
        val overlappingRegion = repeatRegions.find { region ->
            range.firstRow in region.area.rowRange && range.lastRow in region.area.rowRange
        } ?: return listOf(calculateOffsetRange(range, totalRowOffset, maxRepeatEndRow))

        val itemCount = (data[overlappingRegion.collection] as? Collection<*>)?.size
            ?: collectionSizes[overlappingRegion.collection]
            ?: return emptyList()

        // 빈 컬렉션인 경우 건너뜀 (emptyRange 조건부 서식이 별도로 적용됨)
        if (itemCount == 0) return emptyList()

        val relativeStartRow = range.firstRow - overlappingRegion.area.start.row
        val rowSpan = range.lastRow - range.firstRow

        return (0 until itemCount).map { itemIdx ->
            (overlappingRegion.area.start.row + (itemIdx * overlappingRegion.area.rowRange.count) + relativeStartRow).let { newFirstRow ->
                CellRangeAddress(newFirstRow, newFirstRow + rowSpan, range.firstColumn, range.lastColumn)
            }
        }
    }

    /** 반복 영역 외부 범위에 오프셋 적용 */
    private fun calculateOffsetRange(range: CellRangeAddress, totalRowOffset: Int, maxRepeatEndRow: Int) =
        (if (range.firstRow > maxRepeatEndRow) totalRowOffset else 0).let { offset ->
            CellRangeAddress(range.firstRow + offset, range.lastRow + offset, range.firstColumn, range.lastColumn)
        }

    /** 조건부 서식 규칙 생성 (dxfId 설정 포함) */
    private fun createConditionalFormattingRule(
        scf: SheetConditionalFormatting,
        ruleInfo: ConditionalFormattingRuleSpec
    ) = runCatching {
        val rule = when (ruleInfo.conditionType) {
            ConditionType.CELL_VALUE_IS -> scf.createConditionalFormattingRule(
                ruleInfo.comparisonOperator, ruleInfo.formula1 ?: "", ruleInfo.formula2
            )
            ConditionType.FORMULA -> scf.createConditionalFormattingRule(ruleInfo.formula1 ?: "TRUE")
            else -> null
        }
        rule?.also { if (ruleInfo.dxfId >= 0) setDxfIdViaReflection(it, ruleInfo.dxfId) }
    }.getOrNull()

    /**
     * 빈 컬렉션의 emptyRange 조건부 서식을 적용한다.
     *
     * repeat 영역이 빈 컬렉션인 경우, repeat 영역의 조건부 서식 대신
     * emptyRange의 조건부 서식을 적용한다.
     */
    fun applyEmptyRangeConditionalFormattings(
        sheet: SXSSFSheet,
        repeatRegions: List<RepeatRegionSpec>,
        collectionSizes: CollectionSizes,
        calculator: PositionCalculator
    ) {
        val xssfSheet = (sheet.workbook as SXSSFWorkbook).xssfWorkbook.getSheetAt(sheet.workbook.getSheetIndex(sheet))
        val scf = xssfSheet.sheetConditionalFormatting

        for (region in repeatRegions) {
            // 빈 컬렉션이 아니면 건너뜀
            val itemCount = collectionSizes[region.collection] ?: continue
            if (itemCount > 0) continue

            // emptyRangeContent가 없거나 조건부 서식이 없으면 건너뜀
            val emptyRangeContent = region.emptyRangeContent ?: continue
            if (emptyRangeContent.conditionalFormattings.isEmpty()) continue

            // repeat 영역의 실제 위치 계산
            val expansion = calculator.getExpansionForRegion(
                region.collection, region.area.start.row, region.area.start.col
            )
            val actualStartRow = expansion?.finalStartRow ?: region.area.start.row

            // emptyRange 조건부 서식 적용
            for (cfSpec in emptyRangeContent.conditionalFormattings) {
                // 상대 좌표를 실제 좌표로 변환
                val actualRanges = cfSpec.ranges.map { range ->
                    CellRangeAddress(
                        range.firstRow + actualStartRow,
                        range.lastRow + actualStartRow,
                        range.firstColumn + region.area.start.col,
                        range.lastColumn + region.area.start.col
                    )
                }.toTypedArray()

                val rules = cfSpec.rules.mapNotNull { createConditionalFormattingRule(scf, it) }.toTypedArray()
                if (rules.isNotEmpty() && actualRanges.isNotEmpty()) {
                    scf.addConditionalFormatting(actualRanges, rules)
                }
            }
        }
    }

    /** dxfId 설정 (ConditionalFormattingUtils 위임) */
    private fun setDxfIdViaReflection(rule: ConditionalFormattingRule, dxfId: Int) =
        ConditionalFormattingUtils.setDxfId(rule, dxfId)

    /**
     * 헤더/푸터 설정 적용 (SXSSF 모드용)
     *
     * 템플릿의 헤더/푸터를 복사하고 변수 치환 적용
     *
     * @param workbook SXSSF 워크북
     * @param sheetIndex 시트 인덱스
     * @param headerFooter 헤더/푸터 정보
     * @param data 변수 치환 데이터
     * @param textEvaluator 텍스트 내 변수를 치환하는 함수
     */
    fun applyHeaderFooter(
        workbook: SXSSFWorkbook,
        sheetIndex: Int,
        headerFooter: HeaderFooterSpec?,
        data: Map<String, Any>,
        textEvaluator: (String, Map<String, Any>) -> String
    ) {
        if (headerFooter == null) return

        // SXSSFWorkbook의 내부 XSSFWorkbook을 통해 XSSFSheet 접근
        val xssfSheet = workbook.xssfWorkbook.getSheetAt(sheetIndex)

        // 홀수 페이지 헤더/푸터 (기본)
        val oddHeaderStr = buildHeaderFooterString(
            headerFooter.leftHeader?.let { textEvaluator(it, data) },
            headerFooter.centerHeader?.let { textEvaluator(it, data) },
            headerFooter.rightHeader?.let { textEvaluator(it, data) }
        )
        val oddFooterStr = buildHeaderFooterString(
            headerFooter.leftFooter?.let { textEvaluator(it, data) },
            headerFooter.centerFooter?.let { textEvaluator(it, data) },
            headerFooter.rightFooter?.let { textEvaluator(it, data) }
        )

        if (oddHeaderStr != null || oddFooterStr != null) {
            oddHeaderStr?.let { xssfSheet.oddHeader.apply { left = ""; center = ""; right = "" } }
            applyHeaderFooterParts(
                xssfSheet.oddHeader, xssfSheet.oddFooter, data, textEvaluator,
                headerFooter.leftHeader, headerFooter.centerHeader, headerFooter.rightHeader,
                headerFooter.leftFooter, headerFooter.centerFooter, headerFooter.rightFooter
            )
        }

        // 첫 페이지용 (differentFirst=true일 때)
        if (headerFooter.differentFirst) {
            applyHeaderFooterParts(
                xssfSheet.firstHeader, xssfSheet.firstFooter, data, textEvaluator,
                headerFooter.firstLeftHeader, headerFooter.firstCenterHeader, headerFooter.firstRightHeader,
                headerFooter.firstLeftFooter, headerFooter.firstCenterFooter, headerFooter.firstRightFooter
            )
        }

        // 짝수 페이지용 (differentOddEven=true일 때)
        if (headerFooter.differentOddEven) {
            applyHeaderFooterParts(
                xssfSheet.evenHeader, xssfSheet.evenFooter, data, textEvaluator,
                headerFooter.evenLeftHeader, headerFooter.evenCenterHeader, headerFooter.evenRightHeader,
                headerFooter.evenLeftFooter, headerFooter.evenCenterFooter, headerFooter.evenRightFooter
            )
        }
    }

    /**
     * 헤더/푸터 부분 적용
     */
    private fun applyHeaderFooterParts(
        header: Header,
        footer: Footer,
        data: Map<String, Any>,
        textEvaluator: (String, Map<String, Any>) -> String,
        leftH: String?, centerH: String?, rightH: String?,
        leftF: String?, centerF: String?, rightF: String?
    ) {
        leftH?.let { header.left = textEvaluator(it, data) }
        centerH?.let { header.center = textEvaluator(it, data) }
        rightH?.let { header.right = textEvaluator(it, data) }
        leftF?.let { footer.left = textEvaluator(it, data) }
        centerF?.let { footer.center = textEvaluator(it, data) }
        rightF?.let { footer.right = textEvaluator(it, data) }
    }

    /**
     * 헤더/푸터 문자열 조합 (Excel 형식: &L...&C...&R...)
     */
    private fun buildHeaderFooterString(left: String?, center: String?, right: String?) =
        listOfNotNull(
            left?.let { "&L$it" },
            center?.let { "&C$it" },
            right?.let { "&R$it" }
        ).takeIf { it.isNotEmpty() }?.joinToString("")

    /**
     * 인쇄 설정 적용 (SXSSF 모드용)
     */
    fun applyPrintSetup(sheet: SXSSFSheet, printSetup: PrintSetupSpec?) = printSetup?.let { setup ->
        sheet.printSetup.apply {
            paperSize = setup.paperSize
            landscape = setup.landscape
            fitWidth = setup.fitWidth
            fitHeight = setup.fitHeight
            scale = setup.scale
            headerMargin = setup.headerMargin
            footerMargin = setup.footerMargin
        }
    }

    // ========== PositionCalculator 연동 메서드 ==========

    /**
     * 병합 영역 적용 (PositionCalculator 사용)
     *
     * PositionCalculator를 사용하여 각 병합 영역의 최종 위치를 계산한다.
     * 반복 영역 내의 병합은 각 아이템마다 복제된다.
     *
     * @param sheet 대상 시트
     * @param mergedRegions 템플릿의 병합 영역 목록
     * @param calculator 위치 계산기
     */
    fun applyMergedRegionsWithCalculator(
        sheet: SXSSFSheet,
        mergedRegions: List<CellRangeAddress>,
        calculator: PositionCalculator
    ) {
        val addedRegions = mutableSetOf<String>()
        val expansions = calculator.getExpansions()

        for (region in mergedRegions) {
            // 이 병합 영역이 속한 반복 영역 찾기
            val containingExpansion = expansions.find { expansion ->
                region.firstRow in expansion.region.area.rowRange &&
                    region.lastRow in expansion.region.area.rowRange &&
                    region.firstColumn in expansion.region.area.colRange &&
                    region.lastColumn in expansion.region.area.colRange
            }

            if (containingExpansion != null) {
                // 반복 영역 내 병합: 각 아이템마다 복제
                val repeatRegion = containingExpansion.region
                val templateRowCount = repeatRegion.area.rowRange.count
                val templateColCount = repeatRegion.area.colRange.count
                val itemCount = containingExpansion.itemCount

                val relativeFirstRow = region.firstRow - repeatRegion.area.start.row
                val relativeLastRow = region.lastRow - repeatRegion.area.start.row
                val relativeFirstCol = region.firstColumn - repeatRegion.area.start.col
                val relativeLastCol = region.lastColumn - repeatRegion.area.start.col

                for (itemIdx in 0 until itemCount) {
                    val (newFirstRow, newFirstCol) = when (repeatRegion.direction) {
                        RepeatDirection.DOWN -> {
                            (containingExpansion.finalStartRow + (itemIdx * templateRowCount) + relativeFirstRow) to
                                (containingExpansion.finalStartCol + relativeFirstCol)
                        }
                        RepeatDirection.RIGHT -> {
                            (containingExpansion.finalStartRow + relativeFirstRow) to
                                (containingExpansion.finalStartCol + (itemIdx * templateColCount) + relativeFirstCol)
                        }
                    }

                    val (newLastRow, newLastCol) = when (repeatRegion.direction) {
                        RepeatDirection.DOWN -> {
                            (containingExpansion.finalStartRow + (itemIdx * templateRowCount) + relativeLastRow) to
                                (containingExpansion.finalStartCol + relativeLastCol)
                        }
                        RepeatDirection.RIGHT -> {
                            (containingExpansion.finalStartRow + relativeLastRow) to
                                (containingExpansion.finalStartCol + (itemIdx * templateColCount) + relativeLastCol)
                        }
                    }

                    val key = "$newFirstRow:$newLastRow:$newFirstCol:$newLastCol"
                    if (key !in addedRegions) {
                        runCatching {
                            sheet.addMergedRegion(CellRangeAddress(newFirstRow, newLastRow, newFirstCol, newLastCol))
                        }
                        addedRegions.add(key)
                    }
                }
            } else {
                // 반복 영역 외부: 최종 위치만 계산
                val final = calculator.getFinalRange(region)

                val key = "${final.firstRow}:${final.lastRow}:${final.firstColumn}:${final.lastColumn}"
                if (key !in addedRegions) {
                    runCatching { sheet.addMergedRegion(final) }
                    addedRegions.add(key)
                }
            }
        }
    }
}
