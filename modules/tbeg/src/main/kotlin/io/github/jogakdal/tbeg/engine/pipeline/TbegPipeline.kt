package io.github.jogakdal.tbeg.engine.pipeline

/**
 * TBEG 처리 파이프라인.
 *
 * 여러 프로세서를 순차적으로 실행하여 Excel 파일을 처리한다.
 * 각 프로세서는 shouldProcess() 결과에 따라 조건부로 실행된다.
 *
 * ## 사용 예시
 * ```kotlin
 * val pipeline = TbegPipeline(
 *     ChartExtractProcessor(chartProcessor),
 *     PivotExtractProcessor(pivotTableProcessor),
 *     TemplateRenderProcessor(),
 *     ZipStreamPostProcessor(xmlVariableProcessor),
 *     PivotRecreateProcessor(pivotTableProcessor),
 *     ChartRestoreProcessor(chartProcessor)
 * )
 *
 * val context = ProcessingContext(templateBytes, dataProvider, config, metadata)
 * val result = pipeline.execute(context)
 * output.write(result.resultBytes)
 * ```
 *
 * @property processors 실행할 프로세서 목록 (순서대로 실행)
 */
internal class TbegPipeline(private val processors: List<ExcelProcessor>) {

    /**
     * 프로세서들을 가변 인자로 받는 생성자.
     */
    constructor(vararg processors: ExcelProcessor) : this(processors.toList())

    /**
     * 파이프라인을 실행한다.
     *
     * 각 프로세서를 순차적으로 실행하며, shouldProcess()가 false를 반환하면 건너뜁니다.
     *
     * @param context 초기 처리 컨텍스트
     * @return 최종 처리된 컨텍스트
     */
    fun execute(context: ProcessingContext): ProcessingContext =
        processors.fold(context) { ctx, processor ->
            if (processor.shouldProcess(ctx)) processor.process(ctx) else ctx
        }

}
