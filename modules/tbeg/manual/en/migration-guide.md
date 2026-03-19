> **[한국어](../ko/migration-guide.md)** | English

# TBEG Migration Guide

## 1.2.1 to 1.2.2

### New Feature: Selective Field Visibility (hideable)

A new feature has been added that allows restricting the visibility of specific fields based on context.

- New marker: `${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}`
- New API: `ExcelDataProvider.getHiddenFields()` (default implementation provided, no impact on existing code)
- New API: `SimpleDataProvider.Builder.hideFields()`
- New setting: `TbegConfig.unmarkedHidePolicy`
- **No breaking changes**: Existing code works without modification

### How to Upgrade

Simply update the dependency version.

```kotlin
// gradle.properties
moduleVersion.tbeg=1.2.2
```

---

## 1.1.x to 1.2.0

### StreamingMode Removed

Starting from 1.2.0, TBEG always operates in streaming mode. The existing `StreamingMode.DISABLED` setting is ignored.

**Migration:**
- `TbegConfig(streamingMode = StreamingMode.DISABLED)` -> `TbegConfig()` (or `TbegConfig.default()`)
- `TbegConfig.forSmallData()` -> `TbegConfig.default()`
- Spring setting `streaming-mode: disabled` -> Remove the setting
- `TemplateRenderingEngine(StreamingMode.xxx)` -> `TemplateRenderingEngine()`

The deprecated APIs are retained for backward compatibility but will be removed in a future version.

---

## 1.1.1 to 1.1.2

### Bug Fixes

Version 1.1.2 is a bug fix release for non-repeat area handling and cross-sheet marker grouping.

### How to Upgrade

Simply update the dependency version. There are no API changes.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.2")
}
```

---

## 1.0.x to 1.1.0

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
=AVERAGE(Sheet2!B3:B3) -> =AVERAGE(Sheet2!B3:B5)  // When repeat on Sheet2 expanded by 3 rows
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
- [Best Practices](./best-practices.md) - Tips for using the latest features
- [User Guide](./user-guide.md) - How to use TBEG
