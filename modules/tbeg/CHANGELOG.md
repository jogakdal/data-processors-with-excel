> **English** | [한국어](./CHANGELOG.ko.md)

# TBEG Changelog

## 1.2.0

### Breaking Changes

- **XSSF (non-streaming) mode removed**: Now always operates in streaming mode
  - `StreamingMode` enum: deprecated (to be removed in a future version)
  - `TbegConfig.streamingMode`: deprecated (value is ignored)
  - `TbegConfig.forSmallData()`: deprecated (behaves identically to `default()`)
  - `TbegConfig.withStreamingMode()`: deprecated
  - `TbegConfig.Builder.streamingMode()`: deprecated
  - Spring configuration `streaming-mode`: deprecated (value is ignored)
  - Internal class `XssfRenderingStrategy` removed
  - `SxssfRenderingStrategy` renamed to `StreamingRenderingStrategy`

### New Features

- **Image URL support**: Specify an HTTP(S) URL string instead of `ByteArray` as image data, and it will be automatically downloaded at rendering time. Use with `imageUrl("logo", "https://...")` form
  - Configure inter-call cache TTL with the `imageUrlCacheTtlSeconds` setting (default: 0, no caching)
  - On download failure, a warning log is emitted and the image is skipped
- **Automatic cell merge**: Automatically merges consecutive cells with the same value during repeat expansion. Use with `${merge(item.field)}` or `=TBEG_MERGE(item.field)` marker
- **Bundle**: Groups elements within a specified range into a single unit that moves together during repeat expansion. Use with `${bundle(range)}` or `=TBEG_BUNDLE(range)` marker

### Bug Fixes

- **RIGHT repeat column width duplication fix**: Fixed an issue where column widths were not correctly duplicated when multiple RIGHT repeats shared the same column range
- **RIGHT repeat non-repeat cell filtering fix**: Fixed to correctly exclude columns from other RIGHT repeat regions during non-repeat cell writing
- **RIGHT repeat indirect overlap check fix**: Fixed a missing column overlap check between RIGHT repeats with overlapping row ranges

<details>
<summary>Internal Improvements</summary>

- **Chaining-based position calculation**: PositionCalculator switched from ColumnGroup-based MAX approach to a chaining algorithm. Cross-column displacement propagation through merged cells and bundles is now accurate
- **MergeTracker**: Added MergeTracker class to handle automatic cell merging
- **TemplateAnalyzer 5-phase analysis**: Added bundle collection and validation phase (Phase 2.5)
- **Dead zone handling**: Added logic to detect and correctly skip empty regions caused by displacement
- **Forward verification**: Added verification logic to prevent reverse calculation ambiguity in `getFinalPosition()`
- **Row height MAX application**: Applies maximum height when multiple template rows map to the same actual row through chaining
- **`ensureCalculated()` pattern extraction**: Consolidated repeated lazy calculation calls in PositionCalculator's public methods into a single method
- **RIGHT repeat column width application improvement**: Prevents duplicate application by grouping repeats with the same colRange via `groupBy` in `applyColumnWidths()`
- Added unit/integration tests: `CellMergeTest`, `PositionCalculatorTest` chaining/bundle tests

</details>

## 1.1.3

### New Features

- **Automatic chart data range adjustment**: When data is expanded via repeat, chart data ranges are automatically updated
- **Improved image alignment**: Image alignment within cells has been improved during image insertion

### Bug Fixes

- **Chart restoration fix**: Fixed an issue where charts were not correctly restored after data expansion in templates containing charts
- **Formula range adjustment fix**: Fixed an issue where formula cell ranges were not correctly expanded under certain conditions
- **Image insertion position fix**: Fixed an issue where images were not placed precisely at the designated cell position
- **Chart range adjustment fix (RIGHT direction)**: Fixed an issue in RIGHT repeat where single-cell range expansion did not validate row ranges, causing unrelated repeat regions to be expanded
- **Chart range adjustment fix (multiple RIGHT repeats)**: Fixed an issue where chart column ranges were not cumulatively expanded when multiple RIGHT repeats existed on the same sheet

### Documentation

- **README improvements**: Added POI comparison code, enhanced key features, suitability table, and template syntax table
- **Manual enhancements**: Added comprehensive example (Rich Sample) and screenshot-based introduction section
- **Library comparison document**: Added feature/performance comparison with JXLS, EasyExcel, and others

<details>
<summary>Internal Improvements</summary>

- Added new `ChartRangeAdjuster`
- Refactored `ChartProcessor`
- Refactored `ImageInserter`
- Extended `FormulaAdjuster`
- Improved rendering strategies
- Added Rich Sample (quarterly sales performance report demo)
- Added unit/integration tests: `ChartRangeAdjusterTest`, `ChartRepeatIntegrationTest`, `DrawingXmlMergeTest`, `FormulaAdjusterTest`, `ImageInserterAlignmentTest`

</details>

## 1.1.2

### Bug Fixes

- **Non-repeat area formula handling improvement**: Fixed an issue where formula cells in non-repeat areas were not processed correctly
- **Non-repeat area static row flag fix**: Fixed an issue where the `isStaticRow` flag for non-repeat area cells was not set correctly
- **Cross-sheet duplicate marker grouping fix**: Fixed duplicate repeat marker detection to group by the target sheet rather than the sheet where the marker is located

## 1.1.1

### New Features

- **Duplicate marker detection**: Added duplicate detection for range-handling markers (repeat, image)
  - repeat: When the same collection + same target range (sheet + area) is duplicated, a warning log is emitted and only the last marker is retained
  - image: When the same name + same position (sheet + cell) + same size is duplicated, a warning log is emitted and only the last marker is retained
  - Cross-sheet duplicates are also detected (when referencing the same target via sheet prefix)

### Bug Fixes

- **Fix multiple independent repeat regions on the same row**: Fixed a bug where some regions were omitted or non-repeat cells were rendered duplicately when multiple repeat regions with non-overlapping column ranges existed on the same row
- **Multiple repeat variable recognition**: Improved `UnifiedMarkerParser` to recognize all repeat variables on the same row

<details>
<summary>Internal Improvements</summary>

- **Complete overhaul of row specification structure**: Simplified `RowSpec` from a sealed class to a single data class, and separated repeat information into `RepeatRegionSpec`
- **Common types introduction**: Introduced `IndexRange`/`RowRange`/`ColRange` range types and `CollectionSizes` value class
- **`CellCoord` type publicized and expanded**: Converted `CellCoord` from a private class inside `TemplateAnalyzer` to a public type
- **`CellArea` type introduction**: Introduced a cell area representation type and consolidated `RepeatRegionSpec` into a single `area: CellArea` property
- Reorganized `TemplateAnalyzer` into a 4-phase analysis structure (collect -> deduplicate repeats -> build SheetSpec -> deduplicate cell markers)
- Internal code refactoring and KDoc updates
- Added verification tests for multiple independent repeat regions on the same row
- Added duplicate marker detection tests (repeat: 7 cases + image: 6 cases)

</details>

## 1.1.0

> [!NOTE]
> If you are upgrading from 1.0.x, see the [Migration Guide](./manual/en/migration-guide.md).

### New Features

- **Empty collection handling**: Specify replacement content via the `empty` parameter when repeat region data is an empty collection
- **Multiple independent repeat regions on the same row**: Place multiple independent repeat regions on a single row
- **Cross-sheet formula reference expansion**: Formulas referencing repeat regions on other sheets are automatically expanded

### Breaking Changes

- **Configuration class renaming**: `ExcelGeneratorConfig` -> `TbegConfig`
  - The old name is preserved as a type alias, so existing code continues to work as-is

<details>
<summary>Internal Improvements</summary>

- **Unified marker parser introduction**: Unified parsing logic for REPEAT, IMAGE, SIZE markers with `UnifiedMarkerParser`
- Absolute reference (`$`) parsing support
- Conditional formatting processing logic improvements and utility extraction
- BOM module addition
- Kotlin/Java samples with 6 usage patterns (basic, lazy loading, async, large data, encryption, metadata)
- Performance benchmark added
- Empty collection handling tests and parameterized test adoption

</details>
