package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.engine.core.ChartProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext

/**
 * 차트 추출 프로세서.
 *
 * 스트리밍 렌더링에서 차트가 손실되는 것을 방지하기 위해
 * 템플릿 처리 전에 차트를 추출한다.
 *
 * 추출된 차트 정보는 context.chartInfo에 저장되며,
 * 나중에 [ChartRestoreProcessor]가 복원한다.
 */
internal class ChartExtractProcessor(
    private val chartProcessor: ChartProcessor
) : ExcelProcessor {

    override val name: String = "ChartExtract"

    override fun process(context: ProcessingContext): ProcessingContext {
        val (chartInfo, bytesWithoutChart) = chartProcessor.extractAndRemove(context.resultBytes)
        context.chartInfo = chartInfo
        context.resultBytes = bytesWithoutChart
        return context
    }
}
