package io.github.jogakdal.tbeg.engine.pipeline.processors.zippost

import io.github.jogakdal.tbeg.TbegConfig
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 숫자 서식 변형의 스타일 인덱스 매핑.
 *
 * @property integerIndex NUMERIC 정수용 변형 cellXf 인덱스
 * @property decimalIndex NUMERIC 소수용 변형 cellXf 인덱스
 * @property formulaIndex FORMULA용 변형 cellXf 인덱스
 */
internal data class StyleVariants(
    val integerIndex: Int,
    val decimalIndex: Int,
    val formulaIndex: Int
)

/**
 * styles.xml을 분석하여 numFmtId=0인 cellXf에 대한 변형을 추가한다.
 *
 * 변형 규칙:
 * - alignment=general인 경우: NUMERIC은 정렬을 RIGHT로 변경, FORMULA는 유지
 * - alignment!=general인 경우: 정렬을 그대로 유지
 * - 모든 변형에서 numFmtId를 config의 pivotIntegerFormatIndex 또는 pivotDecimalFormatIndex로 설정
 */
internal object StylesXmlHandler {

    /**
     * styles.xml을 분석하고 숫자 서식 변형을 추가한다.
     *
     * @param stylesBytes styles.xml의 바이트 배열
     * @param config TBEG 설정
     * @return 수정된 styles.xml 바이트와 원본 cellXf 인덱스 → 변형 인덱스 매핑
     */
    fun process(stylesBytes: ByteArray, config: TbegConfig): Pair<ByteArray, Map<Int, StyleVariants>> {
        val doc = parseXml(stylesBytes)
        val mapping = mutableMapOf<Int, StyleVariants>()

        val cellXfs = doc.getElementsByTagName("cellXfs").item(0) as? Element ?: return stylesBytes to mapping
        val xfNodes = cellXfs.getElementsByTagName("xf")

        // 원본 cellXf 분석: numFmtId=0인 것만 대상
        data class TargetXf(val index: Int, val element: Element, val isAlignmentGeneral: Boolean)

        val targets = (0 until xfNodes.length).mapNotNull { i ->
            val xf = xfNodes.item(i) as Element
            // cellXfs 직접 자식만 (numFmts 내부의 xf 제외)
            if (xf.parentNode != cellXfs) return@mapNotNull null

            val numFmtId = xf.getAttribute("numFmtId").toIntOrNull() ?: return@mapNotNull null
            if (numFmtId != 0) return@mapNotNull null

            val isGeneral = isAlignmentGeneral(xf)
            TargetXf(i, xf, isGeneral)
        }

        if (targets.isEmpty()) return stylesBytes to mapping

        // cellXfs 직접 자식 수를 정확히 계산하여 다음 인덱스 결정
        var nextIndex = generateSequence(cellXfs.firstChild) { it.nextSibling }
            .count { it is Element && it.tagName == "xf" }

        for (target in targets) {
            val intIdx = nextIndex++
            val decIdx = nextIndex++
            val fmtIdx = nextIndex++

            // 정수 NUMERIC 변형
            cellXfs.appendChild(createVariantXf(doc, target.element, config.pivotIntegerFormatIndex,
                applyRightAlignment = target.isAlignmentGeneral))
            // 소수 NUMERIC 변형
            cellXfs.appendChild(createVariantXf(doc, target.element, config.pivotDecimalFormatIndex,
                applyRightAlignment = target.isAlignmentGeneral))
            // FORMULA 변형 (정렬 변경 없음)
            cellXfs.appendChild(createVariantXf(doc, target.element, config.pivotIntegerFormatIndex,
                applyRightAlignment = false))

            mapping[target.index] = StyleVariants(intIdx, decIdx, fmtIdx)
        }

        // cellXfs count 속성 업데이트
        cellXfs.setAttribute("count", nextIndex.toString())

        return serializeXml(doc) to mapping
    }

    private fun isAlignmentGeneral(xf: Element): Boolean {
        val alignments = xf.getElementsByTagName("alignment")
        if (alignments.length == 0) return true
        val alignment = alignments.item(0) as Element
        val horizontal = alignment.getAttribute("horizontal")
        return horizontal.isEmpty() || horizontal == "general"
    }

    private fun createVariantXf(
        doc: Document,
        original: Element,
        numFmtId: Short,
        applyRightAlignment: Boolean
    ): Element {
        val clone = original.cloneNode(true) as Element
        clone.setAttribute("numFmtId", numFmtId.toInt().toString())
        clone.setAttribute("applyNumberFormat", "1")

        if (applyRightAlignment) {
            val alignments = clone.getElementsByTagName("alignment")
            if (alignments.length > 0) {
                (alignments.item(0) as Element).setAttribute("horizontal", "right")
            } else {
                val alignment = doc.createElement("alignment")
                alignment.setAttribute("horizontal", "right")
                clone.appendChild(alignment)
            }
        }

        return clone
    }

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
