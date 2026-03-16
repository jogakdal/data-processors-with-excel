package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.MissingDataBehavior
import io.github.jogakdal.tbeg.exception.MissingTemplateDataException
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import io.github.jogakdal.tbeg.engine.rendering.RequiredNames
import io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer
import io.github.jogakdal.tbeg.engine.rendering.TemplateRenderingEngine
import io.github.jogakdal.tbeg.internal.commonLogger
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream

/**
 * 템플릿 렌더링 프로세서.
 *
 * TemplateRenderingEngine을 사용하여 템플릿에 데이터를 바인딩한다.
 *
 * - 반복 영역 확장
 * - 변수 치환
 * - 이미지 삽입
 * - 수식 조정
 */
internal class TemplateRenderProcessor : ExcelProcessor {

    companion object {
        private val LOG by commonLogger()
    }

    override val name = "TemplateRender"

    override fun process(context: ProcessingContext): ProcessingContext {
        // 템플릿 분석하여 필요한 데이터 이름 추출
        val blueprint = XSSFWorkbook(ByteArrayInputStream(context.resultBytes)).use { workbook ->
            TemplateAnalyzer().analyzeFromWorkbook(workbook)
        }
        val requiredNames = blueprint.extractRequiredNames()
        context.requiredNames = requiredNames

        // 누락 데이터 검증
        validateMissingData(context, requiredNames)

        // processedRowCount 계산 (필요한 컬렉션만 조회)
        context.processedRowCount = requiredNames.collections.sumOf { name ->
            context.dataProvider.getItems(name)?.asSequence()?.count() ?: 0
        }

        // 템플릿 렌더링
        val engine = TemplateRenderingEngine(
            imageUrlCacheTtlSeconds = context.config.imageUrlCacheTtlSeconds
        )
        context.resultBytes = engine.process(
            ByteArrayInputStream(context.resultBytes),
            context.dataProvider,
            requiredNames
        )

        // 차트 범위 조정을 위한 repeat 확장 정보 전달
        context.repeatExpansionInfos = engine.lastRepeatExpansionInfos

        return context
    }

    /**
     * 템플릿에 필요한 데이터가 DataProvider에 있는지 검증한다.
     */
    private fun validateMissingData(context: ProcessingContext, requiredNames: RequiredNames) {
        val dataProvider = context.dataProvider

        // 실제 제공되는 데이터 확인
        val providedVariables = requiredNames.variables.filter { dataProvider.getValue(it) != null }.toSet()
        val providedCollections = requiredNames.collections.filter { dataProvider.getItems(it) != null }.toSet()
        val providedImages = requiredNames.images.filter { dataProvider.getImage(it) != null }.toSet()

        // 누락된 데이터 계산
        val missingVariables = requiredNames.variables - providedVariables
        val missingCollections = requiredNames.collections - providedCollections
        val missingImages = requiredNames.images - providedImages

        if (missingVariables.isEmpty() && missingCollections.isEmpty() && missingImages.isEmpty()) {
            return
        }

        when (context.config.missingDataBehavior) {
            MissingDataBehavior.WARN -> {
                if (missingVariables.isNotEmpty()) {
                    LOG.warn("Missing required template variables: {}", missingVariables)
                }
                if (missingCollections.isNotEmpty()) {
                    LOG.warn("Missing required template collections: {}", missingCollections)
                }
                if (missingImages.isNotEmpty()) {
                    LOG.warn("Missing required template images: {}", missingImages)
                }
            }
            MissingDataBehavior.THROW -> {
                throw MissingTemplateDataException(missingVariables, missingCollections, missingImages)
            }
        }
    }
}
