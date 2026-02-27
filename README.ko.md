> 한국어 | **[English](./README.md)**

# TBEG - Template Based Excel Generator

[![CI](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml/badge.svg)](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 JVM 라이브러리입니다. Kotlin과 Java에서 동일하게 사용할 수 있습니다.

## 주요 기능

- **템플릿 기반 생성**: Excel 템플릿에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리**: `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환**: `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩
- **이미지 삽입**: 템플릿 셀에 동적 이미지 삽입
- **수식 자동 조정**: 데이터 확장 시 SUM, AVERAGE 등 수식 범위를 자동 갱신
- **조건부 서식 복제**: 반복 행에 원본의 조건부 서식을 자동 적용
- **차트 데이터 반영**: 확장된 데이터 범위를 차트에 자동 반영
- **파일 암호화**: 생성된 Excel 파일에 열기 암호 설정
- **문서 메타데이터**: 제목, 작성자, 키워드 등 문서 속성 설정
- **비동기 처리**: 대용량 데이터를 백그라운드에서 처리
- **지연 로딩**: DataProvider를 통한 메모리 효율적 데이터 처리

## 왜 TBEG인가

Apache POI로 직접 Excel을 생성하면 수십 줄의 코드가 필요합니다.

```kotlin
// Apache POI 직접 사용
val workbook = XSSFWorkbook()
val sheet = workbook.createSheet("직원 현황")
val headerRow = sheet.createRow(0)
headerRow.createCell(0).setCellValue("이름")
headerRow.createCell(1).setCellValue("직급")
headerRow.createCell(2).setCellValue("연봉")

employees.forEachIndexed { index, emp ->
    val row = sheet.createRow(index + 1)
    row.createCell(0).setCellValue(emp.name)
    row.createCell(1).setCellValue(emp.position)
    row.createCell(2).setCellValue(emp.salary.toDouble())
}

// 열 폭 조정, 스타일 적용, 수식 추가, 차트... 끝이 없음
```

TBEG을 사용하면 디자이너가 만든 **Excel 템플릿을 그대로 활용**하면서 데이터만 바인딩하면 됩니다.

```kotlin
// TBEG 사용
val data = mapOf(
    "title" to "직원 현황",
    "employees" to employeeList
)

ExcelGenerator().use { generator ->
    val bytes = generator.generate(template, data)
    File("output.xlsx").writeBytes(bytes)
}
```

서식, 차트, 수식, 조건부 서식은 **모두 템플릿에서 관리**합니다. 코드는 데이터 바인딩에만 집중합니다.

> [!TIP]
> **설계 철학**: Excel이 이미 잘하는 기능은 재구현하지 않고 그대로 살립니다.
> 집계는 `=SUM()`으로, 조건부 강조는 조건부 서식으로, 시각화는 차트로 — 익숙한 Excel 기능을 그대로 활용하세요.
> TBEG은 여기에 동적 데이터 바인딩을 더하고, 데이터가 확장되어도 이 기능들이 의도대로 동작하도록 조정합니다.

## 한 눈에 보기

**템플릿**

![템플릿](modules/tbeg/src/main/resources/sample/screenshot_template.png)

**코드**

```kotlin
val data = simpleDataProvider {
    value("reportTitle", "Q1 2026 Sales Performance Report")
    value("period", "Jan 2026 ~ Mar 2026")
    value("author", "Yongho Hwang")
    value("reportDate", LocalDate.now().toString())
    image("logo", logoBytes)
    image("ci", ciBytes)
    items("depts") { deptList.iterator() }
    items("products") { productList.iterator() }
}

ExcelGenerator().use { generator ->
    generator.generateToFile(template, data, outputDir, "quarterly_report")
}
```

**결과**

![결과](modules/tbeg/src/main/resources/sample/screenshot_result.png)

변수 치환, 이미지 삽입, 반복 데이터 확장, 수식 범위 조정, 조건부 서식 복제, 차트 데이터 반영까지 TBEG이 자동으로 처리합니다.

> 전체 코드와 템플릿 다운로드는 [종합 예제](modules/tbeg/manual/ko/examples/advanced-examples.md#11-종합-예제--분기-매출-실적-보고서)를 참조하세요.

## 이럴 때 TBEG을 사용하세요

| 상황 | 적합 여부 |
|------|----------|
| 정형화된 보고서/명세서 생성 | 적합 |
| 디자이너가 제공한 Excel 양식에 데이터 채우기 | 적합 |
| 복잡한 서식(조건부 서식, 차트, 피벗 테이블)이 필요한 보고서 | 적합 |
| 수만~수십만 행의 대용량 데이터 처리 | 적합 |
| 열 구조가 동적으로 변하는 Excel | 비적합 |
| Excel 파일 읽기/파싱 | 비적합 (TBEG은 생성 전용) |

## 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.3")
}
```

```groovy
// Gradle (Groovy DSL)
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.3'
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.3</version>
</dependency>
```

## 빠른 시작

### Kotlin

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "title" to "직원 현황",
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("한용호", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Java

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Example {
    public static void main(String[] args) throws Exception {
        var data = Map.<String, Object>of(
            "title", "직원 현황",
            "employees", List.of(
                Map.of("name", "황용호", "position", "부장", "salary", 8000),
                Map.of("name", "홍용호", "position", "과장", "salary", 6500)
            )
        );

        try (var generator = new ExcelGenerator()) {
            byte[] bytes = generator.generate(new FileInputStream("template.xlsx"), data);
            Files.write(Path.of("output.xlsx"), bytes);
        }
    }
}
```

### DataProvider (Kotlin DSL / Java Builder)

<details>
<summary>Kotlin DSL</summary>

```kotlin
val provider = simpleDataProvider {
    value("title", "직원 현황")
    items("employees", listOf(
        mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
        mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500)
    ))
}
```

</details>

<details>
<summary>Java Builder</summary>

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "직원 현황")
    .items("employees", List.of(
        Map.of("name", "황용호", "position", "부장", "salary", 8000),
        Map.of("name", "홍용호", "position", "과장", "salary", 6500)
    ))
    .build();
```

</details>

## 템플릿 문법

| 문법 | 설명 | 예시 |
|------|------|------|
| `${변수명}` | 변수 치환 | `${title}` |
| `${item.필드}` | 반복 항목 필드 | `${emp.name}` |
| `${repeat(컬렉션, 범위, 변수)}` | 반복 처리 | `${repeat(items, A2:C2, item)}` |
| `${image(이름)}` | 이미지 삽입 | `${image(logo)}` |
| `${size(컬렉션)}` | 컬렉션 크기 | `${size(items)}` |

상세 문법은 [템플릿 문법 레퍼런스](modules/tbeg/manual/ko/reference/template-syntax.md)를 참조하세요.

## Spring Boot

Spring Boot 환경에서는 `ExcelGenerator`가 자동으로 Bean으로 등록됩니다.

<details open>
<summary>Kotlin</summary>

```kotlin
@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader
) {
    fun generateReport(): ByteArray {
        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        val data = mapOf("title" to "보고서", "items" to listOf(...))
        return excelGenerator.generate(template.inputStream, data)
    }
}
```

</details>

<details>
<summary>Java</summary>

```java
@Service
public class ReportService {
    private final ExcelGenerator excelGenerator;
    private final ResourceLoader resourceLoader;

    public ReportService(ExcelGenerator excelGenerator, ResourceLoader resourceLoader) {
        this.excelGenerator = excelGenerator;
        this.resourceLoader = resourceLoader;
    }

    public byte[] generateReport() throws IOException {
        Resource template = resourceLoader.getResource("classpath:templates/report.xlsx");
        Map<String, Object> data = Map.of("title", "보고서", "items", List.of(...));
        return excelGenerator.generate(template.getInputStream(), data);
    }
}
```

</details>

### 설정 (application.yml)

```yaml
tbeg:
  streaming-mode: enabled       # enabled, disabled
  file-naming-mode: timestamp
  preserve-template-layout: true
```

## 스트리밍 모드

메모리 효율성과 처리 속도를 개선하는 모드입니다. 기본값(`enabled`)을 권장합니다.

| 모드              | 설명                   |
|-----------------|----------------------|
| `enabled` (기본값) | 메모리 효율적, 빠른 처리 속도    |
| `disabled`      | 모든 행을 메모리에 유지(장점 없음) |

### 성능 벤치마크

**테스트 환경**: Java 21, macOS, 3개 컬럼 repeat + SUM 수식

| 데이터 크기   | disabled | enabled | 속도 향상    |
|----------|----------|---------|----------|
| 1,000행   | 179ms    | 146ms   | 1.2배     |
| 10,000행  | 1,887ms  | 519ms   | **3.6배** |
| 30,000행  | -        | 1,104ms | -        |
| 50,000행  | -        | 1,269ms | -        |
| 100,000행 | -        | 2,599ms | -        |

### 타 라이브러리와 비교 (30,000행)

| 라이브러리    | 소요 시간    | 비고                                                          |
|----------|----------|-------------------------------------------------------------|
| **TBEG** | **1.1초** | 스트리밍 모드                                                     |
| JXLS     | 5.2초     | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203) |

> [!TIP]
> 벤치마크 실행: `./gradlew :tbeg:runBenchmark`

설정 옵션의 상세 내용은 [설정 옵션 레퍼런스](modules/tbeg/manual/ko/reference/configuration.md)를 참조하세요.

## 문서

**상세 문서는 [TBEG 매뉴얼](modules/tbeg/manual/ko/index.md)을 참조하세요.**

- [TBEG 모듈 README](modules/tbeg/README.ko.md)
- [사용자 가이드](modules/tbeg/manual/ko/user-guide.md)
- [템플릿 문법 레퍼런스](modules/tbeg/manual/ko/reference/template-syntax.md)
- [API 레퍼런스](modules/tbeg/manual/ko/reference/api-reference.md)
- [설정 옵션 레퍼런스](modules/tbeg/manual/ko/reference/configuration.md)
- [기본 예제](modules/tbeg/manual/ko/examples/basic-examples.md)
- [고급 예제](modules/tbeg/manual/ko/examples/advanced-examples.md)
- [Spring Boot 예제](modules/tbeg/manual/ko/examples/spring-boot-examples.md)
- [모범 사례](modules/tbeg/manual/ko/best-practices.md)
- [문제 해결](modules/tbeg/manual/ko/troubleshooting.md)
- [유지보수 개발자 가이드](modules/tbeg/manual/ko/developer-guide.md)

## 샘플 실행

샘플은 `modules/tbeg/src/test/resources/templates/template.xlsx` 템플릿을 사용합니다.

```bash
# Kotlin 샘플
./gradlew :tbeg:runSample
# 결과: build/samples/

# Java 샘플
./gradlew :tbeg:runJavaSample
# 결과: build/samples-java/

# Spring Boot 샘플
./gradlew :tbeg:runSpringBootSample
# 결과: build/samples-spring/
```

## 요구사항

- Java 21+
- Kotlin 2.1.20+ (Kotlin 프로젝트에서 사용 시)

## 작성자

[황용호 (Yongho Hwang)](https://github.com/jogakdal) (jogakdal@gmail.com)

## 라이선스

[Apache License 2.0](LICENSE)
