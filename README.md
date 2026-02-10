# TBEG - Template Based Excel Generator

[![CI](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml/badge.svg)](https://github.com/jogakdal/data-processors-with-excel/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 Kotlin 라이브러리입니다.

## 주요 기능

- **템플릿 기반 생성** — Excel 템플릿에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리** — `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환** — `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩
- **이미지 삽입** — 템플릿 셀에 동적 이미지 삽입
- **스트리밍 모드** — SXSSF 기반 대용량 데이터 고속 처리
- **파일 암호화** — 생성된 Excel 파일에 열기 암호 설정
- **비동기 처리** — 대용량 데이터를 백그라운드에서 처리
- **Spring Boot 지원** — Auto-configuration으로 별도 설정 없이 사용

## 의존성 추가

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 빠른 시작

### Map 방식

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "title" to "직원 현황",
        "employees" to listOf(
            Employee("홍길동", "부장", 8000),
            Employee("김철수", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### DataProvider DSL

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

fun main() {
    val provider = simpleDataProvider {
        value("title", "직원 현황")
        items("employees", listOf(
            mapOf("name" to "홍길동", "position" to "부장", "salary" to 8000),
            mapOf("name" to "김철수", "position" to "과장", "salary" to 6500)
        ))
    }

    ExcelGenerator().use { generator ->
        val bytes = generator.generate(File("template.xlsx").inputStream(), provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

## 템플릿 문법

### 변수 치환

```
${title}
${employee.name}
```

### 반복 데이터

```
${repeat(employees, A3:C3, emp, DOWN)}
```

### 이미지

```
${image(logo)}
${image(logo, B5)}
${image(logo, B5, 100:50)}
```

## Spring Boot

Spring Boot 환경에서는 `ExcelGenerator`가 자동으로 Bean에 등록됩니다.

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

### 설정 (application.yml)

```yaml
tbeg:
  streaming-mode: enabled       # enabled, disabled
  file-naming-mode: timestamp
  preserve-template-layout: true
```

## 성능

**테스트 환경**: Java 21, macOS, 3개 컬럼 repeat + SUM 수식

| 데이터 크기   | disabled | enabled | 속도 향상    |
|----------|----------|---------|----------|
| 1,000행   | 172ms    | 147ms   | 1.2배     |
| 10,000행  | 1,801ms  | 663ms   | **2.7배** |
| 30,000행  | -        | 1,057ms | -        |
| 50,000행  | -        | 1,202ms | -        |
| 100,000행 | -        | 3,154ms | -        |

### 타 라이브러리와 비교 (30,000행)

| 라이브러리    | 소요 시간    | 비고                                                          |
|----------|----------|-------------------------------------------------------------|
| **TBEG** | **1.1초** | 스트리밍 모드                                                     |
| JXLS     | 5.2초     | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203) |

> 벤치마크 실행: `./gradlew :tbeg:runBenchmark`

## 문서

상세 문서는 아래 링크를 참고하세요:

- [TBEG 모듈 README](modules/tbeg/README.md)
- [사용자 가이드](modules/tbeg/manual/ko/user-guide.md)
- [템플릿 문법 레퍼런스](modules/tbeg/manual/ko/reference/template-syntax.md)
- [API 레퍼런스](modules/tbeg/manual/ko/reference/api-reference.md)
- [설정 옵션 레퍼런스](modules/tbeg/manual/ko/reference/configuration.md)
- [기본 예제](modules/tbeg/manual/ko/examples/basic-examples.md)
- [고급 예제](modules/tbeg/manual/ko/examples/advanced-examples.md)
- [Spring Boot 예제](modules/tbeg/manual/ko/examples/spring-boot-examples.md)

## 요구사항

- Kotlin 2.1.20+
- Java 21+

## 라이선스

[Apache License 2.0](LICENSE)
