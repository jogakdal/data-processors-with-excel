> **[한국어](../../ko/appendix/library-comparison.md)** | English

# Appendix: Comparison with Other Libraries

This document compares TBEG with two similar template-based libraries — JXLS and ExcelReportGenerator (ERG) — to help you choose the right tool for Excel report generation.

---

## 1. Libraries Compared

### JXLS

The most widely known template-based Excel generation library in the JVM ecosystem. It runs on Apache POI and defines report structure through **commands written in cell comments (Notes)**.

- **Platform**: JVM (Java 17)
- **Excel Engine**: Apache POI
- **License**: Apache License 2.0
- **GitHub**: [jxlsteam/jxls](https://github.com/jxlsteam/jxls)

### ExcelReportGenerator (ERG)

A .NET-based Excel report generation library. It runs on ClosedXML and organizes reports through Named Ranges and a hierarchical "panel" structure.

- **Platform**: .NET Standard 2.0
- **Excel Engine**: ClosedXML (OpenXML wrapper)
- **License**: MIT
- **GitHub**: [traf72/excel-report-generator](https://github.com/traf72/excel-report-generator)

### TBEG (Template-Based Excel Generator)

A Kotlin/JVM-based Excel report generation library. Markers are written directly in Excel template cells, allowing you to use designer-created Excel forms as-is and simply bind data to them.

- **Platform**: JVM (Kotlin/Java)
- **Excel Engine**: Apache POI (XSSF/SXSSF)
- **Package**: `io.github.jogakdal:tbeg`

---

## 2. At a Glance

| Feature | JXLS | ERG | TBEG |
|---------|------|-----|------|
| **Template Design** | Commands in cell comments | Named Range + panel tree in code | Markers written directly in cells |
| **Image Insertion** | Supported | Not supported | Position, size, aspect ratio, and more |
| **Charts** | Not supported | Not supported | Auto-adjusted when data expands |
| **Pivot Tables** | Relies on Excel refresh | Not supported | Auto-adjusted when data expands |
| **Formula Auto-Adjustment** | Supported | Basic level | All reference types supported; auto-adjusted on expansion |
| **Large Data Processing** | Streaming (with limitations) | Full in-memory load | Streaming mode (100K+ rows) |
| **Asynchronous Processing** | Not supported | Not supported | Background generation, progress tracking, cancellation |
| **Built-in Aggregation** | GroupSum | Sum, Avg, Count, Min, Max | Uses Excel formulas |
| **Grouping** | groupBy (each attribute) | GroupBy (auto cell merge) | Requires pre-grouped data |
| **Spring Boot** | Not supported | Not supported | Ready to use by simply adding a dependency |

---

## 3. Detailed Comparison by Category

### 3.1 Template Design Approach

| | ERG | JXLS | TBEG |
|---|---|---|---|
| **Marker Location** | Panel tree defined in code | Written in cell comments | Written directly in cells |
| **Structure Visible from Template Alone** | No (requires code) | Difficult (must open comments) | Yes (markers are visible in cells) |
| **On Layout Change** | Code modification required | Comment modification required | Only the template file needs updating |

---

### 3.2 Formula Handling

| | ERG | JXLS | TBEG |
|---|---|---|---|
| **Formula Range Expansion** | Basic row shifting | Supported (Standard/Fast mode trade-off) | All reference types supported |
| **Variables in Formulas** | Not supported | Separate `$[...]` syntax + additional call required | Same `${variable}` syntax |
| **Cross-Sheet Formulas** | Not supported | Supported | Supported |

---

### 3.3 Large Data Processing

| | ERG | JXLS | TBEG |
|---|---|---|---|
| **Streaming** | Not supported (full in-memory load) | Supported (disabled by default, with limitations) | Enabled by default (handles 100K+ rows) |
| **Asynchronous Processing** | Not supported | Not supported | Background generation, progress tracking, cancellation |

---

### 3.4 Layout Preservation

| | ERG | JXLS | TBEG |
|---|---|---|---|
| **Merged Cells** | Partial | Supported (dynamic merge limitations) | Supported |
| **Conditional Formatting** | Partial | Partial (multi-sheet limitations) | Supported |
| **Empty Collection Replacement** | Not supported (empty area exposed) | Requires `jx:if` + `jx:each` combination | Specified via `empty` parameter |

---

### 3.5 Framework Integration

| | ERG | JXLS | TBEG |
|---|---|---|---|
| **Spring Boot** | Not supported | Not supported | Ready to use by simply adding a dependency |

---

## 4. Unique Strengths of Other Libraries

For a fair comparison, here are the unique strengths each library offers.

### JXLS

**Grouping and Aggregation**

The `groupBy` attribute in `jx:each` and the `GroupSum` class allow declarative data grouping and aggregation.

In TBEG, you can achieve the same result using Excel formulas (`=SUM()`, `=AVERAGE()`, etc.). This approach has the advantage of allowing you to **inspect and verify calculation logic directly in Excel**.

**Conditional Rendering**

The `jx:if` command can show or hide regions based on conditions. In TBEG, you can achieve the same result by pre-processing data in the DataProvider.

**Automatic Multi-Sheet Generation**

The `multisheet` attribute automatically creates a separate sheet for each item in a collection.

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

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Cell variable substitution | O | O | O |
| Variable substitution in formulas | O | X | O |
| Variable substitution in shapes/chart titles | X | X | O |
| Header/footer variable substitution | △ | X | O |
| Code-free template design | O | X | O |
| Marker visibility | △ | X | O |

### Repeat Processing

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Vertical repeat | O | O | O |
| Horizontal repeat | O | O | O |
| Multi-row/column repeat | O | O | O |
| Multiple repeat regions in one sheet | O | O | O |
| Cross-sheet repeat | X | X | O |
| Empty collection replacement content | △ | X | O |
| Built-in grouping | O | O | X |
| Conditional rendering | O | X | X |
| Automatic multi-sheet generation | O | X | X |

### Formulas & Formatting

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Basic formula preservation | O | O | O |
| Automatic formula range expansion | O | X | O |
| Absolute/relative/mixed reference handling | O | △ | O |
| Cross-sheet formula adjustment | O | X | O |
| Automatic merged cell replication | O | △ | O |
| Automatic conditional formatting replication | △ | △ | O |
| Automatic number format application | X | X | O |

### Advanced Features

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Image insertion | O | X | O |
| Chart support | X | X | O |
| Pivot table support | △ | X | O |
| Document metadata | X | X | O |
| File encryption | X | X | O |
| Built-in aggregation functions | O | O | X |
| Internationalization (resource bundles) | O | X | O |

### Performance & Scalability

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Streaming mode | △ | X | O |
| Asynchronous processing | X | X | O |
| Progress callback | X | △ | O |
| Task cancellation | X | X | O |
| Lazy loading (DataProvider) | X | X | O |

### Framework Integration

| Feature | JXLS | ERG | TBEG |
|---------|:----:|:---:|:----:|
| Spring Boot AutoConfiguration | X | X | O |
| Configuration file (yml/properties) integration | X | X | O |
| Java-compatible API | O | X | O |

> [!TIP]
> **Legend**: O = Supported, △ = Partial support or with limitations, X = Not supported

---

## 6. Selection Guide

### When TBEG Is the Right Choice

- You need to use **designer-created Excel forms** as-is
- Reports include **images, charts, or pivot tables**
- **Formula-based automatic calculations** are critical
- You need to reliably process **tens of thousands to hundreds of thousands of rows**
- **Asynchronous processing** and **progress tracking** are required
- You want seamless integration in a **Spring Boot** environment
- You want to handle template changes **without modifying code**

### When JXLS Is the Right Choice

- Conditional rendering (`jx:if`) is a core requirement
- **Automatic multi-sheet generation** is needed
- Reports are primarily text-data oriented

### When ERG Is the Right Choice

- Your project is **.NET**-based
- You want to handle **aggregation and grouping** declaratively in code
- You need code-level control over complex nested structures via **panel trees**

> [!TIP]
> TBEG takes the approach of **leveraging Excel formulas** instead of built-in aggregation. By placing formulas such as `=SUM()` and `=AVERAGE()` in the template, formula ranges are automatically adjusted as data expands, giving you the advantage of being able to **inspect and verify calculation logic directly in Excel**.
