package io.github.jogakdal.tbeg.engine.pipeline.processors.zippost

/**
 * XML 엔트리에 대해 변수 치환을 수행한다.
 *
 * XmlVariableProcessor의 VariableProcessor를 재사용하여 문자열 수준 치환을 수행하며,
 * 결과를 XML 이스케이프 처리한다.
 */
internal object VariableXmlHandler {

    private val EXCLUDE_SUFFIXES = listOf(".rels")
    private val EXCLUDE_NAMES = listOf("[Content_Types].xml")
    private val EXCLUDE_PREFIXES = listOf("docProps/")

    // 다른 핸들러가 처리하는 엔트리
    private val HANDLED_ENTRIES = listOf(
        "xl/styles.xml",
        "xl/workbook.xml",
        "docProps/core.xml",
        "docProps/app.xml"
    )

    /**
     * 해당 엔트리가 변수 치환 대상인지 판별한다.
     */
    fun shouldProcess(entryName: String): Boolean =
        entryName.endsWith(".xml")
            && entryName !in HANDLED_ENTRIES
            && !entryName.startsWith("xl/worksheets/")
            && EXCLUDE_SUFFIXES.none { entryName.endsWith(it) }
            && entryName !in EXCLUDE_NAMES
            && EXCLUDE_PREFIXES.none { entryName.startsWith(it) }

    /**
     * XML 바이트 배열에 대해 변수 치환을 수행한다.
     *
     * @param xmlBytes XML 바이트 배열
     * @param variableResolver 변수 치환 함수 (null이면 원본 반환)
     * @return 변수가 치환된 XML 바이트 배열
     */
    fun process(xmlBytes: ByteArray, variableResolver: ((String) -> String)?): ByteArray {
        if (variableResolver == null) return xmlBytes

        val original = xmlBytes.toString(Charsets.UTF_8)
        val processed = variableResolver(original)
        return if (processed != original) processed.toByteArray(Charsets.UTF_8) else xmlBytes
    }
}
