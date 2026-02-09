package io.github.jogakdal.tbeg.engine.rendering.parser

/**
 * 마커 파라미터 파서
 *
 * 지원 형식:
 * - 위치 기반: `value1, value2, value3` (중간 생략 가능: `value1, , value3`)
 * - 명시적: `name=value, name="value", name='value', name=`value``
 *
 * 주의: 위치 기반과 명시적 형식은 혼용할 수 없음.
 * 하나의 파라미터라도 이름을 명시하면 모든 파라미터에 이름을 명시해야 함.
 */
object ParameterParser {

    /**
     * 파라미터 문자열을 파싱하여 Map으로 반환
     *
     * @param paramString 괄호 내부의 파라미터 문자열 (예: "employees, A2:C2" 또는 "collection=employees, range=A2:C2")
     * @param definition 마커 정의 (파라미터 순서 정보)
     * @return 파라미터 이름 -> 값 맵
     * @throws IllegalArgumentException 위치 기반과 명시적 파라미터가 혼용된 경우
     */
    fun parse(paramString: String, definition: MarkerDefinition): Map<String, String> {
        val tokens = tokenize(paramString)
        if (tokens.isEmpty()) return emptyMap()

        // 비어있지 않은 토큰만 분석 (빈 토큰은 위치 기반에서 생략을 의미)
        val nonEmptyTokens = tokens.filter { it.isNotEmpty() }
        if (nonEmptyTokens.isEmpty()) return emptyMap()

        // 각 토큰이 명시적인지 분석 (빈 토큰 제외)
        val tokenAnalysis = nonEmptyTokens.map { token ->
            token to (parseNamedParameter(token) != null)
        }

        val hasNamed = tokenAnalysis.any { it.second }
        val hasPositional = tokenAnalysis.any { !it.second }

        // 혼용 검사
        if (hasNamed && hasPositional) {
            throw IllegalArgumentException(
                "위치 기반과 명시적 파라미터를 혼용할 수 없습니다. " +
                "모든 파라미터에 이름을 명시하거나, 모두 위치 기반으로 작성하세요: $paramString"
            )
        }

        return if (hasNamed) {
            parseNamedParameters(nonEmptyTokens, definition)
        } else {
            parsePositionalParameters(tokens, definition)  // 빈 토큰 포함하여 전달
        }
    }

    /**
     * 명시적 파라미터 파싱
     */
    private fun parseNamedParameters(
        tokens: List<String>,
        definition: MarkerDefinition
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()

        for (token in tokens) {
            val namedMatch = parseNamedParameter(token) ?: continue
            val (name, value) = namedMatch
            if (value.isNotEmpty()) {
                val normalizedName = normalizeParameterName(name, definition)
                result[normalizedName] = value
            }
        }

        return result
    }

    /**
     * 위치 기반 파라미터 파싱
     */
    private fun parsePositionalParameters(
        tokens: List<String>,
        definition: MarkerDefinition
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()

        tokens.forEachIndexed { index, token ->
            val value = unquote(token.trim())
            if (value.isNotEmpty() && index < definition.parameters.size) {
                val paramDef = definition.parameters[index]
                result[paramDef.name] = value
            }
        }

        return result
    }

    /**
     * 파라미터 문자열을 토큰으로 분리
     * 따옴표 내부의 쉼표는 구분자로 취급하지 않음
     * 빈 토큰도 유지하여 위치 기반 파라미터 생략 지원 (예: "a, , c")
     */
    private fun tokenize(paramString: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar: Char? = null

        for (char in paramString) {
            when {
                !inQuote && char in "\"'`" -> {
                    inQuote = true
                    quoteChar = char
                    current.append(char)
                }
                inQuote && char == quoteChar -> {
                    inQuote = false
                    quoteChar = null
                    current.append(char)
                }
                !inQuote && char == ',' -> {
                    tokens.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        // 마지막 토큰 추가 (빈 문자열이라도 추가)
        tokens.add(current.toString().trim())

        return tokens
    }

    /**
     * 명시적 파라미터 파싱 (name=value 형태)
     *
     * @return Pair(이름, 값) 또는 null (명시적 파라미터가 아닌 경우)
     *         값이 NULL(대소문자 무관)이면 빈 문자열로 반환
     */
    private fun parseNamedParameter(token: String): Pair<String, String>? {
        val trimmed = token.trim()

        // = 위치 찾기 (따옴표 내부가 아닌 곳에서)
        var inQuote = false
        var quoteChar: Char? = null
        var equalsIndex = -1

        for ((index, char) in trimmed.withIndex()) {
            when {
                !inQuote && char in "\"'`" -> {
                    inQuote = true
                    quoteChar = char
                }
                inQuote && char == quoteChar -> {
                    inQuote = false
                    quoteChar = null
                }
                !inQuote && char == '=' -> {
                    equalsIndex = index
                    break
                }
            }
        }

        if (equalsIndex <= 0) return null

        val name = trimmed.take(equalsIndex).trim()
        val rawValue = trimmed.substring(equalsIndex + 1).trim()

        // 이름이 유효한 식별자인지 확인 (알파벳, 숫자, 언더스코어)
        if (!name.matches(Regex("""\w+"""))) return null

        val unquotedValue = unquote(rawValue)

        // NULL 값은 빈 문자열로 처리 (파라미터 생략과 동일한 효과)
        val value = if (unquotedValue.equals("null", ignoreCase = true)) "" else unquotedValue

        return name.lowercase() to value
    }

    /**
     * 파라미터 이름 정규화 (별칭을 정규 이름으로 변환)
     */
    private fun normalizeParameterName(name: String, definition: MarkerDefinition): String {
        val lowerName = name.lowercase()
        val paramDef = definition.parameters.find {
            it.name == lowerName || lowerName in it.aliases
        }
        return paramDef?.name ?: lowerName
    }

    /**
     * 따옴표 제거
     */
    private fun unquote(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.length >= 2 &&
                trimmed.first() in "\"'`" &&
                trimmed.last() == trimmed.first() ->
                trimmed.substring(1, trimmed.length - 1)
            else -> trimmed
        }
    }
}
