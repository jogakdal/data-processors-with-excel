package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.internal.commonLogger
import io.github.jogakdal.tbeg.ExcelDataProvider
import java.io.Closeable

/**
 * SXSSF 모드에서 Iterator를 순차적으로 소비하는 데이터 소스.
 *
 * **목적:**
 * - Iterator를 한 번만 순회하면서 데이터를 제공
 * - 전체 컬렉션을 메모리에 올리지 않음
 * - 각 repeat 영역별로 독립적인 Iterator 관리
 *
 * **동작 방식:**
 * - 각 repeat 영역은 RepeatKey로 식별됨 (collectionName, startRow, startCol)
 * - 같은 컬렉션이 다른 repeat 영역에서 사용되면 DataProvider를 재호출하여 새 Iterator 획득
 * - DataProvider.getItems()가 같은 데이터를 다시 제공할 수 있어야 함
 *
 * @param dataProvider 데이터 제공자
 * @param expectedSizes 각 컬렉션의 예상 크기 (DataProvider.getItemCount에서 제공된 값)
 */
internal class StreamingDataSource(
    private val dataProvider: ExcelDataProvider,
    private val expectedSizes: Map<String, Int> = emptyMap()
) : Closeable {
    private val iteratorsByRepeat = mutableMapOf<RepeatKey, Iterator<Any>>()
    private val currentItemByRepeat = mutableMapOf<RepeatKey, Any?>()
    private val exhaustedRepeats = mutableSetOf<RepeatKey>()
    private val warnedCollections = mutableSetOf<String>()
    private val consumedCountByRepeat = mutableMapOf<RepeatKey, Int>()
    private val mismatchWarnedRepeats = mutableSetOf<RepeatKey>()

    companion object {
        private val LOG by commonLogger()
    }

    /**
     * Repeat 영역을 고유하게 식별하는 키
     *
     * @param sheetIndex 시트 인덱스 (다른 시트의 같은 위치 repeat 구분)
     * @param collectionName 컬렉션 이름
     * @param startRow 시작 행
     * @param startCol 시작 열
     */
    data class RepeatKey(
        val sheetIndex: Int,
        val collectionName: String,
        val startRow: Int,
        val startCol: Int
    )

    /** 현재 아이템 반환 (advanceToNextItem() 호출 전이거나 소진 시 null) */
    fun getCurrentItem(repeatKey: RepeatKey) = currentItemByRepeat[repeatKey]

    /**
     * 다음 아이템으로 이동한다.
     *
     * Iterator에서 다음 아이템을 가져와 현재 아이템으로 설정한다.
     * Iterator가 소진되면 null을 반환한다.
     *
     * @return 다음 아이템, 또는 소진 시 null
     */
    fun advanceToNextItem(repeatKey: RepeatKey): Any? {
        // 이미 소진된 repeat은 null 반환
        if (repeatKey in exhaustedRepeats) {
            return null
        }

        val iterator = iteratorsByRepeat.getOrPut(repeatKey) {
            // 같은 컬렉션에 대해 다른 repeat 영역이 이미 존재하면 경고
            val existingIteratorForCollection = iteratorsByRepeat.keys.any {
                it.collectionName == repeatKey.collectionName && it != repeatKey
            }

            if (existingIteratorForCollection && repeatKey.collectionName !in warnedCollections) {
                LOG.warn(
                    "컬렉션 '{}'이 여러 repeat 영역에서 사용되고 있습니다. " +
                        "DataProvider를 재호출하여 새 Iterator를 생성합니다. (repeat 영역: row={}, col={})",
                    repeatKey.collectionName,
                    repeatKey.startRow,
                    repeatKey.startCol
                )
                warnedCollections.add(repeatKey.collectionName)
            }

            consumedCountByRepeat[repeatKey] = 0
            dataProvider.getItems(repeatKey.collectionName) ?: emptyList<Any>().iterator()
        }

        val item = if (iterator.hasNext()) {
            val nextItem = iterator.next()
            consumedCountByRepeat[repeatKey] = (consumedCountByRepeat[repeatKey] ?: 0) + 1
            nextItem
        } else {
            // Iterator 소진 시 count 불일치 검증
            checkCountMismatchOnExhaustion(repeatKey)
            exhaustedRepeats.add(repeatKey)
            iteratorsByRepeat.remove(repeatKey)
            null
        }

        currentItemByRepeat[repeatKey] = item
        return item
    }

    /**
     * Iterator 소진 시 count 불일치를 검증한다.
     */
    private fun checkCountMismatchOnExhaustion(repeatKey: RepeatKey) {
        if (repeatKey in mismatchWarnedRepeats) return

        val expectedCount = expectedSizes[repeatKey.collectionName] ?: return
        val actualCount = consumedCountByRepeat[repeatKey] ?: 0

        if (actualCount < expectedCount) {
            LOG.warn(
                "컬렉션 '{}' count 불일치: 제공된 count={}개, 실제 아이템={}개. " +
                    "Iterator가 예상보다 일찍 소진되어 {}개의 빈 행이 생성됩니다. " +
                    "(repeat 영역: sheet={}, row={}, col={})",
                repeatKey.collectionName,
                expectedCount,
                actualCount,
                expectedCount - actualCount,
                repeatKey.sheetIndex,
                repeatKey.startRow,
                repeatKey.startCol
            )
            mismatchWarnedRepeats.add(repeatKey)
        }
    }

    /**
     * 예상 count에 도달했는지 확인하고, 초과 아이템이 있으면 경고한다.
     * repeat 처리 완료 후 호출해야 한다.
     */
    fun checkRemainingItems(repeatKey: RepeatKey) {
        if (repeatKey in mismatchWarnedRepeats) return

        val expectedCount = expectedSizes[repeatKey.collectionName] ?: return
        val consumedCount = consumedCountByRepeat[repeatKey] ?: 0

        // 이미 소진되었으면 skip (checkCountMismatchOnExhaustion에서 처리됨)
        if (repeatKey in exhaustedRepeats) return

        val iterator = iteratorsByRepeat[repeatKey] ?: return

        // 예상 count만큼 소비했는데 Iterator에 아이템이 남아있으면 경고
        if (consumedCount >= expectedCount && iterator.hasNext()) {
            // 남은 아이템 수 계산 (최대 100개까지만 확인)
            var remainingCount = 0
            while (iterator.hasNext() && remainingCount < 100) {
                iterator.next()
                remainingCount++
            }
            val hasMore = iterator.hasNext()

            LOG.warn(
                "컬렉션 '{}' count 불일치: 제공된 count={}개, 실제 아이템={}개 이상. " +
                    "제공된 count만큼만 처리되고 나머지 아이템은 무시됩니다. " +
                    "(repeat 영역: sheet={}, row={}, col={})",
                repeatKey.collectionName,
                expectedCount,
                consumedCount + remainingCount + (if (hasMore) 1 else 0),
                repeatKey.sheetIndex,
                repeatKey.startRow,
                repeatKey.startCol
            )
            mismatchWarnedRepeats.add(repeatKey)
        }
    }

    override fun close() {
        iteratorsByRepeat.clear()
        currentItemByRepeat.clear()
        exhaustedRepeats.clear()
        warnedCollections.clear()
        consumedCountByRepeat.clear()
        mismatchWarnedRepeats.clear()
    }
}

/**
 * 컬렉션 크기 계산 (count 미제공 시 Iterator 순회, 이후 DataProvider.getItems() 재호출 필요)
 */
internal fun getCollectionSize(dataProvider: ExcelDataProvider, collectionName: String) =
    dataProvider.getItemCount(collectionName)
        ?: dataProvider.getItems(collectionName)?.asSequence()?.count()
        ?: 0
