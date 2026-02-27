package io.github.jogakdal.tbeg.samples

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.chart.*
import org.apache.poi.xssf.usermodel.*
import java.io.FileOutputStream
import java.nio.file.Path

/**
 * Rich Sample 템플릿을 POI로 생성하는 유틸리티.
 *
 * openpyxl로 생성한 차트는 POI(TBEG 엔진)와 호환되지 않으므로,
 * POI 네이티브 API로 직접 생성한다.
 */
object RichSampleTemplateGenerator {

    @JvmStatic
    fun main(args: Array<String>) {
        val outputPath = if (args.isNotEmpty()) {
            Path.of(args[0])
        } else {
            // 기본 경로: src/test/resources/templates/
            findResourceDir().resolve("templates/rich_sample_template.xlsx")
        }

        generate(outputPath)
        println("템플릿 생성 완료: $outputPath")
    }

    fun generate(outputPath: Path) {
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Sales Report")
            val helper = wb.creationHelper

            // === 스타일 정의 ===
            val styles = createStyles(wb)

            // === 열 너비 ===
            sheet.setColumnWidth(1, 18 * 256)   // B: Department / Category
            (2..6).forEach { sheet.setColumnWidth(it, 14 * 256) }  // C~G
            sheet.setColumnWidth(7, 3 * 256)    // H: gap
            sheet.setColumnWidth(8, 18 * 256)   // I: Category
            (9..10).forEach { sheet.setColumnWidth(it, 14 * 256) }  // J~K

            // === Row 1-2: Title ===
            val row1 = sheet.createRow(0).apply { heightInPoints = 50f }
            row1.createCell(1).apply {
                setCellValue("\${reportTitle}")
                cellStyle = styles.titleStyle
            }

            sheet.createRow(1).apply { heightInPoints = 30f }
            sheet.addMergedRegion(CellRangeAddress(0, 1, 1, 10))  // B1:K2

            // === Row 3: Info ===
            val row3 = sheet.createRow(2).apply { heightInPoints = 22f }
            row3.createCell(2).apply {
                setCellValue("Period: \${period}")
                cellStyle = styles.infoStyle
            }
            row3.createCell(4).apply {
                setCellValue("Author: \${author}")
                cellStyle = styles.infoStyle
            }
            row3.createCell(6).apply {
                setCellValue("Date: \${reportDate}")
                cellStyle = styles.infoStyle
            }

            // === Row 4: Spacer ===
            sheet.createRow(3).apply { heightInPoints = 8f }

            // === Row 5: Repeat markers ===
            val row5 = sheet.createRow(4).apply { heightInPoints = 18f }
            row5.createCell(1).apply {
                setCellValue("\${repeat(depts, B7:G7, d)}")
                cellStyle = styles.markerStyle
            }
            row5.createCell(8).apply {
                setCellValue("\${repeat(products, I7:K7, p)}")
                cellStyle = styles.markerStyle
            }

            // === Row 6: Headers ===
            val row6 = sheet.createRow(5).apply { heightInPoints = 28f }
            // 왼쪽 헤더 (navy)
            listOf("Department", "Revenue", "Cost", "Profit", "Target", "Achievement").forEachIndexed { i, title ->
                row6.createCell(i + 1).apply {
                    setCellValue(title)
                    cellStyle = styles.headerStyle
                }
            }
            // 오른쪽 헤더 (teal)
            listOf("Category", "Revenue", "Share").forEachIndexed { i, title ->
                row6.createCell(i + 8).apply {
                    setCellValue(title)
                    cellStyle = styles.tealHeaderStyle
                }
            }

            // === Row 7: Data row (repeat target) ===
            val row7 = sheet.createRow(6).apply { heightInPoints = 24f }
            // 왼쪽 데이터
            row7.createCell(1).apply {
                setCellValue("\${d.deptName}")
                cellStyle = styles.dataCenterStyle
            }
            row7.createCell(2).apply {
                setCellValue("\${d.revenue}")
                cellStyle = styles.dataNumberStyle
            }
            row7.createCell(3).apply {
                setCellValue("\${d.cost}")
                cellStyle = styles.dataNumberStyle
            }
            row7.createCell(4).apply {
                setCellFormula("C7-D7")
                cellStyle = styles.dataNumberStyle
            }
            row7.createCell(5).apply {
                setCellValue("\${d.target}")
                cellStyle = styles.dataNumberStyle
            }
            row7.createCell(6).apply {
                setCellFormula("C7/F7")
                cellStyle = styles.dataPercentStyle
            }
            // 오른쪽 데이터
            row7.createCell(8).apply {
                setCellValue("\${p.category}")
                cellStyle = styles.dataCenterStyle
            }
            row7.createCell(9).apply {
                setCellValue("\${p.revenue}")
                cellStyle = styles.dataNumberStyle
            }
            row7.createCell(10).apply {
                setCellFormula("J7/J8")
                cellStyle = styles.dataPercentStyle
            }

            // === Row 8: Total ===
            val row8 = sheet.createRow(7).apply { heightInPoints = 24f }
            // 왼쪽 Total
            row8.createCell(1).apply {
                setCellValue("Total")
                cellStyle = styles.totalTextStyle
            }
            row8.createCell(2).apply {
                setCellFormula("SUM(C7:C7)")
                cellStyle = styles.totalNumberStyle
            }
            row8.createCell(3).apply {
                setCellFormula("SUM(D7:D7)")
                cellStyle = styles.totalNumberStyle
            }
            row8.createCell(4).apply {
                setCellFormula("SUM(E7:E7)")
                cellStyle = styles.totalNumberStyle
            }
            row8.createCell(5).apply {
                setCellFormula("SUM(F7:F7)")
                cellStyle = styles.totalNumberStyle
            }
            row8.createCell(6).apply {
                setCellFormula("C8/F8")
                cellStyle = styles.totalPercentStyle
            }
            // 오른쪽 Total
            row8.createCell(8).apply {
                setCellValue("Total")
                cellStyle = styles.totalTextStyle
            }
            row8.createCell(9).apply {
                setCellFormula("SUM(J7:J7)")
                cellStyle = styles.totalNumberStyle
            }
            row8.createCell(10).apply {
                setCellValue(1.0)  // Total Share = 100%
                cellStyle = styles.totalPercentStyle
            }

            // === Row 9: Average (왼쪽만) ===
            val row9 = sheet.createRow(8).apply { heightInPoints = 24f }
            row9.createCell(1).apply {
                setCellValue("Average")
                cellStyle = styles.avgTextStyle
            }
            row9.createCell(2).apply {
                setCellFormula("AVERAGE(C7:C7)")
                setCellStyle(styles.avgNumberStyle)
            }
            row9.createCell(3).apply {
                setCellFormula("AVERAGE(D7:D7)")
                setCellStyle(styles.avgNumberStyle)
            }
            row9.createCell(4).apply {
                setCellFormula("AVERAGE(E7:E7)")
                setCellStyle(styles.avgNumberStyle)
            }
            row9.createCell(5).apply { setCellStyle(styles.avgBorderStyle) }
            row9.createCell(6).apply { setCellStyle(styles.avgBorderStyle) }

            // === 조건부 서식 ===
            val cfRules = sheet.sheetConditionalFormatting

            // G7: Achievement >= 100% → 초록, < 100% → 빨강
            val greenRule = cfRules.createConditionalFormattingRule(ComparisonOperator.GE, "1")
            greenRule.createPatternFormatting().apply {
                fillBackgroundColor = IndexedColors.LIGHT_GREEN.index
                fillPattern = PatternFormatting.SOLID_FOREGROUND
            }
            greenRule.createFontFormatting().apply {
                fontColorIndex = IndexedColors.GREEN.index
            }

            val redRule = cfRules.createConditionalFormattingRule(ComparisonOperator.LT, "1")
            redRule.createPatternFormatting().apply {
                fillBackgroundColor = IndexedColors.ROSE.index
                fillPattern = PatternFormatting.SOLID_FOREGROUND
            }
            redRule.createFontFormatting().apply {
                fontColorIndex = IndexedColors.RED.index
            }

            cfRules.addConditionalFormatting(
                arrayOf(CellRangeAddress.valueOf("G7")),
                greenRule, redRule
            )

            // K7: Share >= 30% → 초록, < 30% → 빨강
            val greenShareRule = cfRules.createConditionalFormattingRule(ComparisonOperator.GE, "0.3")
            greenShareRule.createPatternFormatting().apply {
                fillBackgroundColor = IndexedColors.LIGHT_GREEN.index
                fillPattern = PatternFormatting.SOLID_FOREGROUND
            }
            greenShareRule.createFontFormatting().apply {
                fontColorIndex = IndexedColors.GREEN.index
            }

            val redShareRule = cfRules.createConditionalFormattingRule(ComparisonOperator.LT, "0.3")
            redShareRule.createPatternFormatting().apply {
                fillBackgroundColor = IndexedColors.ROSE.index
                fillPattern = PatternFormatting.SOLID_FOREGROUND
            }
            redShareRule.createFontFormatting().apply {
                fontColorIndex = IndexedColors.RED.index
            }

            cfRules.addConditionalFormatting(
                arrayOf(CellRangeAddress.valueOf("K7")),
                greenShareRule, redShareRule
            )

            // === 차트 ===
            createBarChart(sheet)
            createPieChart(sheet)

            // === Row 28: 이미지 마커 ===
            val row28 = sheet.createRow(27).apply { heightInPoints = 18f }
            row28.createCell(5).apply {
                setCellValue("\${image(logo)}")
                cellStyle = styles.markerStyle
            }
            row28.createCell(10).apply {
                setCellValue("\${image(ci)}")
                cellStyle = styles.markerStyle
            }

            // === 인쇄 설정 ===
            sheet.printSetup.landscape = true
            sheet.fitToPage = true
            sheet.printSetup.fitWidth = 1
            sheet.printSetup.fitHeight = 0

            // === 저장 ===
            outputPath.parent.toFile().mkdirs()
            FileOutputStream(outputPath.toFile()).use { wb.write(it) }
        }
    }

    private fun createBarChart(sheet: XSSFSheet) {
        val drawing = sheet.createDrawingPatriarch()
        val anchor = drawing.createAnchor(0, 0, 0, 0, 2, 10, 7, 25)  // C11:H26

        val chart = drawing.createChart(anchor)
        chart.setTitleText("\${reportTitle} by Department")
        chart.setTitleOverlay(false)

        // 축
        val bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM)
        val leftAxis = chart.createValueAxis(AxisPosition.LEFT)
        leftAxis.crosses = AxisCrosses.AUTO_ZERO
        leftAxis.crossBetween = AxisCrossBetween.BETWEEN

        // 차트 데이터
        val data = chart.createData(ChartTypes.BAR, bottomAxis, leftAxis) as XDDFBarChartData
        data.barDirection = BarDirection.COL

        // 카테고리 (B7 = 부서명)
        val categories = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, CellRangeAddress(6, 6, 1, 1)
        )

        // 시리즈 1: Revenue (C7)
        val revenueSrc = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(6, 6, 2, 2)
        )
        data.addSeries(categories, revenueSrc).apply {
            setTitle("Revenue", null)
        }

        // 시리즈 2: Cost (D7)
        val costSrc = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(6, 6, 3, 3)
        )
        data.addSeries(categories, costSrc).apply {
            setTitle("Cost", null)
        }

        // 시리즈 3: Profit (E7)
        val profitSrc = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(6, 6, 4, 4)
        )
        data.addSeries(categories, profitSrc).apply {
            setTitle("Profit", null)
        }

        chart.plot(data)

        // 범례 (차트 하단)
        chart.orAddLegend.position = LegendPosition.BOTTOM
    }

    private fun createPieChart(sheet: XSSFSheet) {
        val drawing = sheet.drawingPatriarch
            ?: sheet.createDrawingPatriarch()
        val anchor = drawing.createAnchor(0, 0, 0, 0, 8, 10, 11, 25)  // I11:K25 (col 8~10, row 10~24)

        val chart = drawing.createChart(anchor)
        chart.setTitleText("Revenue Distribution by Category")
        chart.setTitleOverlay(false)

        // 파이 차트 (축 없음)
        val data = chart.createData(ChartTypes.PIE, null, null) as XDDFPieChartData

        // 카테고리 (I7 = Category)
        val categories = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, CellRangeAddress(6, 6, 8, 8)
        )

        // 값 (J7 = Revenue)
        val values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(6, 6, 9, 9)
        )

        data.addSeries(categories, values).apply {
            setTitle("Revenue", null)
        }

        chart.plot(data)

        // 범례 (차트 하단)
        chart.orAddLegend.position = LegendPosition.BOTTOM
    }

    private data class Styles(
        val markerStyle: XSSFCellStyle,
        val titleStyle: XSSFCellStyle,
        val infoStyle: XSSFCellStyle,
        val headerStyle: XSSFCellStyle,
        val tealHeaderStyle: XSSFCellStyle,
        val dataCenterStyle: XSSFCellStyle,
        val dataNumberStyle: XSSFCellStyle,
        val dataPercentStyle: XSSFCellStyle,
        val totalTextStyle: XSSFCellStyle,
        val totalNumberStyle: XSSFCellStyle,
        val totalPercentStyle: XSSFCellStyle,
        val avgTextStyle: XSSFCellStyle,
        val avgNumberStyle: XSSFCellStyle,
        val avgBorderStyle: XSSFCellStyle,
    )

    private fun createStyles(wb: XSSFWorkbook): Styles {
        val navy = XSSFColor(byteArrayOf(0x1F, 0x38, 0x64.toByte()), null)
        val teal = XSSFColor(byteArrayOf(0x1B, 0x5E, 0x5E), null)
        val darkGray = XSSFColor(byteArrayOf(0x33, 0x33, 0x33), null)
        val medGray = XSSFColor(byteArrayOf(0x55, 0x55, 0x55), null)

        val thinBorder = { style: XSSFCellStyle ->
            style.borderLeft = BorderStyle.THIN
            style.borderRight = BorderStyle.THIN
            style.borderTop = BorderStyle.THIN
            style.borderBottom = BorderStyle.THIN
            val borderColor = XSSFColor(byteArrayOf(0xD9.toByte(), 0xD9.toByte(), 0xD9.toByte()), null)
            style.setLeftBorderColor(borderColor)
            style.setRightBorderColor(borderColor)
            style.setTopBorderColor(borderColor)
            style.setBottomBorderColor(borderColor)
        }

        val markerStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                fontHeightInPoints = 9
                color = IndexedColors.GREY_40_PERCENT.index
            })
        } as XSSFCellStyle

        val titleStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                fontHeightInPoints = 18
                bold = true
            }.also { (it as XSSFFont).setColor(darkGray) })
            verticalAlignment = VerticalAlignment.CENTER
        } as XSSFCellStyle

        val infoStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply {
                fontHeightInPoints = 11
            }.also { (it as XSSFFont).setColor(medGray) })
            verticalAlignment = VerticalAlignment.CENTER
        } as XSSFCellStyle

        val headerStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply {
                fontHeightInPoints = 11
                bold = true
                color = IndexedColors.WHITE.index
            })
            setFillForegroundColor(navy)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }

        val tealHeaderStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply {
                fontHeightInPoints = 11
                bold = true
                color = IndexedColors.WHITE.index
            })
            setFillForegroundColor(teal)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }

        val dataCenterStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11 })
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            thinBorder(this)
        }

        val dataNumberStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11 })
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            alignment = HorizontalAlignment.RIGHT
            verticalAlignment = VerticalAlignment.CENTER
            thinBorder(this)
        }

        val dataPercentStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11 })
            dataFormat = wb.createDataFormat().getFormat("0%")
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            thinBorder(this)
        }

        val mediumTopBorder = { style: XSSFCellStyle ->
            thinBorder(style)
            style.borderTop = BorderStyle.MEDIUM
            style.setTopBorderColor(XSSFColor(byteArrayOf(0x33, 0x33, 0x33), null))
        }

        val totalTextStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11; bold = true })
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            mediumTopBorder(this)
        }

        val totalNumberStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11; bold = true })
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            alignment = HorizontalAlignment.RIGHT
            verticalAlignment = VerticalAlignment.CENTER
            mediumTopBorder(this)
        }

        val totalPercentStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11; bold = true })
            dataFormat = wb.createDataFormat().getFormat("0%")
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            mediumTopBorder(this)
        }

        val avgTextStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11; bold = true })
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            thinBorder(this)
        }

        val avgNumberStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            setFont(wb.createFont().apply { fontHeightInPoints = 11; bold = true })
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            alignment = HorizontalAlignment.RIGHT
            verticalAlignment = VerticalAlignment.CENTER
            thinBorder(this)
        }

        val avgBorderStyle = (wb.createCellStyle() as XSSFCellStyle).apply {
            thinBorder(this)
        }

        return Styles(
            markerStyle, titleStyle, infoStyle, headerStyle, tealHeaderStyle,
            dataCenterStyle, dataNumberStyle, dataPercentStyle,
            totalTextStyle, totalNumberStyle, totalPercentStyle,
            avgTextStyle, avgNumberStyle, avgBorderStyle,
        )
    }

    private fun findResourceDir(): Path {
        val classLocation = RichSampleTemplateGenerator::class.java.protectionDomain.codeSource?.location
        if (classLocation != null) {
            var current = Path.of(classLocation.toURI())
            while (current.parent != null) {
                if (current.fileName?.toString() == "build") {
                    return current.resolveSibling("src/test/resources")
                }
                current = current.parent
            }
        }
        return Path.of("src/test/resources")
    }
}
