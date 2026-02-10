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
interface ExcelProcessor {
    fun process(context: ProcessingContext): ProcessingContext
}
```

### 2.3 ProcessingContext

A context object that carries data between processors.

```kotlin
data class ProcessingContext(
    val templateBytes: ByteArray,
    val dataProvider: ExcelDataProvider,
    val config: TbegConfig,
    val metadata: DocumentMetadata? = null,
    var resultBytes: ByteArray = ByteArray(0),
    var processedRowCount: Int = 0,
    // Shared data between processors
    var chartInfoList: List<ChartInfo> = emptyList(),
    var pivotInfoList: List<PivotInfo> = emptyList(),
    var workbookSpec: WorkbookSpec? = null
)
```

### 2.4 Processor Implementation Example

```kotlin
class TemplateRenderProcessor : ExcelProcessor {
    override fun process(context: ProcessingContext): ProcessingContext {
        val engine = TemplateRenderingEngine(context.config.streamingMode)

        val resultBytes = engine.process(
            ByteArrayInputStream(context.templateBytes),
            context.dataProvider,
            context.workbookSpec?.extractRequiredNames()
        )

        return context.copy(resultBytes = resultBytes)
    }
}
```

---

## 3. Rendering Strategy

### 3.1 Strategy Pattern

Different strategies are used depending on the rendering mode (XSSF/SXSSF).

```kotlin
sealed interface RenderingStrategy {
    fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray
}

class XssfRenderingStrategy : RenderingStrategy
class SxssfRenderingStrategy : RenderingStrategy
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
abstract class AbstractRenderingStrategy : RenderingStrategy {
    protected fun evaluateCellContent(
        content: CellContent,
        data: Map<String, Any>,
        context: RenderingContext
    ): Any?

    protected fun applyCellStyle(cell: Cell, styleIndex: Short, workbook: Workbook)
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
// Parse a marker
val marker = UnifiedMarkerParser.parse("repeat(employees, A3:C3, emp, DOWN)")

// Access parameters
val collection = marker["collection"]     // "employees"
val range = marker.getRange("range")      // CellRangeAddress
val direction = marker.getDirection("direction")  // RepeatDirection.DOWN

// Check optional parameters
if (marker.has("alias")) {
    val alias = marker["alias"]
}
```

### 4.4 Supported Markers

| Marker | Purpose | Required Parameters |
|--------|---------|---------------------|
| `repeat` | Expand repeated data | collection, range |
| `image` | Insert image | name |
| `formulaRange` | Define formula range | range |
| `emptyRange` | Handle empty ranges | range, direction |
| `akzj` | Handle empty ranges (alias) | range, direction |

---

## 5. Position Calculation

### 5.1 PositionCalculator

Calculates the positions of multiple repeat regions.

```kotlin
class PositionCalculator(
    repeatRegions: List<RepeatRegionSpec>,
    collectionSizes: Map<String, Int>,
    templateLastRow: Int = -1
) {
    // Calculate all repeat expansion information
    fun calculate(): List<RepeatExpansion>

    // Convert template position to final position
    fun getFinalPosition(templateRow: Int, templateCol: Int): Pair<Int, Int>

    // Calculate final position of a range
    fun getFinalRange(
        templateFirstRow: Int, templateLastRow: Int,
        templateFirstCol: Int, templateLastCol: Int
    ): CellRangeAddress

    // Retrieve row information for an actual output row
    fun getRowInfo(actualRow: Int): RowInfo
}
```

### 5.2 RepeatExpansion

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

### 5.3 Position Calculation Rules

1. **Independent elements**: If not affected by any repeat, the template position is preserved.
2. **Single influence**: If affected by only one repeat, the position shifts by that expansion amount.
3. **Multiple influences**: If affected by multiple repeats, the maximum offset is applied.

### 5.4 Column Groups

Repeats sharing the same column range affect each other, while repeats in different column groups expand independently.

```kotlin
data class ColumnGroup(
    val groupId: Int,
    val startCol: Int,
    val endCol: Int,
    val repeatRegions: List<RepeatRegionSpec>
)
```

---

## 6. Streaming Data Processing

### 6.1 StreamingDataSource

Consumes iterators sequentially in SXSSF mode.

```kotlin
class StreamingDataSource(
    private val dataProvider: ExcelDataProvider,
    private val expectedSizes: Map<String, Int>
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
            RepeatRegionSpec("items", "item", 2, 2, 0, 2, RepeatDirection.DOWN)
        )
        val sizes = mapOf("items" to 5)

        val calculator = PositionCalculator(regions, sizes)
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
