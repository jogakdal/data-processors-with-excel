> **[한국어](./DEVELOPMENT.ko.md)** | English

# TBEG Development Guide

This document defines the architecture, implementation principles, and development guidelines for the Template-Based Excel Generator (TBEG) module.
**This document must be updated whenever the code is modified.**

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Template Syntax](#template-syntax)
5. [Implemented Features](#implemented-features)
6. [Implementation Principles](#implementation-principles)
7. [Configuration Options](#configuration-options)
8. [Limitations](#limitations)
9. [Internal Optimizations](#internal-optimizations)
10. [Extension Points](#extension-points)
11. [Testing Guide](#testing-guide)

---

## Architecture Overview

### Overall Structure

```
┌─────────────────────────────────────────────────────────────┐
│                     ExcelGenerator                          │
│                      (Public API)                           │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      TbegPipeline                           │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │ChartExtract  │ → │PivotExtract  │ → │TemplateRender│     │
│  └──────────────┘   └──────────────┘   └──────┬───────┘     │
│                                               │             │
│                                               ▼             │
│                                    ┌──────────────────┐     │
│                                    │ RenderingEngine  │     │
│                                    │ ┌──────┬───────┐ │     │
│                                    │ │ XSSF │ SXSSF │ │     │
│                                    │ └──────┴───────┘ │     │
│                                    └──────────────────┘     │
│                                               │             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────▼───────┐     │
│  │  Metadata    │ ← │ ChartRestore │ ← │ NumberFormat │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Pipeline Processing Order

| Order | Processor          | Class                         | Role                                      | Execution Condition |
|-------|--------------------|-------------------------------|--------------------------------------------|---------------------|
| 1     | ChartExtract       | `ChartExtractProcessor`       | Extract chart info and temporarily remove   | SXSSF mode          |
| 2     | PivotExtract       | `PivotExtractProcessor`       | Extract pivot table info                    | Always              |
| 3     | TemplateRender     | `TemplateRenderProcessor`     | Template rendering (XSSF/SXSSF strategy)   | Always              |
| 4     | NumberFormat       | `NumberFormatProcessor`       | Auto-apply number formats                   | Always              |
| 5     | XmlVariableReplace | `XmlVariableReplaceProcessor` | Variable substitution within XML            | Always              |
| 6     | PivotRecreate      | `PivotRecreateProcessor`      | Recreate pivot tables                       | When pivots exist   |
| 7     | ChartRestore       | `ChartRestoreProcessor`       | Restore charts and adjust data ranges       | When charts exist   |
| 8     | Metadata           | `MetadataProcessor`           | Apply document metadata                     | Always              |

### Rendering Strategy (Strategy Pattern)

| Strategy | Class                      | Condition                | Characteristics                    |
|----------|----------------------------|--------------------------|------------------------------------|
| SXSSF    | `SxssfRenderingStrategy`   | Streaming mode (default) | 100-row buffer, memory efficient   |
| XSSF     | `XssfRenderingStrategy`    | Non-streaming mode       | Full in-memory load, all features  |

---

## Project Structure

```
src/main/kotlin/io/github/jogakdal/tbeg/
├── ExcelGenerator.kt                       # Main entry point (Public API)
├── ExcelDataProvider.kt                    # Data provider interface
├── SimpleDataProvider.kt                   # Simple Map-based DataProvider implementation
├── TbegConfig.kt                           # Configuration class (Builder pattern)
├── DocumentMetadata.kt                     # Document metadata
├── Enums.kt                                # Enums: StreamingMode, FileNamingMode, etc.
│
├── async/                                  # Async processing
│   ├── ExcelGenerationListener.kt          # Callback interface
│   ├── GenerationJob.kt                    # Async job handle
│   ├── GenerationResult.kt                 # Generation result DTO
│   └── ProgressInfo.kt                     # Progress info
│
├── engine/                                 # Internal engine (internal)
│   ├── core/                               # Core utilities
│   │   ├── ChartProcessor.kt               # Chart extraction/restoration
│   │   ├── PivotTableProcessor.kt          # Pivot table processing
│   │   ├── XmlVariableProcessor.kt         # Variable substitution in XML
│   │   └── ExcelUtils.kt                   # Utility functions
│   │
│   ├── pipeline/                           # Processing pipeline
│   │   ├── TbegPipeline.kt                 # Pipeline definition
│   │   ├── ExcelProcessor.kt               # Processor interface
│   │   ├── ProcessingContext.kt            # Processing context
│   │   └── processors/                     # Individual processors (8)
│   │       ├── ChartExtractProcessor.kt
│   │       ├── ChartRestoreProcessor.kt
│   │       ├── MetadataProcessor.kt
│   │       ├── NumberFormatProcessor.kt
│   │       ├── PivotExtractProcessor.kt
│   │       ├── PivotRecreateProcessor.kt
│   │       ├── TemplateRenderProcessor.kt
│   │       └── XmlVariableReplaceProcessor.kt
│   │
│   └── rendering/                          # Rendering strategies
│       ├── RenderingStrategy.kt            # Rendering strategy interface
│       ├── AbstractRenderingStrategy.kt    # Common logic
│       ├── XssfRenderingStrategy.kt        # XSSF (non-streaming)
│       ├── SxssfRenderingStrategy.kt       # SXSSF (streaming)
│       ├── TemplateRenderingEngine.kt      # Rendering engine
│       ├── TemplateAnalyzer.kt             # Template analyzer
│       ├── parser/                         # Marker parser (unified)
│       │   ├── MarkerDefinition.kt         #   Marker definitions
│       │   ├── ParameterParser.kt          #   Parameter parsing
│       │   ├── ParsedMarker.kt             #   Parse results
│       │   └── UnifiedMarkerParser.kt      #   Unified parser
│       ├── WorkbookSpec.kt                 # Workbook/sheet/cell specification
│       ├── PositionCalculator.kt           # Position calculation for repeat expansion
│       ├── StreamingDataSource.kt          # Streaming data source
│       ├── ImageInserter.kt                # Image insertion
│       ├── FormulaAdjuster.kt              # Formula adjustment
│       ├── RepeatExpansionProcessor.kt     # Repeat region expansion
│       └── SheetLayoutApplier.kt           # Layout application
│
├── exception/                              # Exception classes
│   ├── TemplateProcessingException.kt
│   ├── MissingTemplateDataException.kt
│   └── FormulaExpansionException.kt
│
└── spring/                                 # Spring Boot integration
    ├── TbegAutoConfiguration.kt            # Auto-configuration
    └── TbegProperties.kt                   # Configuration properties
```

---

## Core Components

### Public API

| Class                | Role                                                                      |
|----------------------|---------------------------------------------------------------------------|
| `ExcelGenerator`     | Main entry point. Provides `generate()`, `generateAsync()`, `submit()`    |
| `ExcelDataProvider`  | Data provider interface. Supports lazy loading of collections and images   |
| `SimpleDataProvider` | Simple Map-based DataProvider implementation. Supports lazy loading        |
| `TbegConfig`         | Configuration options (Builder pattern)                                   |
| `DocumentMetadata`   | Document metadata (title, author, etc.)                                   |

### Pipeline

| Class               | Role                                                       |
|---------------------|------------------------------------------------------------|
| `TbegPipeline`      | Manages and executes the processor chain                   |
| `ExcelProcessor`    | Processor interface (`process(context)`)                   |
| `ProcessingContext`  | Shared data between processors (workbook, expansion info)  |

### Rendering Engine

| Class                       | Role                                                  |
|-----------------------------|-------------------------------------------------------|
| `TemplateRenderingEngine`   | Selects and executes rendering strategy                |
| `TemplateAnalyzer`          | Template analysis (marker parsing, regex definitions)  |
| `WorkbookSpec`              | Analyzed template specification (SheetSpec, CellSpec)  |
| `PositionCalculator`        | Cell position calculation during repeat expansion      |
| `FormulaAdjuster`           | Automatic formula reference expansion                  |

### Streaming Support

| Class                      | Role                                        |
|----------------------------|---------------------------------------------|
| `SxssfRenderingStrategy`   | SXSSF-based streaming rendering             |
| `StreamingDataSource`      | Sequential data consumption via Iterator    |

### Async Processing

| Class                       | Role                                                  |
|-----------------------------|-------------------------------------------------------|
| `GenerationJob`             | Async job handle (supports cancellation and waiting)  |
| `ExcelGenerationListener`   | Callback interface (onStarted, onCompleted, onFailed) |
| `GenerationResult`          | Generation result DTO                                 |

---

## Template Syntax

### Syntax Overview

TBEG supports two forms of markers:

| Form              | Syntax           | Location     | Purpose                          |
|-------------------|------------------|--------------|----------------------------------|
| **Text marker**   | `${...}`         | Cell value   | General usage                    |
| **Formula marker**| `=TBEG_*(...)`   | Cell formula | Markers invisible in Excel       |

Formula markers display as `#NAME?` errors in Excel but are processed correctly during generation.

### Variable Substitution

**Text markers:**
```
${변수명}
${객체.속성}
${객체.속성.서브속성}
```

**Supported types:**
- data class, POJO properties/fields
- Map key access
- Getter methods (`getFieldName()`)

### Repeat Processing

**Text markers:**
```
${repeat(컬렉션, 범위, 변수)}
${repeat(컬렉션, 범위, 변수, DOWN)}
${repeat(컬렉션, 범위, 변수, RIGHT)}
```

**Formula markers:**
```
=TBEG_REPEAT(컬렉션, 범위, 변수)
=TBEG_REPEAT(컬렉션, 범위, 변수, DOWN)
=TBEG_REPEAT(컬렉션, 범위, 변수, RIGHT)
```

**Parameters:**

| Parameter   | Description                                          | Example       |
|-------------|------------------------------------------------------|---------------|
| collection  | Key name of the data                                 | `employees`   |
| range       | Cell range to repeat                                 | `A2:C2`       |
| var         | Variable name to reference each item                 | `emp`         |
| direction   | DOWN (default) or RIGHT                              | `DOWN`        |
| empty       | Replacement range for empty collections (optional)   | `A10:C10`     |

**Examples:**
```
${repeat(employees, A2:C2, emp)}
${repeat(employees, A2:C2, emp, DOWN, A10:C10)}
=TBEG_REPEAT(employees, A2:C2, emp)
=TBEG_REPEAT(employees, A2:C2, emp, DOWN, A10:C10)
=TBEG_REPEAT(months, B1:B2, m, RIGHT)
```

**Supported empty range formats:**
- Normal range: `A10:C10`
- Absolute coordinates: `$A$10:$C$10` ($ signs are ignored during parsing)
- Sheet reference: `'Sheet2'!A1:C1`

**Marker placement:** Anywhere outside the repeat range (even on a different sheet)

### Image Insertion

**Text markers:**
```
${image(이름)}
${image(이름, 위치)}
${image(이름, 위치, 크기)}
```

**Formula markers:**
```
=TBEG_IMAGE(이름)
=TBEG_IMAGE(이름, 위치)
=TBEG_IMAGE(이름, 위치, 크기)
```

**Size options:**

| Option                  | Description                              |
|-------------------------|------------------------------------------|
| `fit` or `0:0`          | Fit to cell/range size (default)         |
| `original` or `-1:-1`   | Keep original size                       |
| `200:150`               | Width 200px, height 150px                |
| `200:-1`                | Width 200px, maintain height ratio       |
| `-1:150`                | Height 150px, maintain width ratio       |
| `0:-1`                  | Fit to cell width, maintain height ratio |
| `-1:0`                  | Fit to cell height, maintain width ratio |

**Examples:**
```
${image(logo)}
${image(logo, B2)}
${image(logo, B2:D4)}
${image(logo, B2, 200:150)}
${image(logo, B2, original)}
=TBEG_IMAGE(logo, B2, 200:150)
```

### Collection Size

**Text markers:**
```
${size(컬렉션)}
```

**Formula markers:**
```
=TBEG_SIZE(컬렉션)
```

**Examples:**
```
총 직원 수: ${size(employees)}명
=TBEG_SIZE(employees)
```

### Variables in Formulas

Variables can also be used within formulas:

```
=HYPERLINK("${url}", "${text}")
=SUM(B${startRow}:B${endRow})
=IF(${condition}, "yes", "no")
```

### Composite Text

Multiple variables and text can be used together:

```
작성자: ${author} (${department})
```

### Marker Parser Structure

Marker parsing is handled by the **unified marker parser** in the `engine/rendering/parser/` package:

```
parser/
├── MarkerDefinition.kt       # Parameter schema per marker
├── ParameterParser.kt        # Common parameter parsing logic
├── ParsedMarker.kt           # Parse result data
└── UnifiedMarkerParser.kt    # Unified parser (entry point)
```

| Class                 | Role                                                              |
|-----------------------|-------------------------------------------------------------------|
| `MarkerDefinition`    | Parameter definitions per marker (name, required, default, aliases) |
| `ParameterParser`     | Positional + explicit parameter parsing                            |
| `ParsedMarker`        | Parse result (parameter Map)                                       |
| `UnifiedMarkerParser` | Detects text/formula markers and converts to `CellContent`         |

**Supported markers:**

| Marker   | Parameters                                                 |
|----------|------------------------------------------------------------|
| `repeat` | `collection`, `range`, `var`, `direction`, `empty`         |
| `image`  | `name`, `position`, `size`                                 |
| `size`   | `collection`                                               |

**Parameter formats:**
- Positional: `${repeat(employees, A2:C2, emp)}`
- Positional (with gaps): `${repeat(employees, A2:C2, , , A10:C10)}`
- Explicit: `${repeat(collection=employees, range=A2:C2, var=emp)}`

> **Note**: Positional and explicit parameters cannot be mixed.

**Adding a new marker:**
1. Add marker definition in `MarkerDefinition.kt`
2. Add new type to the `CellContent` sealed class
3. Add conversion logic in `UnifiedMarkerParser.convertToContent()`

---

## Implemented Features

### Feature List

| Feature                | Description                                  | Handled By                |
|------------------------|----------------------------------------------|---------------------------|
| Variable substitution  | Simple value binding                         | `TemplateRenderingEngine` |
| Nested variables       | Object property access                       | `TemplateRenderingEngine` |
| Repeat (DOWN)          | Row-direction expansion                      | `RenderingStrategy`       |
| Repeat (RIGHT)         | Column-direction expansion                   | `RenderingStrategy`       |
| Empty collection       | Replacement content via empty parameter      | `RenderingStrategy`       |
| Image insertion        | Dynamic images                               | `ImageInserter`           |
| Charts                 | Automatic data range expansion               | `ChartProcessor`          |
| Pivot tables           | Automatic source range expansion             | `PivotTableProcessor`     |
| Formula expansion      | Auto-expansion of repeat region references   | `FormulaAdjuster`         |
| Merged cells           | Automatic position adjustment                | `PositionCalculator`      |
| Conditional formatting | Automatic range adjustment                   | `FormulaAdjuster`         |
| Header/Footer          | Variable substitution support                | `XmlVariableProcessor`    |
| File encryption        | Open password setting                        | `ExcelGenerator`          |
| Async processing       | Background generation + progress tracking    | `GenerationJob`           |

---

## Implementation Principles

### 1. Rendering Principles

#### 1.1 Full Preservation of Template Formatting
All formatting defined in the template (alignment, font, color, borders, fill, row height, etc.) must be identically applied in the generated Excel file.

#### 1.2 Repeat Row Style Principle
All rows expanded by repeat follow the style (height, formatting) of the **repeat template row**.

- Single-row repeat: All expanded rows inherit the same template row style
- Multi-row repeat: Template row patterns cycle through repeatedly

```
Example: Single-row repeat (templateRow 6)
- actualRow 6 -> templateRow 6 style
- actualRow 7 -> templateRow 6 style
- actualRow 8 -> templateRow 6 style
```

#### 1.3 Row Height Conflict Resolution
When multiple template rows map to the same actualRow (from different column groups), `maxOf` is used to apply the tallest height.

- Reason: In Excel, row height applies to the entire row, so all cells must be displayed properly
- Ensures consistent results regardless of processing order

#### 1.4 Column Group Independence
Repeats in different column ranges have their positions calculated independently.

```
Example:
- Columns A-C: employees repeat (expanded by +2 rows)
- Columns F-H: department repeat (expanded by +3 rows)

At actualRow 10:
- Columns A-C perspective: templateRow = actualRow - employees expansion
- Columns F-H perspective: templateRow = actualRow - department expansion (within that column range only)
```

### 1.5 Empty Collection Handling (emptyRange)

Controls behavior when a collection is empty.

#### Default Behavior (no empty parameter)
If the collection is empty, one blank row (or column) is output in the repeat region.

#### When empty Parameter Is Specified
- **Pre-read emptyRangeContent**: During template analysis, cell contents and styles from the empty range are saved as `CellSnapshot`
- **Empty collection rendering**: The saved emptyRangeContent is output at the repeat region position
- **Empty source cell handling**: In the result file, cells are cleared to blank cells with default styles

#### emptyRange Size Handling

| emptyRange Size       | Handling                                                    |
|-----------------------|-------------------------------------------------------------|
| Single cell (smaller) | Merge the entire repeat region and insert the content       |
| Multiple cells (smaller) | Output only the emptyRange portion; rest as blank cells  |
| Larger                | Output only up to the repeat region size; truncate the rest |

#### Processing Location

| Mode                  | Handler Function                                          |
|-----------------------|-----------------------------------------------------------|
| XSSF                  | `XssfRenderingStrategy.writeEmptyRangeContent()`          |
| SXSSF (streaming)     | `SxssfRenderingStrategy.writeRepeatCellsForRow()`         |
| SXSSF (pendingRows)   | `SxssfRenderingStrategy.collectEmptyRangeContentCells()`  |

### 2. Memory Management Principles (SXSSF Mode)

#### 2.1 No Full Collection Memory Loading
In SXSSF mode, entire collections are not loaded into memory to support large data processing.

| Mode  | Memory Policy                                  | Reason                      |
|-------|------------------------------------------------|-----------------------------|
| SXSSF | Does not load entire collection into memory    | For large data processing   |
| XSSF  | Full memory loading allowed                    | For small data only         |

#### 2.2 DataProvider Re-invocation
When the same collection is used across multiple repeat regions, the DataProvider is called again.

- No temporary file (DiskCachedCollection) usage
- DataProvider.getItems() must be able to return the same data again (user responsibility)

#### 2.3 Sequential Iterator Consumption
Only one item currently being processed is kept in memory.

```kotlin
// StreamingDataSource usage
fun advanceToNextItem(repeatKey: RepeatKey): Any?
fun getCurrentItem(repeatKey: RepeatKey): Any?
```

### 3. Position Calculation Principles

#### 3.1 Automatic Formula/Range Expansion
Formulas and range references within repeat regions are automatically adjusted by the expansion amount.

```
Example: =SUM(C6) -> =SUM(C6:C105) (when expanded by 100 items)
```

**Handling by reference type:**

| Reference Type              | Handling                                           |
|-----------------------------|----------------------------------------------------|
| Relative reference (`B3`)         | Expanded to a range based on repeat expansion |
| Absolute reference (`$B$3`)       | Not expanded (fixed position)                 |
| Row absolute (`B$3`)             | Not expanded in DOWN direction                 |
| Column absolute (`$B3`)          | Not expanded in RIGHT direction                |
| Other sheet (`Sheet2!B3`)        | Processed using that sheet's repeat expansion info |

**Cross-sheet reference handling:**
- `expandToRangeWithCalculator()` receives other sheet's expansion info via the `otherSheetExpansions` parameter
- `SheetExpansionInfo` includes per-sheet `expansions` and `collectionSizes`
- Sheet name extraction: `Sheet1!` -> `"Sheet1"`, `'Sheet Name'!` -> `"Sheet Name"`

#### 3.2 Static Element Position Shifting
Static elements (formulas, merged cells, conditional formatting, etc.) affected by repeat expansion are shifted by the expansion amount.

#### 3.3 PositionCalculator Position Determination Rules

1. Elements not affected by any repeat: Stay at the template position
2. Elements affected by only one repeat: Shifted by that repeat's expansion
3. Elements affected by two or more repeats: Moved to the most shifted position

### 4. Number Format Principles

#### 4.1 Automatic Number Format Conditions
When a value auto-generated by the library is numeric and the cell's display format is unset or "General", a number format is automatically applied.

- Integer: `pivotIntegerFormatIndex` (default 3, `#,##0`)
- Decimal: `pivotDecimalFormatIndex` (default 4, `#,##0.00`)

#### 4.2 Automatic Alignment Conditions
When a value auto-generated by the library is numeric and the cell's alignment is "General", right alignment is automatically applied.

#### 4.3 Existing Format Preservation
Even when conditions 4.1 and 4.2 apply, all other formatting (font, color, borders, etc.) retains the template formatting.

---

## StyleInfo Required Properties

List of properties that must be preserved during style copying:

| Property                        | Description          |
|---------------------------------|----------------------|
| `horizontalAlignment`           | Horizontal alignment |
| `verticalAlignment`             | Vertical alignment   |
| `fontBold`                      | Bold                 |
| `fontItalic`                    | Italic               |
| `fontUnderline`                 | Underline            |
| `fontStrikeout`                 | Strikethrough        |
| `fontName`                      | Font name            |
| `fontSize`                      | Font size            |
| `fontColorRgb`                  | Font color           |
| `fillForegroundColorRgb`        | Fill color           |
| `fillPatternType`               | Fill pattern         |
| `borderTop/Bottom/Left/Right`   | Borders              |
| `dataFormat`                    | Display format       |

---

## Configuration Options

### TbegConfig Defaults

| Option                      | Default               | Description                        |
|-----------------------------|-----------------------|------------------------------------|
| `streamingMode`             | `ENABLED`             | ENABLED (SXSSF) / DISABLED (XSSF) |
| `fileNamingMode`            | `TIMESTAMP`           | TIMESTAMP / NONE                   |
| `timestampFormat`           | `"yyyyMMdd_HHmmss"`  | DateTimeFormatter pattern          |
| `fileConflictPolicy`        | `SEQUENCE`            | SEQUENCE / ERROR                   |
| `progressReportInterval`    | `100`                 | Progress report interval (rows)    |
| `preserveTemplateLayout`    | `true`                | Preserve template layout           |
| `pivotIntegerFormatIndex`   | `3`                   | Integer format index (`#,##0`)     |
| `pivotDecimalFormatIndex`   | `4`                   | Decimal format index (`#,##0.00`)  |
| `missingDataBehavior`       | `WARN`                | WARN / THROW                       |

### Preset Configurations

```kotlin
// Optimized for large data processing
TbegConfig.forLargeData()
// streamingMode = ENABLED, progressReportInterval = 500

// For small data
TbegConfig.forSmallData()
// streamingMode = DISABLED
```

### Spring Boot Configuration (application.yml)

```yaml
tbeg:
  streaming-mode: enabled
  file-naming-mode: timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence
  progress-report-interval: 100
  preserve-template-layout: true
  missing-data-behavior: warn
```

---

## Limitations

### SXSSF (Streaming) Mode

| Item                                 | Supported | Notes                                         |
|--------------------------------------|-----------|-----------------------------------------------|
| Formulas referencing rows below      | Yes       | Auto-expanded when referencing repeat regions  |
| Formulas referencing rows above      | Yes       | Automatically adjusted                         |
| Auto-expanding formulas (SUM, etc.)  | Yes       | Range auto-expansion                           |
| Charts                               | Yes       | Handled via Extract/Restore processors         |
| Pivot tables                         | Yes       | Handled via Extract/Recreate processors        |

### General Limitations

- Repeat regions must occupy the same 2D space
- Multiple repeats within the same column range can be stacked vertically
- Sequence numbers are attempted up to a maximum of 10,000

### Internal Constants

| Constant          | Value       | Location                         |
|-------------------|-------------|----------------------------------|
| SXSSF buffer size | 100 rows    | `SxssfRenderingStrategy.kt:49`  |
| Image margin      | 1px         | `ImageInserter.kt`              |
| EMU conversion    | 9525 EMU/px | `ImageInserter.kt`              |

---

## Internal Optimizations

### Style Caching

Prevents duplicate creation of identical styles.

| Class                   | Cache                        | Purpose              |
|-------------------------|------------------------------|----------------------|
| `PivotTableProcessor`   | `styleCache` (WeakHashMap)   | Pivot cell styles    |
| `NumberFormatProcessor`  | `styleCache`                | Number format styles |

### Field Caching

Optimizes reflection performance.

| Class                       | Cache           | Purpose         |
|-----------------------------|-----------------|-----------------|
| `TemplateRenderingEngine`   | `fieldCache`    | Field info      |
| `TemplateRenderingEngine`   | `getterCache`   | Getter methods  |

### calcChain Cleanup

Prevents formula recalculation errors.

- Both XSSF/SXSSF call `clearCalcChain()`
- Triggers formula recalculation when the file is opened in Excel

---

## Extension Points

### Adding a New Processor

1. Implement the `ExcelProcessor` interface
2. Register the processor in `TbegPipeline` (order matters)

```kotlin
class MyProcessor : ExcelProcessor {
    override val name: String = "MyProcessor"

    override fun shouldProcess(context: ProcessingContext): Boolean = true

    override fun process(context: ProcessingContext): ProcessingContext {
        // Processing logic
        return context
    }
}
```

**Registration location:** `ExcelGenerator.kt` line 71-80

### Adding a New Rendering Strategy

1. Implement the `RenderingStrategy` interface (or extend `AbstractRenderingStrategy`)
2. Modify the strategy selection logic in `TemplateRenderingEngine`

**Strategy selection location:** `TemplateRenderingEngine.kt` line 48-51

### Adding New Template Syntax

1. Add marker definition in `MarkerDefinition.kt` (parameter schema)
2. Add new type to the `CellContent` sealed class
3. Add conversion logic in `UnifiedMarkerParser.convertToContent()`
4. Handle the new specification in the rendering strategy

**Marker definition location:** `parser/MarkerDefinition.kt`

```kotlin
// Example of adding a new marker
val NEW_MARKER = MarkerDefinition("newmarker", listOf(
    ParameterDef("param1", required = true),
    ParameterDef("param2", aliases = setOf("p2", "alt")),
    ParameterDef("param3", defaultValue = "default")
))
```

---

## Testing Guide

### Test Structure

```
src/test/
├── kotlin/io/github/jogakdal/tbeg/
│   ├── TbegTest.kt                     # Integration tests
│   ├── PerformanceBenchmark.kt         # Performance benchmark
│   ├── engine/
│   │   ├── rendering/
│   │   │   ├── PositionCalculatorTest.kt
│   │   │   ├── FormulaAdjusterTest.kt
│   │   │   └── ...
│   │   └── ...
│   └── ...
└── resources/
    └── templates/                      # Test templates
        ├── template.xlsx
        ├── simple_template.xlsx
        └── ...
```

### Running Tests

```bash
# All tests
./gradlew :tbeg:test

# Specific test
./gradlew :tbeg:test --tests "*PositionCalculatorTest*"
```

### Running Samples

```bash
# Kotlin sample
./gradlew :tbeg:runSample          # Output: build/samples/

# Java sample
./gradlew :tbeg:runJavaSample      # Output: build/samples-java/

# Spring Boot sample
./gradlew :tbeg:runSpringBootSample  # Output: build/samples-spring/

# Performance benchmark
./gradlew :tbeg:runBenchmark
```

### Testing Principles

1. **Use template files**: Integration tests with real Excel templates
2. **Verify output files**: Open the generated Excel files to check results
3. **Edge cases**: Test with empty data, large data, special characters, etc.

---

## Performance Benchmark

### TBEG Performance

**Test environment**: Java 21, macOS, 3-column repeat + SUM formula

| Data Size     | DISABLED (XSSF) | ENABLED (SXSSF) | Speedup    |
|---------------|-----------------|-----------------|------------|
| 1,000 rows    | 172ms           | 147ms           | 1.2x       |
| 10,000 rows   | 1,801ms         | 663ms           | **2.7x**   |
| 30,000 rows   | -               | 1,057ms         | -          |
| 50,000 rows   | -               | 1,202ms         | -          |
| 100,000 rows  | -               | 3,154ms         | -          |

### Comparison with Other Libraries (30,000 rows)

| Library    | Time        | Notes                                                           |
|------------|-------------|-----------------------------------------------------------------|
| **TBEG**   | **1.1 sec** | Streaming mode                                                  |
| JXLS       | 5.2 sec     | [Benchmark source](https://github.com/jxlsteam/jxls/discussions/203) |
