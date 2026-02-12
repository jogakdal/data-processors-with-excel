# TBEG 개발자 가이드

이 문서는 TBEG 라이브러리의 내부 아키텍처와 확장 방법을 설명합니다.

## 목차
1. [아키텍처 개요](#1-아키텍처-개요)
2. [파이프라인 패턴](#2-파이프라인-패턴)
3. [렌더링 전략](#3-렌더링-전략)
4. [마커 파서](#4-마커-파서)
5. [위치 계산](#5-위치-계산)
6. [스트리밍 데이터 처리](#6-스트리밍-데이터-처리)
7. [테스트 및 샘플](#7-테스트-작성-가이드)

---

## 1. 아키텍처 개요

### 1.1 모듈 구조

```
io.github.jogakdal.tbeg/
├── ExcelGenerator.kt                   # 메인 진입점
├── TbegConfig.kt                       # 설정 클래스
├── ExcelDataProvider.kt                # 데이터 제공 인터페이스
├── SimpleDataProvider.kt               # 기본 데이터 제공자 구현
├── DocumentMetadata.kt                 # 문서 메타데이터
├── Enums.kt                            # 열거형 정의
├── async/                              # 비동기 처리
│   ├── GenerationJob.kt
│   ├── GenerationResult.kt
│   ├── ExcelGenerationListener.kt
│   └── ProgressInfo.kt
├── engine/
│   ├── core/                           # 핵심 유틸리티
│   │   ├── CommonTypes.kt              # 공통 타입 (CellCoord, CellArea, IndexRange, CollectionSizes 등)
│   │   ├── ExcelUtils.kt
│   │   ├── ChartProcessor.kt
│   │   ├── PivotTableProcessor.kt
│   │   └── XmlVariableProcessor.kt
│   ├── pipeline/                       # 파이프라인 패턴
│   │   ├── TbegPipeline.kt
│   │   ├── ExcelProcessor.kt
│   │   ├── ProcessingContext.kt
│   │   └── processors/                 # 개별 프로세서
│   └── rendering/                      # 렌더링 엔진
│       ├── RenderingStrategy.kt
│       ├── AbstractRenderingStrategy.kt
│       ├── XssfRenderingStrategy.kt
│       ├── SxssfRenderingStrategy.kt
│       ├── TemplateRenderingEngine.kt
│       ├── TemplateAnalyzer.kt
│       ├── PositionCalculator.kt
│       ├── StreamingDataSource.kt
│       ├── WorkbookSpec.kt
│       └── parser/                     # 마커 파서
│           ├── MarkerDefinition.kt     # 마커 정의
│           ├── UnifiedMarkerParser.kt  # 통합 파서
│           ├── ParameterParser.kt      # 파라미터 파서
│           └── ParsedMarker.kt         # 파싱 결과
├── exception/                          # 예외 클래스
└── spring/                             # Spring Boot 자동 설정
```

### 1.2 처리 흐름

```
템플릿 + 데이터
       │
       ▼
───────────────────────────────────────────────────────────────
                       TbegPipeline
───────────────────────────────────────────────────────────────
   1. ChartExtractProcessor       - 차트 추출 (SXSSF 손실 방지)
   2. PivotExtractProcessor       - 피벗 테이블 정보 추출
   3. TemplateRenderProcessor     - 템플릿 렌더링 (데이터 바인딩)
   4. NumberFormatProcessor       - 숫자 서식 적용
   5. XmlVariableReplaceProcessor - XML 내 변수 치환
   6. PivotRecreateProcessor      - 피벗 테이블 재생성
   7. ChartRestoreProcessor       - 차트 복원
   8. MetadataProcessor           - 문서 메타데이터 적용
───────────────────────────────────────────────────────────────
       │
       ▼
  생성된 Excel
```

---

## 2. 파이프라인 패턴

### 2.1 TbegPipeline

파이프라인은 여러 프로세서를 순차적으로 실행합니다.

```kotlin
class TbegPipeline(vararg processors: ExcelProcessor) {
    fun execute(context: ProcessingContext): ProcessingContext
}
```

### 2.2 ExcelProcessor

각 프로세서는 특정 처리 단계를 담당합니다.

```kotlin
internal interface ExcelProcessor {
    val name: String
    fun shouldProcess(context: ProcessingContext): Boolean = true
    fun process(context: ProcessingContext): ProcessingContext
}
```

### 2.3 ProcessingContext

프로세서 간 데이터를 전달하는 컨텍스트입니다.

```kotlin
internal class ProcessingContext(
    val templateBytes: ByteArray,
    val dataProvider: ExcelDataProvider,
    val config: TbegConfig,
    val metadata: DocumentMetadata?
) {
    var resultBytes: ByteArray = templateBytes
    var processedRowCount: Int = 0
    // 프로세서 간 공유 데이터
    var chartInfo: ChartProcessor.ChartInfo? = null
    var pivotTableInfos: List<PivotTableProcessor.PivotTableInfo> = emptyList()
    var variableResolver: ((String) -> String)? = null
    var requiredNames: RequiredNames? = null
}
```

### 2.4 프로세서 구현 예시

```kotlin
class TemplateRenderProcessor : ExcelProcessor {
    override val name = "TemplateRender"

    override fun process(context: ProcessingContext): ProcessingContext {
        val analyzer = TemplateAnalyzer()
        val blueprint = XSSFWorkbook(ByteArrayInputStream(context.resultBytes)).use {
            analyzer.analyzeFromWorkbook(it)
        }
        context.requiredNames = blueprint.extractRequiredNames()

        val engine = TemplateRenderingEngine(context.config.streamingMode)
        context.resultBytes = engine.process(
            ByteArrayInputStream(context.resultBytes),
            context.dataProvider,
            context.requiredNames!!
        )
        return context
    }
}
```

---

## 3. 렌더링 전략

### 3.1 Strategy 패턴

렌더링 모드(XSSF/SXSSF)에 따라 다른 전략을 사용합니다.

```kotlin
internal interface RenderingStrategy {
    val name: String

    fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray
}

internal class XssfRenderingStrategy : RenderingStrategy
internal class SxssfRenderingStrategy : RenderingStrategy
```

### 3.2 XSSF vs SXSSF

| 특성    | XSSF           | SXSSF       |
|-------|----------------|-------------|
| 메모리   | 전체 워크북 메모리 로드  | 윈도우 기반 스트리밍 |
| 행 삽입  | shiftRows() 지원 | 순차 출력만 가능   |
| 수식 참조 | 자동 조정          | 자동 조정       |
| 대용량   | 제한적            | 적합          |

### 3.3 AbstractRenderingStrategy

공통 로직을 추상 클래스로 분리합니다.

```kotlin
internal abstract class AbstractRenderingStrategy : RenderingStrategy {
    // 추상 메서드 — 서브클래스 구현 필수
    protected abstract fun <T> withWorkbook(
        templateBytes: ByteArray,
        block: (workbook: Workbook, xssfWorkbook: XSSFWorkbook) -> T
    ): T
    protected abstract fun processSheet(
        sheet: Sheet, sheetIndex: Int, blueprint: SheetSpec,
        data: Map<String, Any>, imageLocations: MutableList<ImageLocation>,
        context: RenderingContext
    )
    protected abstract fun finalizeWorkbook(workbook: Workbook): ByteArray

    // 훅 메서드 — 선택적 오버라이드
    protected open fun beforeProcessSheets(workbook: Workbook, blueprint: WorkbookSpec, ...)
    protected open fun afterProcessSheets(workbook: Workbook, context: RenderingContext)

    // 공통 유틸리티
    protected fun processCellContent(cell: Cell, content: CellContent, ...)
    protected fun setCellValue(cell: Cell, value: Any?)
    protected fun insertImages(workbook: Workbook, imageLocations: List<ImageLocation>, ...)
}
```

---

## 4. 마커 파서

### 4.1 개요

템플릿 마커(`${...}`)를 파싱하는 전용 파서입니다. 선언적 정의로 마커를 쉽게 추가하고 관리합니다.

```
${repeat(employees, A3:C3, emp, DOWN)}
         │          │       │    │
         │          │       │    └─ direction (선택)
         │          │       └─ alias (선택)
         │          └─ range (필수)
         └─ collection (필수)
```

### 4.2 구성 요소

| 클래스                   | 역할              |
|-----------------------|-----------------|
| `MarkerDefinition`    | 마커별 이름과 파라미터 정의 |
| `UnifiedMarkerParser` | 마커 문자열 파싱       |
| `ParameterParser`     | 파라미터 값 파싱 및 변환  |
| `ParsedMarker`        | 파싱 결과 저장 및 접근   |

### 4.3 사용 예시

```kotlin
// 마커 파싱 — 반환 타입은 CellContent (sealed interface)
val content = UnifiedMarkerParser.parse("\${repeat(employees, A3:C3, emp, DOWN)}")

// 반환값은 CellContent의 서브타입으로 분기
when (content) {
    is CellContent.RepeatMarker -> {
        content.collection   // "employees"
        content.area         // CellArea
        content.variable     // "emp"
        content.direction    // RepeatDirection.DOWN
    }
    is CellContent.ImageMarker -> { content.name; content.position }
    is CellContent.Variable -> content.name
    is CellContent.ItemField -> content.fieldPath
    is CellContent.Formula -> content.formula
    // ... StaticString, StaticNumber, StaticBoolean, Empty 등
    else -> {}
}
```

### 4.4 지원 마커

| 마커       | 용도        | 필수 파라미터           | 선택 파라미터                         |
|----------|-----------|-------------------|---------------------------------|
| `repeat` | 반복 데이터 확장 | collection, range | var, direction(=DOWN), empty    |
| `image`  | 이미지 삽입    | name              | position, size(=fit)            |
| `size`   | 컬렉션 크기 출력 | collection        |                                 |

---

## 5. 위치 계산

### 5.0 공통 타입 (CommonTypes)

#### CollectionSizes

컬렉션 이름 → 아이템 수 매핑을 나타내는 value class이다. 위치 계산과 수식 확장에 사용된다.

```kotlin
// 팩토리 메서드
val sizes = CollectionSizes.of("employees" to 10, "departments" to 3)

// 빌더 함수
val sizes = buildCollectionSizes {
    put("employees", 10)
    put("departments", 3)
}

// 빈 인스턴스
val empty = CollectionSizes.EMPTY

// 조회
val count: Int? = sizes["employees"]  // 10
```

#### CellArea

셀 영역(start/end 좌표)을 표현하는 data class이다. `RepeatRegionSpec`과 `ColumnGroup` 등에서 영역 정보를 담는다.

```kotlin
// CellCoord 기반 생성
val area = CellArea(CellCoord(2, 0), CellCoord(5, 3))

// 4개 좌표 직접 생성
val area = CellArea(startRow = 2, startCol = 0, endRow = 5, endCol = 3)

// 프로퍼티 접근
area.start.row   // 2
area.end.col     // 3
area.rowRange    // RowRange(2, 5)
area.colRange    // ColRange(0, 3)

// 영역 겹침 판별
area.overlapsColumns(other)  // 열 범위 겹침 여부
area.overlapsRows(other)     // 행 범위 겹침 여부
area.overlaps(other)         // 2D 공간 겹침 여부
```

### 5.1 중복 마커 감지

`TemplateAnalyzer.analyzeWorkbook()`은 4단계로 템플릿을 분석합니다:

1. **수집**: 모든 시트에서 repeat 마커 수집 (`collectRepeatRegions`)
2. **repeat 중복 제거**: 같은 컬렉션 + 같은 대상 범위의 repeat이 여러 개이면 경고 후 마지막만 유지 (`deduplicateRepeatRegions`)
3. **SheetSpec 생성**: 중복 제거된 repeat 목록을 기반으로 각 시트 분석 (`analyzeSheet`)
4. **셀 마커 중복 제거**: image 등 셀에 남는 범위 마커의 중복을 후처리로 제거 (`deduplicateCellMarkers`)

대상 시트 결정: 범위에 시트 접두사(`'Sheet1'!A1:B2`)가 있으면 해당 시트, 없으면 마커가 위치한 시트가 대상입니다.

새로운 범위 마커를 추가할 때 중복 감지가 필요하면 `cellMarkerDedupKey()` 메서드의 `when` 분기에 추가합니다.

### 5.2 PositionCalculator

다중 repeat 영역의 위치를 계산합니다.

```kotlin
class PositionCalculator(
    repeatRegions: List<RepeatRegionSpec>,
    collectionSizes: CollectionSizes,
    templateLastRow: Int = -1
) {
    // 모든 repeat 확장 정보 계산
    fun calculate(): List<RepeatExpansion>

    // 템플릿 위치 -> 최종 위치 변환
    fun getFinalPosition(templateRow: Int, templateCol: Int): CellCoord
    fun getFinalPosition(template: CellCoord): CellCoord

    // 범위의 최종 위치 계산
    fun getFinalRange(start: CellCoord, end: CellCoord): CellRangeAddress
    fun getFinalRange(range: CellRangeAddress): CellRangeAddress

    // 실제 출력 행 정보 조회
    fun getRowInfo(actualRow: Int): RowInfo
}
```

### 5.3 RepeatExpansion

```kotlin
data class RepeatExpansion(
    val region: RepeatRegionSpec,    // 원본 repeat 영역
    val finalStartRow: Int,          // 확장 시작 행
    val finalStartCol: Int,          // 확장 시작 열
    val rowExpansion: Int,           // 행 확장량
    val colExpansion: Int,           // 열 확장량
    val itemCount: Int               // 아이템 수
)
```

### 5.4 위치 계산 규칙

1. **독립 요소**: 어느 repeat에도 영향받지 않으면 템플릿 위치 유지
2. **단일 영향**: 하나의 repeat에만 영향받으면 해당 확장량만큼 이동
3. **다중 영향**: 여러 repeat에 영향받으면 최대 오프셋 적용

### 5.5 열 그룹

같은 열 범위의 repeat는 서로 영향을 주고, 다른 열 그룹의 repeat는 독립적으로 확장됩니다.

```kotlin
data class ColumnGroup(
    val groupId: Int,
    val colRange: ColRange,
    val repeatRegions: List<RepeatRegionSpec>
)
```

---

## 6. 스트리밍 데이터 처리

### 6.1 StreamingDataSource

SXSSF 모드에서 Iterator를 순차적으로 소비합니다.

```kotlin
internal class StreamingDataSource(
    private val dataProvider: ExcelDataProvider,
    private val expectedSizes: CollectionSizes = CollectionSizes.EMPTY
) : Closeable {
    // repeat 영역별 현재 아이템
    fun advanceToNextItem(repeatKey: RepeatKey): Any?
    fun getCurrentItem(repeatKey: RepeatKey): Any?
}
```

### 6.2 메모리 최적화 원칙

| 모드    | 메모리 정책           |
|-------|------------------|
| SXSSF | 현재 아이템만 메모리 유지   |
| XSSF  | 전체 데이터 메모리 로드 허용 |

### 6.3 DataProvider 조건

- `getItems()`는 같은 데이터를 다시 제공할 수 있어야 함
- 같은 컬렉션이 여러 repeat에서 사용되면 `getItems()` 재호출

---

## 7. 테스트 작성 가이드

테스트 코드는 `src/test/kotlin/com/hunet/common/tbeg/`에 위치합니다.
테스트용 템플릿은 `src/test/resources/templates/`에 위치합니다.

### 7.1 테스트 예시

```kotlin
class PositionCalculatorTest {
    @Test
    fun `단일 repeat 확장 계산`() {
        val regions = listOf(
            RepeatRegionSpec(
                collection = "items",
                variable = "item",
                area = CellArea(2, 0, 2, 2),
                direction = RepeatDirection.DOWN
            )
        )

        val calculator = PositionCalculator(
            repeatRegions = regions,
            collectionSizes = CollectionSizes.of("items" to 5)
        )
        val expansions = calculator.calculate()

        assertEquals(1, expansions.size)
        assertEquals(4, expansions[0].rowExpansion)  // (5-1) * 1행
    }
}
```

### 7.2 통합 테스트 예시

```kotlin
class ExcelGeneratorIntegrationTest {
    @Test
    fun `반복 데이터 처리`() {
        val data = mapOf(
            "items" to listOf(
                mapOf("name" to "A", "value" to 100),
                mapOf("name" to "B", "value" to 200)
            )
        )

        ExcelGenerator().use { generator ->
            val template = javaClass.getResourceAsStream("/templates/repeat.xlsx")!!
            val bytes = generator.generate(template, data)

            XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                assertEquals("A", sheet.getRow(0).getCell(0).stringCellValue)
                assertEquals("B", sheet.getRow(1).getCell(0).stringCellValue)
            }
        }
    }
}
```

### 7.3 테스트 실행

```bash
# 전체 테스트
./gradlew :tbeg:test

# 특정 테스트
./gradlew :tbeg:test --tests "*PositionCalculator*"
```

### 7.4 샘플 및 벤치마크

테스트 코드와 별도로 실행 가능한 샘플 및 벤치마크 코드는 독립된 디렉토리에 관리됩니다.

```
src/test/
├── kotlin/com/hunet/common/tbeg/
│   ├── samples/                            # 샘플 코드 (Kotlin)
│   │   ├── TbegSample.kt
│   │   ├── EmptyCollectionSample.kt
│   │   ├── TbegSpringBootSample.kt
│   │   └── TemplateRenderingEngineSample.kt
│   ├── benchmark/                          # 벤치마크 코드
│   │   ├── PerformanceBenchmark.kt         # 대용량 벤치마크
│   │   └── PerformanceBenchmarkTest.kt     # XSSF vs SXSSF 비교
│   └── ...                                 # 테스트 코드
├── java/com/hunet/common/tbeg/samples/     # 샘플 코드 (Java)
│   ├── TbegJavaSample.java
│   └── TbegSpringBootJavaSample.java
```

```bash
# Kotlin 샘플 실행 (출력: build/samples/)
./gradlew :tbeg:runSample

# Java 샘플 실행 (출력: build/samples-java/)
./gradlew :tbeg:runJavaSample

# Spring Boot 샘플 실행 (출력: build/samples-spring/)
./gradlew :tbeg:runSpringBootSample

# 성능 벤치마크 실행
./gradlew :tbeg:runBenchmark
```

---

## 확장 포인트

### 커스텀 DataProvider

특수한 데이터 소스가 필요한 경우 `ExcelDataProvider`를 직접 구현합니다.

```kotlin
class DatabaseDataProvider(
    private val dataSource: DataSource
) : ExcelDataProvider {
    override fun getValue(name: String): Any? = /* SQL 쿼리 */
    override fun getItems(name: String): Iterator<Any>? = /* 스트리밍 쿼리 */
    override fun getItemCount(name: String): Int? = /* COUNT 쿼리 */
}
```

### 커스텀 프로세서

파이프라인에 새로운 처리 단계를 추가할 수 있습니다.

```kotlin
class WatermarkProcessor : ExcelProcessor {
    override val name = "Watermark"

    override fun process(context: ProcessingContext): ProcessingContext {
        // 워터마크 추가 로직
        return context
    }
}
```

---

## 다음 단계

- [API 레퍼런스](./reference/api-reference.md) - API 상세
- [설정 옵션](./reference/configuration.md) - 설정 옵션
