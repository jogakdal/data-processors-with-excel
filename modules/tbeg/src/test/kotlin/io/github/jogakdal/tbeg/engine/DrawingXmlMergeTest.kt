package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.core.ChartProcessor
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster.RepeatExpansionInfo
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * ChartProcessor.mergeDrawingXml() DOM 파싱 방식 테스트
 *
 * regex 기반에서 DOM 파싱으로 전환한 mergeDrawingXml()의 정확성을 검증한다.
 */
class DrawingXmlMergeTest {

    private val chartProcessor = ChartProcessor()

    /**
     * private mergeDrawingXml()를 리플렉션으로 호출
     */
    private fun callMergeDrawingXml(
        currentXml: String,
        originalXml: String,
        ridMapping: Map<String, String> = emptyMap(),
        expansions: List<RepeatExpansionInfo> = emptyList()
    ): String {
        val method: Method = ChartProcessor::class.java.getDeclaredMethod(
            "mergeDrawingXml", String::class.java, String::class.java, Map::class.java, List::class.java
        ).apply { isAccessible = true }
        return method.invoke(chartProcessor, currentXml, originalXml, ridMapping, expansions) as String
    }

    private fun wrapInWsDr(content: String) = """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">$content</xdr:wsDr>"""

    // ========== 차트 앵커 병합 ==========

    @Test
    fun `차트 앵커만 있는 경우 정상 병합`() {
        val currentXml = wrapInWsDr("")
        val originalXml = wrapInWsDr("""
            <xdr:twoCellAnchor>
                <xdr:from><xdr:col>0</xdr:col><xdr:row>5</xdr:row></xdr:from>
                <xdr:to><xdr:col>10</xdr:col><xdr:row>20</xdr:row></xdr:to>
                <xdr:graphicFrame>
                    <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData>
                    </a:graphic>
                </xdr:graphicFrame>
            </xdr:twoCellAnchor>
        """)

        val result = callMergeDrawingXml(currentXml, originalXml)

        assertTrue(result.contains("graphicFrame"), "차트 앵커가 병합되어야 한다")
        assertTrue(result.contains("twoCellAnchor"), "twoCellAnchor가 존재해야 한다")
    }

    @Test
    fun `차트와 이미지 혼합 - 이미지 anchor가 보존된다`() {
        val imageAnchor = """<xdr:oneCellAnchor><xdr:from><xdr:col>5</xdr:col><xdr:row>10</xdr:row></xdr:from><xdr:pic><xdr:blipFill><a:blip xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" r:embed="rId2"/></xdr:blipFill></xdr:pic></xdr:oneCellAnchor>"""
        val currentXml = wrapInWsDr(imageAnchor)

        val chartAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:row>5</xdr:row></xdr:from><xdr:to><xdr:col>10</xdr:col><xdr:row>20</xdr:row></xdr:to><xdr:graphicFrame><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData></a:graphic></xdr:graphicFrame></xdr:twoCellAnchor>"""
        val originalXml = wrapInWsDr(chartAnchor)

        val result = callMergeDrawingXml(currentXml, originalXml)

        // 이미지 anchor가 그대로 존재
        assertTrue(result.contains("oneCellAnchor"), "이미지 anchor가 보존되어야 한다")
        assertTrue(result.contains("pic"), "pic 요소가 존재해야 한다")
        // 차트 anchor도 추가
        assertTrue(result.contains("graphicFrame"), "차트 anchor가 추가되어야 한다")
    }

    @Test
    fun `도형 anchor가 원본에서 추가된다`() {
        val currentXml = wrapInWsDr("")
        val shapeAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>1</xdr:col><xdr:row>1</xdr:row></xdr:from><xdr:to><xdr:col>3</xdr:col><xdr:row>3</xdr:row></xdr:to><xdr:sp><xdr:txBody><a:p xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:r><a:t>텍스트</a:t></a:r></a:p></xdr:txBody></xdr:sp></xdr:twoCellAnchor>"""
        val originalXml = wrapInWsDr(shapeAnchor)

        val result = callMergeDrawingXml(currentXml, originalXml)

        assertTrue(result.contains("<xdr:sp"), "도형 anchor가 추가되어야 한다")
        assertTrue(result.contains("텍스트"), "도형 내용이 보존되어야 한다")
    }

    @Test
    fun `연결선 anchor가 원본에서 추가된다`() {
        val currentXml = wrapInWsDr("")
        val connectorAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:row>0</xdr:row></xdr:from><xdr:to><xdr:col>5</xdr:col><xdr:row>5</xdr:row></xdr:to><xdr:cxnSp><xdr:nvCxnSpPr><xdr:cNvPr id="3" name="Connector"/></xdr:nvCxnSpPr></xdr:cxnSp></xdr:twoCellAnchor>"""
        val originalXml = wrapInWsDr(connectorAnchor)

        val result = callMergeDrawingXml(currentXml, originalXml)

        assertTrue(result.contains("cxnSp"), "연결선 anchor가 추가되어야 한다")
    }

    // ========== rId 매핑 ==========

    @Test
    fun `rId 매핑이 차트 앵커에 적용된다`() {
        val currentXml = wrapInWsDr("")
        val originalXml = wrapInWsDr("""<xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:row>5</xdr:row></xdr:from><xdr:to><xdr:col>10</xdr:col><xdr:row>20</xdr:row></xdr:to><xdr:graphicFrame><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData></a:graphic></xdr:graphicFrame></xdr:twoCellAnchor>""")

        val result = callMergeDrawingXml(
            currentXml, originalXml,
            ridMapping = mapOf("rId1" to "rId5")
        )

        assertTrue(result.contains("rId5"), "rId가 매핑되어야 한다")
        assertFalse(result.contains("\"rId1\""), "원본 rId가 남아있으면 안 된다")
    }

    // ========== rId 매핑 - 속성 순서 ==========

    /**
     * private calculateRidMapping()를 리플렉션으로 호출
     */
    private fun callCalculateRidMapping(currentRelsXml: String, originalRelsXml: String): Map<*, *> {
        val method = ChartProcessor::class.java.getDeclaredMethod(
            "calculateRidMapping", String::class.java, String::class.java
        ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return method.invoke(chartProcessor, currentRelsXml, originalRelsXml) as Map<*, *>
    }

    @Test
    fun `calculateRidMapping - Target이 Id보다 앞에 오는 속성 순서에서도 차트 rId를 매핑한다`() {
        // 현재 rels에 rId1, rId2가 존재
        val currentRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image2.png"/>
            </Relationships>
        """.trimIndent()

        // 원본 rels: Target이 Id보다 앞에 오는 순서 (실제 Excel이 생성하는 형태)
        val originalRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="/xl/charts/chart1.xml" Id="rId1"/>
            </Relationships>
        """.trimIndent()

        val mapping = callCalculateRidMapping(currentRelsXml, originalRelsXml)

        // 현재 max rId = 2, 차트 rId1 → rId3으로 매핑되어야 한다
        assertEquals(1, mapping.size, "차트 관계 1개가 매핑되어야 한다")
        assertEquals("rId3", mapping["rId1"], "rId1 → rId3 매핑")
    }

    @Test
    fun `calculateRidMapping - Id가 Target보다 앞에 오는 기존 순서에서도 정상 동작한다`() {
        val currentRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
            </Relationships>
        """.trimIndent()

        // Id가 Target보다 앞에 오는 순서
        val originalRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart2.xml"/>
            </Relationships>
        """.trimIndent()

        val mapping = callCalculateRidMapping(currentRelsXml, originalRelsXml)

        assertEquals(2, mapping.size, "차트 관계 2개가 매핑되어야 한다")
        assertEquals("rId2", mapping["rId1"], "rId1 → rId2")
        assertEquals("rId3", mapping["rId2"], "rId2 → rId3")
    }

    // ========== 중복 방지 ==========

    @Test
    fun `현재에 이미 있는 도형은 중복 추가하지 않는다`() {
        val shapeAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>1</xdr:col><xdr:row>1</xdr:row></xdr:from><xdr:to><xdr:col>3</xdr:col><xdr:row>3</xdr:row></xdr:to><xdr:sp><xdr:txBody><a:p xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:r><a:t>기존 도형</a:t></a:r></a:p></xdr:txBody></xdr:sp></xdr:twoCellAnchor>"""
        val currentXml = wrapInWsDr(shapeAnchor)
        val originalXml = wrapInWsDr(shapeAnchor)

        val result = callMergeDrawingXml(currentXml, originalXml)

        // "기존 도형"이 1번만 존재해야 한다
        val count = Regex("기존 도형").findAll(result).count()
        assertEquals(1, count, "중복 도형이 추가되면 안 된다")
    }

    // ========== namespace 보존 ==========

    @Test
    fun `원본의 namespace 선언이 보존된다`() {
        val currentXml = """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"></xdr:wsDr>"""
        val originalXml = """<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"></xdr:wsDr>"""

        val result = callMergeDrawingXml(currentXml, originalXml)

        // xmlns:r 선언이 결과에 존재해야 한다
        assertTrue(result.contains("xmlns:r"), "xmlns:r 선언이 보존되어야 한다")
    }

    // ========== 빈 입력 ==========

    @Test
    fun `원본에 추가할 앵커가 없으면 현재 XML을 반환한다`() {
        val currentXml = wrapInWsDr("""<xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:row>0</xdr:row></xdr:from><xdr:pic/></xdr:oneCellAnchor>""")
        val originalXml = wrapInWsDr("")

        val result = callMergeDrawingXml(currentXml, originalXml)

        assertTrue(result.contains("oneCellAnchor"), "기존 이미지가 유지되어야 한다")
    }

    // ========== 차트 + 이미지가 아닌 twoCellAnchor 분류 정확성 ==========

    @Test
    fun `graphicFrame이 없는 twoCellAnchor는 차트가 아닌 도형으로 분류된다`() {
        // 이미지가 twoCellAnchor 안에 sp로 들어가고 그 뒤에 graphicFrame이 있는 다른 anchor가 있는 경우
        val imageInTwoCellAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:row>0</xdr:row></xdr:from><xdr:to><xdr:col>2</xdr:col><xdr:row>2</xdr:row></xdr:to><xdr:sp><xdr:nvSpPr><xdr:cNvPr id="1" name="Image1"/></xdr:nvSpPr></xdr:sp></xdr:twoCellAnchor>"""
        val chartAnchor = """<xdr:twoCellAnchor><xdr:from><xdr:col>3</xdr:col><xdr:row>3</xdr:row></xdr:from><xdr:to><xdr:col>10</xdr:col><xdr:row>10</xdr:row></xdr:to><xdr:graphicFrame><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData></a:graphic></xdr:graphicFrame></xdr:twoCellAnchor>"""

        val currentXml = wrapInWsDr("")
        val originalXml = wrapInWsDr(imageInTwoCellAnchor + chartAnchor)

        val result = callMergeDrawingXml(currentXml, originalXml)

        // 두 앵커 모두 존재해야 한다
        assertTrue(result.contains("Image1"), "도형(이미지) anchor가 추가되어야 한다")
        assertTrue(result.contains("graphicFrame"), "차트 anchor가 추가되어야 한다")

        // twoCellAnchor가 정확히 2개여야 한다 (서로 다른 anchor)
        val anchorCount = Regex("twoCellAnchor").findAll(result).count()
        // 열고 닫기 태그이므로 4개 (2개 anchor * 2 태그)
        assertEquals(4, anchorCount, "twoCellAnchor가 2쌍(4개 태그) 있어야 한다")
    }

    // ========== 앵커 위치 시프트 ==========

    @Test
    fun `expansions 전달 시 원본 앵커의 행이 시프트된다`() {
        val currentXml = wrapInWsDr("")
        // 원본 차트 앵커: from row=10, to row=25
        val originalXml = wrapInWsDr("""
            <xdr:twoCellAnchor>
                <xdr:from><xdr:col>0</xdr:col><xdr:row>10</xdr:row></xdr:from>
                <xdr:to><xdr:col>10</xdr:col><xdr:row>25</xdr:row></xdr:to>
                <xdr:graphicFrame>
                    <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData>
                    </a:graphic>
                </xdr:graphicFrame>
            </xdr:twoCellAnchor>
        """)

        // repeat: rows 3-5 (0-based, 3행), 5개 아이템 → expansion = (5-1)*3 = 12
        val expansions = listOf(
            RepeatExpansionInfo(
                templateStartRow = 3, templateEndRow = 5,
                templateStartCol = 0, templateEndCol = 10,
                itemCount = 5, direction = RepeatDirection.DOWN
            )
        )

        val result = callMergeDrawingXml(currentXml, originalXml, expansions = expansions)

        // from row: 10 > 5 → 10 + 12 = 22
        // to row: 25 > 5 → 25 + 12 = 37
        val fromRowPattern = Regex("""<xdr:from>.*?<xdr:row>(\d+)</xdr:row>""", RegexOption.DOT_MATCHES_ALL)
        val toRowPattern = Regex("""<xdr:to>.*?<xdr:row>(\d+)</xdr:row>""", RegexOption.DOT_MATCHES_ALL)

        val fromRow = fromRowPattern.find(result)?.groupValues?.get(1)?.toInt()
        val toRow = toRowPattern.find(result)?.groupValues?.get(1)?.toInt()

        assertEquals(22, fromRow, "원본 앵커 from row가 시프트되어야 한다 (10+12=22)")
        assertEquals(37, toRow, "원본 앵커 to row가 시프트되어야 한다 (25+12=37)")
    }

    @Test
    fun `expansions가 비어있으면 앵커 위치가 변경되지 않는다`() {
        val currentXml = wrapInWsDr("")
        val originalXml = wrapInWsDr("""
            <xdr:twoCellAnchor>
                <xdr:from><xdr:col>0</xdr:col><xdr:row>10</xdr:row></xdr:from>
                <xdr:to><xdr:col>10</xdr:col><xdr:row>25</xdr:row></xdr:to>
                <xdr:graphicFrame>
                    <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" r:id="rId1"/></a:graphicData>
                    </a:graphic>
                </xdr:graphicFrame>
            </xdr:twoCellAnchor>
        """)

        val result = callMergeDrawingXml(currentXml, originalXml)

        val fromRowPattern = Regex("""<xdr:from>.*?<xdr:row>(\d+)</xdr:row>""", RegexOption.DOT_MATCHES_ALL)
        val toRowPattern = Regex("""<xdr:to>.*?<xdr:row>(\d+)</xdr:row>""", RegexOption.DOT_MATCHES_ALL)

        assertEquals(10, fromRowPattern.find(result)?.groupValues?.get(1)?.toInt())
        assertEquals(25, toRowPattern.find(result)?.groupValues?.get(1)?.toInt())
    }
}
