> **[한국어](../../ko/examples/spring-boot-examples.md)** | English

# TBEG Spring Boot Examples

## Table of Contents
1. [Setup](#1-setup)
2. [Basic Service Pattern](#2-basic-service-pattern)
3. [File Download from Controller](#3-file-download-from-controller)
4. [Asynchronous Report Generation API](#4-asynchronous-report-generation-api)
5. [JPA Stream Integration](#5-jpa-stream-integration)
6. [Event-Based Notifications](#6-event-based-notifications)
7. [Writing Tests](#7-writing-tests)

---

## 1. Setup

### Adding Repository and Dependency

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.2.2")
}
```

> [!TIP]
> For detailed setup instructions (Groovy DSL, Maven), see the [User Guide](../user-guide.md#11-adding-dependencies).

### application.yml

```yaml
tbeg:
  # streaming-mode: deprecated (the value is ignored starting from 1.2.0)
  file-naming-mode: timestamp       # none, timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence    # error, sequence
  preserve-template-layout: true
  missing-data-behavior: warn       # warn, throw
```

### Auto-Configuration

Adding the `tbeg` dependency automatically activates `TbegAutoConfiguration`:

- `ExcelGenerator` bean is registered automatically
- `TbegProperties` binding
- Automatic cleanup on application shutdown

---

## 2. Basic Service Pattern

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
     * Generate a simple report using a Map
     */
    fun generateSimpleReport(): ByteArray {
        val template = resourceLoader.getResource("classpath:templates/simple.xlsx")

        val data = mapOf(
            "title" to "Simple Report",
            "date" to LocalDate.now().toString(),
            "author" to "System"
        )

        return excelGenerator.generate(template.inputStream, data)
    }

    /**
     * Generate a report using a DataProvider
     */
    fun generateEmployeeReport(): Path {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")

        // Query count first (SELECT COUNT uses only indexes, so it's fast)
        val employeeCount = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "Employee Status Report")
            value("date", LocalDate.now().toString())

            // Provide count along with lazy loading
            // - TBEG can determine total row count upfront for immediate formula range calculation
            // - Prevents double iteration for performance optimization
            items("employees", employeeCount) {
                employeeRepository.findAll().iterator()
            }

            metadata {
                title = "Employee Status Report"
                author = "HR System"
                company = "Hunet Inc."
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
        data.put("title", "Simple Report");
        data.put("date", LocalDate.now().toString());
        data.put("author", "System");

        return excelGenerator.generate(template.getInputStream(), data);
    }

    public Path generateEmployeeReport() throws IOException {
        var template = resourceLoader.getResource("classpath:templates/employees.xlsx");

        // Query count first (for formula range calculation and preventing double iteration)
        int employeeCount = (int) employeeRepository.count();

        var provider = SimpleDataProvider.builder()
            .value("title", "Employee Status Report")
            .value("date", LocalDate.now().toString())
            .itemsFromSupplier("employees", employeeCount,
                () -> employeeRepository.findAll().iterator())
            .metadata(meta -> meta
                .title("Employee Status Report")
                .author("HR System")
                .company("Hunet Inc."))
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

## 3. File Download from Controller

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
     * Download employee report
     */
    @GetMapping("/employees/download")
    fun downloadEmployeeReport(): ResponseEntity<Resource> {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")

        val data = mapOf(
            "title" to "Employee Status",
            "date" to LocalDate.now().toString(),
            "employees" to employeeRepository.findAll()
        )

        val bytes = excelGenerator.generate(template.inputStream, data)

        val filename = "employee_status_${LocalDate.now()}.xlsx"
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
     * Download department-specific report
     */
    @GetMapping("/departments/{deptId}/download")
    fun downloadDepartmentReport(
        @PathVariable deptId: Long
    ): ResponseEntity<Resource> {
        val template = resourceLoader.getResource("classpath:templates/department.xlsx")
        val department = departmentRepository.findById(deptId)
            .orElseThrow { NoSuchElementException("Department not found: $deptId") }

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
        data.put("title", "Employee Status");
        data.put("date", LocalDate.now().toString());
        data.put("employees", employeeRepository.findAll());

        byte[] bytes = excelGenerator.generate(template.getInputStream(), data);

        String filename = "employee_status_" + LocalDate.now() + ".xlsx";
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

## 4. Asynchronous Report Generation API

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

// Request/Response DTOs
data class ReportRequest(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class JobResponse(
    val jobId: String,
    val message: String = "Report generation has started."
)

// Events
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
                    // Publish completion event
                    eventPublisher.publishEvent(
                        ReportReadyEvent(
                            jobId = jobId,
                            filePath = result.filePath!!,
                            rowsProcessed = result.rowsProcessed
                        )
                    )
                }

                override fun onFailed(jobId: String, error: Exception) {
                    // Publish failure event
                    eventPublisher.publishEvent(
                        ReportFailedEvent(
                            jobId = jobId,
                            errorMessage = error.message
                        )
                    )
                }
            }
        )

        // Return 202 Accepted immediately
        return ResponseEntity.accepted().body(JobResponse(job.jobId))
    }
}
```

### Event Handler

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
        // Send email
        emailService.sendReportReadyEmail(
            jobId = event.jobId,
            downloadUrl = "/api/reports/download/${event.filePath.fileName}"
        )

        // Push notification
        notificationService.sendNotification(
            title = "Report generation completed",
            message = "A report with ${event.rowsProcessed} records has been generated."
        )
    }

    @EventListener
    fun handleReportFailed(event: ReportFailedEvent) {
        notificationService.sendNotification(
            title = "Report generation failed",
            message = "Error: ${event.errorMessage}"
        )
    }
}
```

---

## 5. JPA Stream Integration

Process large datasets in a memory-efficient manner.

```kotlin
@Service
class LargeReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader,
    private val employeeRepository: EmployeeRepository
) {
    /**
     * Generate a large employee report
     */
    @Transactional(readOnly = true)  // Required to keep the Stream alive
    fun generateLargeEmployeeReport(): Path {
        val template = resourceLoader.getResource("classpath:templates/employees.xlsx")
        val employeeCount = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "All Employee Status")

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
> When using JPA Streams, you must use the `@Transactional` annotation. The Stream is closed when the transaction ends, so the transaction must remain active until the Excel generation is complete.

For detailed information on Repository interface definitions, paged Iterator implementations, and MyBatis integration, see [Advanced Examples - JPA/Spring Data Integration](./advanced-examples.md#13-jpaspring-data-integration).

---

## 6. Event-Based Notifications

### WebSocket Integration

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
        // Calculate percentage (only when totalRows is available)
        val percentage = progress.totalRows?.let { total ->
            if (total > 0) (progress.processedRows * 100.0 / total) else null
        }

        sendMessage(jobId, "progress", mapOf(
            "jobId" to jobId,
            "processedRows" to progress.processedRows,
            "totalRows" to progress.totalRows,
            "percentage" to percentage  // excluded from JSON when null
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
            "error" to (error.message ?: "Unknown error")
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

### Using in a Controller

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
        listener = webSocketReportListener  // Inject WebSocket listener
    )

    return ResponseEntity.accepted().body(JobResponse(job.jobId))
}
```

---

## 7. Writing Tests

### Service Test

```kotlin
package com.example.report

import io.github.jogakdal.tbeg.ExcelGenerator
import io.mockk.every
import io.mockk.mockk
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.io.ByteArrayInputStream

class ReportServiceTest {

    private val excelGenerator = ExcelGenerator()
    private val employeeRepository = mockk<EmployeeRepository>()

    @Test
    fun `employee report generation test`() {
        // Given
        every { employeeRepository.findAll() } returns listOf(
            Employee(1, "Yongho Hwang", "Common Platform Team", 5000),
            Employee(2, "Yongho Hong", "IT Strategy Team", 4500)
        )

        val template = ClassPathResource("templates/employees.xlsx")

        // When
        val bytes = excelGenerator.generate(
            template.inputStream,
            mapOf(
                "title" to "Test Report",
                "employees" to employeeRepository.findAll()
            )
        )

        // Then
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // Verify title
            assertEquals("Test Report", sheet.getRow(0).getCell(0).stringCellValue)

            // Verify data rows
            assertEquals("Yongho Hwang", sheet.getRow(2).getCell(0).stringCellValue)
            assertEquals("Yongho Hong", sheet.getRow(3).getCell(0).stringCellValue)
        }
    }
}
```

### Controller Integration Test

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
    fun `employee report download test`() {
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
    fun `async report generation request test`() {
        mockMvc.post("/api/reports/employees/async") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "title": "Test Report",
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

## Next Steps

- [User Guide](../user-guide.md) - Complete guide
- [API Reference](../reference/api-reference.md) - API details
- [Configuration Reference](../reference/configuration.md) - Configuration options
