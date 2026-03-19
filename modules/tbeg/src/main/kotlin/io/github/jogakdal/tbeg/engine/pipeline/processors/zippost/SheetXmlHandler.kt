package io.github.jogakdal.tbeg.engine.pipeline.processors.zippost

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter

/**
 * sheet*.xml을 StAX로 스트리밍 처리하여 셀의 스타일 인덱스를 교체한다.
 *
 * NUMERIC 또는 FORMULA 셀 중 styleMapping에 해당하는 스타일이 있으면
 * 적절한 변형 인덱스로 교체한다.
 */
internal object SheetXmlHandler {

    private val inputFactory = XMLInputFactory.newInstance().apply {
        // 외부 엔티티 비활성화 (보안)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
    }
    private val outputFactory = XMLOutputFactory.newInstance()

    /**
     * sheet XML을 처리하여 숫자 서식 스타일을 교체한다.
     *
     * @param sheetBytes sheet XML 바이트 배열
     * @param styleMapping 원본 스타일 인덱스 → 변형 인덱스 매핑
     * @return 수정된 sheet XML 바이트 배열
     */
    fun process(sheetBytes: ByteArray, styleMapping: Map<Int, StyleVariants>): ByteArray {
        if (styleMapping.isEmpty()) return sheetBytes

        val output = ByteArrayOutputStream(sheetBytes.size)
        val reader = inputFactory.createXMLStreamReader(ByteArrayInputStream(sheetBytes))
        val writer = outputFactory.createXMLStreamWriter(output, "UTF-8")

        try {
            processEvents(reader, writer, styleMapping)
        } finally {
            reader.close()
            writer.close()
        }

        return output.toByteArray()
    }

    private fun processEvents(
        reader: XMLStreamReader,
        writer: XMLStreamWriter,
        styleMapping: Map<Int, StyleVariants>
    ) {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_DOCUMENT ->
                    writer.writeStartDocument(reader.encoding ?: "UTF-8", reader.version ?: "1.0")
                XMLStreamConstants.END_DOCUMENT ->
                    writer.writeEndDocument()
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.localName == "c") {
                        processCellElement(reader, writer, styleMapping)
                    } else {
                        copyStartElement(reader, writer)
                    }
                }
                XMLStreamConstants.END_ELEMENT ->
                    writer.writeEndElement()
                XMLStreamConstants.CHARACTERS ->
                    writer.writeCharacters(reader.text)
                XMLStreamConstants.CDATA ->
                    writer.writeCData(reader.text)
                XMLStreamConstants.PROCESSING_INSTRUCTION ->
                    writer.writeProcessingInstruction(reader.piTarget, reader.piData ?: "")
                XMLStreamConstants.COMMENT ->
                    writer.writeComment(reader.text)
            }
        }
    }

    /**
     * `<c>` 요소를 버퍼링하여 분석 후 스타일을 교체한다.
     */
    private fun processCellElement(
        reader: XMLStreamReader,
        writer: XMLStreamWriter,
        styleMapping: Map<Int, StyleVariants>
    ) {
        val attrs = readAttributes(reader)
        val styleIdx = attrs["s"]?.toIntOrNull()
        val cellType = attrs["t"]

        // 매핑 대상이 아니면 빠르게 통과
        val variants = styleIdx?.let { styleMapping[it] }
        if (variants == null) {
            copyStartElementWithAttrs(writer, reader, attrs)
            return
        }

        // <c> 내부 자식 요소를 재귀적으로 버퍼링
        val children = mutableListOf<XmlNode>()
        bufferChildren(reader, children)

        // 셀 분석
        val hasFormula = children.any { it is XmlNode.Element && it.name == "f" }
        val valueText = children.filterIsInstance<XmlNode.Element>()
            .firstOrNull { it.name == "v" }
            ?.let { extractText(it) }

        // 새 스타일 인덱스 결정
        val newStyleIdx = when {
            hasFormula -> variants.formulaIndex
            (cellType == "n" || cellType == null) && valueText != null ->
                if (isIntegerValue(valueText)) variants.integerIndex else variants.decimalIndex
            else -> styleIdx
        }

        // <c> 출력
        val newAttrs = attrs.toMutableMap().apply { put("s", newStyleIdx.toString()) }
        writer.writeStartElement("c")
        newAttrs.forEach { (k, v) -> writer.writeAttribute(k, v) }
        children.forEach { writeNode(writer, it) }
        writer.writeEndElement()
    }

    /**
     * <c> 내부 자식 노드를 재귀적으로 버퍼링한다.
     * END_ELEMENT(</c>)을 만나면 종료한다.
     */
    private fun bufferChildren(reader: XMLStreamReader, nodes: MutableList<XmlNode>) {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    val name = reader.localName
                    val attrs = readAttributes(reader)
                    val childNodes = mutableListOf<XmlNode>()
                    bufferChildren(reader, childNodes)
                    nodes.add(XmlNode.Element(name, attrs, childNodes))
                }
                XMLStreamConstants.END_ELEMENT -> return
                XMLStreamConstants.CHARACTERS -> {
                    val text = reader.text
                    if (text.isNotEmpty()) nodes.add(XmlNode.Text(text))
                }
                XMLStreamConstants.CDATA -> {
                    val text = reader.text
                    if (text.isNotEmpty()) nodes.add(XmlNode.CData(text))
                }
            }
        }
    }

    /** 요소에서 모든 텍스트 콘텐츠를 재귀적으로 추출한다 */
    private fun extractText(element: XmlNode.Element): String = buildString {
        for (node in element.children) {
            when (node) {
                is XmlNode.Text -> append(node.text)
                is XmlNode.CData -> append(node.text)
                is XmlNode.Element -> append(extractText(node))
            }
        }
    }

    /** 버퍼링된 노드를 XMLStreamWriter로 출력한다 */
    private fun writeNode(writer: XMLStreamWriter, node: XmlNode) {
        when (node) {
            is XmlNode.Text -> writer.writeCharacters(node.text)
            is XmlNode.CData -> writer.writeCData(node.text)
            is XmlNode.Element -> {
                writer.writeStartElement(node.name)
                node.attrs.forEach { (k, v) -> writer.writeAttribute(k, v) }
                node.children.forEach { writeNode(writer, it) }
                writer.writeEndElement()
            }
        }
    }

    private fun isIntegerValue(value: String): Boolean {
        val d = value.toDoubleOrNull() ?: return false
        return d == d.toLong().toDouble()
    }

    private fun readAttributes(reader: XMLStreamReader): Map<String, String> =
        (0 until reader.attributeCount).associate { i ->
            reader.getAttributeLocalName(i) to reader.getAttributeValue(i)
        }

    private fun copyStartElement(reader: XMLStreamReader, writer: XMLStreamWriter) {
        val prefix = reader.prefix
        val namespaceURI = reader.namespaceURI
        val localName = reader.localName

        if (namespaceURI != null && namespaceURI.isNotEmpty()) {
            writer.writeStartElement(prefix ?: "", localName, namespaceURI)
        } else {
            writer.writeStartElement(localName)
        }

        for (i in 0 until reader.namespaceCount) {
            val nsPrefix = reader.getNamespacePrefix(i)
            val nsURI = reader.getNamespaceURI(i)
            if (nsPrefix == null || nsPrefix.isEmpty()) {
                writer.writeDefaultNamespace(nsURI)
            } else {
                writer.writeNamespace(nsPrefix, nsURI)
            }
        }

        for (i in 0 until reader.attributeCount) {
            val attrNs = reader.getAttributeNamespace(i)
            val attrPrefix = reader.getAttributePrefix(i)
            val attrLocal = reader.getAttributeLocalName(i)
            val attrValue = reader.getAttributeValue(i)

            if (attrNs != null && attrNs.isNotEmpty()) {
                writer.writeAttribute(attrPrefix ?: "", attrNs, attrLocal, attrValue)
            } else {
                writer.writeAttribute(attrLocal, attrValue)
            }
        }
    }

    /** 매핑 대상이 아닌 <c> 요소의 시작 태그를 그대로 복사한다 */
    private fun copyStartElementWithAttrs(
        writer: XMLStreamWriter,
        reader: XMLStreamReader,
        @Suppress("UNUSED_PARAMETER") attrs: Map<String, String>
    ) = copyStartElement(reader, writer)

    /** XML 노드의 재귀적 트리 표현 */
    private sealed class XmlNode {
        data class Element(val name: String, val attrs: Map<String, String>, val children: List<XmlNode>) : XmlNode()
        data class Text(val text: String) : XmlNode()
        data class CData(val text: String) : XmlNode()
    }
}
