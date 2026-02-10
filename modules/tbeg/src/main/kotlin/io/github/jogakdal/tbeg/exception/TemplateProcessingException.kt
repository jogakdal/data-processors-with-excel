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
        /** repeat 마커 문법 오류 (괄호 불일치 등) */
        INVALID_REPEAT_SYNTAX,

        /** repeat 마커 필수 파라미터 누락 */
        MISSING_REQUIRED_PARAMETER,

        /** 잘못된 셀 범위 형식 */
        INVALID_RANGE_FORMAT,

        /** 존재하지 않는 시트 참조 */
        SHEET_NOT_FOUND,

        /** 잘못된 파라미터 값 */
        INVALID_PARAMETER_VALUE
    }

    companion object {
        private fun buildMessage(errorType: ErrorType, details: String): String {
            val typeMessage = when (errorType) {
                ErrorType.INVALID_REPEAT_SYNTAX -> "repeat 마커 문법 오류"
                ErrorType.MISSING_REQUIRED_PARAMETER -> "필수 파라미터 누락"
                ErrorType.INVALID_RANGE_FORMAT -> "잘못된 셀 범위 형식"
                ErrorType.SHEET_NOT_FOUND -> "존재하지 않는 시트"
                ErrorType.INVALID_PARAMETER_VALUE -> "잘못된 파라미터 값"
            }
            return "템플릿 처리 오류 [$typeMessage]: $details"
        }

        /**
         * repeat 마커 문법 오류
         */
        @Suppress("unused")
        fun invalidRepeatSyntax(marker: String, reason: String) = TemplateProcessingException(
            errorType = ErrorType.INVALID_REPEAT_SYNTAX,
            details = "마커 '$marker' - $reason"
        )

        /**
         * 필수 파라미터 누락
         */
        @Suppress("unused")
        fun missingParameter(marker: String, parameterName: String) = TemplateProcessingException(
            errorType = ErrorType.MISSING_REQUIRED_PARAMETER,
            details = "마커 '$marker'에서 필수 파라미터 '$parameterName'이(가) 누락되었습니다."
        )

        /**
         * 잘못된 셀 범위 형식
         */
        @Suppress("unused")
        fun invalidRange(marker: String, rangeStr: String) = TemplateProcessingException(
            errorType = ErrorType.INVALID_RANGE_FORMAT,
            details = "마커 '$marker'의 범위 '$rangeStr'이(가) 올바른 Excel 셀 범위 형식이 아닙니다. (예: A1:C10, 'Sheet1'!A1:C10)"
        )

        /**
         * 존재하지 않는 시트 참조
         */
        @Suppress("unused")
        fun sheetNotFound(sheetName: String, availableSheets: List<String>) = TemplateProcessingException(
            errorType = ErrorType.SHEET_NOT_FOUND,
            details = "시트 '$sheetName'을(를) 찾을 수 없습니다. 사용 가능한 시트: ${availableSheets.joinToString(", ")}"
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
                append("마커 '$marker'의 파라미터 '$parameterName' 값 '$value'이(가) 올바르지 않습니다.")
                validValues?.let { append(" 사용 가능한 값: ${it.joinToString(", ")}") }
            }
        )
    }
}