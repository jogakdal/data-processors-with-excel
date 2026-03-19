package io.github.jogakdal.tbeg.benchmark

import com.sun.management.OperatingSystemMXBean
import org.openjdk.jmh.infra.BenchmarkParams
import org.openjdk.jmh.infra.IterationParams
import org.openjdk.jmh.profile.InternalProfiler
import org.openjdk.jmh.results.AggregationPolicy
import org.openjdk.jmh.results.IterationResult
import org.openjdk.jmh.results.Result
import org.openjdk.jmh.results.ScalarResult
import java.lang.management.ManagementFactory

/**
 * JMH 커스텀 프로파일러: 프로세스 CPU 사용률 측정.
 *
 * 두 가지 지표를 보고한다:
 * - **코어 당 사용률**: `프로세스 CPU 시간 / wall-clock 시간 × 100` (100% = 1코어 풀 사용)
 * - **시스템 전체 사용률**: `프로세스 CPU 시간 / (wall-clock 시간 × 코어 수) × 100`
 */
class CpuTimeProfiler : InternalProfiler {

    private var startCpuTimeNs = 0L
    private var startWallTimeNs = 0L

    override fun getDescription() = "Process CPU usage profiler"

    override fun beforeIteration(benchmarkParams: BenchmarkParams, iterationParams: IterationParams) {
        startCpuTimeNs = processCpuTimeNs()
        startWallTimeNs = System.nanoTime()
    }

    override fun afterIteration(
        benchmarkParams: BenchmarkParams,
        iterationParams: IterationParams,
        result: IterationResult
    ): Collection<Result<*>> {
        val cpuTimeNs = processCpuTimeNs() - startCpuTimeNs
        val wallTimeNs = System.nanoTime() - startWallTimeNs
        val cores = Runtime.getRuntime().availableProcessors()

        val perCore = if (wallTimeNs > 0) cpuTimeNs.toDouble() / wallTimeNs * 100.0 else 0.0
        val system = if (wallTimeNs > 0) cpuTimeNs.toDouble() / wallTimeNs / cores * 100.0 else 0.0

        return listOf(
            ScalarResult("cpu.per.core", perCore, "%", AggregationPolicy.AVG),
            ScalarResult("cpu.system", system, "%", AggregationPolicy.AVG)
        )
    }

    private fun processCpuTimeNs(): Long =
        (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).processCpuTime
}
