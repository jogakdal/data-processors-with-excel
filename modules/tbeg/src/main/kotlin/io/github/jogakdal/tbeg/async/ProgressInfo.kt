package io.github.jogakdal.tbeg.async

/**
 * Excel 생성 작업의 진행률 정보.
 *
 * @property processedRows 현재 처리된 행 수
 * @property totalRows 전체 행 수 (알 수 없는 경우 null)
 */
data class ProgressInfo(
    val processedRows: Int,
    val totalRows: Int? = null
)
