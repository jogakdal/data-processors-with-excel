package io.github.jogakdal.tbeg.engine.rendering

import org.apache.poi.ss.util.CellRangeAddress

/**
 * 자동 셀 병합 추적기.
 *
 * repeat 확장 시 연속된 같은 값의 셀을 추적하고, 병합 영역을 생성한다.
 * 인라인 방식으로 동작하여 스트리밍 렌더링 중에도 사용 가능하다.
 *
 * - DOWN repeat: 열(col)별로 행 방향 병합 추적
 * - RIGHT repeat: 행(row)별로 열 방향 병합 추적
 *
 * @param direction 추적 방향 (repeat의 확장 방향과 동일)
 */
internal class MergeTracker(private val direction: RepeatDirection) {

    /**
     * 현재 진행 중인 병합 그룹
     *
     * @param key DOWN: 열 인덱스, RIGHT: 행 인덱스
     * @param startPos DOWN: 시작 행, RIGHT: 시작 열
     * @param endPos DOWN: 끝 행, RIGHT: 끝 열
     * @param value 병합 기준 값
     */
    private data class TrackingGroup(
        val key: Int,
        val startPos: Int,
        var endPos: Int,
        val value: Any?
    )

    // key별로 현재 진행 중인 그룹 (DOWN: col -> group, RIGHT: row -> group)
    private val currentGroups = mutableMapOf<Int, TrackingGroup>()
    private val finalized = mutableListOf<CellRangeAddress>()

    /**
     * 셀 값을 추적하고 병합 여부를 결정한다.
     *
     * @param col 셀의 열 인덱스
     * @param row 셀의 행 인덱스
     * @param value 셀 값
     * @return true이면 셀에 값을 쓰고, false이면 비워둔다 (병합될 예정)
     */
    fun track(col: Int, row: Int, value: Any?): Boolean {
        // null 값은 병합하지 않음 (빈 셀끼리 병합은 무의미)
        if (value == null) {
            finalizeGroupFor(keyOf(col, row))
            return true
        }

        val key = keyOf(col, row)
        val pos = posOf(col, row)
        val current = currentGroups[key]

        return if (current != null && current.value == value) {
            // 같은 값이 연속 -> 그룹 확장, 셀 비움
            current.endPos = pos
            false
        } else {
            // 다른 값 또는 새 시작 -> 이전 그룹 종료, 새 그룹 시작
            current?.let { finalizeGroup(key, it) }
            currentGroups[key] = TrackingGroup(key, pos, pos, value)
            true
        }
    }

    /**
     * 모든 미종료 그룹을 포함하여 전체 병합 영역을 반환한다.
     * 시트 처리 완료 후 호출한다.
     */
    fun finalizeAll(): List<CellRangeAddress> {
        currentGroups.forEach { (key, group) -> finalizeGroup(key, group) }
        currentGroups.clear()
        return finalized.toList()
    }

    // 방향에 따라 key/pos 결정
    private fun keyOf(col: Int, row: Int) =
        if (direction == RepeatDirection.DOWN) col else row

    private fun posOf(col: Int, row: Int) =
        if (direction == RepeatDirection.DOWN) row else col

    private fun finalizeGroupFor(key: Int) {
        currentGroups.remove(key)?.let { finalizeGroup(key, it) }
    }

    private fun finalizeGroup(key: Int, group: TrackingGroup) {
        // 1셀만이면 병합 불필요
        if (group.startPos == group.endPos) return

        finalized += if (direction == RepeatDirection.DOWN) {
            CellRangeAddress(group.startPos, group.endPos, key, key)
        } else {
            CellRangeAddress(key, key, group.startPos, group.endPos)
        }
    }
}
