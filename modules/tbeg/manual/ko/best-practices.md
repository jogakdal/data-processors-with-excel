# TBEG 모범 사례

## 목차
1. [템플릿 설계](#1-템플릿-설계)
2. [성능 최적화](#2-성능-최적화)
3. [서버 운영](#3-서버-운영)
4. [셀 병합과 요소 묶음](#4-셀-병합과-요소-묶음)
5. [오류 방지](#5-오류-방지)

---

## 1. 템플릿 설계

### repeat 마커를 반복 범위 밖에 배치하세요

`${repeat(...)}` 마커는 반복 범위 밖이면 워크북 내 어디든 배치할 수 있습니다. 마커를 데이터 영역 위의 행에 두면 가독성이 좋아집니다.

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A2:C2, emp)} |                 |               |
| 2 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

- 1행: repeat 마커 (반복 범위 밖에 배치)
- 2행: 반복 범위

---

### 수식은 데이터 영역 아래에 배치하세요

repeat 영역을 참조하는 수식은 영역이 어디에 있든 범위가 자동으로 조정됩니다. 다만 수식을 repeat 영역 아래에 배치하면 Excel에서 템플릿을 편집할 때 자연스러운 순서(데이터 → 집계)가 되어 가독성이 좋습니다.

|   | A                             | B             | C               |
|---|-------------------------------|---------------|-----------------|
| 1 | ${repeat(items, A2:C2, item)} |               |                 |
| 2 | ${item.name}                  | ${item.value} | ${item.qty}     |
| 3 | 합계                            | =SUM(B2:B2)   | =AVERAGE(C2:C2) |

3행의 수식은 repeat 확장 시 자동으로 `=SUM(B2:BN)`, `=AVERAGE(C2:CN)`으로 범위가 조정됩니다. `SUM`, `AVERAGE` 외에도 `COUNT`, `MAX`, `MIN` 등 범위를 참조하는 모든 수식이 동일하게 조정됩니다.

---

### 1행에 1개 데이터를 배치하세요

repeat 범위는 직관적으로 설계하세요. 1행 1데이터를 기본으로 하되, 복잡한 레이아웃이 필요하면 다중 행 반복을 사용합니다.

**권장** -- 1행 단위 반복:

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A2:C2, emp)} |                 |               |
| 2 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

**복잡한 경우** -- 2행 단위 반복:

|   | A                                | B                   |
|---|----------------------------------|---------------------|
| 1 | ${repeat(employees, A2:B3, emp)} |                     |
| 2 | 이름: ${emp.name}                  | 직급: ${emp.position} |
| 3 | 급여: ${emp.salary}                |                     |

---

### 숨길 가능성이 있는 필드는 hideable 마커로 표시하세요

`${hideable(...)}` 마커를 사용하면 코드에서 동적으로 특정 필드를 숨길 수 있습니다.

|   | A                                | B               | C                                           |
|---|----------------------------------|-----------------|---------------------------------------------|
| 1 | 이름                               | 직급              | 급여                                          |
| 2 | ${emp.name}                      | ${emp.position} | ${hideable(value=emp.salary, bundle=C1:C3)} |
| 3 | 합계                               |                 | =SUM(C2:C2)                                 |
| 4 | ${repeat(employees, A2:C2, emp)} |                 |                                             |

- `bundle` 파라미터로 필드 타이틀이나 수식까지 함께 관리할 수 있습니다.
- `DIM` 모드는 레이아웃을 유지하면서 비활성화 처리할 때 유용합니다.
- `DELETE` 모드(기본값)는 해당 열을 물리적으로 삭제합니다.

---

### bundle 범위를 병합 셀에 맞춰 설정하세요

hideable 마커의 `bundle` 범위가 병합 셀을 부분적으로 포함하면 오류가 발생합니다. bundle 범위는 병합 셀 전체를 포함하거나 아예 포함하지 않도록 설정하세요.

---

### 명시적 파라미터를 사용하세요

파라미터가 3개 이상이면 명시적 파라미터 형식을 사용하면 의도가 명확해집니다.

```excel
// 위치 기반 (파라미터가 많으면 읽기 어려움)
${repeat(items, A2:C2, item, DOWN, A10:C10)}

// 명시적 (의도가 명확)
${repeat(collection=items, range=A2:C2, var=item, direction=DOWN, empty=A10:C10)}
```

---

### 반복 영역이 겹치지 않도록 하세요

같은 시트에 여러 repeat 영역을 배치할 때, 영역이 겹치면 안 됩니다. 열을 분리하거나 행을 분리하여 배치합니다.

**올바른 배치** -- 열 분리:

|   | A                                  | B               | C | D                                    | E              |
|---|------------------------------------|-----------------|---|--------------------------------------|----------------|
| 1 | ${repeat(employees, A2:B2, emp)}   |                 |   | ${repeat(departments, D2:E2, dept)}  |                |
| 2 | ${emp.name}                        | ${emp.salary}   |   | ${dept.name}                         | ${dept.budget} |

두 repeat이 서로 다른 열 범위(A~B, D~E)에 있으므로 독립적으로 확장됩니다.

**올바른 배치** -- 행 분리:

|   | A                                  | B               |
|---|------------------------------------|-----------------|
| 1 | ${repeat(employees, A2:B2, emp)}   |                 |
| 2 | ${emp.name}                        | ${emp.salary}   |
| 3 | ${repeat(departments, A4:B4, dept)} |                |
| 4 | ${dept.name}                       | ${dept.budget}  |

같은 열 범위(A~B)를 사용하지만 행이 분리되어 있으므로 순서대로 확장됩니다.

**잘못된 배치** -- 열 범위가 겹침:

|   | A                                  | B               | C              |
|---|------------------------------------|-----------------|----------------|
| 1 | ${repeat(employees, A2:B2, emp)}   |                 | ${repeat(departments, B2:C2, dept)} |
| 2 | ${emp.name}                        | ${emp.salary}   | ${dept.budget} |

두 repeat의 열 범위(A~B, B~C)가 B열에서 겹칩니다. 이 경우 오류가 발생합니다.

---

## 2. 성능 최적화

### 3단계 최적화 가이드

데이터 크기에 따라 아래 단계를 적용하세요.

#### 1단계: count 제공

DataProvider에 컬렉션의 전체 건수를 함께 제공하면, 데이터 이중 순회를 방지합니다.

```kotlin
val count = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    items("employees", count) {
        employeeRepository.findAll().iterator()
    }
}
```

#### 2단계: 지연 로딩

모든 데이터를 미리 메모리에 로드하지 말고, Lambda로 필요한 시점에 로드합니다.

```kotlin
// 비권장: 모든 데이터를 미리 로드
val allEmployees = employeeRepository.findAll()
items("employees", allEmployees)

// 권장: 지연 로딩
items("employees", count) {
    employeeRepository.findAll().iterator()
}
```

#### 3단계: DB 스트리밍

10만 행 이상의 대용량 데이터는 JPA Stream 또는 MyBatis Cursor를 사용합니다.

```kotlin
@Transactional(readOnly = true)
fun generateLargeReport(): Path {
    val count = employeeRepository.count().toInt()

    val provider = simpleDataProvider {
        items("employees", count) {
            employeeRepository.streamAll().iterator()
        }
    }

    return excelGenerator.generateToFile(
        template = template,
        dataProvider = provider,
        outputDir = outputDir,
        baseFileName = "large_report"
    )
}
```

---

### 데이터 크기별 권장 방식

| 데이터 크기 | 데이터 제공 방식 | 추가 설정 |
|-----------|-------------|---------|
| ~1,000행 | `Map<String, Any>` | 없음 |
| 1,000~10,000행 | `simpleDataProvider` + count | 없음 |
| 10,000행 이상 | `simpleDataProvider` + count + Stream | `generateToFile()` 권장 |

---

## 3. 서버 운영

### 메모리 산정 가이드라인

TBEG은 스트리밍 방식으로 렌더링하여 처리 중 메모리는 일정하지만, 결과 파일을 메모리에 보관하므로 **결과 파일 크기에 비례하는 힙**이 필요합니다.

| 데이터 규모 | 권장 `-Xmx` | 비고 |
|----------|-----------|------|
| 1만 행 이하 | 512MB | 대부분의 일반 보고서 |
| 10만 행 | 1~2GB | |
| 50만 행 | 4GB | |
| 100만 행 | 8GB | |

> 동시에 여러 보고서를 생성하는 경우, 위 값에 동시 요청 수를 곱하여 산정하세요. 컬럼 수, 서식 복잡도, 이미지 포함 여부에 따라 달라질 수 있습니다.

> 피벗 테이블이 포함된 템플릿에서는 피벗 재생성 과정에서 결과 파일 전체를 메모리에 로드하므로 약 30만 행이 현실적 상한입니다.

### 동시 요청 처리

`ExcelGenerator`는 스레드 안전하게 설계되어 있으므로, 하나의 인스턴스를 여러 스레드에서 동시에 사용할 수 있습니다. Spring 싱글톤 빈으로 등록하여 사용하세요.

```kotlin
@Configuration
class TbegConfig {
    @Bean
    fun excelGenerator() = ExcelGenerator()
}
```

> 동시에 여러 보고서를 생성하는 경우, 각 요청의 결과 파일 크기를 합산하여 힙 메모리를 산정하세요.

---

## 4. 셀 병합과 요소 묶음

### merge 마커 사용 시 데이터를 정렬하세요

`${merge(item.field)}` 마커는 연속된 같은 값의 셀을 자동으로 병합합니다. 따라서 병합 기준 필드로 데이터를 미리 정렬해야 의도한 결과를 얻을 수 있습니다.

```
데이터: [영업1팀, 영업2팀, 영업1팀]  -> 영업1팀이 떨어져 있으므로 별도 셀로 남음
데이터: [영업1팀, 영업1팀, 영업2팀]  -> 영업1팀 2셀 병합, 영업2팀 1셀
```

```kotlin
// 권장: 병합 기준 필드로 정렬
val employees = employeeRepository.findAll()
    .sortedBy { it.department }  // 부서별로 정렬

items("employees", employees)
```

---

### bundle로 복합 레이아웃을 보호하세요

repeat 영역 아래에 여러 열에 걸친 표가 있을 때, repeat 확장으로 인해 표가 어긋날 수 있습니다. `${bundle(범위)}`로 표를 묶으면 항상 일체로 이동합니다.

|   | A                               | B               | C    | D    | E      |
|---|----------------------------------|-----------------|------|------|--------|
| 1 | ${repeat(depts, A2:B2, dept)}   |                 |      |      |        |
| 2 | ${dept.name}                    | ${dept.revenue} |      |      |        |
| 3 | ${bundle(A4:E6)}                |                 |      |      |        |
| 4 | 이름                              | 매출              | 원가   | 이익   | 합계     |
| 5 | 황용호                             | 1000            | 500  | 500  | 2000   |
| 6 | 합계                              |                 |      |      | =SUM() |

bundle이 없으면 repeat 열 범위(A~B)만 밀리고 나머지(C~E)는 원래 행에 남아 표가 깨집니다. bundle로 4~6행 전체를 묶으면 A~E열이 일체로 이동하여 레이아웃이 유지됩니다. 상세 비교는 [템플릿 문법 레퍼런스 - 요소 묶음](./reference/template-syntax.md#8-요소-묶음-bundle)을 참조하세요.

---

## 5. 오류 방지

### 개발 중에는 `THROW` 모드를 사용하세요

`MissingDataBehavior.THROW`로 설정하면 누락된 데이터를 즉시 감지할 수 있습니다.

```kotlin
// 개발 환경
val devConfig = TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)

// 운영 환경 (기본값)
val prodConfig = TbegConfig(missingDataBehavior = MissingDataBehavior.WARN)
```

Spring Boot에서는 프로파일별로 설정할 수 있습니다:

```yaml
# application-dev.yml
hunet:
  tbeg:
    missing-data-behavior: throw

# application-prod.yml
hunet:
  tbeg:
    missing-data-behavior: warn
```

---

### JPA Stream 사용 시 `@Transactional`을 확인하세요

JPA Stream은 트랜잭션이 종료되면 닫힙니다. Excel 생성이 완료될 때까지 트랜잭션이 유지되어야 합니다.

```kotlin
@Transactional(readOnly = true)  // 필수
fun generateReport(): ByteArray {
    val provider = simpleDataProvider {
        items("employees", count) {
            employeeRepository.streamAll().iterator()
        }
    }
    return excelGenerator.generate(template, provider)
}
```

> [!WARNING]
> `@Transactional`이 없으면 `LazyInitializationException`이 발생할 수 있습니다.

---

### 단위 테스트를 작성하세요

보고서 생성 로직에 대한 테스트를 작성하면 템플릿 변경으로 인한 오류를 조기에 발견할 수 있습니다.

```kotlin
@Test
fun `직원 보고서 생성 테스트`() {
    val data = mapOf(
        "title" to "테스트",
        "employees" to listOf(Employee("황용호", "부장", 8000))
    )

    ExcelGenerator().use { generator ->
        val template = ClassPathResource("templates/employees.xlsx").inputStream
        val bytes = generator.generate(template, data)

        // 결과 검증
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("황용호", sheet.getRow(1).getCell(0).stringCellValue)
        }
    }
}
```

---

## 다음 단계

- [문제 해결](./troubleshooting.md) - 오류 진단 및 해결
- [API 레퍼런스](./reference/api-reference.md) - API 상세
- [고급 예제](./examples/advanced-examples.md) - 실전 예제
