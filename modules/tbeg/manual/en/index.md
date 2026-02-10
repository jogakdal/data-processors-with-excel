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

## Quick Start

### Add Repository and Dependency

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.0")
}
```

> For detailed setup instructions, see the [User Guide](./user-guide.md#1-quick-start).

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

## Module Information

| Item | Value |
|------|-------|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| Package | `io.github.jogakdal.tbeg` |
| Minimum Java Version | 21 |
| Minimum Kotlin Version | 2.0 |
