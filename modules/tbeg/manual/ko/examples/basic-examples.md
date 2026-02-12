# TBEG 기본 예제

## 목차
1. [간단한 보고서 생성](#1-간단한-보고서-생성)
2. [반복 데이터 (리스트)](#2-반복-데이터-리스트)
3. [이미지 삽입](#3-이미지-삽입)
4. [파일로 저장](#4-파일로-저장)
5. [암호 설정](#5-암호-설정)
6. [문서 메타데이터](#6-문서-메타데이터)

---

## 1. 간단한 보고서 생성

### 템플릿 (template.xlsx)

|   | A    | B         |
|---|------|-----------|
| 1 | 제목   | ${title}  |
| 2 | 작성일  | ${date}   |
| 3 | 작성자  | ${author} |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File
import java.time.LocalDate

fun main() {
    val data = mapOf(
        "title" to "월간 보고서",
        "date" to LocalDate.now().toString(),
        "author" to "황용호"
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java 코드

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class SimpleReport {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "월간 보고서");
        data.put("date", LocalDate.now().toString());
        data.put("author", "황용호");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx");
             FileOutputStream output = new FileOutputStream("output.xlsx")) {

            byte[] bytes = generator.generate(template, data);
            output.write(bytes);
        }
    }
}
```

### 결과


|   | A    | B          |
|---|------|------------|
| 1 | 제목   | 월간 보고서     |
| 2 | 작성일  | 2026-01-15 |
| 3 | 작성자  | 황용호        |

---

## 2. 반복 데이터 (리스트)

### 템플릿 (employee_list.xlsx)

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | 이름                                 | 직급              | 연봉            |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("한용호", "과장", 6500),
            Employee("홍용호", "대리", 4500),
            Employee("김용호", "사원", 3500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("employee_list.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java 코드

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.util.*;

public class EmployeeList {

    public record Employee(String name, String position, int salary) {}

    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("employees", List.of(
            new Employee("황용호", "부장", 8000),
            new Employee("한용호", "과장", 6500),
            new Employee("홍용호", "대리", 4500),
            new Employee("김용호", "사원", 3500)
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

### 결과


|   | A    | B  | C     |
|---|------|----|-------|
| 1 |      |    |       |
| 2 | 이름   | 직급 | 연봉    |
| 3 | 황용호  | 부장 | 8,000 |
| 4 | 한용호  | 과장 | 6,500 |
| 5 | 홍용호  | 대리 | 4,500 |
| 6 | 김용호  | 사원 | 3,500 |

---

## 3. 이미지 삽입

### 템플릿 (with_logo.xlsx)

|   | A             | B                |
|---|---------------|------------------|
| 1 | ${image(logo)}| 회사명: ${company} |
| 2 | (병합된 셀)       | 주소: ${address}   |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

fun main() {
    val logoBytes = File("logo.png").readBytes()

    val provider = simpleDataProvider {
        value("company", "(주)휴넷")
        value("address", "서울시 강남구")
        image("logo", logoBytes)
    }

    ExcelGenerator().use { generator ->
        val template = File("with_logo.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java 코드

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
            .value("company", "(주)휴넷")
            .value("address", "서울시 강남구")
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

## 4. 파일로 저장

`generateToFile()` 메서드를 사용하면 자동으로 타임스탬프가 붙은 파일명으로 저장됩니다.

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.SimpleDataProvider
import java.io.File
import java.nio.file.Path

fun main() {
    val data = mapOf(
        "title" to "보고서",
        "content" to "내용..."
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        val outputPath = generator.generateToFile(
            template = template,
            dataProvider = SimpleDataProvider.of(data),
            outputDir = Path.of("./output"),
            baseFileName = "report"
        )

        println("파일 생성됨: $outputPath")
        // 출력: 파일 생성됨: ./output/report_20260115_143052.xlsx
    }
}
```

### Java 코드

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class SaveToFile {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "보고서");
        data.put("content", "내용...");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            Path outputPath = generator.generateToFile(
                template,
                SimpleDataProvider.of(data),
                Path.of("./output"),
                "report"
            );

            System.out.println("파일 생성됨: " + outputPath);
        }
    }
}
```

---

## 5. 암호 설정

생성된 Excel 파일에 열기 암호를 설정할 수 있습니다.

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

fun main() {
    val data = mapOf(
        "title" to "기밀 보고서",
        "content" to "중요 내용..."
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        // 암호 설정
        val bytes = generator.generate(
            template = template,
            data = data,
            password = "myPassword123"
        )

        File("secured_output.xlsx").writeBytes(bytes)
        println("암호로 보호된 파일이 생성되었습니다.")
    }
}
```

### Java 코드

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.util.*;

public class PasswordProtected {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "기밀 보고서");
        data.put("content", "중요 내용...");

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            // 암호 설정
            byte[] bytes = generator.generate(template, data, "myPassword123");

            try (FileOutputStream output = new FileOutputStream("secured_output.xlsx")) {
                output.write(bytes);
            }
            System.out.println("암호로 보호된 파일이 생성되었습니다.");
        }
    }
}
```

---

## 6. 문서 메타데이터

Excel 파일의 문서 속성(제목, 작성자, 키워드 등)을 설정할 수 있습니다.

### Kotlin 코드 (DSL)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.time.LocalDateTime

fun main() {
    val provider = simpleDataProvider {
        value("title", "보고서 내용")
        value("author", "황용호")

        // 문서 메타데이터 설정
        metadata {
            title = "2026년 1월 월간 보고서"
            author = "황용호"
            subject = "월간 실적"
            keywords("월간", "보고서", "2026년", "실적")
            description = "2026년 1월 월간 실적 보고서입니다."
            category = "업무 보고"
            company = "(주)휴넷"
            manager = "홍상무"
            created = LocalDateTime.now()
        }
    }

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
        println("문서 속성이 설정된 파일이 생성되었습니다.")
        println("Excel에서 '파일 > 정보 > 속성'에서 확인할 수 있습니다.")
    }
}
```

### Java 코드 (Builder)

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.time.LocalDateTime;

public class WithMetadata {
    public static void main(String[] args) throws Exception {
        SimpleDataProvider provider = SimpleDataProvider.builder()
            .value("title", "보고서 내용")
            .value("author", "황용호")
            .metadata(meta -> meta
                .title("2026년 1월 월간 보고서")
                .author("황용호")
                .subject("월간 실적")
                .keywords("월간", "보고서", "2026년", "실적")
                .description("2026년 1월 월간 실적 보고서입니다.")
                .category("업무 보고")
                .company("(주)휴넷")
                .manager("홍상무")
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

### 설정 가능한 메타데이터

| 속성 | 설명 | Excel 위치 |
|------|------|-----------|
| title | 문서 제목 | 파일 > 정보 > 제목 |
| author | 작성자 | 파일 > 정보 > 작성자 |
| subject | 주제 | 파일 > 정보 > 주제 |
| keywords | 키워드 | 파일 > 정보 > 태그 |
| description | 설명 | 파일 > 정보 > 메모 |
| category | 범주 | 파일 > 정보 > 범주 |
| company | 회사 | 파일 > 정보 > 회사 |
| manager | 관리자 | 파일 > 정보 > 관리자 |
| created | 작성 일시 | 파일 > 정보 > 만든 날짜 |

---

## 다음 단계

- [고급 예제](./advanced-examples.md) - 대용량 처리, 비동기 처리 등
- [Spring Boot 예제](./spring-boot-examples.md) - Spring Boot 환경 예제
- [템플릿 문법 레퍼런스](../reference/template-syntax.md) - 상세 문법
- [모범 사례](../best-practices.md) - 템플릿 설계 및 성능 최적화
