package io.github.jogakdal.tbeg.engine.core

import org.apache.poi.ss.usermodel.ConditionalFormattingRule
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCfRule

/**
 * 조건부 서식 관련 유틸리티.
 *
 * POI API에서 직접 제공하지 않는 dxfId(차등 서식 ID) 접근을 리플렉션으로 처리한다.
 */
internal object ConditionalFormattingUtils {

    /**
     * ConditionalFormattingRule에서 dxfId를 추출한다.
     *
     * @param rule 조건부 서식 규칙
     * @return dxfId 값 (실패 시 -1)
     */
    fun extractDxfId(rule: ConditionalFormattingRule): Int = runCatching {
        val ctRule = rule.javaClass.getDeclaredField("_cfRule").apply { isAccessible = true }.get(rule)
        val getDxfId = ctRule.javaClass.getMethod("getDxfId")
        (getDxfId.invoke(ctRule) as? Long)?.toInt() ?: -1
    }.getOrDefault(-1)

    /**
     * XSSFConditionalFormattingRule에 dxfId를 설정한다.
     *
     * @param rule 조건부 서식 규칙
     * @param dxfId 설정할 dxfId 값
     */
    fun setDxfId(rule: ConditionalFormattingRule, dxfId: Int) {
        runCatching {
            val xssfRule = rule as? XSSFConditionalFormattingRule ?: return
            val cfRuleField = xssfRule.javaClass.getDeclaredField("_cfRule")
            cfRuleField.isAccessible = true
            (cfRuleField.get(xssfRule) as CTCfRule).dxfId = dxfId.toLong()
        }
    }
}
