package io.github.jogakdal.tbeg.engine.pipeline.processors.zippost

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * workbook.xml에서 mc:AlternateContent 요소를 제거한다.
 *
 * absPath(원본 파일 경로)가 AlternateContent 내부에 포함되어 있으며,
 * 이를 제거하지 않으면 Excel이 파일을 열 때 경로 불일치로 "수정됨" 상태가 된다.
 */
@Suppress("HttpUrlsUsage") // XML 네임스페이스 URI는 식별자이므로 HTTP 유지
internal object WorkbookXmlHandler {
    private const val MC_NAMESPACE = "http://schemas.openxmlformats.org/markup-compatibility/2006"

    /**
     * workbook.xml에서 AlternateContent 요소를 제거한다.
     *
     * @param workbookBytes workbook.xml 바이트 배열
     * @return AlternateContent가 제거된 workbook.xml 바이트 배열
     */
    fun process(workbookBytes: ByteArray): ByteArray {
        val doc = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(ByteArrayInputStream(workbookBytes))

        // mc:AlternateContent 요소 찾기 및 제거
        val toRemove = mutableListOf<Node>()
        collectAlternateContent(doc.documentElement, toRemove)

        if (toRemove.isEmpty()) return workbookBytes

        toRemove.forEach { it.parentNode.removeChild(it) }

        return ByteArrayOutputStream().also { out ->
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(javax.xml.transform.OutputKeys.STANDALONE, "yes")
            }.transform(DOMSource(doc), StreamResult(out))
        }.toByteArray()
    }

    private fun collectAlternateContent(element: Element, result: MutableList<Node>) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (child.namespaceURI == MC_NAMESPACE && child.localName == "AlternateContent") {
                    result.add(child)
                } else {
                    collectAlternateContent(child, result)
                }
            }
        }
    }
}
