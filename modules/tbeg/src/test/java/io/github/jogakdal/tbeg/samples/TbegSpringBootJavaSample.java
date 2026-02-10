package io.github.jogakdal.tbeg.samples;

import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import io.github.jogakdal.tbeg.async.ExcelGenerationListener;
import io.github.jogakdal.tbeg.async.GenerationJob;
import io.github.jogakdal.tbeg.async.GenerationResult;
import io.github.jogakdal.tbeg.async.ProgressInfo;
import io.github.jogakdal.tbeg.spring.TbegAutoConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Excel Generator Spring Boot 환경 Java 샘플.
 *
 * <p>Spring Boot 환경에서 ExcelGenerator를 사용하는 6가지 방식을 시연합니다:
 * <ol>
 *   <li>기본 사용 - Map 기반 간편 API</li>
 *   <li>지연 로딩 - DataProvider를 통한 대용량 처리</li>
 *   <li>비동기 실행 - 리스너 기반 백그라운드 처리</li>
 *   <li>대용량 비동기 - DataProvider + 비동기 조합</li>
 *   <li>암호화된 대용량 비동기 - 파일 열기 암호 설정</li>
 *   <li>문서 메타데이터 - 제목, 작성자, 키워드 등 설정</li>
 * </ol>
 *
 * <h2>실행 방법</h2>
 * <pre>{@code
 * ./gradlew :tbeg:runSpringBootJavaSample
 * }</pre>
 *
 * <h2>Spring Boot 설정</h2>
 * <pre>{@code
 * # application.yml
 * hunet:
 *   excel:
 *     streaming-mode: auto
 *     streaming-row-threshold: 1000
 *     formula-processing: true
 *     timestamp-format: yyyyMMdd_HHmmss
 * }</pre>
 */
@SpringBootTest(classes = TbegSpringBootJavaSample.TestApplication.class)
public class TbegSpringBootJavaSample {

    /**
     * 테스트용 Spring Boot 애플리케이션.
     * TbegAutoConfiguration이 자동으로 활성화됩니다.
     */
    @SpringBootApplication
    static class TestApplication {
    }

    /**
     * 샘플 데이터 클래스 (Employee).
     * JXLS가 리플렉션으로 getter를 사용하므로 JavaBean 규칙을 따르는 클래스로 정의합니다.
     * (Java record는 name() 형태의 접근자를 사용하여 JXLS와 호환되지 않음)
     */
    public static class Employee {
        private final String name;
        private final String position;
        private final int salary;

        public Employee(String name, String position, int salary) {
            this.name = name;
            this.position = position;
            this.salary = salary;
        }

        public String getName() { return name; }
        public String getPosition() { return position; }
        public int getSalary() { return salary; }
    }

    /**
     * Spring Boot가 자동으로 ExcelGenerator Bean을 주입합니다.
     */
    @Autowired
    ExcelGenerator excelGenerator;

    /**
     * 템플릿 파일 로딩을 위한 ResourceLoader.
     */
    @Autowired
    ResourceLoader resourceLoader;

    /**
     * 모든 샘플을 순차적으로 실행합니다.
     */
    public void runAllSamples() throws Exception {
        Path outputDir = createOutputDirectory();

        System.out.println("=".repeat(60));
        System.out.println("Excel Generator Spring Boot Java 샘플 실행");
        System.out.println("=".repeat(60));

        // 1. 기본 사용 (Map 기반)
        runBasicExample(outputDir);

        // 2. 지연 로딩 (DataProvider)
        runLazyLoadingExample(outputDir);

        // 3. 비동기 실행 (Listener)
        runAsyncExample(outputDir);

        // 4. 대용량 비동기 (DataProvider + Listener)
        runLargeAsyncExample(outputDir);

        // 5. 암호화된 대용량 비동기 (암호: 1234)
        runEncryptedLargeAsyncExample(outputDir);

        // 6. 문서 메타데이터 설정
        runMetadataExample(outputDir);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("샘플 폴더: " + outputDir.toAbsolutePath());
        System.out.println("=".repeat(60));
    }

    // ==================== 1. 기본 사용 ====================

    /**
     * Map 기반의 가장 간단한 사용 방법입니다.
     *
     * <p>Spring Boot 환경에서는:
     * <ul>
     *   <li>ExcelGenerator가 자동으로 주입됩니다</li>
     *   <li>ResourceLoader로 classpath 리소스를 로드합니다</li>
     * </ul>
     *
     * <p>실제 서비스 코드 예시:
     * <pre>{@code
     * @Service
     * public class ReportService {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *     private final EmployeeRepository employeeRepository;
     *
     *     public Path generateReport() throws IOException {
     *         Resource template = resourceLoader.getResource("classpath:templates/report.xlsx");
     *         Map<String, Object> data = Map.of(
     *             "title", "직원 현황",
     *             "employees", employeeRepository.findAll()
     *         );
     *         return excelGenerator.generateToFile(
     *             template.getInputStream(),
     *             SimpleDataProvider.of(data),
     *             Path.of("/output"),
     *             "report"
     *         );
     *     }
     * }
     * }</pre>
     */
    private void runBasicExample(Path outputDir) throws IOException {
        System.out.println("\n[1] 기본 사용 (Map 기반) - Spring Boot Java");
        System.out.println("-".repeat(40));

        // ResourceLoader를 사용하여 classpath에서 템플릿 로드
        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");

        // 데이터를 Map으로 준비
        Map<String, Object> data = new HashMap<>();
        data.put("title", "2026년 직원 현황 (Spring Boot Java)");
        data.put("date", LocalDate.now().toString());
        data.put("linkText", "(주)휴넷 홈페이지");
        data.put("url", "https://www.hunet.co.kr");
        data.put("employees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500),
            new Employee("홍용호", "대리", 4500)
        ));

        // 이미지 추가
        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");
        if (logo != null) data.put("logo", logo);
        if (ci != null) data.put("ci", ci);

        // Excel 생성 - excelGenerator는 Spring이 자동 주입
        Path resultPath = excelGenerator.generateToFile(
            templateResource.getInputStream(),
            SimpleDataProvider.of(data),
            outputDir,
            "spring_basic_example_java"
        );

        System.out.println("\t결과: " + resultPath);
    }

    // ==================== 2. 지연 로딩 ====================

    /**
     * DataProvider Builder를 사용한 지연 로딩 방식입니다.
     *
     * <p>장점:
     * <ul>
     *   <li>데이터를 한 번에 메모리에 올리지 않음</li>
     *   <li>DB 커서나 Iterator를 직접 연결 가능</li>
     *   <li>메모리 효율적</li>
     * </ul>
     *
     * <p>실제 서비스 코드 예시:
     * <pre>{@code
     * @Service
     * public class LargeReportService {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *     private final EmployeeRepository employeeRepository;
     *
     *     @Transactional(readOnly = true)
     *     public Path generateLargeReport() throws IOException {
     *         Resource template = resourceLoader.getResource("classpath:templates/template.xlsx");
     *         SimpleDataProvider provider = SimpleDataProvider.builder()
     *             .value("title", "대용량 직원 현황")
     *             .itemsFromSupplier("employees", () -> employeeRepository.streamAll().iterator())
     *             .build();
     *         return excelGenerator.generateToFile(
     *             template.getInputStream(),
     *             provider,
     *             Path.of("/output"),
     *             "large_report"
     *         );
     *     }
     * }
     * }</pre>
     */
    private void runLazyLoadingExample(Path outputDir) throws IOException {
        System.out.println("\n[2] 지연 로딩 (DataProvider) - Spring Boot Java");
        System.out.println("-".repeat(40));

        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        // Java에서는 Builder 패턴 사용
        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황 (대용량, Spring Boot Java)")
            .value("date", LocalDate.now().toString())
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            // 컬렉션 - 지연 로딩 (Java Supplier 사용)
            .itemsFromSupplier("employees", () -> generateLargeDataSet(100))
            .build();

        System.out.println("\tDataProvider 생성 완료 (데이터는 아직 로드되지 않음)");

        Path resultPath = excelGenerator.generateToFile(
            templateResource.getInputStream(),
            dataProvider,
            outputDir,
            "spring_lazy_loading_example_java"
        );

        System.out.println("\t결과: " + resultPath);
    }

    // ==================== 3. 비동기 실행 ====================

    /**
     * 비동기 실행 방식입니다.
     *
     * <p>사용 시나리오:
     * <ul>
     *   <li>REST API에서 Excel 생성 요청 받음</li>
     *   <li>즉시 jobId 반환 (HTTP 202 Accepted)</li>
     *   <li>백그라운드에서 생성 완료 후 알림 (이메일, 푸시 등)</li>
     * </ul>
     *
     * <p>실제 컨트롤러 코드 예시:
     * <pre>{@code
     * @RestController
     * public class ReportController {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *     private final ApplicationEventPublisher eventPublisher;
     *
     *     @PostMapping("/reports/async")
     *     public ResponseEntity<JobResponse> generateReportAsync(@RequestBody ReportRequest request) {
     *         Resource template = resourceLoader.getResource("classpath:templates/template.xlsx");
     *
     *         GenerationJob job = excelGenerator.submitToFile(
     *             template.getInputStream(),
     *             SimpleDataProvider.of(request.toDataMap()),
     *             Path.of("/output"),
     *             "async_report",
     *             null,
     *             new ExcelGenerationListener() {
     *                 public void onCompleted(String jobId, GenerationResult result) {
     *                     eventPublisher.publishEvent(new ReportReadyEvent(jobId, result.getFilePath()));
     *                 }
     *                 public void onFailed(String jobId, Exception error) {
     *                     eventPublisher.publishEvent(new ReportFailedEvent(jobId, error.getMessage()));
     *                 }
     *             }
     *         );
     *
     *         return ResponseEntity.accepted().body(new JobResponse(job.getJobId()));
     *     }
     * }
     * }</pre>
     */
    private void runAsyncExample(Path outputDir) throws Exception {
        System.out.println("\n[3] 비동기 실행 (Listener) - Spring Boot Java");
        System.out.println("-".repeat(40));

        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1];

        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");

        Map<String, Object> data = new HashMap<>();
        data.put("title", "2026년 직원 현황 (비동기, Spring Boot Java)");
        data.put("date", LocalDate.now().toString());
        data.put("linkText", "(주)휴넷 홈페이지");
        data.put("url", "https://www.hunet.co.kr");
        data.put("employees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500)
        ));

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");
        if (logo != null) data.put("logo", logo);
        if (ci != null) data.put("ci", ci);

        // 비동기 작업 제출
        GenerationJob job = excelGenerator.submitToFile(
            templateResource.getInputStream(),
            SimpleDataProvider.of(data),
            outputDir,
            "spring_async_example_java",
            null,
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                }

                @Override
                public void onCompleted(@NotNull String jobId, @NotNull GenerationResult result) {
                    System.out.println("\t[완료] 소요시간: " + result.getDurationMs() + "ms");
                    System.out.println("\t[완료] 파일: " + result.getFilePath());
                    generatedPath[0] = result.getFilePath();
                    completionLatch.countDown();
                }

                @Override
                public void onFailed(@NotNull String jobId, @NotNull Exception error) {
                    System.out.println("\t[실패] " + error.getMessage());
                    completionLatch.countDown();
                }

                @Override
                public void onCancelled(@NotNull String jobId) {
                    System.out.println("\t[취소됨]");
                    completionLatch.countDown();
                }
            }
        );

        System.out.println("\t작업 제출됨: " + job.getJobId());
        System.out.println("\t(실제 API에서는 여기서 HTTP 202 반환)");

        //noinspection ResultOfMethodCallIgnored
        completionLatch.await(30, TimeUnit.SECONDS);

        if (generatedPath[0] != null) {
            System.out.println("\t결과: " + generatedPath[0]);
        }
    }

    // ==================== 4. 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하는 방식입니다.
     *
     * <p>DataProvider의 지연 로딩과 비동기 실행을 조합하여 최적의 성능을 제공합니다.
     *
     * <p>실제 컨트롤러 코드 예시:
     * <pre>{@code
     * @RestController
     * public class LargeReportController {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *     private final EmployeeRepository employeeRepository;
     *     private final ApplicationEventPublisher eventPublisher;
     *
     *     @PostMapping("/reports/large-async")
     *     @Transactional(readOnly = true)
     *     public ResponseEntity<JobResponse> generateLargeReportAsync() {
     *         Resource template = resourceLoader.getResource("classpath:templates/template.xlsx");
     *
     *         SimpleDataProvider provider = SimpleDataProvider.builder()
     *             .value("title", "대용량 직원 현황")
     *             .itemsFromSupplier("employees", () -> employeeRepository.streamAll().iterator())
     *             .build();
     *
     *         GenerationJob job = excelGenerator.submitToFile(
     *             template.getInputStream(),
     *             provider,
     *             Path.of("/output"),
     *             "large_report",
     *             null,
     *             new ExcelGenerationListener() {
     *                 public void onProgress(String jobId, ProgressInfo progress) {
     *                     log.info("Progress: {}%", progress.getPercentage());
     *                 }
     *                 public void onCompleted(String jobId, GenerationResult result) {
     *                     eventPublisher.publishEvent(new ReportReadyEvent(jobId, result.getFilePath()));
     *                 }
     *             }
     *         );
     *
     *         return ResponseEntity.accepted().body(new JobResponse(job.getJobId()));
     *     }
     * }
     * }</pre>
     */
    private void runLargeAsyncExample(Path outputDir) throws Exception {
        System.out.println("\n[4] 대용량 비동기 (DataProvider + Listener) - Spring Boot Java");
        System.out.println("-".repeat(40));

        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1];
        int[] processedRows = new int[1];

        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");
        int dataCount = 255; // 수식 확장 이슈 방지

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황 (대용량 비동기, Spring Boot Java)")
            .value("date", LocalDate.now().toString())
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            .itemsFromSupplier("employees", () -> generateLargeDataSet(dataCount))
            .build();

        System.out.println("\tDataProvider 생성 완료 (" + dataCount + "건 데이터 지연 로딩 예정)");

        GenerationJob job = excelGenerator.submitToFile(
            templateResource.getInputStream(),
            dataProvider,
            outputDir,
            "spring_large_async_example_java",
            null,
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                    // 진행률 로깅 (실제로는 WebSocket 등으로 전송 가능)
                }

                @Override
                public void onCompleted(@NotNull String jobId, @NotNull GenerationResult result) {
                    System.out.println("\t[완료] 처리된 행: " + result.getRowsProcessed() + "건");
                    System.out.println("\t[완료] 소요시간: " + result.getDurationMs() + "ms");
                    System.out.println("\t[완료] 파일: " + result.getFilePath());
                    generatedPath[0] = result.getFilePath();
                    processedRows[0] = result.getRowsProcessed();
                    completionLatch.countDown();
                }

                @Override
                public void onFailed(@NotNull String jobId, @NotNull Exception error) {
                    System.out.println("\t[실패] " + error.getMessage());
                    completionLatch.countDown();
                }

                @Override
                public void onCancelled(@NotNull String jobId) {
                    System.out.println("\t[취소됨]");
                    completionLatch.countDown();
                }
            }
        );

        System.out.println("\t작업 제출됨: " + job.getJobId());
        System.out.println("\t(백그라운드에서 대용량 데이터 처리 중...)");

        //noinspection ResultOfMethodCallIgnored
        completionLatch.await(60, TimeUnit.SECONDS);

        if (generatedPath[0] != null) {
            System.out.println("\t결과: " + generatedPath[0] + " (" + processedRows[0] + "건 처리)");
        }
    }

    // ==================== 5. 암호화된 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하면서 파일에 암호를 설정합니다.
     *
     * <p>실제 컨트롤러 코드 예시:
     * <pre>{@code
     * @RestController
     * public class SecureReportController {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *     private final EmployeeRepository employeeRepository;
     *
     *     @PostMapping("/reports/secure")
     *     @Transactional(readOnly = true)
     *     public ResponseEntity<JobResponse> generateSecureReport(@RequestParam String password) {
     *         Resource template = resourceLoader.getResource("classpath:templates/template.xlsx");
     *
     *         SimpleDataProvider provider = SimpleDataProvider.builder()
     *             .value("title", "보안 직원 현황")
     *             .itemsFromSupplier("employees", () -> employeeRepository.streamAll().iterator())
     *             .build();
     *
     *         GenerationJob job = excelGenerator.submitToFile(
     *             template.getInputStream(),
     *             provider,
     *             Path.of("/output"),
     *             "secure_report",
     *             password,  // 파일 열기 암호 설정
     *             new ExcelGenerationListener() {
     *                 public void onCompleted(String jobId, GenerationResult result) {
     *                     // 암호화된 파일 생성 완료
     *                 }
     *             }
     *         );
     *
     *         return ResponseEntity.accepted().body(new JobResponse(job.getJobId()));
     *     }
     * }
     * }</pre>
     */
    private void runEncryptedLargeAsyncExample(Path outputDir) throws Exception {
        System.out.println("\n[5] 암호화된 대용량 비동기 (암호: 1234) - Spring Boot Java");
        System.out.println("-".repeat(40));

        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1];
        int[] processedRows = new int[1];
        String password = "1234";

        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");
        int dataCount = 255;

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황 (암호화, Spring Boot Java)")
            .value("date", LocalDate.now().toString())
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            .itemsFromSupplier("employees", () -> generateLargeDataSet(dataCount))
            .build();

        System.out.println("\tDataProvider 생성 완료 (" + dataCount + "건 데이터)");

        GenerationJob job = excelGenerator.submitToFile(
            templateResource.getInputStream(),
            dataProvider,
            outputDir,
            "spring_encrypted_large_async_example_java",
            password,  // 파일 열기 암호 설정
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                }

                @Override
                public void onCompleted(@NotNull String jobId, @NotNull GenerationResult result) {
                    System.out.println("\t[완료] 처리된 행: " + result.getRowsProcessed() + "건");
                    System.out.println("\t[완료] 소요시간: " + result.getDurationMs() + "ms");
                    System.out.println("\t[완료] 파일: " + result.getFilePath());
                    System.out.println("\t[완료] 파일 열기 암호: " + password);
                    generatedPath[0] = result.getFilePath();
                    processedRows[0] = result.getRowsProcessed();
                    completionLatch.countDown();
                }

                @Override
                public void onFailed(@NotNull String jobId, @NotNull Exception error) {
                    System.out.println("\t[실패] " + error.getMessage());
                    completionLatch.countDown();
                }

                @Override
                public void onCancelled(@NotNull String jobId) {
                    System.out.println("\t[취소됨]");
                    completionLatch.countDown();
                }
            }
        );

        System.out.println("\t작업 제출됨: " + job.getJobId());
        System.out.println("\t(백그라운드에서 암호화된 파일 생성 중...)");

        //noinspection ResultOfMethodCallIgnored
        completionLatch.await(60, TimeUnit.SECONDS);

        if (generatedPath[0] != null) {
            System.out.println("\t결과: " + generatedPath[0] + " (" + processedRows[0] + "건 처리, 암호: " + password + ")");
        }
    }

    // ==================== 6. 문서 메타데이터 설정 ====================

    /**
     * 문서 메타데이터를 설정하는 방식입니다.
     * Excel 파일의 속성(제목, 작성자, 키워드 등)을 설정할 수 있습니다.
     *
     * <p>메타데이터는 Excel에서 "파일 > 정보 > 속성"에서 확인할 수 있습니다.
     *
     * <p>실제 서비스 코드 예시:
     * <pre>{@code
     * @Service
     * public class MetadataReportService {
     *     private final ExcelGenerator excelGenerator;
     *     private final ResourceLoader resourceLoader;
     *
     *     public Path generateReportWithMetadata() throws IOException {
     *         Resource template = resourceLoader.getResource("classpath:templates/template.xlsx");
     *
     *         SimpleDataProvider provider = SimpleDataProvider.builder()
     *             .value("title", "보고서")
     *             .metadata(meta -> meta
     *                 .title("월간 실적 보고서")
     *                 .author("황용호")
     *                 .subject("2026년 1월 실적")
     *                 .keywords("실적", "월간", "보고서")
     *                 .description("2026년 1월 월간 실적 보고서입니다.")
     *                 .category("업무 보고")
     *                 .company("(주)휴넷")
     *                 .manager("황상무"))
     *             .build();
     *
     *         return excelGenerator.generateToFile(
     *             template.getInputStream(),
     *             provider,
     *             Path.of("/output"),
     *             "monthly_report"
     *         );
     *     }
     * }
     * }</pre>
     */
    private void runMetadataExample(Path outputDir) throws IOException {
        System.out.println("\n[6] 문서 메타데이터 설정 - Spring Boot Java");
        System.out.println("-".repeat(40));

        Resource templateResource = resourceLoader.getResource("classpath:templates/template.xlsx");

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        // Java에서는 Builder의 metadata(Consumer) 사용
        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황 (메타데이터 포함, Spring Boot Java)")
            .value("date", LocalDate.now().toString())
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            .items("employees", Arrays.asList(
                new Employee("황용호", "부장", 8000),
                new Employee("한용호", "과장", 6500),
                new Employee("홍용호", "대리", 4500)
            ))
            // 문서 메타데이터 설정
            .metadata((java.util.function.Consumer<io.github.jogakdal.tbeg.DocumentMetadata.Builder>) meta -> meta
                .title("2026년 직원 현황 보고서")
                .author("황용호")
                .subject("직원 현황")
                .keywords("직원", "현황", "2026년", "보고서")
                .description("2026년도 직원 현황을 정리한 보고서입니다.")
                .category("인사 보고")
                .company("(주)휴넷")
                .manager("황상무"))
            .build();

        System.out.println("\t문서 메타데이터:");
        System.out.println("\t  - 제목: 2026년 직원 현황 보고서");
        System.out.println("\t  - 작성자: 황용호");
        System.out.println("\t  - 회사: (주)휴넷");

        Path resultPath = excelGenerator.generateToFile(
            templateResource.getInputStream(),
            dataProvider,
            outputDir,
            "spring_metadata_example_java"
        );

        System.out.println("\t결과: " + resultPath);
        System.out.println("\t(Excel에서 \"파일 > 정보 > 속성\"에서 메타데이터 확인 가능)");
    }

    // ==================== 유틸리티 메서드 ====================

    private Path createOutputDirectory() throws IOException {
        Path outputDir = findModuleDir().resolve("build/samples-spring-java");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        return outputDir;
    }

    private Path findModuleDir() {
        try {
            java.security.CodeSource classLocation = TbegSpringBootJavaSample.class.getProtectionDomain().getCodeSource();
            if (classLocation != null) {
                Path current = Path.of(classLocation.getLocation().toURI());
                while (current.getParent() != null) {
                    Path fileName = current.getFileName();
                    if (fileName != null && "build".equals(fileName.toString()) &&
                        Files.exists(current.resolveSibling("src"))) {
                        return current.getParent();
                    }
                    current = current.getParent();
                }
            }
        } catch (java.net.URISyntaxException e) {
            // Fall through to use working directory
        }

        Path workingDir = Path.of("").toAbsolutePath();
        if (Files.exists(workingDir.resolve("src/main/kotlin/com/hunet/common/excel"))) {
            return workingDir;
        }

        Path moduleFromRoot = workingDir.resolve("modules/core/tbeg");
        if (Files.exists(moduleFromRoot)) {
            return moduleFromRoot;
        }

        return workingDir;
    }

    private byte[] loadImage(String fileName) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + fileName);
            return resource.getInputStream().readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private Iterator<Object> generateLargeDataSet(int count) {
        String[] positions = {"사원", "대리", "과장", "차장", "부장"};
        String[] names = {"황", "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임"};

        List<Object> employees = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            employees.add(new Employee(
                names[i % names.length] + "용호" + (i + 1),
                positions[i % positions.length],
                3000 + (i % 5) * 1000
            ));
        }
        return employees.iterator();
    }

    /**
     * 샘플 실행 진입점.
     *
     * <p>Spring Boot 테스트 컨텍스트 없이 직접 실행하는 경우
     * ApplicationContextRunner를 사용합니다.
     */
    public static void main(String[] args) {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TbegAutoConfiguration.class))
            .withBean(DefaultResourceLoader.class)
            .run(context -> {
                TbegSpringBootJavaSample sample = new TbegSpringBootJavaSample();
                sample.excelGenerator = context.getBean(ExcelGenerator.class);
                sample.resourceLoader = new DefaultResourceLoader(
                    TbegSpringBootJavaSample.class.getClassLoader()
                );
                sample.runAllSamples();
                sample.excelGenerator.close();
            });
    }
}
