package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.CellArea
import io.github.jogakdal.tbeg.engine.core.ColRange
import io.github.jogakdal.tbeg.engine.core.RowRange
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.ConditionType
import org.apache.poi.ss.util.CellRangeAddress

/**
 * 워크북 명세 - 템플릿 분석 결과
 */
data class WorkbookSpec(
    val sheets: List<SheetSpec>
) {
    /**
     * 템플릿에서 필요로 하는 데이터 이름을 추출한다.
     */
    fun extractRequiredNames(): RequiredNames {
        val variables = mutableSetOf<String>()
        val collections = mutableSetOf<String>()
        val images = mutableSetOf<String>()

        sheets.asSequence()
            .flatMap { it.rows.asSequence() }
            .flatMap { it.cells.asSequence() }
            .map { it.content }
            .forEach { content ->
                when (content) {
                    is CellContent.Variable -> variables += content.variableName
                    is CellContent.RepeatMarker -> collections += content.collection
                    is CellContent.ImageMarker -> images += content.imageName
                    is CellContent.FormulaWithVariables -> variables += content.variableNames
                    is CellContent.SizeMarker -> collections += content.collectionName
                    else -> {}
                }
            }

        // repeatRegions의 collection도 추가
        sheets.flatMap { it.repeatRegions }
            .forEach { collections += it.collection }

        return RequiredNames(variables, collections, images)
    }
}

/**
 * 시트 명세
 */
data class SheetSpec(
    val sheetName: String,
    val sheetIndex: Int,
    val rows: List<RowSpec>,
    val mergedRegions: List<CellRangeAddress>,
    val columnWidths: Map<Int, Int>,
    val defaultRowHeight: Short,
    val headerFooter: HeaderFooterSpec? = null,
    val printSetup: PrintSetupSpec? = null,
    val conditionalFormattings: List<ConditionalFormattingSpec> = emptyList(),
    val repeatRegions: List<RepeatRegionSpec> = emptyList()
) {
    /** templateRowIndex로 RowSpec을 빠르게 조회하기 위한 맵 */
    val rowsByTemplateIndex: Map<Int, RowSpec> by lazy {
        rows.associateBy { it.templateRowIndex }
    }
}

/**
 * 헤더/푸터 정보
 */
data class HeaderFooterSpec(
    val leftHeader: String?,
    val centerHeader: String?,
    val rightHeader: String?,
    val leftFooter: String?,
    val centerFooter: String?,
    val rightFooter: String?,
    val differentFirst: Boolean = false,
    val differentOddEven: Boolean = false,
    val scaleWithDoc: Boolean = true,
    val alignWithMargins: Boolean = true,
    // 첫 페이지용 (differentFirst=true일 때)
    val firstLeftHeader: String? = null,
    val firstCenterHeader: String? = null,
    val firstRightHeader: String? = null,
    val firstLeftFooter: String? = null,
    val firstCenterFooter: String? = null,
    val firstRightFooter: String? = null,
    // 짝수 페이지용 (differentOddEven=true일 때)
    val evenLeftHeader: String? = null,
    val evenCenterHeader: String? = null,
    val evenRightHeader: String? = null,
    val evenLeftFooter: String? = null,
    val evenCenterFooter: String? = null,
    val evenRightFooter: String? = null
)

/**
 * 인쇄 설정 정보
 */
data class PrintSetupSpec(
    val paperSize: Short,
    val landscape: Boolean,
    val fitWidth: Short,
    val fitHeight: Short,
    val scale: Short,
    val headerMargin: Double,
    val footerMargin: Double
)

/**
 * 행 명세
 */
data class RowSpec(
    val templateRowIndex: Int,
    val height: Short?,
    val cells: List<CellSpec>
)

/**
 * 셀 명세
 */
data class CellSpec(
    val columnIndex: Int,
    val styleIndex: Short,
    val content: CellContent,
    val columnSpan: Int = 1  // 병합된 경우 열 범위
)

/**
 * 셀 내용 유형
 */
sealed class CellContent {
    /** 빈 셀 */
    data object Empty : CellContent()

    /** 정적 문자열 */
    data class StaticString(val value: String) : CellContent()

    /** 정적 숫자 */
    data class StaticNumber(val value: Double) : CellContent()

    /** 정적 불리언 */
    data class StaticBoolean(val value: Boolean) : CellContent()

    /** 단순 변수 치환 - ${title} */
    data class Variable(val variableName: String, val originalText: String) : CellContent()

    /** 반복 항목 필드 - ${emp.name} */
    data class ItemField(
        val itemVariable: String,
        val fieldPath: String,
        val originalText: String
    ) : CellContent()

    /** 수식 */
    data class Formula(val formula: String) : CellContent()

    /** 변수를 포함하는 수식 - HYPERLINK("${url}", "${text}") */
    data class FormulaWithVariables(
        val formula: String,
        val variableNames: List<String>
    ) : CellContent()

    /**
     * 이미지 마커 - ${image(name)}, ${image(name, position)}, ${image(name, position, size)}
     * @param position null이면 마커 셀 위치 사용
     * @param sizeSpec 크기 명세 (기본값: 셀 크기에 맞춤)
     */
    data class ImageMarker(
        val imageName: String,
        val position: String? = null,
        val sizeSpec: ImageSizeSpec = ImageSizeSpec.FIT_TO_CELL
    ) : CellContent()

    /** 반복 마커 - ${repeat(...)} - 분석 후 제거됨 */
    data class RepeatMarker(
        val collection: String,
        val range: String,
        val variable: String,
        val direction: RepeatDirection = RepeatDirection.DOWN,
        val emptyRange: String? = null  // 컬렉션이 비어있을 때 표시할 범위
    ) : CellContent()

    /**
     * 컬렉션 크기 마커 - ${size(collection)} 또는 =TBEG_SIZE(collection)
     * @param collectionName 컬렉션 이름
     * @param originalText 원본 텍스트 (예: "${size(employees)}명")
     */
    data class SizeMarker(
        val collectionName: String,
        val originalText: String
    ) : CellContent()
}

/**
 * 반복 방향
 */
enum class RepeatDirection {
    DOWN,   // 아래로 확장 (기본값)
    RIGHT   // 오른쪽으로 확장
}

/**
 * 반복 영역 정보
 */
data class RepeatRegionSpec(
    val collection: String,
    val variable: String,
    val area: CellArea,
    val direction: RepeatDirection = RepeatDirection.DOWN,
    val emptyRange: EmptyRangeSpec? = null,           // 컬렉션이 비어있을 때 표시할 범위
    val emptyRangeContent: EmptyRangeContent? = null   // 컬렉션이 비어있을 때 표시할 내용 (미리 읽음)
)

/**
 * 열 그룹 - 열 범위가 겹치는 repeat 영역들의 모음
 *
 * 같은 열 그룹 내의 repeat 영역들은 서로 영향을 주지만,
 * 다른 열 그룹의 repeat 영역들과는 독립적으로 확장된다.
 */
data class ColumnGroup(
    val groupId: Int,
    val colRange: ColRange,
    val repeatRegions: List<RepeatRegionSpec>
) {
    companion object {
        /**
         * repeat 영역들을 열 그룹으로 분류
         */
        fun fromRepeatRegions(regions: List<RepeatRegionSpec>): List<ColumnGroup> {
            if (regions.isEmpty()) return emptyList()

            // DOWN 방향 repeat만 그룹화 대상
            val downRepeats = regions.filter { it.direction == RepeatDirection.DOWN }
            if (downRepeats.isEmpty()) return emptyList()

            // Union-Find로 겹치는 영역들을 그룹화
            val groups = mutableListOf<MutableList<RepeatRegionSpec>>()

            for (region in downRepeats) {
                // 기존 그룹 중 열 범위가 겹치는 그룹 찾기
                val overlappingGroups = groups.filter { group ->
                    group.any { it.area.overlapsColumns(region.area) }
                }

                when (overlappingGroups.size) {
                    0 -> {
                        // 새 그룹 생성
                        groups.add(mutableListOf(region))
                    }
                    1 -> {
                        // 기존 그룹에 추가
                        overlappingGroups[0].add(region)
                    }
                    else -> {
                        // 여러 그룹 병합
                        val merged = mutableListOf(region)
                        overlappingGroups.forEach { merged.addAll(it) }
                        groups.removeAll(overlappingGroups)
                        groups.add(merged)
                    }
                }
            }

            return groups.mapIndexed { index, regionsInGroup ->
                val minCol = regionsInGroup.minOf { it.area.start.col }
                val maxCol = regionsInGroup.maxOf { it.area.end.col }
                ColumnGroup(index, ColRange(minCol, maxCol), regionsInGroup.toList())
            }
        }
    }
}

/**
 * 조건부 서식 정보 (SXSSF 모드용)
 *
 * 템플릿의 조건부 서식을 저장하고, 반복 영역 확장 시 복제에 사용된다.
 */
data class ConditionalFormattingSpec(
    val ranges: List<CellRangeAddress>,
    val rules: List<ConditionalFormattingRuleSpec>
)

/**
 * 조건부 서식 규칙 정보
 */
data class ConditionalFormattingRuleSpec(
    val conditionType: ConditionType,
    val comparisonOperator: Byte,  // POI API는 Byte 사용
    val formula1: String?,
    val formula2: String?,
    val dxfId: Int,       // 차등 서식 ID
    val priority: Int,
    val stopIfTrue: Boolean
)

/**
 * 템플릿에서 필요로 하는 데이터 이름 목록.
 *
 * 템플릿 분석 결과에서 추출되며, DataProvider에게 필요한 데이터만 요청할 때 사용된다.
 */
data class RequiredNames(
    val variables: Set<String>,
    val collections: Set<String>,
    val images: Set<String>
) {
    val isEmpty: Boolean get() = variables.isEmpty() && collections.isEmpty() && images.isEmpty()
}

/**
 * 이미지 크기 명세
 *
 * @property width 너비 (0=셀크기, -1=원본/비율유지, 양수=픽셀)
 * @property height 높이 (0=셀크기, -1=원본/비율유지, 양수=픽셀)
 */
data class ImageSizeSpec(
    val width: Int,
    val height: Int
) {
    companion object {
        /** 셀 크기에 맞춤 */
        val FIT_TO_CELL = ImageSizeSpec(0, 0)
        /** 원본 크기 */
        val ORIGINAL = ImageSizeSpec(-1, -1)
    }
}

// ========== Empty Range 관련 데이터 구조 ==========

/**
 * 빈 컬렉션일 때 표시할 범위 명세
 *
 * @property sheetName 시트 이름 (null이면 같은 시트)
 * @property rowRange 행 범위 (0-based)
 * @property colRange 열 범위 (0-based)
 */
data class EmptyRangeSpec(
    val sheetName: String?,
    val rowRange: RowRange,
    val colRange: ColRange
)

/**
 * 빈 컬렉션일 때 표시할 셀 내용 (미리 읽어둔 스냅샷)
 *
 * @property cells 행/열 순서로 저장된 셀 스냅샷 (행 인덱스 → 열 인덱스 순)
 * @property mergedRegions 병합 영역 목록 (상대 좌표: emptyRange 시작 기준)
 * @property rowHeights 행 높이 목록
 * @property conditionalFormattings 조건부 서식 목록 (상대 좌표: emptyRange 시작 기준)
 */
data class EmptyRangeContent(
    val cells: List<List<CellSnapshot>>,
    val mergedRegions: List<CellRangeAddress>,
    val rowHeights: List<Short?>,
    val conditionalFormattings: List<ConditionalFormattingSpec> = emptyList()
) {
    /** 행 개수 */
    val rowCount: Int get() = cells.size

    /** 열 개수 */
    val colCount: Int get() = cells.firstOrNull()?.size ?: 0

    /** 단일 셀인지 여부 */
    val isSingleCell: Boolean get() = rowCount == 1 && colCount == 1
}

/**
 * 셀 스냅샷 - 셀의 값과 스타일을 저장
 *
 * @property value 셀 값 (String, Double, Boolean, null)
 * @property cellType 셀 타입
 * @property styleIndex 스타일 인덱스
 * @property formula 수식 (수식 셀인 경우)
 */
data class CellSnapshot(
    val value: Any?,
    val cellType: CellType,
    val styleIndex: Short,
    val formula: String?
)

/**
 * CellSnapshot을 CellContent로 변환한다.
 */
fun CellSnapshot.toContent(): CellContent = when (cellType) {
    CellType.STRING -> CellContent.StaticString(value as? String ?: "")
    CellType.NUMERIC -> CellContent.StaticNumber(value as? Double ?: 0.0)
    CellType.BOOLEAN -> CellContent.StaticBoolean(value as? Boolean ?: false)
    CellType.FORMULA -> CellContent.Formula(formula ?: "")
    else -> CellContent.Empty
}

