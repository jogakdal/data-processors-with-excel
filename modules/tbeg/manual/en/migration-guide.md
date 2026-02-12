> **[한국어](../ko/migration-guide.md)** | English

# TBEG Migration Guide

## 1.0.x to 1.1.0

### Class Renaming

`ExcelGeneratorConfig` has been renamed to `TbegConfig`. The old name is still available as a type alias, so existing code continues to work.

**Recommended**: Migrate to the new name. The type alias may be removed in a future release.

```kotlin
// Old code (works but not recommended)
val config = ExcelGeneratorConfig(streamingMode = StreamingMode.ENABLED)

// Recommended
val config = TbegConfig(streamingMode = StreamingMode.ENABLED)
```

---

### New Features

#### Empty collection handling (`empty` parameter)

Adding the `empty` parameter to a repeat marker displays alternative content when the collection is empty.

```
${repeat(employees, A2:C2, emp, DOWN, A10:C10)}
```

See: [Template Syntax - Empty Collection Handling](./reference/template-syntax.md#27-empty-collection-handling-empty)

#### Multiple independent repeat areas on the same row

You can place multiple repeat areas on a single row as long as their column ranges do not overlap.

```
| ${repeat(employees, A2:B2, emp)} | | ${repeat(departments, D2:E2, dept)} |
```

See: [Template Syntax - Multiple Repeat Regions](./reference/template-syntax.md#24-multiple-repeat-regions)

#### Cross-sheet formula reference expansion

Formulas referencing repeat areas on other sheets are automatically expanded.

```
=SUM(Sheet2!B3:B3) → =SUM(Sheet2!B3:B5)  // When repeat on Sheet2 expanded by 3 rows
```

See: [Template Syntax - Automatic Adjustment of Related Elements](./reference/template-syntax.md#28-automatic-adjustment-of-related-elements)

---

## 1.1.0 to 1.1.1

### Bug Fixes

Version 1.1.1 is a bug fix release for multi-repeat area handling. If you are using 1.1.0, upgrading is recommended.

- **Multiple independent repeat areas on the same row**: Fixed a bug where some areas were omitted or non-repeat cells were rendered duplicately
- **Multiple repeat variable recognition**: Improved recognition of all repeat variables on the same row

### New Features

- **Duplicate marker detection**: A warning log is emitted when duplicate repeat markers (same collection + same target range) or duplicate image markers (same name + same position + same size) are detected

### How to Upgrade

Simply update the dependency version. There are no API changes.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

---

## Next Steps

- [Changelog](../CHANGELOG.md) - Full version history
- [User Guide](./user-guide.md) - How to use TBEG
