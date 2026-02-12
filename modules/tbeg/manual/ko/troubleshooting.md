# TBEG 문제 해결 가이드

## 목차
1. [템플릿 관련](#1-템플릿-관련)
2. [실행 시 오류](#2-실행-시-오류)
3. [결과 파일 관련](#3-결과-파일-관련)
4. [성능 문제](#4-성능-문제)
5. [Spring Boot 관련](#5-spring-boot-관련)

---

## 1. 템플릿 관련

### `#NAME?` 에러가 표시됩니다

**증상**: 수식 형태 마커(`=TBEG_REPEAT(...)`, `=TBEG_IMAGE(...)`, `=TBEG_SIZE(...)`)가 있는 셀에서 `#NAME?` 에러가 표시됩니다.

**원인**: 이 마커들은 Excel에 실제로 존재하지 않는 함수이므로, Excel에서 열면 에러로 표시됩니다.

**해결**: 정상입니다. TBEG가 Excel을 생성할 때 올바르게 처리되며, 결과 파일에는 `#NAME?`이 나타나지 않습니다.

---

### 마커가 결과 파일에 그대로 남습니다

**증상**: `${title}`, `${emp.name}` 등의 마커가 치환되지 않고 결과 파일에 그대로 출력됩니다.

**원인 및 해결**:

| 원인 | 해결 방법 |
|------|----------|
| 데이터에 해당 키가 없음 | `data` 맵 또는 DataProvider에 변수를 추가하세요 |
| 변수명 오타 | 템플릿의 마커명과 데이터의 키를 정확히 일치시키세요 |
| repeat 변수가 범위 밖에서 사용됨 | `${emp.name}` 등은 repeat 범위 안에서만 유효합니다 |

> [!TIP]
> `TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)`로 설정하면 누락된 데이터가 있을 때 예외가 발생하여 원인 파악이 쉬워집니다.

---

## 2. 실행 시 오류

### `TemplateProcessingException`

템플릿 파싱 중 문법 오류가 발견되면 발생합니다. `errorType`으로 오류 유형을 구분할 수 있습니다.

| ErrorType | 원인 | 해결 |
|-----------|------|------|
| `INVALID_REPEAT_SYNTAX` | repeat 마커 문법 오류 | `${repeat(컬렉션, 범위, 변수)}` 형식을 확인하세요 |
| `MISSING_REQUIRED_PARAMETER` | 필수 파라미터 누락 | repeat의 `collection`, `range`는 필수입니다 |
| `INVALID_RANGE_FORMAT` | 잘못된 셀 범위 형식 | `A2:C2` 같은 올바른 범위를 사용하세요 |
| `SHEET_NOT_FOUND` | 존재하지 않는 시트 참조 | 시트명이 정확한지 확인하세요 (`'Sheet1'!A2:C2`) |
| `INVALID_PARAMETER_VALUE` | 잘못된 파라미터 값 | direction은 `DOWN`/`RIGHT`만 허용됩니다 |

> [!NOTE]
> 명시적 파라미터와 위치 기반 파라미터를 혼합하면 `INVALID_REPEAT_SYNTAX` 오류가 발생합니다. 한 마커 내에서는 한 가지 방식만 사용하세요.

---

### `MissingTemplateDataException`

`missingDataBehavior = THROW`일 때, 템플릿에 정의된 데이터가 DataProvider에 없으면 발생합니다.

```
MissingTemplateDataException: 템플릿에 필요한 데이터가 누락되었습니다.
  누락된 변수: [title, author]
  누락된 컬렉션: [employees]
```

**해결**: 예외 메시지에 표시된 누락 항목을 DataProvider에 추가하세요.

---

### `FormulaExpansionException`

repeat으로 행이 확장될 때 수식 참조를 자동 조정하는 과정에서 실패하면 발생합니다.

**주요 원인**: 병합 셀이 포함된 영역에서 반복 확장이 이루어질 때, Excel 함수 인자 수 제한(255개)을 초과하는 경우

**해결**: 예외 메시지에 포함된 시트명, 셀 참조, 수식 정보를 확인하여 템플릿의 수식 배치를 조정하세요.

---

### `OutOfMemoryError`

대용량 데이터 처리 시 JVM 메모리가 부족하면 발생합니다.

**해결 단계**:
1. 스트리밍 모드 확인: `StreamingMode.ENABLED` (기본값)
2. DataProvider에서 지연 로딩 사용 (`items("name", count) { ... }`)
3. JVM 힙 메모리 증가: `-Xmx2g` 등
4. 데이터를 여러 파일로 분할 생성

---

## 3. 결과 파일 관련

### 차트 데이터 범위가 맞지 않습니다

**증상**: repeat으로 데이터 행이 확장되었는데 차트가 원래 범위만 참조합니다.

**해결**: TBEG는 차트 데이터 소스 범위를 자동으로 조정합니다. 이 문제가 발생한다면:
- 차트의 데이터 소스가 repeat 영역을 정확히 참조하는지 확인하세요
- 차트와 repeat 영역이 같은 시트에 있는지 확인하세요

---

### 숫자가 텍스트로 표시됩니다

**증상**: 숫자 데이터에 천 단위 구분자가 적용되지 않거나 텍스트로 인식됩니다.

**해결**:
- 데이터 타입 확인: `Int`, `Long`, `Double` 등 숫자 타입으로 전달하세요 (String이 아닌)
- 템플릿 셀에 숫자 서식이 적용되어 있는지 확인하세요

---

### 조건부 서식이 확장된 행에 적용되지 않습니다

**증상**: 반복 영역의 조건부 서식이 원래 행에만 적용됩니다.

**해결**: TBEG는 repeat 영역의 조건부 서식 범위를 자동으로 확장합니다. 조건부 서식의 적용 범위가 repeat의 `range` 파라미터와 일치하는지 확인하세요.

---

## 4. 성능 문제

### 생성 속도가 느립니다

아래 단계를 순서대로 확인하세요.

**1단계: 스트리밍 모드 확인**

```kotlin
val config = TbegConfig(streamingMode = StreamingMode.ENABLED) // 기본값
```

**2단계: count 제공 여부 확인**

count를 제공하면 데이터 이중 순회를 방지하여 성능이 개선됩니다.

```kotlin
items("employees", employeeCount) {
    employeeRepository.streamAll().iterator()
}
```

**3단계: 지연 로딩 사용**

모든 데이터를 미리 로드하지 말고 Lambda를 활용하세요.

```kotlin
items("employees") {
    employeeRepository.findAll().iterator()
}
```

**4단계: DB 스트리밍 사용**

JPA Stream 또는 MyBatis Cursor를 사용하여 DB에서 대용량 데이터를 스트리밍으로 처리하세요.

```kotlin
items("employees", count) {
    employeeRepository.streamAll().iterator()
}
```

### 데이터 크기별 권장 설정

| 데이터 크기 | 권장 방식 |
|-----------|----------|
| ~1,000행 | Map 방식으로 충분 |
| 1,000~10,000행 | simpleDataProvider + count |
| 10,000~100,000행 | simpleDataProvider + count + DB Stream |
| 100,000행 이상 | 커스텀 DataProvider + DB Stream + `generateToFile()` |

---

## 5. Spring Boot 관련

### `ExcelGenerator` Bean이 등록되지 않습니다

**증상**: `NoSuchBeanDefinitionException: No qualifying bean of type 'ExcelGenerator'`

**해결**:
1. 의존성 확인: `io.github.jogakdal:tbeg` 의존성이 추가되어 있는지 확인하세요
2. `@SpringBootApplication` 클래스가 있는 패키지 구조를 확인하세요
3. 커스텀 Bean을 직접 등록한 경우 `@ConditionalOnMissingBean`으로 인해 자동 설정이 비활성화될 수 있습니다

---

### `LazyInitializationException`

**증상**: JPA Stream 사용 시 `LazyInitializationException` 또는 `could not initialize proxy` 오류가 발생합니다.

**원인**: JPA 엔티티의 지연 로딩 프록시가 트랜잭션 밖에서 접근되었습니다.

**해결**: Excel 생성을 `@Transactional` 범위 안에서 수행하세요.

```kotlin
@Transactional(readOnly = true)
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
> JPA Stream을 사용할 때는 반드시 `@Transactional` 어노테이션이 필요합니다. 트랜잭션이 종료되면 Stream도 닫히므로 Excel 생성이 완료될 때까지 트랜잭션이 유지되어야 합니다.

---

## 다음 단계

- [모범 사례](./best-practices.md) - 올바른 사용 패턴
- [사용자 가이드](./user-guide.md) - TBEG 사용법
- [설정 옵션](./reference/configuration.md) - TbegConfig 옵션
