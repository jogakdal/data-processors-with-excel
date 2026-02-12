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

## 왜 TBEG인가

### Excel 보고서, 이렇게 만들고 계신가요?

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

### 이럴 때 TBEG을 사용하세요

| 상황 | 적합 여부 |
|------|----------|
| 정형화된 보고서/명세서 생성 | 적합 |
| 디자이너가 제공한 Excel 양식에 데이터 채우기 | 적합 |
| 복잡한 서식(조건부 서식, 차트, 피벗 테이블)이 필요한 보고서 | 적합 |
| 수만~수십만 행의 대용량 데이터 처리 | 적합 |
| 열 구조가 동적으로 변하는 Excel | 비적합 |
| Excel 파일 읽기/파싱 | 비적합 (TBEG은 생성 전용) |

---

## 어디서부터 시작할까요?

### 처음 사용합니다
1. 아래 [빠른 시작](#빠른-시작)으로 첫 Excel을 생성해 보세요
2. [사용자 가이드](./user-guide.md)에서 핵심 개념을 학습하세요
3. [기본 예제](./examples/basic-examples.md)에서 다양한 사용 패턴을 확인하세요

### Spring Boot에 도입하려고 합니다
1. [Spring Boot 예제](./examples/spring-boot-examples.md)에서 통합 방법을 확인하세요
2. [설정 옵션](./reference/configuration.md)에서 `application.yml` 설정을 확인하세요
3. [고급 예제 - JPA 연동](./examples/advanced-examples.md#13-jpaspring-data-연동)을 참조하세요

### 대용량 데이터를 처리해야 합니다
1. [사용자 가이드 - 대용량 데이터 처리](./user-guide.md#5-대용량-데이터-처리)를 참조하세요
2. [고급 예제 - DataProvider](./examples/advanced-examples.md#1-dataprovider-활용)에서 지연 로딩 패턴을 확인하세요
3. [모범 사례 - 성능 최적화](./best-practices.md#2-성능-최적화)에서 단계별 가이드를 따르세요

### 복잡한 템플릿을 다루고 있습니다
1. [템플릿 문법](./reference/template-syntax.md)에서 전체 마커 문법을 확인하세요
2. [고급 예제](./examples/advanced-examples.md)에서 실전 패턴을 참조하세요
3. [문제 해결](./troubleshooting.md)에서 자주 발생하는 문제를 확인하세요

### 내부 구현을 이해하고 싶습니다
1. [개발자 가이드](./developer-guide.md)에서 아키텍처와 파이프라인을 학습하세요

---

## 빠른 시작

### 리포지토리 및 의존성 추가

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

> [!TIP]
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

### 운영 가이드
- [모범 사례](./best-practices.md) - 템플릿 설계, 성능 최적화, 오류 방지
- [문제 해결](./troubleshooting.md) - 자주 발생하는 문제와 해결 방법
- [마이그레이션 가이드](./migration-guide.md) - 버전 업그레이드 안내

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

## 호환성 정보

| 항목 | 값 |
|------|-----|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| 패키지 | `io.github.jogakdal.tbeg` |
| Java | 21 이상 |
| Kotlin | 2.0 이상 |
| Apache POI | 5.2.5 (전이 의존성) |
| Spring Boot | 3.x (선택 사항) |
| 작성자 | [황용호 (Yongho Hwang)](https://github.com/jogakdal) (jogakdal@gmail.com) |
