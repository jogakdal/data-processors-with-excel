# TBEG 마이그레이션 가이드

## 1.2.1 -> 1.2.2

### 새 기능: 선택적 필드 노출 (hideable)

상황에 따라 특정 필드의 노출을 제한할 수 있는 기능이 추가되었습니다.

- 신규 마커: `${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}`
- 신규 API: `ExcelDataProvider.getHiddenFields()` (기본 구현 제공, 기존 코드 영향 없음)
- 신규 API: `SimpleDataProvider.Builder.hideFields()`
- 신규 설정: `TbegConfig.unmarkedHidePolicy`
- **Breaking Change 없음**: 기존 코드는 수정 없이 동작합니다

### 업그레이드 방법

의존성 버전만 변경하면 됩니다.

```kotlin
// gradle.properties
moduleVersion.tbeg=1.2.2
```

---

## 1.1.x -> 1.2.0

### StreamingMode 폐지

1.2.0부터 항상 스트리밍 모드로 동작합니다. 기존 `StreamingMode.DISABLED` 설정은 무시됩니다.

**마이그레이션:**
- `TbegConfig(streamingMode = StreamingMode.DISABLED)` -> `TbegConfig()` (또는 `TbegConfig.default()`)
- `TbegConfig.forSmallData()` -> `TbegConfig.default()`
- Spring 설정 `streaming-mode: disabled` -> 설정 제거
- `TemplateRenderingEngine(StreamingMode.xxx)` -> `TemplateRenderingEngine()`

deprecated API는 하위 호환성을 위해 유지되지만, 향후 버전에서 제거됩니다.

---

## 1.1.1 -> 1.1.2

### 버그 수정

1.1.2는 비반복 영역 처리와 크로스 시트 마커 그룹핑의 버그 수정 릴리스입니다.

### 업그레이드 방법

의존성 버전만 변경하면 됩니다. API 변경은 없습니다.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.2")
}
```

---

## 1.0.x -> 1.1.0

### 새 기능

#### 빈 컬렉션 처리 (`empty` 파라미터)

repeat 마커에 `empty` 파라미터를 추가하면, 컬렉션이 비어있을 때 대체 내용을 표시할 수 있습니다.

```
${repeat(employees, A2:C2, emp, DOWN, A10:C10)}
```

자세한 내용: [템플릿 문법 - 빈 컬렉션 처리](./reference/template-syntax.md#35-빈-컬렉션-처리-empty)

#### 같은 행의 다중 독립 repeat 영역

하나의 행에 열 범위가 겹치지 않는 여러 repeat 영역을 배치할 수 있습니다.

```
| ${repeat(employees, A2:B2, emp)} | | ${repeat(departments, D2:E2, dept)} |
```

자세한 내용: [템플릿 문법 - 다중 반복 영역](./reference/template-syntax.md#34-다중-반복-영역)

#### 크로스 시트 수식 참조 확장

다른 시트의 repeat 영역을 참조하는 수식이 자동으로 확장됩니다.

```
=SUM(Sheet2!B3:B3) -> =SUM(Sheet2!B3:B5)  // Sheet2의 repeat이 3행 확장된 경우
```

자세한 내용: [템플릿 문법 - 관련 요소 자동 조정](./reference/template-syntax.md#36-관련-요소-자동-조정)

---

## 1.1.0 -> 1.1.1

### 버그 수정

1.1.1은 다중 repeat 영역 처리의 버그 수정 릴리스입니다. 1.1.0을 사용 중이라면 업그레이드를 권장합니다.

- **같은 행의 다중 독립 repeat 영역**: 일부 영역이 누락되거나 non-repeat 셀이 중복 렌더링되는 버그 수정
- **다중 repeat 변수 인식**: 같은 행의 여러 repeat 변수를 모두 인식하도록 개선

### 새 기능

- **중복 마커 감지**: 같은 컬렉션 + 같은 대상 범위의 repeat 마커, 또는 같은 이름 + 같은 위치 + 같은 크기의 image 마커가 중복되면 경고 로그를 출력합니다

### 업그레이드 방법

의존성 버전만 변경하면 됩니다. API 변경은 없습니다.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

---

## 다음 단계

- [변경 이력](../CHANGELOG.md) - 전체 버전별 변경 내역
- [모범 사례](./best-practices.md) - 최신 기능 활용 팁
- [사용자 가이드](./user-guide.md) - TBEG 사용법
