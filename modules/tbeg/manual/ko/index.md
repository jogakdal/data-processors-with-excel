# TBEG (Template-Based Excel Generator)

템플릿 기반 Excel 파일 생성 라이브러리

## 개요

TBEG은 Excel 템플릿에 데이터를 바인딩하여 보고서, 명세서 등의 Excel 파일을 생성하는 라이브러리입니다.

### 주요 기능

- **템플릿 기반 생성**: Excel 템플릿(.xlsx)에 데이터를 바인딩
- **반복 데이터 처리**: 리스트 데이터를 행 또는 열로 확장
- **이미지 삽입**: 템플릿의 지정된 위치에 이미지 삽입
- **대용량 처리**: 스트리밍 모드로 메모리 효율적인 대용량 데이터 처리
- **비동기 처리**: Coroutine, CompletableFuture, 백그라운드 작업 지원

---

## 빠른 시작

### 리포지토리 및 의존성 추가

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
    maven { url = uri("https://nexus.hunet.tech/repository/maven-public/") }
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.0-SNAPSHOT")
}
```

> 상세한 설정 방법은 [사용자 가이드](./user-guide.md#11-의존성-추가)를 참조하세요.

### 기본 사용법

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

fun main() {
    val data = mapOf(
        "title" to "월간 보고서",
        "items" to listOf(
            mapOf("name" to "항목1", "value" to 100),
            mapOf("name" to "항목2", "value" to 200)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

---

## 문서 구조

### 사용자 가이드
- [사용자 가이드](./user-guide.md) - TBEG 사용법 전체 가이드

### 레퍼런스
- [템플릿 문법](./reference/template-syntax.md) - 템플릿에서 사용할 수 있는 문법
- [API 레퍼런스](./reference/api-reference.md) - 클래스 및 메서드 상세
- [설정 옵션](./reference/configuration.md) - TbegConfig 옵션

### 예제
- [기본 예제](./examples/basic-examples.md) - 간단한 사용 예제
- [고급 예제](./examples/advanced-examples.md) - 대용량 처리, 비동기 처리 등
- [Spring Boot 예제](./examples/spring-boot-examples.md) - Spring Boot 환경 통합

### 개발자 가이드
- [개발자 가이드](./developer-guide.md) - 내부 아키텍처 및 확장 방법

---

## 템플릿 문법 미리보기

| 문법 | 설명 | 예시 |
|------|------|------|
| `${변수명}` | 변수 치환 | `${title}` |
| `${item.필드}` | 반복 항목 필드 | `${emp.name}` |
| `${repeat(컬렉션, 범위, 변수)}` | 반복 처리 | `${repeat(items, A2:C2, item)}` |
| `${image(이름)}` | 이미지 삽입 | `${image(logo)}` |
| `${size(컬렉션)}` | 컬렉션 크기 | `${size(items)}` |

---

## 모듈 정보

| 항목 | 값 |
|------|-----|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| 패키지 | `io.github.jogakdal.tbeg` |
| 최소 Java 버전 | 21 |
| 최소 Kotlin 버전 | 2.0 |
| 작성자 | [황용호 (Yongho Hwang)](https://github.com/jogakdal) (jogakdal@gmail.com) |
