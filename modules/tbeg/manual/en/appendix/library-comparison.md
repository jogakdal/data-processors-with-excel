> **[한국어](../../ko/appendix/library-comparison.md)** | English

# Appendix: Comparison with Other Libraries

This document compares TBEG with similar template-based libraries -- JXLS, JETT, and ExcelReportGenerator (ERG) -- to help you choose the right tool for Excel report generation.

---

## 1. Libraries Compared

### JXLS

The most widely known template-based Excel generation library in the JVM ecosystem. It runs on Apache POI and defines report structure through **commands written in cell comments (Notes)**.

- **Platform**: JVM (Java 17)
- **Excel Engine**: Apache POI
- **License**: Apache License 2.0
- **GitHub**: [jxlsteam/jxls](https://github.com/jxlsteam/jxls)

### JETT (Java Excel Template Translator)

A JVM-based template-style Excel generation library. It runs on Apache POI and uses **XML tag syntax** (`<jt:forEach>`, `<jt:if>`, etc.) written in cells to define report structure. It provides JEXL (Java Expression Language)-based expressions and aggregation capabilities through the jAgg library.

- **Platform**: JVM (Java 7+)
- **Excel Engine**: Apache POI
- **License**: LGPL v3
- **GitHub**: [rgettman/jett](https://github.com/rgettman/jett)

> [!WARNING]
> JETT has not been updated since around 2018. Its compatibility with Apache POI 5.x and Java 21 environments has not been verified.

### ExcelReportGenerator (ERG)

A .NET-based Excel report generation library. It runs on ClosedXML and organizes reports through Named Ranges and a hierarchical "panel" structure.

- **Platform**: .NET Standard 2.0
- **Excel Engine**: ClosedXML (OpenXML wrapper)
- **License**: MIT
- **GitHub**: [traf72/excel-report-generator](https://github.com/traf72/excel-report-generator)

### TBEG (Template-Based Excel Generator)

A Kotlin/JVM-based Excel report generation library. Markers are written directly in Excel template cells, allowing you to use designer-created Excel forms as-is and simply bind data to them.

- **Platform**: JVM (Kotlin/Java)
- **Excel Engine**: Apache POI
- **Package**: `io.github.jogakdal:tbeg`

---

## 2. At a Glance

| Feature | JXLS | JETT | ERG | TBEG |
|---------|------|------|-----|------|
| **Template Design** | Commands in cell comments | XML tags in cells | Named Range + panel tree in code | Markers written directly in cells |
| **Image Insertion** | Supported | `<jt:image>` tag | Not supported | Position, size, aspect ratio, and more |
| **Charts** | Requires workaround via dynamic named ranges | Existing charts preserved only | Not supported | Auto-adjusted when data expands |
| **Pivot Tables** | Requires refresh when opening file | Not supported | Not supported | Auto-adjusted when data expands |
| **Formula Auto-Adjustment** | Supported | POI level | Basic level | All reference types supported; auto-adjusted on expansion |
| **Large Data Processing** | Streaming (with limitations) | No streaming support | Full in-memory load | Efficient large data processing (100K+ rows) |
| **Asynchronous Processing** | Not supported | Not supported | Not supported | Background generation, progress tracking, cancellation |
| **Selective Field Visibility** | Workaround via `jx:if` | Workaround via `<jt:if>` | Not supported | Dedicated hideable marker (DELETE/DIM modes) |
| **Built-in Aggregation** | GroupSum | jAgg (Sum, Avg, etc.) | Sum, Avg, Count, Min, Max | Uses Excel formulas |
| **Grouping** | groupBy (each attribute) | Not supported | GroupBy (auto cell merge) | Requires pre-grouped data |
| **Spring Boot** | Not supported | Not supported | Not supported | Ready to use by simply adding a dependency |

---

## 3. Detailed Comparison by Category

### 3.1 Template Design Approach

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Marker Location** | Panel tree defined in code | Written in cell comments | XML tags in cells | Written directly in cells |
| **Structure Visible from Template Alone** | No (requires code) | Difficult (must open comments) | Yes (tags are visible) | Yes (markers are visible in cells) |
| **On Layout Change** | Code modification required | Comment modification required | Tag modification required | Only the template file needs updating |

---

### 3.2 Formula Handling

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Formula Range Expansion** | Basic row shifting | Supported (Standard/Fast mode trade-off) | POI level | All reference types supported |
| **Variables in Formulas** | Not supported | Separate `$[...]` syntax + additional call required | JEXL expressions | Same `${variable}` syntax |
| **Cross-Sheet Formulas** | Not supported | Supported | Not supported | Supported |

---

### 3.3 Large Data Processing

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Streaming** | Not supported (full in-memory load) | Supported (disabled by default, with limitations) | Not supported (XSSF only) | Supported (handles 100K+ rows) |
| **Asynchronous Processing** | Not supported | Not supported | Not supported | Background generation, progress tracking, cancellation |

---

### 3.4 Layout Preservation

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Merged Cells** | Partial | Supported (dynamic merge limitations) | Supported (performance degrades with large volumes) | Supported |
| **Conditional Formatting** | Partial | Partial (multi-sheet limitations) | POI level (limited) | Supported |
| **Empty Collection Replacement** | Not supported (empty area exposed) | Requires `jx:if` + `jx:each` combination | Not supported | Supported - customizable |

---

### 3.5 Selective Field Visibility

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Dedicated syntax** | X | X | X | `${hideable(...)}` marker |
| **Delete mode** | X | Workaround via `jx:if` | Workaround via `<jt:if>` | DELETE (physical removal + shift) |
| **Deactivation mode** | X | X | X | DIM (layout preserved + style applied) |
| **Code-level control** | X | Requires condition variables | Requires condition variables | Single `hideFields()` call |

This feature allows restricting the visibility of specific fields from the same template depending on context. JXLS and JETT can achieve similar results using general-purpose conditional rendering (`jx:if`, `<jt:if>`), but require writing condition blocks for each field, and automatic adjustment of formulas and merged cells is not guaranteed. TBEG expresses this concisely with the dedicated `hideable` marker, and when fields are hidden, formula references, merged cells, and conditional formatting are automatically adjusted.

---

### 3.6 Charts and Pivot Tables

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Chart Data Range Adjustment** | Not supported | Not supported (requires workaround via dynamic named ranges) | Not supported (existing charts preserved only) | Auto-adjusted |
| **Pivot Table Source Range Adjustment** | Not supported | Not supported (requires refresh when opening file) | Not supported | Auto-adjusted |
| **Usable Without Refresh on Open** | - | X | - | O |

In JXLS, using charts requires setting up Excel's dynamic named ranges (`OFFSET` formulas, etc.) separately to handle data expansion. For pivot tables, the "Refresh data when opening the file" option must be enabled in the template's pivot table settings.

TBEG automatically adjusts chart data ranges and pivot table source ranges at generation time, so no separate refresh is needed when opening the output file.

---

### 3.7 Framework Integration

| | ERG | JXLS | JETT | TBEG |
|---|---|---|---|---|
| **Spring Boot** | Not supported | Not supported | Not supported | Ready to use by simply adding a dependency |

---

## 4. Unique Strengths of Other Libraries

For a fair comparison, here are the unique strengths each library offers.

### JXLS

**Grouping and Aggregation**

The `groupBy` attribute in `jx:each` and the `GroupSum` class allow declarative data grouping and aggregation.

In TBEG, you can achieve the same result using Excel formulas (`=SUM()`, `=AVERAGE()`, etc.). This approach has the advantage of allowing you to **inspect and verify calculation logic directly in Excel**.

**Conditional Rendering**

The `jx:if` command can show or hide regions based on conditions. In TBEG, field-level visibility control is handled by the `hideable` marker, while other conditional rendering can be achieved by pre-processing data in the DataProvider.

**Automatic Multi-Sheet Generation**

The `multisheet` attribute automatically creates a separate sheet for each item in a collection.

### JETT

**Direct JDBC Queries**

JDBC queries can be executed directly within templates, enabling data retrieval from databases without separate data access code.

**jAgg Aggregation Engine**

Integration with the jAgg library enables various aggregation operations (Sum, Average, Rollup, Cube, etc.) at the template level. In TBEG, the same results can be achieved using Excel formulas.

**CellListener**

Provides listeners for per-cell custom processing (alternating row highlighting, etc.).

### ERG

**Built-in Aggregation Functions**

Panels include built-in aggregation functions: Sum, Avg, Count, Min, Max, and custom aggregation functions.

**Automatic Cell Merging**

GroupBy automatically merges cells with identical values. This is useful for cases like department-employee lists where the same department name repeats.

**Panel Hierarchy**

Parent/Children panel trees enable complex nested report structures, with per-panel Before/After rendering hooks for fine-grained control.

---

## 5. Comprehensive Comparison Table

### Template & Data Binding

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Cell variable substitution | O | O | O | O |
| Variable substitution in formulas | O | O | X | O |
| Variable substitution in shapes/chart titles | X | X | X | O |
| Header/footer variable substitution | △ | X | X | O |
| Code-free template design | O | O | X | O |
| Marker visibility | △ | O | X | O |

### Repeat Processing

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Vertical repeat | O | O | O | O |
| Horizontal repeat | O | O | O | O |
| Multi-row/column repeat | O | O | O | O |
| Multiple repeat regions in one sheet | O | O | O | O |
| Cross-sheet repeat | X | X | X | O |
| Empty collection replacement content | △ | X | X | O |
| Automatic cell merge | △ | X | O | O |
| Selective field visibility | △ | △ | X | O |
| Built-in grouping | O | X | O | X |
| Conditional rendering | O | O | X | X |
| Automatic multi-sheet generation | O | X | X | X |

### Formulas & Formatting

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Basic formula preservation | O | O | O | O |
| Automatic formula range expansion | O | X | X | O |
| Absolute/relative/mixed reference handling | O | X | △ | O |
| Cross-sheet formula adjustment | O | X | X | O |
| Automatic merged cell replication | O | △ | △ | O |
| Automatic conditional formatting replication | △ | X | △ | O |
| Automatic number format application | X | X | X | O |

### Advanced Features

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Image insertion | O | O | X | O |
| Chart support | X | △ | X | O |
| Pivot table support | △ | X | X | O |
| Document metadata | X | X | X | O |
| File encryption | X | X | X | O |
| Built-in aggregation functions | O | O | O | X |
| Internationalization (resource bundles) | O | X | X | O |

### Performance & Scalability

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Streaming mode | △ | X | X | O |
| Asynchronous processing | X | X | X | O |
| Progress callback | X | X | △ | O |
| Task cancellation | X | X | X | O |
| Lazy loading (DataProvider) | X | X | X | O |

### Framework Integration

| Feature | JXLS | JETT | ERG | TBEG |
|---------|:----:|:----:|:---:|:----:|
| Spring Boot AutoConfiguration | X | X | X | O |
| Configuration file (yml/properties) integration | X | X | X | O |
| Java-compatible API | O | O | X | O |

> [!TIP]
> **Legend**: O = Supported, △ = Partial support or with limitations, X = Not supported

---

## 6. Selection Guide

### When TBEG Is the Right Choice

- You need to use **designer-created Excel forms** as-is
- Reports include **images, charts, or pivot tables**
- **Formula-based automatic calculations** are critical
- You need to **restrict visible fields based on permissions or purpose** from the same template
- You need to reliably process **tens of thousands to hundreds of thousands of rows**
- **Asynchronous processing** and **progress tracking** are required
- You want seamless integration in a **Spring Boot** environment
- You want to handle template changes **without modifying code**

### When JXLS Is the Right Choice

- Conditional rendering (`jx:if`) is a core requirement
- **Automatic multi-sheet generation** is needed
- Reports are primarily text-data oriented

### When JETT Is the Right Choice

- Direct JDBC query execution within templates is needed
- Conditional rendering (`<jt:if>`) is required
- Note: since maintenance has been discontinued, **JETT is not recommended for new projects**

### When ERG Is the Right Choice

- Your project is **.NET**-based
- You want to handle **aggregation and grouping** declaratively in code
- You need code-level control over complex nested structures via **panel trees**

> [!TIP]
> TBEG takes the approach of **leveraging Excel formulas** instead of built-in aggregation. By placing formulas such as `=SUM()` and `=AVERAGE()` in the template, formula ranges are automatically adjusted as data expands, giving you the advantage of being able to **inspect and verify calculation logic directly in Excel**.

---

## 7. Advantages of TBEG over Commercial Solutions

Commercial solutions such as Aspose.Cells and GcExcel (Document Solutions for Excel) are general-purpose Excel processing libraries that cover reading, writing, and converting Excel files. TBEG focuses specifically on **report generation**, offering the following differentiators.

### Cost

Commercial solutions cost $1,000 or more per developer license. TBEG is free to use under the Apache 2.0 license.

### Template-Centric Workflow

Commercial solutions are fundamentally based on **manipulating Excel from code**. Cell formatting, charts, formulas, and all other elements must be controlled through code. TBEG uses **designer-created Excel templates as-is**, with code focusing solely on data binding. No code changes are needed when templates change.

### Native Spring Boot Integration

Commercial solutions require separate configuration for framework integration. TBEG provides AutoConfiguration, configuration file integration, asynchronous processing (Coroutines/CompletableFuture), and progress callbacks out of the box simply by adding the dependency.

### Lightweight

Commercial solutions embed proprietary engines resulting in large JAR sizes and are general-purpose libraries covering reading, writing, and converting Excel files. TBEG includes only the features necessary for report generation and maintains compatibility with existing POI code through its Apache POI foundation.

### When Commercial Solutions Are More Appropriate

Commercial solutions may be more suitable if you have the following requirements:

- You need to **read/parse** Excel files
- You need to **convert Excel to PDF, HTML, or images**
- You need to **dynamically create/control all elements** of an Excel file from code
- You need a **built-in formula calculation engine** (pre-computing formula results on the server)

---

## Next Steps

- [User Guide](../user-guide.md) - Getting started with TBEG
- [TBEG Manual Index](../index.md) - Full documentation overview
