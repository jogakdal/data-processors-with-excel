# TBEG Spring Boot 예제

## 목차
1. [설정](#1-설정)
2. [기본 Service 패턴](#2-기본-service-패턴)
3. [Controller에서 다운로드](#3-controller에서-다운로드)
4. [비동기 보고서 생성 API](#4-비동기-보고서-생성-api)
5. [JPA Stream과 통합](#5-jpa-stream과-통합)
6. [이벤트 기반 알림](#6-이벤트-기반-알림)
7. [테스트 작성](#7-테스트-작성)

---

## 1. 설정

### 리포지토리 및 의존성 추가

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

> [!TIP]
> 상세한 설정 방법(Groovy DSL, Maven)은 [사용자 가이드](../user-guide.md#11-의존성-추가)를 참조하세요.

### application.yml

```yaml
tbeg:
  streaming-mode: enabled           # enabled, disabled
  file-naming-mode: timestamp       # none, timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence    # error, sequence
  preserve-template-layout: true
  missing-data-behavior: warn       # warn, throw
```

### 자동 설정

`tbeg` 의존성을 추가하면 `TbegAutoConfiguration`이 자동으로 활성화됩니다:

- `ExcelGenerator` Bean 자동 등록
- `TbegProperties` 바인딩
- 애플리케이션 종료 시 자동 정리

---

## 2. 기본 Service 패턴

### Kotlin

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.LocalDate

@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader,
    private val employeeRepository: EmployeeRepository
) {
    /**
     * Map 기반 간단한 보고서 생성
     */
    fun generateSimpleReport(): ByteArray {
        val template = resourceLoader.getResource("classpath:templates/simple.xlsx")

        val data = mapOf(
            "title" to "간단한 보고서",
            "date" to LocalDate.now().toString(),
            "author" to "시스템"
        )

        return excelGenerator.generate(template.inputStream, data)
    }

    /**
     * DataProvider를 사용한 보고서 생성
     */
    fun generateEmployeeReport(): Path {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")

        // count를 먼저 조회 (SELECT COUNT는 인덱스만 사용하므로 빠름)
        val employeeCount = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "직원 현황 보고서")
            value("date", LocalDate.now().toString())

            // count와 함께 지연 로딩 제공
            // - TBEG가 전체 행 수를 미리 알 수 있어 수식 범위 즉시 계산
            // - 데이터 이중 순회 방지로 성능 최적화
            items("employees", employeeCount) {
                employeeRepository.findAll().iterator()
            }

            metadata {
                title = "직원 현황 보고서"
                author = "HR 시스템"
                company = "(주)휴넷"
            }
        }

        return excelGenerator.generateToFile(
            template = template.inputStream,
            dataProvider = provider,
            outputDir = Path.of("/var/reports"),
            baseFileName = "employee_report"
        )
    }
}
```

### Java

```java
package com.example.report;

import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

@Service
public class ReportService {

    private final ExcelGenerator excelGenerator;
    private final ResourceLoader resourceLoader;
    private final EmployeeRepository employeeRepository;

    public ReportService(
            ExcelGenerator excelGenerator,
            ResourceLoader resourceLoader,
            EmployeeRepository employeeRepository) {
        this.excelGenerator = excelGenerator;
        this.resourceLoader = resourceLoader;
        this.employeeRepository = employeeRepository;
    }

    public byte[] generateSimpleReport() throws IOException {
        var template = resourceLoader.getResource("classpath:templates/simple.xlsx");

        Map<String, Object> data = new HashMap<>();
        data.put("title", "간단한 보고서");
        data.put("date", LocalDate.now().toString());
        data.put("author", "시스템");

        return excelGenerator.generate(template.getInputStream(), data);
    }

    public Path generateEmployeeReport() throws IOException {
        var template = resourceLoader.getResource("classpath:templates/employees.xlsx");

        // count를 먼저 조회 (수식 범위 계산 및 이중 순회 방지)
        int employeeCount = (int) employeeRepository.count();

        var provider = SimpleDataProvider.builder()
            .value("title", "직원 현황 보고서")
            .value("date", LocalDate.now().toString())
            .itemsFromSupplier("employees", employeeCount,
                () -> employeeRepository.findAll().iterator())
            .metadata(meta -> meta
                .title("직원 현황 보고서")
                .author("HR 시스템")
                .company("(주)휴넷"))
            .build();

        return excelGenerator.generateToFile(
            template.getInputStream(),
            provider,
            Path.of("/var/reports"),
            "employee_report"
        );
    }
}
```

---

## 3. Controller에서 다운로드

### Kotlin

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.ExcelGenerator
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@RestController
@RequestMapping("/api/reports")
class ReportController(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader,
    private val employeeRepository: EmployeeRepository
) {
    /**
     * 직원 보고서 다운로드
     */
    @GetMapping("/employees/download")
    fun downloadEmployeeReport(): ResponseEntity<Resource> {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")

        val data = mapOf(
            "title" to "직원 현황",
            "date" to LocalDate.now().toString(),
            "employees" to employeeRepository.findAll()
        )

        val bytes = excelGenerator.generate(template.inputStream, data)

        val filename = "직원현황_${LocalDate.now()}.xlsx"
        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''$encodedFilename"
            )
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .contentLength(bytes.size.toLong())
            .body(ByteArrayResource(bytes))
    }

    /**
     * 부서별 보고서 다운로드
     */
    @GetMapping("/departments/{deptId}/download")
    fun downloadDepartmentReport(
        @PathVariable deptId: Long
    ): ResponseEntity<Resource> {
        val template = resourceLoader.getResource("classpath:templates/department.xlsx")
        val department = departmentRepository.findById(deptId)
            .orElseThrow { NoSuchElementException("부서를 찾을 수 없습니다: $deptId") }

        val data = mapOf(
            "department" to department,
            "employees" to employeeRepository.findByDepartmentId(deptId)
        )

        val bytes = excelGenerator.generate(template.inputStream, data)

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"${department.name}_report.xlsx\""
            )
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .body(ByteArrayResource(bytes))
    }
}
```

### Java

```java
package com.example.report;

import io.github.jogakdal.tbeg.ExcelGenerator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ExcelGenerator excelGenerator;
    private final ResourceLoader resourceLoader;
    private final EmployeeRepository employeeRepository;

    public ReportController(
            ExcelGenerator excelGenerator,
            ResourceLoader resourceLoader,
            EmployeeRepository employeeRepository) {
        this.excelGenerator = excelGenerator;
        this.resourceLoader = resourceLoader;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/employees/download")
    public ResponseEntity<Resource> downloadEmployeeReport() throws IOException {
        var template = resourceLoader.getResource("classpath:templates/employees.xlsx");

        Map<String, Object> data = new HashMap<>();
        data.put("title", "직원 현황");
        data.put("date", LocalDate.now().toString());
        data.put("employees", employeeRepository.findAll());

        byte[] bytes = excelGenerator.generate(template.getInputStream(), data);

        String filename = "직원현황_" + LocalDate.now() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFilename
            )
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .contentLength(bytes.length)
            .body(new ByteArrayResource(bytes));
    }
}
```

---

## 4. 비동기 보고서 생성 API

### Kotlin

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.ResourceLoader
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Path
import java.time.LocalDate

// 요청/응답 DTO
data class ReportRequest(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class JobResponse(
    val jobId: String,
    val message: String = "보고서 생성이 시작되었습니다."
)

// 이벤트
data class ReportReadyEvent(
    val jobId: String,
    val filePath: Path,
    val rowsProcessed: Int
)

data class ReportFailedEvent(
    val jobId: String,
    val errorMessage: String?
)

@RestController
@RequestMapping("/api/reports")
class AsyncReportController(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader,
    private val employeeRepository: EmployeeRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @PostMapping("/employees/async")
    fun generateReportAsync(
        @RequestBody request: ReportRequest
    ): ResponseEntity<JobResponse> {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")

        val provider = simpleDataProvider {
            value("title", request.title)
            value("startDate", request.startDate.toString())
            value("endDate", request.endDate.toString())
            items("employees") {
                employeeRepository.findByHireDateBetween(
                    request.startDate,
                    request.endDate
                ).iterator()
            }
        }

        val job = excelGenerator.submitToFile(
            template = template.inputStream,
            dataProvider = provider,
            outputDir = Path.of("/var/reports"),
            baseFileName = "employee_report",
            listener = object : ExcelGenerationListener {
                override fun onCompleted(jobId: String, result: GenerationResult) {
                    // 완료 이벤트 발행
                    eventPublisher.publishEvent(
                        ReportReadyEvent(
                            jobId = jobId,
                            filePath = result.filePath!!,
                            rowsProcessed = result.rowsProcessed
                        )
                    )
                }

                override fun onFailed(jobId: String, error: Exception) {
                    // 실패 이벤트 발행
                    eventPublisher.publishEvent(
                        ReportFailedEvent(
                            jobId = jobId,
                            errorMessage = error.message
                        )
                    )
                }
            }
        )

        // 즉시 202 Accepted 반환
        return ResponseEntity.accepted().body(JobResponse(job.jobId))
    }
}
```

### 이벤트 핸들러

```kotlin
package com.example.report

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ReportEventHandler(
    private val emailService: EmailService,
    private val notificationService: NotificationService
) {
    @EventListener
    fun handleReportReady(event: ReportReadyEvent) {
        // 이메일 발송
        emailService.sendReportReadyEmail(
            jobId = event.jobId,
            downloadUrl = "/api/reports/download/${event.filePath.fileName}"
        )

        // 푸시 알림
        notificationService.sendNotification(
            title = "보고서 생성 완료",
            message = "${event.rowsProcessed}건의 데이터가 포함된 보고서가 생성되었습니다."
        )
    }

    @EventListener
    fun handleReportFailed(event: ReportFailedEvent) {
        notificationService.sendNotification(
            title = "보고서 생성 실패",
            message = "오류: ${event.errorMessage}"
        )
    }
}
```

---

## 5. JPA Stream과 통합

대용량 데이터를 메모리 효율적으로 처리합니다.

```kotlin
@Service
class LargeReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader,
    private val employeeRepository: EmployeeRepository
) {
    /**
     * 대용량 직원 보고서 생성
     */
    @Transactional(readOnly = true)  // Stream 유지를 위해 필수
    fun generateLargeEmployeeReport(): Path {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")
        val employeeCount = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "전체 직원 현황")

            items("employees", employeeCount) {
                employeeRepository.streamAll().iterator()
            }
        }

        return excelGenerator.generateToFile(
            template = template.inputStream,
            dataProvider = provider,
            outputDir = Path.of("/var/reports"),
            baseFileName = "all_employees"
        )
    }
}
```

> [!WARNING]
> JPA Stream을 사용할 때는 반드시 `@Transactional` 어노테이션을 사용해야 합니다. Stream은 트랜잭션이 끝나면 닫히므로, Excel 생성이 완료될 때까지 트랜잭션이 유지되어야 합니다.

Repository 인터페이스 정의, 페이징 기반 Iterator 구현, MyBatis 연동 등 상세한 내용은 [고급 예제 - JPA/Spring Data 연동](./advanced-examples.md#13-jpaspring-data-연동)을 참조하세요.

---

## 6. 이벤트 기반 알림

### WebSocket 연동

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.async.ProgressInfo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class WebSocketReportListener(
    private val messagingTemplate: SimpMessagingTemplate
) : ExcelGenerationListener {

    override fun onStarted(jobId: String) {
        sendMessage(jobId, "started", mapOf("jobId" to jobId))
    }

    override fun onProgress(jobId: String, progress: ProgressInfo) {
        // percentage 계산 (totalRows가 있는 경우에만)
        val percentage = progress.totalRows?.let { total ->
            if (total > 0) (progress.processedRows * 100.0 / total) else null
        }

        sendMessage(jobId, "progress", mapOf(
            "jobId" to jobId,
            "processedRows" to progress.processedRows,
            "totalRows" to progress.totalRows,
            "percentage" to percentage  // null이면 JSON에서 제외됨
        ))
    }

    override fun onCompleted(jobId: String, result: GenerationResult) {
        sendMessage(jobId, "completed", mapOf(
            "jobId" to jobId,
            "filePath" to result.filePath.toString(),
            "rowsProcessed" to result.rowsProcessed,
            "durationMs" to result.durationMs
        ))
    }

    override fun onFailed(jobId: String, error: Exception) {
        sendMessage(jobId, "failed", mapOf(
            "jobId" to jobId,
            "error" to (error.message ?: "알 수 없는 오류")
        ))
    }

    private fun sendMessage(jobId: String, type: String, payload: Map<String, Any>) {
        messagingTemplate.convertAndSend(
            "/topic/reports/$jobId",
            mapOf("type" to type, "payload" to payload)
        )
    }
}
```

### Controller에서 사용

```kotlin
@PostMapping("/employees/async")
fun generateReportAsync(
    @RequestBody request: ReportRequest
): ResponseEntity<JobResponse> {
    val template = resourceLoader.getResource("classpath:templates/employees.xlsx")
    val provider = /* ... */

    val job = excelGenerator.submitToFile(
        template = template.inputStream,
        dataProvider = provider,
        outputDir = Path.of("/var/reports"),
        baseFileName = "report",
        listener = webSocketReportListener  // WebSocket 리스너 주입
    )

    return ResponseEntity.accepted().body(JobResponse(job.jobId))
}
```

---

## 7. 테스트 작성

### Service 테스트

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.ExcelGenerator
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.io.ByteArrayInputStream

class ReportServiceTest {

    private val excelGenerator = ExcelGenerator()
    private val employeeRepository = mockk<EmployeeRepository>()

    @Test
    fun `직원 보고서 생성 테스트`() {
        // Given
        every { employeeRepository.findAll() } returns listOf(
            Employee(1, "황용호", "공통플랫폼팀", 5000),
            Employee(2, "홍용호", "IT전략기획팀", 4500)
        )

        val template = ClassPathResource("templates/employees.xlsx")

        // When
        val bytes = excelGenerator.generate(
            template.inputStream,
            mapOf(
                "title" to "테스트 보고서",
                "employees" to employeeRepository.findAll()
            )
        )

        // Then
        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(0)

        // 제목 확인
        assert(sheet.getRow(0).getCell(0).stringCellValue == "테스트 보고서")

        // 데이터 행 확인
        assert(sheet.getRow(2).getCell(0).stringCellValue == "황용호")
        assert(sheet.getRow(3).getCell(0).stringCellValue == "홍용호")

        workbook.close()
    }
}
```

### Controller 통합 테스트

```kotlin
package com.example.report

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `직원 보고서 다운로드 테스트`() {
        mockMvc.get("/api/reports/employees/download")
            .andExpect {
                status { isOk() }
                header {
                    string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                }
                header {
                    exists("Content-Disposition")
                }
            }
    }

    @Test
    fun `비동기 보고서 생성 요청 테스트`() {
        mockMvc.post("/api/reports/employees/async") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "title": "테스트 보고서",
                    "startDate": "2026-01-01",
                    "endDate": "2026-01-31"
                }
            """.trimIndent()
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.jobId") { exists() }
        }
    }
}
```

---

## 다음 단계

- [사용자 가이드](../user-guide.md) - 전체 가이드
- [API 레퍼런스](../reference/api-reference.md) - API 상세
- [설정 옵션 레퍼런스](../reference/configuration.md) - 설정 옵션
