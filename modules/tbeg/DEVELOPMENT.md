# TBEG 개발 가이드

이 문서는 Template-Based Excel Generator(TBEG) 모듈의 아키텍처, 구현 원칙, 개발 가이드라인을 정의합니다.
**코드 수정 시 이 문서도 함께 갱신해야 합니다.**

## 목차

1. [아키텍처 개요](#아키텍처-개요)
2. [프로젝트 구조](#프로젝트-구조)
3. [핵심 컴포넌트](#핵심-컴포넌트)
4. [템플릿 문법](#템플릿-문법)
5. [구현된 기능](#구현된-기능)
6. [구현 원칙](#구현-원칙)
7. [설정 옵션](#설정-옵션)
8. [제한사항](#제한사항)
9. [내부 최적화](#내부-최적화)
10. [확장 포인트](#확장-포인트)
11. [테스트 가이드](#테스트-가이드)

---

## 아키텍처 개요

### 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│                     ExcelGenerator                          │
│                      (Public API)                           │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      TbegPipeline                           │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │ChartExtract  │ → │PivotExtract  │ → │TemplateRender│     │
│  └──────────────┘   └──────────────┘   └──────┬───────┘     │
│                                               │             │
│                                               ▼             │
│                                    ┌──────────────────┐     │
│                                    │ RenderingEngine  │     │
│                                    │ ┌──────┬───────┐ │     │
│                                    │ │ XSSF │ SXSSF │ │     │
│                                    │ └──────┴───────┘ │     │
│                                    └──────────────────┘     │
│                                               │             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────▼───────┐     │
│  │  Metadata    │ ← │ ChartRestore │ ← │ NumberFormat │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 파이프라인 처리 순서

| 순서 | 프로세서               | 클래스                           | 역할                      | 실행 조건    |
|----|--------------------|-------------------------------|-------------------------|----------|
| 1  | ChartExtract       | `ChartExtractProcessor`       | 차트 정보 추출 및 임시 제거        | SXSSF 모드 |
| 2  | PivotExtract       | `PivotExtractProcessor`       | 피벗 테이블 정보 추출            | 항상       |
| 3  | TemplateRender     | `TemplateRenderProcessor`     | 템플릿 렌더링 (XSSF/SXSSF 전략) | 항상       |
| 4  | NumberFormat       | `NumberFormatProcessor`       | 숫자 서식 자동 적용             | 항상       |
| 5  | XmlVariableReplace | `XmlVariableReplaceProcessor` | XML 내 변수 치환             | 항상       |
| 6  | PivotRecreate      | `PivotRecreateProcessor`      | 피벗 테이블 재생성              | 피벗 존재 시  |
| 7  | ChartRestore       | `ChartRestoreProcessor`       | 차트 복원 및 데이터 범위 조정       | 차트 존재 시  |
| 8  | Metadata           | `MetadataProcessor`           | 문서 메타데이터 적용             | 항상       |

### 렌더링 전략 (Strategy Pattern)

| 전략    | 클래스                      | 사용 조건         | 특징                  |
|-------|--------------------------|---------------|---------------------|
| SXSSF | `SxssfRenderingStrategy` | 스트리밍 모드 (기본값) | 100행 버퍼, 메모리 효율적    |
| XSSF  | `XssfRenderingStrategy`  | 비스트리밍 모드      | 전체 메모리 로드, 모든 기능 지원 |

---

## 프로젝트 구조

```
src/main/kotlin/io/github/jogakdal/tbeg/
├── ExcelGenerator.kt                       # 메인 진입점 (Public API)
├── ExcelDataProvider.kt                    # 데이터 제공 인터페이스
├── SimpleDataProvider.kt                   # Map 기반 간단한 DataProvider 구현
├── TbegConfig.kt                           # 설정 클래스 (Builder 패턴)
├── DocumentMetadata.kt                     # 문서 메타데이터
├── Enums.kt                                # StreamingMode, FileNamingMode 등 열거형
│
├── async/                                  # 비동기 처리
│   ├── ExcelGenerationListener.kt          # 콜백 인터페이스
│   ├── GenerationJob.kt                    # 비동기 작업 핸들
│   ├── GenerationResult.kt                 # 생성 결과 DTO
│   └── ProgressInfo.kt                     # 진행률 정보
│
├── engine/                                 # 내부 엔진 (internal)
│   ├── core/                               # 핵심 유틸리티
│   │   ├── ChartProcessor.kt               # 차트 추출/복원
│   │   ├── PivotTableProcessor.kt          # 피벗 테이블 처리
│   │   ├── XmlVariableProcessor.kt         # XML 내 변수 치환
│   │   └── ExcelUtils.kt                   # 유틸리티 함수
│   │
│   ├── pipeline/                           # 처리 파이프라인
│   │   ├── TbegPipeline.kt                 # 파이프라인 정의
│   │   ├── ExcelProcessor.kt               # 프로세서 인터페이스
│   │   ├── ProcessingContext.kt            # 처리 컨텍스트
│   │   └── processors/                     # 개별 프로세서 (8개)
│   │       ├── ChartExtractProcessor.kt
│   │       ├── ChartRestoreProcessor.kt
│   │       ├── MetadataProcessor.kt
│   │       ├── NumberFormatProcessor.kt
│   │       ├── PivotExtractProcessor.kt
│   │       ├── PivotRecreateProcessor.kt
│   │       ├── TemplateRenderProcessor.kt
│   │       └── XmlVariableReplaceProcessor.kt
│   │
│   └── rendering/                          # 렌더링 전략
│       ├── RenderingStrategy.kt            # 렌더링 전략 인터페이스
│       ├── AbstractRenderingStrategy.kt    # 공통 로직
│       ├── XssfRenderingStrategy.kt        # XSSF (비스트리밍)
│       ├── SxssfRenderingStrategy.kt       # SXSSF (스트리밍)
│       ├── TemplateRenderingEngine.kt      # 렌더링 엔진
│       ├── TemplateAnalyzer.kt             # 템플릿 분석기
│       ├── parser/                         # 마커 파서 (통합)
│       │   ├── MarkerDefinition.kt         #   마커 정의
│       │   ├── ParameterParser.kt          #   파라미터 파싱
│       │   ├── ParsedMarker.kt             #   파싱 결과
│       │   └── UnifiedMarkerParser.kt      #   통합 파서
│       ├── WorkbookSpec.kt                 # 워크북/시트/셀 명세
│       ├── PositionCalculator.kt           # 반복 확장 위치 계산
│       ├── StreamingDataSource.kt          # 스트리밍 데이터 소스
│       ├── ImageInserter.kt                # 이미지 삽입
│       ├── FormulaAdjuster.kt              # 수식 조정
│       ├── RepeatExpansionProcessor.kt     # 반복 영역 확장
│       └── SheetLayoutApplier.kt           # 레이아웃 적용
│
├── exception/                              # 예외 클래스
│   ├── TemplateProcessingException.kt
│   ├── MissingTemplateDataException.kt
│   └── FormulaExpansionException.kt
│
└── spring/                                 # Spring Boot 통합
    ├── TbegAutoConfiguration.kt            # 자동 설정
    └── TbegProperties.kt                   # 설정 속성
```

---

## 핵심 컴포넌트

### Public API

| 클래스                  | 역할                                                         |
|----------------------|------------------------------------------------------------|
| `ExcelGenerator`     | 메인 진입점. `generate()`, `generateAsync()`, `submit()` 메서드 제공 |
| `ExcelDataProvider`  | 데이터 제공 인터페이스. 컬렉션/이미지 지연 로딩 지원                             |
| `SimpleDataProvider` | Map 기반 간단한 DataProvider 구현. 컬렉션/이미지 지연 로딩 지원               |
| `TbegConfig`         | 설정 옵션 (Builder 패턴)                                         |
| `DocumentMetadata`   | 문서 메타데이터 (제목, 작성자 등)                                       |

### 파이프라인

| 클래스                 | 역할                              |
|---------------------|---------------------------------|
| `TbegPipeline`      | 프로세서 체인 관리 및 실행                 |
| `ExcelProcessor`    | 프로세서 인터페이스 (`process(context)`) |
| `ProcessingContext` | 프로세서 간 공유 데이터 (워크북, 확장 정보 등)    |

### 렌더링 엔진

| 클래스                       | 역할                               |
|---------------------------|----------------------------------|
| `TemplateRenderingEngine` | 렌더링 전략 선택 및 실행                   |
| `TemplateAnalyzer`        | 템플릿 분석 (마커 파싱, 정규식 정의)           |
| `WorkbookSpec`            | 분석된 템플릿 명세 (SheetSpec, CellSpec) |
| `PositionCalculator`      | repeat 확장 시 셀 위치 계산              |
| `FormulaAdjuster`         | 수식 참조 자동 확장                      |

### 스트리밍 지원

| 클래스                      | 역할                    |
|--------------------------|-----------------------|
| `SxssfRenderingStrategy` | SXSSF 기반 스트리밍 렌더링     |
| `StreamingDataSource`    | Iterator 기반 데이터 순차 소비 |

### 비동기 처리

| 클래스                       | 역할                                          |
|---------------------------|---------------------------------------------|
| `GenerationJob`           | 비동기 작업 핸들 (취소, 대기 지원)                       |
| `ExcelGenerationListener` | 콜백 인터페이스 (onStarted, onCompleted, onFailed) |
| `GenerationResult`        | 생성 결과 DTO                                   |

---

## 템플릿 문법

### 문법 형태 개요

TBEG는 두 가지 형태의 마커를 지원합니다:

| 형태         | 문법             | 위치   | 용도                 |
|------------|----------------|------|--------------------|
| **텍스트 마커** | `${...}`       | 셀 값  | 일반적인 사용            |
| **수식 마커**  | `=TBEG_*(...)` | 셀 수식 | Excel에서 마커가 보이지 않게 |

수식 마커는 Excel에서 `#NAME?` 오류로 표시되지만, 생성 시 올바르게 처리됩니다.

### 변수 치환

**텍스트 마커:**
```
${변수명}
${객체.속성}
${객체.속성.서브속성}
```

**지원 타입:**
- data class, POJO 프로퍼티/필드
- Map의 키 접근
- getter 메서드 (`getFieldName()`)

### 반복 처리 (repeat)

**텍스트 마커:**
```
${repeat(컬렉션, 범위, 변수)}
${repeat(컬렉션, 범위, 변수, DOWN)}
${repeat(컬렉션, 범위, 변수, RIGHT)}
```

**수식 마커:**
```
=TBEG_REPEAT(컬렉션, 범위, 변수)
=TBEG_REPEAT(컬렉션, 범위, 변수, DOWN)
=TBEG_REPEAT(컬렉션, 범위, 변수, RIGHT)
```

**파라미터:**

| 파라미터  | 설명                 | 예시          |
|-------|--------------------|-------------|
| 컬렉션   | 데이터의 키 이름          | `employees` |
| 범위    | 반복할 셀 범위           | `A2:C2`     |
| 변수    | 각 아이템을 참조할 변수명     | `emp`       |
| 방향    | DOWN (기본) 또는 RIGHT | `DOWN`      |
| empty | 빈 컬렉션 시 대체 범위 (선택) | `A10:C10`   |

**예시:**
```
${repeat(employees, A2:C2, emp)}
${repeat(employees, A2:C2, emp, DOWN, A10:C10)}
=TBEG_REPEAT(employees, A2:C2, emp)
=TBEG_REPEAT(employees, A2:C2, emp, DOWN, A10:C10)
=TBEG_REPEAT(months, B1:B2, m, RIGHT)
```

**empty 범위 지원 형식:**
- 일반 범위: `A10:C10`
- 절대 좌표: `$A$10:$C$10` ($ 기호는 파싱 시 무시)
- 시트 참조: `'Sheet2'!A1:C1`

**마커 위치:** 반복 범위 밖 어디든지 가능 (다른 시트도 가능)

### 이미지 삽입

**텍스트 마커:**
```
${image(이름)}
${image(이름, 위치)}
${image(이름, 위치, 크기)}
```

**수식 마커:**
```
=TBEG_IMAGE(이름)
=TBEG_IMAGE(이름, 위치)
=TBEG_IMAGE(이름, 위치, 크기)
```

**크기 옵션:**

| 옵션                    | 설명                 |
|-----------------------|--------------------|
| `fit` 또는 `0:0`        | 셀/범위 크기에 맞춤 (기본값)  |
| `original` 또는 `-1:-1` | 원본 크기 유지           |
| `200:150`             | 너비 200px, 높이 150px |
| `200:-1`              | 너비 200px, 높이 비율 유지 |
| `-1:150`              | 높이 150px, 너비 비율 유지 |
| `0:-1`                | 셀 너비에 맞춤, 높이 비율 유지 |
| `-1:0`                | 셀 높이에 맞춤, 너비 비율 유지 |

**예시:**
```
${image(logo)}
${image(logo, B2)}
${image(logo, B2:D4)}
${image(logo, B2, 200:150)}
${image(logo, B2, original)}
=TBEG_IMAGE(logo, B2, 200:150)
```

### 컬렉션 크기

**텍스트 마커:**
```
${size(컬렉션)}
```

**수식 마커:**
```
=TBEG_SIZE(컬렉션)
```

**예시:**
```
총 직원 수: ${size(employees)}명
=TBEG_SIZE(employees)
```

### 수식 내 변수

수식에서도 변수를 사용할 수 있습니다:

```
=HYPERLINK("${url}", "${text}")
=SUM(B${startRow}:B${endRow})
=IF(${condition}, "yes", "no")
```

### 복합 텍스트

여러 변수와 텍스트를 함께 사용:

```
작성자: ${author} (${department})
```

### 마커 파서 구조

마커 파싱은 `engine/rendering/parser/` 패키지의 **통합 마커 파서**에서 처리합니다:

```
parser/
├── MarkerDefinition.kt       # 마커별 파라미터 스키마 정의
├── ParameterParser.kt        # 공통 파라미터 파싱 로직
├── ParsedMarker.kt           # 파싱 결과 데이터
└── UnifiedMarkerParser.kt    # 통합 파서 (진입점)
```

| 클래스                   | 역할                               |
|-----------------------|----------------------------------|
| `MarkerDefinition`    | 마커별 파라미터 정의 (이름, 필수 여부, 기본값, 별칭) |
| `ParameterParser`     | 위치 기반 + 명시적 파라미터 파싱              |
| `ParsedMarker`        | 파싱 결과 (파라미터 Map)                 |
| `UnifiedMarkerParser` | 텍스트/수식 마커 감지 및 `CellContent` 변환  |

**지원 마커:**

| 마커       | 파라미터                                               |
|----------|----------------------------------------------------|
| `repeat` | `collection`, `range`, `var`, `direction`, `empty` |
| `image`  | `name`, `position`, `size`                         |
| `size`   | `collection`                                       |

**파라미터 형식:**
- 위치 기반: `${repeat(employees, A2:C2, emp)}`
- 위치 기반 (중간 생략): `${repeat(employees, A2:C2, , , A10:C10)}`
- 명시적: `${repeat(collection=employees, range=A2:C2, var=emp)}`

> **주의**: 위치 기반과 명시적 파라미터는 혼합할 수 없습니다.

**새 마커 추가 시:**
1. `MarkerDefinition.kt`에 마커 정의 추가
2. `CellContent` sealed class에 새 타입 추가
3. `UnifiedMarkerParser.convertToContent()`에 변환 로직 추가

---

## 구현된 기능

### 지원 기능 목록

| 기능         | 설명                   | 처리 위치                     |
|------------|----------------------|---------------------------|
| 변수 치환      | 단순 값 바인딩             | `TemplateRenderingEngine` |
| 중첩 변수      | 객체 속성 접근             | `TemplateRenderingEngine` |
| 반복 (DOWN)  | 행 방향 확장              | `RenderingStrategy`       |
| 반복 (RIGHT) | 열 방향 확장              | `RenderingStrategy`       |
| 빈 컬렉션 처리   | empty 파라미터로 대체 내용 지정 | `RenderingStrategy`       |
| 이미지 삽입     | 동적 이미지               | `ImageInserter`           |
| 차트         | 데이터 범위 자동 확장         | `ChartProcessor`          |
| 피벗 테이블     | 소스 범위 자동 확장          | `PivotTableProcessor`     |
| 수식 확장      | repeat 영역 참조 자동 확장   | `FormulaAdjuster`         |
| 셀 병합       | 위치 자동 조정             | `PositionCalculator`      |
| 조건부 서식     | 범위 자동 조정             | `FormulaAdjuster`         |
| 머리글/바닥글    | 변수 치환 지원             | `XmlVariableProcessor`    |
| 파일 암호화     | 열기 암호 설정             | `ExcelGenerator`          |
| 비동기 처리     | 백그라운드 생성 + 진행률       | `GenerationJob`           |

---

## 구현 원칙

### 1. 렌더링 원칙

#### 1.1 템플릿 서식 완전 보존
템플릿에 작성된 모든 서식(정렬, 글꼴, 색상, 테두리, 채우기, 행 높이 등)은 생성된 Excel에 동일하게 적용되어야 합니다.

#### 1.2 Repeat 행 스타일 원칙
repeat에서 확장된 모든 행은 **repeat 기준 템플릿 행**의 스타일(높이, 서식)을 따릅니다.

- 단일 행 repeat: 모든 확장 행이 동일한 템플릿 행의 스타일 적용
- 다중 행 repeat: 템플릿 행 패턴이 순환하며 적용

```
예시: 단일 행 repeat (templateRow 6)
- actualRow 6 -> templateRow 6 스타일
- actualRow 7 -> templateRow 6 스타일
- actualRow 8 -> templateRow 6 스타일
```

#### 1.3 행 높이 충돌 해결
같은 actualRow에 여러 템플릿 행이 매핑되는 경우 (서로 다른 열 그룹), `maxOf`로 가장 높은 높이를 적용합니다.

- 이유: Excel에서 행 높이는 행 전체에 적용되므로, 모든 셀이 잘 표시되도록 함
- 처리 순서와 무관하게 일관된 결과 보장

#### 1.4 열 그룹 독립성
서로 다른 열 범위의 repeat은 독립적으로 위치가 계산됩니다.

```
예시:
- A-C 열: employees repeat (확장 +2행)
- F-H 열: department repeat (확장 +3행)

actualRow 10에서:
- A-C 열 관점: templateRow = actualRow - employees확장량
- F-H 열 관점: templateRow = actualRow - department확장량 (해당 열 범위에서만)
```

### 1.5 빈 컬렉션 처리 (emptyRange)

컬렉션이 비어있을 때의 동작을 제어합니다.

#### 기본 동작 (empty 미지정)
빈 컬렉션이면 반복 영역에 빈 행(또는 열)이 1개 출력됩니다.

#### empty 파라미터 지정 시
- **emptyRangeContent 미리 읽기**: 템플릿 분석 단계에서 empty 범위의 셀 내용/스타일을 `CellSnapshot`으로 저장
- **빈 컬렉션 렌더링**: 반복 영역 위치에 저장해둔 emptyRangeContent 출력
- **empty 원본 셀 처리**: 결과 파일에서 빈 셀 + 기본 스타일로 클리어

#### emptyRange 크기 처리

| emptyRange 크기 | 처리 방식                      |
|---------------|----------------------------|
| 단일 셀 (더 작음)   | 반복 영역 전체를 셀 병합 후 내용 삽입     |
| 다중 셀 (더 작음)   | emptyRange만큼만 출력, 나머지는 빈 셀 |
| 더 큼           | 반복 영역 크기만큼만 출력, 나머지는 버림    |

#### 처리 위치

| 모드                  | 처리 함수                                                    |
|---------------------|----------------------------------------------------------|
| XSSF                | `XssfRenderingStrategy.writeEmptyRangeContent()`         |
| SXSSF (streaming)   | `SxssfRenderingStrategy.writeRepeatCellsForRow()`        |
| SXSSF (pendingRows) | `SxssfRenderingStrategy.collectEmptyRangeContentCells()` |

### 2. 메모리 관리 원칙 (SXSSF 모드)

#### 2.1 컬렉션 전체 메모리 로드 금지
SXSSF 모드에서는 대용량 데이터 처리를 위해 컬렉션 전체를 메모리에 올리지 않습니다.

| 모드    | 메모리 정책              | 이유            |
|-------|---------------------|---------------|
| SXSSF | 컬렉션 전체를 메모리에 올리지 않음 | 대용량 데이터 처리 목적 |
| XSSF  | 전체 메모리 로드 허용        | 소량 데이터 전용 모드  |

#### 2.2 DataProvider 재호출 방식
같은 컬렉션이 여러 repeat 영역에서 사용되면 DataProvider를 재호출합니다.

- 임시 파일(DiskCachedCollection) 사용하지 않음
- DataProvider.getItems()가 같은 데이터를 다시 제공할 수 있어야 함 (사용자 조건)

#### 2.3 Iterator 순차 소비
현재 처리 중인 아이템 1개만 메모리에 유지합니다.

```kotlin
// StreamingDataSource 사용
fun advanceToNextItem(repeatKey: RepeatKey): Any?
fun getCurrentItem(repeatKey: RepeatKey): Any?
```

### 3. 위치 계산 원칙

#### 3.1 수식/범위 자동 확장
repeat 영역에 포함된 수식과 범위 참조는 확장량만큼 자동 조정됩니다.

```
예시: =SUM(C6) -> =SUM(C6:C105) (100개 아이템 확장 시)
```

**참조 유형별 처리:**

| 참조 유형               | 처리 방식                   |
|---------------------|-------------------------|
| 상대 참조 (`B3`)        | repeat 확장에 따라 범위로 확장    |
| 절대 참조 (`$B$3`)      | 확장하지 않음 (고정 위치)         |
| 행 절대 (`B$3`)        | DOWN 방향 확장 안 함          |
| 열 절대 (`$B3`)        | RIGHT 방향 확장 안 함         |
| 다른 시트 (`Sheet2!B3`) | 해당 시트의 repeat 확장 정보로 처리 |

**다른 시트 참조 처리:**
- `expandToRangeWithCalculator()`에 `otherSheetExpansions` 파라미터로 다른 시트의 확장 정보 전달
- `SheetExpansionInfo`에 시트별 `expansions`와 `collectionSizes` 포함
- 시트 이름 추출: `Sheet1!` → `"Sheet1"`, `'Sheet Name'!` → `"Sheet Name"`

#### 3.2 정적 요소 위치 이동
repeat 확장에 영향받는 정적 요소(수식, 병합 셀, 조건부 서식 등)는 확장량만큼 밀립니다.

#### 3.3 PositionCalculator 위치 결정 규칙

1. 어느 repeat에도 영향받지 않는 요소: 템플릿 위치 그대로 고정
2. 하나의 repeat에만 영향받는 요소: 그 repeat 확장만큼 밀림
3. 두 개 이상의 repeat에 영향받는 요소: 가장 많이 밀리는 위치로 이동

### 4. 숫자 서식 원칙

#### 4.1 자동 숫자 서식 지정 조건
라이브러리에 의해 자동 생성된 값이 숫자 타입이고, 해당 셀의 "표시 형식"이 없거나 "일반"인 경우 자동으로 숫자 서식을 적용합니다.

- 정수: `pivotIntegerFormatIndex` (기본값 3, `#,##0`)
- 소수: `pivotDecimalFormatIndex` (기본값 4, `#,##0.00`)

#### 4.2 자동 정렬 지정 조건
라이브러리에 의해 자동 생성된 값이 숫자 타입이고, 해당 셀의 정렬이 "일반"인 경우 자동으로 오른쪽 정렬을 적용합니다.

#### 4.3 기존 서식 보존
위 4.1, 4.2 조건에 해당하더라도 해당 셀의 나머지 모든 서식(글꼴, 색상, 테두리 등)은 템플릿 서식을 유지합니다.

---

## StyleInfo 필수 속성

스타일 복사 시 유지해야 하는 속성 목록:

| 속성                            | 설명     |
|-------------------------------|--------|
| `horizontalAlignment`         | 가로 정렬  |
| `verticalAlignment`           | 세로 정렬  |
| `fontBold`                    | 굵게     |
| `fontItalic`                  | 기울임꼴   |
| `fontUnderline`               | 밑줄     |
| `fontStrikeout`               | 취소선    |
| `fontName`                    | 글꼴 이름  |
| `fontSize`                    | 글꼴 크기  |
| `fontColorRgb`                | 글꼴 색상  |
| `fillForegroundColorRgb`      | 채우기 색상 |
| `fillPatternType`             | 채우기 패턴 |
| `borderTop/Bottom/Left/Right` | 테두리    |
| `dataFormat`                  | 표시 형식  |

---

## 설정 옵션

### TbegConfig 기본값

| 옵션                        | 기본값                 | 설명                                |
|---------------------------|---------------------|-----------------------------------|
| `streamingMode`           | `ENABLED`           | ENABLED (SXSSF) / DISABLED (XSSF) |
| `fileNamingMode`          | `TIMESTAMP`         | TIMESTAMP / NONE                  |
| `timestampFormat`         | `"yyyyMMdd_HHmmss"` | DateTimeFormatter 패턴              |
| `fileConflictPolicy`      | `SEQUENCE`          | SEQUENCE / ERROR                  |
| `progressReportInterval`  | `100`               | 진행률 보고 간격 (행 수)                   |
| `preserveTemplateLayout`  | `true`              | 템플릿 레이아웃 보존                       |
| `pivotIntegerFormatIndex` | `3`                 | 정수 포맷 인덱스 (`#,##0`)               |
| `pivotDecimalFormatIndex` | `4`                 | 소수 포맷 인덱스 (`#,##0.00`)            |
| `missingDataBehavior`     | `WARN`              | WARN / THROW                      |

### 프리셋 설정

```kotlin
// 대용량 처리 최적화
TbegConfig.forLargeData()
// streamingMode = ENABLED, progressReportInterval = 500

// 소량 처리
TbegConfig.forSmallData()
// streamingMode = DISABLED
```

### Spring Boot 설정 (application.yml)

```yaml
tbeg:
  streaming-mode: enabled
  file-naming-mode: timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence
  progress-report-interval: 100
  preserve-template-layout: true
  missing-data-behavior: warn
```

---

## 제한사항

### SXSSF (스트리밍) 모드

| 항목               | 지원 여부 | 비고                        |
|------------------|-------|---------------------------|
| 아래 행 참조 수식       | ✅     | repeat 영역 참조 시 자동 확장      |
| 위쪽 행 참조 수식       | ✅     | 자동 조정됨                    |
| 자동 확장 수식 (SUM 등) | ✅     | 범위 자동 확장                  |
| 차트               | ✅     | Extract/Restore 프로세서로 처리  |
| 피벗 테이블           | ✅     | Extract/Recreate 프로세서로 처리 |

### 일반 제한사항

- repeat 영역은 2D 공간에서 겹쳐야 함
- 같은 열 범위 내 여러 repeat은 세로로 배치 가능
- 시퀀스 번호는 최대 10,000까지 시도

### 내부 상수

| 상수          | 값           | 위치                             |
|-------------|-------------|--------------------------------|
| SXSSF 버퍼 크기 | 100행        | `SxssfRenderingStrategy.kt:49` |
| 이미지 마진      | 1px         | `ImageInserter.kt`             |
| EMU 변환율     | 9525 EMU/px | `ImageInserter.kt`             |

---

## 내부 최적화

### 스타일 캐싱

동일 스타일 중복 생성을 방지합니다.

| 클래스                     | 캐시                         | 용도        |
|-------------------------|----------------------------|-----------|
| `PivotTableProcessor`   | `styleCache` (WeakHashMap) | 피벗 셀 스타일  |
| `NumberFormatProcessor` | `styleCache`               | 숫자 서식 스타일 |

### 필드 캐싱

리플렉션 성능을 최적화합니다.

| 클래스                       | 캐시            | 용도         |
|---------------------------|---------------|------------|
| `TemplateRenderingEngine` | `fieldCache`  | 필드 정보      |
| `TemplateRenderingEngine` | `getterCache` | getter 메서드 |

### calcChain 정리

수식 재계산 오류를 방지합니다.

- XSSF/SXSSF 모두 `clearCalcChain()` 호출
- Excel에서 파일 열 때 수식 재계산 트리거

---

## 확장 포인트

### 새 프로세서 추가

1. `ExcelProcessor` 인터페이스 구현
2. `TbegPipeline`에 프로세서 등록 (순서 중요)

```kotlin
class MyProcessor : ExcelProcessor {
    override val name: String = "MyProcessor"

    override fun shouldProcess(context: ProcessingContext): Boolean = true

    override fun process(context: ProcessingContext): ProcessingContext {
        // 처리 로직
        return context
    }
}
```

**등록 위치:** `ExcelGenerator.kt` 라인 71-80

### 새 렌더링 전략 추가

1. `RenderingStrategy` 인터페이스 구현 (또는 `AbstractRenderingStrategy` 상속)
2. `TemplateRenderingEngine`에서 전략 선택 로직 수정

**전략 선택 위치:** `TemplateRenderingEngine.kt` 라인 48-51

### 새 템플릿 문법 추가

1. `MarkerDefinition.kt`에 마커 정의 추가 (파라미터 스키마)
2. `CellContent` sealed class에 새 타입 추가
3. `UnifiedMarkerParser.convertToContent()`에 변환 로직 추가
4. 렌더링 전략에서 새 명세 처리

**마커 정의 위치:** `parser/MarkerDefinition.kt`

```kotlin
// 새 마커 추가 예시
val NEW_MARKER = MarkerDefinition("newmarker", listOf(
    ParameterDef("param1", required = true),
    ParameterDef("param2", aliases = setOf("p2", "alt")),
    ParameterDef("param3", defaultValue = "default")
))
```

---

## 테스트 가이드

### 테스트 구조

```
src/test/
├── kotlin/io/github/jogakdal/tbeg/
│   ├── TbegTest.kt                     # 통합 테스트
│   ├── PerformanceBenchmark.kt         # 성능 벤치마크
│   ├── engine/
│   │   ├── rendering/
│   │   │   ├── PositionCalculatorTest.kt
│   │   │   ├── FormulaAdjusterTest.kt
│   │   │   └── ...
│   │   └── ...
│   └── ...
└── resources/
    └── templates/                      # 테스트용 템플릿
        ├── template.xlsx
        ├── simple_template.xlsx
        └── ...
```

### 테스트 실행

```bash
# 전체 테스트
./gradlew :tbeg:test

# 특정 테스트
./gradlew :tbeg:test --tests "*PositionCalculatorTest*"
```

### 샘플 실행

```bash
# Kotlin 샘플
./gradlew :tbeg:runSample          # 결과: build/samples/

# Java 샘플
./gradlew :tbeg:runJavaSample      # 결과: build/samples-java/

# Spring Boot 샘플
./gradlew :tbeg:runSpringBootSample  # 결과: build/samples-spring/

# 성능 벤치마크
./gradlew :tbeg:runBenchmark
```

### 테스트 작성 원칙

1. **템플릿 파일 사용**: 실제 Excel 템플릿으로 통합 테스트
2. **출력 파일 검증**: 생성된 Excel 파일을 열어 결과 확인
3. **엣지 케이스**: 빈 데이터, 대용량 데이터, 특수 문자 등 테스트

---

## 성능 벤치마크

### TBEG 성능

**테스트 환경**: Java 21, macOS, 3개 컬럼 repeat + SUM 수식

| 데이터 크기   | DISABLED (XSSF) | ENABLED (SXSSF) | 속도 향상    |
|----------|-----------------|-----------------|----------|
| 1,000행   | 172ms           | 147ms           | 1.2배     |
| 10,000행  | 1,801ms         | 663ms           | **2.7배** |
| 30,000행  | -               | 1,057ms         | -        |
| 50,000행  | -               | 1,202ms         | -        |
| 100,000행 | -               | 3,154ms         | -        |

### 타 라이브러리 비교 (30,000행)

| 라이브러리    | 소요 시간    | 비고                                                          |
|----------|----------|-------------------------------------------------------------|
| **TBEG** | **1.1초** | 스트리밍 모드                                                     |
| JXLS     | 5.2초     | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203) |
