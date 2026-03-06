> **[한국어](../../ko/examples/basic-examples.md)** | English

# TBEG Basic Examples

## Table of Contents
1. [Simple Report Generation](#1-simple-report-generation)
2. [Repeated Data (Lists)](#2-repeated-data-lists)
3. [Image Insertion](#3-image-insertion)
4. [Saving to File](#4-saving-to-file)
5. [Password Protection](#5-password-protection)
6. [Document Metadata](#6-document-metadata)
7. [Automatic Cell Merge](#7-automatic-cell-merge)

---

## 1. Simple Report Generation

### Template (template.xlsx)

|   | A    | B         |
|---|------|-----------|
| 1 | Title  | ${title}  |
| 2 | Date   | ${date}   |
| 3 | Author | ${author} |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File
import java.time.LocalDate

fun main() {
    val data = mapOf(
        "title" to "Monthly Report",
        "date" to LocalDate.now().toString(),
        "author" to "Yongho Hwang"
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class SimpleReport {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Monthly Report");
        data.put("date", LocalDate.now().toString());
        data.put("author", "Yongho Hwang");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx");
             FileOutputStream output = new FileOutputStream("output.xlsx")) {

            byte[] bytes = generator.generate(template, data);
            output.write(bytes);
        }
    }
}
```

### Result

|   | A    | B          |
|---|------|------------|
| 1 | Title  | Monthly Report |
| 2 | Date   | 2026-01-15 |
| 3 | Author | Yongho Hwang   |

---

## 2. Repeated Data (Lists)

### Template (employee_list.xlsx)

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | Name                                 | Position          | Salary          |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("Yongho Hwang", "Director", 8000),
            Employee("Yongho Han", "Manager", 6500),
            Employee("Yongho Hong", "Assistant Manager", 4500),
            Employee("Yongho Kim", "Staff", 3500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("employee_list.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.util.*;

public class EmployeeList {

    public record Employee(String name, String position, int salary) {}

    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("employees", List.of(
            new Employee("Yongho Hwang", "Director", 8000),
            new Employee("Yongho Han", "Manager", 6500),
            new Employee("Yongho Hong", "Assistant Manager", 4500),
            new Employee("Yongho Kim", "Staff", 3500)
        ));

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("employee_list.xlsx")) {

            byte[] bytes = generator.generate(template, data);
            try (FileOutputStream output = new FileOutputStream("output.xlsx")) {
                output.write(bytes);
            }
        }
    }
}
```

### Result

|   | A    | B  | C     |
|---|------|----|-------|
| 1 |      |    |       |
| 2 | Name          | Position          | Salary  |
| 3 | Yongho Hwang  | Director          | 8,000 |
| 4 | Yongho Han    | Manager           | 6,500 |
| 5 | Yongho Hong   | Assistant Manager | 4,500 |
| 6 | Yongho Kim    | Staff             | 3,500 |

---

## 3. Image Insertion

### Template (with_logo.xlsx)

|   | A             | B                |
|---|---------------|------------------|
| 1 | ${image(logo)}| Company: ${company} |
| 2 | (merged cell) | Address: ${address}   |

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

fun main() {
    val logoBytes = File("logo.png").readBytes()

    val provider = simpleDataProvider {
        value("company", "Hunet Inc.")
        value("address", "Gangnam-gu, Seoul")
        image("logo", logoBytes)
    }

    ExcelGenerator().use { generator ->
        val template = File("with_logo.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class WithLogo {
    public static void main(String[] args) throws Exception {
        byte[] logoBytes = Files.readAllBytes(Path.of("logo.png"));

        SimpleDataProvider provider = SimpleDataProvider.builder()
            .value("company", "Hunet Inc.")
            .value("address", "Gangnam-gu, Seoul")
            .image("logo", logoBytes)
            .build();

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("with_logo.xlsx")) {

            byte[] bytes = generator.generate(template, provider);
            try (FileOutputStream output = new FileOutputStream("output.xlsx")) {
                output.write(bytes);
            }
        }
    }
}
```

---

## 4. Saving to File

Using the `generateToFile()` method automatically saves the file with a timestamped filename.

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.SimpleDataProvider
import java.io.File
import java.nio.file.Path

fun main() {
    val data = mapOf(
        "title" to "Report",
        "content" to "Content..."
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        val outputPath = generator.generateToFile(
            template = template,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = Path.of("./output"),
            baseFileName = "report"
        )

        println("File created: $outputPath")
        // Output: File created: ./output/report_20260115_143052.xlsx
    }
}
```

### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class SaveToFile {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Report");
        data.put("content", "Content...");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            Path outputPath = generator.generateToFile(
                template,
                SimpleDataProvider.of(data),
                Path.of("./output"),
                "report"
            );

            System.out.println("File created: " + outputPath);
        }
    }
}
```

---

## 5. Password Protection

You can set an open password on the generated Excel file.

### Kotlin Code

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

fun main() {
    val data = mapOf(
        "title" to "Confidential Report",
        "content" to "Important content..."
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        // Set password
        val bytes = generator.generate(
            template = template,
            data = data,
            password = "myPassword123"
        )

        File("secured_output.xlsx").writeBytes(bytes)
        println("Password-protected file has been created.")
    }
}
```

### Java Code

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.util.*;

public class PasswordProtected {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "Confidential Report");
        data.put("content", "Important content...");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            // Set password
            byte[] bytes = generator.generate(template, data, "myPassword123");

            try (FileOutputStream output = new FileOutputStream("secured_output.xlsx")) {
                output.write(bytes);
            }
            System.out.println("Password-protected file has been created.");
        }
    }
}
```

---

## 6. Document Metadata

You can set document properties (title, author, keywords, etc.) on the Excel file.

### Kotlin Code (DSL)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.time.LocalDateTime

fun main() {
    val provider = simpleDataProvider {
        value("title", "Report Content")
        value("author", "Yongho Hwang")

        // Set document metadata
        metadata {
            title = "January 2026 Monthly Report"
            author = "Yongho Hwang"
            subject = "Monthly Performance"
            keywords("monthly", "report", "2026", "performance")
            description = "January 2026 monthly performance report."
            category = "Business Report"
            company = "Hunet Inc."
            manager = "Sangmu Hong"
            created = LocalDateTime.now()
        }
    }

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
        println("File with document properties has been created.")
        println("You can verify them in Excel under 'File > Info > Properties'.")
    }
}
```

### Java Code (Builder)

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.time.LocalDateTime;

public class WithMetadata {
    public static void main(String[] args) throws Exception {
        SimpleDataProvider provider = SimpleDataProvider.builder()
            .value("title", "Report Content")
            .value("author", "Yongho Hwang")
            .metadata(meta -> meta
                .title("January 2026 Monthly Report")
                .author("Yongho Hwang")
                .subject("Monthly Performance")
                .keywords("monthly", "report", "2026", "performance")
                .description("January 2026 monthly performance report.")
                .category("Business Report")
                .company("Hunet Inc.")
                .manager("Sangmu Hong")
                .created(LocalDateTime.now()))
            .build();

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            byte[] bytes = generator.generate(template, provider);
            try (FileOutputStream output = new FileOutputStream("output.xlsx")) {
                output.write(bytes);
            }
        }
    }
}
```

### Available Metadata Properties

| Property | Description | Excel Location |
|----------|-------------|----------------|
| title | Document title | File > Info > Title |
| author | Author | File > Info > Author |
| subject | Subject | File > Info > Subject |
| keywords | Keywords | File > Info > Tags |
| description | Description | File > Info > Comments |
| category | Category | File > Info > Category |
| company | Company | File > Info > Company |
| manager | Manager | File > Info > Manager |
| created | Created date/time | File > Info > Created |

---

## 7. Automatic Cell Merge

Automatically merges consecutive cells with the same value in repeated data.

### Template (merge_report.xlsx)

|   | A                    | B           | C           | D                                |
|---|----------------------|-------------|-------------|----------------------------------|
| 1 | Department           | Name        | Position    | ${repeat(employees, A2:C2, emp)} |
| 2 | ${merge(emp.dept)}   | ${emp.name} | ${emp.rank} |                                  |

### Kotlin Code

```kotlin
val data = mapOf(
    "employees" to listOf(
        mapOf("dept" to "Sales", "name" to "Yongho Hwang", "rank" to "Staff"),
        mapOf("dept" to "Sales", "name" to "Yongho Han", "rank" to "Assistant Manager"),
        mapOf("dept" to "Development", "name" to "Yongho Hong", "rank" to "Manager"),
        mapOf("dept" to "Development", "name" to "Yongho Heo", "rank" to "Staff"),
        mapOf("dept" to "Development", "name" to "Yongho Kim", "rank" to "Assistant Manager"),
    )
)

ExcelGenerator().use { generator ->
    val result = generator.generate(templateStream, data)
    File("merge_report.xlsx").writeBytes(result)
}
```

### Result

<table>
  <tr><th></th><th>A</th><th>B</th><th>C</th></tr>
  <tr><td>1</td><td>Department</td><td>Name</td><td>Position</td></tr>
  <tr><td>2</td><td rowspan="2">Sales</td><td>Yongho Hwang</td><td>Staff</td></tr>
  <tr><td>3</td><td>Yongho Han</td><td>Assistant Manager</td></tr>
  <tr><td>4</td><td rowspan="3">Development</td><td>Yongho Hong</td><td>Manager</td></tr>
  <tr><td>5</td><td>Yongho Heo</td><td>Staff</td></tr>
  <tr><td>6</td><td>Yongho Kim</td><td>Assistant Manager</td></tr>
</table>

> Cells A2:A3 are automatically merged as "Sales", and A4:A6 as "Development".
> The data must be pre-sorted by the merge criteria (department).

---

## Next Steps

- [Advanced Examples](./advanced-examples.md) - Large-scale processing, asynchronous processing, etc.
- [Spring Boot Examples](./spring-boot-examples.md) - Spring Boot integration examples
- [Template Syntax Reference](../reference/template-syntax.md) - Detailed syntax
- [Best Practices](../best-practices.md) - Template design and performance optimization
