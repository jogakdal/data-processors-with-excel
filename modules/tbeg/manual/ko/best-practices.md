# TBEG 모범 사례

## 목차
1. [템플릿 설계](#1-템플릿-설계)
2. [성능 최적화](#2-성능-최적화)
3. [오류 방지](#3-오류-방지)

---

## 1. 템플릿 설계

### repeat 마커를 반복 범위 밖에 배치하세요

`${repeat(...)}` 마커는 반복 범위 밖이면 워크북 내 어디든 배치할 수 있습니다. 마커를 데이터 영역 위의 헤더 행에 두면 가독성이 좋아집니다.

```
| A                                  | B               | C             |
| ${repeat(employees, A2:C2, emp)}   |                 |               |  ← 마커
| ${emp.name}                        | ${emp.position} | ${emp.salary} |  ← 반복 범위
```

---

### 수식은 데이터 영역 아래에 배치하세요

`=SUM()` 등의 합계 수식을 repeat 영역 아래에 배치하면, 영역 확장 시 수식의 참조 범위가 자동으로 조정됩니다.

```
| A                               | B             |
| ${repeat(items, A2:B2, item)}   |               |
| ${item.name}                    | ${item.value} |
| 합계                             | =SUM(B2:B2)   |  ← 자동으로 =SUM(B2:BN)으로 확장됨
```

---

### 1행에 1개 데이터를 배치하세요

repeat 범위는 직관적으로 설계하세요. 1행 1데이터를 기본으로 하되, 복잡한 레이아웃이 필요하면 다중 행 반복을 사용합니다.

**권장**:
```
${repeat(employees, A2:C2, emp)}   ← 1행 단위 반복
${emp.name} | ${emp.position} | ${emp.salary}
```

**복잡한 경우**:
```
${repeat(employees, A2:B3, emp)}   ← 2행 단위 반복
이름: ${emp.name}  | 직급: ${emp.position}
급여: ${emp.salary} |
```

---

### 명시적 파라미터를 사용하세요

파라미터가 3개 이상이면 명시적 파라미터 형식을 사용하면 의도가 명확해집니다.

```
// 위치 기반 (파라미터가 많으면 읽기 어려움)
${repeat(items, A2:C2, item, DOWN, A10:C10)}

// 명시적 (의도가 명확)
${repeat(collection=items, range=A2:C2, var=item, direction=DOWN, empty=A10:C10)}
```

---

### 반복 영역이 겹치지 않도록 하세요

같은 시트에 여러 repeat 영역을 배치할 때, 2D 공간(행 x 열)에서 영역이 겹치면 안 됩니다.

**올바른 배치** - 열 그룹을 분리:
```
| A (employees) | B (employees) | C | D (departments) | E (departments) |
```

**올바른 배치** - 행 그룹을 분리:
```
| A (employees) | B (employees) |
| ...           | ...           |
| A (departments) | B (departments) |  ← employees 아래에 배치
```

---

## 2. 성능 최적화

### 4단계 최적화 가이드

데이터 크기에 따라 아래 단계를 적용하세요.

#### 1단계: 스트리밍 모드 (기본 활성화)

```kotlin
val config = TbegConfig(streamingMode = StreamingMode.ENABLED) // 기본값
```

스트리밍 모드는 대용량 데이터에서 2~3배 이상의 성능 향상을 제공합니다.

#### 2단계: count 제공

DataProvider에 컬렉션의 전체 건수를 함께 제공하면, 데이터 이중 순회를 방지합니다.

```kotlin
val count = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    items("employees", count) {
        employeeRepository.findAll().iterator()
    }
}
```

#### 3단계: 지연 로딩

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

#### 4단계: DB 스트리밍

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
| 10,000~100,000행 | `simpleDataProvider` + count + Stream | `generateToFile()` 권장 |
| 100,000행 이상 | 커스텀 DataProvider + Stream | `generateToFile()` + 메모리 설정 |

---

## 3. 오류 방지

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
tbeg:
  missing-data-behavior: throw

# application-prod.yml
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
