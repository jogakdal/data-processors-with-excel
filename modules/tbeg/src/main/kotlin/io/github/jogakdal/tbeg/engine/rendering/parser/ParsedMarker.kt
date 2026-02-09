package io.github.jogakdal.tbeg.engine.rendering.parser

/**
 * 파싱된 마커 정보
 *
 * @param definition 마커 정의
 * @param parameters 파라미터 이름 -> 값 맵
 * @param originalText 원본 텍스트
 * @param isFormula 수식 형태 여부
 */
data class ParsedMarker(
    val definition: MarkerDefinition,
    val parameters: Map<String, String>,
    val originalText: String,
    val isFormula: Boolean
) {
    /**
     * 파라미터 정의 찾기 (이름 또는 별칭으로)
     */
    private fun findParameterDef(paramName: String) =
        definition.parameters.find { it.name == paramName || paramName in it.aliases }

    /**
     * 파라미터 값 조회 (기본값 적용)
     *
     * @param paramName 파라미터 이름
     * @return 파라미터 값 또는 기본값, 둘 다 없으면 null
     */
    fun get(paramName: String): String? {
        // 직접 매칭
        parameters[paramName]?.let { return it }

        val paramDef = findParameterDef(paramName)

        // 별칭으로 검색
        paramDef?.aliases?.forEach { alias ->
            parameters[alias]?.let { return it }
        }

        // 기본값 반환
        return paramDef?.defaultValue
    }

    /**
     * 파라미터 값 조회 (필수, 없으면 예외)
     *
     * @param paramName 파라미터 이름
     * @return 파라미터 값
     * @throws IllegalStateException 파라미터가 없을 때
     */
    fun require(paramName: String): String =
        get(paramName) ?: throw IllegalStateException(
            "Required parameter '$paramName' not found in marker '${definition.name}'"
        )
}
