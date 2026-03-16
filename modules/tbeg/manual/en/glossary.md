> **[한국어](../ko/glossary.md)** | English

# TBEG Glossary

Key terms used in the TBEG documentation.

---

## Marker

A special expression placed in Excel template cells. TBEG substitutes markers with actual data to generate Excel files.

- **Variable markers**: In the form `${variableName}`. Used for simple variable substitution. Examples: `${title}`, `${emp.name}`
- **Function markers**: In the form `${function(parameters)}`. Performs special functions such as repeat, image, and merge. Example: `${repeat(employees, A2:C2, emp)}`

## Repeat Region

The cell range specified by a `${repeat(...)}` marker. This range is duplicated and expanded for each item in the collection. By default, it expands downward (DOWN), and rightward (RIGHT) expansion is also supported.

## Bundle

A cell range grouping specified by the `${bundle(range)}` marker. Elements within a bundle are treated as a single unit and operate independently, unaffected by the expansion of other repeat regions. Primarily used to protect layout when multiple repeat regions exist on the same sheet.

## DataProvider

An interface (`ExcelDataProvider`) that provides data to bind to Excel templates. Using it instead of a simple `Map<String, Any>` enables advanced data provision strategies such as lazy loading and large-scale streaming.

- **SimpleDataProvider**: The default implementation of `ExcelDataProvider`. Created conveniently via DSL or Builder pattern.
- **Custom DataProvider**: Directly implements the `ExcelDataProvider` interface to integrate specialized data sources such as DB streaming or external API calls.

## Selective Field Visibility (Hideable)

A feature that restricts the visibility of specific fields depending on context. Place `${hideable(...)}` markers in the template and specify which fields to hide using `hideFields()` in code.

- **DELETE mode**: Physically deletes the area and shifts remaining elements (default).
- **DIM mode**: Applies a deactivation style (gray background + light text color) to the data area and removes values. Bundle areas outside the repeat range (such as field titles) only have their text color lightened. Layout is preserved.

## Cell Merge

A feature that automatically merges consecutive cells with the same value after repeat expansion, using the `${merge(item.field)}` marker. Data must be pre-sorted by the merge key field for correct merging.

## Formula Binding

A feature that places `${variableName}` markers in Excel formula cells to dynamically substitute parts of formulas. For example, you can bind variables to formula arguments like `=HYPERLINK(${emp.url}, ${emp.name})`.

## Formula Adjustment

A feature that automatically updates formula cell reference ranges when repeat regions expand. For example, `=SUM(B2:B2)` is automatically adjusted to `=SUM(B2:B101)` when expanded to 100 rows.

## Lazy Loading

A pattern where data is not pre-loaded into memory by the DataProvider, but instead loaded by invoking a Lambda/Supplier when TBEG actually needs the data. This significantly reduces memory usage when processing large datasets.

## Count

The total number of items in a collection. When count is provided upfront through the DataProvider, TBEG avoids double-traversing the data, improving performance. If count is not provided, TBEG first traverses the data to determine the count.

## Template

The original Excel file (.xlsx) that TBEG binds data to. You can freely use Excel-native features such as formatting, formulas, conditional formatting, and charts, placing markers at positions where data should be inserted.

---

## Next Steps

- [Template Syntax Reference](./reference/template-syntax.md) - Detailed marker syntax
- [API Reference](./reference/api-reference.md) - Class and method details
- [User Guide](./user-guide.md) - Complete usage guide
