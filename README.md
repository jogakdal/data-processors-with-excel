> **[한국어](./README.ko.md)** | English

# TBEG - Template Based Excel Generator

[![CI](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml/badge.svg)](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A JVM library that generates reports by binding data to Excel templates. Works seamlessly with both Kotlin and Java.

## Key Features

- **Template-based generation** — Generate reports by binding data to Excel templates
- **Repeat data processing** — Expand list data into rows/columns with `${repeat(...)}` syntax
- **Variable substitution** — Bind values to cells, charts, shapes, headers/footers, formula arguments, etc. with `${variableName}` syntax
- **Image insertion** — Insert dynamic images into template cells
- **Streaming mode** — High-speed processing of large data with SXSSF
- **File encryption** — Set open password for generated Excel files
- **Asynchronous processing** — Process large data in the background
- **Spring Boot support** — Zero-config usage via auto-configuration

## Add Dependency

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.2")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.2'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.2</version>
</dependency>
```

## Quick Start

### Kotlin

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "title" to "직원 현황",
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("홍용호", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Example {
    public static void main(String[] args) throws Exception {
        var data = Map.<String, Object>of(
            "title", "직원 현황",
            "employees", List.of(
                Map.of("name", "황용호", "position", "부장", "salary", 8000),
                Map.of("name", "홍용호", "position", "과장", "salary", 6500)
            )
        );

        try (var generator = new ExcelGenerator()) {
            byte[] bytes = generator.generate(new FileInputStream("template.xlsx"), data);
            Files.write(Path.of("output.xlsx"), bytes);
        }
    }
}
```

### DataProvider (Kotlin DSL / Java Builder)

<details>
<summary>Kotlin DSL</summary>

```kotlin
val provider = simpleDataProvider {
    value("title", "직원 현황")
    items("employees", listOf(
        mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
        mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500)
    ))
}
```

</details>

<details>
<summary>Java Builder</summary>

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "직원 현황")
    .items("employees", List.of(
        Map.of("name", "황용호", "position", "부장", "salary", 8000),
        Map.of("name", "홍용호", "position", "과장", "salary", 6500)
    ))
    .build();
```

</details>

## Template Syntax

### Variable Substitution

```
${title}
${employee.name}
```

### Repeat Data

```
${repeat(employees, A3:C3, emp, DOWN)}
```

### Image

```
${image(logo)}
${image(logo, B5)}
${image(logo, B5, 100:50)}
```

## Spring Boot

In a Spring Boot environment, `ExcelGenerator` is automatically registered as a Bean.

<details open>
<summary>Kotlin</summary>

```kotlin
@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader
) {
    fun generateReport(): ByteArray {
        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        val data = mapOf("title" to "보고서", "items" to listOf(...))
        return excelGenerator.generate(template.inputStream, data)
    }
}
```

</details>

<details>
<summary>Java</summary>

```java
@Service
public class ReportService {
    private final ExcelGenerator excelGenerator;
    private final ResourceLoader resourceLoader;

    public ReportService(ExcelGenerator excelGenerator, ResourceLoader resourceLoader) {
        this.excelGenerator = excelGenerator;
        this.resourceLoader = resourceLoader;
    }

    public byte[] generateReport() throws IOException {
        Resource template = resourceLoader.getResource("classpath:templates/report.xlsx");
        Map<String, Object> data = Map.of("title", "보고서", "items", List.of(...));
        return excelGenerator.generate(template.getInputStream(), data);
    }
}
```

</details>

### Configuration (application.yml)

```yaml
tbeg:
  streaming-mode: enabled       # enabled, disabled
  file-naming-mode: timestamp
  preserve-template-layout: true
```

## Performance

**Test environment**: Java 21, macOS, 3 columns repeat + SUM formula

| Data Size    | disabled | enabled | Speed Improvement |
|-------------|----------|---------|-------------------|
| 1,000 rows   | 172ms    | 147ms   | 1.2x              |
| 10,000 rows  | 1,801ms  | 663ms   | **2.7x**          |
| 30,000 rows  | -        | 1,057ms | -                 |
| 50,000 rows  | -        | 1,202ms | -                 |
| 100,000 rows | -        | 3,154ms | -                 |

### Comparison with Other Libraries (30,000 rows)

| Library    | Time      | Notes                                                       |
|------------|-----------|-------------------------------------------------------------|
| **TBEG**   | **1.1s**  | Streaming mode                                              |
| JXLS       | 5.2s      | [Benchmark source](https://github.com/jxlsteam/jxls/discussions/203) |

> Run benchmark: `./gradlew :tbeg:runBenchmark`

## Documentation

For detailed documentation, see the links below:

- [TBEG Module README](modules/tbeg/README.md)
- [Manual Index](modules/tbeg/manual/en/index.md)
- [User Guide](modules/tbeg/manual/en/user-guide.md)
- [Template Syntax Reference](modules/tbeg/manual/en/reference/template-syntax.md)
- [API Reference](modules/tbeg/manual/en/reference/api-reference.md)
- [Configuration Options Reference](modules/tbeg/manual/en/reference/configuration.md)
- [Basic Examples](modules/tbeg/manual/en/examples/basic-examples.md)
- [Advanced Examples](modules/tbeg/manual/en/examples/advanced-examples.md)
- [Spring Boot Examples](modules/tbeg/manual/en/examples/spring-boot-examples.md)
- [Best Practices](modules/tbeg/manual/en/best-practices.md)
- [Troubleshooting](modules/tbeg/manual/en/troubleshooting.md)
- [Migration Guide](modules/tbeg/manual/en/migration-guide.md)

## Requirements

- Java 21+
- Kotlin 2.1.20+ (when used in Kotlin projects)

## Author

[Yongho Hwang](https://github.com/jogakdal) (jogakdal@gmail.com)

## License

[Apache License 2.0](LICENSE)
