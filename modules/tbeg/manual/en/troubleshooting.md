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

### Bundle column range does not match hideable cell

**Symptom**: Error "bundle column range does not match the hideable cell's column range."

**Cause**: The bundle range's column differs from the hideable marker cell's (or merged cell's) column.

**Resolution**: Adjust the bundle range column to match the hideable marker cell. For example, if the hideable marker is in C2, the bundle range should include column C (e.g., `C1:C3`).

---

## 2. Runtime Errors

### `TemplateProcessingException`

This exception is thrown when a syntax error is found during template parsing. Use the `errorType` to identify the type of error.

| ErrorType | Cause | Resolution |
|-----------|-------|------------|
| `INVALID_REPEAT_SYNTAX` | Repeat marker syntax error | Verify the format: `${repeat(collection, range, variable)}` |
| `MISSING_REQUIRED_PARAMETER` | Required parameter missing | `collection` and `range` are required for repeat |
| `INVALID_RANGE_FORMAT` | Invalid cell range format | Use a valid range such as `A2:C2` |
| `SHEET_NOT_FOUND` | Reference to a non-existent sheet | Verify the sheet name is correct (`'Sheet1'!A2:C2`) |
| `INVALID_PARAMETER_VALUE` | Invalid parameter value | `direction` only accepts `DOWN` or `RIGHT`. Bundle nesting and boundary overlap are also reported with this error |

> [!NOTE]
> Mixing named parameters and positional parameters results in an `INVALID_REPEAT_SYNTAX` error. Use only one style within a single marker.

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

---

## 3. Output File Issues

### Chart data range is incorrect

**Symptom**: Data rows were expanded by repeat, but the chart still references the original range.

**Resolution**: TBEG automatically adjusts chart data source ranges. If this issue occurs:
- Verify that the chart's data source references the repeat area correctly
- Verify that the chart and the repeat area are on the same sheet

---

### Merge results are not as expected

**Symptom**: After merging with `${merge(item.field)}`, the same values are split into multiple merge groups.

**Cause**: The merge marker only merges **consecutive** cells with the same value. If the same value appears in non-adjacent positions, they become separate merge groups.

**Resolution**: Pre-sort the data by the merge key field.

```kotlin
// Before sorting: [Sales Team 1, Sales Team 2, Sales Team 1] -> Sales Team 1 is split into 2 groups
// After sorting: [Sales Team 1, Sales Team 1, Sales Team 2] -> Sales Team 1 is merged into one group
val employees = employeeRepository.findAll().sortedBy { it.department }
```

---

### Bundle range error occurs

**Symptom**: An `INVALID_PARAMETER_VALUE` error with a bundle-related message is displayed.

**Cause and Resolution**:

| Cause | Resolution |
|-------|------------|
| Repeat overlaps bundle boundary | Adjust the bundle range to fully contain the repeat region |
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

**Symptom**: A field was specified in `hideFields()`, but it is not hidden in the output file.

**Cause**: The template does not have a hideable marker for the specified field.

**Resolution**: Add a `${hideable(value=item.fieldName)}` marker to the template. If `unmarkedHidePolicy` is set to `WARN_AND_HIDE`, the field will be hidden even without a marker, but a warning log will be emitted.

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

| Data Size | Recommended Approach |
|-----------|---------------------|
| Up to 1,000 rows | Map-based approach is sufficient |
| 1,000 - 10,000 rows | simpleDataProvider + count |
| 10,000 - 100,000 rows | simpleDataProvider + count + DB Stream |
| Over 100,000 rows | Custom DataProvider + DB Stream + `generateToFile()` |

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
