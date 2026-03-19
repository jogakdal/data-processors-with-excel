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

**증상**: 수식 형태 마커(`=TBEG_REPEAT(...)`, `=TBEG_IMAGE(...)`, `=TBEG_SIZE(...)`, `=TBEG_MERGE(...)`, `=TBEG_BUNDLE(...)`)가 있는 셀에서 `#NAME?` 에러가 표시됩니다.

**원인**: 이 마커들은 Excel에 실제로 존재하지 않는 함수이므로, Excel에서 열면 에러로 표시됩니다.

**해결**: 정상입니다. TBEG이 Excel을 생성할 때 올바르게 처리되며, 결과 파일에는 `#NAME?`이 나타나지 않습니다.

---

### 마커가 결과 파일에 그대로 남습니다

**증상**: `${title}`, `${emp.name}` 등의 마커가 치환되지 않고 결과 파일에 그대로 출력됩니다.

**원인 및 해결**:

|           원인           | 해결 방법                                 |
|:----------------------:|:--------------------------------------|
|     데이터에 해당 키가 없음      | `data` 맵 또는 DataProvider에 변수를 추가하세요.   |
|         변수명 오타         | 템플릿의 마커명과 데이터의 키를 정확히 일치시키세요.          |
| repeat 변수가 범위 밖에서 사용됨  | `${emp.name}` 등은 repeat 범위 안에서만 유효합니다. |

> [!TIP]
> `TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)`로 설정하면 누락된 데이터가 있을 때 예외가 발생하여 원인 파악이 쉬워집니다.

---

### hideable 마커가 repeat의 반복 필드가 아닙니다

**증상**: "hideable 마커가 repeat의 반복 항목 필드 범위에 속하지 않습니다" 오류가 발생합니다.

**원인**: hideable 마커가 repeat의 반복 항목 필드가 아닌 셀에 배치되어 있습니다.

**해결**: hideable 마커를 repeat의 반복 범위 내 데이터 셀로 이동하세요. hideable은 repeat의 반복 항목 필드에서만 사용할 수 있습니다.

---

### hideable의 bundle 범위가 hideable 셀과 일치하지 않습니다

**증상**: 다음 중 하나의 오류가 발생합니다.
- "hideable '...'의 bundle **열** 범위(...)가 hideable 셀의 열 범위(...)와 일치하지 않습니다" (DOWN repeat)
- "hideable '...'의 bundle **행** 범위(...)가 hideable 셀의 행 범위(...)와 일치하지 않습니다" (RIGHT repeat)

**원인**: hideable 마커의 `bundle` 파라미터에 지정한 범위가 hideable 마커가 위치한 셀(또는 병합 셀)과 확장 축 방향으로 일치하지 않습니다. DOWN repeat에서는 열이, RIGHT repeat에서는 행이 일치해야 합니다.

**해결**: `bundle` 파라미터의 범위를 hideable 마커 셀에 맞춰 조정하세요.
- DOWN repeat: hideable 마커가 C2에 있다면 `bundle=C1:C3`처럼 C열을 사용
- RIGHT repeat: hideable 마커가 C2에 있다면 `bundle=B2:D2`처럼 2행을 사용

---

## 2. 실행 시 오류

### `TemplateProcessingException`

템플릿 파싱 중 문법 오류가 발견되면 발생합니다. `errorType`으로 오류 유형을 구분할 수 있습니다.

|          ErrorType           |        원인         | 해결                                                      |
|:----------------------------:|:-----------------:|:--------------------------------------------------------|
|   `INVALID_MARKER_SYNTAX`    |     마커 문법 오류      | 마커의 괄호, 파라미터 형식을 확인하세요.                                  |
| `MISSING_REQUIRED_PARAMETER` |    필수 파라미터 누락     | 각 마커의 필수 파라미터를 확인하세요. (예: repeat의 `collection`, `range`) |
|    `INVALID_RANGE_FORMAT`    |    잘못된 셀 범위 형식    | `A2:C2` 같은 올바른 범위를 사용하세요.                                |
|      `SHEET_NOT_FOUND`       |   존재하지 않는 시트 참조   | 시트명이 정확한지 확인하세요. (`'Sheet1'!A2:C2`)                      |
|  `INVALID_PARAMETER_VALUE`   |  허용되지 않는 파라미터 값   | 오류 메시지에 표시된 유효 값을 확인하고 파라미터를 수정하세요.                      |
|       `RANGE_CONFLICT`       | 범위 충돌 (중첩, 경계 걸침) | 겹치는 범위를 분리하거나, 한쪽이 다른 쪽을 완전히 포함하도록 조정하세요.                |

---

### `IllegalArgumentException`

모든 함수형 마커(repeat, hideable, image 등)에서 명시적 파라미터와 위치 기반 파라미터를 혼합하면 발생합니다.

**해결**: 한 마커 내에서는 명시적 파라미터(`name=value`) 또는 위치 기반 파라미터 중 한 가지 방식만 사용하세요.

---

### `MissingTemplateDataException`

`missingDataBehavior = THROW`일 때, 템플릿에 정의된 데이터가 DataProvider에 없으면 발생합니다.

```
MissingTemplateDataException: Required template data is missing.
  - Variables: title, author
  - Collections: employees
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
1. DataProvider에서 지연 로딩 사용 (`items("name", count) { ... }`)
2. JVM 힙 메모리 증가: `-Xmx2g` 등
3. 데이터를 여러 파일로 분할 생성

> 피벗 테이블이 포함된 템플릿에서는 피벗 재생성 과정에서 결과 파일 전체를 메모리에 로드하므로 약 30만 행이 현실적 상한입니다. 대용량 데이터에는 피벗 없는 템플릿을 사용하세요.

---

## 3. 결과 파일 관련

### 차트 데이터 범위가 맞지 않습니다

**증상**: repeat으로 데이터 행이 확장되었는데 차트가 원래 범위만 참조합니다.

**해결**: TBEG은 차트 데이터 소스 범위를 자동으로 조정합니다. 차트와 repeat 영역이 다른 시트에 있어도 정상 동작합니다. 이 문제가 발생한다면:
- 차트의 데이터 소스가 repeat 영역을 정확히 참조하는지 확인하세요.

---

### merge 병합 결과가 기대와 다릅니다

**증상**: `${merge(object.field)}`로 병합했는데 같은 값이 여러 그룹으로 나뉘어 병합됩니다.

**원인**: merge는 **연속된** 같은 값만 병합합니다. 같은 값이 떨어져 있으면 별도의 병합 그룹이 됩니다.

**해결**: 데이터를 병합 기준 필드로 미리 정렬하세요.
```kotlin
// 정렬 전: [영업1팀, 영업2팀, 영업1팀] -> 영업1팀이 2개 그룹으로 분리됨
// 정렬 후: [영업1팀, 영업1팀, 영업2팀] -> 영업1팀이 하나로 병합됨
val employees = employeeRepository.findAll().sortedBy { it.department }
```

---

### bundle 범위 오류가 발생합니다

**증상**: `RANGE_CONFLICT` 오류와 함께 bundle 관련 메시지가 표시됩니다.

**원인 및 해결**:

|             원인              | 해결 방법                                           |
|:---------------------------:|-------------------------------------------------|
| repeat 영역이 bundle 안팎에 걸쳐 있음 | repeat 영역이 bundle에 완전히 포함되거나 완전히 바깥에 있도록 조정하세요. |
|         bundle이 중첩됨         | bundle은 서로 중첩될 수 없습니다. 범위를 분리하세요.               |
|       bundle 범위 형식 오류       | `A1:B10` 같은 올바른 범위를 사용하세요.                      |

---

### 숫자가 텍스트로 표시됩니다

**증상**: 숫자 데이터에 천 단위 구분자가 적용되지 않거나 텍스트로 인식됩니다.

**해결**:
- 데이터 타입 확인: `Int`, `Long`, `Double` 등 숫자 타입으로 전달하세요. (String이 아닌)
- 템플릿 셀에 숫자 서식이 적용되어 있는지 확인하세요.

---

### 조건부 서식이 확장된 행에 적용되지 않습니다

**증상**: 반복 영역의 조건부 서식이 원래 행에만 적용됩니다.

**해결**: TBEG은 repeat 영역의 조건부 서식 범위를 자동으로 확장합니다. 조건부 서식의 적용 범위가 repeat의 `range` 파라미터와 일치하는지 확인하세요.

---

### 이미지 URL로 지정한 이미지가 삽입되지 않습니다

**증상**: `imageUrl()`로 이미지 URL을 지정했지만 결과 파일에 이미지가 없습니다.

**원인 및 해결**:

|     원인     |           로그 메시지           | 해결 방법                               |
|:----------:|:--------------------------:|:------------------------------------|
| URL 접근 불가  |  `이미지 다운로드 실패: HTTP 404`   | URL이 유효한지 브라우저에서 직접 확인하세요.          |
| 네트워크 타임아웃  | `이미지 다운로드 실패: ...Timeout`  | 서버 응답 속도를 확인하세요. (연결 5초, 읽기 10초 제한) |
|  파일 크기 초과  |  `이미지 다운로드 중단: 크기 제한 초과`   | 이미지 크기를 10MB 미만으로 줄이세요.             |
|  리다이렉트 과다  |      `최대 리다이렉트 횟수 초과`      | URL의 리다이렉트 체인을 확인하세요. (최대 3회)       |

> [!TIP]
> 같은 이미지를 여러 보고서에서 반복 사용한다면 `imageUrlCacheTtlSeconds`를 설정하여 불필요한 다운로드를 줄일 수 있습니다.
>
> ```kotlin
> TbegConfig(imageUrlCacheTtlSeconds = 60)  // 60초간 캐싱
> ```

---

### hideFields를 지정했지만 필드가 숨겨지지 않습니다

**증상**: `hideFields()`로 숨길 필드를 지정했지만 결과 파일에서 해당 필드가 그대로 출력됩니다.

**원인 및 해결**:

- **필드명 불일치**: `hideFields`에 지정한 필드명이 템플릿의 마커(`${emp.salary}`)에서 사용하는 필드명과 정확히 일치하는지 확인하세요.
- **컬렉션명 불일치**: `hideFields("employees", "salary")`에서 첫 번째 인자는 repeat의 컬렉션명과 일치해야 합니다.
- **repeat 밖 필드**: hideFields는 repeat의 반복 항목 필드에만 적용됩니다. repeat과 무관한 단순 변수에는 적용되지 않습니다.

> [!NOTE]
> 템플릿에 hideable 마커 없이 `hideFields`를 지정하면 기본 정책(`WARN_AND_HIDE`)에 따라 해당 셀이 DIM 모드로 숨겨지고 경고 로그가 출력됩니다. DELETE 모드로 열을 물리적으로 제거하려면 템플릿에 `${hideable(value=객체.필드명)}` 마커를 추가하세요. `unmarkedHidePolicy`를 `ERROR`로 설정하면 마커 없는 필드에 대해 예외가 발생합니다.

---

### 데이터 영역만 숨겨지고 타이틀/푸터는 그대로 남습니다

**증상**: `hideFields()`로 필드를 숨겼는데 데이터 행의 값만 숨겨지고, 필드 타이틀이나 합계 행 등은 그대로 출력됩니다.

**원인**: hideable 마커에 `bundle` 파라미터가 지정되지 않았거나, hideable 마커 없이 `hideFields`만 지정한 경우입니다. bundle이 없으면 마커가 위치한 데이터 셀만 숨김 대상이 됩니다.

**해결**: hideable 마커의 `bundle` 파라미터로 타이틀, 합계 등 함께 숨길 범위를 지정하세요.

```
${hideable(value=emp.salary, bundle=C1:C4)}
```

위 예시에서 `C1:C4`는 필드 타이틀(C1), 데이터 행(C2~C3), 합계(C4)를 모두 포함합니다. 숨길 때 이 범위가 함께 처리됩니다.

---

### DIM 모드에서 필드 타이틀의 글자색이 변경됩니다

**증상**: DIM 모드를 사용했는데 필드 타이틀의 글자색이 연한 색으로 변경되었습니다.

**참고**: DIM 모드는 repeat 데이터 영역에는 배경색 + 글자색 + 값 제거를 적용하고, bundle 범위 중 repeat 밖 영역(필드 타이틀 등)에는 글자색만 연한 색으로 변경합니다. 배경색과 값은 유지됩니다.

---

## 4. 성능 문제

### 생성 속도가 느립니다

아래 단계를 순서대로 확인하세요.

**1단계: count 제공 여부 확인**

count를 제공하면 데이터 이중 순회를 방지하여 성능이 개선됩니다.

```kotlin
items("employees", employeeCount) {
    employeeRepository.streamAll().iterator()
}
```

**2단계: 지연 로딩 사용**

모든 데이터를 미리 로드하지 말고 Lambda를 활용하세요.

```kotlin
items("employees") {
    employeeRepository.findAll().iterator()
}
```

**3단계: DB 스트리밍 사용**

JPA Stream 또는 MyBatis Cursor를 사용하여 DB에서 대용량 데이터를 스트리밍으로 처리하세요.

```kotlin
items("employees", count) {
    employeeRepository.streamAll().iterator()
}
```

### 데이터 크기별 권장 설정

|      데이터 크기 |   예상 생성 시간 | 권장 방식                                             |
|------------:|-----------:|:--------------------------------------------------|
|     ~1,000행 |      ~20ms | Map 방식으로 충분합니다.                                   |
|    ~10,000행 |     ~110ms | simpleDataProvider + count                        |
|    ~50,000행 |     ~500ms | simpleDataProvider + count + DB Stream            |
|   ~100,000행 |        ~1초 | simpleDataProvider + count + DB Stream            |
|   ~500,000행 |        ~5초 | 커스텀 DataProvider + DB Stream + `generateToFile()` |
| ~1,000,000행 |        ~9초 | 커스텀 DataProvider + DB Stream + `generateToFile()` |

> 예상 생성 시간은 3개 컬럼 repeat + SUM 수식 기준(DataProvider + generateToFile)입니다. 컬럼 수, 수식 복잡도, 서버 사양에 따라 달라질 수 있습니다.

---

## 5. Spring Boot 관련

### `ExcelGenerator` Bean이 등록되지 않습니다

**증상**: `NoSuchBeanDefinitionException: No qualifying bean of type 'ExcelGenerator'`

**해결**:
1. 의존성 확인: `io.github.jogakdal:tbeg` 의존성이 추가되어 있는지 확인하세요.
2. `@SpringBootApplication` 클래스가 있는 패키지 구조를 확인하세요.
3. 커스텀 Bean을 직접 등록한 경우 `@ConditionalOnMissingBean`으로 인해 자동 설정이 비활성화될 수 있습니다.

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
