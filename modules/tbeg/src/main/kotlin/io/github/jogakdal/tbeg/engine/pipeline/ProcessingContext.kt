package io.github.jogakdal.tbeg.engine.pipeline

import io.github.jogakdal.tbeg.DocumentMetadata
import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.engine.core.ChartProcessor
import io.github.jogakdal.tbeg.engine.core.PivotTableProcessor
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster
import io.github.jogakdal.tbeg.engine.rendering.RequiredNames

/**
 * Excel 처리 파이프라인의 컨텍스트.
 *
 * 템플릿 처리 과정에서 필요한 모든 정보와 중간 상태를 보관한다.
 * 각 프로세서는 이 컨텍스트를 통해 데이터를 교환한다.
 *
 * @property templateBytes 원본 템플릿 바이트 배열
 * @property dataProvider 데이터 제공자
 * @property config 생성기 설정
 * @property metadata 문서 메타데이터
 */
internal class ProcessingContext(
    val templateBytes: ByteArray,
    val dataProvider: ExcelDataProvider,
    val config: TbegConfig,
    val metadata: DocumentMetadata?
) {
    /**
     * 처리 결과 바이트 배열.
     * 각 프로세서가 처리 후 결과를 이 속성에 저장한다.
     */
    var resultBytes: ByteArray = templateBytes
        internal set

    /**
     * 추출된 차트 정보 (스트리밍 모드에서 사용).
     * ChartExtractProcessor가 설정하고 ChartRestoreProcessor가 사용한다.
     */
    var chartInfo: ChartProcessor.ChartInfo? = null
        internal set

    /**
     * 추출된 피벗 테이블 정보 목록.
     * PivotExtractProcessor가 설정하고 PivotRecreateProcessor가 사용한다.
     */
    var pivotTableInfos: List<PivotTableProcessor.PivotTableInfo> = emptyList()
        internal set

    /**
     * 변수 치환 함수 (차트 복원 등에서 사용).
     * XmlVariableProcessor에서 생성한다.
     */
    var variableResolver: ((String) -> String)? = null
        internal set

    /**
     * 처리된 데이터 행 수.
     * 진행률 보고 및 결과 반환에 사용된다.
     */
    var processedRowCount: Int = 0
        internal set

    /**
     * 템플릿에서 필요로 하는 데이터 이름 목록.
     * TemplateRenderProcessor가 템플릿 분석 후 설정한다.
     */
    var requiredNames: RequiredNames? = null
        internal set

    /**
     * 시트별 repeat 확장 정보 (SXSSF 차트 범위 조정용).
     * Key: 시트 이름, Value: 해당 시트의 repeat 확장 정보 목록
     * RenderingStrategy가 설정하고 ChartRestoreProcessor가 사용한다.
     */
    var repeatExpansionInfos: Map<String, List<ChartRangeAdjuster.RepeatExpansionInfo>> = emptyMap()
        internal set
}
