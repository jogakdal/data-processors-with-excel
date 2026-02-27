> **[한국어](../ko/developer-guide.md)** | English

# TBEG Developer Guide

This document describes the internal architecture and extension points of the TBEG library.

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Pipeline Pattern](#2-pipeline-pattern)
3. [Rendering Strategy](#3-rendering-strategy)
4. [Marker Parser](#4-marker-parser)
5. [Position Calculation](#5-position-calculation)
6. [Streaming Data Processing](#6-streaming-data-processing)
7. [Testing and Samples](#7-testing-guide)

---

## 1. Architecture Overview

### 1.1 Module Structure

```
io.github.jogakdal.tbeg/
├── ExcelGenerator.kt                   # Main entry point
├── TbegConfig.kt                       # Configuration class
├── ExcelDataProvider.kt                # Data provider interface
├── SimpleDataProvider.kt               # Default data provider implementation
├── DocumentMetadata.kt                 # Document metadata
├── Enums.kt                            # Enum definitions
├── async/                              # Asynchronous processing
│   ├── GenerationJob.kt
│   ├── GenerationResult.kt
│   ├── ExcelGenerationListener.kt
│   └── ProgressInfo.kt
├── engine/
│   ├── core/                           # Core utilities
│   │   ├── CommonTypes.kt              # Common types (CellCoord, CellArea, IndexRange, CollectionSizes, etc.)
│   │   ├── ExcelUtils.kt
│   │   ├── ChartProcessor.kt
│   │   ├── PivotTableProcessor.kt
│   │   └── XmlVariableProcessor.kt
│   ├── pipeline/                       # Pipeline pattern
│   │   ├── TbegPipeline.kt
│   │   ├── ExcelProcessor.kt
│   │   ├── ProcessingContext.kt
│   │   └── processors/                 # Individual processors
│   └── rendering/                      # Rendering engine
│       ├── RenderingStrategy.kt
│       ├── AbstractRenderingStrategy.kt
│       ├── XssfRenderingStrategy.kt
│       ├── SxssfRenderingStrategy.kt
│       ├── TemplateRenderingEngine.kt
│       ├── TemplateAnalyzer.kt
│       ├── PositionCalculator.kt
│       ├── StreamingDataSource.kt
│       ├── WorkbookSpec.kt
│       └── parser/                     # Marker parser
│           ├── MarkerDefinition.kt     # Marker definitions
│           ├── UnifiedMarkerParser.kt  # Unified parser
│           ├── ParameterParser.kt      # Parameter parser
│           └── ParsedMarker.kt         # Parsing result
├── exception/                          # Exception classes
└── spring/                             # Spring Boot auto-configuration
```

### 1.2 Processing Flow

```
Template + Data
       │
       ▼
───────────────────────────────────────────────────────────────
                       TbegPipeline
───────────────────────────────────────────────────────────────
   1. ChartExtractProcessor       - Extract charts (prevent SXSSF loss)
   2. PivotExtractProcessor       - Extract pivot table info
   3. TemplateRenderProcessor     - Render templates (data binding)
   4. NumberFormatProcessor       - Apply number formatting
   5. XmlVariableReplaceProcessor - Replace variables in XML
   6. PivotRecreateProcessor      - Recreate pivot tables
   7. ChartRestoreProcessor       - Restore charts
   8. MetadataProcessor           - Apply document metadata
───────────────────────────────────────────────────────────────
       │
       ▼
  Generated Excel
```

### 1.3 Design Principles

TBEG's core philosophy is **Excel-native features first**.

- Features that Excel already does well (aggregation, conditional formatting, charts, etc.) are not reimplemented
- TBEG provides **dynamic data binding** (variable substitution, repeat expansion, image insertion) — things Excel cannot do on its own
- After data expansion, TBEG **preserves and adjusts** Excel-native features so they work as intended (formula range expansion, conditional formatting duplication, chart data range adjustment)

This principle serves as the foundation for all implementation decisions including the pipeline, rendering strategies, and position calculations.

---

## 2. Pipeline Pattern

### 2.1 TbegPipeline

The pipeline executes multiple processors sequentially.

```kotlin
class TbegPipeline(vararg processors: ExcelProcessor) {
    fun execute(context: ProcessingContext): ProcessingContext
}
```

### 2.2 ExcelProcessor

Each processor is responsible for a specific processing stage.

```kotlin
internal interface ExcelProcessor {
    val name: String
    fun shouldProcess(context: ProcessingContext): Boolean = true
    fun process(context: ProcessingContext): ProcessingContext
}
```

### 2.3 ProcessingContext

A context object that carries data between processors.

```kotlin
internal class ProcessingContext(
    val templateBytes: ByteArray,
    val dataProvider: ExcelDataProvider,
    val config: TbegConfig,
    val metadata: DocumentMetadata?
) {
    var resultBytes: ByteArray = templateBytes
    var processedRowCount: Int = 0
    // Shared data between processors
    var chartInfo: ChartProcessor.ChartInfo? = null
    var pivotTableInfos: List<PivotTableProcessor.PivotTableInfo> = emptyList()
    var variableResolver: ((String) -> String)? = null
    var requiredNames: RequiredNames? = null
}
```

### 2.4 Processor Implementation Example

```kotlin
class TemplateRenderProcessor : ExcelProcessor {
    override val name = "TemplateRender"

    override fun process(context: ProcessingContext): ProcessingContext {
        val analyzer = TemplateAnalyzer()
        val blueprint = XSSFWorkbook(ByteArrayInputStream(context.resultBytes)).use {
            analyzer.analyzeFromWorkbook(it)
        }
        context.requiredNames = blueprint.extractRequiredNames()

        val engine = TemplateRenderingEngine(context.config.streamingMode)
        context.resultBytes = engine.process(
            ByteArrayInputStream(context.resultBytes),
            context.dataProvider,
            context.requiredNames!!
        )
        return context
    }
}
```

---

## 3. Rendering Strategy

### 3.1 Strategy Pattern

Different strategies are used depending on the rendering mode (XSSF/SXSSF).

```kotlin
internal interface RenderingStrategy {
    val name: String

    fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray
}

internal class XssfRenderingStrategy : RenderingStrategy
internal class SxssfRenderingStrategy : RenderingStrategy
```

### 3.2 XSSF vs SXSSF

| Characteristic | XSSF | SXSSF |
|----------------|------|-------|
| Memory | Loads entire workbook into memory | Window-based streaming |
| Row insertion | Supports shiftRows() | Sequential output only |
| Formula references | Auto-adjusted | Auto-adjusted |
| Large datasets | Limited | Suitable |

### 3.3 AbstractRenderingStrategy

Common logic is extracted into an abstract class.

```kotlin
internal abstract class AbstractRenderingStrategy : RenderingStrategy {
    // Abstract methods — must be implemented by subclasses
    protected abstract fun <T> withWorkbook(
        templateBytes: ByteArray,
        block: (workbook: Workbook, xssfWorkbook: XSSFWorkbook) -> T
    ): T
    protected abstract fun processSheet(
        sheet: Sheet, sheetIndex: Int, blueprint: SheetSpec,
        data: Map<String, Any>, imageLocations: MutableList<ImageLocation>,
        context: RenderingContext
    )
    protected abstract fun finalizeWorkbook(workbook: Workbook): ByteArray

    // Hook methods — optional override
    protected open fun beforeProcessSheets(workbook: Workbook, blueprint: WorkbookSpec, ...)
    protected open fun afterProcessSheets(workbook: Workbook, context: RenderingContext)

    // Common utilities
    protected fun processCellContent(cell: Cell, content: CellContent, ...)
    protected fun setCellValue(cell: Cell, value: Any?)
    protected fun insertImages(workbook: Workbook, imageLocations: List<ImageLocation>, ...)
}
```

---

## 4. Marker Parser

### 4.1 Overview

A dedicated parser for template markers (`${...}`). Markers are easily added and managed through declarative definitions.

```
${repeat(employees, A3:C3, emp, DOWN)}
         │          │       │    │
         │          │       │    └─ direction (optional)
         │          │       └─ alias (optional)
         │          └─ range (required)
         └─ collection (required)
```

### 4.2 Components

| Class | Role |
|-------|------|
| `MarkerDefinition` | Defines marker names and parameters |
| `UnifiedMarkerParser` | Parses marker strings |
| `ParameterParser` | Parses and converts parameter values |
| `ParsedMarker` | Stores and provides access to parsing results |

### 4.3 Usage Example

```kotlin
// Parse a marker — return type is CellContent (sealed interface)
val content = UnifiedMarkerParser.parse("\${repeat(employees, A3:C3, emp, DOWN)}")

// Branch based on CellContent subtype
when (content) {
    is CellContent.RepeatMarker -> {
        content.collection   // "employees"
        content.area         // CellArea
        content.variable     // "emp"
        content.direction    // RepeatDirection.DOWN
    }
    is CellContent.ImageMarker -> { content.name; content.position }
    is CellContent.Variable -> content.name
    is CellContent.ItemField -> content.fieldPath
    is CellContent.Formula -> content.formula
    // ... StaticString, StaticNumber, StaticBoolean, Empty, etc.
    else -> {}
}
```

### 4.4 Supported Markers

| Marker | Purpose | Required Parameters | Optional Parameters |
|--------|---------|---------------------|---------------------|
| `repeat` | Expand repeated data | collection, range | var, direction(=DOWN), empty |
| `image` | Insert image | name | position, size(=fit) |
| `size` | Output collection size | collection | |

---

## 5. Position Calculation

### 5.0 Common Types (CommonTypes)

#### CollectionSizes

A value class representing the mapping of collection names to item counts. Used for position calculation and formula expansion.

```kotlin
// Factory method
val sizes = CollectionSizes.of("employees" to 10, "departments" to 3)

// Builder function
val sizes = buildCollectionSizes {
    put("employees", 10)
    put("departments", 3)
}

// Empty instance
val empty = CollectionSizes.EMPTY

// Lookup
val count: Int? = sizes["employees"]  // 10
```

#### CellArea

A data class representing a cell area (start/end coordinates). Used in `RepeatRegionSpec`, `ColumnGroup`, and other components to hold area information.

```kotlin
// Create from CellCoord
val area = CellArea(CellCoord(2, 0), CellCoord(5, 3))

// Create with 4 coordinates directly
val area = CellArea(startRow = 2, startCol = 0, endRow = 5, endCol = 3)

// Property access
area.start.row   // 2
area.end.col     // 3
area.rowRange    // RowRange(2, 5)
area.colRange    // ColRange(0, 3)

// Area overlap detection
area.overlapsColumns(other)  // Whether column ranges overlap
area.overlapsRows(other)     // Whether row ranges overlap
area.overlaps(other)         // Whether 2D areas overlap
```

### 5.1 Duplicate Marker Detection

`TemplateAnalyzer.analyzeWorkbook()` analyzes the template in 4 stages:

1. **Collection**: Collect repeat markers from all sheets (`collectRepeatRegions`)
2. **Repeat deduplication**: If multiple repeats share the same collection + same target range, warn and keep only the last one (`deduplicateRepeatRegions`)
3. **SheetSpec creation**: Analyze each sheet based on the deduplicated repeat list (`analyzeSheet`)
4. **Cell marker deduplication**: Post-process to remove duplicates of range markers remaining in cells, such as image markers (`deduplicateCellMarkers`)

Target sheet determination: If the range has a sheet prefix (`'Sheet1'!A1:B2`), that sheet is used; otherwise, the sheet where the marker is located is the target.

When adding a new range marker that requires duplicate detection, add it to the `when` branch of the `cellMarkerDedupKey()` method.

### 5.2 PositionCalculator

Calculates the positions of multiple repeat regions.

```kotlin
class PositionCalculator(
    repeatRegions: List<RepeatRegionSpec>,
    collectionSizes: CollectionSizes,
    templateLastRow: Int = -1
) {
    // Calculate all repeat expansion information
    fun calculate(): List<RepeatExpansion>

    // Convert template position to final position
    fun getFinalPosition(templateRow: Int, templateCol: Int): CellCoord
    fun getFinalPosition(template: CellCoord): CellCoord

    // Calculate final position of a range
    fun getFinalRange(start: CellCoord, end: CellCoord): CellRangeAddress
    fun getFinalRange(range: CellRangeAddress): CellRangeAddress

    // Retrieve row information for an actual output row
    fun getRowInfo(actualRow: Int): RowInfo
}
```

### 5.3 RepeatExpansion

```kotlin
data class RepeatExpansion(
    val region: RepeatRegionSpec,    // Original repeat region
    val finalStartRow: Int,          // Expansion start row
    val finalStartCol: Int,          // Expansion start column
    val rowExpansion: Int,           // Row expansion amount
    val colExpansion: Int,           // Column expansion amount
    val itemCount: Int               // Number of items
)
```

### 5.4 Position Calculation Rules

1. **Independent elements**: If not affected by any repeat, the template position is preserved.
2. **Single influence**: If affected by only one repeat, the position shifts by that expansion amount.
3. **Multiple influences**: If affected by multiple repeats, the maximum offset is applied.

### 5.5 Column Groups

Repeats sharing the same column range affect each other, while repeats in different column groups expand independently.

```kotlin
data class ColumnGroup(
    val groupId: Int,
    val colRange: ColRange,
    val repeatRegions: List<RepeatRegionSpec>
)
```

---

## 6. Streaming Data Processing

### 6.1 StreamingDataSource

Consumes iterators sequentially in SXSSF mode.

```kotlin
internal class StreamingDataSource(
    private val dataProvider: ExcelDataProvider,
    private val expectedSizes: CollectionSizes = CollectionSizes.EMPTY
) : Closeable {
    // Current item for each repeat region
    fun advanceToNextItem(repeatKey: RepeatKey): Any?
    fun getCurrentItem(repeatKey: RepeatKey): Any?
}
```

### 6.2 Memory Optimization Principles

| Mode | Memory Policy |
|------|--------------|
| SXSSF | Only the current item is kept in memory |
| XSSF | Entire dataset can be loaded into memory |

### 6.3 DataProvider Requirements

- `getItems()` must be able to provide the same data again.
- If the same collection is used in multiple repeats, `getItems()` will be called again.

---

## 7. Testing Guide

Test code is located in `src/test/kotlin/io/github/jogakdal/tbeg/`.
Test templates are located in `src/test/resources/templates/`.

### 7.1 Test Example

```kotlin
class PositionCalculatorTest {
    @Test
    fun `single repeat expansion calculation`() {
        val regions = listOf(
            RepeatRegionSpec(
                collection = "items",
                variable = "item",
                area = CellArea(2, 0, 2, 2),
                direction = RepeatDirection.DOWN
            )
        )

        val calculator = PositionCalculator(
            repeatRegions = regions,
            collectionSizes = CollectionSizes.of("items" to 5)
        )
        val expansions = calculator.calculate()

        assertEquals(1, expansions.size)
        assertEquals(4, expansions[0].rowExpansion)  // (5-1) * 1 row
    }
}
```

### 7.2 Integration Test Example

```kotlin
class ExcelGeneratorIntegrationTest {
    @Test
    fun `repeated data processing`() {
        val data = mapOf(
            "items" to listOf(
                mapOf("name" to "A", "value" to 100),
                mapOf("name" to "B", "value" to 200)
            )
        )

        ExcelGenerator().use { generator ->
            val template = javaClass.getResourceAsStream("/templates/repeat.xlsx")!!
            val bytes = generator.generate(template, data)

            XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                assertEquals("A", sheet.getRow(0).getCell(0).stringCellValue)
                assertEquals("B", sheet.getRow(1).getCell(0).stringCellValue)
            }
        }
    }
}
```

### 7.3 Running Tests

```bash
# All tests
./gradlew :tbeg:test

# Specific test
./gradlew :tbeg:test --tests "*PositionCalculator*"
```

### 7.4 Samples and Benchmarks

Runnable samples and benchmark code are managed in separate directories apart from test code.

```
src/test/
├── kotlin/io/github/jogakdal/tbeg/
│   ├── samples/                            # Sample code (Kotlin)
│   │   ├── TbegSample.kt
│   │   ├── EmptyCollectionSample.kt
│   │   ├── TbegSpringBootSample.kt
│   │   └── TemplateRenderingEngineSample.kt
│   ├── benchmark/                          # Benchmark code
│   │   ├── PerformanceBenchmark.kt         # Large-scale benchmark
│   │   └── PerformanceBenchmarkTest.kt     # XSSF vs SXSSF comparison
│   └── ...                                 # Test code
├── java/io/github/jogakdal/tbeg/samples/     # Sample code (Java)
│   ├── TbegJavaSample.java
│   └── TbegSpringBootJavaSample.java
```

```bash
# Run Kotlin samples (output: build/samples/)
./gradlew :tbeg:runSample

# Run Java samples (output: build/samples-java/)
./gradlew :tbeg:runJavaSample

# Run Spring Boot samples (output: build/samples-spring/)
./gradlew :tbeg:runSpringBootSample

# Run performance benchmark
./gradlew :tbeg:runBenchmark
```

---

## Extension Points

### Custom DataProvider

Implement `ExcelDataProvider` directly when a special data source is needed.

```kotlin
class DatabaseDataProvider(
    private val dataSource: DataSource
) : ExcelDataProvider {
    override fun getValue(name: String): Any? = /* SQL query */
    override fun getItems(name: String): Iterator<Any>? = /* Streaming query */
    override fun getItemCount(name: String): Int? = /* COUNT query */
}
```

### Custom Processor

New processing stages can be added to the pipeline.

```kotlin
class WatermarkProcessor : ExcelProcessor {
    override val name = "Watermark"

    override fun process(context: ProcessingContext): ProcessingContext {
        // Watermark insertion logic
        return context
    }
}
```

---

## Next Steps

- [API Reference](./reference/api-reference.md) - API details
- [Configuration Options](./reference/configuration.md) - Configuration options
