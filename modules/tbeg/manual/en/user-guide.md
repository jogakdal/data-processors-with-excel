> **[한국어](../ko/user-guide.md)** | English

# TBEG User Guide

## Table of Contents
1. [Quick Start](#1-quick-start)
2. [Core Concepts](#2-core-concepts)
3. [Using DataProvider](#3-using-dataprovider)
4. [Asynchronous Processing](#4-asynchronous-processing)
5. [Large-Scale Data Processing](#5-large-scale-data-processing)

---

## 1. Quick Start

### 1.1 Adding Dependencies

#### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.3")
}
```

#### Gradle (Groovy DSL)

```groovy
// build.gradle
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.3'
}
```

#### Maven

```xml
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.3</version>
</dependency>
```

### 1.2 Creating Your First Excel File

#### Template (template.xlsx)

|   | A      | B         |
|---|--------|-----------|
| 1 | Title    | ${title}  |
| 2 | Date     | ${date}   |

#### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File
import java.time.LocalDate

fun main() {
    val data = mapOf(
        "title" to "Monthly Report",
        "date" to LocalDate.now().toString()
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

#### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Monthly Report");
        data.put("date", LocalDate.now().toString());

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {
            byte[] bytes = generator.generate(template, data);
            try (FileOutputStream output = new FileOutputStream("output.xlsx")) {
                output.write(bytes);
            }
        }
    }
}
```

---

## 2. Core Concepts

TBEG provides dynamic data binding (variable substitution, data repetition, image insertion) — things Excel cannot do on its own. Formulas, conditional formatting, charts, and other Excel features are used as-is, and TBEG automatically adjusts them to work correctly even after data expansion.

### 2.1 Template Syntax

TBEG uses special markers in Excel templates to bind data.

| Syntax                     | Description             | Example                              |
|----------------------------|-------------------------|--------------------------------------|
| `${variable}`              | Simple variable substitution | `${title}`                         |
| `${item.field}`            | Object field substitution    | `${emp.name}`                      |
| `${repeat(collection, range, variable)}` | Repeat processing | `${repeat(employees, A3:C3, emp)}` |
| `${image(name)}`           | Image insertion              | `${image(logo)}`                   |
| `${size(collection)}`      | Collection size              | `${size(employees)}`               |

For detailed syntax, see the [Template Syntax Reference](./reference/template-syntax.md).

### 2.2 Repeating Data

List data is repeatedly rendered within the designated range of the template. By default, it expands downward (DOWN), and rightward (RIGHT) expansion is also supported. For detailed syntax, see the [Template Syntax Reference](./reference/template-syntax.md#23-rightward-repeat-right); for code examples, see [Advanced Examples](./examples/advanced-examples.md#8-rightward-repeat).

#### Template (employees.xlsx)

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | Name                                 | Position          | Salary          |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

> [!NOTE]
> The `${repeat(...)}` marker can be placed anywhere in the workbook outside the repeat range (even on a different sheet). The area specified by the range parameter is what gets repeated.

#### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("Yongho Hwang", "Director", 8000),
            Employee("Yongho Han", "Manager", 6500),
            Employee("Yongho Hong", "Assistant Manager", 4500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("employees.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

#### Result


|   | A    | B  | C     |
|---|------|----|-------|
| 1 |      |    |       |
| 2 | Name          | Position          | Salary  |
| 3 | Yongho Hwang  | Director          | 8,000   |
| 4 | Yongho Han    | Manager           | 6,500   |
| 5 | Yongho Hong   | Assistant Manager | 4,500   |

> [!TIP]
> When a repeat range expands, formulas, charts, pivot tables, and other affected elements have their coordinates and ranges automatically adjusted. For details, see the [Template Syntax Reference](./reference/template-syntax.md#28-automatic-adjustment-of-related-elements).

> [!TIP]
> When a collection is empty, you can display alternative content. The content from the range specified by the `empty` parameter is rendered. For details, see the [Template Syntax Reference](./reference/template-syntax.md#27-empty-collection-handling-empty).

### 2.3 Image Insertion

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

fun main() {
    val logoBytes = File("logo.png").readBytes()

    val provider = simpleDataProvider {
        value("company", "Hunet Inc.")
        image("logo", logoBytes)
    }

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### 2.4 Saving Files

`generate()` returns a byte array, while `generateToFile()` saves directly to a file.

```kotlin
ExcelGenerator().use { generator ->
    // Get as byte array
    val bytes = generator.generate(template, data)

    // Save directly to file
    val path = generator.generateToFile(template, data, outputDir, "report")
}
```

When using `generateToFile()`, the filename is generated according to the following rules:

| Setting | Default | Example Result |
|---------|---------|----------------|
| Filename mode | `TIMESTAMP` | `report_20260115_143052.xlsx` |
| On conflict | `SEQUENCE` | `report_20260115_143052_1.xlsx` |

For detailed settings such as filename mode, timestamp format, and conflict policy, see the [Configuration Options Reference](./reference/configuration.md#filenamemode).

---

## 3. Using DataProvider

### 3.1 Map vs DataProvider

| Approach | Advantages | Best For |
|----------|------------|----------|
| Map | Simple, less code | Small datasets, simple reports |
| DataProvider | Lazy loading, memory efficient | Large datasets, DB integration |

### 3.2 simpleDataProvider DSL (Kotlin)

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider

val provider = simpleDataProvider {
    // Simple variables
    value("title", "Report Title")
    value("date", LocalDate.now().toString())

    // Collection (eager loading)
    items("departments", listOf(dept1, dept2, dept3))

    // Collection (lazy loading) - called when data is needed
    items("employees") {
        employeeRepository.findAll().iterator()
    }

    // Image
    image("logo", logoBytes)

    // Document metadata
    metadata {
        title = "Monthly Report"
        author = "Yongho Hwang"
        company = "Hunet Inc."
    }
}
```

### 3.3 SimpleDataProvider.Builder (Java)

```java
import io.github.jogakdal.tbeg.SimpleDataProvider;

SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "Report Title")
    .value("date", LocalDate.now().toString())
    .items("departments", List.of(dept1, dept2, dept3))
    .itemsFromSupplier("employees", () -> employeeRepository.findAll().iterator())
    .image("logo", logoBytes)
    .metadata(meta -> meta
        .title("Monthly Report")
        .author("Yongho Hwang")
        .company("Hunet Inc."))
    .build();
```

### 3.4 Implementing a Custom DataProvider

If you need a specialized data source, you can implement the `ExcelDataProvider` interface directly.

```kotlin
import io.github.jogakdal.tbeg.ExcelDataProvider

class MyDataProvider(
    private val repository: EmployeeRepository
) : ExcelDataProvider {

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "Employee Status"
        "date" -> LocalDate.now().toString()
        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> repository.streamAll().iterator()
        else -> null
    }

    override fun getImage(name: String): ByteArray? = null

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> repository.count().toInt()
        else -> null
    }
}
```

---

## 4. Asynchronous Processing

### 4.1 Kotlin Coroutines

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        // Async generation
        val path = generator.generateToFileAsync(
            template = template,
            data = mapOf("title" to "Async Report"),
            outputDir = Path.of("./output"),
            baseFileName = "async_report"
        )

        println("File created: $path")
    }
}
```

### 4.2 Java CompletableFuture

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class AsyncExample {
    public static void main(String[] args) throws Exception {
        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            CompletableFuture<Path> future = generator.generateToFileFuture(
                template,
                Map.of("title", "Async Report"),
                Path.of("./output"),
                "async_report"
            );

            future.thenAccept(path -> System.out.println("File created: " + path));

            // Wait for completion
            Path result = future.get();
        }
    }
}
```

### 4.3 Background Jobs + Listener

Ideal for API servers that need to respond immediately and process in the background.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import java.nio.file.Path

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
        }

        override fun onFailed(jobId: String, error: Exception) {
            println("[Failed] ${error.message}")
        }
    }
)

// In an API server, respond immediately here
return ResponseEntity.accepted().body(mapOf("jobId" to job.jobId))
```

---

## 5. Large-Scale Data Processing

### 5.1 Streaming Mode

TBEG uses streaming mode (SXSSF) by default to process large datasets in a memory-efficient manner.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode

// Streaming mode (default)
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED
)

// Non-streaming mode (small datasets, complex formulas)
val configNonStreaming = TbegConfig(
    streamingMode = StreamingMode.DISABLED
)
```

### 5.2 Lazy Loading + Providing Count (Recommended)

When processing large datasets, providing the count (total number of items) along with lazy loading yields optimal performance.

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider

val employeeCount = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    value("title", "All Employee Status")

    // Provide count with lazy loading
    items("employees", employeeCount) {
        employeeRepository.streamAll().iterator()
    }
}
```

### 5.3 Providing Count in a Custom DataProvider

```kotlin
class OptimizedDataProvider(
    private val repository: EmployeeRepository
) : ExcelDataProvider {

    override fun getValue(name: String): Any? = /* ... */

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> repository.streamAll().iterator()
        else -> null
    }

    // Provide count for performance optimization
    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> repository.count().toInt()
        else -> null
    }
}
```

### 5.4 JPA Stream Integration

Large-scale processing using Spring Data JPA Streams:

```kotlin
interface EmployeeRepository : JpaRepository<Employee, Long> {
    @Query("SELECT e FROM Employee e")
    fun streamAll(): Stream<Employee>
}

@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val employeeRepository: EmployeeRepository
) {
    @Transactional(readOnly = true)  // Required to keep the Stream open
    fun generateLargeReport(): Path {
        val count = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "All Employee Status")
            items("employees", count) {
                employeeRepository.streamAll().iterator()
            }
        }

        return excelGenerator.generateToFile(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("/var/reports"),
            baseFileName = "all_employees"
        )
    }
}
```

> [!WARNING]
> The `@Transactional` annotation is required when using JPA Streams. Since a Stream is closed when the transaction ends, the transaction must remain active until Excel generation is complete.

### 5.5 Recommended Configuration for Large-Scale Processing

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,    // Enable streaming mode
    progressReportInterval = 1000              // Report progress every 1000 rows
)

val generator = ExcelGenerator(config)
```

---

## Next Steps

- [Template Syntax Reference](./reference/template-syntax.md) - Detailed template syntax
- [API Reference](./reference/api-reference.md) - Class and method details
- [Basic Examples](./examples/basic-examples.md) - Various usage examples
- [Troubleshooting](./troubleshooting.md) - Common issues and solutions
