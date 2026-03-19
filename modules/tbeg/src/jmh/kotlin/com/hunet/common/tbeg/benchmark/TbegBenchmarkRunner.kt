package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.benchmark.BenchmarkSupport.GcMetrics
import io.github.jogakdal.tbeg.benchmark.BenchmarkSupport.bytesToMb
import io.github.jogakdal.tbeg.benchmark.BenchmarkSupport.formatMs
import org.openjdk.jmh.results.RunResult
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder

/**
 * TBEG 벤치마크 커스텀 러너
 *
 * 3가지 벤치마크를 순차 실행하고 정리된 테이블로 출력한다.
 */
object TbegBenchmarkRunner {

    private const val CPU_PER_CORE = "cpu.per.core"
    private const val CPU_SYSTEM = "cpu.system"

    @JvmStatic
    fun main(args: Array<String>) {
        println("================================================================")
        println("TBEG 벤치마크 결과")
        println("================================================================")
        printEnvironment()

        runDataModeBenchmark()
        runOutputModeBenchmark()
        runLargeScaleBenchmark()

        println("================================================================")
    }

    private fun printEnvironment() {
        val runtime = Runtime.getRuntime()
        val os = System.getProperty("os.name")
        val arch = System.getProperty("os.arch")
        val javaVersion = System.getProperty("java.version")
        val vmName = System.getProperty("java.vm.name")
        val cores = runtime.availableProcessors()
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        println()
        println("테스트 환경:")
        println("  OS: $os ($arch)")
        println("  JVM: $vmName $javaVersion")
        println("  CPU 코어: ${cores}개")
        println("  최대 힙: ${maxMemMb}MB")
        println("  JMH: fork=1, warmup=1, iterations=3")
    }

    private fun runDataModeBenchmark() {
        println("\n[1] 데이터 제공 방식 비교 (출력: generate)")
        println()

        val options = OptionsBuilder()
            .include(DataModeBenchmark::class.java.simpleName)
            .forks(1)
            .warmupIterations(1)
            .measurementIterations(3)
            .jvmArgs("-Xms512m", "-Xmx4g")
            .addProfiler("gc")
            .addProfiler(CpuTimeProfiler::class.java)
            .build()

        val results = Runner(options).run()
        printDataModeTable(results)
    }

    private fun runOutputModeBenchmark() {
        println("\n[2] 출력 방식 비교 (데이터: DataProvider)")
        println()

        val options = OptionsBuilder()
            .include(OutputModeBenchmark::class.java.simpleName)
            .forks(1)
            .warmupIterations(1)
            .measurementIterations(3)
            .jvmArgs("-Xms512m", "-Xmx4g")
            .addProfiler("gc")
            .addProfiler(CpuTimeProfiler::class.java)
            .build()

        val results = Runner(options).run()
        printOutputModeTable(results)
    }

    private fun runLargeScaleBenchmark() {
        println("\n[3] 대용량 스케일 (DataProvider + generateToFile)")
        println()

        val options = OptionsBuilder()
            .include(LargeScaleBenchmark::class.java.simpleName)
            .forks(1)
            .warmupIterations(1)
            .measurementIterations(3)
            .jvmArgs("-Xms512m", "-Xmx8g")
            .addProfiler("gc")
            .addProfiler(CpuTimeProfiler::class.java)
            .build()

        val results = Runner(options).run()
        printLargeScaleTable(results)
    }

    private fun printDataModeTable(results: Collection<RunResult>) {
        println("| 데이터 크기    | 방식         | 소요 시간   | CPU/전체 | CPU/코어 | 힙 할당량    | GC 횟수 | GC 시간  |")
        println("|------------|------------|---------|--------|--------|----------|-------|--------|")

        val sorted = results.sortedWith(compareBy(
            { it.paramValue("rowCount").toInt() },
            { it.methodName() }
        ))

        for (result in sorted) {
            val rowCount = result.paramValue("rowCount")
            val method = when (result.methodName()) {
                "map" -> "Map"
                "dataProvider" -> "DataProvider"
                else -> result.methodName()
            }
            println(
                "| %10s | %-10s | %7s | %6s | %6s | %8s | %5s | %6s |".format(
                    formatRowCount(rowCount),
                    method,
                    formatMs(result.primaryResult.score),
                    formatPercent(result.metric(CPU_PER_CORE)),
                    formatPercent(result.metric(CPU_SYSTEM)),
                    bytesToMb(result.metric(GcMetrics.ALLOC_RATE)),
                    result.metric(GcMetrics.GC_COUNT).toLong().toString(),
                    formatMs(result.metric(GcMetrics.GC_TIME))
                )
            )
        }
    }

    private fun printOutputModeTable(results: Collection<RunResult>) {
        println("| 데이터 크기    | 출력 방식          | 소요 시간   | CPU/전체 | CPU/코어 | 힙 할당량    | GC 횟수 | GC 시간  |")
        println("|------------|----------------|---------|--------|--------|----------|-------|--------|")

        val sorted = results.sortedWith(compareBy(
            { it.paramValue("rowCount").toInt() },
            { it.methodName() }
        ))

        for (result in sorted) {
            val rowCount = result.paramValue("rowCount")
            val method = when (result.methodName()) {
                "generate" -> "generate"
                "generateToStream" -> "generateToStream"
                "generateToFile" -> "generateToFile"
                else -> result.methodName()
            }
            println(
                "| %10s | %-14s | %7s | %6s | %6s | %8s | %5s | %6s |".format(
                    formatRowCount(rowCount),
                    method,
                    formatMs(result.primaryResult.score),
                    formatPercent(result.metric(CPU_PER_CORE)),
                    formatPercent(result.metric(CPU_SYSTEM)),
                    bytesToMb(result.metric(GcMetrics.ALLOC_RATE)),
                    result.metric(GcMetrics.GC_COUNT).toLong().toString(),
                    formatMs(result.metric(GcMetrics.GC_TIME))
                )
            )
        }
    }

    private fun printLargeScaleTable(results: Collection<RunResult>) {
        println("| 데이터 크기      | 소요 시간    | CPU/전체 | CPU/코어 | 힙 할당량      | GC 횟수 | GC 시간  |")
        println("|--------------|---------|--------|--------|-----------|-------|--------|")

        val sorted = results.sortedBy { it.paramValue("rowCount").toInt() }

        for (result in sorted) {
            val rowCount = result.paramValue("rowCount")
            println(
                "| %12s | %7s | %6s | %6s | %9s | %5s | %6s |".format(
                    formatRowCount(rowCount),
                    formatMs(result.primaryResult.score),
                    formatPercent(result.metric(CPU_PER_CORE)),
                    formatPercent(result.metric(CPU_SYSTEM)),
                    bytesToMb(result.metric(GcMetrics.ALLOC_RATE)),
                    result.metric(GcMetrics.GC_COUNT).toLong().toString(),
                    formatMs(result.metric(GcMetrics.GC_TIME))
                )
            )
        }
    }

    // --- 유틸리티 ---

    private fun RunResult.paramValue(name: String): String =
        params.getParam(name)

    private fun RunResult.methodName(): String =
        primaryResult.label.substringAfterLast('.')

    private fun RunResult.metric(key: String): Double =
        secondaryResults[key]?.score ?: 0.0

    private fun formatRowCount(value: String): String =
        String.format("%,d행", value.toInt())

    private fun formatPercent(value: Double): String =
        String.format("%.1f%%", value)
}
