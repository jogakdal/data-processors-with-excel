> **[한국어](../ko/best-practices.md)** | English

# TBEG Best Practices

## Table of Contents
1. [Template Design](#1-template-design)
2. [Performance Optimization](#2-performance-optimization)
3. [Error Prevention](#3-error-prevention)

---

## 1. Template Design

### Place repeat markers outside the repeat range

The `${repeat(...)}` marker can be placed anywhere in the workbook as long as it is outside the repeat range. Placing the marker in a header row above the data area improves readability.

```
| A                                  | B               | C             |
| ${repeat(employees, A2:C2, emp)}   |                 |               |  ← Marker
| ${emp.name}                        | ${emp.position} | ${emp.salary} |  ← Repeat range
```

---

### Place formulas below the data area

When you place aggregate formulas such as `=SUM()` below the repeat area, the formula's reference range is automatically adjusted as the area expands.

```
| A                               | B             |
| ${repeat(items, A2:B2, item)}   |               |
| ${item.name}                    | ${item.value} |
| 합계                             | =SUM(B2:B2)   |  ← Automatically expands to =SUM(B2:BN)
```

---

### Keep one data record per row

Design repeat ranges intuitively. Use one row per data record as the default, and switch to multi-row repetition when a more complex layout is needed.

**Recommended**:
```
${repeat(employees, A2:C2, emp)}   ← One-row repetition
${emp.name} | ${emp.position} | ${emp.salary}
```

**For complex layouts**:
```
${repeat(employees, A2:B3, emp)}   ← Two-row repetition
이름: ${emp.name}  | 직급: ${emp.position}
급여: ${emp.salary} |
```

---

### Use named parameters

When a marker has three or more parameters, using named parameters makes the intent clearer.

```
// Positional (hard to read with many parameters)
${repeat(items, A2:C2, item, DOWN, A10:C10)}

// Named (intent is clear)
${repeat(collection=items, range=A2:C2, var=item, direction=DOWN, empty=A10:C10)}
```

---

### Avoid overlapping repeat areas

When placing multiple repeat areas on the same sheet, ensure that no areas overlap in 2D space (rows x columns).

**Correct** - Separate column groups:
```
| A (employees) | B (employees) | C | D (departments) | E (departments) |
```

**Correct** - Separate row groups:
```
| A (employees) | B (employees) |
| ...           | ...           |
| A (departments) | B (departments) |  ← Placed below employees
```

---

## 2. Performance Optimization

### Four-Step Optimization Guide

Apply the following steps based on your data size.

#### Step 1: Streaming mode (enabled by default)

```kotlin
val config = TbegConfig(streamingMode = StreamingMode.ENABLED) // default
```

Streaming mode provides 2-3x or better performance improvement for large datasets.

#### Step 2: Provide count

Supplying the total count alongside the collection in a DataProvider prevents double traversal of the data.

```kotlin
val count = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    items("employees", count) {
        employeeRepository.findAll().iterator()
    }
}
```

#### Step 3: Lazy loading

Instead of loading all data into memory upfront, use a lambda to load data on demand.

```kotlin
// Not recommended: loading all data upfront
val allEmployees = employeeRepository.findAll()
items("employees", allEmployees)

// Recommended: lazy loading
items("employees", count) {
    employeeRepository.findAll().iterator()
}
```

#### Step 4: DB streaming

For datasets exceeding 100,000 rows, use JPA Stream or MyBatis Cursor.

```kotlin
@Transactional(readOnly = true)
fun generateLargeReport(): Path {
    val count = employeeRepository.count().toInt()

    val provider = simpleDataProvider {
        items("employees", count) {
            employeeRepository.streamAll().iterator()
        }
    }

    return excelGenerator.generateToFile(
        template = template,
        dataProvider = provider,
        outputDir = outputDir,
        baseFileName = "large_report"
    )
}
```

---

### Recommended approach by data size

| Data Size | Data Provision Method | Additional Settings |
|-----------|----------------------|---------------------|
| Up to 1,000 rows | `Map<String, Any>` | None |
| 1,000 - 10,000 rows | `simpleDataProvider` + count | None |
| 10,000 - 100,000 rows | `simpleDataProvider` + count + Stream | `generateToFile()` recommended |
| Over 100,000 rows | Custom DataProvider + Stream | `generateToFile()` + memory settings |

---

## 3. Error Prevention

### Use `THROW` mode during development

Setting `MissingDataBehavior.THROW` lets you detect missing data immediately.

```kotlin
// Development environment
val devConfig = TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)

// Production environment (default)
val prodConfig = TbegConfig(missingDataBehavior = MissingDataBehavior.WARN)
```

In Spring Boot, you can configure this per profile:

```yaml
# application-dev.yml
tbeg:
  missing-data-behavior: throw

# application-prod.yml
tbeg:
  missing-data-behavior: warn
```

---

### Ensure `@Transactional` when using JPA Stream

A JPA Stream closes when its transaction ends. The transaction must remain open until Excel generation is complete.

```kotlin
@Transactional(readOnly = true)  // Required
fun generateReport(): ByteArray {
    val provider = simpleDataProvider {
        items("employees", count) {
            employeeRepository.streamAll().iterator()
        }
    }
    return excelGenerator.generate(template, provider)
}
```

> [!WARNING]
> Without `@Transactional`, a `LazyInitializationException` may occur.

---

### Write unit tests

Writing tests for your report generation logic helps catch errors caused by template changes early.

```kotlin
@Test
fun `직원 보고서 생성 테스트`() {
    val data = mapOf(
        "title" to "테스트",
        "employees" to listOf(Employee("황용호", "부장", 8000))
    )

    ExcelGenerator().use { generator ->
        val template = ClassPathResource("templates/employees.xlsx").inputStream
        val bytes = generator.generate(template, data)

        // Verify results
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("황용호", sheet.getRow(1).getCell(0).stringCellValue)
        }
    }
}
```

---

## Next Steps

- [Troubleshooting](./troubleshooting.md) - Diagnosing and resolving errors
- [API Reference](./reference/api-reference.md) - Detailed API documentation
- [Advanced Examples](./examples/advanced-examples.md) - Real-world examples
