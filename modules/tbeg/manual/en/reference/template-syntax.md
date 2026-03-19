> **[한국어](../../ko/reference/template-syntax.md)** | English

# TBEG Template Syntax Reference

## Table of Contents
1. [Marker Overview](#1-marker-overview)
2. [Variable Substitution](#2-variable-substitution)
   - [Formula Binding](#23-formula-binding)
3. [Repeat Processing](#3-repeat-processing)
   - [Empty Collection Handling (empty)](#35-empty-collection-handling-empty)
   - [Automatic Adjustment of Related Elements](#36-automatic-adjustment-of-related-elements)
4. [Image Insertion](#4-image-insertion)
5. [Collection Size](#5-collection-size)
6. [Automatic Cell Merge](#6-automatic-cell-merge)
7. [Variables in Formulas](#7-variables-in-formulas)
8. [Element Bundling (bundle)](#8-element-bundling-bundle)
9. [Formatting Preservation](#9-formatting-preservation)
10. [Selective Field Visibility (hideable)](#10-selective-field-visibility-hideable)

---

## 1. Marker Overview

### 1.1 What Is a Marker

A **marker** is a special text placed in an Excel template to indicate where data should go. When TBEG processes the template, it replaces markers with actual data, and successfully processed markers do not appear in the output file.

### 1.2 Basic Format

All markers are written in cells using the `${...}` format.

```
${...}
```

Markers are divided into two categories: **variable markers** and **function markers**.

#### Variable Markers

Variable markers take the form `${variableName}` and are directly replaced with data values. You can use dot (`.`) notation to access object properties, including multi-level nested fields.

```
${title}                  // Simple value substitution
${emp.name}               // Object property access
${emp.department.name}    // Nested property access
Report: ${title}          // Combined with text
```

Variable markers can be used not only in cell values but also in formula arguments, chart titles, shape text, and headers/footers. If the bound value starts with `=`, it is treated as an Excel formula (see [2.3 Formula Binding](#23-formula-binding)).

#### Function Markers

Function markers take the form `${function(parameters...)}` and perform specific operations based on the data.

| Function | Purpose | Example |
|----------|---------|---------|
| `repeat` | Repeats a range for each item in a collection | `${repeat(employees, A2:C2, emp)}` |
| `image` | Inserts a dynamic image | `${image(logo, B2, 200:150)}` |
| `size` | Outputs the number of items in a collection | `${size(employees)}` |
| `merge` | Automatically merges consecutive cells with the same value | `${merge(emp.dept)}` |
| `bundle` | Groups multiple elements as a single unit | `${bundle(A5:H12)}` |
| `hideable` | Selectively shows or hides a field | `${hideable(emp.salary, C1:C3)}` |

#### Case Sensitivity

| Element | Case Sensitive | Examples |
|---------|---------------|----------|
| Function names | No | `${Repeat(...)}`, `${REPEAT(...)}`, `=tbeg_repeat(...)` are all equivalent |
| Keyword arguments (`direction`, `size`) | No | `DOWN`, `down`, `Right`, `FIT`, `Original` are all equivalent |
| Parameter names (explicit format) | No | `Collection=`, `RANGE=`, `var=` are all equivalent |
| Data keys (collection names, variable names, field names, image names) | **Yes** | `${employeeName}` and `${employeename}` are different |

#### Formula Style

Function markers can also be written in the **formula style** `=TBEG_FUNCTION(parameters...)`. The behavior is identical, and you can leverage Excel's cell reference features (clicking, drag-selecting, etc.) to specify ranges.

| Text Form | Formula Form |
|---|---|
| `${repeat(col, range, var)}` | `=TBEG_REPEAT(col, range, var)` |
| `${image(name)}` | `=TBEG_IMAGE(name)` |
| `${size(col)}` | `=TBEG_SIZE(col)` |
| `${merge(item.field)}` | `=TBEG_MERGE(item.field)` |
| `${bundle(range)}` | `=TBEG_BUNDLE(range)` |
| `${hideable(value, bundle)}` | `=TBEG_HIDEABLE(value, bundle)` |

> [!NOTE]
> Formula-style markers display as `#NAME?` errors in Excel. This is expected and they are processed correctly during generation.

### 1.3 Common Rules for Function Markers

The following rules apply to all function markers.

#### Marker Placement

Function markers can be placed anywhere in the workbook as long as they are outside the target range. They can even be placed on a different sheet.

For example, `${repeat(employees, A2:C2, emp)}` can be placed in any cell on the same sheet or a different sheet, as long as it is outside the repeat range A2:C2. Similarly, `${bundle(A5:H12)}` can be placed anywhere outside the bundle range.

#### Cell Range Notation

Range parameters in function markers (`range`, `bundle`, `empty`, `position`, etc.) use Excel's cell range notation. Columns are represented by letters (A, B, ..., Z, AA, AB, ...) and rows by numbers starting from 1.

| Notation | Meaning | Example |
|----------|---------|---------|
| `A1` | Single cell | Column A, row 1 |
| `A1:C3` | Cell range | Area from column A row 1 to column C row 3 |
| `$A$1:$C$3` | Absolute reference | Same as above (`$` is ignored) |
| `'SheetName'!A1:C3` | Range on another sheet | A1:C3 area on the specified sheet |

> [!NOTE]
> Range notation uses the same format that Excel auto-generates when you drag-select a cell area during editing. In formula-style (`=TBEG_REPEAT(...)`) markers, you can conveniently specify ranges using Excel's cell reference features (clicking, drag-selecting).

#### Explicit Parameter Format

All parameters of function markers can be specified explicitly by name.

```
// Positional
${repeat(employees, A2:C2, emp)}
${image(logo, B2, 200:150)}

// Explicit
${repeat(collection=employees, range=A2:C2, var=emp)}
${image(name=logo, position=B2, size=200:150)}
```

The same applies to formula-style markers.

```
=TBEG_REPEAT(collection=employees, range=A2:C2, var=emp)
=TBEG_IMAGE(name=logo, position=B2, size=original)
```

#### Omitting Parameters

In the explicit format, optional parameters can be omitted entirely, set to `NULL`, or left as an empty value. The following three are all equivalent.

```
${repeat(collection=items, range=A2:C2, empty=A10:C10)}           // var omitted
${repeat(collection=items, range=A2:C2, var=NULL, empty=A10:C10)} // var=NULL
${repeat(collection=items, range=A2:C2, var=, empty=A10:C10)}     // var= (empty value)
```

#### Quotes

All parameter values can be wrapped in quotes (`"`, `'`, `` ` ``). Whether wrapped or not, they are processed identically. This applies to both positional and explicit formats.

```
// All four are equivalent
${repeat(collection=employees, range=A2:C2, var=emp)}
${repeat(collection="employees", range="A2:C2", var="emp")}
${repeat(collection='employees', range='A2:C2', var='emp')}
${repeat(collection=`employees`, range=`A2:C2`, var=`emp`)}
```

#### Mixing Not Allowed

Positional and explicit parameters cannot be mixed. Once any parameter is named, all parameters must be named.

```
// Correct
${repeat(items, A2:C2, item, DOWN, A10:C10)}                                    // All positional
${repeat(collection=items, range=A2:C2, var=item, direction=DOWN, empty=A10:C10)} // All explicit

// Incorrect (causes an error)
${repeat(items, A2:C2, item, empty=A10:C10)}         // Cannot mix
${repeat(items, A2:C2, var=item, direction=DOWN)}   // Cannot mix
```

#### No Boundary Overlap

All elements that deal with cell ranges (repeat ranges, bundle ranges, merged cells, etc.) must not partially overlap each other. Each element must be either fully contained within another element or fully outside of it.

```
// Correct
${repeat(a, A2:C2, ...)}    // Columns A-C
${repeat(b, E2:G2, ...)}    // Columns E-G (fully separate)
${bundle(A5:G10)}            // Fully contains both repeats

// Incorrect (causes an error)
${repeat(a, A2:C2, ...)}    // Columns A-C
${bundle(B5:G10)}            // Partially overlaps repeat range at column B
```

#### Skipping Positional Parameters

In positional format, intermediate parameters can be skipped by leaving them empty.

```
// Skip direction (4th) and specify only empty (5th)
${repeat(items, A2:C2, item, , A10:C10)}

// Skip var (3rd) and direction (4th)
${repeat(items, A2:C2, , , A10:C10)}

// In image, skip position (2nd) and specify only size (3rd)
${image(logo, , 200:150)}
```

---

## 2. Variable Substitution

### 2.1 Simple Variables

**Syntax**: `${variableName}`

Replaces `${variableName}` markers in the template with the corresponding key values from the data.

#### Template

|   | A      | B         |
|---|--------|-----------|
| 1 | Title    | ${title}  |
| 2 | Date     | ${date}   |
| 3 | Author   | ${author} |

#### Data

```kotlin
mapOf(
    "title" to "Monthly Report",
    "date" to "2026-01-15",
    "author" to "Yongho Hwang"
)
```

#### Result

|   | A   | B          |
|---|-----|------------|
| 1 | Title  | Monthly Report |
| 2 | Date   | 2026-01-15     |
| 3 | Author | Yongho Hwang   |

#### Supported Types

The following types are supported for dot (`.`) notation access.

- **Object fields**: Properties/fields of data classes or POJOs
- **Map keys**: Access by Map key
- **Getter methods**: Getters in the form `getFieldName()`

These rules apply equally to simple variables (`${emp.name}`) and repeat item fields (`${item.field}`).

### 2.2 Composite Text

Multiple variables and text can be combined in a single cell.

```
Author: ${author} (${department})
```

### 2.3 Formula Binding

When a bound value starts with `=`, it is treated as an **Excel formula** rather than plain text.

#### Basic Usage

```kotlin
val data = mapOf("formula" to "=SUM(A1:A10)")
// Template cell: ${formula}
// Result: =SUM(A1:A10) (actual formula)
```

This allows you to dynamically determine formulas based on data.

#### Formula Binding in Repeat

Formula binding works the same way within repeat item fields. Automatic formula range adjustments (expansion, row shifting) from the repeat are also applied.

**Template**

|   | A                                | B             | C             | D                |
|---|----------------------------------|---------------|---------------|------------------|
| 1 | Name                             | Revenue       | Target        | Achievement      |
| 2 | ${s.name}                        | ${s.amount}   | ${s.target}   | ${s.rateFormula} |
| 3 | ${repeat(sales, A2:D2, s)}       |               |               |                  |
| 4 | Total                            | ${totalRevenue} |             |                  |

**Data**

```kotlin
mapOf(
    "sales" to listOf(
        mapOf("name" to "Team A", "amount" to 15000, "target" to 20000, "rateFormula" to "=B2/C2"),
        mapOf("name" to "Team B", "amount" to 22000, "target" to 18000, "rateFormula" to "=B2/C2"),
    ),
    "totalRevenue" to "=SUM(B2:B2)"
)
```

**Result** (2 items)

|   | A  | B      | C      | D          |
|---|----|--------|--------|------------|
| 1 | Name | Revenue | Target | Achievement |
| 2 | Team A | 15,000 | 20,000 | =B2/C2     |
| 3 | Team B | 22,000 | 18,000 | =B3/C3     |
| 4 | Total | =SUM(B2:B3) |   |            |

- `rateFormula` (`=B2/C2`) is row-shifted to `=B2/C2`, `=B3/C3` for each row
- `totalRevenue` (`=SUM(B2:B2)`) is range-expanded to `=SUM(B2:B3)` to match the repeat expansion

#### Caution

**All** string values starting with `=` are treated as formulas. Binding plain text that starts with an equals sign (e.g., `"=Grade A talent"`) may cause errors because it is processed as an invalid formula. To output text starting with an equals sign as-is, prepend a space or apostrophe to prevent formula recognition.

```kotlin
// Treated as a formula (intended)
mapOf("formula" to "=SUM(A1:A10)")

// To keep as text, prepend a space
mapOf("grade" to " =Grade A talent")
```

#### Automatic Number Format

When a formula-substituted cell has the "General" display format, an integer number format (`#,##0`) is automatically applied. For formulas that require decimal places (e.g., ratios, averages), set the desired display format (e.g., `0.0%`, `#,##0.00`) directly on the template cell.

> [!TIP]
> For detailed rules on automatic formula range adjustment (row shifting, range expansion) within repeat, see [3.6 Automatic Adjustment of Related Elements](#36-automatic-adjustment-of-related-elements).

---

## 3. Repeat Processing

### 3.1 Basic Repeat (DOWN Direction)

**Syntax**: `${repeat(collection, range, variable)}`

Repeats the specified range downward for each item in the collection.

#### Template

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | Name                               | Position        | Salary        |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

#### Data

```kotlin
data class Employee(val name: String, val position: String, val salary: Int)

mapOf(
    "employees" to listOf(
        Employee("Yongho Hwang", "Director", 8000),
        Employee("Yongho Han", "Manager", 6500),
        Employee("Yongho Hong", "Assistant Manager", 4500)
    )
)
```

#### Result

|   | A   | B  | C     |
|---|-----|----|-------|
| 1 |     |    |       |
| 2 | Name              | Position          | Salary |
| 3 | Yongho Hwang      | Director          | 8,000  |
| 4 | Yongho Han        | Manager           | 6,500  |
| 5 | Yongho Hong       | Assistant Manager | 4,500  |

### 3.2 Multi-Row Repeat

Specifying multiple rows in the range causes them to repeat together as a group.

#### Template

|   | A                                | B                   |
|---|----------------------------------|---------------------|
| 1 | ${repeat(employees, A2:B3, emp)} |                     |
| 2 | Name: ${emp.name}                | Position: ${emp.position} |
| 3 | Salary: ${emp.salary}            |                           |

### 3.3 Rightward Repeat (RIGHT)

**Syntax**: `${repeat(collection, range, variable, RIGHT)}`

Repeats the specified range to the right for each item in the collection.

#### Template

|   | A                                      | B           |
|---|----------------------------------------|-------------|
| 1 | ${repeat(months, B1:B2, m, RIGHT)}     | ${m.month}  |
| 2 |                                        | ${m.sales}  |

#### Result

|   | A  | B      | C      | D      |
|---|----|--------|--------|--------|
| 1 |    | Jan    | Feb    | Mar    |
| 2 |    | 100    | 150    | 200    |

### 3.4 Multiple Repeat Regions

Multiple repeat regions can be used within a single sheet.

#### Separate Column Groups

Repeat regions located in different columns expand independently.

|   | A                                | B             | C | D                                   | E              |
|---|----------------------------------|---------------|---|-------------------------------------|----------------|
| 1 | ${repeat(employees, A2:B2, emp)} |               |   | ${repeat(departments, D2:E2, dept)} |                |
| 2 | ${emp.name}                      | ${emp.salary} |   | ${dept.name}                        | ${dept.budget} |

#### Duplicate Markers

If multiple repeat markers share the same collection and the same target range, they are considered duplicates. When duplicate markers are found, a warning log is emitted and only the last marker takes effect.

- Even if they are on different sheets, referencing the same target via a sheet prefix (e.g., `'Sheet1'!A2:C2`) counts as a duplicate
- If the ranges are the same but the collections differ, they are not duplicates

#### Restrictions

- Repeat regions must not partially overlap other range elements (see [No Boundary Overlap](#no-boundary-overlap))
- Multiple repeat regions can be stacked vertically within the same column range

### 3.5 Empty Collection Handling (empty)

**Syntax**: `${repeat(collection, range, variable, direction, fallbackRange)}`

Displays the contents of a fallback cell range when the collection is empty.

#### Default Behavior (no empty parameter)

When the collection is empty and no `empty` parameter is specified, one blank row (DOWN mode) or blank column (RIGHT mode) matching the repeat region size is produced.

- **Blank row/column scope**: Only the cells within the repeat region (range) are output as blank values, not the entire Excel row.
- **Example**: For `range=A2:C2`, only columns A, B, and C are blank; column D and beyond are unaffected.

#### Using the empty Parameter

When the collection is empty, the contents of the specified cell range are displayed instead.

#### Template

|     | A                                               | B               | C             |
|-----|-------------------------------------------------|-----------------|---------------|
| 1   | ${repeat(employees, A2:C2, emp, DOWN, A10:C10)} |                 |               |
| 2   | ${emp.name}                                     | ${emp.position} | ${emp.salary} |
| ... |                                                 |                 |               |
| 10  | (No data available)                             |                 |               |

#### Data (empty collection)

```kotlin
mapOf("employees" to emptyList<Employee>())
```

#### Result

|   | A           | B | C |
|---|-------------|---|---|
| 1 |             |   |   |
| 2 | (No data available) |   |   |

> [!NOTE]
> - The content and style from A10:C10 are copied to the A2:C2 position.
> - The original A10:C10 cells become blank in the output file.
> - If the empty range is a single cell and the repeat region is larger, cells are automatically merged.

#### Single-Cell Merge Example

If the empty range is a single cell, the entire repeat region is merged and its content is displayed.

**Template**

|     | A                                           | B               | C             |
|-----|---------------------------------------------|-----------------|---------------|
| 1   | ${repeat(employees, A2:C2, emp, DOWN, A10)} |                 |               |
| 2   | ${emp.name}                                 | ${emp.position} | ${emp.salary} |
| ... |                                             |                 |               |
| 10  | No data available                           |                 |               |

- **A10**: Write the message in a single cell (specify only A10, not A10:C10)

**Result (empty collection)**

<table>
  <tr><th></th><th>A</th><th>B</th><th>C</th></tr>
  <tr><td>1</td><td></td><td></td><td></td></tr>
  <tr><td>2</td><td colspan="3" style="text-align:center">No data available</td></tr>
</table>

- The A2:C2 region is automatically merged and the content of A10 is displayed.

#### Specifying the empty Range

```
${repeat(items, A2:C3, item, DOWN, A10:C11)}     // Normal range
${repeat(items, A2:C3, item, DOWN, $A$10:$C$11)} // Absolute reference ($ is ignored)
${repeat(items, A2:C3, item, DOWN, 'Sheet2'!A1:C1)} // Cross-sheet reference
```

#### Positional Parameter Syntax

The empty range can also be specified as the 5th positional parameter without explicit names.

```
${repeat(items, A2:C3, item, DOWN, A10:C11)}
=TBEG_REPEAT(items, A2:C3, item, DOWN, A10:C11)
```

### 3.6 Automatic Adjustment of Related Elements

When a repeat region expands, the coordinates and ranges of affected Excel elements are automatically adjusted.

#### Automatically Adjusted Elements

| Element              | Adjustment Details                                                      |
|----------------------|-------------------------------------------------------------------------|
| Formula references   | Cell references (e.g., `=SUM(A1:A10)`) are adjusted to the expanded range |
| Charts               | Data source ranges are adjusted to match the expanded data              |
| Pivot tables         | Source data ranges are adjusted to match the expanded data              |
| Merged cells         | Positions of merged cells outside the repeat region are adjusted        |
| Conditional formats  | Applied ranges are adjusted to match the expanded region                |

#### Position Shifting and Range Expansion

When a repeat region expands:
- Elements below the repeat region shift down by the expansion amount.
- Formulas referencing the repeat region are updated to cover the entire expanded range.

**Template**

|   | A                                | B             |
|---|----------------------------------|---------------|
| 1 | ${repeat(items, A2:B2, item)}    |               |
| 2 | ${item.name}                     | ${item.value} |
| 3 | Total                            | =SUM(B2:B2)   |

**Result** (3 items)

|   | A      | B           |
|---|--------|-------------|
| 1 |        |             |
| 2 | Item 1 | 100         |
| 3 | Item 2 | 200         |
| 4 | Item 3 | 300         |
| 5 | Total  | =SUM(B2:B4) |

> [!NOTE]
> The totals row originally at row 3 shifts to row 5, and the formula `=SUM(B2:B2)` is updated to reference the expanded range `=SUM(B2:B4)`.

#### Formula Reference Behavior by Type

| Reference Type       | Example            | Expansion Behavior                             |
|----------------------|--------------------|------------------------------------------------|
| Relative reference   | `B3:B3`            | Expanded (`B3:B5`)                             |
| Absolute reference   | `$B$3:$B$3`        | Preserved (not expanded)                       |
| Row-absolute         | `B$3:B$3`          | Preserved (not expanded in DOWN direction)     |
| Column-absolute      | `$B3:$B3`          | Expanded (DOWN direction: `$B3:$B5`)           |
| Cross-sheet          | `Sheet2!B3:B3`     | Reflects repeat expansion on that sheet        |

**Example: Controlling expansion with absolute references**

```
=SUM(B3:B3)      -> =SUM(B3:B5)     // Relative reference: expanded
=SUM($B$3:$B$3)  -> =SUM($B$3:$B$3) // Absolute reference: preserved
=SUM($B3:$B3)    -> =SUM($B3:$B5)   // Column-absolute only: row expansion applies
```

**Example: Referencing a repeat region on another sheet**

If Sheet2 has a repeat region that expands to 3 items, formulas on Sheet1 referencing Sheet2 are also expanded.

```
=SUM(Sheet2!B3:B3)       -> =SUM(Sheet2!B3:B5)
=SUM('Data Sheet'!B3:B3) -> =SUM('Data Sheet'!B3:B5)
```

---

## 4. Image Insertion

### 4.1 Basic Image

**Syntax**: `${image(name)}`

Inserts an image into the cell (or merged region) containing the marker.

#### Template

|   | A              | B               |
|---|----------------|-----------------|
| 1 | ${image(logo)} | Company: ${company} |
| 2 | (merged cell)  |                     |

#### Data

```kotlin
val provider = simpleDataProvider {
    value("company", "Hunet Inc.")

    // Provide as ByteArray
    image("logo", logoBytes)

    // Or provide as URL (auto-downloaded during rendering)
    imageUrl("logo", "https://example.com/logo.png")
}
```

### 4.2 Specifying Position

**Syntax**: `${image(name, position)}`

Specifies the cell position where the image should be inserted.

```
${image(logo, B2)}      // Insert at cell B2
${image(stamp, D5:F8)}  // Insert within the D5:F8 range
```

### 4.3 Specifying Size

**Syntax**: `${image(name, position, size)}`

Specifies the image size. When no size is given, the default is to fit the cell/range.

| Size Notation           | Description                              |
|-------------------------|------------------------------------------|
| `fit` or `0:0`          | Fit to cell/range (default)              |
| `original` or `-1:-1`   | Original size                            |
| `200:150`               | 200px wide, 150px tall                   |
| `200:-1`                | 200px wide, proportional height          |
| `-1:150`                | 150px tall, proportional width           |
| `0:-1`                  | Fit to cell width, proportional height   |
| `-1:0`                  | Fit to cell height, proportional width   |

```
${image(logo, B2)}            // Fit to cell size (default)
${image(logo, B2, fit)}       // Fit to cell size
${image(logo, B2, original)}  // Original size
${image(logo, B2, 200:150)}   // 200x150 pixels
```

### 4.4 Duplicate Markers

If multiple image markers share the same name, position, and size, they are considered duplicates. When duplicate markers are found, a warning log is emitted and only the last marker takes effect.

- If the sizes differ, they are not duplicates (e.g., `${image(logo, B1:C2, 100:50)}` and `${image(logo, B1:C2, 200:100)}`)
- Image markers without a `position` parameter are each inserted at their own marker cell location, so they are not subject to duplicate checking
- Referencing the same target position from a different sheet via a sheet prefix (e.g., `'Sheet1'!B1:C2`) counts as a duplicate

### 4.5 URL Images

When an HTTP(S) URL string is specified as image data instead of a `ByteArray`, the image is automatically downloaded at Excel generation time and embedded in the file. The generated Excel file does not require network access when opened.

```kotlin
val provider = simpleDataProvider {
    image("logo", logoBytes)                               // ByteArray
    imageUrl("banner", "https://example.com/banner.png")   // URL
}
```

**Caching behavior**:
- Within a single `generate()` call, the same URL is downloaded only once (regardless of settings)
- To enable caching across multiple `generate()` calls, use the `imageUrlCacheTtlSeconds` setting ([see Configuration Options](./configuration.md#imageurlcachettlseconds))

**Failure handling**: If a download fails (network error, 404, etc.), a warning log is emitted and the image is skipped. Excel generation itself completes normally.

---

## 5. Collection Size

**Syntax**: `${size(collection)}`

Displays the number of items in a collection.

#### Template

|   | A                           |
|---|-----------------------------|
| 1 | Total employees: ${size(employees)} |

#### Result (when employees has 5 entries)

|   | A                  |
|---|---------------------|
| 1 | Total employees: 5 |

---

## 6. Automatic Cell Merge

**Syntax**: `${merge(item.field)}`

Automatically merges consecutive cells with the same value during repeat expansion.
Data must be pre-sorted by the merge key field.

- **DOWN repeat**: Merges vertically
- **RIGHT repeat**: Merges horizontally

### 6.1 Basic Usage

#### Template

|   | A                    | B           | C           | D                              |
|---|----------------------|-------------|-------------|--------------------------------|
| 1 | Department           | Name        | Rank        | ${repeat(employees, A2:C2, emp)} |
| 2 | ${merge(emp.dept)}   | ${emp.name} | ${emp.rank} |                                |

#### Data

```kotlin
mapOf(
    "employees" to listOf(
        mapOf("dept" to "Sales", "name" to "Yongho Hwang", "rank" to "Staff"),
        mapOf("dept" to "Sales", "name" to "Yongho Han", "rank" to "Assistant Manager"),
        mapOf("dept" to "Engineering", "name" to "Yongho Hong", "rank" to "Manager"),
    )
)
```

#### Result

<table>
  <tr><th></th><th>A</th><th>B</th><th>C</th></tr>
  <tr><td>1</td><td>Department</td><td>Name</td><td>Rank</td></tr>
  <tr><td>2</td><td rowspan="2">Sales</td><td>Yongho Hwang</td><td>Staff</td></tr>
  <tr><td>3</td><td>Yongho Han</td><td>Assistant Manager</td></tr>
  <tr><td>4</td><td>Engineering</td><td>Yongho Hong</td><td>Manager</td></tr>
</table>

> A2:A3 is automatically merged as "Sales".

### 6.2 Multi-Level Merge

Using merge markers on multiple columns causes each column to merge independently.

```
${merge(emp.dept)}   <- Column A (merge by department)
${merge(emp.team)}   <- Column B (merge by team)
${emp.name}          <- Column C (no merge)
```

### 6.3 Notes

- Using a merge marker outside a repeat region performs simple value substitution only.
- Null values are excluded from merge candidates.

---

## 7. Variables in Formulas

Variables can be used within Excel formulas.

### 7.1 HYPERLINK

**Syntax**: `=HYPERLINK("${url}", "${text}")`

```
=HYPERLINK("${linkUrl}", "${linkText}")
```

### 7.2 Dynamic Ranges

**Syntax**: `=SUM(B${startRow}:B${endRow})`

Variables can be used in cell references within formulas to create dynamic ranges.

#### Template

|   | A  | B                               |
|---|----|---------------------------------|
| 1 | Start | ${startRow}                     |
| 2 | End   | ${endRow}                       |
| 3 | Total | =SUM(B\${startRow}:B\${endRow}) |

#### Data

```kotlin
mapOf("startRow" to 5, "endRow" to 10)
```

#### Result

|   | A      | B              |
|---|--------|----------------|
| 1 | Start  | 5              |
| 2 | End    | 10             |
| 3 | Total  | =SUM(B5:B10)   |

---

## 8. Element Bundling (bundle)

**Syntax**: `${bundle(range)}`

Treats all elements within the specified range as a single unit, so that the entire bundle moves together during repeat expansion.

### 8.1 Purpose

When a table spanning multiple columns (with headers, data rows, total rows, etc.) exists, expanding only some columns' repeat regions can cause the table to become misaligned. Wrapping the entire table in a bundle ensures it always moves as a single unit.

### 8.2 Basic Usage

#### Template

|     | A                             | B               | C    | D    | E      |
|-----|-------------------------------|-----------------|------|------|--------|
| 1   | ${repeat(depts, A2:B2, dept)} |                 |      |      |        |
| 2   | ${dept.name}                  | ${dept.revenue} |      |      |        |
| 3   | ${bundle(A4:E6)}              |                 |      |      |        |
| 4   | Name                          | Revenue         | Cost | Profit | Total |
| 5   | Yongho Hwang                  | 1000            | 500  | 500  | 2000   |
| 6   | Total                         |                 |      |      | =SUM() |

- Rows 1-2: `depts` repeat expands in columns A-B
- Rows 4-6: Table wrapped in a bundle (entire columns A-E)

Below is a comparison of results when depts has 3 items.

#### Without bundle -- table becomes misaligned

Only the repeat column range (A-B) shifts down, while columns outside the range (C-E) remain at their original rows.

|     | A      | B     | C    | D    | E      |
|-----|--------|-------|------|------|--------|
| 2   | Dept A | 52000 |      |      |        |
| 3   | Dept B | 38000 |      |      |        |
| 4   | Dept C | 28000 | Cost | Profit | Total |
| 5   |        |       | 500  | 500  | 2000   |
| 6   | Name   | Revenue |    |      | =SUM() |
| 7   | Yongho Hwang | 1000 |  |      |        |
| 8   | Total  |       |      |      |        |

Row 4: Column A has "Dept C" while columns C-E have headers (Cost/Profit/Total) -- different content ends up on the same row, breaking the table.

#### With bundle -- table moves as a unit

With `bundle(A4:E6)`, all columns A-E move as a single unit, so every column starts at the same row.

|     | A      | B     | C    | D    | E      |
|-----|--------|-------|------|------|--------|
| 2   | Dept A | 52000 |      |      |        |
| 3   | Dept B | 38000 |      |      |        |
| 4   | Dept C | 28000 |      |      |        |
| 5   |        |       |      |      |        |
| 6   | Name   | Revenue | Cost | Profit | Total |
| 7   | Yongho Hwang | 1000 | 500  | 500  | 2000   |
| 8   | Total  |       |      |      | =SUM() |

All columns A-E start at the same row, keeping the table intact.

### 8.3 Bundle as an Independent Region

A bundled region is treated like an independent sheet. Bundles can contain repeat, formulas, merged cells, and all other elements, with internal expansion and adjustment processed at the bundle level.

#### Repeat Inside a Bundle

When a repeat exists inside a bundle, the entire bundle height grows with the repeat expansion, and elements below the bundle shift accordingly.

**Template**

|     | A                             | B               | C    | D    | E                 |
|-----|-------------------------------|-----------------|------|------|-------------------|
| 1   | Summary                       |                 |      |      |                   |
| 2   | ${bundle(A3:E7)}              |                 |      |      |                   |
| 3   | Revenue by Department         |                 |      |      |                   |
| 4   | Department                    | Revenue         | Cost | Profit |                 |
| 5   | ${dept.name}                  | ${dept.revenue} | ${dept.cost} | ${dept.profit} | ${repeat(depts, A5:D5, dept)} |
| 6   |                               | =SUM(B5:B5)     | =SUM(C5:C5) | =SUM(D5:D5) |                   |
| 7   | Total Profit: =D6             |                 |      |      |                   |
| 8   | Notes                         |                 |      |      |                   |

- Rows 3-7: Bundled region (title, header, data, totals, summary)
- Row 5: Repeat inside the bundle
- Row 6: Totals formulas that auto-adjust with repeat expansion
- Row 8: Independent element below the bundle

**Result** (3 items in depts)

|     | A                        | B      | C      | D      | E |
|-----|--------------------------|--------|--------|--------|---|
| 1   | Summary                  |        |        |        |   |
| 2   |                          |        |        |        |   |
| 3   | Revenue by Department    |        |        |        |   |
| 4   | Department               | Revenue | Cost  | Profit |   |
| 5   | Sales                    | 52000  | 30000  | 22000  |   |
| 6   | Engineering              | 38000  | 25000  | 13000  |   |
| 7   | Support                  | 28000  | 20000  | 8000   |   |
| 8   |                          | =SUM(B5:B7) | =SUM(C5:C7) | =SUM(D5:D7) |   |
| 9   | Total Profit: =D8        |        |        |        |   |
| 10  | Notes                    |        |        |        |   |

- The repeat inside the bundle (row 5) expanded to 3 items, shifting the totals row (row 8) and summary row (row 9) down.
- The totals formula `=SUM(B5:B5)` was auto-adjusted to `=SUM(B5:B7)`.
- "Notes" (row 10) below the bundle also shifted according to the total bundle expansion.

#### Multiple Repeats Inside a Bundle

When a bundle contains multiple repeats, all their expansions are reflected. For example, if a single bundle contains both a department revenue repeat and a product revenue repeat, the total expansion of both repeats is summed to shift elements below the bundle.

### 8.4 Restrictions

- **No boundary overlap**: The common rule for all range elements applies (see [No Boundary Overlap](#no-boundary-overlap))
- **No nesting**: Bundles cannot be placed inside other bundles.

---

## 9. Formatting Preservation

All formatting applied to the template is preserved in the generated Excel file.

### 9.1 Preserved Formatting

- Cell alignment (horizontal/vertical)
- Font (name, size, bold, italic, underline, color)
- Fill color
- Borders
- Number formats
- Conditional formatting

### 9.2 Repeat Region Formatting

Formatting applied to the template rows within a repeat region is applied identically to all repeated rows.

---

## 10. Selective Field Visibility (hideable)

### 10.1 Basic Usage

Repeat fields can be selectively hidden as needed. Change the repeat field marker (e.g., `${emp.salary}`) to a hideable marker, and specify which fields to hide in the DataProvider. The corresponding area is automatically removed.

**Syntax**: `${hideable(value=field, bundle=range, mode=mode)}`

If the field in the marker cell is not a hide target, it behaves identically to a regular field (`${item.field}`).

#### Template

|   | A                                | B               | C                              | D             |
|---|----------------------------------|-----------------|--------------------------------|---------------|
| 1 | Name                             | Position        | Salary                         | Department    |
| 2 | ${emp.name}                      | ${emp.position} | ${hideable(emp.salary)}        | ${emp.dept}   |
| 3 | ${repeat(employees, A2:D2, emp)} |                 |                                |               |

#### Data

```kotlin
val provider = simpleDataProvider {
    items("employees", listOf(
        mapOf("name" to "Yongho Hwang", "position" to "Director", "salary" to 8000, "dept" to "Development"),
        mapOf("name" to "Yongho Han", "position" to "Manager", "salary" to 6500, "dept" to "Sales")
    ))
    hideFields("employees", "salary")
}
```

#### Result (salary hidden)

|   | A   | B  | C   |
|---|-----|----|-----|
| 1 | Name  | Position | Department  |
| 2 | Yongho Hwang | Director | Development |
| 3 | Yongho Han | Manager | Sales |

Column C (Salary) is removed and column D (Department) shifts left.

#### Result (not hidden)

When `hideFields` is not specified, the hideable marker behaves like a regular field.

|   | A   | B  | C     | D   |
|---|-----|----|-------|-----|
| 1 | Name  | Position | Salary    | Department  |
| 2 | Yongho Hwang | Director | 8,000 | Development |
| 3 | Yongho Han | Manager | 6,500 | Sales |

### 10.2 Bundle Range

By default, a hideable marker only hides the cell where the marker is located. Use the `bundle` parameter to specify a range so that related cells such as headers and totals are hidden together.

#### Template

|   | A                                | B               | C                                        | D             |
|---|----------------------------------|-----------------|------------------------------------------|---------------|
| 1 | ${repeat(employees, A3:D3, emp)} |                 |                                          |               |
| 2 | Name                             | Position        | Salary                                   | Department    |
| 3 | ${emp.name}                      | ${emp.position} | ${hideable(emp.salary, C2:C4)}           | ${emp.dept}   |
| 4 | Total                            |                 | =SUM(C3:C3)                              |               |

The bundle range `C2:C4` includes the field title (C2), data (C3), and total (C4). When hidden, this entire range is removed together.

> [!NOTE]
> The bundle range must include the hideable marker cell. If the marker cell is a merged cell, the column/row range of the bundle must match the merge range.

### 10.3 Hide Modes

The hideable marker supports two hide modes.

#### DELETE Mode (default)

Physically deletes the area and shifts the remaining elements. Formula references, merged cells, conditional formatting, etc. are automatically adjusted.

```
${hideable(emp.salary, C1:C3)}            // DELETE mode (default)
${hideable(emp.salary, C1:C3, delete)}    // DELETE mode (explicit)
=TBEG_HIDEABLE(emp.salary, C1:C3)         // Formula style
```

#### DIM Mode

Applies a disabled style (gray background + light text color) to cells in the repeat data area and clears cell values. For bundle range areas outside the repeat (such as field titles), only the text color is lightened while the background color and values are preserved.

```
${hideable(emp.salary, C1:C3, dim)}       // DIM mode
${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}  // Explicit parameters
=TBEG_HIDEABLE(emp.salary, C1:C3, dim)    // Formula style
```

| Mode | Behavior | Best For |
|------|----------|----------|
| `DELETE` | Deletes the area and shifts remaining elements | Completely removing unnecessary columns/rows |
| `DIM` | Applies disabled style + clears values | Hiding data while preserving the layout |

### 10.4 Specifying Hidden Fields in DataProvider

Hidden fields are specified via `ExcelDataProvider.getHiddenFields()`. In `SimpleDataProvider`, use the `hideFields()` method.

#### Kotlin DSL

```kotlin
val provider = simpleDataProvider {
    items("employees", employeeList)
    hideFields("employees", "salary", "age")
}
```

#### Java Builder

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .items("employees", employeeList)
    .hideFields("employees", "salary", "age")
    .build();
```

#### Custom DataProvider

```kotlin
class MyDataProvider : ExcelDataProvider {
    override fun getValue(name: String): Any? = /* ... */
    override fun getItems(name: String): Iterator<Any>? = /* ... */

    override fun getHiddenFields(collectionName: String): Set<String> =
        when (collectionName) {
            "employees" -> setOf("salary", "age")
            else -> emptySet()
        }
}
```

### 10.5 Parameter Reference

| Parameter | Required | Aliases | Default | Description |
|-----------|----------|---------|---------|-------------|
| `value` | Yes | `field`, `val` | - | Field to hide (`item.field` form, nesting supported) |
| `bundle` | No | `range` | Marker cell | Cell range to hide together |
| `mode` | No | - | `delete` | Hide mode (`delete` / `dim`) |

#### Positional Notation

```
${hideable(emp.salary)}                   // value only (hides that cell only)
${hideable(emp.salary, C1:C3)}            // value + bundle
${hideable(emp.salary, C1:C3, dim)}       // value + bundle + mode
```

#### Explicit Parameter Notation

```
${hideable(value=emp.salary)}
${hideable(value=emp.salary, bundle=C1:C3)}
${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}
${hideable(field=emp.salary)}             // Using alias
```

#### Formula Style

```
=TBEG_HIDEABLE(emp.salary)
=TBEG_HIDEABLE(emp.salary, C1:C3)
=TBEG_HIDEABLE(emp.salary, C1:C3, dim)
```

### 10.6 Validation Rules / Notes

- **Repeat field only**: The hideable marker can only be used on repeat item fields. Using it on a cell unrelated to a repeat causes an error.
- **Bundle must include marker**: The bundle range must include the hideable marker cell. Omitting it causes an error.
- **No partial merge overlap**: If the bundle range partially includes a merged cell, an error occurs.
- **No overlap between hideable areas**: Hideable areas (bundle ranges) cannot overlap each other. However, overlapping is allowed when both are in DIM mode.
- **Bundle range must match merge**: If the marker cell is a merged cell, the column/row range of the bundle must match the merge range.

### 10.7 Hiding Fields Without a Hideable Marker

Even if a field specified in `hideFields` exists in the template only as a regular field (`${item.field}`) without a hideable marker, hiding is still possible. In this case, the field cell is processed in DIM mode -- the value is cleared and a disabled style is applied. However, since there is no bundle range, related cells such as headers and totals are not affected.

Using the hideable marker's `bundle` parameter allows you to hide related cells together or remove the column entirely with DELETE mode, so using a hideable marker is recommended when possible.

This behavior can be controlled with the `unmarkedHidePolicy` setting ([see Configuration Options](./configuration.md#unmarkedhidepolicy)).

---

## Next Steps

- [API Reference](./api-reference.md) - Detailed class and method reference
- [Configuration](./configuration.md) - TbegConfig options
- [Basic Examples](../examples/basic-examples.md) - Various usage examples
