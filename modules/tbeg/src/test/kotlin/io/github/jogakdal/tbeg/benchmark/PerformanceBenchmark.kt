package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.simpleDataProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * TBEG 성능 벤치마크
 *
 * JXLS 벤치마크 참고: 30,000행 ~5.2초
 * https://github.com/jxlsteam/jxls/discussions/203
 */
object PerformanceBenchmark {

    data class Employee(val name: String, val position: String, val salary: Int)

    @JvmStatic
    fun main(args: Array<String>) {
        val outputDir = Files.createTempDirectory("tbeg-benchmark")

        println("=" .repeat(60))
        println("TBEG 성능 벤치마크")
        println("=" .repeat(60))

        // 벤치마크용 미니멀 템플릿 생성
        val templateBytes = createMinimalTemplate()
        println("템플릿 생성 완료 (단순 DOWN repeat만 포함)")

        // 워밍업
        println("\n[워밍업] 100행 생성...")
        ExcelGenerator().use { generator ->
            runBenchmark(generator, templateBytes, outputDir, 100, warmup = true)
        }

        // 벤치마크 실행
        val rowCounts = listOf(1_000, 10_000, 30_000, 50_000, 100_000)

        println("\n" + "=" .repeat(60))
        println("SXSSF (스트리밍) 모드")
        println("=" .repeat(60))

        ExcelGenerator().use { generator ->
            for (count in rowCounts) {
                runBenchmark(generator, templateBytes, outputDir, count)
            }
        }

        println("\n" + "=" .repeat(60))
        println("XSSF (비스트리밍) 모드 - 메모리 제한으로 10,000행까지만")
        println("=" .repeat(60))

        val xssfConfig = TbegConfig(streamingMode = StreamingMode.DISABLED)
        ExcelGenerator(xssfConfig).use { generator ->
            for (count in rowCounts.filter { it <= 10_000 }) {
                runBenchmark(generator, templateBytes, outputDir, count)
            }
        }

        // 정리
        outputDir.toFile().deleteRecursively()

        println("\n" + "=" .repeat(60))
        println("벤치마크 완료")
        println("=" .repeat(60))
    }

    /**
     * 벤치마크용 미니멀 템플릿 생성
     * - 단순 DOWN 방향 repeat만 포함
     * - 셀 병합, RIGHT repeat, 피벗 테이블 없음
     */
    private fun createMinimalTemplate(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Data")

        // Row 0: 제목
        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("\${title}")

        // Row 1: Repeat 마커
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${repeat(employees, A3:C3, emp)}")

        // Row 2: 데이터 행 (템플릿)
        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${emp.name}")
        row2.createCell(1).setCellValue("\${emp.position}")
        row2.createCell(2).setCellValue("\${emp.salary}")

        // Row 3: 합계 수식
        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("합계")
        row3.createCell(2).setCellFormula("SUM(C3:C3)")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    private fun runBenchmark(
        generator: ExcelGenerator,
        templateBytes: ByteArray,
        outputDir: Path,
        rowCount: Int,
        warmup: Boolean = false
    ) {
        val dataProvider = simpleDataProvider {
            value("title", "성능 벤치마크 (${rowCount}행)")

            items("employees", rowCount) {
                generateData(rowCount).iterator()
            }
        }

        val elapsed = measureTimeMillis {
            generator.generateToFile(
                template = ByteArrayInputStream(templateBytes),
                dataProvider = dataProvider,
                outputDir = outputDir,
                baseFileName = "benchmark_${rowCount}"
            )
        }

        if (!warmup) {
            val rowsPerSecond = (rowCount * 1000.0 / elapsed).toInt()
            println(String.format(
                "%,7d행: %,6dms (%,d rows/sec)",
                rowCount, elapsed, rowsPerSecond
            ))
        }
    }

    private fun generateData(count: Int): Sequence<Employee> = sequence {
        val positions = listOf("사원", "대리", "과장", "차장", "부장")
        val names = listOf("황", "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")

        repeat(count) { i ->
            yield(Employee(
                name = "${names[i % names.size]}용호${i + 1}",
                position = positions[i % positions.size],
                salary = 3000 + (i % 5) * 1000
            ))
        }
    }
}
