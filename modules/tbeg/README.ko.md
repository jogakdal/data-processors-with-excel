> 한국어 | **[English](./README.md)**

# TBEG (Template Based Excel Generator)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 라이브러리입니다.

## 주요 기능

- **템플릿 기반 생성**: Excel 템플릿에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리**: `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환**: `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩
- **이미지 삽입**: 템플릿 셀에 동적 이미지 삽입
- **파일 암호화**: 생성된 Excel 파일에 열기 암호 설정
- **문서 메타데이터**: 제목, 작성자, 키워드 등 문서 속성 설정
- **비동기 처리**: 대용량 데이터를 백그라운드에서 처리
- **지연 로딩**: DataProvider를 통한 메모리 효율적 데이터 처리

## 의존성 추가

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.1'
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.1.1</version>
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

### Spring Boot

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

Spring Boot 환경에서는 `ExcelGenerator`가 자동으로 Bean으로 등록됩니다.

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

설정 옵션의 상세 내용은 [설정 옵션 레퍼런스](./manual/ko/reference/configuration.md)를 참조하세요.

## 문서

**상세 문서는 [TBEG 매뉴얼](./manual/ko/index.md)을 참조하세요.**

- [사용자 가이드](./manual/ko/user-guide.md)
- [템플릿 문법 레퍼런스](./manual/ko/reference/template-syntax.md)
- [API 레퍼런스](./manual/ko/reference/api-reference.md)
- [설정 옵션 레퍼런스](./manual/ko/reference/configuration.md)
- [기본 예제](./manual/ko/examples/basic-examples.md)
- [고급 예제](./manual/ko/examples/advanced-examples.md)
- [Spring Boot 예제](./manual/ko/examples/spring-boot-examples.md)
- [모범 사례](./manual/ko/best-practices.md)
- [문제 해결](./manual/ko/troubleshooting.md)
- [유지보수 개발자 가이드](./manual/ko/developer-guide.md)

## 샘플 실행

샘플은 `src/test/resources/templates/template.xlsx` 템플릿을 사용합니다.

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

## 작성자

[황용호 (Yongho Hwang)](https://github.com/jogakdal) (jogakdal@gmail.com)
