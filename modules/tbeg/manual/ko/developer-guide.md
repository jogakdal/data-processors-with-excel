# TBEG 개발자 가이드

이 문서는 TBEG 라이브러리의 내부 아키텍처와 확장 방법을 설명합니다.

## 목차
1. [아키텍처 개요](#1-아키텍처-개요)
2. [파이프라인 패턴](#2-파이프라인-패턴)
3. [렌더링 엔진](#3-렌더링-엔진)
4. [마커 파서](#4-마커-파서)
5. [위치 계산](#5-위치-계산)
6. [테스트 및 샘플](#6-테스트-및-샘플)

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
│   │   ├── ConditionalFormattingUtils.kt # 조건부 서식 유틸리티
│   │   ├── ChartProcessor.kt
│   │   ├── PivotTableProcessor.kt
│   │   └── XmlVariableProcessor.kt
│   ├── pipeline/                       # 파이프라인 패턴
│   │   ├── TbegPipeline.kt
│   │   ├── ExcelProcessor.kt
│   │   ├── ProcessingContext.kt
│   │   └── processors/                 # 개별 프로세서
│   └── rendering/                      # 렌더링 엔진
│       ├── TemplateRenderingEngine.kt  # 렌더링 오케스트레이터
│       ├── RenderingStrategy.kt        # 전략 인터페이스
│       ├── AbstractRenderingStrategy.kt # 공통 렌더링 로직
│       ├── StreamingRenderingStrategy.kt # 스트리밍 전략 구현
│       ├── TemplateAnalyzer.kt         # 템플릿 분석
│       ├── PositionCalculator.kt       # 위치 계산 (체이닝 알고리즘)
│       ├── WorkbookSpec.kt             # 템플릿 분석 결과
│       ├── StreamingDataSource.kt      # 스트리밍 데이터 소비
│       ├── FormulaAdjuster.kt          # 수식 참조 범위 조정
│       ├── ChartRangeAdjuster.kt       # 차트 데이터 범위 조정
│       ├── ImageInserter.kt            # 이미지 삽입
│       ├── MergeTracker.kt             # repeat 확장 시 병합 셀 추적
│       ├── SheetLayoutApplier.kt       # 시트 레이아웃 적용 (행 높이, 열 너비, 병합 등)
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
   1. ChartExtractProcessor       - 차트 추출 (스트리밍 처리 시 손실 방지)
   2. PivotExtractProcessor       - 피벗 테이블 정보 추출
   3. TemplateRenderProcessor     - 템플릿 렌더링 (데이터 바인딩)
   4. NumberFormatProcessor       - 숫자/수식 셀 서식 적용
   5. XmlVariableReplaceProcessor - XML 내 변수 치환
   6. PivotRecreateProcessor      - 피벗 테이블 재생성
   7. ChartRestoreProcessor       - 차트 복원
   8. MetadataProcessor           - 문서 메타데이터 적용
───────────────────────────────────────────────────────────────
       │
       ▼
  생성된 Excel
```

### 1.3 설계 원칙

TBEG의 핵심 철학은 **Excel 네이티브 기능 우선**이다.

- Excel이 이미 잘하는 것(집계, 조건부 서식, 차트 등)은 재구현하지 않는다
- TBEG은 Excel이 할 수 없는 **동적 데이터 바인딩**(변수 치환, repeat 확장, 이미지 삽입)을 제공한다
- 데이터 확장 후에도 Excel 네이티브 기능이 의도대로 동작하도록 **보존하고 조정**한다 (수식 범위 확장, 조건부 서식 복제, 차트 데이터 범위 조정)

이 원칙이 파이프라인, 렌더링 전략, 위치 계산 등 모든 구현의 기준이 된다.

---

## 2. 파이프라인 패턴

### 2.1 TbegPipeline

파이프라인은 여러 프로세서를 순차적으로 실행합니다.

```kotlin
class TbegPipeline(vararg processors: ExcelProcessor) {
    fun execute(context: ProcessingContext): ProcessingContext
    fun addProcessor(processor: ExcelProcessor): TbegPipeline      // 불변, 새 인스턴스
    fun addProcessors(vararg processors: ExcelProcessor): TbegPipeline
    fun excludeProcessor(name: String): TbegPipeline
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
    var repeatExpansionInfos: Map<String, List<ChartRangeAdjuster.RepeatExpansionInfo>> = emptyMap()
}
```

| 프로퍼티 | 쓰기 | 읽기 |
|---------|------|------|
| `chartInfo` | ChartExtract | ChartRestore |
| `pivotTableInfos` | PivotExtract | PivotRecreate |
| `variableResolver` | TemplateRender | XmlVariableReplace |
| `requiredNames` | TemplateRender | XmlVariableReplace |
| `repeatExpansionInfos` | TemplateRender | ChartRestore |

### 2.4 프로세서 구현 예시

`TemplateRenderProcessor`의 실제 처리 흐름:

```kotlin
class TemplateRenderProcessor : ExcelProcessor {
    override val name = "TemplateRender"

    override fun process(context: ProcessingContext): ProcessingContext {
        // 1. 템플릿 분석 -> 필요한 데이터 이름 추출
        val blueprint = XSSFWorkbook(ByteArrayInputStream(context.resultBytes)).use { workbook ->
            TemplateAnalyzer().analyzeFromWorkbook(workbook)
        }
        val requiredNames = blueprint.extractRequiredNames()
        context.requiredNames = requiredNames

        // 2. 누락 데이터 검증 (WARN이면 로그, THROW이면 예외)
        validateMissingData(context, requiredNames)

        // 3. processedRowCount 계산
        context.processedRowCount = requiredNames.collections.sumOf { name ->
            context.dataProvider.getItems(name)?.asSequence()?.count() ?: 0
        }

        // 4. 렌더링 실행
        val engine = TemplateRenderingEngine(
            imageUrlCacheTtlSeconds = context.config.imageUrlCacheTtlSeconds
        )
        context.resultBytes = engine.process(
            ByteArrayInputStream(context.resultBytes),
            context.dataProvider,
            requiredNames
        )

        // 5. 차트 범위 조정을 위한 repeat 확장 정보 전달
        context.repeatExpansionInfos = engine.lastRepeatExpansionInfos

        return context
    }
}
```

### 2.5 커스텀 프로세서

파이프라인에 새로운 처리 단계를 추가할 수 있습니다.

```kotlin
class WatermarkProcessor : ExcelProcessor {
    override val name = "Watermark"

    override fun process(context: ProcessingContext): ProcessingContext {
        // 워터마크 추가 로직
        return context
    }
}

// 파이프라인에 추가
val customPipeline = pipeline.addProcessor(WatermarkProcessor())
```

---

## 3. 렌더링 엔진

### 3.1 TemplateRenderingEngine

렌더링의 중심 오케스트레이터이다. `RenderingStrategy`를 호출하여 실제 렌더링을 수행하고, `RenderingContext`를 통해 내부 컴포넌트들을 전달한다.

```kotlin
class TemplateRenderingEngine(
    private val imageUrlCacheTtlSeconds: Long = 0
) {
    private val analyzer = TemplateAnalyzer()
    private val imageInserter = ImageInserter()
    private val sheetLayoutApplier = SheetLayoutApplier()
    private val strategy: RenderingStrategy = StreamingRenderingStrategy()

    // 마지막 렌더링의 repeat 확장 정보 (차트 범위 조정에 사용)
    internal var lastRepeatExpansionInfos: Map<String, List<RepeatExpansionInfo>>

    // Map 기반 렌더링
    fun process(template: InputStream, data: Map<String, Any>): ByteArray

    // DataProvider 기반 렌더링 (스트리밍)
    fun process(template: InputStream, dataProvider: ExcelDataProvider,
                requiredNames: RequiredNames? = null): ByteArray
}
```

주요 내부 컴포넌트:

| 컴포넌트 | 역할 |
|---------|------|
| `FormulaAdjuster` | 수식 참조 범위 조정 (행 시프트, 범위 확장, 절대 참조 보존) |
| `ChartRangeAdjuster` | 차트 데이터 범위를 확장된 데이터에 맞게 조정 |
| `ImageInserter` | 이미지 삽입, 크기 조정, URL 다운로드 |
| `MergeTracker` | repeat 확장 시 `${merge()}` 마커에 의한 병합 셀 추적 |
| `SheetLayoutApplier` | 시트 레이아웃 적용 (행 높이, 열 너비, 병합, 머리글/바닥글, 조건부 서식 복제) |

### 3.2 Strategy 패턴

렌더링은 `StreamingRenderingStrategy` 하나의 전략으로 처리한다. 스트리밍 방식으로 메모리 효율적인 대용량 처리를 지원한다.

```kotlin
internal interface RenderingStrategy {
    val name: String

    fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray
}

internal class StreamingRenderingStrategy : AbstractRenderingStrategy()
```

### 3.3 AbstractRenderingStrategy

공통 로직을 추상 클래스로 분리한다.

```kotlin
internal abstract class AbstractRenderingStrategy : RenderingStrategy {
    // 추상 메서드 -- 서브클래스 구현 필수
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

    // 훅 메서드 -- 선택적 오버라이드
    protected open fun beforeProcessSheets(workbook: Workbook, blueprint: WorkbookSpec, ...)
    protected open fun afterProcessSheets(workbook: Workbook, context: RenderingContext)

    // 공통 유틸리티
    protected fun processCellContent(cell: Cell, content: CellContent, ...)
    protected fun setCellValue(cell: Cell, value: Any?)
    protected fun insertImages(workbook: Workbook, imageLocations: List<ImageLocation>, ...)
}
```

`setCellValue()`는 값이 `=`로 시작하면 Excel 수식으로 처리한다. `StreamingRenderingStrategy.setValueOrFormula()`에서 이를 분기한다.

### 3.4 스트리밍 데이터 처리

`StreamingDataSource`는 DataProvider의 Iterator를 순차적으로 소비하여 현재 처리 중인 아이템만 메모리에 유지한다.

```kotlin
internal class StreamingDataSource(
    private val dataProvider: ExcelDataProvider,
    private val expectedSizes: CollectionSizes = CollectionSizes.EMPTY
) : Closeable {
    fun advanceToNextItem(repeatKey: RepeatKey): Any?
    fun getCurrentItem(repeatKey: RepeatKey): Any?
}
```

DataProvider 조건:
- `getItems()`는 같은 데이터를 다시 제공할 수 있어야 한다
- 같은 컬렉션이 여러 repeat에서 사용되면 `getItems()`가 재호출된다

---

## 4. 마커 파서

### 4.1 개요

템플릿 마커(`${...}`)를 파싱하는 전용 파서이다. 선언적 정의로 마커를 쉽게 추가하고 관리한다.

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
// 마커 파싱 -- 반환 타입은 CellContent (sealed interface)
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
| `merge`  | 자동 셀 병합   | field             |                                 |
| `bundle` | 요소 묶음     | range             |                                 |

### 4.5 중복 마커 감지

`TemplateAnalyzer.analyzeWorkbook()`은 4단계로 템플릿을 분석한다:

1. **수집**: 모든 시트에서 repeat 마커 수집 (`collectRepeatRegions`)
2. **repeat 중복 제거**: 같은 컬렉션 + 같은 대상 범위의 repeat이 여러 개이면 경고 후 마지막만 유지 (`deduplicateRepeatRegions`)
3. **SheetSpec 생성**: 중복 제거된 repeat 목록을 기반으로 각 시트 분석 (`analyzeSheet`)
4. **셀 마커 중복 제거**: image 등 셀에 남는 범위 마커의 중복을 후처리로 제거 (`deduplicateCellMarkers`)

대상 시트 결정: 범위에 시트 접두사(`'Sheet1'!A1:B2`)가 있으면 해당 시트, 없으면 마커가 위치한 시트가 대상이다.

새로운 범위 마커를 추가할 때 중복 감지가 필요하면 `cellMarkerDedupKey()` 메서드의 `when` 분기에 추가한다.

---

## 5. 위치 계산

### 5.1 공통 타입 (CommonTypes)

#### CollectionSizes

컬렉션 이름 -> 아이템 수 매핑을 나타내는 value class이다. 위치 계산과 수식 확장에 사용된다.

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

셀 영역(start/end 좌표)을 표현하는 data class이다. `RepeatRegionSpec`, `BundleRegionSpec` 등에서 영역 정보를 담는다.

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

### 5.2 PositionCalculator

다중 repeat 영역의 위치를 체이닝 알고리즘으로 계산한다.

```kotlin
class PositionCalculator(
    repeatRegions: List<RepeatRegionSpec>,
    collectionSizes: CollectionSizes,
    templateLastRow: Int = -1,
    mergedRegions: List<CellRangeAddress> = emptyList(),
    bundleRegions: List<BundleRegionSpec> = emptyList()
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

### 5.4 위치 계산 규칙 (체이닝 알고리즘)

모든 요소(repeat, 병합 셀, bundle)의 위치는 **체이닝 알고리즘**으로 결정된다.

1. **요소 수집**: repeat -> Expandable, repeat 밖 병합 셀 -> Fixed, bundle -> Bundle
2. **위에서 아래로 순차 계산**: 각 요소의 열 범위에서 바로 위의 해결된 요소를 찾아 밀림량 계산
3. **여러 열에 걸친 요소**: 각 열의 계산 결과 중 **MAX**를 최종 위치로 채택

#### 핵심 규칙

- **독립 요소**: 위쪽에 확장 요소가 없으면 템플릿 위치 유지
- **밀림 전파**: 위의 요소가 밀리면, 그 아래의 요소도 밀린다
- **교차 열 전파**: 여러 열에 걸친 요소(병합 셀, bundle)를 통해 다른 열의 repeat 밀림이 전파

### 5.5 bundle (요소 묶음)

지정된 범위 내의 요소를 하나의 단위로 묶어 밀림 계산에 참여시킨다.

- 내부는 독자적인 시트처럼 밀림이 계산된다
- 크기가 확정된 bundle은 넓은 요소로서 체이닝에 참여한다
- 경계 걸침과 중첩은 금지된다

---

## 6. 테스트 및 샘플

테스트 코드는 `src/test/kotlin/io/github/jogakdal/tbeg/`에 위치한다.
테스트용 템플릿은 `src/test/resources/templates/`에 위치한다.

### 6.1 테스트 예시

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

### 6.2 통합 테스트 예시

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

### 6.3 테스트 실행

```bash
# 전체 테스트
./gradlew :tbeg:test

# 특정 테스트
./gradlew :tbeg:test --tests "*PositionCalculator*"
```

### 6.4 샘플 및 벤치마크

테스트 코드와 별도로 실행 가능한 샘플 및 벤치마크 코드는 독립된 디렉토리에 관리된다.

```
src/test/
├── kotlin/io/github/jogakdal/tbeg/
│   ├── samples/                            # 샘플 코드 (Kotlin)
│   │   ├── TbegSample.kt
│   │   ├── EmptyCollectionSample.kt
│   │   ├── FormulaSubstitutionSample.kt
│   │   ├── RichSample.kt
│   │   ├── CellMergeSampleRunner.kt
│   │   ├── TbegSpringBootSample.kt
│   │   └── TemplateRenderingEngineSample.kt
│   ├── benchmark/                          # 벤치마크 코드
│   │   ├── PerformanceBenchmark.kt         # 대용량 벤치마크
│   │   └── PerformanceBenchmarkTest.kt     # 처리 속도 벤치마크
│   └── ...                                 # 테스트 코드
├── java/io/github/jogakdal/tbeg/samples/     # 샘플 코드 (Java)
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

## 다음 단계

- [API 레퍼런스](./reference/api-reference.md) - API 상세
- [설정 옵션](./reference/configuration.md) - 설정 옵션
- [사용자 가이드](./user-guide.md) - 커스텀 DataProvider 구현 예시
