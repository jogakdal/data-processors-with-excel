# Changelog

All notable changes to this project will be documented in this file.

This file is maintained in this project only and is not affected by upstream sync.

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
