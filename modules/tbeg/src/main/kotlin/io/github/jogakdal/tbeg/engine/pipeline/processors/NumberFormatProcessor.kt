package io.github.jogakdal.tbeg.engine.pipeline.processors

import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.engine.pipeline.ExcelProcessor
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import io.github.jogakdal.tbeg.engine.core.toByteArray
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream

/**
 * 숫자 서식 자동 적용 프로세서.
 *
 * 숫자 값이 있는 셀 중 표시 형식이 "일반"인 경우 자동으로 숫자 서식을 적용한다.
 * 또한 정렬이 "일반"인 경우 오른쪽 정렬을 적용한다.
 * 나머지 서식(글꼴, 색상 등)은 보존된다.
 */
internal class NumberFormatProcessor : ExcelProcessor {

    override val name: String = "NumberFormat"

    // 워크북별 스타일 캐시
    private val styleCache = mutableMapOf<XSSFWorkbook, MutableMap<String, XSSFCellStyle>>()

    override fun process(context: ProcessingContext): ProcessingContext {
        context.resultBytes = applyNumberFormatToNumericCells(context.resultBytes, context.config)
        return context
    }

    private fun applyNumberFormatToNumericCells(bytes: ByteArray, config: TbegConfig): ByteArray {
        return XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            workbook.forEach { sheet ->
                sheet.forEach { row ->
                    row.forEach { cell ->
                        if (cell.cellType == CellType.NUMERIC) {
                            val currentStyle = cell.cellStyle
                            val needsNumberFormat = currentStyle.dataFormat.toInt() == 0
                            val needsAlignment = currentStyle.alignment == HorizontalAlignment.GENERAL

                            // 표시 형식이 "일반"이거나 정렬이 "일반"인 경우 서식 적용
                            if (needsNumberFormat || needsAlignment) {
                                val value = cell.numericCellValue
                                val isInteger = value == value.toLong().toDouble()
                                cell.cellStyle = getOrCreateNumberStyle(
                                    workbook, currentStyle.index, isInteger,
                                    needsNumberFormat, needsAlignment, config
                                )
                            }
                        }
                    }
                }
            }
            workbook.toByteArray()
        }
    }

    private fun getOrCreateNumberStyle(
        workbook: XSSFWorkbook,
        templateStyleIdx: Short?,
        isInteger: Boolean,
        applyNumberFormat: Boolean,
        applyAlignment: Boolean,
        config: TbegConfig
    ): XSSFCellStyle {
        val formatSuffix = when {
            applyNumberFormat -> if (isInteger) "int" else "dec"
            else -> "noFmt"
        }
        val alignSuffix = if (applyAlignment) "right" else "noAlign"
        val cacheKey = "${templateStyleIdx ?: "none"}_${formatSuffix}_$alignSuffix"
        val workbookCache = styleCache.getOrPut(workbook) { mutableMapOf() }

        return workbookCache.getOrPut(cacheKey) {
            workbook.createCellStyle().apply {
                if (templateStyleIdx != null) {
                    cloneStyleFrom(workbook.getCellStyleAt(templateStyleIdx.toInt()))
                }
                if (applyNumberFormat) {
                    dataFormat = if (isInteger) {
                        config.pivotIntegerFormatIndex
                    } else {
                        config.pivotDecimalFormatIndex
                    }
                }
                if (applyAlignment) {
                    alignment = HorizontalAlignment.RIGHT
                }
            }
        }
    }
}
