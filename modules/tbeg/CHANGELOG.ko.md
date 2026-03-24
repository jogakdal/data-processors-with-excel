# TBEG Changelog

## 1.2.3

### 새 기능

- **`generateToStream()` API 추가**: 생성 결과를 `OutputStream`에 직접 쓰는 편의 메서드가 추가되었습니다. HTTP 응답 스트림 등에 중간 바이트 배열 복사 없이 출력할 수 있습니다.

### 성능 개선

- **ZIP 단일 패스 후처리 통합**: 기존 3단계 후처리(숫자 서식, 메타데이터, 변수 치환)를 한 번의 ZIP iteration으로 통합하였습니다. `XSSFWorkbook`/`OPCPackage` 전체 로드를 제거하여 대용량 파일(30만 행 이상) 처리 한계를 해소하였습니다.
- **JMH 벤치마크 도입**: 기존 `measureTimeMillis` 기반 벤치마크를 JMH(Java Microbenchmark Harness)로 전환하여 소요 시간, 힙 할당량, GC 통계를 정밀 측정합니다. 데이터 제공 방식(Map vs DataProvider), 출력 방식(generate/toStream/toFile), 대용량 스케일(최대 30만 행)의 3가지 차원으로 비교할 수 있습니다.

<details>
<summary>내부 개선</summary>

- **`ZipStreamPostProcessor` 신규 추가**: 숫자 서식, 메타데이터, 변수 치환, absPath 제거를 ZIP 스트리밍으로 처리하는 통합 프로세서입니다. 2-phase 접근으로 styles.xml을 먼저 분석한 후 전체 ZIP을 순회합니다.
- **`zippost/` 핸들러 패키지**: `StylesXmlHandler`(DOM), `SheetXmlHandler`(StAX), `WorkbookXmlHandler`(DOM), `MetadataXmlHandler`(DOM), `VariableXmlHandler`(문자열 치환) 5개 핸들러가 각 ZIP 엔트리별 처리를 담당합니다.
- **`XssfPostProcessor`, `XmlVariableReplaceProcessor` 삭제**: `ZipStreamPostProcessor`로 대체되어 제거되었습니다.
- **JMH 벤치마크 구성**: `src/jmh/` 소스셋에 `DataModeBenchmark`, `OutputModeBenchmark`, `LargeScaleBenchmark`, `TbegBenchmarkRunner` 클래스를 추가하였습니다.
- 기존 `PerformanceBenchmark.kt`를 삭제하고 JMH로 대체하였습니다. CI 회귀 테스트(`PerformanceBenchmarkTest`)는 유지됩니다.

</details>

## 1.2.2

### 새 기능

- **선택적 필드 노출 (hideable)**: 상황에 따라 특정 필드의 노출을 제한할 수 있습니다. `${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}` 또는 `=TBEG_HIDEABLE(...)` 수식 마커로 사용합니다.
  - **HideMode**: `DELETE`(물리적 삭제 + 시프트, 기본값) 또는 `DIM`(비활성화 스타일 + 값 제거) 중 선택할 수 있습니다.
  - **UnmarkedHidePolicy**: hideable 마커가 없는 필드를 숨기려 할 때의 동작을 설정합니다. `WARN_AND_HIDE`(경고 후 숨김, 기본값) 또는 `ERROR`(예외 발생)를 지원합니다.
  - **API**: `ExcelDataProvider.getHiddenFields()`로 숨길 필드 목록을 지정하고, `SimpleDataProvider.Builder.hideFields()`로 간편하게 설정할 수 있습니다.
  - **Spring Boot 설정**: `hunet.tbeg.unmarked-hide-policy` 프로퍼티로 정책을 설정할 수 있습니다.

<details>
<summary>내부 개선</summary>

- **전처리 패키지 신설**: `engine/preprocessing/` 패키지에 `HidePreprocessor`, `HideValidator`, `ElementShifter`, `CellUtils` 클래스를 추가하여 필드 숨기기 관련 전처리 로직을 구성하였습니다.
- **마커 파서 확장**: `UnifiedMarkerParser`에 hideable 마커 파싱을 추가하였습니다.
- **설정 확장**: `TbegConfig`에 `unmarkedHidePolicy` 옵션을 추가하였습니다.

</details>

## 1.2.1

### 새 기능

- **변수형 마커 수식 치환**: `${변수}` 마커에 `=`로 시작하는 값을 바인딩하면 Excel 수식으로 처리됩니다. repeat 내 아이템 필드에서도 동일하게 동작하며, 수식 범위 자동 조정(확장, 행 시프트)이 적용됩니다.
- **수식 셀 자동 숫자 서식**: 수식으로 치환된 셀의 표시 형식이 "일반"인 경우 정수 숫자 서식(`#,##0`)이 자동 적용됩니다. 소수점이 필요한 수식은 템플릿에서 직접 서식을 지정합니다.

### 문서

- 문서 내 깨진 앵커 링크 수정.
- 템플릿 문법 레퍼런스에 `=` 접두사 텍스트 관련 주의사항 추가.

<details>
<summary>내부 개선</summary>

- **`setCellValue`/`setValueOrFormula` 분리**: 수식 감지 로직을 `setValueOrFormula`로 분리하여 사용자 데이터 바인딩 경로(Variable, ItemField, MergeField)에서만 수식 치환이 동작하도록 개선하였습니다.
- **`StreamingRenderingStrategy` 수식 처리 강화**: `processCellContentWithCalculator`에서 Variable, ItemField, MergeField 타입을 직접 처리하여 수식 값의 범위 조정(PositionCalculator 기반)이 올바르게 적용됩니다.
- **ZIP 처리 유틸리티 추출**: `ChartProcessor`와 `PivotTableProcessor`에서 반복되던 ZIP 스트림 처리 패턴을 `ByteArray.transformZipEntries()` 고차 함수로 통합하였습니다.
- **`escapeXml()` 공통화**: TBEG 전용이던 `escapeXml()` 확장 함수를 `common-core` 모듈로 이동하였습니다.
- **`toColumnName()` 중복 제거**: `AbstractRenderingStrategy`의 `toColumnName()` 메서드를 제거하고 `ExcelUtils.toCellRef()`를 사용하도록 통합하였습니다.
- 미사용 import 정리 (`ChartProcessor`, `PivotTableProcessor`).

</details>

## 1.2.0

### Breaking Changes

- **XSSF(비스트리밍) 모드 폐지**: 항상 스트리밍 모드로 동작합니다.
  - `StreamingMode` enum: deprecated (향후 버전에서 제거 예정)
  - `TbegConfig.streamingMode`: deprecated (값이 무시됩니다)
  - `TbegConfig.forSmallData()`: deprecated (`default()`와 동일하게 동작)
  - `TbegConfig.withStreamingMode()`: deprecated
  - `TbegConfig.Builder.streamingMode()`: deprecated
  - Spring 설정 `streaming-mode`: deprecated (값이 무시됩니다)
  - 내부 클래스 `XssfRenderingStrategy` 삭제
  - `SxssfRenderingStrategy` -> `StreamingRenderingStrategy`로 리네이밍

### 새 기능

- **이미지 URL 지원**: 이미지 데이터로 `ByteArray` 대신 HTTP(S) URL 문자열을 지정하면 렌더링 시점에 자동으로 다운로드합니다. `imageUrl("logo", "https://...")` 형태로 사용합니다.
  - `imageUrlCacheTtlSeconds` 설정으로 호출 간 캐시 TTL을 지정할 수 있습니다 (기본: 0, 캐싱 안 함)
  - 다운로드 실패 시 경고 로그를 출력하고 해당 이미지를 건너뜁니다.
- **자동 셀 병합 (merge)**: repeat 확장 시 연속된 같은 값의 셀을 자동으로 병합합니다. `${merge(item.field)}` 또는 `=TBEG_MERGE(item.field)` 마커로 사용합니다.
- **요소 묶음 (bundle)**: 지정된 범위의 요소를 하나의 단위로 묶어 repeat 확장 시 일체로 이동합니다. `${bundle(범위)}` 또는 `=TBEG_BUNDLE(범위)` 마커로 사용합니다.

### 버그 수정

- **RIGHT repeat 열 너비 복제 수정**: 같은 열 범위의 RIGHT repeat이 여러 개일 때 열 너비가 올바르게 복제되지 않던 문제가 수정되었습니다.
- **RIGHT repeat non-repeat 셀 필터링 수정**: 다른 RIGHT repeat 영역의 열도 non-repeat 셀 기록에서 올바르게 제외하도록 수정되었습니다.
- **RIGHT repeat 간접 겹침 검사 수정**: 행 범위가 겹치는 RIGHT repeat 간의 열 겹침 검사가 누락되던 문제가 수정되었습니다.

<details>
<summary>내부 개선</summary>

- **체이닝 기반 위치 계산**: PositionCalculator가 ColumnGroup 기반 MAX 방식에서 체이닝 알고리즘으로 전환되었습니다. 병합 셀과 bundle을 통한 교차 열 밀림 전파가 정확해졌습니다.
- **MergeTracker**: 자동 셀 병합을 처리하는 MergeTracker 클래스가 추가되었습니다.
- **TemplateAnalyzer 5단계 분석**: bundle 수집 및 검증 단계(Phase 2.5)가 추가되었습니다.
- **dead zone 처리**: 밀림으로 인한 빈 영역을 감지하고 올바르게 스킵하는 로직이 추가되었습니다.
- **forward verification**: getFinalPosition()의 역방향 계산 모호성을 방지하는 검증 로직이 추가되었습니다.
- **행 높이 MAX 적용**: 체이닝으로 여러 템플릿 행이 같은 실제 행에 매핑될 때 최대 높이를 적용합니다.
- **`ensureCalculated()` 패턴 추출**: PositionCalculator의 공개 메서드에서 반복되던 지연 계산 호출을 단일 메서드로 통합하였습니다.
- **RIGHT repeat 열 너비 적용 개선**: `applyColumnWidths()`에서 같은 colRange를 가진 repeat을 groupBy로 묶어 중복 적용을 방지합니다.
- 단위/통합 테스트 추가: `CellMergeTest`, `PositionCalculatorTest` 체이닝/bundle 테스트

</details>

## 1.1.3

### 새 기능

- **차트 데이터 범위 자동 조정**: repeat으로 데이터가 확장될 때 차트의 데이터 범위가 자동으로 갱신됩니다.
- **이미지 정렬 개선**: 이미지 삽입 시 셀 내 정렬이 개선되었습니다.

### 버그 수정

- **차트 복원 처리 수정**: 차트가 포함된 템플릿에서 데이터 확장 후 차트가 올바르게 복원되지 않던 문제가 수정되었습니다.
- **수식 범위 조정 수정**: 특정 조건에서 수식의 셀 범위가 올바르게 확장되지 않던 문제가 수정되었습니다.
- **이미지 삽입 위치 수정**: 이미지가 지정된 셀 위치에 정확하게 배치되지 않던 문제가 수정되었습니다.
- **차트 범위 조정 수정 (RIGHT 방향)**: RIGHT repeat에서 단일 셀 범위 확장 시 행 범위를 검증하지 않아 관련 없는 repeat 영역까지 확장되던 문제가 수정되었습니다.
- **차트 범위 조정 수정 (다중 RIGHT repeat)**: 같은 시트에 여러 RIGHT repeat이 있을 때 차트의 열 범위가 누적 확장되지 않던 문제가 수정되었습니다.

### 문서

- **README 개선**: POI 비교 코드, 주요 기능 보강, 적합성 표, 템플릿 문법 표 추가
- **매뉴얼 강화**: 종합 예제(Rich Sample), 스크린샷 기반 소개 섹션 추가
- **타 라이브러리 비교 문서 추가**: JXLS, EasyExcel 등과의 기능/성능 비교

<details>
<summary>내부 개선</summary>

- `ChartRangeAdjuster` 신규 추가
- `ChartProcessor` 리팩토링
- `ImageInserter` 리팩토링
- `FormulaAdjuster` 확장
- 렌더링 전략 개선
- Rich Sample 추가 (분기 매출 실적 보고서 데모)
- 단위/통합 테스트 추가: `ChartRangeAdjusterTest`, `ChartRepeatIntegrationTest`, `DrawingXmlMergeTest`, `FormulaAdjusterTest`, `ImageInserterAlignmentTest`

</details>

## 1.1.2

### 버그 수정

- **비반복 영역 수식 처리 개선**: 비반복 영역의 수식 셀이 올바르게 처리되지 않던 문제가 수정되었습니다.
- **비반복 영역 정적 행 플래그 수정**: 비반복 영역 셀의 `isStaticRow` 플래그가 올바르게 설정되지 않던 문제가 수정되었습니다.
- **크로스 시트 중복 마커 그룹핑 수정**: 중복 repeat 마커 감지 시 마커가 위치한 시트가 아닌 대상 시트 기준으로 그룹핑하도록 수정되었습니다.

## 1.1.1

### 새 기능

- **중복 마커 감지**: 범위를 취급하는 마커(repeat, image)에 대해 중복 감지 기능이 추가되었습니다.
  - repeat: 같은 컬렉션 + 같은 대상 범위(시트+영역)가 중복되면 경고 로그 출력 후 마지막 마커만 유지합니다.
  - image: 같은 이름 + 같은 위치(시트+셀) + 같은 크기가 중복되면 경고 로그 출력 후 마지막 마커만 유지합니다.
  - 시트 간 중복도 감지합니다 (시트 접두사로 같은 대상을 참조하는 경우)

### 버그 수정

- **같은 행의 다중 독립 repeat 영역 처리 수정**: 하나의 행에 열 범위가 겹치지 않는 여러 repeat 영역이 있을 때 일부 영역이 누락되거나 non-repeat 셀이 중복 렌더링되는 문제가 수정되었습니다.
- **다중 repeat 변수 인식**: `UnifiedMarkerParser`가 같은 행의 여러 repeat 변수를 모두 인식하도록 개선되었습니다.

<details>
<summary>내부 개선</summary>

- **행 명세 구조 전면 개선**: `RowSpec`을 sealed class에서 단일 data class로 단순화하고, repeat 정보를 `RepeatRegionSpec`으로 분리했습니다.
- **공통 타입 도입**: `IndexRange`/`RowRange`/`ColRange` 범위 타입과 `CollectionSizes` value class를 도입했습니다.
- **`CellCoord` 타입 공개 및 활용 확대**: `TemplateAnalyzer` 내부 private class였던 `CellCoord`를 공개 타입으로 전환했습니다.
- **`CellArea` 타입 도입**: 셀 영역 표현 타입을 도입하고 `RepeatRegionSpec`을 `area: CellArea` 단일 프로퍼티로 통합했습니다.
- `TemplateAnalyzer`를 4단계 분석 구조로 개편했습니다 (수집 -> repeat 중복 제거 -> SheetSpec 생성 -> 셀 마커 중복 제거)
- 내부 코드 리팩토링 및 KDoc 현행화
- 같은 행의 다중 독립 repeat 영역 검증 테스트 추가
- 중복 마커 감지 테스트 추가 (repeat 7건 + image 6건)

</details>

## 1.1.0

> [!NOTE]
> 1.0.x에서 업그레이드하시는 분은 [마이그레이션 가이드](./manual/migration-guide.md)를 참조하세요.

### 새 기능

- **빈 컬렉션 처리**: repeat 영역의 데이터가 빈 컬렉션일 때 `empty` 파라미터로 대체 콘텐츠를 지정할 수 있습니다.
- **같은 행의 다중 독립 repeat 영역 지원**: 하나의 행에 서로 독립적인 여러 repeat 영역을 배치할 수 있습니다.
- **크로스 시트 수식 참조 확장**: 다른 시트의 repeat 영역을 참조하는 수식이 자동으로 확장됩니다.

### Breaking Changes

- **설정 클래스 리네이밍**: `ExcelGeneratorConfig` -> `TbegConfig`
  - 기존 이름은 타입 별칭으로 유지되므로 기존 코드는 그대로 동작합니다.

<details>
<summary>내부 개선</summary>

- **통합 마커 파서 도입**: `UnifiedMarkerParser`로 REPEAT, IMAGE, SIZE 마커의 파싱 로직을 일원화했습니다.
- 절대 참조(`$`) 파싱을 지원합니다.
- 조건부 서식 처리 로직을 개선하고 유틸리티를 추출했습니다.
- BOM 모듈을 추가했습니다.
- Kotlin/Java 샘플 6가지 사용 패턴을 추가했습니다 (기본, 지연 로딩, 비동기, 대용량, 암호화, 메타데이터)
- 성능 벤치마크를 추가했습니다.
- 빈 컬렉션 처리 테스트 및 매개변수화 테스트를 도입했습니다.

</details>
