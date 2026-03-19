> **[한국어](../ko/best-practices.md)** | English

# TBEG Best Practices

## Table of Contents
1. [Template Design](#1-template-design)
2. [Performance Optimization](#2-performance-optimization)
3. [Server Operations](#3-server-operations)
4. [Cell Merge and Bundle](#4-cell-merge-and-bundle)
5. [Error Prevention](#5-error-prevention)

---

## 1. Template Design

### Place repeat markers outside the repeat range

The `${repeat(...)}` marker can be placed anywhere in the workbook as long as it is outside the repeat range. Placing the marker in a header row above the data area improves readability.

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A2:C2, emp)} |                 |               |
| 2 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

- Row 1: repeat marker (placed outside the repeat range)
- Row 2: repeat range

---

### Place formulas below the data area

Formulas referencing a repeat area have their ranges automatically adjusted regardless of where they are placed. However, placing formulas below the repeat area provides a natural order (data then aggregation) when editing the template in Excel, improving readability.

|   | A                             | B             | C               |
|---|-------------------------------|---------------|-----------------|
| 1 | ${repeat(items, A2:C2, item)} |               |                 |
| 2 | ${item.name}                  | ${item.value} | ${item.qty}     |
| 3 | Total                         | =SUM(B2:B2)   | =AVERAGE(C2:C2) |

The formulas in row 3 are automatically adjusted to `=SUM(B2:BN)` and `=AVERAGE(C2:CN)` when the repeat area expands. In addition to `SUM` and `AVERAGE`, all range-referencing formulas such as `COUNT`, `MAX`, `MIN`, etc. are adjusted in the same way.

---

### Keep one data record per row

Design repeat ranges intuitively. Use one row per data record as the default, and switch to multi-row repetition when a more complex layout is needed.

**Recommended** -- One-row repetition:

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A2:C2, emp)} |                 |               |
| 2 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

**For complex layouts** -- Two-row repetition:

|   | A                                | B                   |
|---|----------------------------------|---------------------|
| 1 | ${repeat(employees, A2:B3, emp)} |                     |
| 2 | Name: ${emp.name}                | Position: ${emp.position} |
| 3 | Salary: ${emp.salary}            |                     |

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

### Mark potentially hideable fields with hideable markers

Using `${hideable(...)}` markers allows dynamically hiding specific fields from code.

|   | A                                | B               | C                                           |
|---|----------------------------------|-----------------|---------------------------------------------|
| 1 | Name                             | Position        | Salary                                      |
| 2 | ${emp.name}                      | ${emp.position} | ${hideable(value=emp.salary, bundle=C1:C3)} |
| 3 | Total                            |                 | =SUM(C2:C2)                                 |
| 4 | ${repeat(employees, A2:C2, emp)} |                 |                                             |

- The `bundle` parameter lets you manage the field title and formulas together
- `DIM` mode is useful when you want to deactivate while preserving the layout
- `DELETE` mode (default) physically removes the column

---

### Align bundle range with merged cells

If the `bundle` range of a hideable marker partially includes a merged cell, an error occurs. Set the bundle range to either fully include or completely exclude merged cells.

---

### Avoid overlapping repeat areas

When placing multiple repeat areas on the same sheet, ensure that no areas overlap in 2D space (rows x columns).

**Correct** -- Separate column groups:

|   | A (employees)  | B (employees)  | C | D (departments)  | E (departments)  |
|---|----------------|----------------|---|------------------|------------------|
|   | ...            | ...            |   | ...              | ...              |

**Correct** -- Separate row groups:

|   | A (employees)    | B (employees)    |
|---|------------------|------------------|
|   | ...              | ...              |
|   | A (departments)  | B (departments)  |
|   | ...              | ...              |

---

## 2. Performance Optimization

### Three-Step Optimization Guide

Apply the following steps based on your data size.

#### Step 1: Provide count

Supplying the total count alongside the collection in a DataProvider prevents double traversal of the data.

```kotlin
val count = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    items("employees", count) {
        employeeRepository.findAll().iterator()
    }
}
```

#### Step 2: Lazy loading

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

#### Step 3: DB streaming

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
| Over 10,000 rows | `simpleDataProvider` + count + Stream | `generateToFile()` recommended |

---

## 3. Server Operations

### Memory Sizing Guidelines

TBEG renders in streaming mode, so memory usage during processing remains constant. However, the output file is held in memory, so **heap proportional to the output file size** is required.

| Data Volume | Recommended `-Xmx` | Notes |
|-------------|---------------------|-------|
| Up to 10,000 rows | 512MB | Sufficient for most general reports |
| 100,000 rows | 1-2GB | |
| 500,000 rows | 4GB | |
| 1,000,000 rows | 8GB | |

> When generating multiple reports concurrently, multiply the above values by the number of concurrent requests. Actual requirements may vary depending on the number of columns, formatting complexity, and whether images are included.

> For templates with pivot tables, the pivot regeneration process loads the entire output file into memory, so approximately 300,000 rows is the practical upper limit.

### Handling Concurrent Requests

`ExcelGenerator` is designed to be thread-safe, so a single instance can be used concurrently by multiple threads. Register it as a Spring singleton bean.

```kotlin
@Configuration
class TbegConfig {
    @Bean
    fun excelGenerator() = ExcelGenerator()
}
```

> When generating multiple reports concurrently, estimate the heap memory by summing the output file size of each request.

---

## 4. Cell Merge and Bundle

### Sort data when using merge markers

The `${merge(object.field)}` marker automatically merges consecutive cells with the same value. Therefore, data must be pre-sorted by the merge key field to achieve the intended result.

```
Data: [Sales Team 1, Sales Team 2, Sales Team 1]  -> Sales Team 1 is split, so separate cells remain
Data: [Sales Team 1, Sales Team 1, Sales Team 2]  -> Sales Team 1: 2 cells merged, Sales Team 2: 1 cell
```

```kotlin
// Recommended: sort by merge key field
val employees = employeeRepository.findAll()
    .sortedBy { it.department }  // Sort by department

items("employees", employees)
```

---

### Use bundle to protect complex layouts

When a table spanning multiple columns exists below a repeat area, the table may become misaligned due to repeat expansion. Wrapping the table with `${bundle(range)}` ensures it always moves as a unit.

|   | A                               | B               | C    | D    | E      |
|---|----------------------------------|-----------------|------|------|--------|
| 1 | ${repeat(depts, A2:B2, dept)}   |                 |      |      |        |
| 2 | ${dept.name}                    | ${dept.revenue} |      |      |        |
| 3 | ${bundle(A4:E6)}                |                 |      |      |        |
| 4 | Name                            | Revenue         | Cost | Profit | Total |
| 5 | Yongho Hwang                    | 1000            | 500  | 500  | 2000   |
| 6 | Total                           |                 |      |      | =SUM() |

Without a bundle, only columns within the repeat column range (A-B) would shift while the rest (C-E) stay in their original rows, breaking the table layout. Wrapping rows 4-6 with a bundle ensures all columns A-E move as a unit, preserving the layout. For a detailed comparison, see the [Template Syntax Reference - Bundle](./reference/template-syntax.md#8-bundle).

---

## 5. Error Prevention

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
fun `employee report generation test`() {
    val data = mapOf(
        "title" to "Test",
        "employees" to listOf(Employee("Yongho Hwang", "Director", 8000))
    )

    ExcelGenerator().use { generator ->
        val template = ClassPathResource("templates/employees.xlsx").inputStream
        val bytes = generator.generate(template, data)

        // Verify results
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("Yongho Hwang", sheet.getRow(1).getCell(0).stringCellValue)
        }
    }
}
```

---

## Next Steps

- [Troubleshooting](./troubleshooting.md) - Diagnosing and resolving errors
- [API Reference](./reference/api-reference.md) - Detailed API documentation
- [Advanced Examples](./examples/advanced-examples.md) - Real-world examples
