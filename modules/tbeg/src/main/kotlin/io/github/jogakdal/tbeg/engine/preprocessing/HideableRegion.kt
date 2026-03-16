package io.github.jogakdal.tbeg.engine.preprocessing

import io.github.jogakdal.tbeg.HideMode
import org.apache.poi.ss.util.CellRangeAddress

/**
 * hideable 마커에서 추출된 숨김 대상 영역 정보.
 *
 * @param sheetIndex 시트 인덱스
 * @param fieldPath 필드 경로 (예: "salary")
 * @param itemVariable 아이템 변수명 (예: "emp")
 * @param markerCell hideable 마커가 위치한 셀 좌표
 * @param effectiveRange 실제 숨김 대상 범위 (bundle 또는 마커 셀 자체, 병합 고려)
 * @param mode 숨김 모드 (DELETE: 물리적 삭제, DIM: 비활성화 스타일 적용)
 */
data class HideableRegion(
    val sheetIndex: Int,
    val fieldPath: String,
    val itemVariable: String,
    val markerCell: CellRangeAddress,
    val effectiveRange: CellRangeAddress,
    val mode: HideMode = HideMode.DELETE
)
