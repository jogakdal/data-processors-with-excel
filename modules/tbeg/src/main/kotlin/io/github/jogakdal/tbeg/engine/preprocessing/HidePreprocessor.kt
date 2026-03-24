package io.github.jogakdal.tbeg.engine.preprocessing

import io.github.jogakdal.tbeg.internal.commonLogger
import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.HideMode
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.UnmarkedHidePolicy
import io.github.jogakdal.tbeg.engine.rendering.CellContent
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerValidationException
import io.github.jogakdal.tbeg.engine.rendering.parser.UnifiedMarkerParser
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Hide 전처리기 (1st Pass).
 *
 * hideFields에 지정된 필드에 대응하는 hideable 마커의 bundle 범위를 삭제하고,
 * 나머지 요소를 당기며 위치/범위를 조정한 중간 템플릿을 생성한다.
 *
 * 숨기지 않을 hideable 마커는 일반 아이템 필드 마커(`${item.field}`)로 변환한다.
 */
class HidePreprocessor(
    private val config: TbegConfig
) {
    companion object {
        private val LOG by commonLogger()

        // DIM 스타일 색상 (RGB)
        private val DIM_BACKGROUND_RGB = byteArrayOf(0xD9.toByte(), 0xD9.toByte(), 0xD9.toByte())  // #D9D9D9
        private val DIM_FONT_RGB = byteArrayOf(0xBF.toByte(), 0xBF.toByte(), 0xBF.toByte())        // #BFBFBF
    }

    /**
     * 템플릿에서 hide 대상 영역을 제거한 중간 템플릿을 생성한다.
     *
     * @param templateBytes 원본 템플릿 바이트
     * @param dataProvider 데이터 제공자 (hideFields 정보 포함)
     * @return 전처리된 중간 템플릿 바이트
     */
    fun preprocess(templateBytes: ByteArray, dataProvider: ExcelDataProvider): ByteArray {
        XSSFWorkbook(ByteArrayInputStream(templateBytes)).use { workbook ->
            // 1. 모든 시트에서 hideable 마커, repeat, bundle 마커 수집
            val scanResult = scanAllSheets(workbook)

            // 얼리 리턴: hideable 마커도 없고 repeat도 없으면 전처리 불필요
            if (scanResult.hideableMarkers.isEmpty() && scanResult.repeatInfos.isEmpty()) {
                return templateBytes
            }

            // 2. hideFields와 매칭하여 숨길 대상 결정
            val hideTargets = resolveHideTargets(scanResult, dataProvider)

            if (hideTargets.isEmpty()) {
                // hideable 마커만 있고 hideFields 미지정 -> 경고 로깅 + 마커 변환만 수행
                logUnusedHideableMarkers(scanResult)
                convertRemainingHideables(workbook, scanResult.hideableMarkers)
                return writeWorkbook(workbook)
            }

            // 3. 검증
            HideValidator.validate(
                hideTargets,
                scanResult.mergedRegions,
                scanResult.repeatInfos,
                scanResult.bundleMarkerRanges
            )

            // 4. 시트별로 hide 처리
            workbook.forEachIndexed { sheetIndex, sheet ->
                val sheetTargets = hideTargets.filter { it.sheetIndex == sheetIndex }
                if (sheetTargets.isEmpty()) return@forEachIndexed

                val repeats = scanResult.repeatInfos[sheetIndex] ?: emptyList()
                val bundles = scanResult.bundleMarkerRanges[sheetIndex] ?: emptyList()

                // DIM 먼저 처리 (구조 변경 없음 -- 스타일 적용 + 데이터 영역만 값 제거)
                val dimTargets = sheetTargets.filter { it.mode == HideMode.DIM }
                if (dimTargets.isNotEmpty()) {
                    processDimHide(workbook, sheet, dimTargets, repeats)
                }

                // DELETE 후속 처리 (물리적 삭제 + 시프트)
                val deleteTargets = sheetTargets.filter { it.mode == HideMode.DELETE }
                if (deleteTargets.isNotEmpty()) {
                    val targetsByRepeat = deleteTargets.groupBy { target ->
                        repeats.find { it.range.containsCell(target.markerCell.firstRow, target.markerCell.firstColumn) }
                    }

                    // repeat이 null인 경우: unmarkedHidePolicy=WARN_AND_HIDE로 hideable 마커 없이
                    // 숨기는 필드. null은 isDown != false -> true로 평가되어 DOWN으로 처리된다.
                    targetsByRepeat.forEach { (repeat, targets) ->
                        if (repeat?.isDown != false) {
                            processDownRepeatHide(sheet, repeat, targets, bundles)
                        } else {
                            processRightRepeatHide(sheet, targets)
                        }
                    }
                }
            }

            // 5. 숨기지 않는 hideable 마커를 일반 아이템 필드로 변환
            //    DELETE 타겟에 의해 삭제된 열/행에 따라 남은 마커의 위치를 조정한다.
            //    DIM은 시프트를 발생시키지 않으므로 위치 조정에 반영하지 않는다.
            val remainingHideables = scanResult.hideableMarkers.filter { marker ->
                hideTargets.none { it.sheetIndex == marker.sheetIndex &&
                    it.fieldPath == marker.content.fieldPath && it.itemVariable == marker.content.itemVariable }
            }.map { marker ->
                val repeats = scanResult.repeatInfos[marker.sheetIndex] ?: emptyList()
                val deleteSheetTargets = hideTargets.filter {
                    it.sheetIndex == marker.sheetIndex && it.mode == HideMode.DELETE
                }

                var adjustedCol = marker.col
                var adjustedRow = marker.row

                deleteSheetTargets.forEach { target ->
                    val repeat = repeats.find { it.range.containsCell(target.markerCell.firstRow, target.markerCell.firstColumn) }
                    if (repeat?.isDown != false) {
                        // DOWN repeat: 열 삭제 -> 삭제된 열이 마커보다 왼쪽이면 조정
                        if (target.effectiveRange.lastColumn < marker.col) {
                            adjustedCol -= target.effectiveRange.lastColumn - target.effectiveRange.firstColumn + 1
                        }
                    } else {
                        // RIGHT repeat: 행 삭제 -> 삭제된 행이 마커보다 위이면 조정
                        if (target.effectiveRange.lastRow < marker.row) {
                            adjustedRow -= target.effectiveRange.lastRow - target.effectiveRange.firstRow + 1
                        }
                    }
                }

                marker.copy(col = adjustedCol, row = adjustedRow)
            }
            convertRemainingHideables(workbook, remainingHideables)

            return writeWorkbook(workbook)
        }
    }

    /**
     * 모든 시트를 스캔하여 hideable, repeat, bundle 마커와 병합 영역을 수집한다.
     *
     * 2-phase 스캔:
     * 1st phase: repeat 마커와 hideable/bundle 마커를 수집 (repeat 변수명 파악)
     * 2nd phase: repeat 변수명을 기반으로 ItemField를 정확히 식별
     */
    private fun scanAllSheets(workbook: XSSFWorkbook): ScanResult {
        val hideableMarkers = mutableListOf<HideableMarkerInfo>()
        val repeatInfos = mutableMapOf<Int, MutableList<RepeatInfo>>()
        val bundleMarkerRanges = mutableMapOf<Int, MutableList<CellRangeAddress>>()
        val mergedRegions = mutableMapOf<Int, List<CellRangeAddress>>()

        // 셀 텍스트 캐시 (2nd phase에서 재사용)
        data class CellTextInfo(val sheetIndex: Int, val row: Int, val col: Int, val text: String, val isFormula: Boolean)
        val allCellTexts = mutableListOf<CellTextInfo>()

        // 1st phase: repeat/hideable/bundle 마커 수집
        workbook.forEachIndexed { sheetIndex, sheet ->
            mergedRegions[sheetIndex] = (0 until sheet.numMergedRegions).map { sheet.getMergedRegion(it) }
            val sheetMergedRegions = mergedRegions[sheetIndex]!!

            sheet.forEach { row ->
                row.forEach { cell ->
                    val isFormula = cell.cellType == CellType.FORMULA
                    val text = when (cell.cellType) {
                        CellType.STRING -> cell.stringCellValue
                        CellType.FORMULA -> cell.cellFormula
                        else -> null
                    } ?: return@forEach

                    allCellTexts += CellTextInfo(sheetIndex, cell.rowIndex, cell.columnIndex, text, isFormula)
                    when (val content = UnifiedMarkerParser.parse(text, isFormula)) {
                        is CellContent.HideableField -> {
                            val markerCellRange = findMergedRegionOrSingle(
                                sheetMergedRegions, cell.rowIndex, cell.columnIndex
                            )
                            hideableMarkers += HideableMarkerInfo(
                                sheetIndex = sheetIndex,
                                row = cell.rowIndex,
                                col = cell.columnIndex,
                                content = content,
                                markerCellRange = markerCellRange
                            )
                        }
                        is CellContent.RepeatMarker -> {
                            val range = parseRange(content.range)
                            repeatInfos.getOrPut(sheetIndex) { mutableListOf() } += RepeatInfo(
                                collection = content.collection,
                                variable = content.variable,
                                range = range,
                                isDown = content.direction == RepeatDirection.DOWN,
                                markerRow = cell.rowIndex,
                                markerCol = cell.columnIndex
                            )
                        }
                        is CellContent.BundleMarker -> {
                            bundleMarkerRanges.getOrPut(sheetIndex) { mutableListOf() } +=
                                parseRange(content.range)
                        }
                        else -> {}
                    }
                }
            }
        }

        // 2nd phase: repeat 변수명을 기반으로 ItemField 식별
        val repeatVariables = repeatInfos.values.flatten().map { it.variable }.toSet()
        val itemFieldCells = mutableListOf<ItemFieldCellInfo>()

        allCellTexts.forEach { cellText ->
            val content = UnifiedMarkerParser.parse(cellText.text, cellText.isFormula, repeatVariables)
            if (content is CellContent.ItemField) {
                itemFieldCells += ItemFieldCellInfo(
                    sheetIndex = cellText.sheetIndex,
                    row = cellText.row,
                    col = cellText.col,
                    content = content
                )
            }
        }

        return ScanResult(hideableMarkers, repeatInfos, bundleMarkerRanges, mergedRegions, itemFieldCells)
    }

    /**
     * hideFields와 매칭하여 실제 숨길 HideableRegion 목록을 결정한다.
     */
    private fun resolveHideTargets(
        scanResult: ScanResult,
        dataProvider: ExcelDataProvider
    ): List<HideableRegion> {
        val result = mutableListOf<HideableRegion>()

        // repeat별로 hideFields 수집
        val hiddenFieldsByVariable = mutableMapOf<String, Set<String>>()
        scanResult.repeatInfos.values.flatten().forEach { repeat ->
            val hidden = dataProvider.getHiddenFields(repeat.collection)
            if (hidden.isNotEmpty()) {
                hiddenFieldsByVariable[repeat.variable] = hidden
            }
        }

        if (hiddenFieldsByVariable.isEmpty()) return emptyList()

        // hideable 마커 매칭
        scanResult.hideableMarkers.forEach { marker ->
            val hiddenFields = hiddenFieldsByVariable[marker.content.itemVariable] ?: return@forEach
            if (marker.content.fieldPath !in hiddenFields) return@forEach

            val effectiveRange = if (marker.content.bundleRange != null) {
                parseRange(marker.content.bundleRange)
            } else {
                marker.markerCellRange
            }

            result += HideableRegion(
                sheetIndex = marker.sheetIndex,
                fieldPath = marker.content.fieldPath,
                itemVariable = marker.content.itemVariable,
                markerCell = marker.markerCellRange,
                effectiveRange = effectiveRange,
                mode = marker.content.mode
            )
        }

        // hideable 없이 hideFields에 지정된 일반 ItemField 처리
        scanResult.itemFieldCells.forEach { itemField ->
            val hiddenFields = hiddenFieldsByVariable[itemField.content.itemVariable] ?: return@forEach
            if (itemField.content.fieldPath !in hiddenFields) return@forEach

            // 이미 hideable 마커로 처리된 필드는 건너뛰기
            val alreadyHandled = result.any {
                it.sheetIndex == itemField.sheetIndex &&
                it.itemVariable == itemField.content.itemVariable &&
                it.fieldPath == itemField.content.fieldPath
            }
            if (alreadyHandled) return@forEach

            when (config.unmarkedHidePolicy) {
                UnmarkedHidePolicy.WARN_AND_HIDE -> {
                    LOG.warn(
                        "필드 '{}.{}'가 hideFields에 지정되었지만 hideable 마커가 없습니다. " +
                        "해당 셀을 DIM 모드로 숨깁니다. hideable 마커를 사용하면 bundle 범위와 숨김 모드를 지정할 수 있습니다.",
                        itemField.content.itemVariable, itemField.content.fieldPath
                    )
                    val mergedRegions = scanResult.mergedRegions[itemField.sheetIndex] ?: emptyList()
                    val cellRange = findMergedRegionOrSingle(mergedRegions, itemField.row, itemField.col)
                    result += HideableRegion(
                        sheetIndex = itemField.sheetIndex,
                        fieldPath = itemField.content.fieldPath,
                        itemVariable = itemField.content.itemVariable,
                        markerCell = cellRange,
                        effectiveRange = cellRange,
                        mode = HideMode.DIM
                    )
                }
                UnmarkedHidePolicy.ERROR -> {
                    throw MarkerValidationException(
                        "필드 '${itemField.content.itemVariable}.${itemField.content.fieldPath}'가 " +
                        "hideFields에 지정되었지만 hideable 마커가 없습니다. " +
                        "hideable 마커를 추가하거나 unmarkedHidePolicy를 WARN_AND_HIDE로 변경해 주세요."
                    )
                }
            }
        }

        return result
    }

    /**
     * DOWN repeat의 hide 대상을 처리한다.
     *
     * 영향 행 범위를 repeat/bundle/hide target의 합집합으로 산출하고,
     * 해당 행 범위 내에서 열 이동을 수행한다.
     * 열 경계는 repeat 범위와 hide target의 합집합으로, 그 바깥의 셀은 이동하지 않는다.
     */
    private fun processDownRepeatHide(
        sheet: Sheet,
        repeat: RepeatInfo?,
        targets: List<HideableRegion>,
        bundles: List<CellRangeAddress>
    ) {
        // 행 범위: repeat/bundle/target의 합집합
        val affectedRowStart = minOf(
            repeat?.range?.firstRow ?: Int.MAX_VALUE,
            repeat?.markerRow ?: Int.MAX_VALUE,
            bundles.minOfOrNull { it.firstRow } ?: Int.MAX_VALUE,
            targets.minOf { it.effectiveRange.firstRow }
        )
        val affectedRowEnd = maxOf(
            repeat?.range?.lastRow ?: 0,
            repeat?.markerRow ?: 0,
            bundles.maxOfOrNull { it.lastRow } ?: 0,
            targets.maxOf { it.effectiveRange.lastRow }
        )

        // 열 경계: repeat 범위와 target의 합집합 (외부 영역은 이동하지 않는다)
        val affectedColEnd = maxOf(
            repeat?.range?.lastColumn ?: 0,
            targets.maxOf { it.effectiveRange.lastColumn }
        )

        // 열 인덱스 역순으로 처리
        targets.sortedByDescending { it.effectiveRange.firstColumn }.forEach { target ->
            ElementShifter.shiftColumnsLeft(
                sheet,
                target.effectiveRange.firstColumn, target.effectiveRange.lastColumn,
                affectedRowStart, affectedRowEnd, affectedColEnd
            )
        }
    }

    /**
     * DIM 모드로 숨길 영역을 처리한다.
     *
     * - repeat 데이터 영역: 비활성화 스타일(회색 배경 + 연한 글자색)을 적용하고 값을 제거한다.
     * - bundle의 repeat 밖 영역(필드 타이틀 등): 글자색만 연한 색으로 변경하고, 배경과 값은 유지한다.
     */
    private fun processDimHide(
        workbook: XSSFWorkbook,
        sheet: Sheet,
        targets: List<HideableRegion>,
        repeats: List<RepeatInfo>
    ) {
        val dimStyleCache = mutableMapOf<Short, CellStyle>()
        val dimFontOnlyStyleCache = mutableMapOf<Short, CellStyle>()
        targets.forEach { target ->
            val repeat = repeats.find {
                it.range.containsCell(target.markerCell.firstRow, target.markerCell.firstColumn)
            } ?: return@forEach

            val rowRange = target.effectiveRange.firstRow..target.effectiveRange.lastRow
            val colRange = target.effectiveRange.firstColumn..target.effectiveRange.lastColumn
            val repeatRowRange = repeat.range.firstRow..repeat.range.lastRow
            val repeatColRange = repeat.range.firstColumn..repeat.range.lastColumn
            for (rowIdx in rowRange) {
                val row = sheet.getRow(rowIdx) ?: sheet.createRow(rowIdx)
                val inRepeat = rowIdx in repeatRowRange
                for (colIdx in colRange) {
                    if (inRepeat && colIdx !in repeatColRange) continue
                    val cell = row.getCell(colIdx) ?: row.createCell(colIdx)
                    if (inRepeat) {
                        // repeat 데이터 영역: 배경 + 글자색 + 값 제거
                        cell.cellStyle = dimStyleCache.getOrPut(cell.cellStyle.index) {
                            createDimStyle(workbook, cell.cellStyle as XSSFCellStyle)
                        }
                        cell.setBlank()
                    } else {
                        // repeat 밖 bundle 영역: 글자색만 변경
                        cell.cellStyle = dimFontOnlyStyleCache.getOrPut(cell.cellStyle.index) {
                            createDimFontOnlyStyle(workbook, cell.cellStyle as XSSFCellStyle)
                        }
                    }
                }
            }
        }
    }

    /**
     * DIM 스타일을 생성한다 (repeat 데이터 영역용).
     *
     * 원본 스타일을 복제하고 배경색(#D9D9D9) + 글자색(#BFBFBF)을 적용한다.
     */
    private fun createDimStyle(workbook: XSSFWorkbook, sourceStyle: XSSFCellStyle): XSSFCellStyle {
        val sourceFont = sourceStyle.font
        return (workbook.createCellStyle() as XSSFCellStyle).apply {
            cloneStyleFrom(sourceStyle)
            setFillForegroundColor(XSSFColor(DIM_BACKGROUND_RGB))
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(createDimFont(workbook, sourceFont))
        }
    }

    /**
     * DIM 글자색만 적용한 스타일을 생성한다 (repeat 밖 bundle 영역용).
     *
     * 원본 스타일을 복제하고 글자색(#BFBFBF)만 적용한다. 배경색과 값은 유지한다.
     */
    private fun createDimFontOnlyStyle(workbook: XSSFWorkbook, sourceStyle: XSSFCellStyle): XSSFCellStyle {
        val sourceFont = sourceStyle.font
        return (workbook.createCellStyle() as XSSFCellStyle).apply {
            cloneStyleFrom(sourceStyle)
            setFont(createDimFont(workbook, sourceFont))
        }
    }

    private fun createDimFont(workbook: XSSFWorkbook, sourceFont: XSSFFont) =
        (workbook.createFont() as XSSFFont).apply {
            fontName = sourceFont.fontName
            fontHeightInPoints = sourceFont.fontHeightInPoints
            bold = sourceFont.bold
            italic = sourceFont.italic
            strikeout = sourceFont.strikeout
            underline = sourceFont.underline
            typeOffset = sourceFont.typeOffset
            setColor(XSSFColor(DIM_FONT_RGB))
        }

    /**
     * RIGHT repeat의 hide 대상을 처리한다.
     */
    private fun processRightRepeatHide(
        sheet: Sheet,
        targets: List<HideableRegion>
    ) {
        targets.sortedByDescending { it.effectiveRange.firstRow }.forEach { target ->
            ElementShifter.shiftRowsUp(
                sheet, target.effectiveRange.firstRow, target.effectiveRange.lastRow,
                target.effectiveRange.firstColumn, target.effectiveRange.lastColumn
            )
        }
    }

    /**
     * hideable 마커가 있지만 hideFields가 지정되지 않은 경우 경고를 로깅한다.
     */
    private fun logUnusedHideableMarkers(scanResult: ScanResult) {
        if (scanResult.hideableMarkers.isNotEmpty()) {
            val fields = scanResult.hideableMarkers.joinToString(", ") {
                "${it.content.itemVariable}.${it.content.fieldPath}"
            }
            LOG.warn(
                "템플릿에 hideable 마커({})가 있지만 해당 컬렉션에 hideFields가 지정되지 않았습니다. " +
                "일반 필드로 처리합니다.",
                fields
            )
        }
    }

    /**
     * 숨기지 않는 hideable 마커를 일반 아이템 필드 텍스트로 변환한다.
     */
    private fun convertRemainingHideables(workbook: XSSFWorkbook, markers: List<HideableMarkerInfo>) {
        markers.forEach { marker ->
            val sheet = workbook.getSheetAt(marker.sheetIndex)
            val row = sheet.getRow(marker.row) ?: return@forEach
            val cell = row.getCell(marker.col) ?: return@forEach

            // ${item.field} 형태의 텍스트로 변환
            val fieldText = "\${${marker.content.itemVariable}.${marker.content.fieldPath}}"
            if (cell.cellType == CellType.FORMULA) {
                cell.cellFormula = null
            }
            cell.setCellValue(fieldText)
        }
    }

    private fun findMergedRegionOrSingle(
        mergedRegions: List<CellRangeAddress>,
        row: Int,
        col: Int
    ): CellRangeAddress =
        mergedRegions.find { it.isInRange(row, col) }
            ?: CellRangeAddress(row, row, col, col)

    private fun parseRange(rangeStr: String): CellRangeAddress =
        CellRangeAddress.valueOf(rangeStr)

    private fun writeWorkbook(workbook: XSSFWorkbook): ByteArray =
        ByteArrayOutputStream().use { out ->
            workbook.write(out)
            out.toByteArray()
        }

}

/**
 * 시트 스캔 결과
 */
internal data class ScanResult(
    val hideableMarkers: List<HideableMarkerInfo>,
    val repeatInfos: Map<Int, List<RepeatInfo>>,
    val bundleMarkerRanges: Map<Int, List<CellRangeAddress>>,
    val mergedRegions: Map<Int, List<CellRangeAddress>>,
    val itemFieldCells: List<ItemFieldCellInfo>
)

/**
 * hideable 마커 정보 (스캔 시 수집)
 */
internal data class HideableMarkerInfo(
    val sheetIndex: Int,
    val row: Int,
    val col: Int,
    val content: CellContent.HideableField,
    val markerCellRange: CellRangeAddress
)

/**
 * 일반 아이템 필드 셀 정보 (unmarkedHidePolicy 처리용)
 */
internal data class ItemFieldCellInfo(
    val sheetIndex: Int,
    val row: Int,
    val col: Int,
    val content: CellContent.ItemField
)
