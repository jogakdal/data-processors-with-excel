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
    value("title", "직원 현황 보고서")
    value("date", LocalDate.now())
    value("author", "황용호")

    // Collection (List)
    items("employees", listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500)
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
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500),
        Employee("홍용호", "대리", 4500)
    ).iterator()
}

// 1. Query the count first (lightweight query)
val employeeCount = countEmployees()

// 2. Create DataProvider (collection data is NOT loaded at this point)
val provider = simpleDataProvider {
    // Single values
    value("title", "직원 현황 보고서")
    value("date", LocalDate.now())
    value("author", "황용호")

    // Collection: provide count along with lazy loading
    items("employees", employeeCount) {
        // This block is executed at Excel generation time
        streamEmployees()
    }
}

// 3. Generate Excel (the lambda is invoked here, loading the data)
ExcelGenerator().use { generator ->
    // Load template from resources/templates/ directory
    val template = javaClass.getResourceAsStream("/templates/template.xlsx")
        ?: throw IllegalStateException("Template not found")
    // To read directly from file: val template = File("template.xlsx").inputStream()

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

> **Note**: Everything works correctly even without providing a count. However, TBEG will need to traverse the collection first to determine the total row count, which may cause a performance penalty due to double iteration.

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
    value("company", "(주)휴넷")

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
    value("title", "보고서")

    metadata {
        title = "2026년 월간 보고서"
        author = "황용호"
        subject = "월간 실적"
        keywords("월간", "보고서", "실적")
        company = "(주)휴넷"
    }
}
```

#### Java Builder Pattern

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "직원 현황 보고서")
    .value("date", LocalDate.now())
    .items("employees", employeeList)
    .items("employees", employeeCount, () -> fetchEmployees())  // count + lambda
    .image("logo", logoBytes)  // eager loading
    .imageFromSupplier("signature", () -> downloadSignature())  // lazy loading
    .metadata(meta -> meta
        .title("보고서")
        .author("황용호"))
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
        "title" -> "부서별 직원 현황"

        // Caching: performs DB query only once when the same value is referenced from multiple cells
        "departmentName" -> cachedDepartmentName
            ?: employeeRepository.getDepartmentName(departmentId)
                .also { cachedDepartmentName = it }

        "reportDate" -> reportDate.toString()

        // Dynamic value: current time at invocation
        "generatedAt" -> LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        // [Advantage 3] Data dependencies - leveraging results from other methods
        "summary" -> "${cachedDepartmentName ?: "부서"} 소속 총 ${getOrLoadCount()}명"

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
        title = "${cachedDepartmentName ?: "부서"} 직원 현황 보고서",
        author = "HR 시스템",
        subject = "직원 현황",
        company = "(주)휴넷"
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

> **Note**: Streams must be used within a `@Transactional` scope. Once the transaction ends, the DB connection is closed and the Stream becomes invalid.

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
            case "title" -> "부서별 직원 현황";
            case "departmentName" -> getOrLoadDepartmentName();
            case "reportDate" -> reportDate.toString();
            case "generatedAt" -> LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            // [Advantage 3] Data dependencies
            case "summary" -> getOrLoadDepartmentName() + " 소속 총 " + getOrLoadCount() + "명";
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
            value("title", "직원 현황")
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
    value("title", "대용량 보고서")

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
        "title" -> "직원 현황"
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

> **Warning**: Cursors must be used within a `@Transactional` scope. Once the transaction ends, the Cursor becomes invalid.

---

### 1.5 External API Integration

In a microservice architecture, this pattern fetches data from another service's API in **paginated** chunks and converts it to Excel.

#### PageableList-Based Iterator

```kotlin
class PageableListIterator<T>(
    private val pageSize: Int = 100,
    private val fetcher: (page: Int, size: Int) -> PageableList<T>
) : Iterator<T> {

    private var currentPage = 1
    private var currentIterator: Iterator<T> = emptyList<T>().iterator()
    private var hasMorePages = true

    override fun hasNext(): Boolean {
        if (currentIterator.hasNext()) return true
        if (!hasMorePages) return false

        // Load next page (API call)
        val result = fetcher(currentPage++, pageSize)
        currentIterator = result.items.list.iterator()
        hasMorePages = result.page.current < result.page.total

        return currentIterator.hasNext()
    }

    override fun next(): T = currentIterator.next()
}
```

#### Usage Example

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

data class EmployeeDto(val name: String, val salary: Int)

// Fetch data by calling another microservice's API
fun fetchEmployeesFromApi(page: Int, size: Int): PageableList<EmployeeDto> {
    // With Feign Client:
    // return employeeApiClient.getEmployees(page, size).payload
    //     ?: throw Exception("API call failed")

    // With RestTemplate:
    // return restTemplate.exchange(
    //     "/api/employees?page=$page&size=$size",
    //     HttpMethod.GET,
    //     null,
    //     object : ParameterizedTypeReference<StandardResponse<PageableList<EmployeeDto>>>() {}
    // ).body?.payload ?: throw Exception("API call failed")

    // With WebClient:
    // return webClient.get()
    //     .uri("/api/employees?page=$page&size=$size")
    //     .retrieve()
    //     .bodyToMono<StandardResponse<PageableList<EmployeeDto>>>()
    //     .block()?.payload ?: throw Exception("API call failed")

    // Dummy response for example
    return PageableList.build(
        items = listOf(EmployeeDto("황용호", 8000), EmployeeDto("한용호", 6500)),
        totalItems = 100,
        pageSize = size.toLong(),
        currentPage = page.toLong()
    )
}

fun main() {
    // First, query totalItems (via first page call or separate count API)
    val firstPage = fetchEmployeesFromApi(1, 1)
    val totalCount = firstPage.items.total.toInt()

    val provider = simpleDataProvider {
        value("title", "API 데이터 보고서")

        items("employees", totalCount) {
            PageableListIterator(pageSize = 50) { page, size ->
                fetchEmployeesFromApi(page, size)
            }
        }
    }

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("template.xlsx").inputStream()

        val result = generator.generate(template, provider)
        File("api_report.xlsx").writeBytes(result)
    }
}
```

> **Note**: `PageableList` and `StandardResponse` are types from a standard API response library. You can adopt this pattern when using a standard API response format across microservices.

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
        value("title", "비동기 보고서")
        items("data") { generateData().iterator() }
    }

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("template.xlsx").inputStream()

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
            .value("title", "비동기 보고서")
            .items("data", generateData())
            .build();

        // Load template from resources/templates/ directory
        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = AsyncWithFuture.class.getResourceAsStream("/templates/template.xlsx")) {
            // To read directly from file: new FileInputStream("template.xlsx")

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
        value("title", "백그라운드 보고서")
        items("data") { (1..5000).map { mapOf("id" to it) }.iterator() }
    }

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("template.xlsx").inputStream()

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
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/formula_template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("formula_template.xlsx").inputStream()

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
        "text" to "휴넷 홈페이지 바로가기",
        "url" to "https://www.hunet.co.kr"
    )

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/link_template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("link_template.xlsx").inputStream()

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
| 1 | ${repeat(employees, A2:C2, emp)}   |                 |               |
| 2 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val employees = listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500),
        Employee("홍용호", "대리", 4500)
    )

    val data = mapOf(
        "title" to "직원 현황",
        "employees" to employees
    )

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_sheet_template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("multi_sheet_template.xlsx").inputStream()

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
        value("title", "대용량 보고서")

        // Provide count along with lazy loading (optimal performance)
        items("data", dataCount) {
            // Simulate 1 million records
            (1..dataCount).asSequence().map {
                mapOf("id" to it, "value" to it * 10)
            }.iterator()
        }
    }

    ExcelGenerator(config).use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("template.xlsx").inputStream()

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
            Employee("황용호", 8000),
            Employee("한용호", 6500),
            Employee("홍용호", 4500)
        ),
        "departments" to listOf(
            Department("공통플랫폼팀", 50000),
            Department("IT전략기획팀", 30000)
        )
    )

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("multi_repeat.xlsx").inputStream()

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
                Employee("황용호", 8000),
                Employee("한용호", 6500),
                Employee("홍용호", 4500)
            ).iterator()
        }

        // Second collection: departments
        items("departments", departmentCount) {
            // departmentRepository.findAll().iterator()
            listOf(
                Department("공통플랫폼팀", 50000),
                Department("IT전략기획팀", 30000)
            ).iterator()
        }
    }

    ExcelGenerator().use { generator ->
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("multi_repeat.xlsx").inputStream()

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
| 3 | 황용호  | 8,000 |   | 공통플랫폼팀   | 50,000 |
| 4 | 한용호  | 6,500 |   | IT전략기획팀  | 30,000 |
| 5 | 홍용호  | 4,500 |   |          |        |

> **Note**: Each repeat region expands independently. In the example above, there are 3 employees and 2 departments, so each expands by a different number of rows.

> **Warning**: Repeat regions must not overlap in 2D space.

---

## 8. Rightward Repeat

### Template (right_repeat.xlsx)

|   | A                                       | B             |
|---|-----------------------------------------|---------------|
| 1 | ${repeat(months, B1:B2, m, RIGHT)}      | ${m.month}월   |
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
        // Load template from resources/templates/ directory
        val template = object {}.javaClass.getResourceAsStream("/templates/right_repeat.xlsx")
            ?: throw IllegalStateException("Template not found")
        // To read directly from file: val template = File("right_repeat.xlsx").inputStream()

        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Result

|   | A  | B      | C      | D      | E      |
|---|----|--------|--------|--------|--------|
| 1 |    | 1월     | 2월     | 3월     | 4월     |
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
| 4 | 황용호  | 부장   | 8,000 |
| 5 | 한용호  | 과장   | 6,500 |

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

> **Note**: The `empty` range must be at a different location from the repeat region. It can reference another area in the same sheet or a different sheet.

---

## Next Steps

- [Spring Boot Examples](./spring-boot-examples.md) - Spring Boot integration
- [Configuration Reference](../reference/configuration.md) - Detailed settings
- [API Reference](../reference/api-reference.md) - API details
