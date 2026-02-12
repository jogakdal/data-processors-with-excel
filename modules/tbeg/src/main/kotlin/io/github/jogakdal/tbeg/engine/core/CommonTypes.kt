package io.github.jogakdal.tbeg.engine.core

/**
 * 셀 좌표 (row, col 모두 0-based)
 */
data class CellCoord(val row: Int, val col: Int)

/**
 * 셀 범위 (start, end 모두 0-based, 양 끝 포함)
 */
data class CellArea(val start: CellCoord, val end: CellCoord) {
    constructor(startRow: Int, startCol: Int, endRow: Int, endCol: Int) :
        this(CellCoord(startRow, startCol), CellCoord(endRow, endCol))

    val rowRange get() = RowRange(start.row, end.row)
    val colRange get() = ColRange(start.col, end.col)

    /** 다른 영역과 열 범위가 겹치는지 확인 */
    fun overlapsColumns(other: CellArea) =
        !(end.col < other.start.col || start.col > other.end.col)

    /** 다른 영역과 행 범위가 겹치는지 확인 */
    fun overlapsRows(other: CellArea) =
        !(end.row < other.start.row || start.row > other.end.row)

    /** 다른 영역과 2D 공간(행×열)에서 겹치는지 확인 */
    fun overlaps(other: CellArea) = overlapsRows(other) && overlapsColumns(other)
}

/**
 * 수식 확장 결과
 *
 * @param formula 확장된 수식 문자열
 * @param isSequential 연속 범위 참조 여부 (false면 비연속 셀 나열: B3,B5,B7)
 */
data class FormulaExpansionResult(val formula: String, val isSequential: Boolean)

/**
 * 컬렉션 이름 → 아이템 수 매핑
 *
 * repeat 영역의 위치 계산 및 수식 확장에 사용된다.
 */
@JvmInline
value class CollectionSizes internal constructor(private val sizes: Map<String, Int> = emptyMap()) {
    operator fun get(name: String): Int? = sizes[name]

    companion object {
        val EMPTY = CollectionSizes()
        fun of(vararg pairs: Pair<String, Int>) = CollectionSizes(mapOf(*pairs))
    }
}

/** [CollectionSizes] 빌더 함수 */
fun buildCollectionSizes(block: MutableMap<String, Int>.() -> Unit) =
    CollectionSizes(buildMap(block))

/**
 * 0-based 인덱스 범위 (start..end, 양 끝 포함)
 */
data class IndexRange(val start: Int, val end: Int) : Iterable<Int> {
    /** 범위 내 요소 수 (end - start + 1) */
    val count get() = end - start + 1

    /** 인덱스가 범위에 포함되는지 확인 */
    operator fun contains(index: Int) = index in start..end

    override fun iterator(): Iterator<Int> = (start..end).iterator()
}

/** 행 범위 */
typealias RowRange = IndexRange

/** 열 범위 */
typealias ColRange = IndexRange
