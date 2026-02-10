package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.exception.FormulaExpansionException
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Excel Generator 샘플 실행 클래스.
 *
 * 여섯 가지 생성 방식을 시연합니다:
 * 1. 기본 사용 - Map 기반 간편 API
 * 2. 지연 로딩 - DataProvider를 통한 대용량 처리
 * 3. 비동기 실행 - 리스너 기반 백그라운드 처리
 * 4. 대용량 비동기 - DataProvider + 비동기 조합
 * 5. 암호화된 대용량 비동기 - 파일 열기 암호 설정
 * 6. 문서 메타데이터 - 제목, 작성자, 키워드 등 설정
 *
 * ## Spring Boot 환경에서 사용
 *
 * `excel-generator` 의존성을 추가하면 `ExcelGenerator`가 자동으로 Bean으로 등록됩니다.
 *
 * ```kotlin
 * // build.gradle.kts
 * implementation("com.hunet.common:excel-generator:1.0.0-SNAPSHOT")
 * ```
 *
 * ```kotlin
 * @Service
 * class ReportService(
 *     private val excelGenerator: ExcelGenerator,  // 자동 주입
 *     private val resourceLoader: ResourceLoader
 * ) {
 *     fun generateReport(data: Map<String, Any>): Path {
 *         val template = resourceLoader.getResource("classpath:templates/report.xlsx")
 *         return excelGenerator.generateToFile(
 *             template = template.inputStream,
 *             dataProvider = SimpleDataProvider.of(data),
 *             outputDir = Path.of("/output"),
 *             baseFileName = "report"
 *         )
 *     }
 * }
 * ```
 *
 * ### application.yml 설정 예시
 * ```yaml
 * hunet:
 *   excel:
 *     streaming-mode: auto
 *     streaming-row-threshold: 1000
 *     formula-processing: true
 *     timestamp-format: yyyyMMdd_HHmmss
 * ```
 */
object TbegSample {

    // 샘플 데이터 클래스
    data class Employee(val name: String, val position: String, val salary: Int)
    data class Department(val name: String, val members: Int, val office: String)

    @JvmStatic
    fun main(args: Array<String>) {
        val moduleDir = findModuleDir()
        val outputDir = moduleDir.resolve("build/samples")
        Files.createDirectories(outputDir)

        println("=" .repeat(60))
        println("Excel Generator 샘플 실행")
        println("=" .repeat(60))

        // Spring Boot 환경에서는 ExcelGenerator가 Bean으로 자동 주입됩니다.
        // 이 샘플은 테스트 목적으로 직접 인스턴스를 생성합니다.

        // ========== SXSSF (스트리밍) 모드 ==========
        println("\n" + "=" .repeat(60))
        println("SXSSF (스트리밍) 모드")
        println("=" .repeat(60))

        ExcelGenerator().use { generator ->
            // 1. 기본 사용 (Map 기반)
            runBasicExample(generator, outputDir, "basic_example_sxssf")

            // 2. 지연 로딩 (DataProvider)
            runLazyLoadingExample(generator, outputDir, "lazy_loading_example_sxssf")

            // 3. 비동기 실행 (Listener)
            runAsyncExample(generator, outputDir, "async_example_sxssf")

            // 4. 대용량 비동기 (DataProvider + Listener)
            runLargeAsyncExample(generator, outputDir, "large_async_example_sxssf")

            // 5. 암호화된 대용량 비동기 (암호: 1234)
            runEncryptedLargeAsyncExample(generator, outputDir)

            // 6. 문서 메타데이터 설정
            runMetadataExample(generator, outputDir)
        }

        // ========== XSSF (비스트리밍) 모드 ==========
        println("\n" + "=" .repeat(60))
        println("XSSF (비스트리밍) 모드")
        println("=" .repeat(60))

        val xssfConfig = TbegConfig(streamingMode = StreamingMode.DISABLED)
        ExcelGenerator(xssfConfig).use { generator ->
            // 1. 기본 사용 (Map 기반) - XSSF
            runBasicExample(generator, outputDir, "basic_example_xssf")

            // 2. 지연 로딩 (DataProvider) - XSSF
            runLazyLoadingExample(generator, outputDir, "lazy_loading_example_xssf")

            // 3. 대용량 비동기 (DataProvider + Listener) - XSSF
            runLargeAsyncExample(generator, outputDir, "large_async_example_xssf")
        }

        println("\n" + "=" .repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=" .repeat(60))
    }

    // ==================== 1. 기본 사용 ====================

    /**
     * Map 기반의 가장 간단한 사용 방법입니다.
     * 소량의 데이터를 빠르게 처리할 때 적합합니다.
     *
     * Spring Boot 예시:
     * ```kotlin
     * @Service
     * class BasicReportService(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val employeeRepository: EmployeeRepository
     * ) {
     *     fun generateBasicReport(): Path {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *         val data = mapOf(
     *             "title" to "보고서",
     *             "employees" to employeeRepository.findAll()
     *         )
     *         return excelGenerator.generateToFile(
     *             template = template.inputStream,
     *             dataProvider = SimpleDataProvider.of(data),
     *             outputDir = Path.of("/output"),
     *             baseFileName = "basic_report"
     *         )
     *     }
     * }
     * ```
     */
    private fun runBasicExample(
        generator: ExcelGenerator,
        outputDir: Path,
        baseFileName: String = "basic_example"
    ) {
        println("\n[1] 기본 사용 (Map 기반)")
        println("-" .repeat(40))

        // 데이터를 Map으로 준비
        val data = mapOf(
            "title" to "2026년 직원 현황",
            "date" to LocalDate.now().toString(),
            "secondTitle" to "부서별 현황",
            "linkText" to "(주)휴넷 홈페이지",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500),
                Employee("홍용호", "대리", 4500)
            ),
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
            "mergedEmployees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500),
                Employee("홍용호", "대리", 4500)
            ),
            "departments" to listOf(
                Department("공통플랫폼팀", 15, "814호"),
                Department("IT전략기획팀", 8, "801호"),
                Department("인재경영실", 5, "813호")
            ),
            // 빈 컬렉션 테스트 (emptyRange 기능)
            "emptyCollection" to emptyList<Employee>(),
            "logo" to loadImage("hunet_logo.png"),
            "ci" to loadImage("hunet_ci.png")
        ).filterValues { it != null }.mapValues { it.value!! }

        // 템플릿 로드 및 생성
        val template = loadTemplate()
        val resultPath = generator.generateToFile(
            template = template,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = outputDir,
            baseFileName = baseFileName
        )

        println("\t결과: $resultPath")
    }

    // ==================== 2. 지연 로딩 ====================

    /**
     * DataProvider를 사용한 지연 로딩 방식입니다.
     * 대용량 데이터를 스트리밍으로 처리할 때 적합합니다.
     *
     * 장점:
     * - 데이터를 한 번에 메모리에 올리지 않음
     * - DB 커서나 Iterator를 직접 연결 가능
     * - 메모리 효율적
     *
     * Spring Boot 예시:
     * ```kotlin
     * @Service
     * class LargeDataReportService(
     *     private val excelGenerator: ExcelGenerator,
     *     private val employeeRepository: EmployeeRepository,
     *     private val resourceLoader: ResourceLoader
     * ) {
     *     @Transactional(readOnly = true)
     *     fun generateLargeReport(): Path {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *         val provider = simpleDataProvider {
     *             value("title", "대용량 직원 현황")
     *             value("date", LocalDate.now().toString())
     *             items("employees") {
     *                 // JPA Stream으로 대용량 데이터 처리
     *                 employeeRepository.streamAll().iterator()
     *             }
     *         }
     *         return excelGenerator.generateToFile(
     *             template = template.inputStream,
     *             dataProvider = provider,
     *             outputDir = Path.of("/output"),
     *             baseFileName = "large_report"
     *         )
     *     }
     * }
     * ```
     */
    private fun runLazyLoadingExample(
        generator: ExcelGenerator,
        outputDir: Path,
        baseFileName: String = "lazy_loading_example"
    ) {
        println("\n[2] 지연 로딩 (DataProvider)")
        println("-" .repeat(40))

        // simpleDataProvider DSL 사용
        val dataProvider = simpleDataProvider {
            // 단순 값
            value("title", "2026년 직원 현황(대용량)")
            value("date", LocalDate.now().toString())
            value("secondTitle", "부서별 현황")
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")

            // 이미지
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            // 컬렉션 - 지연 로딩 (실제로는 DB 쿼리 등 사용)
            items("employees") {
                // 이 블록은 실제로 데이터가 필요할 때 호출됨
                // 실제 사용 시: repository.streamAll().iterator()
                generateLargeDataSet().iterator()
            }

            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            items("mergedEmployees") {
                generateLargeDataSet(100).iterator()
            }

            // 부서 컬렉션
            items("departments") {
                listOf(
                    Department("공통플랫폼팀", 15, "814호"),
                    Department("IT전략기획팀", 8, "801호"),
                    Department("인재경영실", 5, "813호")
                ).iterator()
            }

            // 빈 컬렉션 테스트 (emptyRange 기능)
            items("emptyCollection") { emptyList<Employee>().iterator() }
        }

        println("\tDataProvider 생성 완료 (데이터는 아직 로드되지 않음)")

        val template = loadTemplate()
        val resultPath = generator.generateToFile(
            template = template,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = baseFileName
        )

        println("\t결과: $resultPath")
    }

    /**
     * 대용량 데이터셋을 시뮬레이션합니다.
     * 실제 환경에서는 DB 스트리밍 쿼리 등으로 대체할 수 있습니다.
     */
    private fun generateLargeDataSet(count: Int = 100): Sequence<Employee> = sequence {
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

    // ==================== 3. 비동기 실행 ====================

    /**
     * 비동기 실행 방식입니다.
     * API 서버에서 즉시 응답 후 백그라운드 처리할 때 적합합니다.
     *
     * 사용 시나리오:
     * - REST API에서 Excel 생성 요청 받음
     * - 즉시 jobId 반환 (HTTP 202 Accepted)
     * - 백그라운드에서 생성 완료 후 알림 (이메일, 푸시 등)
     *
     * Spring Boot 예시:
     * ```kotlin
     * @RestController
     * class ReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val eventPublisher: ApplicationEventPublisher,
     *     private val resourceLoader: ResourceLoader
     * ) {
     *     @PostMapping("/reports/async")
     *     fun generateReportAsync(@RequestBody request: ReportRequest): ResponseEntity<JobResponse> {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *         val provider = SimpleDataProvider.of(request.toDataMap())
     *
     *         val job = excelGenerator.submitToFile(
     *             template = template.inputStream,
     *             dataProvider = provider,
     *             outputDir = Path.of("/output"),
     *             baseFileName = "async_report",
     *             listener = object : ExcelGenerationListener {
     *                 override fun onCompleted(jobId: String, result: GenerationResult) {
     *                     // 완료 이벤트 발행 (이메일 발송, 푸시 알림 등)
     *                     eventPublisher.publishEvent(ReportReadyEvent(jobId, result.filePath))
     *                 }
     *                 override fun onFailed(jobId: String, error: Exception) {
     *                     eventPublisher.publishEvent(ReportFailedEvent(jobId, error.message))
     *                 }
     *             }
     *         )
     *
     *         return ResponseEntity.accepted().body(JobResponse(job.jobId))
     *     }
     * }
     * ```
     */
    private fun runAsyncExample(
        generator: ExcelGenerator,
        outputDir: Path,
        baseFileName: String = "async_example"
    ) {
        println("\n[3] 비동기 실행 (Listener)")
        println("-".repeat(40))

        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null

        val data = mapOf(
            "title" to "2026년 직원 현황(비동기 생성)",
            "date" to LocalDate.now().toString(),
            "secondTitle" to "부서별 현황",
            "linkText" to "(주)휴넷 홈페이지",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500)
            ),
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
            "mergedEmployees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500)
            ),
            "departments" to listOf(
                Department("공통플랫폼팀", 15, "814호"),
                Department("IT전략기획팀", 8, "801호")
            ),
            "logo" to loadImage("hunet_logo.png"),
            "ci" to loadImage("hunet_ci.png")
        ).filterValues { it != null }.mapValues { it.value!! }

        val template = loadTemplate()

        // 비동기 작업 제출
        val job = generator.submitToFile(
            template = template,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = outputDir,
            baseFileName = baseFileName,
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("\t[시작] jobId: $jobId")
                }

                override fun onCompleted(jobId: String, result: GenerationResult) {
                    println("\t[완료] 소요시간: ${result.durationMs}ms")
                    println("\t[완료] 파일: ${result.filePath}")
                    generatedPath = result.filePath
                    completionLatch.countDown()
                }

                override fun onFailed(jobId: String, error: Exception) {
                    println("\t[실패] ${error.message}")
                    completionLatch.countDown()
                }

                override fun onCancelled(jobId: String) {
                    println("\t[취소됨]")
                    completionLatch.countDown()
                }
            }
        )

        // API 서버에서는 여기서 즉시 jobId를 반환
        println("\t작업 제출됨: ${job.jobId}")
        println("\t(API 서버에서는 여기서 HTTP 202 반환)")

        // 샘플에서는 완료 대기
        completionLatch.await(30, TimeUnit.SECONDS)

        if (generatedPath != null) {
            println("\t결과: $generatedPath")
        }
    }

    // ==================== 4. 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하는 방식입니다.
     * DataProvider의 지연 로딩과 비동기 실행을 조합하여 최적의 성능을 제공합니다.
     *
     * 사용 시나리오:
     * - 대용량 Excel 생성 요청 (수만~수십만 행)
     * - 메모리 효율적인 스트리밍 처리 필요
     * - API 서버에서 즉시 응답 후 백그라운드 처리
     *
     * Spring Boot 예시:
     * ```kotlin
     * @RestController
     * class LargeReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val employeeRepository: EmployeeRepository,
     *     private val eventPublisher: ApplicationEventPublisher,
     *     private val resourceLoader: ResourceLoader
     * ) {
     *     @PostMapping("/reports/large-async")
     *     @Transactional(readOnly = true)
     *     fun generateLargeReportAsync(): ResponseEntity<JobResponse> {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *
     *         // DataProvider로 지연 로딩 설정
     *         val provider = simpleDataProvider {
     *             value("title", "대용량 직원 현황")
     *             value("date", LocalDate.now().toString())
     *             items("employees") {
     *                 employeeRepository.streamAll().iterator()
     *             }
     *         }
     *
     *         // 비동기로 제출
     *         val job = excelGenerator.submitToFile(
     *             template = template.inputStream,
     *             dataProvider = provider,
     *             outputDir = Path.of("/output"),
     *             baseFileName = "large_report",
     *             listener = object : ExcelGenerationListener {
     *                 override fun onProgress(jobId: String, progress: ProgressInfo) {
     *                     // 진행률 로깅 또는 WebSocket으로 클라이언트에 전송
     *                     log.info("Progress: ${progress.percentage}%")
     *                 }
     *                 override fun onCompleted(jobId: String, result: GenerationResult) {
     *                     eventPublisher.publishEvent(
     *                         LargeReportReadyEvent(jobId, result.filePath, result.rowsProcessed)
     *                     )
     *                 }
     *                 override fun onFailed(jobId: String, error: Exception) {
     *                     eventPublisher.publishEvent(ReportFailedEvent(jobId, error.message))
     *                 }
     *             }
     *         )
     *
     *         return ResponseEntity.accepted().body(JobResponse(job.jobId))
     *     }
     * }
     * ```
     */
    private fun runLargeAsyncExample(
        generator: ExcelGenerator,
        outputDir: Path,
        baseFileName: String = "large_async_example"
    ) {
        println("\n[4] 대용량 비동기 (DataProvider + Listener)")
        println("-" .repeat(40))

        // 먼저 1000건으로 시도, 수식 확장 실패 시 255건으로 재시도
        val initialCount = 1000
        val retryCount = 255

        val result = runLargeAsyncWithRetry(
            generator = generator,
            outputDir = outputDir,
            baseFileName = baseFileName,
            dataCount = initialCount,
            retryDataCount = retryCount
        )

        if (result != null) {
            println("\t결과: ${result.first} (${result.second}건 처리)")
        }
    }

    /**
     * 대용량 비동기 생성을 시도하고, FormulaExpansionException 발생 시 재시도합니다.
     */
    private fun runLargeAsyncWithRetry(
        generator: ExcelGenerator,
        outputDir: Path,
        baseFileName: String,
        dataCount: Int,
        retryDataCount: Int
    ): Pair<Path, Int>? {
        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null
        var processedRows = 0
        var formulaError: FormulaExpansionException? = null

        // DataProvider로 대용량 데이터 지연 로딩 설정
        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황(대용량 비동기)")
            value("date", LocalDate.now().toString())
            value("secondTitle", "부서별 현황")
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            // 대용량 데이터 - 실제로는 DB 스트리밍 쿼리 사용
            items("employees") {
                generateLargeDataSet(dataCount).iterator()
            }

            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            items("mergedEmployees") {
                generateLargeDataSet(100).iterator()
            }

            // 부서 컬렉션
            items("departments") {
                listOf(
                    Department("공통플랫폼팀", 15, "814호"),
                    Department("IT전략기획팀", 8, "801호"),
                    Department("인재경영실", 5, "813호")
                ).iterator()
            }

            // 빈 컬렉션 테스트 (emptyRange 기능)
            items("emptyCollection") { emptyList<Employee>().iterator() }
        }

        println("\tDataProvider 생성 완료 (${dataCount}건 데이터 지연 로딩 예정)")

        val template = loadTemplate()

        // 비동기 작업 제출
        val job = generator.submitToFile(
            template = template,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = baseFileName,
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("\t[시작] jobId: $jobId")
                }

                override fun onCompleted(jobId: String, result: GenerationResult) {
                    println("\t[완료] 처리된 행: ${result.rowsProcessed}건")
                    println("\t[완료] 소요시간: ${result.durationMs}ms")
                    println("\t[완료] 파일: ${result.filePath}")
                    generatedPath = result.filePath
                    processedRows = result.rowsProcessed
                    completionLatch.countDown()
                }

                override fun onFailed(jobId: String, error: Exception) {
                    println("\t[실패] ${error.message}")
                    if (error is FormulaExpansionException) {
                        formulaError = error
                    }
                    completionLatch.countDown()
                }

                override fun onCancelled(jobId: String) {
                    println("\t[취소됨]")
                    completionLatch.countDown()
                }
            }
        )

        println("\t작업 제출됨: ${job.jobId}")
        println("\t(백그라운드에서 대용량 데이터 처리 중...)")

        // 샘플에서는 완료 대기
        completionLatch.await(60, TimeUnit.SECONDS)

        // FormulaExpansionException 발생 시 데이터 수를 줄여서 재시도
        if (formulaError != null && dataCount > retryDataCount) {
            println("\n\t⚠️ 수식 확장 실패로 인해 ${retryDataCount}건으로 재시도합니다...")
            return runLargeAsyncWithRetry(
                generator = generator,
                outputDir = outputDir,
                baseFileName = baseFileName,
                dataCount = retryDataCount,
                retryDataCount = retryDataCount  // 더 이상 재시도하지 않음
            )
        }

        return if (generatedPath != null) {
            generatedPath!! to processedRows
        } else {
            null
        }
    }

    // ==================== 5. 암호화된 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하면서 파일에 암호를 설정합니다.
     * 생성된 Excel 파일을 열 때 암호 입력이 필요합니다.
     *
     * Spring Boot 예시:
     * ```kotlin
     * @RestController
     * class SecureReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val employeeRepository: EmployeeRepository,
     *     private val resourceLoader: ResourceLoader
     * ) {
     *     @PostMapping("/reports/secure")
     *     @Transactional(readOnly = true)
     *     fun generateSecureReport(
     *         @RequestParam password: String
     *     ): ResponseEntity<JobResponse> {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *
     *         val provider = simpleDataProvider {
     *             value("title", "보안 직원 현황")
     *             items("employees") { employeeRepository.streamAll().iterator() }
     *         }
     *
     *         val job = excelGenerator.submitToFile(
     *             template = template.inputStream,
     *             dataProvider = provider,
     *             outputDir = Path.of("/output"),
     *             baseFileName = "secure_report",
     *             password = password,  // 파일 열기 암호 설정
     *             listener = object : ExcelGenerationListener {
     *                 override fun onCompleted(jobId: String, result: GenerationResult) {
     *                     // 암호화된 파일 생성 완료
     *                 }
     *             }
     *         )
     *
     *         return ResponseEntity.accepted().body(JobResponse(job.jobId))
     *     }
     * }
     * ```
     */
    private fun runEncryptedLargeAsyncExample(generator: ExcelGenerator, outputDir: Path) {
        println("\n[5] 암호화된 대용량 비동기 (암호: 1234)")
        println("-" .repeat(40))

        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null
        var processedRows = 0

        val dataCount = 255  // 수식 확장 이슈 방지를 위해 255건으로 제한

        // DataProvider로 대용량 데이터 지연 로딩 설정
        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황(암호화)")
            value("date", LocalDate.now().toString())
            value("secondTitle", "부서별 현황")
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            items("employees") {
                generateLargeDataSet(dataCount).iterator()
            }

            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            items("mergedEmployees") {
                generateLargeDataSet(100).iterator()
            }

            // 부서 컬렉션
            items("departments") {
                listOf(
                    Department("공통플랫폼팀", 15, "814호"),
                    Department("IT전략기획팀", 8, "801호"),
                    Department("인재경영실", 5, "813호")
                ).iterator()
            }
        }

        println("\tDataProvider 생성 완료 (${dataCount}건 데이터)")

        val template = loadTemplate()

        // 비동기 작업 제출 (암호 설정)
        val job = generator.submitToFile(
            template = template,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "encrypted_large_async_example",
            password = "1234",  // 파일 열기 암호 설정
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("\t[시작] jobId: $jobId")
                }

                override fun onCompleted(jobId: String, result: GenerationResult) {
                    println("\t[완료] 처리된 행: ${result.rowsProcessed}건")
                    println("\t[완료] 소요시간: ${result.durationMs}ms")
                    println("\t[완료] 파일: ${result.filePath}")
                    println("\t[완료] 파일 열기 암호: 1234")
                    generatedPath = result.filePath
                    processedRows = result.rowsProcessed
                    completionLatch.countDown()
                }

                override fun onFailed(jobId: String, error: Exception) {
                    println("\t[실패] ${error.message}")
                    completionLatch.countDown()
                }

                override fun onCancelled(jobId: String) {
                    println("\t[취소됨]")
                    completionLatch.countDown()
                }
            }
        )

        println("\t작업 제출됨: ${job.jobId}")
        println("\t(백그라운드에서 암호화된 파일 생성 중...)")

        // 샘플에서는 완료 대기
        completionLatch.await(60, TimeUnit.SECONDS)

        if (generatedPath != null) {
            println("\t결과: $generatedPath (${processedRows}건 처리, 암호: 1234)")
        }
    }

    // ==================== 6. 문서 메타데이터 설정 ====================

    /**
     * 문서 메타데이터를 설정하는 방식입니다.
     * Excel 파일의 속성(제목, 작성자, 키워드 등)을 설정할 수 있습니다.
     *
     * 메타데이터는 Excel에서 "파일 > 정보 > 속성"에서 확인할 수 있습니다.
     *
     * Spring Boot 예시:
     * ```kotlin
     * @Service
     * class MetadataReportService(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader
     * ) {
     *     fun generateReportWithMetadata(): Path {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *
     *         val provider = simpleDataProvider {
     *             value("title", "보고서")
     *             items("employees") { employeeRepository.findAll().iterator() }
     *
     *             // 문서 메타데이터 설정
     *             metadata {
     *                 title = "월간 실적 보고서"
     *                 author = "황용호"
     *                 subject = "2026년 1월 실적"
     *                 keywords("실적", "월간", "보고서")
     *                 description = "2026년 1월 월간 실적 보고서입니다."
     *                 category = "업무 보고"
     *                 company = "(주)휴넷"
     *                 manager = "황상무"
     *             }
     *         }
     *
     *         return excelGenerator.generateToFile(
     *             template = template.inputStream,
     *             dataProvider = provider,
     *             outputDir = Path.of("/output"),
     *             baseFileName = "monthly_report"
     *         )
     *     }
     * }
     * ```
     *
     * Java에서 사용하는 경우:
     * ```java
     * var provider = SimpleDataProvider.builder()
     *     .value("title", "보고서")
     *     .metadata(meta -> meta
     *         .title("월간 실적 보고서")
     *         .author("황용호")
     *         .company("(주)휴넷"))
     *     .build();
     * ```
     */
    private fun runMetadataExample(generator: ExcelGenerator, outputDir: Path) {
        println("\n[6] 문서 메타데이터 설정")
        println("-" .repeat(40))

        // simpleDataProvider DSL 사용
        val dataProvider = simpleDataProvider {
            // 단순 값
            value("title", "2026년 직원 현황(메타데이터 포함)")
            value("date", LocalDate.now().toString())
            value("secondTitle", "부서별 현황")
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")

            // 이미지
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            // 컬렉션
            items("employees") {
                listOf(
                    Employee("황용호", "부장", 8000),
                    Employee("한용호", "과장", 6500),
                    Employee("홍용호", "대리", 4500)
                ).iterator()
            }

            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
            items("mergedEmployees") {
                listOf(
                    Employee("황용호", "부장", 8000),
                    Employee("한용호", "과장", 6500),
                    Employee("홍용호", "대리", 4500)
                ).iterator()
            }

            // 부서 컬렉션
            items("departments") {
                listOf(
                    Department("공통플랫폼팀", 15, "814호"),
                    Department("IT전략기획팀", 8, "801호"),
                    Department("인재경영실", 5, "813호")
                ).iterator()
            }

            // 문서 메타데이터 설정
            metadata {
                title = "2026년 직원 현황 보고서"
                author = "황용호"
                subject = "직원 현황"
                keywords("직원", "현황", "2026년", "보고서")
                description = "2026년도 직원 현황을 정리한 보고서입니다."
                category = "인사 보고"
                company = "(주)휴넷"
                manager = "황상무"
            }
        }

        println("\t문서 메타데이터:")
        println("\t  - 제목: 2026년 직원 현황 보고서")
        println("\t  - 작성자: 황용호")
        println("\t  - 회사: (주)휴넷")

        val template = loadTemplate()
        val resultPath = generator.generateToFile(
            template = template,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "metadata_example"
        )

        println("\t결과: $resultPath")
        println("\t(Excel에서 \"파일 > 정보 > 속성\"에서 메타데이터 확인 가능)")
    }

    // ==================== 유틸리티 메서드 ====================

    private fun loadTemplate() =
        TbegSample::class.java.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿 파일을 찾을 수 없습니다: /templates/template.xlsx")

    private fun loadImage(fileName: String): ByteArray? =
        TbegSample::class.java.getResourceAsStream("/$fileName")?.use { it.readBytes() }

    private fun findModuleDir(): Path {
        val classLocation = TbegSample::class.java.protectionDomain.codeSource?.location
        if (classLocation != null) {
            val classPath = Path.of(classLocation.toURI())
            var current = classPath
            while (current.parent != null) {
                if (current.fileName?.toString() == "build" &&
                    Files.exists(current.resolveSibling("src"))
                ) {
                    return current.parent
                }
                current = current.parent
            }
        }

        val workingDir = Path.of("").toAbsolutePath()
        if (Files.exists(workingDir.resolve("src/main/kotlin/com/hunet/common/excel"))) {
            return workingDir
        }

        val moduleFromRoot = workingDir.resolve("modules/core/excel-generator")
        if (Files.exists(moduleFromRoot)) {
            return moduleFromRoot
        }

        return workingDir
    }
}
