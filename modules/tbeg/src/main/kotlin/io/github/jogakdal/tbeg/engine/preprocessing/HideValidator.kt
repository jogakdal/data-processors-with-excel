package io.github.jogakdal.tbeg.engine.preprocessing

import io.github.jogakdal.tbeg.HideMode
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerValidationException
import org.apache.poi.ss.util.CellRangeAddress

/**
 * hideable 마커의 bundle 범위를 검증한다.
 */
object HideValidator {

    /**
     * 모든 hideable 영역에 대해 검증을 수행한다.
     *
     * @param regions 검증 대상 hideable 영역 목록
     * @param mergedRegions 시트별 병합 영역 목록 (sheetIndex -> 병합 영역 목록)
     * @param repeatRegions 시트별 repeat 영역 정보 (sheetIndex -> repeat 범위 목록)
     * @param bundleMarkerRegions 시트별 bundle 마커 범위 목록 (sheetIndex -> bundle 범위 목록)
     */
    fun validate(
        regions: List<HideableRegion>,
        mergedRegions: Map<Int, List<CellRangeAddress>>,
        repeatRegions: Map<Int, List<RepeatInfo>>,
        bundleMarkerRegions: Map<Int, List<CellRangeAddress>>
    ) {
        regions.forEach { region ->
            validateMarkerInRepeat(region, repeatRegions[region.sheetIndex] ?: emptyList())
            validateBundleContainsMarker(region)
            validateBundleColumnAlignment(region, mergedRegions[region.sheetIndex] ?: emptyList(),
                repeatRegions[region.sheetIndex] ?: emptyList())
            validateMergedCellIntegrity(region, mergedRegions[region.sheetIndex] ?: emptyList())
            validateNoBundleMarkerOverlap(region, bundleMarkerRegions[region.sheetIndex] ?: emptyList())
        }

        // 영역 간 교차 검증
        validateNoOverlap(regions)
        validateNoMutualInclusion(regions)
    }

    /**
     * hideable 마커가 repeat 범위 안에 있는지 검증한다.
     */
    private fun validateMarkerInRepeat(region: HideableRegion, repeats: List<RepeatInfo>) {
        val markerInRepeat = repeats.any { repeat ->
            repeat.range.containsCell(region.markerCell.firstRow, region.markerCell.firstColumn)
        }
        if (!markerInRepeat) {
            throw MarkerValidationException(
                "hideable 마커(${cellRef(region.markerCell)})가 repeat의 반복 항목 필드 범위에 속하지 않습니다."
            )
        }
    }

    /**
     * bundle 범위가 hideable 마커 셀을 포함하는지 검증한다.
     */
    private fun validateBundleContainsMarker(region: HideableRegion) {
        if (!rangeContains(region.effectiveRange, region.markerCell)) {
            throw MarkerValidationException(
                "hideable 마커(${cellRef(region.markerCell)})가 " +
                "bundle 범위(${rangeRef(region.effectiveRange)})에 포함되어야 합니다."
            )
        }
    }

    /**
     * bundle의 열/행 범위가 hideable 셀(또는 병합 셀)의 범위와 일치하는지 검증한다.
     */
    private fun validateBundleColumnAlignment(
        region: HideableRegion,
        mergedRegions: List<CellRangeAddress>,
        repeats: List<RepeatInfo>
    ) {
        // effectiveRange가 markerCell과 같으면 bundle 미지정 -> 검증 불필요
        if (region.effectiveRange == region.markerCell) return

        val repeat = repeats.find { it.range.containsCell(region.markerCell.firstRow, region.markerCell.firstColumn) }
            ?: return

        // 마커 셀이 포함된 병합 영역 찾기
        val markerMergedRegion = mergedRegions.find { merged ->
            merged.containsCell(region.markerCell.firstRow, region.markerCell.firstColumn)
        }

        // 기준 열/행 범위 결정 (병합 셀이면 병합 범위, 아니면 마커 셀 자체)
        val baseColFirst = markerMergedRegion?.firstColumn ?: region.markerCell.firstColumn
        val baseColLast = markerMergedRegion?.lastColumn ?: region.markerCell.lastColumn
        val baseRowFirst = markerMergedRegion?.firstRow ?: region.markerCell.firstRow
        val baseRowLast = markerMergedRegion?.lastRow ?: region.markerCell.lastRow

        if (repeat.isDown) {
            // DOWN repeat: bundle의 열 범위가 기준과 일치해야 한다
            if (region.effectiveRange.firstColumn != baseColFirst ||
                region.effectiveRange.lastColumn != baseColLast) {
                throw MarkerValidationException(
                    "hideable '${region.fieldPath}'의 bundle 열 범위" +
                    "(${ElementShifter.columnIndexToLetters(region.effectiveRange.firstColumn)}:${ElementShifter.columnIndexToLetters(region.effectiveRange.lastColumn)})가 " +
                    "hideable 셀의 열 범위(${ElementShifter.columnIndexToLetters(baseColFirst)}:${ElementShifter.columnIndexToLetters(baseColLast)})와 일치하지 않습니다."
                )
            }
        } else {
            // RIGHT repeat: bundle의 행 범위가 기준과 일치해야 한다
            if (region.effectiveRange.firstRow != baseRowFirst ||
                region.effectiveRange.lastRow != baseRowLast) {
                throw MarkerValidationException(
                    "hideable '${region.fieldPath}'의 bundle 행 범위" +
                    "(${region.effectiveRange.firstRow + 1}:${region.effectiveRange.lastRow + 1})가 " +
                    "hideable 셀의 행 범위(${baseRowFirst + 1}:${baseRowLast + 1})와 일치하지 않습니다."
                )
            }
        }
    }

    /**
     * bundle 범위가 병합 셀을 부분적으로 포함하지 않는지 검증한다.
     */
    private fun validateMergedCellIntegrity(region: HideableRegion, mergedRegions: List<CellRangeAddress>) {
        mergedRegions.forEach { merged ->
            if (rangesOverlap(region.effectiveRange, merged) &&
                !rangeContains(region.effectiveRange, merged) &&
                !rangeContains(merged, region.effectiveRange)) {
                throw MarkerValidationException(
                    "hideable '${region.fieldPath}'의 bundle 범위(${rangeRef(region.effectiveRange)})가 " +
                    "병합 셀(${rangeRef(merged)})을 부분적으로 포함합니다. " +
                    "bundle을 병합 셀 전체를 포함하도록 확장하거나, 병합을 조정해 주세요."
                )
            }
        }
    }

    /**
     * hideable의 bundle 범위가 bundle() 마커 범위와 부분 겹침이 없는지 검증한다.
     */
    private fun validateNoBundleMarkerOverlap(region: HideableRegion, bundleMarkerRanges: List<CellRangeAddress>) {
        bundleMarkerRanges.forEach { bundleRange ->
            if (rangesOverlap(region.effectiveRange, bundleRange) &&
                !rangeContains(bundleRange, region.effectiveRange) &&
                !rangeContains(region.effectiveRange, bundleRange)) {
                throw MarkerValidationException(
                    "hideable '${region.fieldPath}'의 bundle 범위(${rangeRef(region.effectiveRange)})가 " +
                    "bundle 마커 범위(${rangeRef(bundleRange)})와 부분적으로 겹칩니다."
                )
            }
        }
    }

    /**
     * hideable 영역 간 범위 겹침이 없는지 검증한다.
     *
     * 양쪽 모두 DIM이면 겹침을 허용한다 (idempotent하므로).
     */
    private fun validateNoOverlap(regions: List<HideableRegion>) {
        for (i in regions.indices) {
            for (j in i + 1 until regions.size) {
                val a = regions[i]
                val b = regions[j]
                if (a.sheetIndex != b.sheetIndex) continue
                if (a.mode == HideMode.DIM && b.mode == HideMode.DIM) continue

                if (rangesOverlap(a.effectiveRange, b.effectiveRange)) {
                    throw MarkerValidationException(
                        "hideable '${a.fieldPath}'의 bundle 범위(${rangeRef(a.effectiveRange)})가 " +
                        "hideable '${b.fieldPath}'의 bundle 범위(${rangeRef(b.effectiveRange)})와 겹칩니다."
                    )
                }
            }
        }
    }

    /**
     * 한 hideable의 bundle이 다른 hideable 마커 셀을 포함하지 않는지 검증한다.
     *
     * 양쪽 모두 DIM이면 상호 포함을 허용한다.
     */
    private fun validateNoMutualInclusion(regions: List<HideableRegion>) {
        for (i in regions.indices) {
            for (j in regions.indices) {
                if (i == j) continue
                val a = regions[i]
                val b = regions[j]
                if (a.sheetIndex != b.sheetIndex) continue
                if (a.mode == HideMode.DIM && b.mode == HideMode.DIM) continue

                if (rangeContains(a.effectiveRange, b.markerCell)) {
                    throw MarkerValidationException(
                        "hideable '${a.fieldPath}'의 bundle 범위(${rangeRef(a.effectiveRange)})에 " +
                        "다른 hideable 마커 '${b.fieldPath}'(${cellRef(b.markerCell)})가 포함되어 있습니다."
                    )
                }
            }
        }
    }

    // === 유틸리티 ===

    private fun rangesOverlap(a: CellRangeAddress, b: CellRangeAddress) =
        a.firstRow <= b.lastRow && a.lastRow >= b.firstRow &&
        a.firstColumn <= b.lastColumn && a.lastColumn >= b.firstColumn

    private fun rangeContains(outer: CellRangeAddress, inner: CellRangeAddress) =
        outer.firstRow <= inner.firstRow && outer.lastRow >= inner.lastRow &&
        outer.firstColumn <= inner.firstColumn && outer.lastColumn >= inner.lastColumn

    private fun cellRef(range: CellRangeAddress) =
        "${ElementShifter.columnIndexToLetters(range.firstColumn)}${range.firstRow + 1}"

    private fun rangeRef(range: CellRangeAddress) =
        "${ElementShifter.columnIndexToLetters(range.firstColumn)}${range.firstRow + 1}:" +
        "${ElementShifter.columnIndexToLetters(range.lastColumn)}${range.lastRow + 1}"
}

/**
 * repeat 영역 정보 (검증용 경량 데이터)
 */
data class RepeatInfo(
    val collection: String,
    val variable: String,
    val range: CellRangeAddress,
    val isDown: Boolean,
    val markerRow: Int,
    val markerCol: Int
)
