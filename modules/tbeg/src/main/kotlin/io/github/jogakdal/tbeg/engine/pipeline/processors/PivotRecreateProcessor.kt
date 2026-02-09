package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.engine.core.PivotTableProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext

/**
 * 피벗 테이블 재생성 프로세서.
 *
 * [PivotExtractProcessor]가 추출한 피벗 테이블 정보를 바탕으로
 * 확장된 데이터 소스 범위에 맞게 피벗 테이블을 재생성한다.
 *
 * 처리 내용:
 * - 확장된 데이터 소스 범위 계산
 * - 피벗 테이블 재생성
 * - 피벗 캐시 업데이트
 * - 원본 스타일 복원
 */
internal class PivotRecreateProcessor(
    private val pivotTableProcessor: PivotTableProcessor
) : ExcelProcessor {

    override val name: String = "PivotRecreate"

    /**
     * 추출된 피벗 테이블 정보가 있을 때만 실행
     */
    override fun shouldProcess(context: ProcessingContext): Boolean =
        context.pivotTableInfos.isNotEmpty()

    override fun process(context: ProcessingContext): ProcessingContext {
        context.resultBytes = pivotTableProcessor.recreate(
            context.resultBytes,
            context.pivotTableInfos
        )
        return context
    }
}
