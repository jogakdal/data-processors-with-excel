> **[한국어](../../ko/examples/advanced-examples.md)** | English

# TBEG Advanced Examples

## Table of Contents
1. [DataProvider Usage](#1-dataprovider-usage)
   - [1.1 simpleDataProvider DSL](#11-simpledataprovider-dsl)
   - [1.2 Direct ExcelDataProvider Implementation](#12-direct-exceldataprovider-implementation)
   - [1.3 JPA/Spring Data Integration](#13-jpaspring-data-integration)
   - [1.4 MyBatis Integration](#14-mybatis-integration)
   - [1.5 External API Integration](#15-external-api-integration)
2. [Asynchronous Processing](#2-asynchronous-processing)
3. [Variables in Formulas](#3-variables-in-formulas)
4. [Hyperlinks](#4-hyperlinks)
5. [Multiple Sheets](#5-multiple-sheets)
6. [Large-Scale Data Processing](#6-large-scale-data-processing)
7. [Multiple Repeat Regions](#7-multiple-repeat-regions)
8. [Rightward Repeat](#8-rightward-repeat)
9. [Empty Collection Handling](#9-empty-collection-handling)
10. [Internationalization (I18N)](#10-internationalization-i18n)
11. [Comprehensive Example -- Quarterly Sales Performance Report](#11-comprehensive-example--quarterly-sales-performance-report)
12. [Automatic Cell Merge in Practice](#12-automatic-cell-merge-in-practice)
13. [Bundle](#13-bundle)

> [!NOTE]
> The examples in this document load templates from the `resources/templates/` directory.
> To read directly from the file system, use `File("template.xlsx").inputStream()`.

---

## 1. DataProvider Usage

DataProvider is a core concept in TBEG. It supports **lazy loading** and **streaming** for efficient processing of large datasets.

### Comparison of Data Supply Methods

| Method | Memory Usage | Best For |
|--------|-------------|----------|
| `Map<String, Any>` | Loads all at once | Small datasets, simple reports |
| `simpleDataProvider` DSL | Lazy loading | Medium-scale, general use |
| `ExcelDataProvider` implementation | Full control | Large-scale, direct DB integration |

---

### 1.1 simpleDataProvider DSL

The most convenient approach using the Kotlin DSL.

#### Template (template.xlsx)

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${title}                         |                 |               |
| 2 | Date: ${date}                    | Author: ${author}  |               |
| 3 | ${repeat(employees, A5:C5, emp)} |                 |               |
| 4 | Name                             | Position        | Salary        |
| 5 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

- **Single values**: `${title}`, `${date}`, `${author}` -- supplied via DataProvider's `value()`
- **Repeat region**: `${repeat(employees, A5:C5, emp)}` -- supplied via DataProvider's `items()`
- **Item properties**: `${emp.name}`, `${emp.position}`, `${emp.salary}` -- field references for each item

#### Basic Usage

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider
import java.time.LocalDate

// Define data class
data class Employee(val name: String, val position: String, val salary: Int)

val provider = simpleDataProvider {
    // Single values
    value("title", "Employee Status Report")
    value("date", LocalDate.now())
    value("author", "Yongho Hwang")

    // Collection (List)
    items("employees", listOf(
        Employee("Yongho Hwang", "Director", 8000),
        Employee("Yongho Han", "Manager", 6500)
    ))
}
```

#### Lazy Loading (Lambda)

Defers data loading until it is actually needed.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.time.LocalDate

data class Employee(val name: String, val position: String, val salary: Int)

// Function that queries employee count (SELECT COUNT(*) query)
fun countEmployees(): Int {
    // With JPA:
    // return employeeRepository.count().toInt()
    // return employeeRepository.countByDepartmentId(deptId)

    // Dummy data for example
    return 3
}

// Function that streams employee list
fun streamEmployees(): Iterator<Employee> {
    // With JPA:
    // return employeeRepository.findAll().iterator()
    // return employeeRepository.streamAll().iterator()  // for large datasets

    // Dummy data for example
    return listOf(
        Employee("Yongho Hwang", "Director", 8000),
        Employee("Yongho Han", "Manager", 6500),
        Employee("Yongho Hong", "Assistant Manager", 4500)
    ).iterator()
}

// 1. Query the count first (lightweight query)
val employeeCount = countEmployees()

// 2. Create DataProvider (collection data is NOT loaded at this point)
val provider = simpleDataProvider {
    // Single values
    value("title", "Employee Status Report")
    value("date", LocalDate.now())
    value("author", "Yongho Hwang")

    // Collection: provide count along with lazy loading
    items("employees", employeeCount) {
        // This block is executed at Excel generation time
        streamEmployees()
    }
}

// 3. Generate Excel (the lambda is invoked here, loading the data)
ExcelGenerator().use { generator ->
    val template = javaClass.getResourceAsStream("/templates/template.xlsx")
        ?: throw IllegalStateException("Template not found")

    val result = generator.generate(template, provider)
    File("output.xlsx").writeBytes(result)
}
```

**Execution flow:**
1. `countEmployees()` is called -- only the count is queried first (lightweight query)
2. `simpleDataProvider { ... }` is called -- Provider object is created (collection data is NOT loaded)
3. `generator.generate(template, provider)` is called
4. When the `employees` data is needed during template processing, the lambda executes and queries the DB
5. The generated Excel byte array is saved to a file

**Why providing a count is recommended:**
- TBEG can determine the total row count upfront and calculate formula ranges immediately
- No need to iterate over the data twice
- A `SELECT COUNT(*)` query in the DB is very fast as it typically uses only indexes

> [!NOTE]
> Everything works correctly even without providing a count. However, TBEG will need to traverse the collection first to determine the total row count, which may cause a performance penalty due to double iteration.

#### Including Images

```kotlin
import java.io.File
import java.net.URL

// Function that downloads an image from a server (example)
fun downloadImage(imageUrl: String): ByteArray {
    // Method 1: Java URL (for simple cases)
    return URL(imageUrl).readBytes()

    // Method 2: Spring RestTemplate
    // return restTemplate.getForObject(imageUrl, ByteArray::class.java)!!

    // Method 3: Spring WebClient (reactive)
    // return webClient.get().uri(imageUrl).retrieve().bodyToMono<ByteArray>().block()!!
}

val provider = simpleDataProvider {
    value("company", "Hunet Inc.")

    // Image - eager loading (load from resources directory)
    image("logo", javaClass.getResourceAsStream("/images/logo.png")!!.readBytes())
    // To read directly from file: image("logo", File("logo.png").readBytes())

    // Image - lazy loading (Lambda)
    image("signature") {
        // This block is called when the image is actually needed
        // e.g., download user signature image from server
        downloadImage("https://example.com/signatures/user123.png")
    }
}
```

**When lazy loading is useful:**
- When images need to be downloaded from an external server
- When image generation takes time (e.g., rendering chart images)
- When the image may not be needed depending on conditions

#### Document Metadata

```kotlin
val provider = simpleDataProvider {
    value("title", "Report")

    metadata {
        title = "2026 Monthly Report"
        author = "Yongho Hwang"
        subject = "Monthly Performance"
        keywords("monthly", "report", "performance")
        company = "Hunet Inc."
    }
}
```

#### Java Builder Pattern

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "Employee Status Report")
    .value("date", LocalDate.now())
    .items("employees", employeeList)
    .items("employees", employeeCount, () -> fetchEmployees())  // count + lambda
    .image("logo", logoBytes)  // eager loading
    .imageFromSupplier("signature", () -> downloadSignature())  // lazy loading
    .metadata(meta -> meta
        .title("Report")
        .author("Yongho Hwang"))
    .build();
```

---

### 1.2 Direct ExcelDataProvider Implementation

While the `simpleDataProvider` DSL is sufficient for most cases, directly implementing the interface is advantageous in the following situations.

#### Comparison with SimpleDataProvider

| Aspect | SimpleDataProvider | Direct Implementation |
|--------|-------------------|----------------------|
| Data dependencies | Not possible | Possible (inter-method calls) |
| Caching query results | Workaround via external lambda variables | Natural via class fields |
| Conditional data supply | Limited to within lambdas | Free branching logic |
| Resource cleanup (DB cursors, etc.) | Not possible | Implement `Closeable` |
| Unit testing | Requires full replacement | Easy mock injection of repositories |

#### Interface Structure

```kotlin
interface ExcelDataProvider {
    fun getValue(name: String): Any?           // Single value
    fun getItems(name: String): Iterator<Any>? // Collection (Iterator)
    fun getImage(name: String): ByteArray?     // Image (optional)
    fun getMetadata(): DocumentMetadata?       // Metadata (optional)
    fun getItemCount(name: String): Int?       // Item count (optional, performance optimization)
}
```

#### Kotlin Implementation Example

```kotlin
import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.DocumentMetadata
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EmployeeReportDataProvider(
    private val departmentId: Long,
    private val reportDate: LocalDate,
    private val employeeRepository: EmployeeRepository
) : ExcelDataProvider, Closeable {

    // [Advantage 1] Caching query results - naturally managed as class fields
    private var cachedCount: Int? = null
    private var cachedDepartmentName: String? = null

    // [Advantage 2] Resource cleanup - able to clean up Stream/Cursor, etc.
    private var employeeStream: java.util.stream.Stream<Employee>? = null

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "Employee Status by Department"

        // Caching: performs DB query only once when the same value is referenced from multiple cells
        "departmentName" -> cachedDepartmentName
            ?: employeeRepository.getDepartmentName(departmentId)
                .also { cachedDepartmentName = it }

        "reportDate" -> reportDate.toString()

        // Dynamic value: current time at invocation
        "generatedAt" -> LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        // [Advantage 3] Data dependencies - leveraging results from other methods
        "summary" -> "Total of ${getOrLoadCount()} members in ${cachedDepartmentName ?: "Department"}"

        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> {
            // Store Stream in a field so it can be cleaned up in close()
            employeeStream = employeeRepository.streamByDepartmentId(departmentId)
            employeeStream!!.iterator()
        }

        // [Advantage 4] Conditional data supply - provide separate manager list only for large departments
        "managers" -> if (getOrLoadCount() > 50) {
            employeeRepository.findManagersByDepartmentId(departmentId).iterator()
        } else {
            null  // No manager list for small departments
        }

        else -> null
    }

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> getOrLoadCount()
        "managers" -> if (getOrLoadCount() > 50) {
            employeeRepository.countManagersByDepartmentId(departmentId)
        } else null
        else -> null
    }

    override fun getMetadata(): DocumentMetadata = DocumentMetadata(
        title = "${cachedDepartmentName ?: "Department"} Employee Status Report",
        author = "HR System",
        subject = "Employee Status",
        company = "Hunet Inc."
    )

    // Internal helper: count caching logic
    private fun getOrLoadCount(): Int =
        cachedCount ?: employeeRepository.countByDepartmentId(departmentId)
            .also { cachedCount = it }

    // [Advantage 2] Resource cleanup implementation
    override fun close() {
        employeeStream?.close()
    }
}
```

#### Usage Example

```kotlin
@Service
class ReportService(
    private val employeeRepository: EmployeeRepository,
    private val resourceLoader: ResourceLoader
) {
    @Transactional(readOnly = true)
    fun generateDepartmentReport(departmentId: Long): ByteArray {
        // Since it implements Closeable, use a `use` block for automatic resource cleanup
        EmployeeReportDataProvider(
            departmentId = departmentId,
            reportDate = LocalDate.now(),
            employeeRepository = employeeRepository
        ).use { provider ->
            return ExcelGenerator().use { generator ->
                val template = resourceLoader.getResource("classpath:templates/department_report.xlsx")
                generator.generate(template.inputStream, provider)
            }
        }
    }
}
```

> [!WARNING]
> Streams must be used within a `@Transactional` scope. Once the transaction ends, the DB connection is closed and the Stream becomes invalid.

#### Java Implementation Example

```java
public class EmployeeReportDataProvider implements ExcelDataProvider, Closeable {

    private final Long departmentId;
    private final LocalDate reportDate;
    private final EmployeeRepository repository;

    // [Advantage 1] Caching query results
    private Integer cachedCount = null;
    private String cachedDepartmentName = null;

    // [Advantage 2] For resource cleanup
    private Stream<Employee> employeeStream = null;

    public EmployeeReportDataProvider(Long departmentId, LocalDate reportDate,
                                      EmployeeRepository repository) {
        this.departmentId = departmentId;
        this.reportDate = reportDate;
        this.repository = repository;
    }

    @Override
    public Object getValue(String name) {
        return switch (name) {
            case "title" -> "Employee Status by Department";
            case "departmentName" -> getOrLoadDepartmentName();
            case "reportDate" -> reportDate.toString();
            case "generatedAt" -> LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            // [Advantage 3] Data dependencies
            case "summary" -> "Total of " + getOrLoadCount() + " members in " + getOrLoadDepartmentName();
            default -> null;
        };
    }

    @Override
    public Iterator<Object> getItems(String name) {
        return switch (name) {
            case "employees" -> {
                employeeStream = repository.streamByDepartmentId(departmentId);
                yield employeeStream.map(e -> (Object) e).iterator();
            }
            // [Advantage 4] Conditional data supply
            case "managers" -> getOrLoadCount() > 50
                ? repository.findManagersByDepartmentId(departmentId)
                    .stream().map(e -> (Object) e).iterator()
                : null;
            default -> null;
        };
    }

    @Override
    public Integer getItemCount(String name) {
        return switch (name) {
            case "employees" -> getOrLoadCount();
            case "managers" -> getOrLoadCount() > 50
                ? repository.countManagersByDepartmentId(departmentId)
                : null;
            default -> null;
        };
    }

    private int getOrLoadCount() {
        if (cachedCount == null) {
            cachedCount = repository.countByDepartmentId(departmentId);
        }
        return cachedCount;
    }

    private String getOrLoadDepartmentName() {
        if (cachedDepartmentName == null) {
            cachedDepartmentName = repository.getDepartmentName(departmentId);
        }
        return cachedDepartmentName;
    }

    // [Advantage 2] Resource cleanup
    @Override
    public void close() {
        if (employeeStream != null) {
            employeeStream.close();
        }
    }
}
```

#### Java Usage Example

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final ResourceLoader resourceLoader;

    @Transactional(readOnly = true)
    public byte[] generateDepartmentReport(Long departmentId) throws IOException {
        try (var provider = new EmployeeReportDataProvider(
                departmentId, LocalDate.now(), employeeRepository);
             var generator = new ExcelGenerator();
             var template = resourceLoader.getResource("classpath:templates/department_report.xlsx")
                .getInputStream()) {

            return generator.generate(template, provider);
        }
    }
}
```

---

### 1.3 JPA/Spring Data Integration

#### Repository Interface

```kotlin
interface EmployeeRepository : JpaRepository<Employee, Long> {

    // Count query (for performance optimization)
    fun countByDepartmentId(departmentId: Long): Int

    // Stream return (for large-scale processing)
    @QueryHints(QueryHint(name = HINT_FETCH_SIZE, value = "100"))
    fun streamByDepartmentId(departmentId: Long): Stream<Employee>

    // Or Slice-based pagination
    fun findByDepartmentId(departmentId: Long, pageable: Pageable): Slice<Employee>
}
```

#### Stream-Based DataProvider

```kotlin
@Service
class ReportService(
    private val employeeRepository: EmployeeRepository,
    private val excelGenerator: ExcelGenerator
) {
    @Transactional(readOnly = true)
    fun generateReport(departmentId: Long): ByteArray {
        val count = employeeRepository.countByDepartmentId(departmentId)

        val provider = simpleDataProvider {
            value("title", "Employee Status")
            value("date", LocalDate.now())

            items("employees", count) {
                // Use Stream within @Transactional
                employeeRepository.streamByDepartmentId(departmentId).iterator()
            }
        }

        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        return excelGenerator.generate(template.inputStream, provider)
    }
}
```

#### Paged Iterator (Memory-Efficient)

An Iterator implementation that fetches large datasets page by page:

```kotlin
class PagedIterator<T>(
    private val pageSize: Int = 1000,
    private val fetcher: (Pageable) -> Slice<T>
) : Iterator<T> {

    private var currentPage = 0
    private var currentIterator: Iterator<T> = emptyList<T>().iterator()
    private var hasMorePages = true

    override fun hasNext(): Boolean {
        if (currentIterator.hasNext()) return true
        if (!hasMorePages) return false

        // Load next page
        val slice = fetcher(PageRequest.of(currentPage++, pageSize))
        currentIterator = slice.content.iterator()
        hasMorePages = slice.hasNext()

        return currentIterator.hasNext()
    }

    override fun next(): T = currentIterator.next()
}
```

Usage example:

```kotlin
val provider = simpleDataProvider {
    value("title", "Large-Scale Report")

    items("employees", employeeCount) {
        PagedIterator(pageSize = 1000) { pageable ->
            employeeRepository.findByDepartmentId(departmentId, pageable)
        }
    }
}
```

---

### 1.4 MyBatis Integration

#### Mapper Interface

```kotlin
@Mapper
interface EmployeeMapper {

    fun countByDepartmentId(departmentId: Long): Int

    // Cursor-based query (streaming)
    @Options(fetchSize = 100)
    fun selectByDepartmentIdWithCursor(departmentId: Long): Cursor<Employee>
}
```

#### DataProvider Implementation

```kotlin
class MyBatisEmployeeDataProvider(
    private val departmentId: Long,
    private val employeeMapper: EmployeeMapper
) : ExcelDataProvider {

    private var cursor: Cursor<Employee>? = null

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "Employee Status"
        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> {
            cursor = employeeMapper.selectByDepartmentIdWithCursor(departmentId)
            cursor!!.iterator()
        }
        else -> null
    }

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> employeeMapper.countByDepartmentId(departmentId)
        else -> null
    }

    fun close() {
        cursor?.close()
    }
}
```

#### Cursor Resource Cleanup

MyBatis Cursors maintain a database connection, so they must be closed after use.

```kotlin
@Transactional(readOnly = true)
fun generateReport(departmentId: Long): ByteArray {
    val provider = MyBatisEmployeeDataProvider(departmentId, employeeMapper)

    try {
        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        return excelGenerator.generate(template.inputStream, provider)
    } finally {
        provider.close()  // Failing to close the Cursor will cause a DB connection leak
    }
}
```

> [!WARNING]
> Cursors must be used within a `@Transactional` scope. Once the transaction ends, the Cursor becomes invalid.

---

### 1.5 External API Integration

In a microservice architecture, this pattern fetches data from another service's API in **paginated** chunks and converts it to Excel.

#### Spring Data Page-Based Iterator

This leverages Spring Data's `Page<T>` type to convert paginated API responses into an Iterator.

```kotlin
import org.springframework.data.domain.Page

class PagedApiIterator<T>(
    private val pageSize: Int = 100,
    private val fetcher: (page: Int, size: Int) -> Page<T>
) : Iterator<T> {

    private var currentPage = 0
    private var currentIterator: Iterator<T> = emptyList<T>().iterator()
    private var hasMorePages = true

    override fun hasNext(): Boolean {
        if (currentIterator.hasNext()) return true
        if (!hasMorePages) return false

        // Load next page (API call)
        val result = fetcher(currentPage++, pageSize)
        currentIterator = result.content.iterator()
        hasMorePages = result.hasNext()

        return currentIterator.hasNext()
    }

    override fun next(): T = currentIterator.next()
}
```

#### Usage Example

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.io.File

data class EmployeeDto(val name: String, val salary: Int)

// Feign Client interface definition
// @FeignClient(name = "employee-service")
// interface EmployeeApiClient {
//     @GetMapping("/api/employees")
//     fun getEmployees(
//         @RequestParam("page") page: Int,
//         @RequestParam("size") size: Int
//     ): Page<EmployeeDto>
// }

// Fetch data by calling another microservice's API
fun fetchEmployeesFromApi(page: Int, size: Int): Page<EmployeeDto> {
    // With Feign Client:
    // return employeeApiClient.getEmployees(page, size)

    // With RestTemplate:
    // return restTemplate.exchange(
    //     "/api/employees?page=$page&size=$size",
    //     HttpMethod.GET,
    //     null,
    //     object : ParameterizedTypeReference<RestPageImpl<EmployeeDto>>() {}
    // ).body ?: throw Exception("API call failed")

    // With WebClient:
    // return webClient.get()
    //     .uri("/api/employees?page=$page&size=$size")
    //     .retrieve()
    //     .bodyToMono<RestPageImpl<EmployeeDto>>()
    //     .block() ?: throw Exception("API call failed")

    // Dummy response for example
    val content = listOf(EmployeeDto("Yongho Hwang", 8000), EmployeeDto("Yongho Han", 6500))
    return PageImpl(content, PageRequest.of(page, size), 100)
}

fun main() {
    // First, query totalElements (via first page call or separate count API)
    val firstPage = fetchEmployeesFromApi(0, 1)
    val totalCount = firstPage.totalElements.toInt()

    val provider = simpleDataProvider {
        value("title", "API Data Report")

        items("employees", totalCount) {
            PagedApiIterator(pageSize = 50) { page, size ->
                fetchEmployeesFromApi(page, size)
            }
        }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val result = generator.generate(template, provider)
        File("api_report.xlsx").writeBytes(result)
    }
}
```

> [!NOTE]
> Spring Data's `Page<T>` uses zero-based page numbers. When deserializing `Page` from a REST API, `PageImpl` lacks a default constructor, so you may need a custom class like `RestPageImpl` or define a `@JsonCreator`.

---

## 2. Asynchronous Processing

### 2.1 Kotlin Coroutines

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    val provider = simpleDataProvider {
        value("title", "Async Report")
        items("data") { generateData().iterator() }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")

        // Asynchronous generation
        val path = generator.generateToFileAsync(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("./output"),
            baseFileName = "async_report"
        )

        println("File created: $path")
    }
}

fun generateData() = (1..1000).map { mapOf("id" to it, "value" to it * 10) }
```

### 2.2 Java CompletableFuture

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AsyncWithFuture {
    public static void main(String[] args) throws Exception {
        SimpleDataProvider provider = SimpleDataProvider.builder()
            .value("title", "Async Report")
            .items("data", generateData())
            .build();

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = AsyncWithFuture.class.getResourceAsStream("/templates/template.xlsx")) {

            CompletableFuture<Path> future = generator.generateToFileFuture(
                template,
                provider,
                Path.of("./output"),
                "async_report"
            );

            // Callback on completion
            future.thenAccept(path -> {
                System.out.println("File created: " + path);
            });

            // Wait for completion
            Path result = future.get();
        }
    }

    private static List<Map<String, Object>> generateData() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            data.add(Map.of("id", i, "value", i * 10));
        }
        return data;
    }
}
```

### 2.3 Background Job + Listener

Respond immediately from the API server and process in the background.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main() {
    val latch = CountDownLatch(1)

    val provider = simpleDataProvider {
        value("title", "Background Report")
        items("data") { (1..5000).map { mapOf("id" to it) }.iterator() }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val job = generator.submitToFile(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("./output"),
            baseFileName = "background_report",
            listener = object : ExcelGenerationListener {
                override fun onStarted(jobId: String) {
                    println("[Started] Job ID: $jobId")
                }

                override fun onCompleted(jobId: String, result: GenerationResult) {
                    println("[Completed] File: ${result.filePath}")
                    println("[Completed] Rows processed: ${result.rowsProcessed}")
                    println("[Completed] Duration: ${result.durationMs}ms")
                    latch.countDown()
                }

                override fun onFailed(jobId: String, error: Exception) {
                    println("[Failed] ${error.message}")
                    latch.countDown()
                }

                override fun onCancelled(jobId: String) {
                    println("[Cancelled]")
                    latch.countDown()
                }
            }
        )

        println("Job submitted: ${job.jobId}")
        println("(In an API server, you would return HTTP 202 here)")

        // Example of cancelling a job
        // job.cancel()

        latch.await(60, TimeUnit.SECONDS)
    }
}
```

---

## 3. Variables in Formulas

### Template (formula_template.xlsx)

|   | A         | B                             |
|---|-----------|-------------------------------|
| 1 | Start Row | ${startRow}                   |
| 2 | End Row   | ${endRow}                     |
| 3 |           |                               |
| 4 | Data 1    | 100                           |
| 5 | Data 2    | 200                           |
| 6 | Data 3    | 300                           |
| 7 |           |                               |
| 8 | Total     | =SUM(B${startRow}:B${endRow}) |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

fun main() {
    val data = mapOf(
        "startRow" to 4,
        "endRow" to 6
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/formula_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("formula_output.xlsx").writeBytes(bytes)
    }
}
```

### Result

|   | A     | B                  |
|---|-------|--------------------|
| 8 | Total | =SUM(B4:B6) -> 600 |

---

## 4. Hyperlinks

### Template (link_template.xlsx)

Set a HYPERLINK formula in cell A1:
```
=HYPERLINK("${url}", "${text}")
```

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

fun main() {
    val data = mapOf(
        "text" to "Visit Website",
        "url" to "https://example.com"
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/link_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("link_output.xlsx").writeBytes(bytes)
    }
}
```

---

## 5. Multiple Sheets

### Template (multi_sheet_template.xlsx)

**Summary sheet**:

|   | A              | B                  |
|---|----------------|--------------------|
| 1 | Title          | ${title}           |
| 2 | Total Employees | ${size(employees)} |

**Employees sheet**:

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | Name                               | Position        | Salary        |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val employees = listOf(
        Employee("Yongho Hwang", "Director", 8000),
        Employee("Yongho Han", "Manager", 6500),
        Employee("Yongho Hong", "Assistant Manager", 4500)
    )

    val data = mapOf(
        "title" to "Employee Status",
        "employees" to employees
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_sheet_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("multi_sheet_output.xlsx").writeBytes(bytes)
    }
}
```

---

## 6. Large-Scale Data Processing

### Recommended Configuration

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path

fun main() {
    // Configuration for large-scale data
    val config = TbegConfig(
        streamingMode = StreamingMode.ENABLED,  // Enable streaming mode
        progressReportInterval = 1000           // Report progress every 1000 rows
    )

    // Data count (queried via DB COUNT query)
    val dataCount = 1_000_000

    // Provide data via lazy loading
    val provider = simpleDataProvider {
        value("title", "Large-Scale Report")

        // Provide count along with lazy loading (optimal performance)
        items("data", dataCount) {
            // Simulate 1 million records
            (1..dataCount).asSequence().map {
                mapOf("id" to it, "value" to it * 10)
            }.iterator()
        }
    }

    ExcelGenerator(config).use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val path = generator.generateToFile(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("./output"),
            baseFileName = "large_report"
        )

        println("File created: $path")
    }
}
```

---

## 7. Multiple Repeat Regions

You can use multiple repeat regions in a single sheet.

### Template (multi_repeat.xlsx)

|   | A                                | B             | C | D                                   | E              |
|---|----------------------------------|---------------|---|-------------------------------------|----------------|
| 1 | ${repeat(employees, A3:B3, emp)} |               |   | ${repeat(departments, D3:E3, dept)} |                |
| 2 | Name                             | Salary        |   | Department                          | Budget         |
| 3 | ${emp.name}                      | ${emp.salary} |   | ${dept.name}                        | ${dept.budget} |

### Kotlin Code (Map Approach)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val salary: Int)
data class Department(val name: String, val budget: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("Yongho Hwang", 8000),
            Employee("Yongho Han", 6500),
            Employee("Yongho Hong", 4500)
        ),
        "departments" to listOf(
            Department("Common Platform Team", 50000),
            Department("IT Strategy Team", 30000)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Kotlin Code (simpleDataProvider DSL - Lazy Loading)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider

data class Employee(val name: String, val salary: Int)
data class Department(val name: String, val budget: Int)

fun main() {
    // Query count for each collection
    val employeeCount = 3   // employeeRepository.count().toInt()
    val departmentCount = 2 // departmentRepository.count().toInt()

    val provider = simpleDataProvider {
        // First collection: employees
        items("employees", employeeCount) {
            // employeeRepository.findAll().iterator()
            listOf(
                Employee("Yongho Hwang", 8000),
                Employee("Yongho Han", 6500),
                Employee("Yongho Hong", 4500)
            ).iterator()
        }

        // Second collection: departments
        items("departments", departmentCount) {
            // departmentRepository.findAll().iterator()
            listOf(
                Department("Common Platform Team", 50000),
                Department("IT Strategy Team", 30000)
            ).iterator()
        }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Result

|   | A    | B     | C | D        | E      |
|---|------|-------|---|----------|--------|
| 1 |      |       |   |          |        |
| 2 | Name | Salary|   | Department | Budget |
| 3 | Yongho Hwang  | 8,000 |   | Common Platform Team | 50,000 |
| 4 | Yongho Han    | 6,500 |   | IT Strategy Team     | 30,000 |
| 5 | Yongho Hong   | 4,500 |   |                      |        |

> [!NOTE]
> Each repeat region expands independently. In the example above, there are 3 employees and 2 departments, so each expands by a different number of rows.

> [!IMPORTANT]
> Repeat regions must not overlap in 2D space.

---

## 8. Rightward Repeat

### Template (right_repeat.xlsx)

|   | A                                       | B             |
|---|-----------------------------------------|---------------|
| 1 | ${repeat(months, B1:B2, m, RIGHT)}      | ${m.month}     |
| 2 |                                         | ${m.sales}    |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class MonthData(val month: Int, val sales: Int)

fun main() {
    val data = mapOf(
        "months" to listOf(
            MonthData(1, 1000),
            MonthData(2, 1500),
            MonthData(3, 2000),
            MonthData(4, 1800)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/right_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Result

|   | A  | B      | C      | D      | E      |
|---|----|--------|--------|--------|--------|
| 1 |    | Jan    | Feb    | Mar    | Apr    |
| 2 |    | 1,000  | 1,500  | 2,000  | 1,800  |

---

## 9. Empty Collection Handling

When a collection is empty, you can display a message such as "No data found."

### Template (empty_collection.xlsx)

|   | A                                              | B               | C             |
|---|------------------------------------------------|-----------------|---------------|
| 1 | Employee Status                                |                 |               |
| 2 | ${repeat(employees, A4:C4, emp, DOWN, A7:C7)}  |                 |               |
| 3 | Name                                           | Position        | Salary        |
| 4 | ${emp.name}                                    | ${emp.position} | ${emp.salary} |
| 5 |                                                |                 |               |
| 6 |                                                |                 |               |
| 7 | No employees found.                            |                 |               |

- **A2**: The repeat marker specifies `A7:C7` as the `empty` parameter
- **A7:C7**: Content to display when the collection is empty (merged cells are supported)

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    // Empty collection
    val provider = simpleDataProvider {
        items("employees", emptyList<Employee>())
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/empty_collection.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Result (When Data Exists)

|   | A    | B    | C     |
|---|------|------|-------|
| 1 | Employee Status |      |       |
| 2 |      |      |       |
| 3 | Name | Position | Salary |
| 4 | Yongho Hwang | Director | 8,000 |
| 5 | Yongho Han   | Manager  | 6,500 |

- The message row (row 7) is removed from the result

### Result (When Data Is Empty)

|   | A                   | B    | C    |
|---|---------------------|------|------|
| 1 | Employee Status     |      |      |
| 2 |                     |      |      |
| 3 | Name                | Position | Salary |
| 4 | No employees found. |      |      |

- The `empty` range content is displayed in the repeat region
- If the `empty` range is a single cell, the entire repeat region is merged and the message is displayed

### Explicit Parameter Format

```
${repeat(collection=employees, range=A4:C4, var=emp, direction=DOWN, empty=A7:C7)}
```

### Formula Format

```
=TBEG_REPEAT(collection=employees, range=A4:C4, var=emp, direction=DOWN, empty=A7:C7)
```

> [!NOTE]
> The `empty` range must be at a different location from the repeat region. It can reference another area in the same sheet or a different sheet.

---

## 10. Internationalization (I18N)

By leveraging TBEG's variable substitution, you can generate multilingual reports without any dedicated I18N feature.

### Template (i18n_template.xlsx)

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${label.title}                   |                 |               |
| 2 | ${repeat(employees, A4:C4, emp)} |                 |               |
| 3 | ${label.name}                    | ${label.position} | ${label.salary} |
| 4 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

A single template supports all languages. Use `${label.*}` variables instead of hard-coded text.

### Preparing Resource Bundles

**messages_ko.properties**
```properties
report.title=직원 현황 보고서
label.name=이름
label.position=직급
label.salary=연봉
```

**messages_en.properties**
```properties
report.title=Employee Report
label.name=Name
label.position=Position
label.salary=Salary
```

### Kotlin Code (ResourceBundle)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.util.Locale
import java.util.ResourceBundle

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val locale = Locale.KOREAN  // or Locale.ENGLISH
    val bundle = ResourceBundle.getBundle("messages", locale)

    val data = mapOf(
        "label" to mapOf(
            "title" to bundle.getString("report.title"),
            "name" to bundle.getString("label.name"),
            "position" to bundle.getString("label.position"),
            "salary" to bundle.getString("label.salary")
        ),
        "employees" to listOf(
            Employee("Yongho Hwang", "Director", 8000),
            Employee("Yongho Han", "Manager", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/i18n_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("report_${locale.language}.xlsx").writeBytes(bytes)
    }
}
```

### Kotlin Code (Spring MessageSource)

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.context.MessageSource
import java.util.Locale

fun buildI18nProvider(messageSource: MessageSource, locale: Locale) = simpleDataProvider {
    // Load label variables from MessageSource in bulk
    val keys = listOf("report.title", "label.name", "label.position", "label.salary")
    value("label", keys.associate { key ->
        key.substringAfter(".") to messageSource.getMessage(key, null, locale)
    })

    items("employees") {
        // DB query, etc.
        emptyList<Any>().iterator()
    }
}
```

### Result (Korean)

|   | A         | B    | C     |
|---|-----------|------|-------|
| 1 | 직원 현황 보고서 |      |       |
| 2 |           |      |       |
| 3 | 이름        | 직급   | 연봉    |
| 4 | Yongho Hwang | Director | 8,000 |
| 5 | Yongho Han   | Manager  | 6,500 |

### Result (English)

|   | A               | B        | C      |
|---|-----------------|----------|--------|
| 1 | Employee Report |          |        |
| 2 |                 |          |        |
| 3 | Name            | Position | Salary |
| 4 | Yongho Hwang    | Director | 8,000  |
| 5 | Yongho Han      | Manager  | 6,500  |

> [!TIP]
> TBEG does not provide dedicated I18N syntax. Instead, use Java/Spring's `ResourceBundle` or `MessageSource` to resolve translations and pass the results as variables. A single template can serve all languages.

---

## 11. Comprehensive Example -- Quarterly Sales Performance Report

This example demonstrates variable substitution, image insertion, repeat data expansion, automatic formula adjustment, conditional formatting replication, chart data range reflection, automatic cell merge, and bundle -- all within a single report.

### Template

> [!TIP]
> [Download template (rich_sample_template.xlsx)](../../src/test/resources/templates/rich_sample_template.xlsx)

![Template](../../src/main/resources/sample/screenshot_template.png)

Template structure:
- **Variable markers**: `${reportTitle}`, `${period}`, `${author}`, `${reportDate}`, `${subtitle_emp}`
- **Image markers**: `${image(logo,,-1:0)}`, `${image(ci)}`
- **Repeat markers**: `${repeat(depts, B8:G8, d)}` (department performance), `${repeat(products, I8:K8, p)}` (product categories), `${repeat(employees, B31:K31, emp)}` (employee performance)
- **Auto-merge markers**: `${merge(emp.dept)}` (department name merge), `${merge(emp.team)}` (team name merge)
- **Bundle markers**: `${bundle(B30:K33)}` (protects employee performance area as an independent unit)
- **Formulas**: SUM, AVERAGE (total/average rows), inter-cell calculations (Profit = Revenue - Cost, Achievement = Revenue / Target)
- **Conditional formatting**: Achievement >= 100% -> green, < 100% -> red / Share >= 30% -> green, < 30% -> red
- **Charts**: Department-level Revenue/Cost/Profit bar chart, product category pie chart

### Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.nio.file.Path
import java.time.LocalDate

data class DeptResult(val deptName: String, val revenue: Long, val cost: Long, val target: Long)
data class ProductCategory(val category: String, val revenue: Long)
data class Employee(
    val dept: String, val team: String, val name: String, val rank: String,
    val revenue: Long, val cost: Long, val target: Long
)

fun main() {
    val data = simpleDataProvider {
        value("reportTitle", "Q1 2026 Sales Performance Report")
        value("period", "Jan 2026 ~ Mar 2026")
        value("author", "Yongho Hwang")
        value("reportDate", LocalDate.now().toString())
        value("subtitle_emp", "Employee Performance Details")
        image("logo", File("logo.png").readBytes())
        image("ci", File("ci.png").readBytes())

        items("depts") {
            listOf(
                DeptResult("Common Platform", 52000, 31000, 50000),
                DeptResult("IT Strategy",     38000, 22000, 40000),
                DeptResult("HR Management",   28000, 19000, 30000),
                DeptResult("Education Biz",   95000, 61000, 90000),
                DeptResult("Content Dev",     42000, 28000, 45000),
            ).iterator()
        }

        items("products") {
            listOf(
                ProductCategory("Online Courses", 128000),
                ProductCategory("Consulting", 67000),
                ProductCategory("Certification", 45000),
                ProductCategory("Contents License", 15000),
            ).iterator()
        }

        items("employees") {
            listOf(
                Employee("Common Platform", "Strategy", "Hwang Yongho", "Manager", 18000, 11000, 17000),
                Employee("Common Platform", "Strategy", "Park Sungjun",  "Senior",  15000,  9000, 14000),
                Employee("Common Platform", "Backend",  "Choi Changmin", "Senior",  12000,  7000, 13000),
                Employee("Common Platform", "Backend",  "Kim Hyunkyung",  "Junior",   7000,  4000,  6000),
                Employee("IT Strategy",     "Planning", "Byun Jaemyung","Manager", 20000, 12000, 20000),
                Employee("IT Strategy",     "Planning", "Kim Minchul", "Senior",  11000,  6000, 12000),
                Employee("IT Strategy",     "Analysis", "Kim Minhee",   "Senior",   7000,  4000,  8000),
                Employee("Education Biz",   "Sales",    "Yoon Seojin",  "Manager", 35000, 22000, 30000),
                Employee("Education Biz",   "Sales",    "Kang Minwoo",  "Senior",  28000, 18000, 25000),
                Employee("Education Biz",   "Sales",    "Lim Soyeon",   "Junior",  15000, 10000, 15000),
                Employee("Education Biz",   "Support",  "Oh Junhyeok",  "Senior",  17000, 11000, 20000),
            ).iterator()
        }
    }

    ExcelGenerator().use { generator ->
        val template = File("rich_sample_template.xlsx").inputStream()
        generator.generateToFile(template, data, Path.of("output"), "quarterly_report")
    }
}
```

### Result

![Result](../../src/main/resources/sample/screenshot_result.png)

What TBEG handled automatically:
- **Variable substitution** -- title, period, author, date, employee performance subtitle
- **Image insertion** -- logo, CI
- **Repeat data expansion** -- departments expanded to 5 rows, products to 4 rows, employees to 11 rows
- **Automatic cell merge** -- consecutive cells with the same department/team name are automatically merged
- **Bundle** -- employee performance area is protected from department performance expansion
- **Automatic formula range adjustment** -- `SUM(C8:C8)` -> `SUM(C8:C12)`, `AVERAGE(C8:C8)` -> `AVERAGE(C8:C12)`
- **Conditional formatting replication** -- achievement/share colors applied to all rows
- **Chart data range reflection** -- charts reference the expanded data range

---

## 12. Automatic Cell Merge in Practice

An example of automatically merging the same department names in a departmental sales report.

### Template (dept_merge_template.xlsx)

|   | A                                      | B               | C             | D              |
|---|----------------------------------------|-----------------|---------------|----------------|
| 1 | ${repeat(sales, A3:D3, s)}             |                 |               |                |
| 2 | Department                             | Person          | Amount        | Note           |
| 3 | ${merge(s.dept)}                       | ${s.name}       | ${s.amount}   | ${s.note}      |

- **A3**: `${merge(s.dept)}` automatically merges consecutive cells with the same department name
- Other columns are regular fields that output individual values in each row without merging

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class SalesRecord(val dept: String, val name: String, val amount: Int, val note: String)

fun main() {
    // Sort by merge criteria (dept)
    val data = mapOf(
        "sales" to listOf(
            SalesRecord("Common Platform Team", "Yongho Hwang", 12000, ""),
            SalesRecord("Common Platform Team", "Yongho Han", 9500, ""),
            SalesRecord("Common Platform Team", "Yongho Hong", 8000, "New"),
            SalesRecord("IT Strategy Team", "Cheolsu Kim", 15000, ""),
            SalesRecord("IT Strategy Team", "Younghee Lee", 11000, ""),
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/dept_merge_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("dept_merge_output.xlsx").writeBytes(bytes)
    }
}
```

### Result

<table>
<tr><th></th><th>A</th><th>B</th><th>C</th><th>D</th></tr>
<tr><td>1</td><td></td><td></td><td></td><td></td></tr>
<tr><td>2</td><td>Department</td><td>Person</td><td>Amount</td><td>Note</td></tr>
<tr><td>3</td><td rowspan="3">Common Platform Team</td><td>Yongho Hwang</td><td>12,000</td><td></td></tr>
<tr><td>4</td><td>Yongho Han</td><td>9,500</td><td></td></tr>
<tr><td>5</td><td>Yongho Hong</td><td>8,000</td><td>New</td></tr>
<tr><td>6</td><td rowspan="2">IT Strategy Team</td><td>Cheolsu Kim</td><td>15,000</td><td></td></tr>
<tr><td>7</td><td>Younghee Lee</td><td>11,000</td><td></td></tr>
</table>

- A3:A5 are merged as "Common Platform Team", A6:A7 are merged as "IT Strategy Team"
- When `merge` is applied to multiple columns, each column is merged independently

> [!IMPORTANT]
> The `merge` marker only merges consecutive cells with the same value. Make sure to pre-sort the data by the merge criteria field.

---

## 13. Bundle

An example of wrapping two independent repeat regions in bundles so that one's expansion does not affect the other.

### Template (bundle_template.xlsx)

|   | A                                      | B             | C | D                                       | E              |
|---|----------------------------------------|---------------|---|-----------------------------------------|----------------|
| 1 | ${bundle(A1:B10)}                      |               |   | ${bundle(D1:E10)}                       |                |
| 2 | ${repeat(employees, A4:B4, emp)}       |               |   | ${repeat(departments, D4:E4, dept)}     |                |
| 3 | Name                                   | Salary        |   | Department                              | Budget         |
| 4 | ${emp.name}                            | ${emp.salary} |   | ${dept.name}                            | ${dept.budget} |

- **A1**: `${bundle(A1:B10)}` wraps the left region
- **D1**: `${bundle(D1:E10)}` wraps the right region
- Each repeat within a bundle expands independently and does not affect other bundles

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val salary: Int)
data class Department(val name: String, val budget: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("Yongho Hwang", 8000),
            Employee("Yongho Han", 6500),
            Employee("Yongho Hong", 4500),
            Employee("Cheolsu Kim", 5500),
            Employee("Younghee Lee", 7000),
        ),
        "departments" to listOf(
            Department("Common Platform Team", 50000),
            Department("IT Strategy Team", 30000),
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/bundle_template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val bytes = generator.generate(template, data)
        File("bundle_output.xlsx").writeBytes(bytes)
    }
}
```

### Result

|   | A    | B     | C | D        | E      |
|---|------|-------|---|----------|--------|
| 1 |      |       |   |          |        |
| 2 |      |       |   |          |        |
| 3 | Name | Salary|   | Department | Budget |
| 4 | Yongho Hwang  | 8,000 |   | Common Platform Team | 50,000 |
| 5 | Yongho Han    | 6,500 |   | IT Strategy Team     | 30,000 |
| 6 | Yongho Hong   | 4,500 |   |          |        |
| 7 | Cheolsu Kim   | 5,500 |   |          |        |
| 8 | Younghee Lee  | 7,000 |   |          |        |

- Employees (5) and departments (2) expand independently
- Without bundles, the employee region's expansion would push the department region down; with bundles, each region stays in place

> [!NOTE]
> The bundle range must fully encompass all repeat regions contained within it. An error occurs if a repeat region crosses a bundle boundary.

---

## Next Steps

- [Basic Examples](./basic-examples.md) - Basic usage
- [Spring Boot Examples](./spring-boot-examples.md) - Spring Boot integration
- [Configuration Reference](../reference/configuration.md) - Detailed settings
- [API Reference](../reference/api-reference.md) - API details
- [Best Practices](../best-practices.md) - Template design and performance optimization
