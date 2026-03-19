> **[한국어](../ko/troubleshooting.md)** | English

# TBEG Troubleshooting Guide

## Table of Contents
1. [Template Issues](#1-template-issues)
2. [Runtime Errors](#2-runtime-errors)
3. [Output File Issues](#3-output-file-issues)
4. [Performance Issues](#4-performance-issues)
5. [Spring Boot Issues](#5-spring-boot-issues)

---

## 1. Template Issues

### `#NAME?` error is displayed

**Symptom**: Cells containing formula-style markers (`=TBEG_REPEAT(...)`, `=TBEG_IMAGE(...)`, `=TBEG_SIZE(...)`, `=TBEG_MERGE(...)`, `=TBEG_BUNDLE(...)`) show a `#NAME?` error.

**Cause**: These markers are not actual Excel functions, so Excel displays them as errors when the template file is opened.

**Resolution**: This is expected behavior. TBEG processes these markers correctly during Excel generation, and the `#NAME?` error does not appear in the output file.

---

### Markers remain in the output file as-is

**Symptom**: Markers such as `${title}` or `${emp.name}` appear in the output file without being substituted.

**Cause and Resolution**:

| Cause | Resolution |
|-------|------------|
| Key not found in data | Add the variable to the `data` map or DataProvider |
| Typo in variable name | Ensure the marker name in the template matches the key in the data exactly |
| Repeat variable used outside its range | Variables like `${emp.name}` are only valid within the repeat range |

> [!TIP]
> Setting `TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)` causes an exception when data is missing, making it easier to identify the root cause.

---

### Hideable marker is not within a repeat field

**Symptom**: Error "hideable marker is not within the repeat item field range."

**Cause**: The hideable marker is placed in a cell that is not a repeat item field.

**Resolution**: Move the hideable marker into the data row (within the repeat range). Hideable can only be used in repeat item fields.

---

### Bundle range does not match hideable cell

**Symptom**: One of the following errors occurs:
- "bundle **column** range (...) does not match the hideable cell's column range (...)" (DOWN repeat)
- "bundle **row** range (...) does not match the hideable cell's row range (...)" (RIGHT repeat)

**Cause**: The range specified in the hideable marker's `bundle` parameter does not align with the hideable marker's cell (or merged cell) along the expansion axis. For DOWN repeat, columns must match; for RIGHT repeat, rows must match.

**Resolution**: Adjust the `bundle` parameter range to match the hideable marker cell.
- DOWN repeat: If the hideable marker is in C2, use column C (e.g., `bundle=C1:C3`)
- RIGHT repeat: If the hideable marker is in C2, use row 2 (e.g., `bundle=B2:D2`)

---

## 2. Runtime Errors

### `TemplateProcessingException`

This exception is thrown when a syntax error is found during template parsing. Use the `errorType` to identify the type of error.

| ErrorType | Cause | Resolution |
|:---------:|:-----:|:-----------|
| `INVALID_MARKER_SYNTAX` | Marker syntax error | Verify the parentheses and parameter format of the marker |
| `MISSING_REQUIRED_PARAMETER` | Required parameter missing | Check the required parameters for each marker (e.g., `collection` and `range` for repeat) |
| `INVALID_RANGE_FORMAT` | Invalid cell range format | Use a valid range such as `A2:C2` |
| `SHEET_NOT_FOUND` | Reference to a non-existent sheet | Verify the sheet name is correct (`'Sheet1'!A2:C2`) |
| `INVALID_PARAMETER_VALUE` | Invalid parameter value | Check the valid values shown in the error message and correct the parameter |
| `RANGE_CONFLICT` | Range conflict (overlap or boundary crossing) | Separate overlapping ranges, or adjust so that one fully contains the other |

---

### `IllegalArgumentException`

This exception is thrown when named parameters and positional parameters are mixed in any functional marker (repeat, hideable, image, etc.).

**Resolution**: Use only one parameter style within a single marker -- either named parameters (`name=value`) or positional parameters.

---

### `MissingTemplateDataException`

When `missingDataBehavior = THROW`, this exception is thrown if data required by the template is not found in the DataProvider.

```
MissingTemplateDataException: Required template data is missing.
  - Variables: title, author
  - Collections: employees
```

**Resolution**: Add the missing items listed in the exception message to your DataProvider.

---

### `FormulaExpansionException`

This exception is thrown when automatic formula reference adjustment fails during row expansion by repeat.

**Common cause**: When repeat expansion occurs in an area containing merged cells and the Excel function argument limit (255) is exceeded.

**Resolution**: Review the sheet name, cell reference, and formula information included in the exception message, and adjust the formula placement in your template.

---

### `OutOfMemoryError`

This error occurs when the JVM runs out of memory while processing large datasets.

**Resolution steps**:
1. Use lazy loading in DataProvider (`items("name", count) { ... }`)
2. Increase JVM heap memory: e.g., `-Xmx2g`
3. Split the data across multiple output files

> For templates with pivot tables, the pivot regeneration process loads the entire output file into memory, so approximately 300,000 rows is the practical upper limit. Use templates without pivot tables for large datasets.

---

## 3. Output File Issues

### Chart data range is incorrect

**Symptom**: Data rows were expanded by repeat, but the chart still references the original range.

**Resolution**: TBEG automatically adjusts chart data source ranges. This works correctly even when the chart and repeat area are on different sheets. If this issue occurs:
- Verify that the chart's data source references the repeat area correctly

---

### Merge results are not as expected

**Symptom**: After merging with `${merge(object.field)}`, the same values are split into multiple merge groups.

**Cause**: The merge marker only merges **consecutive** cells with the same value. If the same value appears in non-adjacent positions, they become separate merge groups.

**Resolution**: Pre-sort the data by the merge key field.

```kotlin
// Before sorting: [Sales Team 1, Sales Team 2, Sales Team 1] -> Sales Team 1 is split into 2 groups
// After sorting: [Sales Team 1, Sales Team 1, Sales Team 2] -> Sales Team 1 is merged into one group
val employees = employeeRepository.findAll().sortedBy { it.department }
```

---

### Bundle range error occurs

**Symptom**: A `RANGE_CONFLICT` error with a bundle-related message is displayed.

**Cause and Resolution**:

| Cause | Resolution |
|:-----:|------------|
| Repeat area straddles the bundle boundary | Adjust so the repeat area is either fully inside or fully outside the bundle |
| Bundles are nested | Bundles cannot be nested. Separate the ranges |
| Invalid bundle range format | Use a valid range such as `A1:B10` |

---

### Numbers are displayed as text

**Symptom**: Numeric data does not have thousands separators applied, or is recognized as text.

**Resolution**:
- Check the data type: Pass values as numeric types (`Int`, `Long`, `Double`, etc.) rather than `String`
- Verify that the template cell has a number format applied

---

### Conditional formatting is not applied to expanded rows

**Symptom**: Conditional formatting in the repeat area is only applied to the original row.

**Resolution**: TBEG automatically expands the conditional formatting range for repeat areas. Verify that the conditional formatting's applied range matches the repeat marker's `range` parameter.

---

### Image specified via URL is not inserted

**Symptom**: An image URL was specified using `imageUrl()`, but the image does not appear in the output file.

**Cause and Resolution**:

| Cause | Log Message | Resolution |
|-------|-------------|------------|
| URL inaccessible | `Image download failed: HTTP 404` | Verify the URL is valid by opening it in a browser |
| Network timeout | `Image download failed: ...Timeout` | Check the server response time (connection 5s, read 10s limit) |
| File size exceeded | `Image download aborted: size limit exceeded` | Reduce the image size to under 10MB |
| Too many redirects | `Maximum redirect count exceeded` | Check the URL's redirect chain (max 3 redirects) |

> [!TIP]
> If the same image is used repeatedly across multiple reports, set `imageUrlCacheTtlSeconds` to reduce unnecessary downloads.
>
> ```kotlin
> TbegConfig(imageUrlCacheTtlSeconds = 60)  // Cache for 60 seconds
> ```

---

### hideFields was specified but the field is not hidden

**Symptom**: A field was specified in `hideFields()`, but it still appears in the output file.

**Cause and Resolution**:

- **Field name mismatch**: Verify that the field name specified in `hideFields` exactly matches the field name used in the template marker (e.g., `${emp.salary}`).
- **Collection name mismatch**: In `hideFields("employees", "salary")`, the first argument must match the repeat marker's collection name.
- **Field outside repeat**: `hideFields` only applies to repeat item fields. It does not apply to simple variables unrelated to a repeat.

> [!NOTE]
> If `hideFields` is specified without a hideable marker in the template, the default policy (`WARN_AND_HIDE`) hides the cell in DIM mode and emits a warning log. To physically remove the column in DELETE mode, add a `${hideable(value=object.fieldName)}` marker to the template. Setting `unmarkedHidePolicy` to `ERROR` causes an exception for fields without markers.

---

### Only the data area is hidden, but titles/footers remain

**Symptom**: A field was hidden via `hideFields()`, but only the data row values are hidden -- field titles and total rows remain visible.

**Cause**: The hideable marker does not have a `bundle` parameter, or `hideFields` was specified without a hideable marker in the template. Without a bundle, only the data cell where the marker is located is hidden.

**Resolution**: Use the `bundle` parameter in the hideable marker to specify the range that should be hidden together, including titles and totals.

```
${hideable(value=emp.salary, bundle=C1:C4)}
```

In this example, `C1:C4` covers the field title (C1), data rows (C2-C3), and total (C4). All cells within this range are hidden together.

---

### In DIM mode, field title text color is changed

**Symptom**: When DIM mode is used, the field title text color is lightened.

**Note**: DIM mode applies background color + text color + value removal to the repeat data area, while for bundle areas outside the repeat range (such as field titles), only the text color is lightened. Background and values are preserved.

---

## 4. Performance Issues

### Generation speed is slow

Check the following steps in order.

**Step 1: Verify count is provided**

Providing count prevents double traversal of data, improving performance.

```kotlin
items("employees", employeeCount) {
    employeeRepository.streamAll().iterator()
}
```

**Step 2: Use lazy loading**

Instead of loading all data upfront, leverage lambdas.

```kotlin
items("employees") {
    employeeRepository.findAll().iterator()
}
```

**Step 3: Use DB streaming**

Use JPA Stream or MyBatis Cursor to stream large datasets from the database.

```kotlin
items("employees", count) {
    employeeRepository.streamAll().iterator()
}
```

### Recommended settings by data size

| Data Size | Est. Generation Time | Recommended Approach |
|----------:|-----------:|:--|
| ~1,000 rows | ~20ms | Map-based approach is sufficient |
| ~10,000 rows | ~110ms | simpleDataProvider + count |
| ~50,000 rows | ~500ms | simpleDataProvider + count + DB Stream |
| ~100,000 rows | ~1s | simpleDataProvider + count + DB Stream |
| ~500,000 rows | ~5s | Custom DataProvider + DB Stream + `generateToFile()` |
| ~1,000,000 rows | ~9s | Custom DataProvider + DB Stream + `generateToFile()` |

> Estimated generation times are based on a 3-column repeat + SUM formula (DataProvider + generateToFile). Actual times may vary depending on the number of columns, formula complexity, and server specifications.

---

## 5. Spring Boot Issues

### `ExcelGenerator` bean is not registered

**Symptom**: `NoSuchBeanDefinitionException: No qualifying bean of type 'ExcelGenerator'`

**Resolution**:
1. Verify the dependency: Ensure `io.github.jogakdal:tbeg` is included in your dependencies
2. Check the package structure of the class annotated with `@SpringBootApplication`
3. If you have registered a custom bean manually, auto-configuration may be disabled due to `@ConditionalOnMissingBean`

---

### `LazyInitializationException`

**Symptom**: `LazyInitializationException` or `could not initialize proxy` error occurs when using JPA Stream.

**Cause**: A JPA entity's lazy-loading proxy was accessed outside of a transaction.

**Resolution**: Perform Excel generation within a `@Transactional` scope.

```kotlin
@Transactional(readOnly = true)
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
> When using JPA Stream, the `@Transactional` annotation is required. Since the stream closes when the transaction ends, the transaction must remain open until Excel generation is complete.

---

## Next Steps

- [Best Practices](./best-practices.md) - Recommended usage patterns
- [User Guide](./user-guide.md) - How to use TBEG
- [Configuration Options](./reference/configuration.md) - TbegConfig options
