> **[한국어](../ko/best-practices.md)** | English

# TBEG Best Practices

## Table of Contents
1. [Template Design](#1-template-design)
2. [Performance Optimization](#2-performance-optimization)
3. [Cell Merge and Bundle](#3-cell-merge-and-bundle)
4. [Error Prevention](#4-error-prevention)

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

When you place aggregate formulas such as `=SUM()` below the repeat area, the formula's reference range is automatically adjusted as the area expands.

|   | A                             | B             |
|---|-------------------------------|---------------|
| 1 | ${repeat(items, A2:B2, item)} |               |
| 2 | ${item.name}                  | ${item.value} |
| 3 | Total                         | =SUM(B2:B2)   |

The formula in row 3 is automatically adjusted to `=SUM(B2:BN)` when the repeat area expands.

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

## 3. Cell Merge and Bundle

### Sort data when using merge markers

The `${merge(item.field)}` marker automatically merges consecutive cells with the same value. Therefore, data must be pre-sorted by the merge key field to achieve the intended result.

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

In complex layouts where multiple repeat regions are stacked vertically, the default behavior is for upper region expansion to push lower regions down. If regions need to expand independently, wrap them with `${bundle(range)}`.

|   | A                                | B             | C | D                                   | E              |
|---|----------------------------------|---------------|---|-------------------------------------|----------------|
| 1 | ${bundle(A1:B5)}                 |               |   | ${bundle(D1:E5)}                    |                |
| 2 | ${repeat(employees, A3:B3, emp)} |               |   | ${repeat(departments, D3:E3, dept)} |                |
| 3 | Name                             | Salary        |   | Department                          | Budget         |
| 4 | ${emp.name}                      | ${emp.salary} |   | ${dept.name}                        | ${dept.budget} |

A bundle groups all elements within the range into a single unit so they are unaffected by expansion of other regions. The bundle range must fully contain the repeat region.

---

## 4. Error Prevention

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
