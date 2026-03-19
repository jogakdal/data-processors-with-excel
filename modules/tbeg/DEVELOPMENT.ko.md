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
│                   HidePreprocessor                          │
│               (1st pass: hideable 전처리)                    │
│                                                             │
│  hideable 마커 스캔 → hide 대상 결정 → DELETE/DIM 처리           │
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
│                                    │ ┌──────────────┐ │     │
│                                    │ │   Default    │ │     │
│                                    │ └──────────────┘ │     │
│                                    └──────────────────┘     │
│                                               │             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────▼───────┐     │
│  │ ChartRestore │ ← │PivotRecreate │ ← │ZipStreamPost │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 파이프라인 처리 순서

| 순서 | 프로세서               | 클래스                           | 역할                      | 실행 조건         |
|----|--------------------|-------------------------------|-------------------------|----------------|
| 0  | HidePreprocess     | `HidePreprocessor`            | hideable 마커 전처리 (삭제/DIM) | hideFields 존재 시 |
| 1  | ChartExtract       | `ChartExtractProcessor`       | 차트 정보 추출 및 임시 제거        | 항상             |
| 2  | PivotExtract       | `PivotExtractProcessor`       | 피벗 테이블 정보 추출            | 항상             |
| 3  | TemplateRender     | `TemplateRenderProcessor`     | 템플릿 렌더링                 | 항상             |
| 4  | ZipStreamPost      | `ZipStreamPostProcessor`      | 숫자 서식 + 메타데이터 + 변수 치환 + absPath 제거 (ZIP 단일 패스) | 항상             |
| 5  | PivotRecreate      | `PivotRecreateProcessor`      | 피벗 테이블 재생성              | 피벗 존재 시        |
| 6  | ChartRestore       | `ChartRestoreProcessor`       | 차트 복원 및 데이터 범위 조정       | 차트 존재 시        |

### 렌더링 전략

| 클래스                          | 특징                                           |
|------------------------------|----------------------------------------------|
| `StreamingRenderingStrategy` | 내부적으로 Apache POI SXSSF 모드를 사용하여 메모리 효율적으로 처리 |

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
│   │   ├── CommonTypes.kt                  # 공통 타입 (CellCoord, CellArea, IndexRange, CollectionSizes 등)
│   │   ├── ChartProcessor.kt               # 차트 추출/복원
│   │   ├── PivotTableProcessor.kt          # 피벗 테이블 처리
│   │   ├── XmlVariableProcessor.kt         # XML 내 변수 치환
│   │   └── ExcelUtils.kt                   # 유틸리티 함수
│   │
│   ├── pipeline/                           # 처리 파이프라인
│   │   ├── TbegPipeline.kt                 # 파이프라인 정의
│   │   ├── ExcelProcessor.kt               # 프로세서 인터페이스
│   │   ├── ProcessingContext.kt            # 처리 컨텍스트
│   │   └── processors/                     # 개별 프로세서
│   │       ├── ChartExtractProcessor.kt
│   │       ├── ChartRestoreProcessor.kt
│   │       ├── MetadataProcessor.kt        # 메타데이터 적용 (독립 사용 가능)
│   │       ├── NumberFormatProcessor.kt    # 숫자 서식 적용 (독립 사용 가능)
│   │       ├── PivotExtractProcessor.kt
│   │       ├── PivotRecreateProcessor.kt
│   │       ├── TemplateRenderProcessor.kt
│   │       ├── ZipStreamPostProcessor.kt   # ZIP 단일 패스 통합 후처리
│   │       └── zippost/                    # ZIP 후처리 핸들러
│   │           ├── StylesXmlHandler.kt     #   styles.xml 숫자 서식 변형
│   │           ├── SheetXmlHandler.kt      #   sheet*.xml 셀 스타일 교체 (StAX)
│   │           ├── WorkbookXmlHandler.kt   #   workbook.xml absPath 제거
│   │           ├── MetadataXmlHandler.kt   #   docProps/*.xml 메타데이터
│   │           └── VariableXmlHandler.kt   #   기타 XML 변수 치환
│   │
│   ├── preprocessing/                      # 전처리 (렌더링 파이프라인 전 실행)
│   │   ├── HidePreprocessor.kt            #   hideable 전처리기 (마커 스캔, hide 결정, 삭제/DIM)
│   │   ├── HideValidator.kt               #   bundle 범위 검증 (repeat 내 위치, 열 정렬, 병합 셀, 겹침)
│   │   ├── ElementShifter.kt              #   삭제 후 요소 이동 (열/행 시프트, 수식 참조 조정)
│   │   ├── HideableRegion.kt              #   hide 대상 영역 데이터 클래스
│   │   └── CellUtils.kt                   #   셀 유틸리티 (containsCell, setFormulaRaw)
│   │
│   └── rendering/                          # 렌더링 전략
│       ├── RenderingStrategy.kt            # 렌더링 전략 인터페이스
│       ├── AbstractRenderingStrategy.kt    # 공통 로직
│       ├── StreamingRenderingStrategy.kt   # 기본 렌더링 전략
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

| 클래스                       | 역할                                                                            |
|---------------------------|-------------------------------------------------------------------------------|
| `TemplateRenderingEngine` | 렌더링 전략 선택 및 실행                                                                |
| `TemplateAnalyzer`        | 템플릿 분석 (마커 파싱, 중복 마커 감지)                                                      |
| `WorkbookSpec`            | 분석된 템플릿 명세 (SheetSpec, RowSpec, CellSpec, RepeatRegionSpec, BundleRegionSpec) |
| `PositionCalculator`      | repeat 확장 시 셀 위치 계산 (체이닝 알고리즘)                                                |
| `FormulaAdjuster`         | 수식 참조 자동 확장                                                                   |

### 전처리 (preprocessing)

| 클래스                 | 역할                                                 |
|---------------------|----------------------------------------------------|
| `HidePreprocessor`  | 렌더링 파이프라인 전에 hideable 마커를 스캔하고 hide 대상 필드를 삭제/DIM 처리 |
| `HideValidator`     | hideable의 bundle 범위 검증 (repeat 내 위치, 열 정렬, 병합 셀 겹침) |
| `ElementShifter`    | DELETE 모드에서 삭제 후 나머지 요소를 시프트 (열/행, 수식 참조 조정)       |
| `HideableRegion`    | hide 대상 영역 정보 데이터 클래스                              |

### 스트리밍 지원

| 클래스                        | 역할              |
|----------------------------|-----------------|
| `StreamingRenderingStrategy` | 스트리밍 기반 렌더링     |
| `StreamingDataSource`      | Iterator 기반 데이터 순차 소비 |

### 비동기 처리

| 클래스                       | 역할                                          |
|---------------------------|---------------------------------------------|
| `GenerationJob`           | 비동기 작업 핸들 (취소, 대기 지원)                       |
| `ExcelGenerationListener` | 콜백 인터페이스 (onStarted, onCompleted, onFailed) |
| `GenerationResult`        | 생성 결과 DTO                                   |

---

## 템플릿 문법

### 문법 형태 개요

TBEG은 두 가지 형태의 마커를 지원합니다:

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

### 자동 셀 병합 (merge)

**텍스트 마커:**
```
${merge(item.field)}
```

**수식 마커:**
```
=TBEG_MERGE(item.field)
```

**파라미터:**

| 파라미터 | 설명             | 예시         |
|------|----------------|------------|
| field | item.field 형태 | `emp.dept` |

repeat 확장 시 연속된 같은 값의 셀을 자동으로 병합한다.
DOWN repeat에서는 세로 병합, RIGHT repeat에서는 가로 병합이 적용된다.
데이터가 병합 기준으로 사전 정렬되어 있어야 한다.

**예시:**
```
${merge(emp.dept)}
=TBEG_MERGE(emp.dept)
```

### 필드 숨기기 (hideable)

repeat 확장 시 특정 필드(열)를 조건에 따라 숨길 수 있다. 숨길 필드는 `ExcelDataProvider.getHideFields()`로 지정한다.

**텍스트 마커:**
```
${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}
${hideable(emp.salary)}
```

**수식 마커:**
```
=TBEG_HIDEABLE(emp.salary, C1:C3, dim)
=TBEG_HIDEABLE(emp.salary)
```

**파라미터:**

| 파라미터   | 설명                          | 필수 | 기본값    | 별칭           |
|--------|-----------------------------|----|---------|--------------|
| value  | item.field 형태의 필드 참조        | O  |         | field, val   |
| bundle | 함께 숨길 셀 범위                  |    |         | range        |
| mode   | 숨김 모드 (DELETE / DIM)        |    | delete  |              |

**숨김 모드:**

| 모드       | 동작                                |
|----------|-----------------------------------|
| `DELETE` | 물리적으로 삭제하고 나머지 요소를 당긴다 (기본값)     |
| `DIM`    | 데이터 영역에 비활성화 스타일(배경 + 글자색)을 적용하고 값을 제거한다. 필드 타이틀 등 repeat 밖 bundle 영역은 글자색만 변경한다 |

**처리 흐름:**

1. `HidePreprocessor`가 렌더링 파이프라인 전에 실행 (1st pass 전처리)
2. 2-pass 스캔: 1st phase에서 repeat 변수명 파악, 2nd phase에서 ItemField/HideableField 식별
3. `getHideFields()`에 지정된 필드에 대해 DELETE 또는 DIM 처리
4. 숨기지 않는 hideable 마커는 `${item.field}` 형태로 변환되어 일반 ItemField로 처리

**DIM 모드 처리:**
- repeat 데이터 영역만 DIM 처리 (bundle 범위와 repeat 범위의 교집합)
- 필드 타이틀 등 repeat 밖의 셀은 배경/값 변경 대상이 아니다 (글자색만 변경)

**예시:**
```
${repeat(employees, A2:H2, emp)}
${hideable(emp.salary, C1:C3)}          <- salary 필드: C열 + 필드 타이틀(C1) 포함 bundle
${hideable(emp.bonus, D1:D3, dim)}      <- bonus 필드: DIM 모드
${hideable(emp.name)}                   <- name 필드: bundle 없음, DELETE 기본
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
| `merge`  | `field`                                            |
| `bundle` | `range`                                            |
| `hideable` | `value`(필수, 별칭: field/val), `bundle`(별칭: range), `mode`(기본: delete) |

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
| 중복 마커 감지  | 범위 마커 중복 경고 및 자동 제거 | `TemplateAnalyzer`        |
| 변수 치환      | 단순 값 바인딩             | `TemplateRenderingEngine` |
| 중첩 변수      | 객체 속성 접근             | `TemplateRenderingEngine` |
| 반복 (DOWN)  | 행 방향 확장              | `RenderingStrategy`       |
| 반복 (RIGHT) | 열 방향 확장              | `RenderingStrategy`       |
| 빈 컬렉션 처리   | empty 파라미터로 대체 내용 지정 | `RenderingStrategy`       |
| 이미지 삽입     | 동적 이미지               | `ImageInserter`           |
| 자동 셀 병합    | 연속 같은 값 자동 병합        | `MergeTracker`            |
| 차트         | 데이터 범위 자동 확장         | `ChartProcessor`          |
| 피벗 테이블     | 소스 범위 자동 확장          | `PivotTableProcessor`     |
| 수식 확장      | repeat 영역 참조 자동 확장   | `FormulaAdjuster`         |
| 셀 병합       | 위치 자동 조정             | `PositionCalculator`      |
| 조건부 서식     | 범위 자동 조정             | `FormulaAdjuster`         |
| 머리글/바닥글    | 변수 치환 지원             | `XmlVariableProcessor`    |
| 파일 암호화     | 열기 암호 설정             | `ExcelGenerator`          |
| 필드 숨기기     | hideable 마커로 필드 삭제/DIM | `HidePreprocessor`        |
| 비동기 처리     | 백그라운드 생성 + 진행률       | `GenerationJob`           |

---

## 구현 원칙

### 0. 핵심 설계 철학: Excel 네이티브 기능 우선

TBEG은 **Excel이 이미 잘하는 기능을 재구현하지 않습니다.** 집계, 조건부 서식, 차트 렌더링 등은 Excel 네이티브 기능을 그대로 활용합니다. TBEG의 역할은 두 가지입니다:

1. Excel이 자체적으로 수행할 수 없는 **동적 데이터 바인딩**을 제공합니다 -- 변수 치환, repeat 확장, 이미지 삽입
2. 데이터 확장 과정에서 Excel 네이티브 기능이 의도대로 동작하도록 **보존하고 조정**합니다 -- 수식 범위 확장, 조건부 서식 복제, 차트 데이터 범위 조정

이 철학은 아래의 모든 구현 원칙의 근거가 됩니다:
- **렌더링 원칙**: 템플릿 서식을 완전 보존하는 이유는 Excel이 이미 완성한 서식을 존중하기 위함입니다.
- **수식 처리**: 수식을 재계산하지 않고 참조 범위를 조정하는 이유는 계산 자체는 Excel의 몫이기 때문입니다.
- **차트/피벗**: 새로 생성하지 않고 템플릿의 것을 보존하는 이유도 동일합니다.

새로운 기능을 설계할 때 "이것을 Excel 네이티브 기능으로 해결할 수 있는가?"를 먼저 판단하세요. Excel로 가능하다면 TBEG이 별도로 구현하기보다, 사용자가 템플릿에서 Excel 기능을 활용하도록 안내하고, TBEG은 그 기능이 데이터 확장 후에도 올바르게 동작하도록 보장하는 데 집중합니다.

### 1. 렌더링 원칙

#### 1.1 템플릿 서식 완전 보존
템플릿에 작성된 모든 서식(정렬, 글꼴, 색상, 테두리, 채우기, 행 높이 등)은 생성된 Excel에 동일하게 적용되어야 합니다.
**값이 없는 셀도 스타일이 설정되어 있으면 보존 대상입니다.** repeat 확장 시 행 복사/이동 과정에서 `CellType.BLANK` 셀의 스타일을 누락하지 않도록 주의해야 합니다.

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
같은 actualRow에 여러 템플릿 행이 매핑되는 경우 (체이닝으로 서로 다른 열에서 다른 밀림이 발생), `maxOf`로 가장 높은 높이를 적용합니다.

- 이유: Excel에서 행 높이는 행 전체에 적용되므로, 모든 셀이 잘 표시되도록 함
- 처리 순서와 무관하게 일관된 결과 보장

#### 1.4 체이닝 기반 위치 계산
서로 다른 열 범위의 repeat은 독립적으로 위치가 계산됩니다.
**같은 행에 여러 독립 repeat이 있어도** 열 범위가 겹치지 않으면 각각 별도의 `RepeatRegionSpec`으로 관리되어 독립 확장됩니다.

밀림의 전파는 **체이닝 알고리즘**으로 처리된다:
- 각 요소(repeat, 병합 셀, bundle)는 자신의 열 범위에서 바로 위의 해결된 요소를 찾아 밀림량을 계산한다
- **넓은 요소**(여러 열에 걸친 요소 -- 병합 셀, bundle)를 통해 교차 열 간 밀림이 전파된다
- gap 열(repeat에 속하지 않는 열)의 정적 셀은 넓은 요소에 의해 연결되지 않는 한 밀리지 않는다

```
예시 1: 서로 다른 행에 배치된 repeat
- A-C 열 (3행): employees repeat (확장 +2행)
- F-H 열 (8행): department repeat (확장 +3행)

예시 2: 같은 행에 배치된 repeat
- A-D 열 (2행): eventTypes repeat (5개 -> +4행)
- J-K 열 (2행): languages repeat (4개 -> +3행)
-> 각 repeat이 독립적으로 확장, 결과 행 수 = max(5, 4)
```

**같은 행 다중 repeat 구현 핵심:**
- `SheetSpec.repeatRegions`: 반복 영역 정보를 `RepeatRegionSpec` 리스트로 저장 (행 중심이 아닌 영역 중심)
- `TemplateAnalyzer.buildRowSpecs()`: repeat 영역에 속한 행의 셀 파싱 시 모든 아이템 변수를 인식
- `UnifiedMarkerParser.parse()`: `repeatItemVariables: Set<String>`으로 같은 행의 모든 아이템 변수를 인식
- non-repeat 셀은 같은 행의 첫 번째 repeat에서만 처리 (중복 방지)

### 1.5 중복 마커 감지

범위를 취급하는 마커(repeat, image)가 동일 대상에 대해 중복 선언되면 경고 로그를 출력하고 마지막 마커만 유지한다.

#### TemplateAnalyzer의 5단계 분석 구조

```
analyzeWorkbook(workbook)
  Phase 1: 모든 시트에서 repeat 마커 수집 (collectRepeatRegions)
  Phase 2: repeat 중복 제거 (deduplicateRepeatRegions)
  Phase 2.5: bundle 마커 수집 및 검증 (collectBundleRegions, validateBundleRegions)
  Phase 3: SheetSpec 생성 (analyzeSheet -- 중복 제거된 repeat/bundle 목록 사용)
  Phase 4: 셀 단위 범위 마커 중복 제거 (deduplicateCellMarkers)
```

- **Phase 1~2**: repeat는 `RepeatRegionSpec`으로 추출되어 셀에서 분리되므로 SheetSpec 생성 전에 중복 제거
- **Phase 2.5**: bundle 마커를 수집하고 중첩/경계 걸침을 검증
- **Phase 3~4**: image 등 셀에 남는 마커는 SheetSpec 생성 후 후처리로 중복 제거 (중복 마커를 `CellContent.Empty`로 교체)

#### 중복 판정 기준

| 마커     | 중복 키                          | 비고                              |
|--------|-------------------------------|---------------------------------|
| repeat | 컬렉션 + 대상 시트 + 영역(CellArea)   | 대상 시트는 범위의 시트 접두사로 결정, 없으면 현재 시트 |
| image  | 이름 + 대상 시트 + 위치 + 크기(sizeSpec) | position이 없는 image는 중복 체크 대상 아님 |

#### 겹침 검증과의 처리 순서

중복 제거(Phase 2)는 겹침 검증(`PositionCalculator.validateNoOverlap`)보다 **반드시 먼저** 실행된다.
같은 영역의 중복 repeat은 `overlaps() == true`이므로, 중복 제거가 선행되지 않으면 겹침 검증에서 예외가 발생한다.

```
TemplateAnalyzer.analyzeWorkbook()
  -> Phase 2: 중복 repeat 제거 (경고 로그)     <- 먼저
      v
RenderingStrategy.processSheet()
  -> validateNoOverlap(blueprint.repeatRegions)  <- 나중
```

### 1.6 빈 컬렉션 처리 (emptyRange)

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

| 처리 경로         | 처리 함수                                                      |
|---------------|--------------------------------------------------------------|
| streaming     | `StreamingRenderingStrategy.writeRepeatCellsForRow()`          |
| pendingRows   | `StreamingRenderingStrategy.collectEmptyRangeContentCells()`   |

### 2. 메모리 관리 원칙

#### 2.1 컬렉션 전체 메모리 로드 금지
대용량 데이터 처리를 위해 컬렉션 전체를 메모리에 올리지 않는다.

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
- 시트 이름 추출: `Sheet1!` -> `"Sheet1"`, `'Sheet Name'!` -> `"Sheet Name"`

**repeat 영역 밖 참조 시프트:**

repeat 영역 **내부** 수식이 영역 **밖**의 셀을 참조하는 경우, `$` 유무와 무관하게 동일하게 처리됩니다.
- 복사 단계(`adjustForRepeatIndex`)에서 이동하지 않음
- 행 삽입에 의한 시프트만 적용(`adjustRefsOutsideRepeat`)

처리 순서가 중요합니다: `adjustRefsOutsideRepeat` -> `adjustForRepeatIndex`.
원본 수식에서 영역 안/밖을 정확히 판단하기 위해 시프트를 먼저 적용합니다.

```
예시: =J7/J8 (J8은 repeat 영역 밖 Total 행, 3행 확장 시)
K7 (index 0): J7/J11   <- J8 -> J11 (시프트), J7 그대로
K8 (index 1): J8/J11   <- J7 -> J8 (복사 이동), J11 그대로
K9 (index 2): J9/J11   <- J7 -> J9 (복사 이동), J11 그대로
```

#### 3.2 요소 밀려남 원칙

repeat 확장에 의해 요소들이 밀려나는 위치를 결정하는 원칙이다.

**기본 원칙: 위의 요소가 밀리면, 그 아래의 요소도 밀린다.** 요소의 종류나 크기와 무관하다.

밀림의 **원인**은 확장 요소(repeat)의 크기 변화이지만, 밀림의 **전파**는 모든 요소를 통해 체인 방식으로 이루어진다.

##### 3.2.1 용어 정의

| 용어 | 정의 |
|------|------|
| **요소** | 템플릿 시트에서 위치를 가진 모든 항목. 단일 셀, 병합 셀, repeat 범위, 이미지 앵커, 차트 앵커 등 |
| **확장 요소** | 데이터 건수에 따라 크기가 변하는 요소. 현재는 repeat만 해당 |
| **상위 요소** | 해당 요소의 각 열에서, 위쪽으로 가장 가까운 요소. **단일 셀이든 병합 셀이든 repeat이든** 모든 요소가 상위 후보가 된다 |
| **절대 간격** | 템플릿에서 해당 요소의 시작 행과 상위 요소의 끝 행 사이의 고정된 거리 |
| **렌더링 끝** | 최종 출력에서 해당 요소가 차지하는 마지막 행 |

##### 3.2.2 상위 요소 탐색 규칙

1. 해당 요소가 차지하는 **각 열**에 대해 독립적으로 탐색한다
2. 위쪽으로 올라가면서 가장 가까운(바로 위의) **요소**를 찾는다
3. **종류/크기 무관**: 단일 셀, 병합 셀, repeat, 이미지, 차트 등 모든 요소가 상위 후보이다
4. 같은 열에 복수 후보가 있으면, **가장 가까운(아래쪽)** 것만 선택한다

```
예시:
  Row 1:  [repeat]       <- 요소
  Row 5:  [단일 셀]      <- 요소
  Row 8:  [병합 셀]      <- 요소
  Row 12: [이미지]       <- 이 요소의 상위 = 병합 셀 (가장 가까운 요소)
```

##### 3.2.3 밀림 위치 계산

```
최종 시작 위치 = 상위 요소의 렌더링 끝 + 절대 간격
절대 간격 = 해당 요소 템플릿 시작 행 - 상위 요소 템플릿 끝 행
```

상위 요소의 렌더링 끝이 먼저 확정되어야 하므로, **위에서 아래로 순차 계산**한다 (의존성 체인).

##### 3.2.4 복수 열에 걸친 요소의 상위 처리 (MAX)

여러 열에 걸친 요소는 각 열에서 독립적으로 상위를 탐색한다.
각 열의 계산 결과 중 **최대값(MAX)**을 최종 위치로 채택한다.

```
예시:
  Col A-D:  Row 1: [repeat] -> 100건 확장 -> 렌더링 끝 = Row 100
  Col E-H:  (repeat 없음)
  Col A-H:  Row 5: [병합 셀] (8열 전체에 걸침)

  병합 셀의 상위:
    A열: repeat(렌더링 끝 100) -> 100 + (5-1) = 104
    E열: 상위 없음 -> 5 (밀리지 않음)
    MAX -> 104 (병합 셀 최종 시작 = Row 104)
```

##### 3.2.5 렌더링 끝 계산

| 요소 종류 | 렌더링 끝 |
|----------|---------|
| **확장 요소(repeat)** | 최종 시작 + (데이터 건수 x 템플릿 행 수) - 1 |
| **일반 요소** | 최종 시작 + (템플릿 크기 - 1) |

- 확장 요소: 데이터에 의해 크기가 변하므로 렌더링 끝이 템플릿과 다를 수 있다
- 일반 요소 (단일 셀 포함): 크기가 고정이므로 밀린 만큼만 이동한다. 단일 셀의 경우 렌더링 끝 = 최종 시작

##### 3.2.6 상위 요소 부재 시 처리

- 해당 열에서 위쪽에 요소가 없으면, 해당 열에서는 밀리지 않는다
- **모든 열**에서 상위가 없으면, 템플릿 위치를 유지한다 (밀리지 않음)

##### 3.2.7 교차 열 밀림 전파

밀림 체이닝이 필수적인 핵심 시나리오이다.
여러 열에 걸친 요소(병합 셀, bundle)를 통해, 서로 다른 열의 repeat 간에도 밀림이 전파되어 레이아웃이 유지된다.

```
템플릿:
  Col A-D:  Row 1:    [repeat] (A1:D1)         <- A-D열만 확장
  Col A-H:  Row 5-10: [구분선 병합] (A5:H10)   <- A-H 전체에 걸침
  Col E-H:  Row 12:   [요소 X] (E12:H12)       <- E-H열만

repeat -> 100건 확장:

  [x] 체이닝 없이 repeat만 참조할 경우:
    구분선 -> Row 104 (A열 repeat에 의해 밀림), 렌더링 끝 = Row 109
    요소 X -> Row 12 (E열에 repeat 없으므로 밀리지 않음)
    -> 요소 X가 구분선 위에 남음 -- 레이아웃 깨짐!

  [v] 체이닝으로 모든 요소를 통해 전파할 경우:
    구분선 -> Row 104, 렌더링 끝 = Row 109
    요소 X -> 상위 = 구분선 -> 109 + (12-10) = Row 111
    -> 요소 X가 구분선 아래에 올바르게 위치
```

##### 3.2.8 가로 확장 적용

RIGHT 방향 repeat에 대해서도 동일한 원리를 적용한다. 방향만 치환하면 된다:

| 세로 확장 | 가로 확장 |
|---------|---------|
| 행 | 열 |
| 위 | 왼쪽 |
| 아래 | 오른쪽 |
| 렌더링 끝 행 | 렌더링 끝 열 |

##### 3.2.9 가로/세로 중첩 금지

가로 확장과 세로 확장이 2D 공간에서 교차하는 구성은 지원하지 않는다. 오류를 발생시킨다.

##### 3.2.10 적용 범위

- 스트리밍 렌더링 전략(`StreamingRenderingStrategy`)에서 적용한다

##### 3.2.11 bundle (요소 묶음)

지정된 범위 내의 모든 요소를 하나의 넓은 요소로 묶어, 밀림 정책을 일관되게 적용한다.
bundle 자체는 밀림 정책 외의 다른 효과는 없다.

**문법:**

```
${bundle(A15:H20)}
=TBEG_BUNDLE(A15:H20)
```

**마커 배치:** bundle 범위 밖 어디든 가능 (다른 시트도 가능)

**동작 모델:**

1. **내부 격리**: bundle 내부는 독자적인 시트처럼 취급된다
   - 내부 요소의 상위 탐색은 bundle 경계에서 멈춘다
   - bundle 첫 행의 요소는 상위가 없다 (시트 첫 행처럼)
   - bundle 외부의 요소는 내부의 상위 후보가 되지 않는다

2. **내부 밀림 계산**: bundle 내부에서 본 절(3.2)의 밀림 정책을 동일하게 적용한다
   - 내부 repeat 확장, 요소 간 밀림 체이닝, MAX 규칙 등 모두 적용

3. **bundle 크기 결정**: bundle range의 행 수 + 내부 밀림 정책 적용 후 확장량
   - 내부에 나란히 있는 여러 repeat가 있으면 MAX 적용

4. **외부 참여**: 크기가 확정된 bundle은 하나의 넓은 요소로 밀림 체이닝에 참여
   - bundle의 열 범위 = bundle range의 열 범위
   - bundle의 렌더링 끝 = bundle 최종 시작 + 최종 크기 - 1

**예시:**

```
템플릿:
  Col A-D:  Row 1:    [repeat(depts)] (A1:D1)     <- 5건 (확장량 +4)
  Col A-H:  Row 5-12: ${bundle(A5:H12)}     <- 요소 묶음 (8행)
              Row 5:  "직원별 실적" (타이틀)
              Row 6:  필드 타이틀 행
              Row 7:  [repeat(employees)] (A7:H7)  <- 11건 (확장량 +10)
              Row 12: SUM행

bundle 없이:
  A열 요소는 depts에 의해 밀리지만, E-H열 요소는 밀리지 않음 -> 표 어긋남

bundle 있으면:
  1) 내부 계산: employees +10행 -> bundle 최종 크기 = 8 + 10 = 18행
  2) 외부: bundle(A-H, 18행)이 넓은 요소로 참여
     A열 상위 = depts(렌더링 끝 5) -> bundle 시작 = 5 + (5-1) = 9
     E열 상위 = 없음 -> 5
     MAX -> bundle 최종 시작 = Row 9, 렌더링 끝 = Row 26
  3) 내부 요소가 bundle과 함께 이동 -> 표 전체가 일체로 밀림
```

**제약사항:**

- **경계 걸침 금지**: 요소가 bundle 범위를 부분적으로 걸치면 오류
- **bundle 중첩 금지**: bundle 안에 다른 bundle이 있으면 오류

### 4. 스레드 안전성 원칙

`ExcelGenerator`는 Spring 싱글톤 빈으로 사용될 수 있으므로, 여러 스레드에서 동시에 호출되어도 안전해야 한다.

#### 4.1 호출별 격리 구조

`TemplateRenderProcessor.process()`가 매 호출마다 `TemplateRenderingEngine`을 새로 생성하므로, 렌더링 관련 mutable 상태(fieldCache, getterCache, styleMap, repeatExpansionInfos 등)는 호출별로 완전 격리된다. `ProcessingContext`도 매 호출마다 생성된다.

#### 4.2 공유 인스턴스의 스레드 안전성

`ExcelGenerator`가 보유하는 공유 객체의 스레드 안전성 현황:

| 객체 | 스레드 안전 | 근거 |
|------|-----------|------|
| `PivotTableProcessor` | O | `styleCache`, `styleInfoCache`가 `Collections.synchronizedMap` + `ConcurrentHashMap` 사용 |
| `XmlVariableProcessor` | O | companion object 상수만 사용 |
| `ChartProcessor` | O | companion object lazy 상수만 사용 |
| `TbegPipeline` | O | 프로세서 목록 불변 |
| `ZipStreamPostProcessor` | O | 인스턴스 상태 없음 |
| 기타 프로세서 | O | 인스턴스 상태 없음 (PivotTableProcessor/ChartProcessor에 위임) |

#### 4.3 새 코드 작성 시 주의사항

- 인스턴스 레벨 mutable 상태는 `ConcurrentHashMap`, `Collections.synchronizedMap` 등 스레드 안전한 자료구조를 사용한다
- 호출별 상태는 지역 변수나 메서드 파라미터로 격리한다
- 새 프로세서 추가 시 "이 상태가 여러 스레드에서 동시 접근될 수 있는가?"를 항상 검토한다

---

### 5. 숫자 서식 원칙

#### 5.1 자동 숫자 서식 지정 조건
라이브러리에 의해 자동 생성된 값이 숫자 타입이고, 해당 셀의 "표시 형식"이 없거나 "일반"인 경우 자동으로 숫자 서식을 적용합니다.

- 정수: `pivotIntegerFormatIndex` (기본값 3, `#,##0`)
- 소수: `pivotDecimalFormatIndex` (기본값 4, `#,##0.00`)

#### 5.2 수식 셀의 숫자 서식
변수형 마커(`${var}`)에 `=`로 시작하는 값을 바인딩하여 수식으로 치환된 셀도 동일하게 숫자 서식이 적용됩니다.

- 수식 결과 타입을 사전에 알 수 없으므로 정수 포맷(`#,##0`)을 기본 적용합니다
- 정렬은 GENERAL을 유지하여 Excel이 수식 결과 타입에 따라 자동 결정합니다
- 이미 특정 서식이 설정된 수식 셀(템플릿에서 직접 지정)은 변경하지 않습니다
- 소수점이 필요한 수식(비율, 평균 등)은 템플릿 셀에 원하는 서식을 직접 지정합니다

> **구현**: `NumberFormatProcessor`에서 `CellType.FORMULA` 분기로 처리

#### 5.3 자동 정렬 지정 조건
라이브러리에 의해 자동 생성된 값이 숫자 타입이고, 해당 셀의 정렬이 "일반"인 경우 자동으로 오른쪽 정렬을 적용합니다. 수식 셀에는 정렬을 적용하지 않습니다.

#### 5.4 기존 서식 보존
위 5.1~5.3 조건에 해당하더라도 해당 셀의 나머지 모든 서식(글꼴, 색상, 테두리 등)은 템플릿 서식을 유지합니다.

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
| `streamingMode`           | `ENABLED`           | **deprecated** (값이 무시됨)            |
| `fileNamingMode`          | `TIMESTAMP`         | TIMESTAMP / NONE                  |
| `timestampFormat`         | `"yyyyMMdd_HHmmss"` | DateTimeFormatter 패턴              |
| `fileConflictPolicy`      | `SEQUENCE`          | SEQUENCE / ERROR                  |
| `progressReportInterval`  | `100`               | 진행률 보고 간격 (행 수)                   |
| `preserveTemplateLayout`  | `true`              | 템플릿 레이아웃 보존                       |
| `pivotIntegerFormatIndex` | `3`                 | 정수 포맷 인덱스 (`#,##0`)               |
| `pivotDecimalFormatIndex` | `4`                 | 소수 포맷 인덱스 (`#,##0.00`)            |
| `missingDataBehavior`     | `WARN`              | WARN / THROW                      |
| `unmarkedHidePolicy`      | `WARN_AND_HIDE`     | hideable 마커 없이 hideFields에 지정된 필드 처리 정책 |

### 프리셋 설정

```kotlin
// 대용량 처리 최적화
TbegConfig.forLargeData()
// progressReportInterval = 500

// 소량 처리 (deprecated -- default()와 동일)
TbegConfig.forSmallData()
```

### Spring Boot 설정 (application.yml)

```yaml
tbeg:
  file-naming-mode: timestamp
  timestamp-format: yyyyMMdd_HHmmss
  file-conflict-policy: sequence
  progress-report-interval: 100
  preserve-template-layout: true
  missing-data-behavior: warn
```

---

## 제한사항

### 지원 기능

| 항목               | 지원 여부 | 비고                        |
|------------------|-------|---------------------------|
| 아래 행 참조 수식       | [v]   | repeat 영역 참조 시 자동 확장      |
| 위쪽 행 참조 수식       | [v]   | 자동 조정됨                    |
| 자동 확장 수식 (SUM 등) | [v]   | 범위 자동 확장                  |
| 차트               | [v]   | Extract/Restore 프로세서로 처리  |
| 피벗 테이블           | [v]   | Extract/Recreate 프로세서로 처리 |

### 일반 제한사항

- repeat 영역은 2D 공간에서 겹치면 안 됨
- 같은 열 범위 내 여러 repeat은 세로로 배치 가능
- 같은 행에 여러 repeat을 배치할 경우 열 범위가 겹치지 않아야 함
- 시퀀스 번호는 최대 10,000까지 시도

### 내부 상수

| 상수          | 값           | 위치                             |
|-------------|-------------|--------------------------------|
| SXSSF 버퍼 크기 | 100행        | `StreamingRenderingStrategy.kt` |
| 이미지 마진      | 1px         | `ImageInserter.kt`             |
| EMU 변환율     | 9525 EMU/px | `ImageInserter.kt`             |

---

## 내부 최적화

### 스타일 캐싱

동일 스타일 중복 생성을 방지합니다.

| 클래스                     | 캐시                         | 용도        |
|-------------------------|----------------------------|-----------|
| `PivotTableProcessor`   | `styleCache` (synchronizedMap + WeakHashMap) | 피벗 셀 스타일  |
| `NumberFormatProcessor` | `styleCache`               | 숫자 서식 스타일 |

### 필드 캐싱

리플렉션 성능을 최적화합니다.

| 클래스                       | 캐시            | 용도         |
|---------------------------|---------------|------------|
| `TemplateRenderingEngine` | `fieldCache`  | 필드 정보      |
| `TemplateRenderingEngine` | `getterCache` | getter 메서드 |

### calcChain 정리

수식 재계산 오류를 방지합니다.

- `clearCalcChain()` 호출
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
│   ├── ThreadSafetyTest.kt             # 스레드 안전성 테스트
│   ├── EmptyCollectionTest.kt          # 빈 컬렉션 처리 테스트
│   ├── engine/
│   │   ├── TemplateRenderingEngineTest.kt  # 렌더링 엔진 테스트
│   │   ├── DuplicateRepeatDetectionTest.kt # 중복 마커 감지 테스트
│   │   ├── HideableIntegrationTest.kt     # hideable 통합 테스트
│   │   ├── PositionCalculatorTest.kt
│   │   ├── ForwardReferenceTest.kt
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

# 성능 벤치마크 (JMH)
./gradlew :tbeg:runBenchmark      # 커스텀 러너 (정리된 테이블)
./gradlew :tbeg:jmh               # JMH 플러그인 (JSON 결과)
```

### 테스트 작성 원칙

1. **템플릿 파일 사용**: 실제 Excel 템플릿으로 통합 테스트
2. **출력 파일 검증**: 생성된 Excel 파일을 열어 결과 확인
3. **엣지 케이스**: 빈 데이터, 대용량 데이터, 특수 문자 등 테스트

---

## 성능 벤치마크

JMH(Java Microbenchmark Harness)를 사용하여 소요 시간, 힙 할당량, GC 통계를 정밀 측정한다.

### 벤치마크 구성

| 벤치마크 | 클래스 | 고정 | 변수 |
|---------|------|------|------|
| 데이터 제공 방식 비교 | `DataModeBenchmark` | generate() 출력 | Map vs DataProvider x 1K~100K |
| 출력 방식 비교 | `OutputModeBenchmark` | DataProvider | generate/toStream/toFile x 1K~100K |
| 대용량 스케일 | `LargeScaleBenchmark` | DataProvider + generateToFile | 100K/200K/300K/500K/1M |

### 실행 방법

```bash
# JMH 플러그인 벤치마크 (전체, JSON 결과 파일 생성)
./gradlew :tbeg:jmh

# 커스텀 러너 (정리된 테이블 출력)
./gradlew :tbeg:runBenchmark

# CI 회귀 테스트 (빠른 소규모 검증)
./gradlew :tbeg:test --tests "*PerformanceBenchmarkTest*"
```

### 소스 구조

```
src/jmh/kotlin/io/github/jogakdal/tbeg/benchmark/
├── BenchmarkSupport.kt          # 공통: 템플릿 생성, 데이터 생성, 결과 출력
├── DataModeBenchmark.kt         # 벤치마크 1: Map vs DataProvider
├── OutputModeBenchmark.kt       # 벤치마크 2: generate vs toStream vs toFile
├── LargeScaleBenchmark.kt       # 벤치마크 3: 대용량 (100K~1M)
├── CpuTimeProfiler.kt           # 커스텀 프로파일러 (CPU 사용률 측정)
└── TbegBenchmarkRunner.kt       # 커스텀 러너 (전체 실행 + 정리된 테이블 출력)
```

### 측정 결과 (요약)

**테스트 환경**: macOS (aarch64), OpenJDK 21.0.1, 12코어, 3개 컬럼 repeat + SUM 수식

| 데이터 크기 | 소요 시간 | CPU/코어 | 힙 할당량 |
|----------|---------|--------|---------|
| 1,000행 | 20ms | 23.5% | 11.8MB |
| 10,000행 | 109ms | 14.7% | 58.5MB |
| 30,000행 | 315ms | 12.5% | 166.0MB |
| 100,000행 | 993ms | 10.8% | 540.8MB |
| 500,000행 | 4,718ms | 8.9% | 2,614.5MB |
| 1,000,000행 | 8,952ms | 8.8% | 5,230.7MB |

> DataProvider + generateToFile 기준. CPU/코어는 시스템 전체 CPU 용량 대비 프로세스 사용률(코어 수로 나눈 값)입니다.
> 벤치마크 3종(데이터 방식 비교, 출력 방식 비교, 대용량 스케일)의 전체 결과와 분석은 [성능 벤치마크 상세](./manual/ko/appendix/benchmark-results.md)를 참조하세요.
