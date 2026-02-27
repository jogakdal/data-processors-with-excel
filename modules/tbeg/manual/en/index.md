> **[한국어](../ko/index.md)** | English

# TBEG (Template-Based Excel Generator)

A template-based Excel file generation library

> For project introduction and quick start, see the [README](../README.md).

---

## Where to Start

### I'm using TBEG for the first time
1. Try generating your first Excel file with the [Quick Start in README](../README.md#quick-start)
2. Learn the core concepts in the [User Guide](./user-guide.md)
3. Explore various usage patterns in the [Basic Examples](./examples/basic-examples.md)

### I want to integrate with Spring Boot
1. Check the integration guide in the [Spring Boot Examples](./examples/spring-boot-examples.md)
2. Review `application.yml` settings in [Configuration Options](./reference/configuration.md)
3. See [Advanced Examples - JPA Integration](./examples/advanced-examples.md#13-jpaspring-data-integration) for database connectivity

### I need to process large datasets
1. See [User Guide - Large-Scale Data Processing](./user-guide.md#5-large-scale-data-processing)
2. Explore lazy loading patterns in [Advanced Examples - DataProvider](./examples/advanced-examples.md#1-dataprovider-usage)
3. Follow the step-by-step guide in [Best Practices - Performance Optimization](./best-practices.md#2-performance-optimization)

### I'm working with complex templates
1. Review the full marker syntax in [Template Syntax](./reference/template-syntax.md)
2. See real-world patterns in [Advanced Examples](./examples/advanced-examples.md)
3. Check common issues in [Troubleshooting](./troubleshooting.md)

### I want to understand the internals
1. Study the architecture and pipeline in the [Developer Guide](./developer-guide.md)

---

## Documentation

### User Guide
- [User Guide](./user-guide.md) - Complete guide to using TBEG

### Reference
- [Template Syntax](./reference/template-syntax.md) - Syntax available in templates
- [API Reference](./reference/api-reference.md) - Class and method details
- [Configuration Options](./reference/configuration.md) - TbegConfig options

### Examples
- [Basic Examples](./examples/basic-examples.md) - Simple usage examples
- [Advanced Examples](./examples/advanced-examples.md) - Large-scale processing, async processing, etc.
- [Spring Boot Examples](./examples/spring-boot-examples.md) - Spring Boot integration

### Operations Guide
- [Best Practices](./best-practices.md) - Template design, performance optimization, error prevention
- [Troubleshooting](./troubleshooting.md) - Common issues and solutions
- [Migration Guide](./migration-guide.md) - Version upgrade instructions

### Developer Guide
- [Developer Guide](./developer-guide.md) - Internal architecture and extension methods

### Appendix
- [Library Comparison](./appendix/library-comparison.md) - Feature comparison across Excel report libraries

---

## Compatibility

| Item | Value |
|------|-------|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| Package | `io.github.jogakdal.tbeg` |
| Java | 21 or later |
| Kotlin | 2.0 or later |
| Apache POI | 5.2.5 (transitive dependency) |
| Spring Boot | 3.x (optional) |
| Author | [Yongho Hwang (황용호)](https://github.com/jogakdal) (jogakdal@gmail.com) |
