# Changelog

All notable changes to this project will be documented in this file.

This file is maintained in this project only and is not affected by upstream sync.

## [1.2.1] - 2026-03-10

### New Features
- **Formula value substitution**: Values starting with `=` are now treated as Excel formulas
- **Pivot table auto-reflection**: Pivot table source ranges are automatically adjusted when data expands (no manual refresh required when opening the file)
- **Document metadata**: Set document properties such as title, author, keywords, etc.

### Internal Improvements
- `escapeXml()` function internalized (moved from common-core to TBEG internal)
- Number format preservation for formula cells during streaming rendering

### Documentation
- Template syntax reference restructured with formula binding section
- Developer guide rewritten
- English translation incremental update (13 files)

## [1.2.0] - 2026-03-06

### Breaking Changes
- **XSSF (non-streaming) mode removed**: TBEG now always operates in streaming mode
  - `StreamingMode` enum: deprecated (will be removed in a future version)
  - `TbegConfig.streamingMode`: deprecated (value is ignored)
  - `TbegConfig.forSmallData()`: deprecated (behaves the same as `default()`)
  - Internal class `XssfRenderingStrategy` removed
  - `SxssfRenderingStrategy` renamed to `StreamingRenderingStrategy`

### New Features
- **Image URL support**: Specify an HTTP(S) URL instead of `ByteArray` for image data; the image is automatically downloaded at render time. Use `imageUrl("logo", "https://...")` syntax
  - `imageUrlCacheTtlSeconds` setting for inter-call cache TTL (default: 0, no caching)
- **Automatic cell merge**: Automatically merge consecutive cells with the same value during repeat expansion. Use `${merge(item.field)}` or `=TBEG_MERGE(item.field)` markers
- **Bundle**: Group a range of elements into a single unit that moves together during repeat expansion. Use `${bundle(range)}` or `=TBEG_BUNDLE(range)` markers

### Bug Fixes
- RIGHT repeat column width duplication fix: column widths now correctly duplicated when multiple RIGHT repeats share the same column range
- RIGHT repeat non-repeat cell filtering fix: columns from other RIGHT repeat regions correctly excluded from non-repeat cell writing
- RIGHT repeat indirect overlap check fix: column overlap check between RIGHT repeats with overlapping row ranges was missing

### Internal Improvements
- Chaining-based position calculation in PositionCalculator
- MergeTracker for automatic cell merge handling
- TemplateAnalyzer 5-phase analysis with bundle collection/validation (Phase 2.5)
- Dead zone detection and skip logic
- Forward verification for getFinalPosition()
- Row height MAX application for chaining

## [1.1.3] - 2026-02-27

### New Features
- Automatic chart data range adjustment: chart data ranges are automatically updated when data is expanded via repeat
- Improved image alignment within cells during image insertion

### Bug Fixes
- Chart restoration fix: charts now correctly restored after data expansion in templates containing charts
- Formula range adjustment fix: formula cell ranges correctly expanded under all conditions
- Image insertion position fix: images placed precisely at the designated cell position
- Chart range adjustment fix (RIGHT direction): single-cell range expansion now validates row ranges, preventing unrelated repeat regions from being expanded
- Chart range adjustment fix (multiple RIGHT repeats): chart column ranges now cumulatively expanded when multiple RIGHT repeats exist on the same sheet

### Documentation
- README improvements: added POI comparison code, enhanced key features, suitability table, and template syntax table
- Manual enhancements: added comprehensive example (Rich Sample) and screenshot-based introduction section
- Library comparison document: added feature/performance comparison with JXLS, EasyExcel, and others
- Manual index pages reduced to documentation hub (navigation + learning paths only), removing content duplicated with README

## [1.1.2] - 2026-02-23

### Bug Fixes
- Non-repeat area formula handling improvement in XSSF mode
- Non-repeat area static row flag fix in SXSSF mode
- Cross-sheet duplicate marker grouping fix: group by target sheet instead of marker's sheet

### Tests
- Added `NonRepeatFormulaCellTest`

## [1.1.1] - 2026-02-12

### Bug Fixes
- Fixed multi independent repeat regions on the same row: some regions were missing or non-repeat cells were rendered duplicately
- Fixed `UnifiedMarkerParser` to recognize multiple repeat variables on the same row

### New Features
- **Duplicate marker detection**: Warns when repeat markers share the same collection + target range, or image markers share the same name + position + size

### Internal Improvements
- Row spec structure overhaul: simplified `RowSpec` to single data class, separated repeat info into `RepeatRegionSpec`
- Introduced common types: `IndexRange`/`RowRange`/`ColRange`, `CollectionSizes`, `CellCoord`, `CellArea`
- `TemplateAnalyzer` reorganized into 4-phase analysis pipeline
- Added parameterized tests for multi repeat regions and duplicate marker detection

### Documentation
- Added CHANGELOG (ko/en)
- Added 3 new manuals: best-practices, troubleshooting, migration-guide (ko/en)
- Major updates to existing manuals: index, user-guide, developer-guide, api-reference, etc.

## [1.1.0] - 2026-02-10

**First public release on Maven Central**

### Features (from upstream)
- Template-based Excel report generation with `${repeat(...)}` syntax
- Streaming mode (SXSSF) for large dataset processing
- Chart, formula, and conditional formatting auto-adjustment
- Empty collection handling with `emptyRange`
- Spring Boot auto-configuration support

### Refactoring (from upstream)
- `ExcelGeneratorConfig` renamed to `TbegConfig`, `ExcelPipeline` renamed to `TbegPipeline`
- `ConditionalFormattingUtils` extracted to eliminate dxfId reflection duplication
- `CellSnapshot.toContent()` extension function added
- `AbstractRenderingStrategy` numberStyleCache common method (SXSSF/XSSF dedup)
- Unused code cleanup (`has`, `finalEndRow`, `isSingleCell`, `templateRowCount`, etc.)

### Project Setup
- Extracted TBEG module from kotlin-common-library as a standalone project
- Package: `io.github.jogakdal.tbeg`
- Build: Gradle 8.13.0, Kotlin 2.1.20, Java 21
- CI/CD: GitHub Actions (build + test + Maven Central publish on v* tag)
- Documentation: Multilingual support (Korean source, English translation)

### Deployment
- **Maven Central**: `io.github.jogakdal:tbeg:1.1.0`
- Published via vanniktech/gradle-maven-publish-plugin v0.30.0
- GPG signed, Sonatype Central Portal
