package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelGenerator
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * 벤치마크 3: 대용량 스케일 (DataProvider + generateToFile)
 *
 * - 고정: DataProvider + generateToFile
 * - 변수: 100K/200K/300K/500K/1M
 * - -Xmx8g 필요 (100만 행 처리)
 *
 * ZipStreamPostProcessor가 XSSFWorkbook 전체 로드 없이 ZIP 스트리밍으로
 * 후처리하므로 POI의 레코드 크기 제한(100MB)에 걸리지 않는다.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xms512m", "-Xmx8g"])
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Suppress("unused")
open class LargeScaleBenchmark {

    @Param("100000", "200000", "300000", "500000", "1000000")
    open var rowCount: Int = 0

    private lateinit var templateBytes: ByteArray
    private lateinit var generator: ExcelGenerator
    private lateinit var outputDir: Path

    @Setup(Level.Trial)
    fun setup() {
        templateBytes = BenchmarkSupport.createMinimalTemplate()
        generator = ExcelGenerator()
        outputDir = Files.createTempDirectory("tbeg-bench-large")
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        generator.close()
        outputDir.toFile().deleteRecursively()
    }

    @Benchmark
    fun generateToFile() {
        generator.generateToFile(
            template = ByteArrayInputStream(templateBytes),
            dataProvider = BenchmarkSupport.createDataProvider(rowCount),
            outputDir = outputDir,
            baseFileName = "bench_large_${rowCount}"
        )
    }
}
