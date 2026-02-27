package io.github.jogakdal.tbeg.engine.core

import io.github.jogakdal.tbeg.internal.commonLogger
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 차트 프로세서 - SXSSF 스트리밍 모드에서 차트 보존
 *
 * SXSSF로 새 워크북을 생성하면 원본 템플릿의 차트가 손실된다.
 * 이 프로세서는 차트와 관련 드로잉을 추출하고 처리 후 복원한다.
 * 기존 드로잉(이미지 등)에 차트 앵커를 병합하고 드로잉 관계 파일도 병합한다.
 */
internal class ChartProcessor {

    companion object {
        private val LOG by commonLogger()

        private const val RELS_NAMESPACE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val CHART_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"
        private const val CHART_STYLE_CONTENT_TYPE = "application/vnd.ms-office.chartstyle+xml"
        private const val CHART_COLORS_CONTENT_TYPE = "application/vnd.ms-office.chartcolorstyle+xml"

        // 차트가 없는 템플릿에서는 Regex 컴파일 비용 절약
        private val CHART_PATH_PATTERN by lazy { Regex("/xl/charts/[^/]+\\.(xml|rels)") }
        private val CHART_RELS_PATH_PATTERN by lazy { Regex("/xl/charts/_rels/.*") }
        private val DRAWING_PATH_PATTERN by lazy { Regex("/xl/drawings/[^/]+\\.xml") }
        private val DRAWING_RELS_PATH_PATTERN by lazy { Regex("/xl/drawings/_rels/.*") }

        private val RELS_RID_PATTERN by lazy { Regex("Id=\"(rId\\d+)\"") }

        private val CHART_REL_TARGET_PATTERN by lazy {
            Regex("<Relationship[^>]*Target=\"[^\"]*charts/[^\"]*\"[^>]*/?>")
        }
        private val CHART_TARGET_ATTR_PATTERN by lazy { Regex("Target=\"[^\"]*charts/[^\"]*\"") }
        private val OVERRIDE_ELEMENT_PATTERN by lazy { Regex("<Override[^>]*/?>") }
        private val CONTENT_TYPE_ATTR_PATTERN by lazy { Regex("ContentType=\"([^\"]*)\"") }
        private val CHART_OVERRIDE_PATTERN by lazy { Regex("<Override[^>]*PartName=\"[^\"]*/charts/[^\"]*\"[^>]*/?>") }
        private val MULTIPLE_NEWLINES_PATTERN by lazy { Regex("\n\\s*\n") }
        private val CHART_FORMULA_REF_PATTERN by lazy { Regex("<(c:)?f>([^<]+)</(c:)?f>") }
        private val PART_NAME_ATTR_PATTERN by lazy { Regex("PartName=\"([^\"]*)\"") }
    }

    data class ChartInfo(
        val chartFiles: Map<String, ByteArray>,
        val chartRelsFiles: Map<String, ByteArray>,
        val drawingFiles: Map<String, ByteArray>,
        val drawingRelsFiles: Map<String, ByteArray>,
        val contentTypeEntries: List<String>,
        /** 도형 보존을 위한 모든 드로잉 파일 (차트 관련 드로잉 포함) */
        val allDrawingFiles: Map<String, ByteArray> = emptyMap(),
        val allDrawingRelsFiles: Map<String, ByteArray> = emptyMap()
    )

    fun extractAndRemove(inputBytes: ByteArray): Pair<ChartInfo?, ByteArray> {
        val chartFiles = mutableMapOf<String, ByteArray>()
        val chartRelsFiles = mutableMapOf<String, ByteArray>()
        val drawingFiles = mutableMapOf<String, ByteArray>()
        val drawingRelsFiles = mutableMapOf<String, ByteArray>()
        val contentTypeEntries = mutableListOf<String>()
        var contentTypesXml: String? = null

        var hasChart = false

        ZipInputStream(ByteArrayInputStream(inputBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val path = "/" + entry.name

                when {
                    entry.name == "[Content_Types].xml" -> {
                        contentTypesXml = String(zis.readBytes(), Charsets.UTF_8)
                    }
                    CHART_PATH_PATTERN.matches(path) && !path.contains("/_rels/") -> {
                        chartFiles[path] = zis.readBytes()
                        hasChart = true
                    }
                    CHART_RELS_PATH_PATTERN.matches(path) -> {
                        chartRelsFiles[path] = zis.readBytes()
                    }
                    DRAWING_PATH_PATTERN.matches(path) && !path.contains("/_rels/") -> {
                        drawingFiles[path] = zis.readBytes()
                    }
                    DRAWING_RELS_PATH_PATTERN.matches(path) -> {
                        drawingRelsFiles[path] = zis.readBytes()
                    }
                }
                entry = zis.nextEntry
            }
        }

        if (!hasChart) {
            return null to inputBytes
        }

        val chartRelatedDrawings = filterChartRelatedDrawings(drawingFiles, drawingRelsFiles)

        contentTypesXml?.let { xml ->
            contentTypeEntries.addAll(extractChartAndDrawingContentTypes(xml, chartRelatedDrawings))
        }

        val chartInfo = ChartInfo(
            chartFiles = chartFiles,
            chartRelsFiles = chartRelsFiles,
            drawingFiles = chartRelatedDrawings.drawingFiles,
            drawingRelsFiles = chartRelatedDrawings.drawingRelsFiles,
            contentTypeEntries = contentTypeEntries,
            allDrawingFiles = drawingFiles,
            allDrawingRelsFiles = drawingRelsFiles
        )

        val cleanedBytes = removeChartFilesOnly(inputBytes)

        return chartInfo to cleanedBytes
    }

    /**
     * 차트 복원
     * @param variableResolver 변수 치환 함수 (차트 타이틀 등의 변수 치환에 사용)
     * @param repeatExpansionInfos 시트별 repeat 확장 정보 (차트 데이터 범위 조정용)
     */
    fun restore(
        inputBytes: ByteArray,
        chartInfo: ChartInfo?,
        variableResolver: ((String) -> String)? = null,
        repeatExpansionInfos: Map<String, List<ChartRangeAdjuster.RepeatExpansionInfo>> = emptyMap()
    ): ByteArray {
        if (chartInfo == null) return inputBytes

        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zos ->
            val ctx = RestoreContext(zos, chartInfo, variableResolver, repeatExpansionInfos = repeatExpansionInfos)

            val (currentDrawingRels, pendingDrawingFiles, pendingDrawingRelsFiles) =
                collectAndCopyEntries(inputBytes, ctx)

            val ridMappings = calculateAllRidMappings(chartInfo, currentDrawingRels)
            val drawingToSheet = buildDrawingToSheetMapping(chartInfo)

            writePendingDrawingFiles(ctx, pendingDrawingFiles, ridMappings, drawingToSheet)
            writePendingDrawingRelsFiles(ctx, pendingDrawingRelsFiles, ridMappings)
            writeChartFiles(ctx)
        }

        return output.toByteArray()
    }

    private data class RestoreContext(
        val zos: ZipOutputStream,
        val chartInfo: ChartInfo,
        val variableResolver: ((String) -> String)?,
        val writtenEntries: MutableSet<String> = mutableSetOf(),
        val repeatExpansionInfos: Map<String, List<ChartRangeAdjuster.RepeatExpansionInfo>> = emptyMap()
    )

    private class PendingFile(val path: String, val entryName: String, val content: ByteArray) {
        operator fun component1() = path
        operator fun component2() = entryName
        operator fun component3() = content
    }

    private data class CollectedData(
        val currentDrawingRels: Map<String, String>,
        val pendingDrawingFiles: List<PendingFile>,
        val pendingDrawingRelsFiles: List<PendingFile>
    )

    private fun collectAndCopyEntries(inputBytes: ByteArray, ctx: RestoreContext): CollectedData {
        val currentDrawingRels = mutableMapOf<String, String>()
        val pendingDrawingFiles = mutableListOf<PendingFile>()
        val pendingDrawingRelsFiles = mutableListOf<PendingFile>()

        ZipInputStream(ByteArrayInputStream(inputBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val path = "/" + entry.name
                val entryName = entry.name

                when {
                    entryName == "[Content_Types].xml" -> {
                        val updatedContent = addChartAndDrawingContentTypes(
                            String(zis.readBytes(), Charsets.UTF_8), ctx.chartInfo
                        )
                        ctx.zos.putNextEntry(ZipEntry(entryName))
                        ctx.zos.write(updatedContent.toByteArray(Charsets.UTF_8))
                        ctx.zos.closeEntry()
                        ctx.writtenEntries.add(entryName)
                    }
                    DRAWING_PATH_PATTERN.matches(path) && !path.contains("/_rels/") -> {
                        pendingDrawingFiles.add(PendingFile(path, entryName, zis.readBytes()))
                    }
                    DRAWING_RELS_PATH_PATTERN.matches(path) -> {
                        val content = zis.readBytes()
                        currentDrawingRels[path] = String(content, Charsets.UTF_8)
                        pendingDrawingRelsFiles.add(PendingFile(path, entryName, content))
                    }
                    else -> {
                        ctx.zos.putNextEntry(ZipEntry(entryName))
                        ctx.zos.write(zis.readBytes())
                        ctx.zos.closeEntry()
                        ctx.writtenEntries.add(entryName)
                    }
                }
                entry = zis.nextEntry
            }
        }

        return CollectedData(currentDrawingRels, pendingDrawingFiles, pendingDrawingRelsFiles)
    }

    private fun calculateAllRidMappings(
        chartInfo: ChartInfo,
        currentDrawingRels: Map<String, String>
    ): Map<String, Map<String, String>> = buildMap {
        chartInfo.drawingRelsFiles.forEach { (relsPath, originalRelsBytes) ->
            currentDrawingRels[relsPath]?.let { currentRelsXml ->
                put(relsPath, calculateRidMapping(currentRelsXml, String(originalRelsBytes, Charsets.UTF_8)))
            }
        }
    }

    private fun writePendingDrawingFiles(
        ctx: RestoreContext,
        pendingFiles: List<PendingFile>,
        ridMappings: Map<String, Map<String, String>>,
        drawingToSheet: Map<String, String> = emptyMap()
    ) {
        pendingFiles.forEach { (path, entryName, currentContent) ->
            val originalDrawing = ctx.chartInfo.drawingFiles[path]
                ?: ctx.chartInfo.allDrawingFiles[path]

            val ridMapping = ridMappings["/xl/drawings/_rels/${path.substringAfterLast("/")}.rels"]
                ?: emptyMap()

            val sheetName = drawingToSheet[path]
            val expansions = sheetName?.let { ctx.repeatExpansionInfos[it] } ?: emptyList()

            val rawContent = if (originalDrawing != null) {
                mergeDrawingXml(
                    String(currentContent, Charsets.UTF_8),
                    String(originalDrawing, Charsets.UTF_8),
                    ridMapping,
                    expansions
                )
            } else {
                String(currentContent, Charsets.UTF_8)
            }

            val mergedContent = ctx.variableResolver?.invoke(rawContent) ?: rawContent

            ctx.zos.putNextEntry(ZipEntry(entryName))
            ctx.zos.write(mergedContent.toByteArray(Charsets.UTF_8))
            ctx.zos.closeEntry()
            ctx.writtenEntries.add(entryName)
        }
    }

    private fun writePendingDrawingRelsFiles(
        ctx: RestoreContext,
        pendingFiles: List<PendingFile>,
        ridMappings: Map<String, Map<String, String>>
    ) {
        pendingFiles.forEach { (path, entryName, currentContent) ->
            val originalRels = ctx.chartInfo.drawingRelsFiles[path]
            val ridMapping = ridMappings[path] ?: emptyMap()

            val mergedContent = if (originalRels != null) {
                mergeDrawingRelsXml(
                    String(currentContent, Charsets.UTF_8),
                    String(originalRels, Charsets.UTF_8),
                    ridMapping
                )
            } else {
                String(currentContent, Charsets.UTF_8)
            }

            ctx.zos.putNextEntry(ZipEntry(entryName))
            ctx.zos.write(mergedContent.toByteArray(Charsets.UTF_8))
            ctx.zos.closeEntry()
            ctx.writtenEntries.add(entryName)
        }
    }

    /** 차트 XML에서 시트 이름 추출 (첫 번째 `<f>` 또는 `<c:f>` 태그의 시트 참조) */
    private fun extractSheetNameFromChartXml(xml: String): String? {
        val match = CHART_FORMULA_REF_PATTERN.find(xml) ?: return null
        val ref = match.groupValues[2]  // 그룹1은 프리픽스, 그룹2가 수식 내용
        val exclamationIndex = ref.indexOf('!')
        if (exclamationIndex == -1) return null
        return ref.substring(0, exclamationIndex)
            .removeSurrounding("'")
            .replace("''", "'")
    }

    /**
     * drawing 파일 경로 → 시트 이름 매핑을 구축한다.
     *
     * drawing rels에서 차트 참조를 추출하고, 해당 차트 XML의 수식에서 시트 이름을 파싱한다.
     */
    private fun buildDrawingToSheetMapping(chartInfo: ChartInfo): Map<String, String> = buildMap {
        chartInfo.drawingRelsFiles.forEach { (relsPath, relsBytes) ->
            val relsXml = String(relsBytes, Charsets.UTF_8)
            // 속성 순서에 무관한 CHART_REL_TARGET_PATTERN으로 차트 관계를 찾고, Target 경로 추출
            val chartTargets = CHART_REL_TARGET_PATTERN.findAll(relsXml)
                .mapNotNull { CHART_TARGET_ATTR_PATTERN.find(it.value)?.value }
                .map { it.removePrefix("Target=\"").removeSuffix("\"") }
                .toList()

            for (target in chartTargets) {
                // Target: "../charts/chart1.xml" → "/xl/charts/chart1.xml"
                val chartPath = "/xl/charts/" + target.substringAfterLast("/")
                val chartBytes = chartInfo.chartFiles[chartPath] ?: continue
                val sheetName = extractSheetNameFromChartXml(String(chartBytes, Charsets.UTF_8)) ?: continue

                // relsPath: "/xl/drawings/_rels/drawing1.xml.rels" → drawingPath: "/xl/drawings/drawing1.xml"
                val drawingPath = "/xl/drawings/" + relsPath.substringAfterLast("/_rels/").removeSuffix(".rels")
                put(drawingPath, sheetName)
                break  // 같은 drawing 내 차트는 같은 시트
            }
        }
    }

    /**
     * anchor 요소의 xdr:from, xdr:to 내 행/열 좌표를 repeat 확장에 따라 시프트한다.
     */
    private fun adjustAnchorElement(anchor: Element, expansions: List<ChartRangeAdjuster.RepeatExpansionInfo>) {
        if (expansions.isEmpty()) return

        val fromElement = findChildElement(anchor, "from") ?: return
        val toElement = findChildElement(anchor, "to") ?: return

        // 원본 열 범위 읽기 (행 시프트 시 열 범위 교차 판단에 사용)
        val fromCol = findChildElement(fromElement, "col")?.textContent?.toIntOrNull() ?: return
        val toCol = findChildElement(toElement, "col")?.textContent?.toIntOrNull() ?: return
        val colRange = fromCol..toCol

        // 행 시프트 (열 범위 고려)
        val shiftRow = { row: Int -> ChartRangeAdjuster.shiftRow(row, colRange, expansions) }
        shiftChildContent(fromElement, "row", shiftRow)
        shiftChildContent(toElement, "row", shiftRow)

        // 열 시프트: 시프트된 행 범위를 기준으로 판단
        val fromRow = findChildElement(fromElement, "row")?.textContent?.toIntOrNull() ?: return
        val toRow = findChildElement(toElement, "row")?.textContent?.toIntOrNull() ?: return
        val shiftCol = { col: Int -> ChartRangeAdjuster.shiftCol(col, fromRow..toRow, expansions) }
        shiftChildContent(fromElement, "col", shiftCol)
        shiftChildContent(toElement, "col", shiftCol)
    }

    private fun findChildElement(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.localName == localName) return child
        }
        return null
    }

    /** 자식 요소의 정수 텍스트를 변환 함수로 시프트한다. */
    private fun shiftChildContent(parent: Element, childLocalName: String, shiftFn: (Int) -> Int) {
        val element = findChildElement(parent, childLocalName) ?: return
        val value = element.textContent?.toIntOrNull() ?: return
        element.textContent = shiftFn(value).toString()
    }

    private fun writeChartFiles(ctx: RestoreContext) {
        ctx.chartInfo.chartFiles.forEach { (path, bytes) ->
            val processedBytes = if (path.endsWith(".xml")) {
                var xml = String(bytes, Charsets.UTF_8)

                // 차트 데이터 범위 조정 (repeat 확장에 맞게)
                if (ctx.repeatExpansionInfos.isNotEmpty()) {
                    extractSheetNameFromChartXml(xml)?.let { sheetName ->
                        ctx.repeatExpansionInfos[sheetName]?.let { expansions ->
                            xml = ChartRangeAdjuster.adjustChartXml(xml, sheetName, expansions)
                        }
                    }
                }

                // 변수 치환
                ctx.variableResolver?.let { xml = it(xml) }

                xml.toByteArray(Charsets.UTF_8)
            } else bytes

            addZipEntry(ctx.zos, path, processedBytes, ctx.writtenEntries)
        }

        ctx.chartInfo.chartRelsFiles.forEach { (path, bytes) ->
            addZipEntry(ctx.zos, path, bytes, ctx.writtenEntries)
        }

        ctx.chartInfo.drawingFiles.forEach { (path, bytes) ->
            addZipEntry(ctx.zos, path, bytes, ctx.writtenEntries)
        }
        ctx.chartInfo.drawingRelsFiles.forEach { (path, bytes) ->
            addZipEntry(ctx.zos, path, bytes, ctx.writtenEntries)
        }
    }

    /**
     * 원본 드로잉 rels의 차트 관계 rId를 새 rId로 매핑 계산
     */
    private fun calculateRidMapping(currentRelsXml: String, originalRelsXml: String): Map<String, String> {
        val currentMaxRid = RELS_RID_PATTERN.findAll(currentRelsXml)
            .mapNotNull { match ->
                val ridValue = match.groupValues[1]
                ridValue.removePrefix("rId").toIntOrNull().also { rid ->
                    if (rid == null) {
                        LOG.warn("잘못된 rId 형식: $ridValue")
                    }
                }
            }
            .maxOrNull() ?: 0

        // 속성 순서에 무관한 CHART_REL_TARGET_PATTERN으로 차트 관계를 찾고, RELS_RID_PATTERN으로 rId 추출
        val chartRelRids = CHART_REL_TARGET_PATTERN.findAll(originalRelsXml)
            .mapNotNull { RELS_RID_PATTERN.find(it.value)?.groupValues?.get(1) }
            .toList()

        return buildMap {
            var newRidCounter = currentMaxRid + 1
            chartRelRids.forEach { oldRid -> put(oldRid, "rId${newRidCounter++}") }
        }
    }

    /**
     * 드로잉 XML 병합 - DOM 파싱 기반
     *
     * 기존 드로잉에 원본의 모든 드로잉 객체(차트, 도형, 연결선, oneCellAnchor)를 추가한다.
     * DOM 파싱을 사용하여 anchor 경계를 정확히 인식하고 구조적으로 분류한다.
     *
     * @param ridMapping 원본 rId -> 새 rId 매핑
     * @param expansions repeat 확장 정보 (원본 앵커 위치 시프트용)
     */
    private fun mergeDrawingXml(
        currentXml: String,
        originalXml: String,
        ridMapping: Map<String, String>,
        expansions: List<ChartRangeAdjuster.RepeatExpansionInfo> = emptyList()
    ): String {
        val docBuilder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder()

        val currentDoc = docBuilder.parse(currentXml.byteInputStream(Charsets.UTF_8))
        val originalDoc = docBuilder.parse(originalXml.byteInputStream(Charsets.UTF_8))

        val currentRoot = currentDoc.documentElement
        val originalRoot = originalDoc.documentElement

        // 원본 Document의 anchor 요소를 분류
        val originalAnchors = classifyAnchors(originalRoot)
        val currentAnchors = classifyAnchors(currentRoot)

        // 현재 Document에서 graphicFrame 포함 twoCellAnchor 제거 (원본 차트로 대체)
        currentAnchors.chartAnchors.forEach { currentRoot.removeChild(it) }

        // 원본의 차트 앵커에 rId 매핑 적용 + 위치 시프트 후 추가
        originalAnchors.chartAnchors.forEach { anchor ->
            applyRidMapping(anchor, ridMapping)
            adjustAnchorElement(anchor, expansions)
            currentRoot.appendChild(currentDoc.importNode(anchor, true))
        }

        // 원본의 도형/연결선/oneCellAnchor 중 현재에 없는 것만 시프트 후 추가
        val currentAnchorSignatures = currentAnchors.allAnchors
            .map { serializeNode(it) }.toSet()

        (originalAnchors.shapeAnchors + originalAnchors.connectorAnchors + originalAnchors.oneCellAnchors)
            .filter { serializeNode(it) !in currentAnchorSignatures }
            .forEach { anchor ->
                adjustAnchorElement(anchor, expansions)
                currentRoot.appendChild(currentDoc.importNode(anchor, true))
            }

        // 원본 root의 namespace 선언을 현재 root에 보존
        val originalAttrs = originalRoot.attributes
        for (i in 0 until originalAttrs.length) {
            val attr = originalAttrs.item(i) as Attr
            if (attr.name.startsWith("xmlns:") && !currentRoot.hasAttribute(attr.name)) {
                currentRoot.setAttribute(attr.name, attr.value)
            }
        }

        return serializeNode(currentDoc)
    }

    /** anchor 요소들을 타입별로 분류 */
    private data class ClassifiedAnchors(
        val chartAnchors: List<Element>,
        val shapeAnchors: List<Element>,
        val connectorAnchors: List<Element>,
        val oneCellAnchors: List<Element>
    ) {
        val allAnchors get() = chartAnchors + shapeAnchors + connectorAnchors + oneCellAnchors
    }

    private fun classifyAnchors(root: Element): ClassifiedAnchors {
        val chartAnchors = mutableListOf<Element>()
        val shapeAnchors = mutableListOf<Element>()
        val connectorAnchors = mutableListOf<Element>()
        val oneCellAnchors = mutableListOf<Element>()

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            when (node.localName) {
                "twoCellAnchor" -> {
                    when {
                        hasDescendant(node, "graphicFrame") -> chartAnchors.add(node)
                        hasDescendant(node, "cxnSp") -> connectorAnchors.add(node)
                        hasDescendant(node, "sp") -> shapeAnchors.add(node)
                        else -> shapeAnchors.add(node)  // 기본: 도형으로 분류
                    }
                }
                "oneCellAnchor" -> oneCellAnchors.add(node)
            }
        }

        return ClassifiedAnchors(chartAnchors, shapeAnchors, connectorAnchors, oneCellAnchors)
    }

    /** 요소 내에 특정 localName을 가진 자손이 있는지 확인 */
    private fun hasDescendant(element: Element, localName: String): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.localName == localName) return true
            if (hasDescendant(child, localName)) return true
        }
        return false
    }

    /** rId 매핑을 anchor 요소에 적용 */
    private fun applyRidMapping(element: Element, ridMapping: Map<String, String>) {
        val rid = element.getAttributeNS(RELS_NAMESPACE, "id")
        if (rid.isNotEmpty() && rid in ridMapping) {
            element.setAttributeNS(RELS_NAMESPACE, "r:id", ridMapping[rid]!!)
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            (children.item(i) as? Element)?.let { applyRidMapping(it, ridMapping) }
        }
    }

    /** DOM 노드를 XML 문자열로 직렬화한다. Document, Element 등 모든 Node 타입에 사용 가능. */
    private fun serializeNode(node: Node): String {
        val transformer = TransformerFactory.newInstance().apply {
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        return StringWriter().also {
            transformer.transform(DOMSource(node), StreamResult(it))
        }.toString()
    }

    /**
     * 드로잉 관계 XML 병합 - 기존 관계에 차트 참조 추가
     * @param ridMapping 원본 rId -> 새 rId 매핑
     */
    private fun mergeDrawingRelsXml(currentXml: String, originalXml: String, ridMapping: Map<String, String>): String {
        val chartRels = CHART_REL_TARGET_PATTERN.findAll(originalXml).map { it.value }.toList()

        if (chartRels.isEmpty()) {
            return currentXml
        }

        val updatedRels = chartRels.map { rel ->
            ridMapping.entries.fold(rel) { acc, (oldRid, newRid) ->
                acc.replace("Id=\"$oldRid\"", "Id=\"$newRid\"")
            }
        }

        val insertPosition = currentXml.lastIndexOf("</Relationships>")
        if (insertPosition == -1) {
            return currentXml
        }

        return currentXml.take(insertPosition) +
            updatedRels.joinToString("") +
            currentXml.substring(insertPosition)
    }

    private data class ChartRelatedDrawings(
        val drawingFiles: Map<String, ByteArray>,
        val drawingRelsFiles: Map<String, ByteArray>
    )

    private fun filterChartRelatedDrawings(
        allDrawings: Map<String, ByteArray>,
        allDrawingRels: Map<String, ByteArray>
    ): ChartRelatedDrawings {
        val chartRelatedDrawingRels = allDrawingRels.filter { (_, content) ->
            CHART_TARGET_ATTR_PATTERN.containsMatchIn(String(content, Charsets.UTF_8))
        }

        val relatedDrawingNames = chartRelatedDrawingRels.keys.map { relsPath ->
            relsPath.substringAfterLast("/_rels/").removeSuffix(".rels")
        }

        val chartRelatedDrawings = allDrawings.filter { (path, _) ->
            relatedDrawingNames.any { name -> path.endsWith(name) }
        }

        return ChartRelatedDrawings(chartRelatedDrawings, chartRelatedDrawingRels)
    }

    private fun extractChartAndDrawingContentTypes(
        contentTypesXml: String,
        @Suppress("UNUSED_PARAMETER") chartRelatedDrawings: ChartRelatedDrawings
    ): List<String> = buildList {
        OVERRIDE_ELEMENT_PATTERN.findAll(contentTypesXml).forEach { match ->
            val element = match.value
            val partName = PART_NAME_ATTR_PATTERN.find(element)?.groupValues?.get(1) ?: ""
            val contentType = CONTENT_TYPE_ATTR_PATTERN.find(element)?.groupValues?.get(1) ?: ""
            if (partName.contains("/charts/") ||
                contentType == CHART_CONTENT_TYPE ||
                contentType == CHART_STYLE_CONTENT_TYPE ||
                contentType == CHART_COLORS_CONTENT_TYPE
            ) {
                add(element)
            }
        }
    }

    /**
     * 차트 파일만 제거 (드로잉은 유지)
     *
     * 차트 파일과 함께 drawing rels의 차트 참조도 제거하여
     * POI가 워크북을 열 때 "Skipped invalid entry" 경고가 발생하지 않도록 한다.
     */
    private fun removeChartFilesOnly(inputBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zos ->
            ZipInputStream(ByteArrayInputStream(inputBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val path = "/" + entry.name

                    val shouldSkip = CHART_PATH_PATTERN.matches(path) ||
                        CHART_RELS_PATH_PATTERN.matches(path)

                    if (!shouldSkip) {
                        when {
                            entry.name == "[Content_Types].xml" -> {
                                val content = String(zis.readBytes(), Charsets.UTF_8)
                                val cleaned = removeChartContentTypes(content)
                                zos.putNextEntry(ZipEntry(entry.name))
                                zos.write(cleaned.toByteArray(Charsets.UTF_8))
                                zos.closeEntry()
                            }
                            DRAWING_RELS_PATH_PATTERN.matches(path) -> {
                                val content = String(zis.readBytes(), Charsets.UTF_8)
                                val cleaned = removeChartReferencesFromDrawingRels(content)
                                zos.putNextEntry(ZipEntry(entry.name))
                                zos.write(cleaned.toByteArray(Charsets.UTF_8))
                                zos.closeEntry()
                            }
                            else -> {
                                zos.putNextEntry(ZipEntry(entry.name))
                                zos.write(zis.readBytes())
                                zos.closeEntry()
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        return output.toByteArray()
    }

    private fun removeChartReferencesFromDrawingRels(relsXml: String): String =
        relsXml
            .replace(CHART_REL_TARGET_PATTERN, "")
            .replace(MULTIPLE_NEWLINES_PATTERN, "\n")

    private fun removeChartContentTypes(contentTypesXml: String): String =
        contentTypesXml
            .replace(CHART_OVERRIDE_PATTERN, "")
            .replace(MULTIPLE_NEWLINES_PATTERN, "\n")

    private fun addChartAndDrawingContentTypes(contentTypesXml: String, chartInfo: ChartInfo): String {
        if (chartInfo.contentTypeEntries.isEmpty()) return contentTypesXml

        val insertPosition = contentTypesXml.lastIndexOf("</Types>")
        if (insertPosition == -1) return contentTypesXml

        val entriesToAdd = chartInfo.contentTypeEntries
            .filter { entry ->
                val partNameMatch = PART_NAME_ATTR_PATTERN.find(entry)
                val partName = partNameMatch?.groupValues?.get(1) ?: ""
                !contentTypesXml.contains("PartName=\"$partName\"")
            }
            .joinToString("\n")

        if (entriesToAdd.isEmpty()) return contentTypesXml

        return contentTypesXml.take(insertPosition) +
            entriesToAdd + "\n" +
            contentTypesXml.substring(insertPosition)
    }

    private fun addZipEntry(
        zos: ZipOutputStream,
        path: String,
        bytes: ByteArray,
        writtenEntries: MutableSet<String>
    ) {
        val entryPath = path.removePrefix("/")
        if (entryPath !in writtenEntries) {
            zos.putNextEntry(ZipEntry(entryPath))
            zos.write(bytes)
            zos.closeEntry()
            writtenEntries.add(entryPath)
        }
    }
}
