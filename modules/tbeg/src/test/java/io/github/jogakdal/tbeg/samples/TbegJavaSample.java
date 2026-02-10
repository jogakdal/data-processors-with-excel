package io.github.jogakdal.tbeg.samples;

import io.github.jogakdal.tbeg.DocumentMetadata;
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import io.github.jogakdal.tbeg.async.ExcelGenerationListener;
import io.github.jogakdal.tbeg.async.GenerationJob;
import io.github.jogakdal.tbeg.async.GenerationResult;
import io.github.jogakdal.tbeg.async.ProgressInfo;
import io.github.jogakdal.tbeg.exception.FormulaExpansionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Excel Generator Java 샘플 실행 클래스.
 *
 * <p>여섯 가지 생성 방식을 시연합니다:
 * <ol>
 *   <li>기본 사용 - Map 기반 간편 API</li>
 *   <li>지연 로딩 - DataProvider를 통한 대용량 처리</li>
 *   <li>비동기 실행 - 리스너 기반 백그라운드 처리</li>
 *   <li>대용량 비동기 - DataProvider + 비동기 조합</li>
 *   <li>암호화된 대용량 비동기 - 파일 열기 암호 설정</li>
 *   <li>문서 메타데이터 - 제목, 작성자, 키워드 등 설정</li>
 * </ol>
 *
 * <h2>Spring Boot 환경에서 사용</h2>
 * <pre>{@code
 * // build.gradle
 * implementation("com.hunet.common:excel-generator:1.0.0-SNAPSHOT")
 * }</pre>
 *
 * <pre>{@code
 * @Service
 * public class ReportService {
 *     private final ExcelGenerator excelGenerator;
 *     private final ResourceLoader resourceLoader;
 *
 *     public Path generateReport(Map<String, Object> data) throws IOException {
 *         Resource template = resourceLoader.getResource("classpath:templates/report.xlsx");
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
public class TbegJavaSample {

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
     * 샘플 데이터 클래스 (Department).
     */
    public static class Department {
        private final String name;
        private final int members;
        private final String office;

        public Department(String name, int members, String office) {
            this.name = name;
            this.members = members;
            this.office = office;
        }

        public String getName() { return name; }
        public int getMembers() { return members; }
        public String getOffice() { return office; }
    }

    public static void main(String[] args) throws Exception {
        Path moduleDir = findModuleDir();
        Path outputDir = moduleDir.resolve("build/samples-java");
        Files.createDirectories(outputDir);

        System.out.println("=".repeat(60));
        System.out.println("Excel Generator Java 샘플 실행");
        System.out.println("=".repeat(60));

        // Spring Boot 환경에서는 ExcelGenerator가 Bean으로 자동 주입됩니다.
        // 이 샘플은 테스트 목적으로 직접 인스턴스를 생성합니다.
        try (ExcelGenerator generator = new ExcelGenerator()) {
            // 1. 기본 사용 (Map 기반)
            runBasicExample(generator, outputDir);

            // 2. 지연 로딩 (DataProvider + Builder)
            runLazyLoadingExample(generator, outputDir);

            // 3. 비동기 실행 (Listener)
            runAsyncExample(generator, outputDir);

            // 4. 대용량 비동기 (DataProvider + Listener)
            runLargeAsyncExample(generator, outputDir);

            // 5. 암호화된 대용량 비동기 (암호: 1234)
            runEncryptedLargeAsyncExample(generator, outputDir);

            // 6. 문서 메타데이터 설정
            runMetadataExample(generator, outputDir);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("샘플 폴더: " + outputDir.toAbsolutePath());
        System.out.println("=".repeat(60));
    }

    // ==================== 1. 기본 사용 ====================

    /**
     * Map 기반의 가장 간단한 사용 방법입니다.
     * 소량의 데이터를 빠르게 처리할 때 적합합니다.
     */
    private static void runBasicExample(ExcelGenerator generator, Path outputDir) {
        System.out.println("\n[1] 기본 사용 (Map 기반)");
        System.out.println("-".repeat(40));

        // 데이터를 Map으로 준비
        Map<String, Object> data = new HashMap<>();
        data.put("title", "2026년 직원 현황");
        data.put("date", LocalDate.now().toString());
        data.put("secondTitle", "부서별 현황");
        data.put("linkText", "(주)휴넷 홈페이지");
        data.put("url", "https://www.hunet.co.kr");
        data.put("employees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500),
            new Employee("홍용호", "대리", 4500)
        ));
        // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
        data.put("mergedEmployees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500),
            new Employee("홍용호", "대리", 4500)
        ));
        data.put("departments", Arrays.asList(
            new Department("공통플랫폼팀", 15, "814호"),
            new Department("IT전략기획팀", 8, "801호"),
            new Department("인재경영실", 5, "813호")
        ));

        // 이미지 추가 (있는 경우)
        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");
        if (logo != null) data.put("logo", logo);
        if (ci != null) data.put("ci", ci);

        // 템플릿 로드 및 생성
        InputStream template = loadTemplate();
        Path resultPath = generator.generateToFile(
            template,
            SimpleDataProvider.of(data),
            outputDir,
            "basic_example_java"
        );

        System.out.println("\t결과: " + resultPath);
    }

    // ==================== 2. 지연 로딩 ====================

    /**
     * DataProvider Builder를 사용한 지연 로딩 방식입니다.
     * 대용량 데이터를 스트리밍으로 처리할 때 적합합니다.
     *
     * <p>장점:
     * <ul>
     *   <li>데이터를 한 번에 메모리에 올리지 않음</li>
     *   <li>DB 커서나 Iterator를 직접 연결 가능</li>
     *   <li>메모리 효율적</li>
     * </ul>
     */
    private static void runLazyLoadingExample(ExcelGenerator generator, Path outputDir) {
        System.out.println("\n[2] 지연 로딩 (DataProvider + Builder)");
        System.out.println("-".repeat(40));

        // Java에서는 Builder 패턴 사용
        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            // 단순 값
            .value("title", "2026년 직원 현황(대용량)")
            .value("date", LocalDate.now().toString())
            .value("secondTitle", "부서별 현황")
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            // 이미지
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            // 컬렉션 - 지연 로딩 (Java Supplier 사용)
            .itemsFromSupplier("employees", () -> generateLargeDataSet(100))
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            .itemsFromSupplier("mergedEmployees", () -> generateLargeDataSet(100))
            // 부서 컬렉션
            .items("departments", Arrays.asList(
                new Department("공통플랫폼팀", 15, "814호"),
                new Department("IT전략기획팀", 8, "801호"),
                new Department("인재경영실", 5, "813호")
            ))
            .build();

        System.out.println("\tDataProvider 생성 완료 (데이터는 아직 로드되지 않음)");

        InputStream template = loadTemplate();
        Path resultPath = generator.generateToFile(
            template,
            dataProvider,
            outputDir,
            "lazy_loading_example_java"
        );

        System.out.println("\t결과: " + resultPath);
    }

    /**
     * 대용량 데이터셋을 시뮬레이션합니다.
     * 실제 환경에서는 DB 스트리밍 쿼리 등으로 대체할 수 있습니다.
     */
    private static Iterator<Object> generateLargeDataSet(int count) {
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

    // ==================== 3. 비동기 실행 ====================

    /**
     * 비동기 실행 방식입니다.
     * API 서버에서 즉시 응답 후 백그라운드 처리할 때 적합합니다.
     *
     * <p>사용 시나리오:
     * <ul>
     *   <li>REST API에서 Excel 생성 요청 받음</li>
     *   <li>즉시 jobId 반환 (HTTP 202 Accepted)</li>
     *   <li>백그라운드에서 생성 완료 후 알림 (이메일, 푸시 등)</li>
     * </ul>
     */
    private static void runAsyncExample(ExcelGenerator generator, Path outputDir) throws Exception {
        System.out.println("\n[3] 비동기 실행 (Listener)");
        System.out.println("-".repeat(40));

        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1]; // effectively final wrapper

        Map<String, Object> data = new HashMap<>();
        data.put("title", "2026년 직원 현황(비동기 생성)");
        data.put("date", LocalDate.now().toString());
        data.put("secondTitle", "부서별 현황");
        data.put("linkText", "(주)휴넷 홈페이지");
        data.put("url", "https://www.hunet.co.kr");
        data.put("employees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500)
        ));
        // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
        data.put("mergedEmployees", Arrays.asList(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500)
        ));
        data.put("departments", Arrays.asList(
            new Department("공통플랫폼팀", 15, "814호"),
            new Department("IT전략기획팀", 8, "801호")
        ));

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");
        if (logo != null) data.put("logo", logo);
        if (ci != null) data.put("ci", ci);

        InputStream template = loadTemplate();

        // 비동기 작업 제출 (익명 클래스로 리스너 구현)
        GenerationJob job = generator.submitToFile(
            template,
            SimpleDataProvider.of(data),
            outputDir,
            "async_example_java",
            null, // 암호 없음
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                    // 진행률 업데이트 (옵션)
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

        // API 서버에서는 여기서 즉시 jobId를 반환
        System.out.println("\t작업 제출됨: " + job.getJobId());
        System.out.println("\t(API 서버에서는 여기서 HTTP 202 반환)");

        // 샘플에서는 완료 대기
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);

        if (completed && generatedPath[0] != null) {
            System.out.println("\t결과: " + generatedPath[0]);
        }
    }

    // ==================== 4. 대용량 비동기 ====================

    /**
     * 대용량 데이터를 비동기로 처리하는 방식입니다.
     * DataProvider의 지연 로딩과 비동기 실행을 조합하여 최적의 성능을 제공합니다.
     *
     * <p>사용 시나리오:
     * <ul>
     *   <li>대용량 Excel 생성 요청 (수만~수십만 행)</li>
     *   <li>메모리 효율적인 스트리밍 처리 필요</li>
     *   <li>API 서버에서 즉시 응답 후 백그라운드 처리</li>
     * </ul>
     */
    private static void runLargeAsyncExample(ExcelGenerator generator, Path outputDir) throws Exception {
        System.out.println("\n[4] 대용량 비동기 (DataProvider + Listener)");
        System.out.println("-".repeat(40));

        // 먼저 1000건으로 시도, 수식 확장 실패 시 255건으로 재시도
        int initialCount = 1000;
        int retryCount = 255;

        Result result = runLargeAsyncWithRetry(generator, outputDir, initialCount, retryCount);

        if (result != null) {
            System.out.println("\t결과: " + result.path() + " (" + result.rowsProcessed() + "건 처리)");
        }
    }

    /**
     * 결과를 담는 내부 클래스
     */
    private record Result(Path path, int rowsProcessed) {
    }

    /**
     * 대용량 비동기 생성을 시도하고, FormulaExpansionException 발생 시 재시도합니다.
     */
    private static Result runLargeAsyncWithRetry(
            ExcelGenerator generator,
            Path outputDir,
            int dataCount,
            int retryDataCount
    ) throws Exception {
        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1];
        int[] processedRows = new int[1];
        FormulaExpansionException[] formulaError = new FormulaExpansionException[1];

        // DataProvider로 대용량 데이터 지연 로딩 설정 (Builder 사용)
        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황(대용량 비동기)")
            .value("date", LocalDate.now().toString())
            .value("secondTitle", "부서별 현황")
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            // 대용량 데이터 - 실제로는 DB 스트리밍 쿼리 사용
            .itemsFromSupplier("employees", () -> generateLargeDataSet(dataCount))
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            .itemsFromSupplier("mergedEmployees", () -> generateLargeDataSet(100))
            // 부서 컬렉션
            .items("departments", Arrays.asList(
                new Department("공통플랫폼팀", 15, "814호"),
                new Department("IT전략기획팀", 8, "801호"),
                new Department("인재경영실", 5, "813호")
            ))
            .build();

        System.out.println("\tDataProvider 생성 완료 (" + dataCount + "건 데이터 지연 로딩 예정)");

        InputStream template = loadTemplate();

        // 비동기 작업 제출
        GenerationJob job = generator.submitToFile(
            template,
            dataProvider,
            outputDir,
            "large_async_example_java",
            null, // 암호 없음
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                    // 진행률 업데이트 (옵션)
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
                    if (error instanceof FormulaExpansionException) {
                        formulaError[0] = (FormulaExpansionException) error;
                    }
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

        // 샘플에서는 완료 대기
        //noinspection ResultOfMethodCallIgnored
        completionLatch.await(60, TimeUnit.SECONDS);

        // FormulaExpansionException 발생 시 데이터 수를 줄여서 재시도
        if (formulaError[0] != null && dataCount > retryDataCount) {
            System.out.println("\n\t⚠️ 수식 확장 실패로 인해 " + retryDataCount + "건으로 재시도합니다...");
            return runLargeAsyncWithRetry(generator, outputDir, retryDataCount, retryDataCount);
        }

        if (generatedPath[0] != null) {
            return new Result(generatedPath[0], processedRows[0]);
        }
        return null;
    }

    // ==================== 5. 암호화된 대용량 비동기 ====================

    /**
     * 암호화된 대용량 비동기 생성 방식입니다.
     * 대용량 비동기 생성에 파일 열기 암호를 추가합니다.
     *
     * <p>사용 시나리오:
     * <ul>
     *   <li>보안이 필요한 대용량 Excel 생성</li>
     *   <li>파일 열기 시 암호 입력 필요</li>
     * </ul>
     */
    private static void runEncryptedLargeAsyncExample(ExcelGenerator generator, Path outputDir) throws Exception {
        System.out.println("\n[5] 암호화된 대용량 비동기 (암호: 1234)");
        System.out.println("-".repeat(40));

        int dataCount = 255;
        String password = "1234";

        CountDownLatch completionLatch = new CountDownLatch(1);
        Path[] generatedPath = new Path[1];
        int[] processedRows = new int[1];

        // DataProvider로 대용량 데이터 지연 로딩 설정 (Builder 사용)
        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황(암호화)")
            .value("date", LocalDate.now().toString())
            .value("secondTitle", "부서별 현황")
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            .itemsFromSupplier("employees", () -> generateLargeDataSet(dataCount))
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트, 255개 이하)
            .itemsFromSupplier("mergedEmployees", () -> generateLargeDataSet(100))
            // 부서 컬렉션
            .items("departments", Arrays.asList(
                new Department("공통플랫폼팀", 15, "814호"),
                new Department("IT전략기획팀", 8, "801호"),
                new Department("인재경영실", 5, "813호")
            ))
            .build();

        System.out.println("\tDataProvider 생성 완료 (" + dataCount + "건 데이터 지연 로딩 예정)");

        InputStream template = loadTemplate();

        // 비동기 작업 제출 (암호 포함)
        GenerationJob job = generator.submitToFile(
            template,
            dataProvider,
            outputDir,
            "encrypted_large_async_example_java",
            password, // 파일 열기 암호
            new ExcelGenerationListener() {
                @Override
                public void onStarted(@NotNull String jobId) {
                    System.out.println("\t[시작] jobId: " + jobId);
                }

                @Override
                public void onProgress(@NotNull String jobId, @NotNull ProgressInfo progress) {
                    // 진행률 업데이트 (옵션)
                }

                @Override
                public void onCompleted(@NotNull String jobId, @NotNull GenerationResult result) {
                    System.out.println("\t[완료] 처리된 행: " + result.getRowsProcessed() + "건");
                    System.out.println("\t[완료] 소요시간: " + result.getDurationMs() + "ms");
                    System.out.println("\t[완료] 파일: " + result.getFilePath());
                    System.out.println("\t[완료] 암호: " + password);
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
        System.out.println("\t(백그라운드에서 암호화된 Excel 생성 중...)");

        // 샘플에서는 완료 대기
        //noinspection ResultOfMethodCallIgnored
        completionLatch.await(60, TimeUnit.SECONDS);

        if (generatedPath[0] != null) {
            System.out.println("\t결과: " + generatedPath[0] + " (" + processedRows[0] + "건 처리)");
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
    private static void runMetadataExample(ExcelGenerator generator, Path outputDir) {
        System.out.println("\n[6] 문서 메타데이터 설정");
        System.out.println("-".repeat(40));

        byte[] logo = loadImage("hunet_logo.png");
        byte[] ci = loadImage("hunet_ci.png");

        // Java에서는 Builder의 metadata(Consumer) 사용
        SimpleDataProvider dataProvider = SimpleDataProvider.builder()
            .value("title", "2026년 직원 현황(메타데이터 포함)")
            .value("date", LocalDate.now().toString())
            .value("secondTitle", "부서별 현황")
            .value("linkText", "(주)휴넷 홈페이지")
            .value("url", "https://www.hunet.co.kr")
            .image("logo", logo != null ? logo : new byte[0])
            .image("ci", ci != null ? ci : new byte[0])
            .items("employees", Arrays.asList(
                new Employee("황용호", "부장", 8000),
                new Employee("한용호", "과장", 6500),
                new Employee("홍용호", "대리", 4500)
            ))
            // 셀병합 시트용 (비연속적 셀 참조 수식 테스트)
            .items("mergedEmployees", Arrays.asList(
                new Employee("황용호", "부장", 8000),
                new Employee("한용호", "과장", 6500),
                new Employee("홍용호", "대리", 4500)
            ))
            // 부서 컬렉션
            .items("departments", Arrays.asList(
                new Department("공통플랫폼팀", 15, "814호"),
                new Department("IT전략기획팀", 8, "801호"),
                new Department("인재경영실", 5, "813호")
            ))
            // 문서 메타데이터 설정
            .metadata((java.util.function.Consumer<DocumentMetadata.Builder>) meta -> meta
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

        InputStream template = loadTemplate();
        Path resultPath = generator.generateToFile(
            template,
            dataProvider,
            outputDir,
            "metadata_example_java"
        );

        System.out.println("\t결과: " + resultPath);
        System.out.println("\t(Excel에서 \"파일 > 정보 > 속성\"에서 메타데이터 확인 가능)");
    }

    // ==================== 유틸리티 메서드 ====================

    private static InputStream loadTemplate() {
        InputStream stream = TbegJavaSample.class.getResourceAsStream("/templates/template.xlsx");
        if (stream == null) {
            throw new IllegalStateException("템플릿 파일을 찾을 수 없습니다: /templates/template.xlsx");
        }
        return stream;
    }

    private static byte @Nullable [] loadImage(String fileName) {
        try (InputStream stream = TbegJavaSample.class.getResourceAsStream("/" + fileName)) {
            if (stream == null) return null;
            return stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findModuleDir() {
        try {
            CodeSource classLocation = TbegJavaSample.class.getProtectionDomain().getCodeSource();
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
        } catch (URISyntaxException e) {
            // Fall through to use working directory
        }

        Path workingDir = Path.of("").toAbsolutePath();
        if (Files.exists(workingDir.resolve("src/main/kotlin/com/hunet/common/excel"))) {
            return workingDir;
        }

        Path moduleFromRoot = workingDir.resolve("modules/core/excel-generator");
        if (Files.exists(moduleFromRoot)) {
            return moduleFromRoot;
        }

        return workingDir;
    }
}
