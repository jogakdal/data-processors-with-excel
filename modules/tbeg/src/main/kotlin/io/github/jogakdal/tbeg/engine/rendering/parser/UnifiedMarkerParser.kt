package io.github.jogakdal.tbeg.engine.rendering.parser

import io.github.jogakdal.tbeg.engine.rendering.CellContent
import io.github.jogakdal.tbeg.engine.rendering.ImageSizeSpec
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection

/**
 * 통합 마커 파서
 *
 * 모든 마커 형식을 단일 진입점에서 파싱한다.
 * 텍스트 마커(${...})와 수식 마커(=TBEG_...)를 모두 지원한다.
 */
object UnifiedMarkerParser {

    // 텍스트 마커 패턴: ${markerName(...)}
    private val TEXT_MARKER_PATTERN = Regex(
        """\$\{(\w+)\s*\(\s*([^)]*)\s*\)}""",
        RegexOption.IGNORE_CASE
    )

    // 수식 마커 패턴: =TBEG_MARKERNAME(...) 또는 TBEG_MARKERNAME(...)
    private val FORMULA_MARKER_PATTERN = Regex(
        """=?TBEG_(\w+)\s*\(\s*([^)]*)\s*\)""",
        RegexOption.IGNORE_CASE
    )

    // 단순 변수 패턴: ${variableName}
    private val SIMPLE_VARIABLE_PATTERN = Regex("""\$\{(\w+)}""")

    // 아이템 필드 패턴: ${item.field} 또는 ${item.field.subfield}
    private val ITEM_FIELD_PATTERN = Regex("""\$\{(\w+)\.(\w+(?:\.\w+)*)}""")

    /**
     * 셀 내용을 분석하여 CellContent로 변환
     *
     * @param text 셀 텍스트 또는 수식
     * @param isFormula 수식 셀인지 여부
     * @param repeatItemVariables 현재 반복 영역의 아이템 변수명 집합 (같은 행에 여러 repeat이 있을 수 있음)
     * @return 분석된 셀 내용
     */
    fun parse(
        text: String?,
        isFormula: Boolean = false,
        repeatItemVariables: Set<String>? = null
    ): CellContent {
        if (text.isNullOrEmpty()) return CellContent.Empty

        // 1. 마커 함수 형태 파싱 시도
        val parsedMarker = parseMarker(text, isFormula)
        if (parsedMarker != null) {
            return convertToContent(parsedMarker)
        }

        // 2. 수식인 경우 변수 포함 수식 확인
        if (isFormula) {
            return parseFormulaContent(text)
        }

        // 3. 텍스트 형태 변수/아이템 필드 파싱
        return parseTextContent(text, repeatItemVariables)
    }

    /**
     * 마커 함수 파싱 (텍스트/수식 형태 모두 처리)
     */
    private fun parseMarker(text: String, isFormula: Boolean): ParsedMarker? {
        val pattern = if (isFormula) FORMULA_MARKER_PATTERN else TEXT_MARKER_PATTERN
        val match = pattern.find(text) ?: return null

        val markerName = match.groupValues[1]
        val paramString = match.groupValues[2]

        val definition = MarkerDefinition.byName(markerName) ?: return null
        val parameters = ParameterParser.parse(paramString, definition)

        return ParsedMarker(
            definition = definition,
            parameters = parameters,
            originalText = text,
            isFormula = isFormula
        )
    }

    /**
     * ParsedMarker를 CellContent로 변환 (검증 포함)
     */
    private fun convertToContent(marker: ParsedMarker): CellContent {
        return when (marker.definition.name) {
            "repeat" -> convertRepeatMarker(marker)
            "image" -> convertImageMarker(marker)
            "size" -> convertSizeMarker(marker)
            else -> CellContent.StaticString(marker.originalText)
        }
    }

    /**
     * 수식 내용 분석 (마커가 아닌 일반 수식)
     */
    private fun parseFormulaContent(formula: String): CellContent {
        val variables = SIMPLE_VARIABLE_PATTERN.findAll(formula)
            .map { it.groupValues[1] }
            .toList()

        return if (variables.isNotEmpty()) {
            CellContent.FormulaWithVariables(formula, variables)
        } else {
            CellContent.Formula(formula)
        }
    }

    /**
     * 텍스트 내용 분석
     */
    private fun parseTextContent(text: String, repeatItemVariables: Set<String>?): CellContent {
        // 아이템 필드 체크 (반복 영역 내부)
        ITEM_FIELD_PATTERN.find(text)?.let { match ->
            if (repeatItemVariables != null && match.groupValues[1] in repeatItemVariables) {
                return CellContent.ItemField(
                    itemVariable = match.groupValues[1],
                    fieldPath = match.groupValues[2],
                    originalText = text
                )
            }
        }

        // 단순 변수 체크
        SIMPLE_VARIABLE_PATTERN.find(text)?.let { match ->
            return CellContent.Variable(match.groupValues[1], text)
        }

        return CellContent.StaticString(text)
    }

    // === 검증 패턴 ===

    // 유효한 direction 값
    private val VALID_DIRECTIONS = setOf("DOWN", "RIGHT")

    // 셀 범위 패턴: A1:B2, $A$1:$B$2, A1 (단일 셀)
    private val CELL_RANGE_PATTERN = Regex(
        """^('?[^'!]+'?!)?(\$?[A-Za-z]+\$?\d+)(:\$?[A-Za-z]+\$?\d+)?$"""
    )

    // 식별자 패턴 (Named Range 포함)
    private val IDENTIFIER_PATTERN = Regex("""^\w+$""")

    // 이미지 크기 패턴: fit, original, 숫자:숫자, -1:-1, 0:0
    private val SIZE_SPEC_PATTERN = Regex("""^(fit|original|-?\d+:-?\d+)$""", RegexOption.IGNORE_CASE)

    // === 마커 변환 ===

    /**
     * repeat 마커를 CellContent.RepeatMarker로 변환 (검증 포함)
     */
    private fun convertRepeatMarker(marker: ParsedMarker): CellContent.RepeatMarker {
        val collection = marker.require("collection")
        val range = marker.require("range")
        val variable = marker.get("var")
        val directionValue = marker.get("direction")
        val emptyValue = marker.get("empty")

        // 검증
        validateRange(range, "range", marker.originalText)
        variable?.let { validateIdentifier(it, "var", marker.originalText) }
        directionValue?.let { validateDirection(it, marker.originalText) }
        emptyValue?.let { validateRange(it, "empty", marker.originalText) }

        return CellContent.RepeatMarker(
            collection = collection,
            range = range,
            variable = variable ?: collection,
            direction = parseDirection(directionValue),
            emptyRange = emptyValue
        )
    }

    /**
     * image 마커를 CellContent.ImageMarker로 변환 (검증 포함)
     */
    private fun convertImageMarker(marker: ParsedMarker): CellContent.ImageMarker {
        val name = marker.require("name")
        val position = marker.get("position")
        val size = marker.get("size")

        // 검증
        validateIdentifier(name, "name", marker.originalText)
        position?.let { validateRange(it, "position", marker.originalText) }
        size?.let { validateSizeSpec(it, marker.originalText) }

        return CellContent.ImageMarker(
            imageName = name,
            position = position?.takeIf { it.isNotEmpty() },
            sizeSpec = parseSizeSpec(size)
        )
    }

    /**
     * size 마커를 CellContent.SizeMarker로 변환 (검증 포함)
     */
    private fun convertSizeMarker(marker: ParsedMarker): CellContent.SizeMarker {
        val collection = marker.require("collection")

        // 검증
        validateIdentifier(collection, "collection", marker.originalText)

        return CellContent.SizeMarker(
            collectionName = collection,
            originalText = marker.originalText
        )
    }

    // === 검증 함수 ===

    private fun validateDirection(value: String, originalText: String) {
        if (value.uppercase() !in VALID_DIRECTIONS) {
            throw MarkerValidationException(
                "direction 파라미터는 DOWN 또는 RIGHT만 허용됩니다. " +
                "입력값: '$value', 마커: $originalText"
            )
        }
    }

    private fun validateRange(value: String, paramName: String, originalText: String) {
        // 셀 범위 패턴 또는 Named Range(식별자) 허용
        if (!CELL_RANGE_PATTERN.matches(value) && !IDENTIFIER_PATTERN.matches(value)) {
            throw MarkerValidationException(
                "$paramName 파라미터는 셀 범위(예: A1:B2) 또는 Named Range 형식이어야 합니다. " +
                "입력값: '$value', 마커: $originalText"
            )
        }
    }

    private fun validateIdentifier(value: String, paramName: String, originalText: String) {
        if (!IDENTIFIER_PATTERN.matches(value)) {
            throw MarkerValidationException(
                "$paramName 파라미터는 유효한 식별자(영문, 숫자, 언더스코어)여야 합니다. " +
                "입력값: '$value', 마커: $originalText"
            )
        }
    }

    private fun validateSizeSpec(value: String, originalText: String) {
        if (!SIZE_SPEC_PATTERN.matches(value)) {
            throw MarkerValidationException(
                "size 파라미터는 fit, original, 또는 숫자:숫자 형식이어야 합니다. " +
                "입력값: '$value', 마커: $originalText"
            )
        }
    }

    // === 파싱 헬퍼 ===

    private fun parseDirection(value: String?): RepeatDirection =
        when (value?.uppercase()) {
            "RIGHT" -> RepeatDirection.RIGHT
            else -> RepeatDirection.DOWN
        }

    private fun parseSizeSpec(value: String?): ImageSizeSpec =
        when (val trimmed = value?.trim()?.lowercase()) {
            null, "", "fit", "0:0" -> ImageSizeSpec.FIT_TO_CELL
            "original", "-1:-1" -> ImageSizeSpec.ORIGINAL
            else -> {
                val parts = trimmed.split(":")
                if (parts.size == 2) {
                    ImageSizeSpec(
                        parts[0].toIntOrNull() ?: 0,
                        parts[1].toIntOrNull() ?: 0
                    )
                } else {
                    ImageSizeSpec.FIT_TO_CELL
                }
            }
        }
}

/**
 * 마커 파라미터 검증 실패 시 발생하는 예외
 */
class MarkerValidationException(message: String) : IllegalArgumentException(message)
