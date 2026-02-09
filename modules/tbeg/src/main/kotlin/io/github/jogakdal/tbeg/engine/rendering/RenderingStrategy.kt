package io.github.jogakdal.tbeg.engine.rendering

/**
 * 템플릿 렌더링 전략 인터페이스.
 *
 * XSSF(비스트리밍)와 SXSSF(스트리밍) 모드를 추상화하여
 * 동일한 인터페이스로 템플릿 렌더링을 수행한다.
 *
 * ## 구현체
 * - [XssfRenderingStrategy]: XSSF 기반 비스트리밍 렌더링
 * - [SxssfRenderingStrategy]: SXSSF 기반 스트리밍 렌더링
 */
internal interface RenderingStrategy {
    /**
     * 전략 이름 (로깅용).
     */
    val name: String

    /**
     * 템플릿에 데이터를 바인딩하여 Excel을 생성한다.
     *
     * @param templateBytes 템플릿 바이트 배열
     * @param data 바인딩할 데이터 맵
     * @param context 렌더링 컨텍스트 (분석기, 프로세서 등)
     * @return 생성된 Excel 바이트 배열
     */
    fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray
}

/**
 * 렌더링에 필요한 컨텍스트 정보.
 *
 * 전략 구현체에서 공통으로 사용하는 유틸리티와 프로세서를 제공한다.
 *
 * @property analyzer 템플릿 분석기
 * @property imageInserter 이미지 삽입기
 * @property repeatExpansionProcessor 반복 영역 확장 프로세서
 * @property sheetLayoutApplier 시트 레이아웃 적용기
 * @property evaluateText 텍스트 내 변수 평가 함수
 * @property resolveFieldPath 객체 필드 경로 해석 함수
 * @property streamingDataSource SXSSF 스트리밍용 데이터 소스 (SXSSF 모드에서만 설정)
 * @property collectionSizes 컬렉션 크기 맵 (위치 계산용, SXSSF 모드에서만 설정)
 */
internal data class RenderingContext(
    val analyzer: TemplateAnalyzer,
    val imageInserter: ImageInserter,
    val repeatExpansionProcessor: RepeatExpansionProcessor,
    val sheetLayoutApplier: SheetLayoutApplier,
    val evaluateText: (String, Map<String, Any>) -> String,
    val resolveFieldPath: (Any?, String) -> Any?,
    val streamingDataSource: StreamingDataSource? = null,
    val collectionSizes: Map<String, Int> = emptyMap()
)
