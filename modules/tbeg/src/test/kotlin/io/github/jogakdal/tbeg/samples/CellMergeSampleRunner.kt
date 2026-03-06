package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

object CellMergeSampleRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val outputDir = Path.of("build/samples")
        Files.createDirectories(outputDir)

        // 1. 기본 merge 샘플 생성
        generateBasicMergeSample(outputDir)

        // 2. 다중 레벨 merge 샘플 생성
        generateMultiLevelMergeSample(outputDir)

        println("[셀 병합 샘플] 생성 완료: $outputDir")
    }

    private fun generateBasicMergeSample(outputDir: Path) {
        val template = createStyledTemplate("기본 셀 병합") { wb, sheet ->
            val headerStyle = createHeaderStyle(wb)
            val dataStyle = createDataStyle(wb)

            sheet.createRow(0).apply {
                createCell(0).apply { setCellValue("부서"); cellStyle = headerStyle }
                createCell(1).apply { setCellValue("이름"); cellStyle = headerStyle }
                createCell(2).apply { setCellValue("직급"); cellStyle = headerStyle }
                createCell(3).setCellValue("\${repeat(employees, A2:C2, emp)}")
            }
            sheet.createRow(1).apply {
                createCell(0).apply { setCellValue("\${merge(emp.dept)}"); cellStyle = dataStyle }
                createCell(1).apply { setCellValue("\${emp.name}"); cellStyle = dataStyle }
                createCell(2).apply { setCellValue("\${emp.rank}"); cellStyle = dataStyle }
            }
            sheet.setColumnWidth(0, 4000)
            sheet.setColumnWidth(1, 3500)
            sheet.setColumnWidth(2, 3500)
        }

        val data = mapOf<String, Any>(
            "employees" to listOf(
                mapOf("dept" to "영업부", "name" to "홍길동", "rank" to "부장"),
                mapOf("dept" to "영업부", "name" to "김철수", "rank" to "과장"),
                mapOf("dept" to "영업부", "name" to "이영희", "rank" to "대리"),
                mapOf("dept" to "개발부", "name" to "박민수", "rank" to "과장"),
                mapOf("dept" to "개발부", "name" to "최지은", "rank" to "사원"),
                mapOf("dept" to "기획부", "name" to "정하늘", "rank" to "차장"),
            )
        )

        ExcelGenerator().use { gen ->
            val bytes = gen.generate(ByteArrayInputStream(template), data)
            Files.write(outputDir.resolve("cell-merge-basic.xlsx"), bytes)
            println("[기본 셀 병합] cell-merge-basic.xlsx 생성 완료")
        }
    }

    private fun generateMultiLevelMergeSample(outputDir: Path) {
        val template = createStyledTemplate("다중 레벨 셀 병합") { wb, sheet ->
            val headerStyle = createHeaderStyle(wb)
            val dataStyle = createDataStyle(wb)

            sheet.createRow(0).apply {
                createCell(0).apply { setCellValue("부서"); cellStyle = headerStyle }
                createCell(1).apply { setCellValue("팀"); cellStyle = headerStyle }
                createCell(2).apply { setCellValue("이름"); cellStyle = headerStyle }
                createCell(3).apply { setCellValue("직급"); cellStyle = headerStyle }
                createCell(4).setCellValue("\${repeat(employees, A2:D2, emp)}")
            }
            sheet.createRow(1).apply {
                createCell(0).apply { setCellValue("\${merge(emp.dept)}"); cellStyle = dataStyle }
                createCell(1).apply { setCellValue("\${merge(emp.team)}"); cellStyle = dataStyle }
                createCell(2).apply { setCellValue("\${emp.name}"); cellStyle = dataStyle }
                createCell(3).apply { setCellValue("\${emp.rank}"); cellStyle = dataStyle }
            }
            sheet.setColumnWidth(0, 4000)
            sheet.setColumnWidth(1, 4000)
            sheet.setColumnWidth(2, 3500)
            sheet.setColumnWidth(3, 3500)
        }

        val data = mapOf<String, Any>(
            "employees" to listOf(
                mapOf("dept" to "영업부", "team" to "영업1팀", "name" to "홍길동", "rank" to "팀장"),
                mapOf("dept" to "영업부", "team" to "영업1팀", "name" to "김철수", "rank" to "대리"),
                mapOf("dept" to "영업부", "team" to "영업2팀", "name" to "이영희", "rank" to "팀장"),
                mapOf("dept" to "영업부", "team" to "영업2팀", "name" to "박민수", "rank" to "사원"),
                mapOf("dept" to "개발부", "team" to "백엔드팀", "name" to "최지은", "rank" to "팀장"),
                mapOf("dept" to "개발부", "team" to "백엔드팀", "name" to "정하늘", "rank" to "사원"),
                mapOf("dept" to "개발부", "team" to "백엔드팀", "name" to "한바다", "rank" to "사원"),
                mapOf("dept" to "개발부", "team" to "프론트팀", "name" to "오하늘", "rank" to "팀장"),
            )
        )

        ExcelGenerator().use { gen ->
            val bytes = gen.generate(ByteArrayInputStream(template), data)
            Files.write(outputDir.resolve("cell-merge-multi-level.xlsx"), bytes)
            println("[다중 레벨 셀 병합] cell-merge-multi-level.xlsx 생성 완료")
        }
    }

    private fun createStyledTemplate(
        title: String,
        block: (XSSFWorkbook, org.apache.poi.ss.usermodel.Sheet) -> Unit
    ): ByteArray = XSSFWorkbook().use { wb ->
        val sheet = wb.createSheet(title)
        block(wb, sheet)
        ByteArrayOutputStream().apply { wb.write(this) }.toByteArray()
    }

    private fun createHeaderStyle(wb: XSSFWorkbook) = wb.createCellStyle().apply {
        val font = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 11
        }
        setFont(font)
        fillForegroundColor = IndexedColors.PALE_BLUE.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
        alignment = HorizontalAlignment.CENTER
        verticalAlignment = VerticalAlignment.CENTER
        borderBottom = BorderStyle.THIN
        borderTop = BorderStyle.THIN
        borderLeft = BorderStyle.THIN
        borderRight = BorderStyle.THIN
    }

    private fun createDataStyle(wb: XSSFWorkbook) = wb.createCellStyle().apply {
        alignment = HorizontalAlignment.CENTER
        verticalAlignment = VerticalAlignment.CENTER
        borderBottom = BorderStyle.THIN
        borderTop = BorderStyle.THIN
        borderLeft = BorderStyle.THIN
        borderRight = BorderStyle.THIN
    }
}
