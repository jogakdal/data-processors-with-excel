package io.github.jogakdal.tbeg.engine.core

import io.github.jogakdal.tbeg.internal.commonLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

        private const val CHART_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml"
        private const val CHART_STYLE_CONTENT_TYPE = "application/vnd.ms-office.chartstyle+xml"
        private const val CHART_COLORS_CONTENT_TYPE = "application/vnd.ms-office.chartcolorstyle+xml"

        // 차트가 없는 템플릿에서는 Regex 컴파일 비용 절약
        private val CHART_PATH_PATTERN by lazy { Regex("/xl/charts/[^/]+\\.(xml|rels)") }
        private val CHART_RELS_PATH_PATTERN by lazy { Regex("/xl/charts/_rels/.*") }
        private val DRAWING_PATH_PATTERN by lazy { Regex("/xl/drawings/[^/]+\\.xml") }
        private val DRAWING_RELS_PATH_PATTERN by lazy { Regex("/xl/drawings/_rels/.*") }

        private val GRAPHIC_FRAME_PATTERN by lazy {
            Regex(
                "<xdr:twoCellAnchor[^>]*>.*?<xdr:graphicFrame.*?</xdr:graphicFrame>.*?</xdr:twoCellAnchor>",
                RegexOption.DOT_MATCHES_ALL
            )
        }

        private val TWO_CELL_ANCHOR_PATTERN by lazy {
            Regex(
                "<xdr:twoCellAnchor[^>]*>(?:(?!</xdr:twoCellAnchor>).)*</xdr:twoCellAnchor>",
                RegexOption.DOT_MATCHES_ALL
            )
        }

        private val ONE_CELL_ANCHOR_PATTERN by lazy {
            Regex(
                "<xdr:oneCellAnchor[^>]*>(?:(?!</xdr:oneCellAnchor>).)*</xdr:oneCellAnchor>",
                RegexOption.DOT_MATCHES_ALL
            )
        }

        private val RELS_RID_PATTERN by lazy { Regex("Id=\"(rId\\d+)\"") }

        private val CHART_REL_ID_TARGET_PATTERN by lazy {
            Regex("<Relationship[^>]*Id=\"(rId\\d+)\"[^>]*Target=\"([^\"]*)\"[^>]*/?>")
        }
        private val CHART_REL_TARGET_PATTERN by lazy {
            Regex("<Relationship[^>]*Target=\"[^\"]*charts/[^\"]*\"[^>]*/?>")
        }
        private val CHART_TARGET_ATTR_PATTERN by lazy { Regex("Target=\"[^\"]*charts/[^\"]*\"") }
        private val OVERRIDE_PATTERN by lazy {
            Regex("<Override[^>]*PartName=\"([^\"]*)\"[^>]*ContentType=\"([^\"]*)\"[^>]*>")
        }
        private val CHART_OVERRIDE_PATTERN by lazy { Regex("<Override[^>]*PartName=\"[^\"]*/charts/[^\"]*\"[^>]*>") }
        private val MULTIPLE_NEWLINES_PATTERN by lazy { Regex("\n\\s*\n") }
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
        val allDrawingsForShapes = AllDrawings(drawingFiles, drawingRelsFiles)

        contentTypesXml?.let { xml ->
            contentTypeEntries.addAll(extractChartAndDrawingContentTypes(xml, chartRelatedDrawings))
        }

        val chartInfo = ChartInfo(
            chartFiles = chartFiles,
            chartRelsFiles = chartRelsFiles,
            drawingFiles = chartRelatedDrawings.drawingFiles,
            drawingRelsFiles = chartRelatedDrawings.drawingRelsFiles,
            contentTypeEntries = contentTypeEntries,
            allDrawingFiles = allDrawingsForShapes.drawingFiles,
            allDrawingRelsFiles = allDrawingsForShapes.drawingRelsFiles
        )

        val cleanedBytes = removeChartFilesOnly(inputBytes)

        return chartInfo to cleanedBytes
    }

    /**
     * 차트 복원
     * @param variableResolver 변수 치환 함수 (차트 타이틀 등의 변수 치환에 사용)
     */
    fun restore(
        inputBytes: ByteArray,
        chartInfo: ChartInfo?,
        variableResolver: ((String) -> String)? = null
    ): ByteArray {
        if (chartInfo == null) return inputBytes

        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { zos ->
            val ctx = RestoreContext(zos, chartInfo, variableResolver)

            val (currentDrawingRels, pendingDrawingFiles, pendingDrawingRelsFiles) =
                collectAndCopyEntries(inputBytes, ctx)

            val ridMappings = calculateAllRidMappings(chartInfo, currentDrawingRels)

            writePendingDrawingFiles(ctx, pendingDrawingFiles, ridMappings)
            writePendingDrawingRelsFiles(ctx, pendingDrawingRelsFiles, ridMappings)
            writeChartFiles(ctx)
        }

        return output.toByteArray()
    }

    private data class RestoreContext(
        val zos: ZipOutputStream,
        val chartInfo: ChartInfo,
        val variableResolver: ((String) -> String)?,
        val writtenEntries: MutableSet<String> = mutableSetOf()
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
        ridMappings: Map<String, Map<String, String>>
    ) {
        pendingFiles.forEach { (path, entryName, currentContent) ->
            val originalDrawing = ctx.chartInfo.drawingFiles[path]
                ?: ctx.chartInfo.allDrawingFiles[path]

            val ridMapping = ridMappings["/xl/drawings/_rels/${path.substringAfterLast("/")}.rels"]
                ?: emptyMap()

            var mergedContent = if (originalDrawing != null) {
                mergeDrawingXml(
                    String(currentContent, Charsets.UTF_8),
                    String(originalDrawing, Charsets.UTF_8),
                    ridMapping
                )
            } else {
                String(currentContent, Charsets.UTF_8)
            }

            ctx.variableResolver?.let { mergedContent = it(mergedContent) }

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

    private fun writeChartFiles(ctx: RestoreContext) {
        ctx.chartInfo.chartFiles.forEach { (path, bytes) ->
            val processedBytes = if (ctx.variableResolver != null && path.endsWith(".xml")) {
                ctx.variableResolver(String(bytes, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
            } else {
                bytes
            }
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

        val chartRels = CHART_REL_ID_TARGET_PATTERN.findAll(originalRelsXml)
            .filter { it.groupValues[2].contains("charts/") }
            .toList()

        val mapping = mutableMapOf<String, String>()
        var newRidCounter = currentMaxRid + 1

        chartRels.forEach { match ->
            val originalRid = match.groupValues[1]
            val newRid = "rId$newRidCounter"
            mapping[originalRid] = newRid
            newRidCounter++
        }

        return mapping
    }

    /**
     * 드로잉 XML 병합 - 기존 드로잉에 원본의 모든 드로잉 객체 추가
     * 차트 앵커, 도형 앵커(텍스트 상자, WordArt 포함), 연결선, oneCellAnchor 등 모두 포함
     * @param ridMapping 원본 rId -> 새 rId 매핑
     */
    private fun mergeDrawingXml(currentXml: String, originalXml: String, ridMapping: Map<String, String>): String {
        val chartAnchors = GRAPHIC_FRAME_PATTERN.findAll(originalXml).map { it.value }.toList()

        // SXSSF가 생성한 차트 앵커를 원본 템플릿의 차트 앵커로 대체
        var workingXml = currentXml
        if (chartAnchors.isNotEmpty()) {
            workingXml = GRAPHIC_FRAME_PATTERN.replace(workingXml, "")
        }

        val currentTwoCellAnchors = TWO_CELL_ANCHOR_PATTERN.findAll(workingXml).map { it.value }.toSet()
        val currentOneCellAnchors = ONE_CELL_ANCHOR_PATTERN.findAll(workingXml).map { it.value }.toSet()

        val originalTwoCellAnchors = TWO_CELL_ANCHOR_PATTERN.findAll(originalXml).map { it.value }.toList()

        val shapeAnchors = originalTwoCellAnchors
            .filter { it.contains("<xdr:sp") && !it.contains("<xdr:graphicFrame") }
            .filter { it !in currentTwoCellAnchors }

        val connectorAnchors = originalTwoCellAnchors
            .filter { it.contains("<xdr:cxnSp") && !it.contains("<xdr:graphicFrame") }
            .filter { it !in currentTwoCellAnchors }

        val oneCellAnchors = ONE_CELL_ANCHOR_PATTERN.findAll(originalXml)
            .map { it.value }
            .filter { it !in currentOneCellAnchors }
            .toList()

        if (chartAnchors.isEmpty() && shapeAnchors.isEmpty() &&
            connectorAnchors.isEmpty() && oneCellAnchors.isEmpty()) {
            return workingXml
        }

        val updatedChartAnchors = chartAnchors.map { anchor ->
            var updated = anchor
            ridMapping.forEach { (oldRid, newRid) ->
                updated = updated.replace("r:id=\"$oldRid\"", "r:id=\"$newRid\"")
            }
            updated
        }

        val insertPosition = workingXml.lastIndexOf("</xdr:wsDr>")
        if (insertPosition == -1) {
            return workingXml
        }

        return workingXml.take(insertPosition) +
            (shapeAnchors + connectorAnchors + oneCellAnchors + updatedChartAnchors).joinToString("") +
            workingXml.substring(insertPosition)
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
            var updated = rel
            ridMapping.forEach { (oldRid, newRid) ->
                updated = updated.replace("Id=\"$oldRid\"", "Id=\"$newRid\"")
            }
            updated
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

    private data class AllDrawings(
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
        OVERRIDE_PATTERN.findAll(contentTypesXml).forEach { match ->
            val contentType = match.groupValues[2]
            if (match.groupValues[1].contains("/charts/") ||
                contentType == CHART_CONTENT_TYPE ||
                contentType == CHART_STYLE_CONTENT_TYPE ||
                contentType == CHART_COLORS_CONTENT_TYPE
            ) {
                add(match.value)
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
