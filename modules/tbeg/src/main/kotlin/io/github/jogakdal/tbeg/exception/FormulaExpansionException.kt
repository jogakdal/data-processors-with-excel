package io.github.jogakdal.tbeg.exception

/**
 * 수식 확장 실패 시 발생하는 예외.
 *
 * 템플릿 처리 중 repeat 문으로 행이 확장되었지만, 수식이 확장되지 않은 경우 발생한다.
 * 이는 주로 병합 셀로 인해 데이터가 비연속적인 행에 배치되어 Excel의 함수 인자 수 제한(255개)을
 * 초과했을 때 발생한다.
 *
 * ## 해결 방법
 * 1. 템플릿을 1행 1데이터 구조로 수정한다.
 * 2. 병합 셀 사용을 최소화한다.
 * 3. 수식 대신 데이터 레벨에서 집계를 수행한다.
 *
 * @property sheetName 문제가 발생한 시트 이름
 * @property cellRef 문제가 발생한 셀 주소 (예: "B11")
 * @property formula 확장되지 않은 수식 (예: "SUM(B8)")
 */
class FormulaExpansionException(
    val sheetName: String,
    val cellRef: String,
    val formula: String,
    message: String = buildMessage(sheetName, cellRef, formula)
) : RuntimeException(message) {
    companion object {
        private fun buildMessage(sheetName: String, cellRef: String, formula: String): String {
            return """
                |Formula expansion failed: Formula '$formula' in cell $cellRef on sheet '$sheetName' could not be expanded.
                |
                |Cause: Rows were expanded by repeat directive, but non-contiguous cell references exceeded Excel's function argument limit (255).
                |
                |Resolution:
                |1. Modify the template to use a one-row-per-data structure (avoid multi-row layouts caused by merged cells).
                |2. Or provide pre-aggregated values from the DataProvider instead of using formulas.
            """.trimMargin()
        }
    }
}