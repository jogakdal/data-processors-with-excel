package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.engine.core.ChartProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext

/**
 * 차트 복원 프로세서.
 *
 * [ChartExtractProcessor]가 추출한 차트를 다시 Excel 파일에 복원한다.
 * 복원 시 차트 내 변수도 치환한다.
 *
 * 처리 내용:
 * - 차트 파일 복원
 * - 차트 관계 파일 복원
 * - 드로잉 파일 병합
 * - 차트 내 변수 치환 (타이틀, 레이블 등)
 */
internal class ChartRestoreProcessor(
    private val chartProcessor: ChartProcessor
) : ExcelProcessor {

    override val name: String = "ChartRestore"

    /**
     * 추출된 차트 정보가 있을 때만 실행
     */
    override fun shouldProcess(context: ProcessingContext): Boolean =
        context.chartInfo != null

    override fun process(context: ProcessingContext): ProcessingContext {
        context.resultBytes = chartProcessor.restore(
            context.resultBytes,
            context.chartInfo,
            context.variableResolver
        )
        return context
    }
}
