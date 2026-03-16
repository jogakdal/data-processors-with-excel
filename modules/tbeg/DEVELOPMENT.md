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
│                   HidePreprocessor                          │
│              (1st pass: hideable preprocessing)             │
│                                                             │
│  hideable marker scan → determine hide targets → DELETE/DIM │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      TbegPipeline                           │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │ ChartExtract │ → │ PivotExtract │ → │TemplateRender│     │
│  └──────────────┘   └──────────────┘   └──────┬───────┘     │
│                                               │             │
│                                               ▼             │
│                                    ┌──────────────────┐     │
│                                    │ RenderingEngine  │     │
│                                    │ ┌──────────────┐ │     │
│                                    │ │   Default    │ │     │
│                                    │ └──────────────┘ │     │
│                                    └──────────────────┘     │
│                                               │             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────▼───────┐     │
│  │   Metadata   │ ← │ ChartRestore │ ← │ NumberFormat │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Pipeline Processing Order

| Order | Processor          | Class                         | Role                                      | Execution Condition     |
|-------|--------------------|-------------------------------|--------------------------------------------|-------------------------|
| 0     | HidePreprocess     | `HidePreprocessor`            | Hideable marker preprocessing (delete/DIM)  | When hideFields present |
| 1     | ChartExtract       | `ChartExtractProcessor`       | Extract chart info and temporarily remove   | Always                  |
| 2     | PivotExtract       | `PivotExtractProcessor`       | Extract pivot table info                    | Always                  |
| 3     | TemplateRender     | `TemplateRenderProcessor`     | Template rendering                          | Always                  |
| 4     | NumberFormat       | `NumberFormatProcessor`       | Auto-apply number formats                   | Always                  |
| 5     | XmlVariableReplace | `XmlVariableReplaceProcessor` | Variable substitution within XML            | Always                  |
| 6     | PivotRecreate      | `PivotRecreateProcessor`      | Recreate pivot tables                       | When pivots exist       |
| 7     | ChartRestore       | `ChartRestoreProcessor`       | Restore charts and adjust data ranges       | When charts exist       |
| 8     | Metadata           | `MetadataProcessor`           | Apply document metadata                     | Always                  |

### Rendering Strategy

| Class                          | Characteristics                                                        |
|--------------------------------|------------------------------------------------------------------------|
| `StreamingRenderingStrategy`   | Uses Apache POI SXSSF mode internally for memory-efficient processing  |

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
│   │   ├── CommonTypes.kt                  # Common types (CellCoord, CellArea, IndexRange, CollectionSizes, etc.)
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
│   ├── preprocessing/                      # Preprocessing (runs before the rendering pipeline)
│   │   ├── HidePreprocessor.kt            #   Hideable preprocessor (marker scan, hide decision, delete/DIM)
│   │   ├── HideValidator.kt               #   Bundle range validation (repeat position, column alignment, merged cells, overlap)
│   │   ├── ElementShifter.kt              #   Post-deletion element shifting (column/row shift, formula reference adjustment)
│   │   ├── HideableRegion.kt              #   Hide target region data class
│   │   └── CellUtils.kt                   #   Cell utilities (containsCell, setFormulaRaw)
│   │
│   └── rendering/                          # Rendering strategies
│       ├── RenderingStrategy.kt            # Rendering strategy interface
│       ├── AbstractRenderingStrategy.kt    # Common logic
│       ├── StreamingRenderingStrategy.kt     # Default rendering strategy
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

| Class                       | Role                                                                                  |
|-----------------------------|---------------------------------------------------------------------------------------|
| `TemplateRenderingEngine`   | Selects and executes rendering strategy                                                |
| `TemplateAnalyzer`          | Template analysis (marker parsing, duplicate marker detection)                         |
| `WorkbookSpec`              | Analyzed template specification (SheetSpec, RowSpec, CellSpec, RepeatRegionSpec, BundleRegionSpec) |
| `PositionCalculator`        | Cell position calculation during repeat expansion (chaining algorithm)                 |
| `FormulaAdjuster`           | Automatic formula reference expansion                                                  |

### Preprocessing

| Class                | Role                                                                                 |
|----------------------|--------------------------------------------------------------------------------------|
| `HidePreprocessor`   | Scans hideable markers before the rendering pipeline and applies delete/DIM to target fields |
| `HideValidator`      | Validates hideable bundle ranges (repeat position, column alignment, merged cell overlap)    |
| `ElementShifter`     | Shifts remaining elements after deletion (column/row shift, formula reference adjustment)   |
| `HideableRegion`     | Data class for hide target region information                                               |

### Streaming Support

| Class                          | Role                                        |
|--------------------------------|---------------------------------------------|
| `StreamingRenderingStrategy`   | Streaming-based rendering                   |
| `StreamingDataSource`          | Sequential data consumption via Iterator    |

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
${variableName}
${object.property}
${object.property.subProperty}
```

**Supported types:**
- data class, POJO properties/fields
- Map key access
- Getter methods (`getFieldName()`)

### Repeat Processing

**Text markers:**
```
${repeat(collection, range, variable)}
${repeat(collection, range, variable, DOWN)}
${repeat(collection, range, variable, RIGHT)}
```

**Formula markers:**
```
=TBEG_REPEAT(collection, range, variable)
=TBEG_REPEAT(collection, range, variable, DOWN)
=TBEG_REPEAT(collection, range, variable, RIGHT)
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
${image(name)}
${image(name, position)}
${image(name, position, size)}
```

**Formula markers:**
```
=TBEG_IMAGE(name)
=TBEG_IMAGE(name, position)
=TBEG_IMAGE(name, position, size)
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
${size(collection)}
```

**Formula markers:**
```
=TBEG_SIZE(collection)
```

**Examples:**
```
Total employees: ${size(employees)}
=TBEG_SIZE(employees)
```

### Automatic Cell Merge

**Text markers:**
```
${merge(item.field)}
```

**Formula markers:**
```
=TBEG_MERGE(item.field)
```

**Parameters:**

| Parameter | Description          | Example    |
|-----------|----------------------|------------|
| field     | In item.field format | `emp.dept` |

During repeat expansion, consecutive cells with the same value are automatically merged.
For DOWN repeats, vertical merging is applied; for RIGHT repeats, horizontal merging is applied.
Data must be pre-sorted by the merge key.

**Examples:**
```
${merge(emp.dept)}
=TBEG_MERGE(emp.dept)
```

### Selective Field Visibility (hideable)

Specific fields (columns) can be conditionally hidden during repeat expansion. Fields to hide are specified via `ExcelDataProvider.getHideFields()`.

**Text markers:**
```
${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}
${hideable(emp.salary)}
```

**Formula markers:**
```
=TBEG_HIDEABLE(emp.salary, C1:C3, dim)
=TBEG_HIDEABLE(emp.salary)
```

**Parameters:**

| Parameter | Description                           | Required | Default | Aliases      |
|-----------|---------------------------------------|----------|---------|--------------|
| value     | Field reference in item.field format  | Yes      |         | field, val   |
| bundle    | Cell range to hide together           |          |         | range        |
| mode      | Hide mode (DELETE / DIM)              |          | delete  |              |

**Hide modes:**

| Mode     | Behavior                                                                                   |
|----------|--------------------------------------------------------------------------------------------|
| `DELETE` | Physically delete and shift remaining elements (default)                                    |
| `DIM`    | Apply disabled style (background + font color) and remove values in the data region. For field titles and other cells outside the repeat range within the bundle, only font color is changed |

**Processing flow:**

1. `HidePreprocessor` runs before the rendering pipeline (1st pass preprocessing)
2. 2-pass scan: 1st phase identifies repeat variable names, 2nd phase identifies ItemField/HideableField
3. For fields specified in `getHideFields()`, DELETE or DIM processing is applied
4. Hideable markers for non-hidden fields are converted to `${item.field}` format and processed as regular ItemFields

**DIM mode behavior:**
- Only the repeat data region is DIM-processed (intersection of the bundle range and the repeat range)
- Cells outside the repeat range, such as field titles, are not subject to background/value changes (only font color is changed)

**Examples:**
```
${repeat(employees, A2:H2, emp)}
${hideable(emp.salary, C1:C3)}          <- salary field: column C + field title (C1) in bundle
${hideable(emp.bonus, D1:D3, dim)}      <- bonus field: DIM mode
${hideable(emp.name)}                   <- name field: no bundle, DELETE default
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
Author: ${author} (${department})
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
| `merge`  | `field`                                                    |
| `bundle` | `range`                                                    |
| `hideable` | `value` (required, aliases: field/val), `bundle` (alias: range), `mode` (default: delete) |

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

| Feature                  | Description                                  | Handled By                |
|--------------------------|----------------------------------------------|---------------------------|
| Duplicate marker detection | Warning and auto-removal for duplicate range markers | `TemplateAnalyzer`  |
| Variable substitution    | Simple value binding                         | `TemplateRenderingEngine` |
| Nested variables         | Object property access                       | `TemplateRenderingEngine` |
| Repeat (DOWN)            | Row-direction expansion                      | `RenderingStrategy`       |
| Repeat (RIGHT)           | Column-direction expansion                   | `RenderingStrategy`       |
| Empty collection         | Replacement content via empty parameter      | `RenderingStrategy`       |
| Image insertion          | Dynamic images                               | `ImageInserter`           |
| Automatic cell merge     | Auto-merge consecutive cells with same value | `MergeTracker`            |
| Charts                   | Automatic data range expansion               | `ChartProcessor`          |
| Pivot tables             | Automatic source range expansion             | `PivotTableProcessor`     |
| Formula expansion        | Auto-expansion of repeat region references   | `FormulaAdjuster`         |
| Merged cells             | Automatic position adjustment                | `PositionCalculator`      |
| Conditional formatting   | Automatic range adjustment                   | `FormulaAdjuster`         |
| Header/Footer            | Variable substitution support                | `XmlVariableProcessor`    |
| File encryption          | Open password setting                        | `ExcelGenerator`          |
| Selective field visibility | Hide fields via hideable marker (delete/DIM) | `HidePreprocessor`        |
| Async processing         | Background generation + progress tracking    | `GenerationJob`           |

---

## Implementation Principles

### 0. Core Design Philosophy: Excel-Native Features First

TBEG **does not re-implement what Excel already does well.** Aggregation, conditional formatting, chart rendering, and similar capabilities leverage Excel's native features as-is. TBEG's role is twofold:

1. Provide **dynamic data binding** that Excel cannot perform on its own -- variable substitution, repeat expansion, image insertion
2. **Preserve and adjust** Excel-native features so they continue to work as intended after data expansion -- formula range expansion, conditional formatting duplication, chart data range adjustment

This philosophy underpins every implementation principle below:
- **Rendering principles**: Template formatting is fully preserved because TBEG respects the formatting Excel has already finalized.
- **Formula handling**: Formulas are not recalculated; only their reference ranges are adjusted, because computation itself is Excel's responsibility.
- **Charts/Pivots**: They are preserved from the template rather than created anew, for the same reason.

When designing a new feature, first ask: "Can this be solved with an Excel-native feature?" If so, rather than implementing it in TBEG, guide the user to leverage that Excel feature in the template, and focus TBEG's effort on ensuring that feature works correctly after data expansion.

### 1. Rendering Principles

#### 1.1 Full Preservation of Template Formatting
All formatting defined in the template (alignment, font, color, borders, fill, row height, etc.) must be identically applied in the generated Excel file.
**Cells without values must also be preserved if they have styles.** Take care not to drop styles from `CellType.BLANK` cells during row copying and shifting in repeat expansion.

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
When multiple template rows map to the same actualRow (due to chaining producing different shifts across different columns), `maxOf` is used to apply the tallest height.

- Reason: In Excel, row height applies to the entire row, so all cells must be displayed properly
- Ensures consistent results regardless of processing order

#### 1.4 Chaining-Based Position Calculation
Repeats in different column ranges have their positions calculated independently.
**Even when multiple independent repeats exist on the same row**, they are managed as separate `RepeatRegionSpec` instances and expanded independently as long as their column ranges do not overlap.

Shift propagation is handled by the **chaining algorithm**:
- Each element (repeat, merged cell, bundle) finds the nearest resolved element above it in its column range to calculate the shift amount
- **Wide elements** (elements spanning multiple columns -- merged cells, bundles) propagate shifts across columns
- Static cells in gap columns (columns not belonging to any repeat) are not shifted unless connected by a wide element

```
Example 1: Repeats placed on different rows
- Columns A-C (row 3): employees repeat (expanded by +2 rows)
- Columns F-H (row 8): department repeat (expanded by +3 rows)

Example 2: Repeats placed on the same row
- Columns A-D (row 2): eventTypes repeat (5 items -> +4 rows)
- Columns J-K (row 2): languages repeat (4 items -> +3 rows)
-> Each repeat expands independently; resulting row count = max(5, 4)
```

**Key implementation details for multiple repeats on the same row:**
- `SheetSpec.repeatRegions`: Stores repeat region information as a `RepeatRegionSpec` list (region-centric, not row-centric)
- `TemplateAnalyzer.buildRowSpecs()`: When parsing cells in a repeat region's rows, recognizes all item variables
- `UnifiedMarkerParser.parse()`: Uses `repeatItemVariables: Set<String>` to recognize all item variables on the same row
- Non-repeat cells are processed only by the first repeat on the same row (prevents duplication)

### 1.5 Duplicate Marker Detection

Range-handling markers (repeat, image) that are declared multiple times for the same target produce a warning log, and only the last marker is retained.

#### TemplateAnalyzer 5-Phase Analysis Structure

```
analyzeWorkbook(workbook)
  Phase 1: Collect repeat markers from all sheets (collectRepeatRegions)
  Phase 2: Deduplicate repeat markers (deduplicateRepeatRegions)
  Phase 2.5: Collect and validate bundle markers (collectBundleRegions, validateBundleRegions)
  Phase 3: Build SheetSpec (analyzeSheet — uses deduplicated repeat/bundle list)
  Phase 4: Deduplicate cell-level range markers (deduplicateCellMarkers)
```

- **Phase 1-2**: Since repeat markers are extracted into `RepeatRegionSpec` and separated from cells, deduplication occurs before SheetSpec is built
- **Phase 2.5**: Bundle markers are collected and validated for nesting/boundary overlap
- **Phase 3-4**: Markers that remain in cells (e.g., image) are deduplicated as a post-processing step after SheetSpec creation (duplicate markers are replaced with `CellContent.Empty`)

#### Duplication Criteria

| Marker | Duplication Key                              | Notes                                                          |
|--------|----------------------------------------------|----------------------------------------------------------------|
| repeat | collection + target sheet + region (CellArea) | Target sheet is determined by the range's sheet prefix; defaults to current sheet |
| image  | name + target sheet + position + sizeSpec     | Images without a position are not subject to duplication checks |

#### Ordering with Overlap Validation

Deduplication (Phase 2) is **always executed before** overlap validation (`PositionCalculator.validateNoOverlap`).
Since duplicate repeats on the same region yield `overlaps() == true`, failure to deduplicate first would cause an exception during overlap validation.

```
TemplateAnalyzer.analyzeWorkbook()
  -> Phase 2: Remove duplicate repeats (warning log)     <- first
      v
RenderingStrategy.processSheet()
  -> validateNoOverlap(blueprint.repeatRegions)           <- later
```

### 1.6 Empty Collection Handling (emptyRange)

Controls behavior when a collection is empty.

#### Default Behavior (no empty parameter)
If the collection is empty, one blank row (or column) is output in the repeat region.

#### When empty Parameter Is Specified
- **Pre-read emptyRangeContent**: During template analysis, cell contents and styles from the empty range are saved as `CellSnapshot`
- **Empty collection rendering**: The saved emptyRangeContent is output at the repeat region position
- **Empty source cell handling**: In the result file, cells are cleared to blank cells with default styles

#### emptyRange Size Handling

| emptyRange Size         | Handling                                                    |
|-------------------------|-------------------------------------------------------------|
| Single cell (smaller)   | Merge the entire repeat region and insert the content       |
| Multiple cells (smaller)| Output only the emptyRange portion; rest as blank cells     |
| Larger                  | Output only up to the repeat region size; truncate the rest |

#### Processing Location

| Processing Path   | Handler Function                                                 |
|-------------------|------------------------------------------------------------------|
| streaming         | `StreamingRenderingStrategy.writeRepeatCellsForRow()`            |
| pendingRows       | `StreamingRenderingStrategy.collectEmptyRangeContentCells()`     |

### 2. Memory Management Principles

#### 2.1 No Full Collection Memory Loading
Entire collections are not loaded into memory to support large data processing.

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

**Reference shifting outside repeat regions:**

When a formula **inside** a repeat region references a cell **outside** the region, the reference is handled identically regardless of the presence of `$`:
- Not shifted during the copy step (`adjustForRepeatIndex`)
- Only shifted by row insertion (`adjustRefsOutsideRepeat`)

The processing order matters: `adjustRefsOutsideRepeat` -> `adjustForRepeatIndex`.
The shift is applied first so that inside/outside determination can be made accurately against the original formula.

```
Example: =J7/J8 (J8 is a Total row outside the repeat region; 3 rows expanded)
K7 (index 0): J7/J11   <- J8 -> J11 (shifted), J7 unchanged
K8 (index 1): J8/J11   <- J7 -> J8 (copy offset), J11 unchanged
K9 (index 2): J9/J11   <- J7 -> J9 (copy offset), J11 unchanged
```

#### 3.2 Element Shifting Principles

These principles determine where elements are shifted to when repeat expansion occurs.

**Core principle: When an element above is shifted, all elements below it are also shifted.** This applies regardless of element type or size.

The **cause** of shifting is the size change of expanding elements (repeats), but the **propagation** of shifting occurs through all elements in a chain fashion.

##### 3.2.1 Terminology

| Term | Definition |
|------|------------|
| **Element** | Any item with a position on the template sheet: single cell, merged cell, repeat range, image anchor, chart anchor, etc. |
| **Expanding element** | An element whose size varies depending on the data count. Currently only repeat qualifies |
| **Parent element** | For each column of the given element, the nearest element directly above. **Any element** -- single cell, merged cell, or repeat -- can be a parent candidate |
| **Absolute gap** | The fixed distance between the start row of the given element and the end row of the parent element in the template |
| **Rendered end** | The last row occupied by the element in the final output |

##### 3.2.2 Parent Element Lookup Rules

1. Search independently for **each column** the element occupies
2. Traverse upward to find the nearest (directly above) **element**
3. **Type/size agnostic**: Any element -- single cell, merged cell, repeat, image, chart -- qualifies as a parent candidate
4. When multiple candidates exist in the same column, only the **nearest (lowest)** one is selected

```
Example:
  Row 1:  [repeat]       <- element
  Row 5:  [single cell]  <- element
  Row 8:  [merged cell]  <- element
  Row 12: [image]        <- this element's parent = merged cell (nearest element)
```

##### 3.2.3 Shifted Position Calculation

```
Final start position = parent element's rendered end + absolute gap
Absolute gap = this element's template start row - parent element's template end row
```

Since the parent element's rendered end must be determined first, calculation proceeds **top-down sequentially** (dependency chain).

##### 3.2.4 Multi-Column Element Parent Handling (MAX)

Elements spanning multiple columns search for parents independently in each column.
The **maximum (MAX)** among each column's results is adopted as the final position.

```
Example:
  Col A-D:  Row 1: [repeat] -> 100 items expanded -> rendered end = Row 100
  Col E-H:  (no repeat)
  Col A-H:  Row 5: [merged cell] (spans all 8 columns)

  Merged cell's parent:
    Column A: repeat (rendered end 100) -> 100 + (5-1) = 104
    Column E: no parent -> 5 (not shifted)
    MAX -> 104 (merged cell final start = Row 104)
```

##### 3.2.5 Rendered End Calculation

| Element Type | Rendered End |
|--------------|-------------|
| **Expanding element (repeat)** | Final start + (data count x template row count) - 1 |
| **Regular element** | Final start + (template size - 1) |

- Expanding elements: Size changes based on data, so the rendered end may differ from the template
- Regular elements (including single cells): Size is fixed, so they simply shift by the shifted amount. For single cells, rendered end = final start

##### 3.2.6 When No Parent Element Exists

- If no element exists above in a given column, that column experiences no shift
- If **all columns** have no parent, the template position is retained (no shift)

##### 3.2.7 Cross-Column Shift Propagation

This is the essential core scenario where chaining is required.
Through elements spanning multiple columns (merged cells, bundles), shifts propagate even between repeats in different columns, preserving the layout.

```
Template:
  Col A-D:  Row 1:    [repeat] (A1:D1)          <- expands in columns A-D only
  Col A-H:  Row 5-10: [divider merge] (A5:H10)  <- spans all of A-H
  Col E-H:  Row 12:   [element X] (E12:H12)     <- columns E-H only

repeat -> 100 items expanded:

  X Without chaining (referencing only repeats):
    divider -> Row 104 (shifted by A-column repeat), rendered end = Row 109
    element X -> Row 12 (no repeat in column E, so no shift)
    -> element X remains above the divider -- layout broken!

  V With chaining (propagating through all elements):
    divider -> Row 104, rendered end = Row 109
    element X -> parent = divider -> 109 + (12-10) = Row 111
    -> element X correctly positioned below the divider
```

##### 3.2.8 Horizontal Expansion

The same principles apply to RIGHT-direction repeats. Simply substitute the directions:

| Vertical Expansion | Horizontal Expansion |
|-------------------|---------------------|
| Row | Column |
| Above | Left |
| Below | Right |
| Rendered end row | Rendered end column |

##### 3.2.9 No Horizontal/Vertical Overlap

Configurations where horizontal and vertical expansions intersect in 2D space are not supported. An error is raised.

##### 3.2.10 Scope of Application

- Applied in the streaming rendering strategy (`StreamingRenderingStrategy`)

##### 3.2.11 Bundle (Element Bundle)

Bundles group all elements within a specified range into a single wide element, applying shift policies consistently.
A bundle has no effect other than shift policy.

**Syntax:**

```
${bundle(A15:H20)}
=TBEG_BUNDLE(A15:H20)
```

**Marker placement:** Anywhere outside the bundle range (even on a different sheet)

**Behavior model:**

1. **Internal isolation**: The inside of a bundle is treated like an independent sheet
   - Parent element lookup for internal elements stops at the bundle boundary
   - Elements in the bundle's first row have no parent (as if they were at the top of a sheet)
   - Elements outside the bundle are not considered as parent candidates for internal elements

2. **Internal shift calculation**: The shift policies described in this section (3.2) apply identically inside the bundle
   - Internal repeat expansion, element chaining, MAX rule, etc. all apply

3. **Bundle size determination**: Row count of the bundle range + expansion amount after applying internal shift policies
   - If multiple parallel repeats exist inside, the MAX is applied

4. **External participation**: Once the size is determined, the bundle participates in the shift chain as a single wide element
   - Bundle's column range = the bundle range's column range
   - Bundle's rendered end = bundle's final start + final size - 1

**Example:**

```
Template:
  Col A-D:  Row 1:    [repeat(depts)] (A1:D1)     <- 5 items (expansion +4)
  Col A-H:  Row 5-12: ${bundle(A5:H12)}     <- element bundle (8 rows)
              Row 5:  "Employee Performance" (title)
              Row 6:  Header row
              Row 7:  [repeat(employees)] (A7:H7)  <- 11 items (expansion +10)
              Row 12: SUM row

Without bundle:
  A-column elements are shifted by depts, but E-H column elements are not -> table misaligned

With bundle:
  1) Internal calculation: employees +10 rows -> bundle final size = 8 + 10 = 18 rows
  2) External: bundle(A-H, 18 rows) participates as a wide element
     Column A parent = depts (rendered end 5) -> bundle start = 5 + (5-1) = 9
     Column E parent = none -> 5
     MAX -> bundle final start = Row 9, rendered end = Row 26
  3) Internal elements move together with the bundle -> entire table shifts as one unit
```

**Constraints:**

- **No boundary overlap**: An error occurs if an element partially overlaps the bundle range
- **No bundle nesting**: An error occurs if a bundle contains another bundle

### 4. Number Format Principles

#### 4.1 Automatic Number Format Conditions
When a value auto-generated by the library is numeric and the cell's display format is unset or "General", a number format is automatically applied.

- Integer: `pivotIntegerFormatIndex` (default 3, `#,##0`)
- Decimal: `pivotDecimalFormatIndex` (default 4, `#,##0.00`)

#### 4.2 Number Formatting for Formula Cells
When a variable marker (`${var}`) is bound to a value starting with `=`, the cell is converted to a formula. The same number formatting rules apply to these formula cells.

- Since the formula result type cannot be known in advance, the integer format (`#,##0`) is applied by default
- Alignment is kept as GENERAL so that Excel automatically determines it based on the formula result type
- Formula cells that already have a specific format set in the template are not modified
- For formulas that require decimal places (ratios, averages, etc.), set the desired format directly on the template cell

> **Implementation**: Handled by the `CellType.FORMULA` branch in `NumberFormatProcessor`

#### 4.3 Automatic Alignment Conditions
When a value auto-generated by the library is numeric and the cell's alignment is "General", right alignment is automatically applied. Alignment is not applied to formula cells.

#### 4.4 Existing Format Preservation
Even when conditions 4.1 through 4.3 apply, all other formatting (font, color, borders, etc.) retains the template formatting.

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

| Option                      | Default               | Description                                |
|-----------------------------|-----------------------|--------------------------------------------|
| `streamingMode`             | `ENABLED`             | **deprecated** (value is ignored)          |
| `fileNamingMode`            | `TIMESTAMP`           | TIMESTAMP / NONE                           |
| `timestampFormat`           | `"yyyyMMdd_HHmmss"`  | DateTimeFormatter pattern                  |
| `fileConflictPolicy`        | `SEQUENCE`            | SEQUENCE / ERROR                           |
| `progressReportInterval`    | `100`                 | Progress report interval (rows)            |
| `preserveTemplateLayout`    | `true`                | Preserve template layout                   |
| `pivotIntegerFormatIndex`   | `3`                   | Integer format index (`#,##0`)             |
| `pivotDecimalFormatIndex`   | `4`                   | Decimal format index (`#,##0.00`)          |
| `missingDataBehavior`       | `WARN`                | WARN / THROW                               |
| `unmarkedHidePolicy`        | `WARN_AND_HIDE`       | Policy for fields in hideFields without a hideable marker |

### Preset Configurations

```kotlin
// Optimized for large data processing
TbegConfig.forLargeData()
// progressReportInterval = 500

// For small data (deprecated -- identical to default())
TbegConfig.forSmallData()
```

### Spring Boot Configuration (application.yml)

```yaml
tbeg:
  file-naming-mode: timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence
  progress-report-interval: 100
  preserve-template-layout: true
  missing-data-behavior: warn
```

---

## Limitations

### Supported Features

| Item                                 | Supported | Notes                                         |
|--------------------------------------|-----------|-----------------------------------------------|
| Formulas referencing rows below      | Yes       | Auto-expanded when referencing repeat regions  |
| Formulas referencing rows above      | Yes       | Automatically adjusted                         |
| Auto-expanding formulas (SUM, etc.)  | Yes       | Range auto-expansion                           |
| Charts                               | Yes       | Handled via Extract/Restore processors         |
| Pivot tables                         | Yes       | Handled via Extract/Recreate processors        |

### General Limitations

- Repeat regions must not overlap in 2D space
- Multiple repeats within the same column range can be stacked vertically
- When placing multiple repeats on the same row, their column ranges must not overlap
- Sequence numbers are attempted up to a maximum of 10,000

### Internal Constants

| Constant          | Value       | Location                          |
|-------------------|-------------|-----------------------------------|
| SXSSF buffer size | 100 rows    | `StreamingRenderingStrategy.kt`   |
| Image margin      | 1px         | `ImageInserter.kt`                |
| EMU conversion    | 9525 EMU/px | `ImageInserter.kt`                |

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

- `clearCalcChain()` is called
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
│   ├── EmptyCollectionTest.kt          # Empty collection handling tests
│   ├── engine/
│   │   ├── TemplateRenderingEngineTest.kt  # Rendering engine tests
│   │   ├── DuplicateRepeatDetectionTest.kt # Duplicate marker detection tests
│   │   ├── HideableIntegrationTest.kt     # Hideable integration tests
│   │   ├── PositionCalculatorTest.kt
│   │   ├── ForwardReferenceTest.kt
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

| Data Size     | Time      |
|---------------|-----------|
| 1,000 rows    | 147ms     |
| 10,000 rows   | 663ms     |
| 30,000 rows   | 1,057ms   |
| 50,000 rows   | 1,202ms   |
| 100,000 rows  | 3,154ms   |

### Comparison with Other Libraries (30,000 rows)

| Library    | Time        | Notes                                                           |
|------------|-------------|-----------------------------------------------------------------|
| **TBEG**   | **1.1 sec** |                                                                 |
| JXLS       | 5.2 sec     | [Benchmark source](https://github.com/jxlsteam/jxls/discussions/203) |
