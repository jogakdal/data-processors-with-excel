# TBEG (Template-Based Excel Generator)

템플릿 기반 Excel 파일 생성 라이브러리

## 개요

TBEG은 Excel 템플릿에 데이터를 바인딩하여 보고서, 명세서 등의 Excel 파일을 생성하는 라이브러리입니다.

### 주요 기능

- **템플릿 기반 생성**: Excel 템플릿(.xlsx)에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리**: `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환**: `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩. `=`로 시작하는 값은 Excel 수식으로 처리
- **이미지 삽입**: 템플릿의 지정된 위치에 동적 이미지 삽입
- **자동 셀 병합**: 반복 데이터에서 연속된 같은 값의 셀을 자동 병합
- **요소 묶음**: 여러 요소를 하나의 단위로 묶어 일체로 이동
- **선택적 필드 노출**: 상황에 따라 특정 필드의 노출을 제한. 삭제(DELETE) 또는 비활성화(DIM) 모드 선택 가능
- **수식 자동 조정**: 데이터 확장 시 SUM, AVERAGE 등 수식 범위를 자동 갱신
- **조건부 서식 자동 적용**: 반복 행에 원본의 조건부 서식을 자동 적용
- **차트/피벗 테이블 자동 반영**: 데이터 확장 시 차트 데이터 범위와 피벗 테이블 소스 범위를 자동 조정 (파일을 열 때 별도의 새로고침 불필요)
- **파일 암호화**: 생성된 Excel 파일에 열기 암호 설정
- **문서 메타데이터**: 제목, 작성자, 키워드 등 문서 속성 설정
- **대용량 처리**: 100만 행 이상의 데이터를 낮은 CPU 사용률로 안정적으로 처리
- **비동기 처리**: 대용량 데이터를 백그라운드에서 처리
- **지연 로딩**: DataProvider를 통한 메모리 효율적 데이터 처리

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

> [!TIP]
> **TBEG의 설계 철학**: Excel이 이미 잘하는 기능은 재구현하지 않고 그대로 살립니다.
> 집계는 `=SUM()`으로, 조건부 강조는 조건부 서식으로, 시각화는 차트로 -- 익숙한 Excel 기능을 그대로 활용하세요.
> TBEG은 여기에 동적 데이터 바인딩을 더하고, 데이터가 확장되어도 이 기능들이 의도대로 동작하도록 조정합니다.

### 실제로 이렇게 동작합니다

**템플릿**

![템플릿](../images/rich_sample_template.png)

Excel에서 서식, 수식, 조건부 서식, 차트를 자유롭게 디자인하고 데이터가 들어갈 자리에 마커(`${...}`)를 배치합니다.

**코드**

```kotlin
val data = simpleDataProvider {
    value("reportTitle", "Q1 2026 Sales Performance Report")
    value("period", "Jan 2026 ~ Mar 2026")
    value("author", "Yongho Hwang")
    value("reportDate", LocalDate.now().toString())
    value("subtitle_emp", "Employee Performance Details")
    image("logo", logoBytes)
    imageUrl("ci", "https://example.com/ci.png")  // URL도 가능
    items("depts") { deptList.iterator() }
    items("products") { productList.iterator() }
    items("employees") { employeeList.iterator() }
}

ExcelGenerator().use { generator ->
    generator.generateToFile(template, data, outputDir, "quarterly_report")
}
```

코드는 데이터 바인딩에만 집중합니다. 서식이나 레이아웃 코드는 한 줄도 필요하지 않습니다.

**결과**

![결과](../images/rich_sample_result_ko.png)

TBEG이 자동으로 처리한 항목:
- **변수 치환** -- 제목, 기간, 작성자, 날짜, 소제목
- **이미지 삽입** -- 로고, CI
- **반복 데이터 확장** -- 부서별 실적 행, 제품 카테고리 행, 직원별 실적 행
- **자동 셀 병합** -- 같은 부서명/팀명이 연속된 셀을 자동 병합
- **요소 묶음** -- 직원 실적 영역이 부서 실적 확장에 영향받지 않도록 보호
- **수식 범위 자동 조정** -- SUM, AVERAGE 등의 범위가 확장된 데이터에 맞춰 갱신
- **조건부 서식 자동 확장** -- 달성률 색상이 모든 행에 적용
- **차트 데이터 범위 반영** -- 차트가 확장된 데이터를 올바르게 참조

> 전체 코드와 템플릿 다운로드는 [종합 예제](./examples/advanced-examples-kotlin.md#11-종합-예제) ([Java](./examples/advanced-examples-java.md#11-종합-예제))를 참조하세요.

### 이럴 때 TBEG을 사용하세요

|                  상황                  |             적합 여부             |
|:------------------------------------:|:-----------------------------:|
|           엑셀 다운로드/내보내기 기능            |              적합               |
|           정형화된 보고서/명세서 생성            |              적합               |
|     디자이너가 제공한 Excel 양식에 데이터 채우기      |              적합               |
| 복잡한 서식(조건부 서식, 차트, 피벗 테이블)이 필요한 보고서  |              적합               |
|         수만~수십만 행의 대용량 데이터 처리         |              적합               |
|         열 구조가 동적으로 변하는 Excel         | 적합 (RIGHT repeat, 선택적 필드 노출)  |
|            Excel 파일 읽기/파싱            |       비적합 (TBEG은 생성 전용)       |

> [!TIP]
> **엑셀 다운로드/내보내기** 기능을 구현할 때 TBEG이 특히 유용합니다.
> ByteArray 반환, Stream 출력, 파일 저장 등 다양한 출력 방식을 지원하여 웹 응답이나 파일 저장에 바로 활용할 수 있고,
> DataProvider를 통해 데이터를 스트리밍으로 공급하면 대용량 데이터도 메모리 부담 없이 처리할 수 있습니다.

---

## 어디서부터 시작할까요?

### 처음 사용합니다
1. 아래 [빠른 시작](#빠른-시작)으로 첫 Excel을 생성해 보세요
2. [사용자 가이드](./user-guide.md)에서 핵심 개념을 학습하세요
3. [기본 예제](./examples/basic-examples.md)에서 다양한 사용 패턴을 확인하세요

### Spring Boot에 도입하려고 합니다
1. [Spring Boot 예제](./examples/spring-boot-examples.md)에서 통합 방법을 확인하세요
2. [설정 옵션](./reference/configuration.md)에서 `application.yml` 설정을 확인하세요
3. [고급 예제 - JPA 연동](./examples/advanced-examples-kotlin.md#13-jpaspring-data-연동) ([Java](./examples/advanced-examples-java.md#13-jpaspring-data-연동))을 참조하세요

### 대용량 데이터를 처리해야 합니다
1. [사용자 가이드 - 대용량 데이터 처리](./user-guide.md#5-대용량-데이터-처리)를 참조하세요
2. [고급 예제 - DataProvider](./examples/advanced-examples-kotlin.md#1-dataprovider-활용) ([Java](./examples/advanced-examples-java.md#1-dataprovider-활용))에서 지연 로딩 패턴을 확인하세요
3. [모범 사례 - 성능 최적화](./best-practices.md#2-성능-최적화)에서 단계별 가이드를 따르세요

### 복잡한 템플릿을 다루고 있습니다
1. [템플릿 문법](./reference/template-syntax.md)에서 전체 마커 문법을 확인하세요
2. [고급 예제](./examples/advanced-examples.md)에서 실전 패턴을 참조하세요
3. [문제 해결](./troubleshooting.md)에서 자주 발생하는 문제를 확인하세요

### 내부 구현을 이해하고 싶습니다
1. [개발자 가이드](./developer-guide.md)에서 아키텍처와 파이프라인을 학습하세요

---

## 대용량 데이터 처리

TBEG은 대용량 데이터를 최대한의 성능과 최소한의 리소스로 안정적으로 처리합니다. 100만 행을 약 9초에 생성하면서도 시스템 CPU의 9% 미만만 사용하므로, 서버에서 다른 서비스와 함께 운영해도 부담이 없습니다. 렌더링과 후처리 모두 스트리밍 방식으로 동작하여 데이터 크기에 관계없이 일정한 메모리 버퍼만 사용합니다.

### 성능 벤치마크

**테스트 환경**: macOS (aarch64), OpenJDK 21.0.1, 12코어, 3개 컬럼 repeat + SUM 수식 (JMH, fork=1, warmup=1, iterations=3)

|     데이터 크기 |    소요 시간 |  CPU/전체 |  CPU/코어 |     힙 할당량 |
|-----------:|---------:|--------:|--------:|----------:|
|     1,000행 |     20ms |    282% |   23.5% |    11.8MB |
|    10,000행 |    109ms |    177% |   14.7% |    58.5MB |
|    30,000행 |    315ms |    151% |   12.5% |   166.0MB |
|    50,000행 |    505ms |    137% |   11.4% |   270.1MB |
|   100,000행 |    993ms |    130% |   10.8% |   540.8MB |
|   500,000행 |  4,718ms |    106% |    8.9% | 2,614.5MB |
| 1,000,000행 |  8,952ms |    105% |    8.8% | 5,230.7MB |

> DataProvider + generateToFile 기준. CPU/전체는 프로세스 전체 CPU 시간 대비 wall-clock 시간, CPU/코어는 시스템 전체 CPU 용량 대비 사용률(코어 수로 나눈 값)입니다.

> 데이터 제공 방식(Map vs DataProvider), 출력 방식(generate/toStream/toFile) 비교 등 상세 분석은 [성능 벤치마크 상세](./appendix/benchmark-results.md)를 참조하세요.

### 타 라이브러리와 비교 (30,000행)

|   라이브러리    |    소요 시간 |                              비고                              |
|:----------:|---------:|:------------------------------------------------------------:|
|  **TBEG**  | **0.3초** |                                                              |
|    JXLS    |     5.2초 | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203)  |

> TBEG은 POI API를 직접 호출하고 단일 패스로 스트리밍 기록하는 반면, JXLS는 추상화 계층을 거쳐 템플릿 파싱 -> 변환 -> 기록의 다중 패스를 수행하기 때문에 이 차이가 발생하는 것으로 추정됩니다.

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
    implementation("io.github.jogakdal:tbeg:1.2.3")
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

### 별첨
- [용어집](./glossary.md) - TBEG 문서에서 사용하는 주요 용어 정리
- [타 라이브러리 비교](./appendix/library-comparison.md) - Excel 보고서 라이브러리 간 기능 비교
- [성능 벤치마크 상세](./appendix/benchmark-results.md) - JMH 벤치마크 상세 결과 및 분석

---

## 템플릿 문법 미리보기

|                 문법                 |     설명     |                            예시                            |
|:----------------------------------:|:----------:|:--------------------------------------------------------:|
|              `${변수명}`              |   변수 치환    |                        `${title}`                        |
|            `${객체.필드}`            |  반복 항목 필드  |                      `${emp.name}`                       |
|      `${repeat(컬렉션, 범위, 변수)}`      |   반복 처리    |             `${repeat(items, A2:C2, item)}`              |
|           `${image(이름)}`           |   이미지 삽입   |                     `${image(logo)}`                     |
|           `${size(컬렉션)}`           |   컬렉션 크기   |                     `${size(items)}`                     |
|        `${merge(객체.필드)}`         |  자동 셀 병합   |                   `${merge(emp.dept)}`                   |
|          `${bundle(범위)}`           |   요소 묶음    |                   `${bundle(A5:H12)}`                    |
|  `${hideable(객체.필드, 범위, 모드)}`  | 선택적 필드 노출  |          `${hideable(emp.salary, C1:C3, dim)}`           |

---

## 호환성 정보

|      항목      |            값             |
|:------------:|:------------------------:|
|   Group ID   |    `com.hunet.common`    |
| Artifact ID  |          `tbeg`          |
|     패키지      | `io.github.jogakdal.tbeg`  |
|     Java     |          21 이상           |
|    Kotlin    |          2.0 이상          |
|  Apache POI  |      5.2.5 (전이 의존성)      |
| Spring Boot  |       3.x (선택 사항)        |
