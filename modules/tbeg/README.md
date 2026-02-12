> **English** | [한국어](./README.ko.md)

# TBEG (Template Based Excel Generator)

A library that generates reports by binding data to Excel templates.

## Key Features

- **Template-based generation**: Generate reports by binding data to Excel templates
- **Repeat data processing**: Expand list data into rows/columns with `${repeat(...)}` syntax
- **Variable substitution**: Bind values to cells, charts, shapes, headers/footers, formula arguments, etc. with `${variableName}` syntax
- **Image insertion**: Insert dynamic images into template cells
- **File encryption**: Set open password for generated Excel files
- **Document metadata**: Set document properties such as title, author, keywords, etc.
- **Asynchronous processing**: Process large data in the background
- **Lazy loading**: Memory-efficient data processing via DataProvider

## Add Dependency

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.1'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.1</version>
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
            Employee("한용호", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Spring Boot

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

In a Spring Boot environment, `ExcelGenerator` is automatically registered as a Bean.

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

## Streaming Mode

A mode that improves memory efficiency and processing speed. The default value (`enabled`) is recommended.

| Mode              | Description                              |
|-------------------|------------------------------------------|
| `enabled` (default) | Memory-efficient, fast processing speed |
| `disabled`        | Keeps all rows in memory (no advantage)  |

### Performance Benchmark

**Test environment**: Java 21, macOS, 3 columns repeat + SUM formula

| Data Size    | disabled | enabled | Speed Improvement |
|--------------|----------|---------|-------------------|
| 1,000 rows   | 179ms    | 146ms   | 1.2x              |
| 10,000 rows  | 1,887ms  | 519ms   | **3.6x**          |
| 30,000 rows  | -        | 1,104ms | -                 |
| 50,000 rows  | -        | 1,269ms | -                 |
| 100,000 rows | -        | 2,599ms | -                 |

### Comparison with Other Libraries (30,000 rows)

| Library    | Time      | Notes                                                       |
|------------|-----------|-------------------------------------------------------------|
| **TBEG**   | **1.1s**  | Streaming mode                                              |
| JXLS       | 5.2s      | [Benchmark source](https://github.com/jxlsteam/jxls/discussions/203) |

> [!TIP]
> Run benchmark: `./gradlew :tbeg:runBenchmark`

For details on configuration options, see the [Configuration Options Reference](./manual/en/reference/configuration.md).

## Documentation

**For detailed documentation, see the [TBEG Manual](./manual/en/index.md).**

- [User Guide](./manual/en/user-guide.md)
- [Template Syntax Reference](./manual/en/reference/template-syntax.md)
- [API Reference](./manual/en/reference/api-reference.md)
- [Configuration Options Reference](./manual/en/reference/configuration.md)
- [Basic Examples](./manual/en/examples/basic-examples.md)
- [Advanced Examples](./manual/en/examples/advanced-examples.md)
- [Spring Boot Examples](./manual/en/examples/spring-boot-examples.md)
- [Best Practices](./manual/en/best-practices.md)
- [Troubleshooting](./manual/en/troubleshooting.md)
- [Developer Guide](./manual/en/developer-guide.md)

## Run Samples

Samples use the `src/test/resources/templates/template.xlsx` template.

```bash
# Kotlin sample
./gradlew :tbeg:runSample
# Output: build/samples/

# Java sample
./gradlew :tbeg:runJavaSample
# Output: build/samples-java/

# Spring Boot sample
./gradlew :tbeg:runSpringBootSample
# Output: build/samples-spring/
```

## Author

[Yongho Hwang](https://github.com/jogakdal) (jogakdal@gmail.com)
