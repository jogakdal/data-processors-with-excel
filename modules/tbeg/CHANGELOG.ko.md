# TBEG Changelog

## 1.1.2

### 버그 수정

- **비반복 영역 수식 처리 개선**: XSSF 모드에서 비반복 영역의 수식 셀이 올바르게 처리되지 않던 문제가 수정되었습니다
- **비반복 영역 정적 행 플래그 수정**: SXSSF 모드에서 비반복 영역 셀의 `isStaticRow` 플래그가 올바르게 설정되지 않던 문제가 수정되었습니다
- **크로스 시트 중복 마커 그룹핑 수정**: 중복 repeat 마커 감지 시 마커가 위치한 시트가 아닌 대상 시트 기준으로 그룹핑하도록 수정되었습니다

## 1.1.1

### 새 기능

- **중복 마커 감지**: 범위를 취급하는 마커(repeat, image)에 대해 중복 감지 기능이 추가되었습니다
  - repeat: 같은 컬렉션 + 같은 대상 범위(시트+영역)가 중복되면 경고 로그 출력 후 마지막 마커만 유지합니다
  - image: 같은 이름 + 같은 위치(시트+셀) + 같은 크기가 중복되면 경고 로그 출력 후 마지막 마커만 유지합니다
  - 시트 간 중복도 감지합니다 (시트 접두사로 같은 대상을 참조하는 경우)

### 버그 수정

- **같은 행의 다중 독립 repeat 영역 처리 수정**: 하나의 행에 열 범위가 겹치지 않는 여러 repeat 영역이 있을 때 일부 영역이 누락되거나 non-repeat 셀이 중복 렌더링되는 문제가 수정되었습니다
- **다중 repeat 변수 인식**: `UnifiedMarkerParser`가 같은 행의 여러 repeat 변수를 모두 인식하도록 개선되었습니다

<details>
<summary>내부 개선</summary>

- **행 명세 구조 전면 개선**: `RowSpec`을 sealed class에서 단일 data class로 단순화하고, repeat 정보를 `RepeatRegionSpec`으로 분리했습니다
- **공통 타입 도입**: `IndexRange`/`RowRange`/`ColRange` 범위 타입과 `CollectionSizes` value class를 도입했습니다
- **`CellCoord` 타입 공개 및 활용 확대**: `TemplateAnalyzer` 내부 private class였던 `CellCoord`를 공개 타입으로 전환했습니다
- **`CellArea` 타입 도입**: 셀 영역 표현 타입을 도입하고 `RepeatRegionSpec`을 `area: CellArea` 단일 프로퍼티로 통합했습니다
- `TemplateAnalyzer`를 4단계 분석 구조로 개편했습니다 (수집 → repeat 중복 제거 → SheetSpec 생성 → 셀 마커 중복 제거)
- 내부 코드 리팩토링 및 KDoc 현행화
- 같은 행의 다중 독립 repeat 영역 검증 테스트 추가 (XSSF/SXSSF 모드별 매개변수화 테스트)
- 중복 마커 감지 테스트 추가 (repeat 7건 + image 6건)

</details>

## 1.1.0

> [!NOTE]
> 1.0.x에서 업그레이드하시는 분은 [마이그레이션 가이드](./manual/ko/migration-guide.md)를 참조하세요.

### 새 기능

- **빈 컬렉션 처리**: repeat 영역의 데이터가 빈 컬렉션일 때 `empty` 파라미터로 대체 콘텐츠를 지정할 수 있습니다
- **같은 행의 다중 독립 repeat 영역 지원**: 하나의 행에 서로 독립적인 여러 repeat 영역을 배치할 수 있습니다
- **크로스 시트 수식 참조 확장**: 다른 시트의 repeat 영역을 참조하는 수식이 자동으로 확장됩니다

### Breaking Changes

- **설정 클래스 리네이밍**: `ExcelGeneratorConfig` → `TbegConfig`
  - 기존 이름은 타입 별칭으로 유지되므로 기존 코드는 그대로 동작합니다

<details>
<summary>내부 개선</summary>

- **통합 마커 파서 도입**: `UnifiedMarkerParser`로 REPEAT, IMAGE, SIZE 마커의 파싱 로직을 일원화했습니다
- 절대 참조(`$`) 파싱을 지원합니다
- 조건부 서식 처리 로직을 개선하고 유틸리티를 추출했습니다
- BOM 모듈을 추가했습니다
- Kotlin/Java 샘플 6가지 사용 패턴을 추가했습니다 (기본, 지연 로딩, 비동기, 대용량, 암호화, 메타데이터)
- XSSF vs SXSSF 성능 벤치마크를 추가했습니다
- 빈 컬렉션 처리 테스트 및 매개변수화 테스트를 도입했습니다

</details>
