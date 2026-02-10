> **[한국어](../../ko/reference/template-syntax.md)** | English

# TBEG Template Syntax Reference

## Table of Contents
1. [Variable Substitution](#1-variable-substitution)
2. [Repeat Processing](#2-repeat-processing)
   - [Named Parameter Format](#26-named-parameter-format)
   - [Empty Collection Handling (empty)](#27-empty-collection-handling-empty)
   - [Automatic Adjustment of Related Elements](#28-automatic-adjustment-of-related-elements)
3. [Image Insertion](#3-image-insertion)
4. [Collection Size](#4-collection-size)
5. [Variables in Formulas](#5-variables-in-formulas)
6. [Formula-Style Markers](#6-formula-style-markers)

---

TBEG replaces markers (`${...}`, `=TBEG_...()`) in a template with data. Successfully processed markers do not appear in the output file.

---

## 1. Variable Substitution

### 1.1 Simple Variables

**Syntax**: `${variableName}`

Replaces `${variableName}` markers in the template with the corresponding key values from the data.

#### Template

|   | A      | B         |
|---|--------|-----------|
| 1 | 제목     | ${title}  |
| 2 | 작성일    | ${date}   |
| 3 | 작성자    | ${author} |

#### Data

```kotlin
mapOf(
    "title" to "월간 보고서",
    "date" to "2026-01-15",
    "author" to "황용호"
)
```

#### Result

|   | A   | B          |
|---|-----|------------|
| 1 | 제목  | 월간 보고서     |
| 2 | 작성일 | 2026-01-15 |
| 3 | 작성자 | 황용호        |

### 1.2 Composite Text

Multiple variables and text can be combined in a single cell.

```
작성자: ${author} (${department})
```

---

## 2. Repeat Processing

### 2.1 Basic Repeat (DOWN Direction)

**Syntax**: `${repeat(collection, range, variable)}`

Repeats the specified range downward for each item in the collection.

#### Template

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A2:C2, emp)}   |                 |               |
| 2 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

#### Data

```kotlin
data class Employee(val name: String, val position: String, val salary: Int)

mapOf(
    "employees" to listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500),
        Employee("홍용호", "대리", 4500)
    )
)
```

#### Result

|   | A   | B  | C     |
|---|-----|----|-------|
| 1 |     |    |       |
| 2 | 황용호 | 부장 | 8,000 |
| 3 | 한용호 | 과장 | 6,500 |
| 4 | 홍용호 | 대리 | 4,500 |

> **Marker placement**: The `${repeat(...)}` marker can be placed anywhere in the workbook (even on a different sheet) as long as it is outside the repeat range. The area specified by the range parameter is what gets repeated.

### 2.2 Multi-Row Repeat

Specifying multiple rows in the range causes them to repeat together as a group.

#### Template

|   | A                                | B                   |
|---|----------------------------------|---------------------|
| 1 | ${repeat(employees, A2:B3, emp)} |                     |
| 2 | 이름: ${emp.name}                  | 직급: ${emp.position} |
| 3 | 급여: ${emp.salary}                |                     |

### 2.3 Rightward Repeat (RIGHT)

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
| 1 |    | 1월     | 2월     | 3월     |
| 2 |    | 100    | 150    | 200    |

### 2.4 Multiple Repeat Regions

Multiple repeat regions can be used within a single sheet.

#### Separate Column Groups

Repeat regions located in different columns expand independently.

|   | A                                | B             | C | D                                   | E              |
|---|----------------------------------|---------------|---|-------------------------------------|----------------|
| 1 | ${repeat(employees, A2:B2, emp)} |               |   | ${repeat(departments, D2:E2, dept)} |                |
| 2 | ${emp.name}                      | ${emp.salary} |   | ${dept.name}                        | ${dept.budget} |

#### Restrictions

- Repeat regions must not overlap in 2D space (rows x columns)
- Multiple repeat regions can be stacked vertically within the same column range

### 2.5 Accessing Repeat Item Fields

**Syntax**: `${variable.field}`, `${variable.field.subfield}`

Use dot (.) notation to access fields of nested objects.

```
${emp.name}
${emp.department.name}
${emp.address.city}
```

#### Supported Types

- **Object fields**: Properties/fields of data classes or POJOs
- **Map keys**: Access by Map key
- **Getter methods**: Getters in the form `getFieldName()`

### 2.6 Named Parameter Format

All parameters of a repeat marker can be specified explicitly by name.

#### Syntax

```
${repeat(collection=collection, range=range, var=variable, direction=direction, empty=fallbackRange)}
```

#### Examples

```
${repeat(collection=employees, range=A2:C2, var=emp)}
${repeat(collection=months, range=B1:B2, var=m, direction=RIGHT)}
${repeat(collection=items, range=A3:C3, var=item, direction=DOWN, empty=A10:C10)}
```

#### Formula Style

```
=TBEG_REPEAT(collection=employees, range=A2:C2, var=emp)
=TBEG_REPEAT(collection=items, range=A3:C3, var=item, direction=DOWN, empty=A10:C10)
```

#### Omitting Parameters

In the named format, optional parameters can be omitted entirely, set to `NULL`, or left as an empty value. The following three are all equivalent.

```
${repeat(collection=items, range=A2:C2, empty=A10:C10)}           // var omitted
${repeat(collection=items, range=A2:C2, var=NULL, empty=A10:C10)} // var=NULL
${repeat(collection=items, range=A2:C2, var=, empty=A10:C10)}     // var= (empty value)
```

#### Mixing Not Allowed

Positional and named parameters cannot be mixed. Once any parameter is named, all parameters must be named.

```
// Correct
${repeat(items, A2:C2, item, DOWN, A10:C10)}                                    // All positional
${repeat(collection=items, range=A2:C2, var=item, direction=DOWN, empty=A10:C10)} // All named

// Incorrect (causes an error)
${repeat(items, A2:C2, item, empty=A10:C10)}         // Cannot mix
${repeat(items, A2:C2, var=item, direction=DOWN)}   // Cannot mix
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

### 2.7 Empty Collection Handling (empty)

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
| 10  | (데이터가 없습니다)                                     |                 |               |

#### Data (empty collection)

```kotlin
mapOf("employees" to emptyList<Employee>())
```

#### Result

|   | A           | B | C |
|---|-------------|---|---|
| 1 |             |   |   |
| 2 | (데이터가 없습니다) |   |   |

> **Note**:
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
| 10  | 데이터가 없습니다                                   |                 |               |

- **A10**: Write the message in a single cell (specify only A10, not A10:C10)

**Result (empty collection)**

<table>
  <tr><th></th><th>A</th><th>B</th><th>C</th></tr>
  <tr><td>1</td><td></td><td></td><td></td></tr>
  <tr><td>2</td><td colspan="3" style="text-align:center">데이터가 없습니다</td></tr>
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

### 2.8 Automatic Adjustment of Related Elements

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
| 3 | 합계                               | =SUM(B2:B2)   |

**Result** (3 items)

|   | A   | B           |
|---|-----|-------------|
| 1 |     |             |
| 2 | 항목1 | 100         |
| 3 | 항목2 | 200         |
| 4 | 항목3 | 300         |
| 5 | 합계  | =SUM(B2:B4) |

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

## 3. Image Insertion

### 3.1 Basic Image

**Syntax**: `${image(name)}`

Inserts an image into the cell (or merged region) containing the marker.

#### Template

|   | A              | B               |
|---|----------------|-----------------|
| 1 | ${image(logo)} | 회사명: ${company} |
| 2 | (병합된 셀)        |                 |

#### Data

```kotlin
val provider = simpleDataProvider {
    value("company", "(주)휴넷")
    image("logo", logoBytes)
}
```

### 3.2 Specifying Position

**Syntax**: `${image(name, position)}`

Specifies the cell position where the image should be inserted.

```
${image(logo, B2)}      // Insert at cell B2
${image(stamp, D5:F8)}  // Insert within the D5:F8 range
```

### 3.3 Specifying Size

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

### 3.4 Named Parameter Format

All parameters can be specified explicitly by name.

```
${image(name=logo)}
${image(name=logo, position=B2)}
${image(name=logo, position=B2:D4, size=fit)}
${image(name=logo, size=200:150)}  // position omitted
```

Formula style:
```
=TBEG_IMAGE(name=logo)
=TBEG_IMAGE(name=logo, position=B2, size=original)
```

---

## 4. Collection Size

**Syntax**: `${size(collection)}` or `${size(collection=collection)}`

Displays the number of items in a collection.

#### Template

|   | A                           |
|---|-----------------------------|
| 1 | 총 직원 수: ${size(employees)}명 |

#### Result (when employees has 5 entries)

|   | A          |
|---|------------|
| 1 | 총 직원 수: 5명 |

---

## 5. Variables in Formulas

Variables can be used within Excel formulas.

### 5.1 HYPERLINK

**Syntax**: `=HYPERLINK("${url}", "${text}")`

```
=HYPERLINK("${linkUrl}", "${linkText}")
```

### 5.2 Dynamic Ranges

**Syntax**: `=SUM(B${startRow}:B${endRow})`

Variables can be used in cell references within formulas to create dynamic ranges.

#### Template

|   | A  | B                               |
|---|----|---------------------------------|
| 1 | 시작 | ${startRow}                     |
| 2 | 끝  | ${endRow}                       |
| 3 | 합계 | =SUM(B\${startRow}:B\${endRow}) |

#### Data

```kotlin
mapOf("startRow" to 5, "endRow" to 10)
```

#### Result

|   | A      | B              |
|---|--------|----------------|
| 1 | 시작     | 5              |
| 2 | 끝      | 10             |
| 3 | 합계     | =SUM(B5:B10)   |

---

## 6. Formula-Style Markers

Some markers can also be written in formula form.

| Text Form                        | Formula Form                      |
|----------------------------------|-----------------------------------|
| `${repeat(col, range, var)}`     | `=TBEG_REPEAT(col, range, var)`   |
| `${image(name)}`                 | `=TBEG_IMAGE(name)`               |
| `${size(col)}`                   | `=TBEG_SIZE(col)`                 |

**Advantage**: When specifying ranges or cells, you can leverage Excel's cell reference features (clicking, drag-selecting, etc.).

> **Note**: Formula-style markers display as `#NAME?` errors in Excel. This is expected and they are processed correctly during generation.

### 6.1 Formula-Style Repeat Markers

```
=TBEG_REPEAT(employees, A2:C2, emp)
=TBEG_REPEAT(months, B1:B2, m, RIGHT)
```

### 6.2 Formula-Style Image Markers

```
=TBEG_IMAGE(logo)
=TBEG_IMAGE(logo, B2:D4)
=TBEG_IMAGE(logo, B2, 200:150)
```

### 6.3 Formula-Style Size Markers

```
=TBEG_SIZE(employees)
```

---

## Formatting Preservation

All formatting applied to the template is preserved in the generated Excel file.

### Preserved Formatting

- Cell alignment (horizontal/vertical)
- Font (name, size, bold, italic, underline, color)
- Fill color
- Borders
- Number formats
- Conditional formatting

### Repeat Region Formatting

Formatting applied to the template rows within a repeat region is applied identically to all repeated rows.

---

## Next Steps

- [API Reference](./api-reference.md) - Detailed class and method reference
- [Configuration](./configuration.md) - TbegConfig options
- [Basic Examples](../examples/basic-examples.md) - Various usage examples
