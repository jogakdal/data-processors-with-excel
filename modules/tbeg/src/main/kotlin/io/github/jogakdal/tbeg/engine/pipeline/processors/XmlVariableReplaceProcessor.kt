package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.engine.core.XmlVariableProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext

/**
 * XML 변수 치환 프로세서.
 *
 * Excel 패키지 내 XML 요소의 변수를 치환한다.
 * TemplateRenderingEngine이 처리하지 못하는 요소들을 처리한다:
 *
 * - 차트 타이틀 및 레이블
 * - 도형 텍스트
 * - 머리글/바닥글
 * - 텍스트 상자, SmartArt 등
 *
 * 또한 차트 복원 시 사용할 variableResolver를 생성하여
 * context에 저장한다.
 */
internal class XmlVariableReplaceProcessor(
    private val xmlVariableProcessor: XmlVariableProcessor
) : ExcelProcessor {

    override val name: String = "XmlVariableReplace"

    /**
     * 치환할 변수가 있을 때만 실행
     */
    override fun shouldProcess(context: ProcessingContext): Boolean =
        context.requiredNames?.variables?.any { name ->
            context.dataProvider.getValue(name) != null
        } ?: false

    override fun process(context: ProcessingContext): ProcessingContext {
        val variableNames = context.requiredNames?.variables

        // XML 변수 치환
        context.resultBytes = xmlVariableProcessor.processVariables(
            context.resultBytes,
            context.dataProvider,
            variableNames
        )

        // 차트 복원용 변수 해석기 생성
        context.variableResolver = xmlVariableProcessor.createVariableResolver(
            context.dataProvider,
            variableNames
        )

        return context
    }
}
