package io.github.jogakdal.tbeg.engine.core

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.internal.VariableProcessor
import io.github.jogakdal.tbeg.internal.VariableResolverRegistry
import org.apache.poi.openxml4j.opc.OPCPackage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Excel 패키지 내 XML 요소의 변수 치환을 담당하는 프로세서.
 *
 * TemplateRenderingEngine은 POI API를 통해 워크시트 셀을 처리하지만,
 * 차트, 도형, 머리글/바닥글 등의 요소는 POI API로 접근하기 어렵습니다.
 * 이 프로세서는 Excel 패키지의 raw XML을 직접 스캔하여 변수를 치환한다.
 *
 * Excel 파일(.xlsx)은 ZIP 형식의 XML 파일 모음이므로,
 * 모든 XML 파일에서 변수 패턴을 검색하여 치환한다.
 *
 * 처리 대상:
 * - 차트 타이틀 및 레이블 (`xl/charts/\*.xml`)
 * - 도형 텍스트 (`xl/drawings/\*.xml`)
 * - 머리글/바닥글
 * - 텍스트 상자, SmartArt 등 기타 XML 요소
 *
 * 제외 대상:
 * - 관계 파일 (.rels)
 * - 콘텐츠 타입 정의 (`[Content_Types].xml`)
 * - 문서 속성 (/docProps/)
 * - 워크시트 셀 데이터 (TemplateRenderingEngine이 처리)
 *
 * common-core 모듈의 VariableProcessor를 활용하여 변수 치환을 수행한다.
 */
internal class XmlVariableProcessor {
    companion object {
        private val EXCLUDE_PATTERNS = listOf(".rels", "[Content_Types].xml", "/docProps/")

        /** VariableProcessor 구분자: ${변수명} 형태 */
        private val DELIMITERS = VariableProcessor.Delimiters("\${", "}")

        /** VariableProcessor 옵션: 미등록 변수는 원본 유지, 대소문자 구분 */
        private val OPTIONS = VariableProcessor.Options(
            delimiters = DELIMITERS,
            ignoreCase = false,
            ignoreMissing = true
        )
    }

    /**
     * 단순 값 치환용 VariableResolverRegistry 구현.
     * Map<String, String>의 값을 XML 이스케이프 처리하여 반환한다.
     */
    private class XmlValueRegistry(values: Map<String, String>) : VariableResolverRegistry {
        override val resolvers: Map<String, (List<Any?>) -> Any> =
            values.mapValues { (_, v) -> { _: List<Any?> -> v.escapeXml() } }
    }

    /**
     * Excel 패키지 내 XML의 변수를 치환한다.
     *
     * @param inputBytes Excel 파일 바이트 배열
     * @param dataProvider 데이터 제공자
     * @param variableNames 치환할 변수 이름 목록 (null이면 DataProvider의 getAvailableNames() 사용)
     */
    fun processVariables(
        inputBytes: ByteArray,
        dataProvider: ExcelDataProvider,
        variableNames: Set<String>? = null
    ): ByteArray {
        val variableValues = buildVariableValues(dataProvider, variableNames)
            .takeIf { it.isNotEmpty() }
            ?: return inputBytes

        val processor = VariableProcessor(listOf(XmlValueRegistry(variableValues)))

        return OPCPackage.open(ByteArrayInputStream(inputBytes)).use { pkg ->
            val modifiedParts = pkg.parts
                .filter { it.partName.name.endsWith(".xml") && !shouldExclude(it.partName.name) }
                .mapNotNull { part ->
                    val originalXml = part.inputStream.bufferedReader().readText()
                    processor.process(originalXml, OPTIONS)
                        .takeIf { it != originalXml }
                        ?.also { processedXml ->
                            part.outputStream.use { it.write(processedXml.toByteArray(Charsets.UTF_8)) }
                        }
                        ?.let { part.partName.name }
                }

            if (modifiedParts.isNotEmpty()) {
                ByteArrayOutputStream().also { pkg.save(it) }.toByteArray()
            } else {
                inputBytes
            }
        }
    }

    private fun shouldExclude(partName: String) =
        EXCLUDE_PATTERNS.any { it in partName }

    /**
     * 변수 치환 함수 생성
     *
     * @param dataProvider 데이터 제공자
     * @param variableNames 치환할 변수 이름 목록 (null이면 DataProvider의 getAvailableNames() 사용)
     */
    fun createVariableResolver(
        dataProvider: ExcelDataProvider,
        variableNames: Set<String>? = null
    ): ((String) -> String)? {
        val variableValues = buildVariableValues(dataProvider, variableNames)
            .takeIf { it.isNotEmpty() }
            ?: return null

        val processor = VariableProcessor(listOf(XmlValueRegistry(variableValues)))
        return { content: String -> processor.process(content, OPTIONS) }
    }

    private fun buildVariableValues(
        dataProvider: ExcelDataProvider,
        variableNames: Set<String>?
    ): Map<String, String> =
        variableNames
            ?.associateWith { dataProvider.getValue(it)?.toString() }
            ?.filterValues { it != null }
            ?.mapValues { it.value!! }
            ?: emptyMap()
}
