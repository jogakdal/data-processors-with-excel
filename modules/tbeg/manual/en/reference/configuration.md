> **[한국어](../../ko/reference/configuration.md)** | English

# TBEG Configuration Reference

## Table of Contents
1. [TbegConfig](#1-tbegconfig)
2. [Spring Boot Properties](#2-spring-boot-properties)
3. [Enum Types](#3-enum-types)
4. [Preset Configurations](#4-preset-configurations)

---

## 1. TbegConfig

### Package
```kotlin
io.github.jogakdal.tbeg.TbegConfig
```

### All Options

| Option                      | Type                  | Default               | Description                                          |
|-----------------------------|-----------------------|-----------------------|------------------------------------------------------|
| `streamingMode`             | `StreamingMode`       | `ENABLED`             | Streaming mode setting                               |
| `fileNamingMode`            | `FileNamingMode`      | `TIMESTAMP`           | File naming mode                                     |
| `timestampFormat`           | `String`              | `"yyyyMMdd_HHmmss"`  | Timestamp format for filenames                       |
| `fileConflictPolicy`        | `FileConflictPolicy`  | `SEQUENCE`            | Policy when filename conflicts occur                 |
| `progressReportInterval`    | `Int`                 | `100`                 | Progress callback interval (number of rows)          |
| `preserveTemplateLayout`    | `Boolean`             | `true`                | Preserve template layout (column widths, row heights)|
| `pivotIntegerFormatIndex`   | `Short`               | `3`                   | Integer number format index (`#,##0`)                |
| `pivotDecimalFormatIndex`   | `Short`               | `4`                   | Decimal number format index (`#,##0.00`)             |
| `missingDataBehavior`       | `MissingDataBehavior` | `WARN`                | Behavior when data is missing                        |

---

### Option Details

#### streamingMode

Configures the streaming mode.

| Value      | Description                                          |
|------------|------------------------------------------------------|
| `ENABLED`  | Memory-efficient, optimized for large data (default) |
| `DISABLED` | Uses the POI native API with shiftRows-based row insertion |

```kotlin
TbegConfig(streamingMode = StreamingMode.ENABLED)
```

#### fileNamingMode

File naming mode when using `generateToFile()`.

| Value       | Example                               |
|-------------|---------------------------------------|
| `NONE`      | `report.xlsx`                         |
| `TIMESTAMP` | `report_20260115_143052.xlsx` (default) |

```kotlin
TbegConfig(fileNamingMode = FileNamingMode.NONE)
```

#### timestampFormat

Timestamp format used when `fileNamingMode = TIMESTAMP`.

```kotlin
TbegConfig(
    fileNamingMode = FileNamingMode.TIMESTAMP,
    timestampFormat = "yyyy-MM-dd_HH-mm"  // report_2026-01-15_14-30.xlsx
)
```

#### fileConflictPolicy

Policy when a file with the same name already exists.

| Value      | Behavior                                                  |
|------------|-----------------------------------------------------------|
| `ERROR`    | Throws `FileAlreadyExistsException`                       |
| `SEQUENCE` | Appends a sequence number: `report_1.xlsx`, `report_2.xlsx` (default) |

```kotlin
TbegConfig(fileConflictPolicy = FileConflictPolicy.ERROR)
```

#### progressReportInterval

The interval (in number of rows) at which the `onProgress` callback is invoked during async operations.

```kotlin
TbegConfig(progressReportInterval = 500)  // Callback every 500 rows
```

#### preserveTemplateLayout

Whether to preserve the original template's column widths and row heights when rows are expanded by `${repeat(...)}`.

```kotlin
TbegConfig(preserveTemplateLayout = true)
```

#### pivotIntegerFormatIndex / pivotDecimalFormatIndex

Excel built-in format indices used for automatic number formatting. Applied automatically when the display format of a library-generated numeric cell is "General".

| Option                      | Default | Format         | Example Output |
|-----------------------------|---------|----------------|----------------|
| `pivotIntegerFormatIndex`   | `3`     | `#,##0`        | `1,234`        |
| `pivotDecimalFormatIndex`   | `4`     | `#,##0.00`     | `1,234.56`     |

```kotlin
TbegConfig(
    pivotIntegerFormatIndex = 3,   // Integer: #,##0
    pivotDecimalFormatIndex = 4    // Decimal: #,##0.00
)
```

> **Note**: For Excel built-in format indices, refer to the [Microsoft documentation](https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.numberingformat).

#### missingDataBehavior

Behavior when data corresponding to a variable/collection defined in the template is not provided.

| Value   | Behavior                                                   |
|---------|------------------------------------------------------------|
| `WARN`  | Logs a warning and leaves the marker as-is (default)       |
| `THROW` | Throws a `MissingTemplateDataException`                    |

```kotlin
TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)
```

---

### Creation Methods

#### Kotlin - Direct Construction

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,
    progressReportInterval = 500
)
val generator = ExcelGenerator(config)
```

#### Java - Using Builder

```java
TbegConfig config = TbegConfig.builder()
    .streamingMode(StreamingMode.ENABLED)
    .progressReportInterval(500)
    .build();

ExcelGenerator generator = new ExcelGenerator(config);
```

---

## 2. Spring Boot Properties

### Package
```kotlin
io.github.jogakdal.tbeg.spring.TbegProperties
```

### application.yml Example

```yaml
tbeg:
  # Streaming mode: enabled, disabled
  streaming-mode: enabled

  # File naming mode: none, timestamp
  file-naming-mode: timestamp

  # Timestamp format for filenames
  timestamp-format: yyyyMMdd_HHmmss

  # File conflict policy: error, sequence
  file-conflict-policy: sequence

  # Progress callback interval
  progress-report-interval: 100

  # Preserve template layout
  preserve-template-layout: true

  # Integer number format index (default: 3, #,##0)
  pivot-integer-format-index: 3

  # Decimal number format index (default: 4, #,##0.00)
  pivot-decimal-format-index: 4

  # Behavior when data is missing: warn, throw
  missing-data-behavior: warn
```

### Property Mapping

| application.yml Key           | TbegConfig Property            |
|-------------------------------|--------------------------------|
| `streaming-mode`              | `streamingMode`                |
| `file-naming-mode`            | `fileNamingMode`               |
| `timestamp-format`            | `timestampFormat`              |
| `file-conflict-policy`        | `fileConflictPolicy`           |
| `progress-report-interval`    | `progressReportInterval`       |
| `preserve-template-layout`    | `preserveTemplateLayout`       |
| `pivot-integer-format-index`  | `pivotIntegerFormatIndex`      |
| `pivot-decimal-format-index`  | `pivotDecimalFormatIndex`      |
| `missing-data-behavior`       | `missingDataBehavior`          |

---

## 3. Enum Types

### StreamingMode

```kotlin
enum class StreamingMode {
    DISABLED,  // Uses the POI native API
    ENABLED    // Memory-efficient (default)
}
```

| Value      | Recommended Use Case                          |
|------------|-----------------------------------------------|
| `DISABLED` | Small data with 1,000 rows or fewer           |
| `ENABLED`  | Large data with 10,000+ rows, memory-constrained environments |

### FileNamingMode

```kotlin
enum class FileNamingMode {
    NONE,      // Use base filename only
    TIMESTAMP  // Append timestamp (default)
}
```

| Value       | Example Output                  |
|-------------|---------------------------------|
| `NONE`      | `report.xlsx`                   |
| `TIMESTAMP` | `report_20260115_143052.xlsx`   |

### FileConflictPolicy

```kotlin
enum class FileConflictPolicy {
    ERROR,     // Throw exception
    SEQUENCE   // Append sequence number (default)
}
```

| Value      | Behavior                                                  |
|------------|-----------------------------------------------------------|
| `ERROR`    | Throws `FileAlreadyExistsException` if file exists        |
| `SEQUENCE` | `report.xlsx` -> `report_1.xlsx` -> `report_2.xlsx`       |

### MissingDataBehavior

```kotlin
enum class MissingDataBehavior {
    WARN,   // Log warning (default)
    THROW   // Throw exception
}
```

| Value   | Behavior                                                          |
|---------|-------------------------------------------------------------------|
| `WARN`  | Logs a warning and preserves the marker; useful for debugging     |
| `THROW` | Throws `MissingTemplateDataException`; for when data integrity matters |

---

## 4. Preset Configurations

### default()

Returns the default configuration.

```kotlin
val config = TbegConfig.default()
// or
val config = TbegConfig()
```

### forLargeData()

A configuration optimized for processing large datasets.

```kotlin
val config = TbegConfig.forLargeData()
// streamingMode = ENABLED
// progressReportInterval = 500
```

### forSmallData()

A configuration optimized for processing small datasets.

```kotlin
val config = TbegConfig.forSmallData()
// streamingMode = DISABLED
```

---

## Configuration Examples

### Large Report Generation

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,
    progressReportInterval = 1000
)
```

### Prevent File Duplication (Raise Error)

```kotlin
val config = TbegConfig(
    fileNamingMode = FileNamingMode.NONE,
    fileConflictPolicy = FileConflictPolicy.ERROR
)
```

### Custom Timestamp Format

```kotlin
val config = TbegConfig(
    fileNamingMode = FileNamingMode.TIMESTAMP,
    timestampFormat = "yyyy-MM-dd"  // report_2026-01-15.xlsx
)
```

### Throw Exception on Missing Data

```kotlin
val config = TbegConfig(
    missingDataBehavior = MissingDataBehavior.THROW
)
```

---

## Next Steps

- [API Reference](./api-reference.md) - ExcelGenerator API details
- [User Guide](../user-guide.md) - Complete guide
