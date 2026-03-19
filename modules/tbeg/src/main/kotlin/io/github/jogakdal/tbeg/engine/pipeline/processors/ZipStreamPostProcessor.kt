package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.isNullOrEmpty
import io.github.jogakdal.tbeg.engine.core.XmlVariableProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import io.github.jogakdal.tbeg.engine.pipeline.processors.zippost.*
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * ZIP 스트리밍 기반 통합 후처리 프로세서.
 *
 * 기존의 XssfPostProcessor, XmlVariableReplaceProcessor, removeAbsPath()를
 * 한 번의 ZIP iteration으로 통합한다.
 *
 * XSSFWorkbook/OPCPackage 전체 로드를 제거하여 대용량 파일 처리 한계를 해소한다.
 *
 * 처리 순서:
 * 1. Phase 1 (Pre-scan): styles.xml만 추출하여 DOM 파싱, 스타일 변형 추가 및 매핑 구축
 * 2. Phase 2 (Main pass): 전체 ZIP 순회하며 각 엔트리별 처리
 */
internal class ZipStreamPostProcessor(
    private val xmlVariableProcessor: XmlVariableProcessor
) : ExcelProcessor {

    companion object {
        private const val STYLES_XML = "xl/styles.xml"
        private const val WORKBOOK_XML = "xl/workbook.xml"
        private const val CORE_XML = "docProps/core.xml"
        private const val APP_XML = "docProps/app.xml"
        private const val SHEET_XML_PREFIX = "xl/worksheets/sheet"
    }

    override val name = "ZipStreamPost"

    override fun process(context: ProcessingContext): ProcessingContext {
        // 변수 치환 resolver 생성 (ChartRestoreProcessor에서도 사용)
        val variableNames = context.requiredNames?.variables
        val hasVariables = variableNames?.any { context.dataProvider.getValue(it) != null } ?: false

        val variableResolver = if (hasVariables) {
            xmlVariableProcessor.createVariableResolver(context.dataProvider, variableNames)
        } else null

        context.variableResolver = variableResolver

        val needsMetadata = !context.metadata.isNullOrEmpty()

        // Phase 1: styles.xml pre-scan
        val stylesBytes = extractStylesXml(context.resultBytes)
        val (processedStylesBytes, styleMapping) = if (stylesBytes != null) {
            StylesXmlHandler.process(stylesBytes, context.config)
        } else {
            null to emptyMap()
        }

        // Phase 2: ZIP 전체 순회
        context.resultBytes = processZip(
            context, processedStylesBytes, styleMapping, variableResolver, needsMetadata
        )

        return context
    }

    private fun extractStylesXml(zipBytes: ByteArray): ByteArray? {
        ZipArchiveInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                if (entry.name == STYLES_XML) return zis.readAllBytes()
            }
        }
        return null
    }

    private fun processZip(
        context: ProcessingContext,
        processedStylesBytes: ByteArray?,
        styleMapping: Map<Int, StyleVariants>,
        variableResolver: ((String) -> String)?,
        needsMetadata: Boolean
    ): ByteArray {
        val output = ByteArrayOutputStream(context.resultBytes.size)

        ZipArchiveOutputStream(output).use { zos ->
            ZipArchiveInputStream(ByteArrayInputStream(context.resultBytes)).use { zis ->
                generateSequence { zis.nextEntry }.forEach { entry ->
                    val entryBytes = zis.readAllBytes()
                    val processed = processEntry(
                        entry.name, entryBytes,
                        processedStylesBytes, styleMapping,
                        variableResolver, needsMetadata, context
                    )
                    zos.putArchiveEntry(ZipArchiveEntry(entry.name).apply { time = entry.time })
                    zos.write(processed)
                    zos.closeArchiveEntry()
                }
            }
        }

        return output.toByteArray()
    }

    private fun processEntry(
        entryName: String,
        entryBytes: ByteArray,
        processedStylesBytes: ByteArray?,
        styleMapping: Map<Int, StyleVariants>,
        variableResolver: ((String) -> String)?,
        needsMetadata: Boolean,
        context: ProcessingContext
    ): ByteArray = when {
        // styles.xml: Phase 1에서 이미 처리된 결과 사용
        entryName == STYLES_XML ->
            processedStylesBytes ?: entryBytes

        // sheet*.xml: StAX 스트리밍으로 셀 스타일 교체
        entryName.startsWith(SHEET_XML_PREFIX) && entryName.endsWith(".xml") ->
            SheetXmlHandler.process(entryBytes, styleMapping)

        // workbook.xml: AlternateContent 제거 (absPath)
        entryName == WORKBOOK_XML ->
            WorkbookXmlHandler.process(entryBytes)

        // core.xml: 메타데이터 설정
        entryName == CORE_XML && needsMetadata ->
            MetadataXmlHandler.processCoreXml(entryBytes, context.metadata!!)

        // app.xml: 메타데이터 설정
        entryName == APP_XML && needsMetadata &&
            (context.metadata?.company != null || context.metadata?.manager != null) ->
            MetadataXmlHandler.processAppXml(entryBytes, context.metadata)

        // 기타 XML: 변수 치환
        VariableXmlHandler.shouldProcess(entryName) ->
            VariableXmlHandler.process(entryBytes, variableResolver)

        // 나머지: 그대로 통과
        else -> entryBytes
    }
}
