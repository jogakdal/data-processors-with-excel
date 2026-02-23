> **[한국어](../ko/index.md)** | English

# TBEG (Template-Based Excel Generator)

A template-based Excel file generation library

## Overview

TBEG is a library that generates Excel files such as reports and statements by binding data to Excel templates.

### Key Features

- **Template-Based Generation**: Bind data to Excel templates (.xlsx)
- **Repeating Data**: Expand list data into rows or columns
- **Image Insertion**: Insert images at designated positions in the template
- **Large-Scale Processing**: Memory-efficient handling of large datasets via streaming mode
- **Asynchronous Processing**: Support for Coroutines, CompletableFuture, and background jobs

---

## Why TBEG

### Is this how you create Excel reports?

Building Excel files directly with Apache POI requires dozens of lines of code:

```kotlin
// Using Apache POI directly
val workbook = XSSFWorkbook()
val sheet = workbook.createSheet("직원 현황")
val headerRow = sheet.createRow(0)
headerRow.createCell(0).setCellValue("이름")
headerRow.createCell(1).setCellValue("직급")
headerRow.createCell(2).setCellValue("연봉")

employees.forEachIndexed { index, emp ->
    val row = sheet.createRow(index + 1)
    row.createCell(0).setCellValue(emp.name)
    row.createCell(1).setCellValue(emp.position)
    row.createCell(2).setCellValue(emp.salary.toDouble())
}

// Column widths, styles, formulas, charts... it never ends
```

With TBEG, you can **use the Excel template as-is** -- designed by your designer -- and simply bind data to it:

```kotlin
// Using TBEG
val data = mapOf(
    "title" to "직원 현황",
    "employees" to employeeList
)

ExcelGenerator().use { generator ->
    val bytes = generator.generate(template, data)
    File("output.xlsx").writeBytes(bytes)
}
```

Formatting, charts, formulas, and conditional formatting are **all managed in the template**. Your code focuses solely on data binding.

### When to use TBEG

| Scenario | Suitability |
|----------|-------------|
| Generating standardized reports or statements | Suitable |
| Filling data into designer-provided Excel forms | Suitable |
| Reports requiring complex formatting (conditional formatting, charts, pivot tables) | Suitable |
| Processing tens of thousands to hundreds of thousands of rows | Suitable |
| Excel files with dynamically changing column structures | Not suitable |
| Reading/parsing Excel files | Not suitable (TBEG is for generation only) |

---

## Where to Start

### I'm using TBEG for the first time
1. Try generating your first Excel file with the [Quick Start](#quick-start) below
2. Learn the core concepts in the [User Guide](./user-guide.md)
3. Explore various usage patterns in the [Basic Examples](./examples/basic-examples.md)

### I want to integrate with Spring Boot
1. Check the integration guide in the [Spring Boot Examples](./examples/spring-boot-examples.md)
2. Review `application.yml` settings in [Configuration Options](./reference/configuration.md)
3. See [Advanced Examples - JPA Integration](./examples/advanced-examples.md#13-jpaspring-data-integration) for database connectivity

### I need to process large datasets
1. See [User Guide - Large-Scale Data Processing](./user-guide.md#5-large-scale-data-processing)
2. Explore lazy loading patterns in [Advanced Examples - DataProvider](./examples/advanced-examples.md#1-dataprovider-usage)
3. Follow the step-by-step guide in [Best Practices - Performance Optimization](./best-practices.md#2-performance-optimization)

### I'm working with complex templates
1. Review the full marker syntax in [Template Syntax](./reference/template-syntax.md)
2. See real-world patterns in [Advanced Examples](./examples/advanced-examples.md)
3. Check common issues in [Troubleshooting](./troubleshooting.md)

### I want to understand the internals
1. Study the architecture and pipeline in the [Developer Guide](./developer-guide.md)

---

## Quick Start

### Add Repository and Dependency

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.2")
}
```

> [!TIP]
> For detailed setup instructions, see the [User Guide](./user-guide.md#11-adding-dependencies).

### Basic Usage

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

fun main() {
    val data = mapOf(
        "title" to "월간 보고서",
        "items" to listOf(
            mapOf("name" to "항목1", "value" to 100),
            mapOf("name" to "항목2", "value" to 200)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

---

## Documentation

### User Guide
- [User Guide](./user-guide.md) - Complete guide to using TBEG

### Reference
- [Template Syntax](./reference/template-syntax.md) - Syntax available in templates
- [API Reference](./reference/api-reference.md) - Class and method details
- [Configuration Options](./reference/configuration.md) - TbegConfig options

### Examples
- [Basic Examples](./examples/basic-examples.md) - Simple usage examples
- [Advanced Examples](./examples/advanced-examples.md) - Large-scale processing, async processing, etc.
- [Spring Boot Examples](./examples/spring-boot-examples.md) - Spring Boot integration

### Operations Guide
- [Best Practices](./best-practices.md) - Template design, performance optimization, error prevention
- [Troubleshooting](./troubleshooting.md) - Common issues and solutions
- [Migration Guide](./migration-guide.md) - Version upgrade instructions

### Developer Guide
- [Developer Guide](./developer-guide.md) - Internal architecture and extension methods

---

## Template Syntax Preview

| Syntax | Description | Example |
|--------|-------------|---------|
| `${variable}` | Variable substitution | `${title}` |
| `${item.field}` | Repeat item field | `${emp.name}` |
| `${repeat(collection, range, variable)}` | Repeat processing | `${repeat(items, A2:C2, item)}` |
| `${image(name)}` | Image insertion | `${image(logo)}` |
| `${size(collection)}` | Collection size | `${size(items)}` |

---

## Compatibility

| Item | Value |
|------|-------|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| Package | `io.github.jogakdal.tbeg` |
| Java | 21 or later |
| Kotlin | 2.0 or later |
| Apache POI | 5.2.5 (transitive dependency) |
| Spring Boot | 3.x (optional) |
| Author | [Yongho Hwang (황용호)](https://github.com/jogakdal) (jogakdal@gmail.com) |
