package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.TbegConfig
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.chart.*
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * 차트 + repeat 통합 테스트
 *
 * 차트가 포함된 템플릿에서 repeat 확장 후
 * 차트 데이터 범위가 올바르게 조정되는지 검증한다.
 */
class ChartRepeatIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    /**
     * 프로그래밍 방식으로 차트 포함 템플릿을 생성한다.
     *
     * 템플릿 구조:
     * - A1: ${title} (제목)
     * - A2: "부서", B2: "매출", C2: "비용" (헤더)
     * - A3: ${repeat(departments, A3:C3, dept)} (repeat 마커 - 수식으로)
     * - A3: ${dept.name}, B3: ${dept.sales}, C3: ${dept.cost} (데이터)
     * - 차트: B2:C2 ~ B3:C3 범위를 참조하는 Bar chart
     *
     * @return 템플릿 바이트 배열
     */
    private fun createChartTemplate() = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Sheet1")

        // 행 1: 제목
        sheet.createRow(0).createCell(0).setCellValue("\${title}")

        // 행 2: 헤더
        val headerRow = sheet.createRow(1)
        headerRow.createCell(0).setCellValue("부서")
        headerRow.createCell(1).setCellValue("매출")
        headerRow.createCell(2).setCellValue("비용")

        // 행 3: repeat 마커 (수식 형태) + 데이터 변수
        val dataRow = sheet.createRow(2)
        // repeat 마커를 A3 셀에 수식으로 설정
        dataRow.createCell(0).setCellValue("\${dept.name}")
        dataRow.createCell(1).setCellValue("\${dept.sales}")
        dataRow.createCell(2).setCellValue("\${dept.cost}")

        // repeat 마커는 별도 셀에 (예: E3)
        dataRow.createCell(4).setCellValue("\${repeat(departments, A3:C3, dept)}")

        // 차트 생성 - Bar chart
        val drawing = sheet.createDrawingPatriarch()
        val anchor = drawing.createAnchor(0, 0, 0, 0, 0, 5, 10, 20)
        val chart = drawing.createChart(anchor)

        // 데이터 소스 설정 - 템플릿 행 범위 (1행만)
        val catData = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, CellRangeAddress(2, 2, 0, 0)  // A3:A3 (카테고리)
        )
        val valData1 = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(2, 2, 1, 1)  // B3:B3 (매출)
        )
        val valData2 = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, CellRangeAddress(2, 2, 2, 2)  // C3:C3 (비용)
        )

        val barChartData = chart.createData(ChartTypes.BAR, chart.createCategoryAxis(AxisPosition.BOTTOM), chart.createValueAxis(AxisPosition.LEFT)) as XDDFBarChartData
        barChartData.addSeries(catData, valData1).setTitle("매출", null)
        barChartData.addSeries(catData, valData2).setTitle("비용", null)
        chart.plot(barChartData)

        ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
    }

    private fun createTestData(): SimpleDataProvider {
        val departments = listOf(
            mapOf("name" to "영업부", "sales" to 1500, "cost" to 800),
            mapOf("name" to "개발부", "sales" to 2000, "cost" to 1200),
            mapOf("name" to "마케팅부", "sales" to 1800, "cost" to 1000),
            mapOf("name" to "인사부", "sales" to 900, "cost" to 600),
            mapOf("name" to "재무부", "sales" to 1100, "cost" to 700)
        )

        return SimpleDataProvider.Builder()
            .value("title", "부서별 실적 보고서")
            .items("departments", departments)
            .build()
    }

    @Test
    fun `XSSF 모드 - repeat 확장 후 차트 범위가 모든 데이터를 포함한다`() {
        val templateBytes = createChartTemplate()
        val dataProvider = createTestData()

        val resultBytes = ExcelGenerator(TbegConfig(streamingMode = StreamingMode.DISABLED)).use {
            it.generate(ByteArrayInputStream(templateBytes), dataProvider)
        }

        // 결과 검증
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 데이터 확장 확인 (5개 부서)
            assertEquals("영업부", sheet.getRow(2)?.getCell(0)?.stringCellValue)
            assertEquals("재무부", sheet.getRow(6)?.getCell(0)?.stringCellValue)

            // 차트 범위 확인
            val drawing = sheet.drawingPatriarch
            assertNotNull(drawing)
            assertTrue(drawing.charts.isNotEmpty(), "차트가 존재해야 한다")

            val chart = drawing.charts[0]
            val plotArea = chart.ctChart.plotArea

            // Bar chart 시리즈의 범위 확인
            val barChart = plotArea.barChartList[0]
            assertNotNull(barChart)

            // 모든 시리즈의 참조 출력 (디버깅)
            barChart.serList.forEachIndexed { idx, ser ->
                println("  Series $idx cat: ${ser.cat?.strRef?.f ?: ser.cat?.numRef?.f}")
                println("  Series $idx val: ${ser.`val`?.numRef?.f}")
            }

            // 카테고리 범위: 끝 행이 7이어야 한다 (5개 아이템 * 1행 템플릿 → 행 3~7)
            val catRef = barChart.serList[0].cat?.strRef?.f
                ?: barChart.serList[0].cat?.numRef?.f
            assertNotNull(catRef, "카테고리 참조가 존재해야 한다")
            assertEndRow(catRef!!, 7, "카테고리")

            // 값 범위: 끝 행이 7이어야 한다
            val valRef = barChart.serList[0].`val`?.numRef?.f
            assertNotNull(valRef, "값 참조가 존재해야 한다")
            assertEndRow(valRef!!, 7, "매출")

            // 앵커 위치 검증: repeat 확장(4행)에 따라 시프트되어야 한다
            // 원본 anchor: row1=5, row2=20 → repeat(row2, 5items, 1row) → expansion=4
            // 조정 후: row1=9, row2=24
            val anchor = drawing.first().anchor as XSSFClientAnchor
            assertEquals(9, anchor.row1, "차트 앵커 row1이 시프트되어야 한다 (5+4=9)")
            assertEquals(24, anchor.row2, "차트 앵커 row2가 시프트되어야 한다 (20+4=24)")
        }

        // 파일 저장 (육안 확인용)
        val outputPath = tempDir.resolve("chart_xssf_result.xlsx")
        outputPath.toFile().writeBytes(resultBytes)
    }

    @Test
    fun `SXSSF 모드 - repeat 확장 후 차트 범위가 모든 데이터를 포함한다`() {
        val templateBytes = createChartTemplate()
        val dataProvider = createTestData()

        val resultBytes = ExcelGenerator(TbegConfig(streamingMode = StreamingMode.ENABLED)).use {
            it.generate(ByteArrayInputStream(templateBytes), dataProvider)
        }

        // 결과를 XSSF로 다시 열어 차트 범위 확인
        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 데이터 확장 확인
            assertEquals("영업부", sheet.getRow(2)?.getCell(0)?.stringCellValue)
            assertEquals("재무부", sheet.getRow(6)?.getCell(0)?.stringCellValue)

            // 차트 범위 확인 - SXSSF 모드에서도 차트 XML이 조정되었는지
            val drawing = sheet.drawingPatriarch
            assertNotNull(drawing)
            assertTrue(drawing.charts.isNotEmpty(), "차트가 존재해야 한다")

            val chart = drawing.charts[0]
            val plotArea = chart.ctChart.plotArea
            val barChart = plotArea.barChartList[0]

            // 카테고리 범위 확장 확인
            val catRef = barChart.serList[0].cat?.strRef?.f
                ?: barChart.serList[0].cat?.numRef?.f
            assertNotNull(catRef, "카테고리 참조가 존재해야 한다")
            assertEndRow(catRef!!, 7, "카테고리")

            // 값 범위 확장 확인
            val valRef = barChart.serList[0].`val`?.numRef?.f
            assertNotNull(valRef, "값 참조가 존재해야 한다")
            assertEndRow(valRef!!, 7, "매출")

            // 앵커 위치 검증: SXSSF에서도 repeat 확장에 따라 시프트
            val anchor = drawing.first().anchor as XSSFClientAnchor
            assertEquals(9, anchor.row1, "차트 앵커 row1이 시프트되어야 한다 (5+4=9)")
            assertEquals(24, anchor.row2, "차트 앵커 row2가 시프트되어야 한다 (20+4=24)")
        }

        // 파일 저장 (육안 확인용)
        val outputPath = tempDir.resolve("chart_sxssf_result.xlsx")
        outputPath.toFile().writeBytes(resultBytes)
    }

    @Test
    fun `SXSSF 모드 - 결과 파일이 유효한 xlsx이다`() {
        val templateBytes = createChartTemplate()
        val dataProvider = createTestData()

        val resultBytes = ExcelGenerator(TbegConfig(streamingMode = StreamingMode.ENABLED)).use {
            it.generate(ByteArrayInputStream(templateBytes), dataProvider)
        }

        // 파일이 정상적으로 열리는지 확인
        assertDoesNotThrow {
            XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
                assertEquals(1, workbook.numberOfSheets)
                val sheet = workbook.getSheetAt(0)
                assertTrue(sheet.lastRowNum >= 6, "최소 7개 행이 있어야 한다 (제목+헤더+5데이터)")
            }
        }
    }

    @Test
    fun `SXSSF 모드 - drawing rels에 중복 rId가 없다`() {
        val templateBytes = createChartTemplate()
        val dataProvider = createTestData()

        val resultBytes = ExcelGenerator(TbegConfig(streamingMode = StreamingMode.ENABLED)).use {
            it.generate(ByteArrayInputStream(templateBytes), dataProvider)
        }

        // ZIP 내 drawing rels 파일에서 rId 중복 검사
        val ridPattern = Regex("""Id="(rId\d+)"""")
        ZipInputStream(ByteArrayInputStream(resultBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("drawings/_rels/")) {
                    val content = String(zis.readBytes(), Charsets.UTF_8)
                    val rids = ridPattern.findAll(content).map { it.groupValues[1] }.toList()
                    val duplicates = rids.groupBy { it }.filter { it.value.size > 1 }.keys
                    assertTrue(
                        duplicates.isEmpty(),
                        "drawing rels(${entry.name})에 중복 rId가 있다: $duplicates\n$content"
                    )
                }
                entry = zis.nextEntry
            }
        }
    }

    /** 차트 수식 참조의 끝 행이 expectedRow인지 검증 */
    private fun assertEndRow(ref: String, expectedRow: Int, label: String) {
        // 범위의 끝 부분에서 행 번호 추출 (예: Sheet1!$A$3:$A$7 → 7)
        val endRowPattern = Regex("""\$?[A-Z]+\$?(\d+)$""")
        val match = endRowPattern.find(ref)
        assertNotNull(match, "$label 참조에서 끝 행을 추출할 수 없다: $ref")
        assertEquals(expectedRow, match!!.groupValues[1].toInt(),
            "$label 끝 행이 $expectedRow 이어야 한다. 실제 참조: $ref")
    }

    @Test
    fun `XSSF 모드 - 아이템이 1개이면 차트 범위가 변경되지 않는다`() {
        val templateBytes = createChartTemplate()
        val singleItem = SimpleDataProvider.Builder()
            .value("title", "단일 항목 보고서")
            .items("departments", listOf(mapOf("name" to "영업부", "sales" to 1500, "cost" to 800)))
            .build()

        val resultBytes = ExcelGenerator(TbegConfig(streamingMode = StreamingMode.DISABLED)).use {
            it.generate(ByteArrayInputStream(templateBytes), singleItem)
        }

        XSSFWorkbook(ByteArrayInputStream(resultBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val drawing = sheet.drawingPatriarch
            val chart = drawing.charts[0]
            val barChart = chart.ctChart.plotArea.barChartList[0]

            val catRef = barChart.serList[0].cat?.strRef?.f
                ?: barChart.serList[0].cat?.numRef?.f
            assertNotNull(catRef)
            // 1개 아이템이므로 A3:A3 유지
            assertTrue(catRef!!.contains("\$A\$3") || catRef.contains("A3"),
                "카테고리가 A3이어야 한다: $catRef")
        }
    }
}
