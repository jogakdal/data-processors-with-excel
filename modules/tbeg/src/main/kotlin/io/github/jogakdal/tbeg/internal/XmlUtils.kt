package io.github.jogakdal.tbeg.internal

/**
 * XML 특수 문자를 이스케이프한다.
 * &, <, >, ", ' 를 각각의 XML 엔티티로 변환한다.
 */
internal fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
