package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * XSSF vs SXSSF 처리 속도 벤치마크 및 결과 동일성 검증
 */
class PerformanceBenchmarkTest {

    private fun loadSimpleTemplate(): InputStream =
        javaClass.getResourceAsStream("/templates/simple_template.xlsx")
            ?: throw IllegalStateException("simple_template.xlsx를 찾을 수 없습니다")

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `benchmark XSSF vs SXSSF - all sizes`() {
        val sizes = listOf(100, 1_000, 10_000)
        val warmupCount = 1
        val measureCount = 3

        println("=".repeat(70))
        println("XSSF vs SXSSF 벤치마크")
        println("=".repeat(70))

        val results = mutableListOf<BenchmarkResult>()

        for (rowCount in sizes) {
            println("\n[$rowCount 행]")
            val result = runBenchmark(rowCount, warmupCount, measureCount)
            results.add(result)
        }

        // 최종 요약
        println("\n" + "=".repeat(70))
        println("최종 결과 요약")
        println("=".repeat(70))
        println("| 데이터 크기 | XSSF (disabled) | SXSSF (enabled) | 속도 차이 |")
        println("|------------|-----------------|-----------------|-----------|")
        for (result in results) {
            println("| ${result.rowCount}행 | ${result.xssfAvg.toLong()}ms | ${result.sxssfAvg.toLong()}ms | **${String.format("%.1f", result.ratio)}배** |")
        }
        println("=".repeat(70))
    }

    private fun runBenchmark(rowCount: Int, warmupCount: Int, measureCount: Int): BenchmarkResult {
        // 데이터 준비
        val employees = (1..rowCount).map { i ->
            mapOf(
                "name" to "직원$i",
                "position" to listOf("사원", "대리", "과장", "차장", "부장")[i % 5],
                "salary" to (3000 + (i % 5) * 1000)
            )
        }

        fun createDataProvider() = object : ExcelDataProvider {
            override fun getValue(name: String): Any? = when (name) {
                "title" -> "성능 벤치마크 테스트"
                "date" -> "2026-01-30"
                "linkText" -> "링크"
                "url" -> "https://example.com"
                else -> null
            }

            override fun getItems(name: String): Iterator<Any>? = when (name) {
                "employees" -> employees.iterator()
                else -> null
            }

            override fun getItemCount(name: String): Int? = when (name) {
                "employees" -> rowCount
                else -> null
            }
        }

        // XSSF 벤치마크
        print("  XSSF: ")
        val xssfConfig = TbegConfig(streamingMode = StreamingMode.DISABLED)
        val xssfTimes = mutableListOf<Long>()
        var xssfResult: ByteArray? = null

        ExcelGenerator(xssfConfig).use { generator ->
            repeat(warmupCount) {
                generator.generate(loadSimpleTemplate(), createDataProvider())
            }
            repeat(measureCount) {
                val time = measureTimeMillis {
                    xssfResult = generator.generate(loadSimpleTemplate(), createDataProvider())
                }
                xssfTimes.add(time)
            }
        }
        print("${xssfTimes.average().toLong()}ms | ")

        // SXSSF 벤치마크
        print("SXSSF: ")
        val sxssfConfig = TbegConfig(streamingMode = StreamingMode.ENABLED)
        val sxssfTimes = mutableListOf<Long>()
        var sxssfResult: ByteArray? = null

        ExcelGenerator(sxssfConfig).use { generator ->
            repeat(warmupCount) {
                generator.generate(loadSimpleTemplate(), createDataProvider())
            }
            repeat(measureCount) {
                val time = measureTimeMillis {
                    sxssfResult = generator.generate(loadSimpleTemplate(), createDataProvider())
                }
                sxssfTimes.add(time)
            }
        }
        print("${sxssfTimes.average().toLong()}ms | ")

        // 10,000건일 때 파일로 저장
        if (rowCount == 10_000) {
            val outputDir = File("build/samples").apply { mkdirs() }
            File(outputDir, "xssf_10000.xlsx").writeBytes(xssfResult!!)
            File(outputDir, "sxssf_10000.xlsx").writeBytes(sxssfResult!!)
            println("파일 저장: build/samples/")
        }

        // 결과 동일성 검증
        assertExcelFilesEqual(xssfResult!!, sxssfResult!!, rowCount)
        println("✓ 동일")

        val xssfAvg = xssfTimes.average()
        val sxssfAvg = sxssfTimes.average()
        return BenchmarkResult(rowCount, xssfAvg, sxssfAvg, xssfAvg / sxssfAvg)
    }

    data class BenchmarkResult(
        val rowCount: Int,
        val xssfAvg: Double,
        val sxssfAvg: Double,
        val ratio: Double
    )

    private fun assertExcelFilesEqual(bytes1: ByteArray, bytes2: ByteArray, rowCount: Int) {
        XSSFWorkbook(ByteArrayInputStream(bytes1)).use { wb1 ->
            XSSFWorkbook(ByteArrayInputStream(bytes2)).use { wb2 ->
                assertEquals(wb1.numberOfSheets, wb2.numberOfSheets, "[${rowCount}행] 시트 수가 다름")

                for (sheetIdx in 0 until wb1.numberOfSheets) {
                    val sheet1 = wb1.getSheetAt(sheetIdx)
                    val sheet2 = wb2.getSheetAt(sheetIdx)

                    assertEquals(sheet1.sheetName, sheet2.sheetName, "[${rowCount}행] 시트[$sheetIdx] 이름이 다름")

                    val maxRowNum = maxOf(sheet1.lastRowNum, sheet2.lastRowNum)
                    for (rowIdx in 0..maxRowNum) {
                        val row1 = sheet1.getRow(rowIdx)
                        val row2 = sheet2.getRow(rowIdx)

                        if (row1 == null && row2 == null) continue

                        val isRow1Empty = row1 == null || isRowEmpty(row1)
                        val isRow2Empty = row2 == null || isRowEmpty(row2)
                        if (isRow1Empty && isRow2Empty) continue

                        if (isRow1Empty || isRow2Empty) {
                            fail<Unit>("[${rowCount}행] 시트[${sheet1.sheetName}] 행[$rowIdx] - 하나만 빈 행")
                        }

                        val lastCellNum = maxOf(row1!!.lastCellNum.toInt(), row2!!.lastCellNum.toInt())
                        for (colIdx in 0 until lastCellNum) {
                            val cell1 = row1.getCell(colIdx)
                            val cell2 = row2.getCell(colIdx)

                            val cellRef = "${('A' + colIdx)}${rowIdx + 1}"

                            val value1 = cell1?.let { getCellValueAsString(it) } ?: ""
                            val value2 = cell2?.let { getCellValueAsString(it) } ?: ""

                            if (value1.isEmpty() && value2.isEmpty()) continue

                            assertEquals(
                                value1,
                                value2,
                                "[${rowCount}행] 시트[${sheet1.sheetName}] 셀[$cellRef] 값이 다름"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isRowEmpty(row: org.apache.poi.ss.usermodel.Row): Boolean {
        for (colIdx in 0 until row.lastCellNum) {
            val cell = row.getCell(colIdx) ?: continue
            val value = getCellValueAsString(cell)
            if (value.isNotEmpty()) return false
        }
        return true
    }

    private fun getCellValueAsString(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> "FORMULA:${cell.cellFormula}"
            CellType.BLANK -> ""
            CellType.ERROR -> "ERROR:${cell.errorCellValue}"
            else -> ""
        }
    }
}
