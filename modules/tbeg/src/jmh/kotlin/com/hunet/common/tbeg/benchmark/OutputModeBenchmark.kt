package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelGenerator
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * 벤치마크 2: 출력 방식 비교 (generate vs generateToStream vs generateToFile)
 *
 * - 고정: DataProvider 데이터 제공
 * - 변수: 3가지 출력 방식 x 1K/10K/30K/50K/100K
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Suppress("unused")
open class OutputModeBenchmark {

    @Param("1000", "10000", "30000", "50000", "100000")
    open var rowCount: Int = 0

    private lateinit var templateBytes: ByteArray
    private lateinit var generator: ExcelGenerator
    private lateinit var outputDir: Path

    @Setup(Level.Trial)
    fun setup() {
        templateBytes = BenchmarkSupport.createMinimalTemplate()
        generator = ExcelGenerator()
        outputDir = Files.createTempDirectory("tbeg-bench-output")
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        generator.close()
        outputDir.toFile().deleteRecursively()
    }

    @Benchmark
    fun generate(): ByteArray =
        generator.generate(
            ByteArrayInputStream(templateBytes),
            BenchmarkSupport.createDataProvider(rowCount)
        )

    @Benchmark
    fun generateToStream() {
        ByteArrayOutputStream().use { out ->
            generator.generateToStream(
                ByteArrayInputStream(templateBytes),
                BenchmarkSupport.createDataProvider(rowCount),
                out
            )
        }
    }

    @Benchmark
    fun generateToFile() {
        generator.generateToFile(
            template = ByteArrayInputStream(templateBytes),
            dataProvider = BenchmarkSupport.createDataProvider(rowCount),
            outputDir = outputDir,
            baseFileName = "bench_${rowCount}"
        )
    }
}
