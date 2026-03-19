package io.github.jogakdal.tbeg.engine.pipeline.processors.zippost

import io.github.jogakdal.tbeg.DocumentMetadata
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * docProps/core.xml과 docProps/app.xml에 메타데이터를 설정한다.
 */
@Suppress("HttpUrlsUsage") // XML 네임스페이스 URI는 식별자이므로 HTTP 유지
internal object MetadataXmlHandler {

    private const val DC_NS = "http://purl.org/dc/elements/1.1/"
    private const val CP_NS = "http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
    private const val DCTERMS_NS = "http://purl.org/dc/terms/"
    private const val EP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"

    private val W3CDTF_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneId.of("UTC"))

    /**
     * core.xml에 메타데이터를 설정한다.
     *
     * @param coreBytes core.xml 바이트 배열
     * @param metadata 문서 메타데이터
     * @return 수정된 core.xml 바이트 배열
     */
    fun processCoreXml(coreBytes: ByteArray, metadata: DocumentMetadata): ByteArray {
        val doc = parseXml(coreBytes)
        val root = doc.documentElement

        metadata.title?.let { setOrCreateElement(doc, root, DC_NS, "dc:title", it) }
        metadata.author?.let { setOrCreateElement(doc, root, DC_NS, "dc:creator", it) }
        metadata.subject?.let { setOrCreateElement(doc, root, DC_NS, "dc:subject", it) }
        metadata.description?.let { setOrCreateElement(doc, root, DC_NS, "dc:description", it) }
        metadata.keywords?.let { setOrCreateElement(doc, root, CP_NS, "cp:keywords", it.joinToString(", ")) }
        metadata.category?.let { setOrCreateElement(doc, root, CP_NS, "cp:category", it) }
        metadata.created?.let {
            val utcString = W3CDTF_FORMATTER.format(it.atZone(ZoneId.systemDefault()).toInstant())
            val elem = setOrCreateElement(doc, root, DCTERMS_NS, "dcterms:created", utcString)
            elem.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "dcterms:W3CDTF")
        }

        return serializeXml(doc)
    }

    /**
     * app.xml에 메타데이터를 설정한다.
     *
     * @param appBytes app.xml 바이트 배열
     * @param metadata 문서 메타데이터
     * @return 수정된 app.xml 바이트 배열
     */
    fun processAppXml(appBytes: ByteArray, metadata: DocumentMetadata): ByteArray {
        val doc = parseXml(appBytes)
        val root = doc.documentElement

        metadata.company?.let { setOrCreateElement(doc, root, EP_NS, "Company", it) }
        metadata.manager?.let { setOrCreateElement(doc, root, EP_NS, "Manager", it) }

        return serializeXml(doc)
    }

    private fun setOrCreateElement(
        doc: Document,
        parent: Element,
        namespaceURI: String,
        qualifiedName: String,
        value: String
    ): Element {
        val localName = qualifiedName.substringAfter(":")
            .takeIf { qualifiedName.contains(":") }
            ?: qualifiedName

        // 기존 요소 찾기
        val existing = findElement(parent, namespaceURI, localName)
        if (existing != null) {
            existing.textContent = value
            return existing
        }

        // 새 요소 생성
        val newElem = doc.createElementNS(namespaceURI, qualifiedName)
        newElem.textContent = value
        parent.appendChild(newElem)
        return newElem
    }

    private fun findElement(parent: Element, namespaceURI: String, localName: String): Element? =
        parent.getElementsByTagNameNS(namespaceURI, localName)
            .takeIf { it.length > 0 }
            ?.item(0) as? Element

    private fun parseXml(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun serializeXml(doc: Document): ByteArray =
        ByteArrayOutputStream().also { out ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(javax.xml.transform.OutputKeys.STANDALONE, "yes")
            }.transform(DOMSource(doc), StreamResult(out))
        }.toByteArray()
}
