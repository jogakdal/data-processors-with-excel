package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.DocumentMetadata
import io.github.jogakdal.tbeg.isNullOrEmpty
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import io.github.jogakdal.tbeg.engine.core.toByteArray
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.util.*

/**
 * 문서 메타데이터 적용 프로세서.
 *
 * Excel 파일의 문서 속성(제목, 작성자, 키워드 등)을 설정한다.
 * Excel에서 "파일 > 정보 > 속성"에서 확인할 수 있다.
 */
internal class MetadataProcessor : ExcelProcessor {
    override val name: String = "Metadata"

    /**
     * 메타데이터가 있을 때만 실행
     */
    override fun shouldProcess(context: ProcessingContext): Boolean =
        !context.metadata.isNullOrEmpty()

    override fun process(context: ProcessingContext): ProcessingContext {
        context.resultBytes = applyMetadata(context.resultBytes, context.metadata)
        return context
    }

    private fun applyMetadata(bytes: ByteArray, metadata: DocumentMetadata?): ByteArray {
        if (metadata.isNullOrEmpty()) return bytes

        return XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val props = workbook.properties
            val coreProps = props.coreProperties
            val extProps = props.extendedProperties.underlyingProperties

            metadata.title?.let { coreProps.title = it }
            metadata.author?.let { coreProps.creator = it }
            metadata.subject?.let { coreProps.setSubjectProperty(it) }
            metadata.keywords?.let { coreProps.keywords = it.joinToString(", ") }
            metadata.description?.let { coreProps.description = it }
            metadata.category?.let { coreProps.category = it }
            metadata.company?.let { extProps.company = it }
            metadata.manager?.let { extProps.manager = it }
            metadata.created?.let {
                coreProps.setCreated(
                    Optional.of(Date.from(it.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                )
            }

            workbook.toByteArray()
        }
    }
}
