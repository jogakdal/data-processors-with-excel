package io.github.jogakdal.tbeg.exception

/**
 * 템플릿 처리 중 발생하는 예외.
 *
 * @property errorType 오류 유형
 * @property details 오류 상세 정보
 */
class TemplateProcessingException(
    val errorType: ErrorType,
    val details: String,
    message: String = buildMessage(errorType, details),
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /**
     * 오류 유형
     */
    enum class ErrorType {
        /** 마커 문법 오류 (괄호 불일치 등) */
        INVALID_MARKER_SYNTAX,

        /** 필수 파라미터 누락 */
        MISSING_REQUIRED_PARAMETER,

        /** 잘못된 셀 범위 형식 */
        INVALID_RANGE_FORMAT,

        /** 존재하지 않는 시트 참조 */
        SHEET_NOT_FOUND,

        /** 잘못된 파라미터 값 */
        INVALID_PARAMETER_VALUE,

        /** 범위 충돌 (중첩, 경계 걸침 등) */
        RANGE_CONFLICT
    }

    companion object {
        private fun buildMessage(errorType: ErrorType, details: String): String {
            val typeMessage = when (errorType) {
                ErrorType.INVALID_MARKER_SYNTAX -> "Invalid marker syntax"
                ErrorType.MISSING_REQUIRED_PARAMETER -> "Missing required parameter"
                ErrorType.INVALID_RANGE_FORMAT -> "Invalid cell range format"
                ErrorType.SHEET_NOT_FOUND -> "Sheet not found"
                ErrorType.INVALID_PARAMETER_VALUE -> "Invalid parameter value"
                ErrorType.RANGE_CONFLICT -> "Range conflict"
            }
            return "Template processing error [$typeMessage]: $details"
        }

        /**
         * 마커 문법 오류
         */
        @Suppress("unused")
        fun invalidMarkerSyntax(marker: String, reason: String) = TemplateProcessingException(
            errorType = ErrorType.INVALID_MARKER_SYNTAX,
            details = "Marker '$marker' - $reason"
        )

        /**
         * 필수 파라미터 누락
         */
        @Suppress("unused")
        fun missingParameter(marker: String, parameterName: String) = TemplateProcessingException(
            errorType = ErrorType.MISSING_REQUIRED_PARAMETER,
            details = "Required parameter '$parameterName' is missing in marker '$marker'."
        )

        /**
         * 잘못된 셀 범위 형식
         */
        @Suppress("unused")
        fun invalidRange(marker: String, rangeStr: String) = TemplateProcessingException(
            errorType = ErrorType.INVALID_RANGE_FORMAT,
            details = "Range '$rangeStr' in marker '$marker' is not a valid Excel cell range format. (e.g., A1:C10, 'Sheet1'!A1:C10)"
        )

        /**
         * 존재하지 않는 시트 참조
         */
        @Suppress("unused")
        fun sheetNotFound(sheetName: String, availableSheets: List<String>) = TemplateProcessingException(
            errorType = ErrorType.SHEET_NOT_FOUND,
            details = "Sheet '$sheetName' not found. Available sheets: ${availableSheets.joinToString(", ")}"
        )

        /**
         * 범위 충돌
         */
        @Suppress("unused")
        fun rangeConflict(details: String) = TemplateProcessingException(
            errorType = ErrorType.RANGE_CONFLICT,
            details = details
        )

        /**
         * 잘못된 파라미터 값
         */
        @Suppress("unused")
        fun invalidParameterValue(
            marker: String,
            parameterName: String,
            value: String,
            validValues: List<String>? = null
        ) = TemplateProcessingException(
            errorType = ErrorType.INVALID_PARAMETER_VALUE,
            details = buildString {
                append("Invalid value '$value' for parameter '$parameterName' in marker '$marker'.")
                validValues?.let { append(" Valid values: ${it.joinToString(", ")}") }
            }
        )
    }
}
