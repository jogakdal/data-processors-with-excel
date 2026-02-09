package io.github.jogakdal.tbeg.engine.rendering.parser

/**
 * 마커 파라미터 정의
 *
 * @param name 파라미터 이름
 * @param required 필수 여부
 * @param defaultValue 기본값 (없으면 null)
 * @param aliases 별칭 (예: "var" 대신 "variable"도 허용)
 */
data class ParameterDef(
    val name: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val aliases: Set<String> = emptySet()
)

/**
 * 마커 정의
 *
 * @param name 마커 이름 (예: "repeat", "image", "size")
 * @param parameters 파라미터 목록 (순서 중요: 위치 기반 바인딩에 사용)
 */
data class MarkerDefinition(
    val name: String,
    val parameters: List<ParameterDef>
) {
    companion object {
        /** repeat 마커 정의 */
        val REPEAT = MarkerDefinition(
            name = "repeat",
            parameters = listOf(
                ParameterDef("collection", required = true),
                ParameterDef("range", required = true),
                ParameterDef("var", aliases = setOf("variable")),
                ParameterDef("direction", defaultValue = "DOWN"),
                ParameterDef("empty", aliases = setOf("emptyrange"))  // 소문자로 비교
            )
        )

        /** image 마커 정의 */
        val IMAGE = MarkerDefinition(
            name = "image",
            parameters = listOf(
                ParameterDef("name", required = true),
                ParameterDef("position", aliases = setOf("pos", "cell")),
                ParameterDef("size", defaultValue = "fit")
            )
        )

        /** size 마커 정의 */
        val SIZE = MarkerDefinition(
            name = "size",
            parameters = listOf(
                ParameterDef("collection", required = true)
            )
        )

        /** 등록된 모든 마커 정의 */
        val ALL = listOf(REPEAT, IMAGE, SIZE)

        /** 이름으로 마커 정의 조회 */
        fun byName(name: String): MarkerDefinition? =
            ALL.find { it.name.equals(name, ignoreCase = true) }
    }
}
