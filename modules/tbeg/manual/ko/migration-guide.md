# TBEG 마이그레이션 가이드

## 1.0.x → 1.1.0

### 클래스 리네이밍

`ExcelGeneratorConfig`가 `TbegConfig`로 리네이밍되었습니다. 기존 이름은 타입 별칭으로 제공되므로 기존 코드는 그대로 동작합니다.

**권장**: 새 이름으로 변경하세요. 타입 별칭은 향후 제거될 수 있습니다.

```kotlin
// 기존 코드 (동작하지만 비권장)
val config = ExcelGeneratorConfig(streamingMode = StreamingMode.ENABLED)

// 권장
val config = TbegConfig(streamingMode = StreamingMode.ENABLED)
```

---

### 새 기능

#### 빈 컬렉션 처리 (`empty` 파라미터)

repeat 마커에 `empty` 파라미터를 추가하면, 컬렉션이 비어있을 때 대체 내용을 표시할 수 있습니다.

```
${repeat(employees, A2:C2, emp, DOWN, A10:C10)}
```

자세한 내용: [템플릿 문법 - 빈 컬렉션 처리](./reference/template-syntax.md#27-빈-컬렉션-처리-empty)

#### 같은 행의 다중 독립 repeat 영역

하나의 행에 열 범위가 겹치지 않는 여러 repeat 영역을 배치할 수 있습니다.

```
| ${repeat(employees, A2:B2, emp)} | | ${repeat(departments, D2:E2, dept)} |
```

자세한 내용: [템플릿 문법 - 다중 반복 영역](./reference/template-syntax.md#24-다중-반복-영역)

#### 크로스 시트 수식 참조 확장

다른 시트의 repeat 영역을 참조하는 수식이 자동으로 확장됩니다.

```
=SUM(Sheet2!B3:B3) → =SUM(Sheet2!B3:B5)  // Sheet2의 repeat이 3행 확장된 경우
```

자세한 내용: [템플릿 문법 - 관련 요소 자동 조정](./reference/template-syntax.md#28-관련-요소-자동-조정)

---

## 1.1.0 → 1.1.1

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
- [사용자 가이드](./user-guide.md) - TBEG 사용법
