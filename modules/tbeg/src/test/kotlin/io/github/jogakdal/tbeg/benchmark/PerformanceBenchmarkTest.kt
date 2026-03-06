package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.ExcelGenerator
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
 * 처리 속도 벤치마크
 */
class PerformanceBenchmarkTest {

    private fun loadSimpleTemplate(): InputStream =
        javaClass.getResourceAsStream("/templates/simple_template.xlsx")
            ?: throw IllegalStateException("simple_template.xlsx를 찾을 수 없습니다")

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `benchmark performance - all sizes`() {
        val sizes = listOf(100, 1_000, 10_000)
        val warmupCount = 1
        val measureCount = 3

        println("=".repeat(70))
        println("벤치마크")
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
        println("| 데이터 크기 | 평균 소요 시간 |")
        println("|------------|---------------|")
        for (result in results) {
            println("| ${result.rowCount}행 | ${result.avg.toLong()}ms |")
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

        // 벤치마크
        print("  측정: ")
        val times = mutableListOf<Long>()
        var result: ByteArray? = null

        ExcelGenerator().use { generator ->
            repeat(warmupCount) {
                generator.generate(loadSimpleTemplate(), createDataProvider())
            }
            repeat(measureCount) {
                val time = measureTimeMillis {
                    result = generator.generate(loadSimpleTemplate(), createDataProvider())
                }
                times.add(time)
            }
        }
        println("${times.average().toLong()}ms")

        // 10,000건일 때 파일로 저장
        if (rowCount == 10_000) {
            val outputDir = File("build/samples").apply { mkdirs() }
            File(outputDir, "benchmark_10000.xlsx").writeBytes(result!!)
            println("파일 저장: build/samples/")
        }

        // 결과 검증
        assertResultValid(result!!, rowCount)

        return BenchmarkResult(rowCount, times.average())
    }

    data class BenchmarkResult(
        val rowCount: Int,
        val avg: Double
    )

    private fun assertResultValid(bytes: ByteArray, rowCount: Int) {
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            assertTrue(wb.numberOfSheets > 0, "[${rowCount}행] 시트가 존재해야 한다")
            val sheet = wb.getSheetAt(0)
            assertTrue(sheet.lastRowNum >= rowCount, "[${rowCount}행] 데이터 행이 충분해야 한다")
        }
    }
}
