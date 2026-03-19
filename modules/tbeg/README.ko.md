# TBEG (Template Based Excel Generator)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 라이브러리입니다.

## 주요 기능

- **템플릿 기반 생성**: Excel 템플릿에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리**: `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환**: `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩. `=`로 시작하는 값은 Excel 수식으로 처리
- **이미지 삽입**: 템플릿 셀에 동적 이미지 삽입
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
> 집계는 `=SUM()`으로, 조건부 강조는 조건부 서식으로, 시각화는 차트로 -- 익숙한 Excel 기능을 그대로 활용하세요.
> TBEG은 여기에 동적 데이터 바인딩을 더하고, 데이터가 확장되어도 이 기능들이 의도대로 동작하도록 조정합니다.

## 한 눈에 보기

**템플릿**

![템플릿](./src/main/resources/sample/screenshot_template.png)

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

**결과**

![결과](./src/main/resources/sample/screenshot_result.png)

변수 치환, 이미지 삽입, 반복 데이터 확장, 자동 셀 병합, 요소 묶음, 선택적 필드 노출, 수식 범위 조정, 조건부 서식 반영, 차트 데이터 반영까지 TBEG이 자동으로 처리합니다.

> 전체 코드와 템플릿 다운로드는 [종합 예제](./manual/ko/examples/advanced-examples-kotlin.md#11-종합-예제) ([Java](./manual/ko/examples/advanced-examples-java.md#11-종합-예제))를 참조하세요.

## 이럴 때 TBEG을 사용하세요

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

## 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.2.3")
}
```

```groovy
// Gradle (Groovy DSL)
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.2.3'
}
```

```xml
<!-- Maven -->
<dependency>
    <groupId>io.github.jogakdal</groupId>
    <artifactId>tbeg</artifactId>
    <version>1.2.3</version>
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

|                 문법                 |     설명     |                            예시                            |
|:----------------------------------:|:----------:|:--------------------------------------------------------:|
|              `${변수명}`              |   변수 치환    |                        `${title}`                        |
|            `${item.필드}`            |  반복 항목 필드  |                      `${emp.name}`                       |
|      `${repeat(컬렉션, 범위, 변수)}`      |   반복 처리    |             `${repeat(items, A2:C2, item)}`              |
|           `${image(이름)}`           |   이미지 삽입   |                     `${image(logo)}`                     |
|           `${size(컬렉션)}`           |   컬렉션 크기   |                     `${size(items)}`                     |
|        `${merge(item.필드)}`         |  자동 셀 병합   |                   `${merge(emp.dept)}`                   |
|          `${bundle(범위)}`           |   요소 묶음    |                   `${bundle(A5:H12)}`                    |
| `${hideable(value=item.필드, ...)}`  | 선택적 필드 노출  | `${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}`  |

상세 문법은 [템플릿 문법 레퍼런스](./manual/ko/reference/template-syntax.md)를 참조하세요.

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

> 데이터 제공 방식(Map vs DataProvider), 출력 방식(generate/toStream/toFile) 비교 등 상세 분석은 [성능 벤치마크 상세](./manual/ko/appendix/benchmark-results.md)를 참조하세요.

### 타 라이브러리와 비교 (30,000행)

|   라이브러리    |    소요 시간 |                              비고                              |
|:----------:|---------:|:------------------------------------------------------------:|
|  **TBEG**  | **0.3초** |                                                              |
|    JXLS    |     5.2초 | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203)  |

> TBEG은 POI API를 직접 호출하고 단일 패스로 스트리밍 기록하는 반면, JXLS는 추상화 계층을 거쳐 템플릿 파싱 -> 변환 -> 기록의 다중 패스를 수행하기 때문에 이 차이가 발생하는 것으로 추정됩니다.

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
- [타 라이브러리 비교](./manual/ko/appendix/library-comparison.md)
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
