package io.github.jogakdal.tbeg.engine.preprocessing

import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCell
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCellFormula
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellFormulaType

/**
 * 셀 범위가 지정된 행/열 좌표를 포함하는지 확인한다.
 */
internal fun CellRangeAddress.containsCell(row: Int, col: Int) =
    row in firstRow..lastRow && col in firstColumn..lastColumn

/**
 * POI 수식 파서를 거치지 않고 수식 문자열을 직접 설정한다.
 *
 * TBEG 커스텀 함수(TBEG_HIDEABLE 등)는 POI가 인식하지 못하므로
 * setCellFormula()를 사용하면 FormulaParseException이 발생한다.
 */
internal fun XSSFCell.setFormulaRaw(formula: String) {
    val ctFormula = CTCellFormula.Factory.newInstance()
    ctFormula.stringValue = formula
    ctFormula.t = STCellFormulaType.NORMAL
    ctCell.f = ctFormula
}
