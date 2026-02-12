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

**Symptom**: Cells containing formula-style markers (`=TBEG_REPEAT(...)`, `=TBEG_IMAGE(...)`, `=TBEG_SIZE(...)`) show a `#NAME?` error.

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

## 2. Runtime Errors

### `TemplateProcessingException`

This exception is thrown when a syntax error is found during template parsing. Use the `errorType` to identify the type of error.

| ErrorType | Cause | Resolution |
|-----------|-------|------------|
| `INVALID_REPEAT_SYNTAX` | Repeat marker syntax error | Verify the format: `${repeat(collection, range, variable)}` |
| `MISSING_REQUIRED_PARAMETER` | Required parameter missing | `collection` and `range` are required for repeat |
| `INVALID_RANGE_FORMAT` | Invalid cell range format | Use a valid range such as `A2:C2` |
| `SHEET_NOT_FOUND` | Reference to a non-existent sheet | Verify the sheet name is correct (`'Sheet1'!A2:C2`) |
| `INVALID_PARAMETER_VALUE` | Invalid parameter value | `direction` only accepts `DOWN` or `RIGHT` |

> [!NOTE]
> Mixing named parameters and positional parameters results in an `INVALID_REPEAT_SYNTAX` error. Use only one style within a single marker.

---

### `MissingTemplateDataException`

When `missingDataBehavior = THROW`, this exception is thrown if data required by the template is not found in the DataProvider.

```
MissingTemplateDataException: 템플릿에 필요한 데이터가 누락되었습니다.
  누락된 변수: [title, author]
  누락된 컬렉션: [employees]
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
1. Verify streaming mode: `StreamingMode.ENABLED` (default)
2. Use lazy loading in DataProvider (`items("name", count) { ... }`)
3. Increase JVM heap memory: e.g., `-Xmx2g`
4. Split the data across multiple output files

---

## 3. Output File Issues

### Chart data range is incorrect

**Symptom**: Data rows were expanded by repeat, but the chart still references the original range.

**Resolution**: TBEG automatically adjusts chart data source ranges. If this issue occurs:
- Verify that the chart's data source references the repeat area correctly
- Verify that the chart and the repeat area are on the same sheet

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

## 4. Performance Issues

### Generation speed is slow

Check the following steps in order.

**Step 1: Verify streaming mode**

```kotlin
val config = TbegConfig(streamingMode = StreamingMode.ENABLED) // default
```

**Step 2: Verify count is provided**

Providing count prevents double traversal of data, improving performance.

```kotlin
items("employees", employeeCount) {
    employeeRepository.streamAll().iterator()
}
```

**Step 3: Use lazy loading**

Instead of loading all data upfront, leverage lambdas.

```kotlin
items("employees") {
    employeeRepository.findAll().iterator()
}
```

**Step 4: Use DB streaming**

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
