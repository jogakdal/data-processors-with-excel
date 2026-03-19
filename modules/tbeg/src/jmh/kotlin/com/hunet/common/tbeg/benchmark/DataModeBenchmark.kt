package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.ExcelGenerator
import org.openjdk.jmh.annotations.*
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * 벤치마크 1: 데이터 제공 방식 비교 (Map vs DataProvider)
 *
 * - 고정: generate() 출력 (ByteArray 반환)
 * - 변수: Map vs DataProvider x 1K/10K/30K/50K/100K
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Suppress("unused")
open class DataModeBenchmark {

    @Param("1000", "10000", "30000", "50000", "100000")
    open var rowCount: Int = 0

    private lateinit var templateBytes: ByteArray
    private lateinit var generator: ExcelGenerator

    @Setup(Level.Trial)
    fun setup() {
        templateBytes = BenchmarkSupport.createMinimalTemplate()
        generator = ExcelGenerator()
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        generator.close()
    }

    @Benchmark
    fun map(): ByteArray =
        generator.generate(
            ByteArrayInputStream(templateBytes),
            BenchmarkSupport.createMapData(rowCount)
        )

    @Benchmark
    fun dataProvider(): ByteArray =
        generator.generate(
            ByteArrayInputStream(templateBytes),
            BenchmarkSupport.createDataProvider(rowCount)
        )
}
