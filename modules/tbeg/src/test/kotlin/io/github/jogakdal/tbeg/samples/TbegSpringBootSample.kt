package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.async.ProgressInfo
import io.github.jogakdal.tbeg.simpleDataProvider
import io.github.jogakdal.tbeg.spring.TbegAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Excel Generator Spring Boot 환경 샘플.
 *
 * Spring Boot 환경에서 ExcelGenerator를 사용하는 6가지 방식을 시연합니다:
 * 1. 기본 사용 - Map 기반 간편 API
 * 2. 지연 로딩 - DataProvider를 통한 대용량 처리
 * 3. 비동기 실행 - 리스너 기반 백그라운드 처리
 * 4. 대용량 비동기 - DataProvider + 비동기 조합
 * 5. 암호화된 대용량 비동기 - 파일 열기 암호 설정
 * 6. 문서 메타데이터 - 제목, 작성자, 키워드 등 설정
 *
 * ## 실행 방법
 * ```bash
 * ./gradlew :tbeg:runSpringBootSample
 * ```
 *
 * ## Spring Boot 설정
 * `application.yml`에서 ExcelGenerator 설정을 커스터마이징할 수 있습니다:
 * ```yaml
 * hunet:
 *   excel:
 *     streaming-mode: auto
 *     streaming-row-threshold: 1000
 *     formula-processing: true
 *     timestamp-format: yyyyMMdd_HHmmss
 * ```
 */
@SpringBootTest(classes = [TbegSpringBootSample.TestApplication::class])
class TbegSpringBootSample {

    /**
     * 테스트용 Spring Boot 애플리케이션.
     * TbegAutoConfiguration이 자동으로 활성화됩니다.
     */
    @SpringBootApplication
    class TestApplication

    // 샘플 데이터 클래스
    data class Employee(val name: String, val position: String, val salary: Int)

    /**
     * Spring Boot가 자동으로 ExcelGenerator Bean을 주입합니다.
     * TbegAutoConfiguration에 의해 Bean이 생성됩니다.
     */
    @Autowired
    lateinit var excelGenerator: ExcelGenerator

    /**
     * 템플릿 파일 로딩을 위한 ResourceLoader.
     */
    @Autowired
    lateinit var resourceLoader: ResourceLoader

    /**
     * 모든 샘플을 순차적으로 실행합니다.
     */
    fun runAllSamples() {
        val outputDir = createOutputDirectory()

        println("=" .repeat(60))
        println("Excel Generator Spring Boot 샘플 실행")
        println("=" .repeat(60))

        // 1. 기본 사용 (Map 기반)
        runBasicExample(outputDir)

        // 2. 지연 로딩 (DataProvider)
        runLazyLoadingExample(outputDir)

        // 3. 비동기 실행 (Listener)
        runAsyncExample(outputDir)

        // 4. 대용량 비동기 (DataProvider + Listener)
        runLargeAsyncExample(outputDir)

        // 5. 암호화된 대용량 비동기 (암호: 1234)
        runEncryptedLargeAsyncExample(outputDir)

        // 6. 문서 메타데이터 설정
        runMetadataExample(outputDir)

        println("\n" + "=" .repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=" .repeat(60))
    }

    // ==================== 1. 기본 사용 ====================

    /**
     * Map 기반의 가장 간단한 사용 방법입니다.
     *
     * Spring Boot 환경에서는:
     * - ExcelGenerator가 자동으로 주입됩니다
     * - ResourceLoader로 classpath 리소스를 로드합니다
     *
     * 실제 서비스 코드 예시:
     * ```kotlin
     * @Service
     * class ReportService(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val employeeRepository: EmployeeRepository
     * ) {
     *     fun generateReport(): Path {
     *         val template = resourceLoader.getResource("classpath:templates/report.xlsx")
     *         val data = mapOf(
     *             "title" to "직원 현황",
     *             "employees" to employeeRepository.findAll()
     *         )
     *         return excelGenerator.generateToFile(
     *             template = template.inputStream,
     *             dataProvider = SimpleDataProvider.of(data),
     *             outputDir = Path.of("/output"),
     *             baseFileName = "report"
     *         )
     *     }
     * }
     * ```
     */
    fun runBasicExample(outputDir: Path) {
        println("\n[1] 기본 사용 (Map 기반) - Spring Boot")
        println("-" .repeat(40))

        // ResourceLoader를 사용하여 classpath에서 템플릿 로드
        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")

        // 데이터를 Map으로 준비
        val data = mapOf(
            "title" to "2026년 직원 현황 (Spring Boot)",
            "date" to LocalDate.now().toString(),
            "linkText" to "(주)휴넷 홈페이지",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500),
                Employee("홍용호", "대리", 4500)
            ),
            "logo" to loadImage("hunet_logo.png"),
            "ci" to loadImage("hunet_ci.png")
        ).filterValues { it != null }.mapValues { it.value!! }

        // Excel 생성 - excelGenerator는 Spring이 자동 주입
        val resultPath = excelGenerator.generateToFile(
            template = templateResource.inputStream,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = outputDir,
            baseFileName = "spring_basic_example"
        )

        println("\t결과: $resultPath")
    }

    // ==================== 2. 지연 로딩 ====================

    /**
     * DataProvider를 사용한 지연 로딩 방식입니다.
     *
     * 장점:
     * - 데이터를 한 번에 메모리에 올리지 않음
     * - DB 커서나 Iterator를 직접 연결 가능
     * - 메모리 효율적
     *
     * 실제 서비스 코드 예시:
     * ```kotlin
     * @Service
     * class LargeReportService(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val employeeRepository: EmployeeRepository
     * ) {
     *     @Transactional(readOnly = true)
     *     fun generateLargeReport(): Path {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *         val provider = simpleDataProvider {
     *             value("title", "대용량 직원 현황")
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
    fun runLazyLoadingExample(outputDir: Path) {
        println("\n[2] 지연 로딩 (DataProvider) - Spring Boot")
        println("-" .repeat(40))

        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")

        // simpleDataProvider DSL 사용
        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황 (대용량, Spring Boot)")
            value("date", LocalDate.now().toString())
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            // 컬렉션 - 지연 로딩 (실제로는 DB 쿼리 등 사용)
            items("employees") {
                generateLargeDataSet(100).iterator()
            }
        }

        println("\tDataProvider 생성 완료 (데이터는 아직 로드되지 않음)")

        val resultPath = excelGenerator.generateToFile(
            template = templateResource.inputStream,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "spring_lazy_loading_example"
        )

        println("\t결과: $resultPath")
    }

    // ==================== 3. 비동기 실행 ====================

    /**
     * 비동기 실행 방식입니다.
     *
     * 사용 시나리오:
     * - REST API에서 Excel 생성 요청 받음
     * - 즉시 jobId 반환 (HTTP 202 Accepted)
     * - 백그라운드에서 생성 완료 후 알림 (이메일, 푸시 등)
     *
     * 실제 컨트롤러 코드 예시:
     * ```kotlin
     * @RestController
     * class ReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val eventPublisher: ApplicationEventPublisher
     * ) {
     *     @PostMapping("/reports/async")
     *     fun generateReportAsync(@RequestBody request: ReportRequest): ResponseEntity<JobResponse> {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *
     *         val job = excelGenerator.submitToFile(
     *             template = template.inputStream,
     *             dataProvider = SimpleDataProvider.of(request.toDataMap()),
     *             outputDir = Path.of("/output"),
     *             baseFileName = "async_report",
     *             listener = object : ExcelGenerationListener {
     *                 override fun onCompleted(jobId: String, result: GenerationResult) {
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
    fun runAsyncExample(outputDir: Path) {
        println("\n[3] 비동기 실행 (Listener) - Spring Boot")
        println("-" .repeat(40))

        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null

        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")

        val data = mapOf(
            "title" to "2026년 직원 현황 (비동기, Spring Boot)",
            "date" to LocalDate.now().toString(),
            "linkText" to "(주)휴넷 홈페이지",
            "url" to "https://www.hunet.co.kr",
            "employees" to listOf(
                Employee("황용호", "부장", 8000),
                Employee("한용호", "과장", 6500)
            ),
            "logo" to loadImage("hunet_logo.png"),
            "ci" to loadImage("hunet_ci.png")
        ).filterValues { it != null }.mapValues { it.value!! }

        // 비동기 작업 제출
        val job = excelGenerator.submitToFile(
            template = templateResource.inputStream,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = outputDir,
            baseFileName = "spring_async_example",
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

        println("\t작업 제출됨: ${job.jobId}")
        println("\t(실제 API에서는 여기서 HTTP 202 반환)")

        completionLatch.await(30, TimeUnit.SECONDS)

        if (generatedPath != null) {
            println("\t결과: $generatedPath")
        }
    }

    // ==================== 4. 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하는 방식입니다.
     *
     * DataProvider의 지연 로딩과 비동기 실행을 조합하여 최적의 성능을 제공합니다.
     *
     * 실제 컨트롤러 코드 예시:
     * ```kotlin
     * @RestController
     * class LargeReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val employeeRepository: EmployeeRepository,
     *     private val eventPublisher: ApplicationEventPublisher
     * ) {
     *     @PostMapping("/reports/large-async")
     *     @Transactional(readOnly = true)
     *     fun generateLargeReportAsync(): ResponseEntity<JobResponse> {
     *         val template = resourceLoader.getResource("classpath:templates/template.xlsx")
     *
     *         val provider = simpleDataProvider {
     *             value("title", "대용량 직원 현황")
     *             items("employees") {
     *                 employeeRepository.streamAll().iterator()
     *             }
     *         }
     *
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
     *                     eventPublisher.publishEvent(ReportReadyEvent(jobId, result.filePath))
     *                 }
     *             }
     *         )
     *
     *         return ResponseEntity.accepted().body(JobResponse(job.jobId))
     *     }
     * }
     * ```
     */
    fun runLargeAsyncExample(outputDir: Path) {
        println("\n[4] 대용량 비동기 (DataProvider + Listener) - Spring Boot")
        println("-" .repeat(40))

        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null
        var processedRows = 0

        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")
        val dataCount = 255 // 수식 확장 이슈 방지

        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황 (대용량 비동기, Spring Boot)")
            value("date", LocalDate.now().toString())
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())
            items("employees") {
                generateLargeDataSet(dataCount).iterator()
            }
        }

        println("\tDataProvider 생성 완료 (${dataCount}건 데이터 지연 로딩 예정)")

        val job = excelGenerator.submitToFile(
            template = templateResource.inputStream,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "spring_large_async_example",
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("\t[시작] jobId: $jobId")
                }

                override fun onProgress(jobId: String, progress: ProgressInfo) {
                    // 진행률 로깅 (실제로는 WebSocket 등으로 전송 가능)
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

        completionLatch.await(60, TimeUnit.SECONDS)

        if (generatedPath != null) {
            println("\t결과: $generatedPath (${processedRows}건 처리)")
        }
    }

    // ==================== 5. 암호화된 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하면서 파일에 암호를 설정합니다.
     *
     * 실제 컨트롤러 코드 예시:
     * ```kotlin
     * @RestController
     * class SecureReportController(
     *     private val excelGenerator: ExcelGenerator,
     *     private val resourceLoader: ResourceLoader,
     *     private val employeeRepository: EmployeeRepository
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
    fun runEncryptedLargeAsyncExample(outputDir: Path) {
        println("\n[5] 암호화된 대용량 비동기 (암호: 1234) - Spring Boot")
        println("-" .repeat(40))

        val completionLatch = CountDownLatch(1)
        var generatedPath: Path? = null
        var processedRows = 0
        val password = "1234"

        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")
        val dataCount = 255

        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황 (암호화, Spring Boot)")
            value("date", LocalDate.now().toString())
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())
            items("employees") {
                generateLargeDataSet(dataCount).iterator()
            }
        }

        println("\tDataProvider 생성 완료 (${dataCount}건 데이터)")

        val job = excelGenerator.submitToFile(
            template = templateResource.inputStream,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "spring_encrypted_large_async_example",
            password = password,  // 파일 열기 암호 설정
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("\t[시작] jobId: $jobId")
                }

                override fun onCompleted(jobId: String, result: GenerationResult) {
                    println("\t[완료] 처리된 행: ${result.rowsProcessed}건")
                    println("\t[완료] 소요시간: ${result.durationMs}ms")
                    println("\t[완료] 파일: ${result.filePath}")
                    println("\t[완료] 파일 열기 암호: $password")
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

        completionLatch.await(60, TimeUnit.SECONDS)

        if (generatedPath != null) {
            println("\t결과: $generatedPath (${processedRows}건 처리, 암호: $password)")
        }
    }

    // ==================== 6. 문서 메타데이터 설정 ====================

    /**
     * 문서 메타데이터를 설정하는 방식입니다.
     * Excel 파일의 속성(제목, 작성자, 키워드 등)을 설정할 수 있습니다.
     *
     * 메타데이터는 Excel에서 "파일 > 정보 > 속성"에서 확인할 수 있습니다.
     *
     * 실제 서비스 코드 예시:
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
     */
    fun runMetadataExample(outputDir: Path) {
        println("\n[6] 문서 메타데이터 설정 - Spring Boot")
        println("-" .repeat(40))

        val templateResource = resourceLoader.getResource("classpath:templates/template.xlsx")

        val dataProvider = simpleDataProvider {
            value("title", "2026년 직원 현황 (메타데이터 포함, Spring Boot)")
            value("date", LocalDate.now().toString())
            value("linkText", "(주)휴넷 홈페이지")
            value("url", "https://www.hunet.co.kr")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            items("employees") {
                listOf(
                    Employee("황용호", "부장", 8000),
                    Employee("한용호", "과장", 6500),
                    Employee("홍용호", "대리", 4500)
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

        val resultPath = excelGenerator.generateToFile(
            template = templateResource.inputStream,
            dataProvider = dataProvider,
            outputDir = outputDir,
            baseFileName = "spring_metadata_example"
        )

        println("\t결과: $resultPath")
        println("\t(Excel에서 \"파일 > 정보 > 속성\"에서 메타데이터 확인 가능)")
    }

    // ==================== 유틸리티 메서드 ====================

    private fun createOutputDirectory(): Path {
        val outputDir = findModuleDir().resolve("build/samples-spring")
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir)
        }
        return outputDir
    }

    private fun findModuleDir(): Path {
        val classLocation = TbegSpringBootSample::class.java.protectionDomain.codeSource?.location
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

        val moduleFromRoot = workingDir.resolve("modules/core/tbeg")
        if (Files.exists(moduleFromRoot)) {
            return moduleFromRoot
        }

        return workingDir
    }

    private fun loadImage(fileName: String): ByteArray? =
        try {
            resourceLoader.getResource("classpath:$fileName").inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }

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

    companion object {
        /**
         * 샘플 실행 진입점.
         *
         * Spring Boot 테스트 컨텍스트를 사용하여 샘플을 실행합니다.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            // Spring Boot 테스트 컨텍스트 없이 직접 실행하는 경우
            // ApplicationContextRunner를 사용합니다.
            org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        TbegAutoConfiguration::class.java
                    )
                )
                .withBean(org.springframework.core.io.DefaultResourceLoader::class.java)
                .run { context ->
                    val sample = TbegSpringBootSample()
                    sample.excelGenerator = context.getBean(ExcelGenerator::class.java)
                    sample.resourceLoader = org.springframework.core.io.DefaultResourceLoader(
                        TbegSpringBootSample::class.java.classLoader
                    )
                    sample.runAllSamples()
                    sample.excelGenerator.close()
                }
        }
    }
}
