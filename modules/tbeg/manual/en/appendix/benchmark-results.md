> **[한국어](../../ko/appendix/benchmark-results.md)** | English

# Performance Benchmark Detailed Results

Detailed performance benchmark results for TBEG, measured using JMH (Java Microbenchmark Harness).

## Test Environment

| Item | Value |
|:----:|:-----:|
| OS | macOS (aarch64) |
| JVM | OpenJDK 64-Bit Server VM 21.0.1 |
| CPU | Apple Silicon, 12 cores |
| Max Heap | 8,192MB |
| JMH Settings | fork=1, warmup=1 iteration, measurement=3 iterations |
| Workload | 3 column repeat + SUM formula |

## Metric Descriptions

| Metric | Description |
|:------:|:------------|
| **Duration** | Average wall-clock elapsed time (ms) |
| **CPU/Total** | Process total CPU time / wall-clock time (100% = 1 core fully utilized). May exceed 100% since CPU usage from parallel threads such as GC is included |
| **CPU/Core** | Process usage relative to total system CPU capacity (divided by core count). An indicator of how report generation affects other services on the server |
| **Heap Allocation** | Total heap memory allocation per operation (including amounts reclaimed by GC) |
| **GC Count** | Number of GC events during the iteration |
| **GC Time** | Total time spent on GC during the iteration |

---

## 1. Data Source Comparison

Compares performance differences between Map (full in-memory load) and DataProvider (Iterator-based lazy loading). Output method is fixed to `generate()` (ByteArray return).

| Data Size | Method | Duration | CPU/Total | CPU/Core | Heap Allocation | GC Count | GC Time |
|----------:|:------:|---------:|----------:|---------:|----------------:|---------:|--------:|
| 1,000 rows | DataProvider | 20ms | 282% | 23.5% | 11.8MB | 6 | 23ms |
| 1,000 rows | Map | 20ms | 268% | 22.3% | 11.8MB | 6 | 24ms |
| 10,000 rows | DataProvider | 109ms | 177% | 14.7% | 58.5MB | 5 | 15ms |
| 10,000 rows | Map | 113ms | 174% | 14.5% | 58.5MB | 5 | 16ms |
| 30,000 rows | DataProvider | 315ms | 151% | 12.5% | 166.0MB | 5 | 13ms |
| 30,000 rows | Map | 312ms | 152% | 12.7% | 166.5MB | 5 | 14ms |
| 50,000 rows | DataProvider | 505ms | 137% | 11.4% | 270.1MB | 6 | 15ms |
| 50,000 rows | Map | 506ms | 137% | 11.4% | 271.6MB | 6 | 17ms |
| 100,000 rows | DataProvider | 993ms | 130% | 10.8% | 540.8MB | 7 | 16ms |
| 100,000 rows | Map | 977ms | 132% | 11.0% | 534.5MB | 7 | 20ms |

**Analysis**: The performance difference between Map and DataProvider is negligible. The advantage of DataProvider lies not in performance but in **peak memory reduction**, as the streaming approach eliminates the need to load all data into memory at once.

---

## 2. Output Method Comparison

Compares performance differences between `generate()` (ByteArray return), `generateToStream()` (OutputStream), and `generateToFile()` (file save). Data method is fixed to DataProvider.

| Data Size | Output Method | Duration | CPU/Total | CPU/Core | Heap Allocation | GC Count | GC Time |
|----------:|:-------------:|---------:|----------:|---------:|----------------:|---------:|--------:|
| 1,000 rows | generate | 20ms | 268% | 22.4% | 11.8MB | 6 | 21ms |
| 1,000 rows | generateToFile | 20ms | 270% | 22.5% | 11.8MB | 6 | 24ms |
| 1,000 rows | generateToStream | 20ms | 269% | 22.4% | 11.9MB | 6 | 23ms |
| 10,000 rows | generate | 115ms | 170% | 14.2% | 59.8MB | 5 | 16ms |
| 10,000 rows | generateToFile | 113ms | 176% | 14.7% | 58.2MB | 5 | 15ms |
| 10,000 rows | generateToStream | 109ms | 171% | 14.2% | 58.4MB | 5 | 15ms |
| 30,000 rows | generate | 310ms | 151% | 12.5% | 168.0MB | 5 | 13ms |
| 30,000 rows | generateToFile | 302ms | 145% | 12.1% | 164.7MB | 6 | 18ms |
| 30,000 rows | generateToStream | 298ms | 144% | 12.0% | 163.3MB | 5 | 14ms |
| 50,000 rows | generate | 502ms | 140% | 11.6% | 275.3MB | 6 | 16ms |
| 50,000 rows | generateToFile | 486ms | 140% | 11.7% | 268.8MB | 6 | 16ms |
| 50,000 rows | generateToStream | 509ms | 143% | 11.9% | 272.2MB | 6 | 16ms |
| 100,000 rows | generate | 965ms | 130% | 10.8% | 540.1MB | 7 | 18ms |
| 100,000 rows | generateToFile | 969ms | 129% | 10.8% | 539.5MB | 7 | 17ms |
| 100,000 rows | generateToStream | 975ms | 129% | 10.8% | 540.6MB | 7 | 19ms |

**Analysis**: There is no significant performance difference between the three output methods. Due to the pipeline nature where the result is loaded into memory before output, the overhead from the output method is negligible.

---

## 3. Large-Scale Processing

Measures large-scale processing performance for 100K to 1M rows using the DataProvider + `generateToFile()` combination.

| Data Size | Duration | CPU/Total | CPU/Core | Heap Allocation | GC Count | GC Time |
|----------:|---------:|----------:|---------:|----------------:|---------:|---------:|
| 100,000 rows | 1,010ms | 129% | 10.7% | 548.2MB | 7 | 8ms |
| 200,000 rows | 1,901ms | 117% | 9.8% | 1,056.7MB | 7 | 18ms |
| 300,000 rows | 2,764ms | 111% | 9.3% | 1,590.9MB | 6 | 15ms |
| 500,000 rows | 4,718ms | 106% | 8.9% | 2,614.5MB | 19 | 13ms |
| 1,000,000 rows | 8,952ms | 105% | 8.8% | 5,230.7MB | 45 | 39ms |

**Analysis**:

- **Duration**: Scales linearly with row count. Approximately 0.9 seconds per 100K rows, processing 1 million rows in about 9 seconds.
- **CPU/Core**: Converges to **below 10%** as data grows. Even when processing 1 million rows, only 8.8% of the system CPU is used, meaning minimal impact on other services running on the server.
- **CPU/Total**: Converges toward 100% at larger scales, indicating nearly pure single-threaded computation. Higher values at smaller scales are due to initialization overhead from JIT compilation, class loading, and parallel GC threads.
- **Heap Allocation**: Scales linearly with row count. While streaming rendering (SXSSF, 100-row buffer) keeps memory constant at render time, the resulting ZIP file is held in memory, causing total allocation to increase proportionally.

> For **templates containing pivot tables**, approximately 300K rows is the practical upper limit since the pivot recreation process loads the entire output file into memory. This limitation does not apply to reports without pivot tables.

---

## 4. Comparison with Other Libraries (30,000 rows)

| Library | Duration | Notes |
|:-------:|---------:|:-----:|
| **TBEG** | **0.3s** | |
| JXLS | 5.2s | [Benchmark source](https://github.com/jxlsteam/jxls/discussions/203) |

This difference is likely because TBEG directly calls POI APIs and writes via single-pass streaming, whereas JXLS performs multi-pass processing through an abstraction layer: template parsing -> transformation -> writing.
