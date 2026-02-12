package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.core.buildCollectionSizes
import io.github.jogakdal.tbeg.internal.VariableProcessor
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 템플릿 렌더링 엔진 - Excel 템플릿 기반 데이터 바인딩
 *
 * ## 지원 문법
 * - `${변수명}` - 단순 변수 치환
 * - `${item.field}` - 반복 항목의 필드 접근
 * - `${repeat(collection, range, var)}` - 반복 처리
 * - `${image(name)}` - 이미지 삽입
 * - `${size(collection)}` - 컬렉션 크기 (예: `${size(employees)}명`)
 * - `HYPERLINK("${url}", "${text}")` - 수식 내 변수 치환
 *
 * ## 수식 형태 마커
 * - `=TBEG_REPEAT(collection, range, var)` - 반복 처리
 * - `=TBEG_IMAGE(name)` - 이미지 삽입
 * - `=TBEG_SIZE(collection)` - 컬렉션 크기
 *
 * ## 처리 방식
 * - **비스트리밍 모드**: XSSF 기반 템플릿 변환 (shiftRows + copyRowFrom)
 * - **스트리밍 모드**: SXSSF 기반 순차 생성 (청사진 기반)
 *
 * ## Strategy Pattern
 * 내부적으로 RenderingStrategy를 사용하여 XSSF/SXSSF 모드를 분리한다.
 * - [XssfRenderingStrategy]: 비스트리밍 모드
 * - [SxssfRenderingStrategy]: 스트리밍 모드
 */
class TemplateRenderingEngine(
    private val streamingMode: StreamingMode = StreamingMode.ENABLED
) {
    private val analyzer = TemplateAnalyzer()
    private val variableProcessor = VariableProcessor(emptyList())
    private val imageInserter = ImageInserter()
    private val repeatExpansionProcessor = RepeatExpansionProcessor()
    private val sheetLayoutApplier = SheetLayoutApplier()

    private val fieldCache = mutableMapOf<Pair<Class<*>, String>, Field?>()
    private val getterCache = mutableMapOf<Pair<Class<*>, String>, Method?>()

    private val strategy: RenderingStrategy = when (streamingMode) {
        StreamingMode.DISABLED -> XssfRenderingStrategy()
        StreamingMode.ENABLED -> SxssfRenderingStrategy()
    }

    /**
     * 기본 렌더링 컨텍스트 생성
     */
    private fun createRenderingContext(
        streamingDataSource: StreamingDataSource? = null,
        collectionSizes: CollectionSizes = CollectionSizes.EMPTY
    ) = RenderingContext(
        analyzer = analyzer,
        imageInserter = imageInserter,
        repeatExpansionProcessor = repeatExpansionProcessor,
        sheetLayoutApplier = sheetLayoutApplier,
        evaluateText = ::evaluateText,
        resolveFieldPath = ::resolveFieldPath,
        streamingDataSource = streamingDataSource,
        collectionSizes = collectionSizes
    )

    /**
     * 템플릿에 데이터를 바인딩하여 Excel 생성
     */
    fun process(template: InputStream, data: Map<String, Any>) =
        strategy.render(template.readBytes(), data, createRenderingContext())

    /**
     * 템플릿에 DataProvider 데이터를 바인딩하여 Excel 생성
     *
     * **메모리 효율성 (모드별):**
     * - **SXSSF (스트리밍)**: Iterator 순차 소비, 현재 아이템만 메모리 유지
     * - **XSSF (비스트리밍)**: List로 변환 (소량 데이터 전용)
     *
     * **DataProvider 조건 (SXSSF):**
     * - 같은 컬렉션이 여러 repeat에서 사용될 경우 getItems()가 다시 호출됨
     * - getItems()는 같은 데이터를 다시 제공할 수 있어야 함
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param requiredNames 템플릿에서 필요로 하는 데이터 이름 (선택적)
     */
    fun process(template: InputStream, dataProvider: ExcelDataProvider, requiredNames: RequiredNames? = null) =
        when (streamingMode) {
            StreamingMode.ENABLED -> processWithStreaming(template, dataProvider, requiredNames)
            StreamingMode.DISABLED -> processWithoutStreaming(template, dataProvider, requiredNames)
        }

    /**
     * SXSSF (스트리밍) 모드 처리
     *
     * - Iterator를 순차적으로 소비하여 메모리 사용 최소화
     * - 현재 아이템만 메모리에 유지
     */
    private fun processWithStreaming(template: InputStream,
        dataProvider: ExcelDataProvider,
        requiredNames: RequiredNames?
    ): ByteArray {
        val templateBytes = template.readBytes()

        // 컬렉션 크기 계산 (위치 계산용)
        val collectionSizes = buildCollectionSizes {
            requiredNames?.collections?.forEach { name ->
                put(name, getCollectionSize(dataProvider, name))
            }
        }

        // 단순 변수와 이미지 데이터 수집 (컬렉션 제외)
        val simpleData = buildMap {
            requiredNames?.variables?.forEach { name ->
                dataProvider.getValue(name)?.let { put(name, it) }
            }
            requiredNames?.images?.forEach { name ->
                dataProvider.getImage(name)?.let { put("image.$name", it) }
            }
        }

        // StreamingDataSource 생성 (expectedSizes 전달: count 불일치 경고용)
        val streamingDataSource = StreamingDataSource(dataProvider, collectionSizes)

        return streamingDataSource.use { streamingDataSource ->
            val renderingContext = createRenderingContext(
                streamingDataSource = streamingDataSource,
                collectionSizes = collectionSizes
            )
            strategy.render(templateBytes, simpleData, renderingContext)
        }
    }

    /**
     * XSSF (비스트리밍) 모드 처리
     *
     * - 컬렉션을 List로 변환하여 전체 메모리에 로드
     * - 소량 데이터 전용
     */
    private fun processWithoutStreaming(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        requiredNames: RequiredNames?
    ) = buildMap {
        requiredNames?.let { names ->
            // 단순 변수
            names.variables.forEach { name ->
                dataProvider.getValue(name)?.let { put(name, it) }
            }

            // 컬렉션 (List로 변환)
            names.collections.forEach { name ->
                val iterator = dataProvider.getItems(name) ?: return@forEach
                put(name, iterator.asSequence().toList())
            }

            // 이미지
            names.images.forEach { name ->
                dataProvider.getImage(name)?.let { put("image.$name", it) }
            }
        }
    }.let { data ->
        strategy.render(template.readBytes(), data, createRenderingContext())
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateText(text: String, data: Map<String, Any>) =
        variableProcessor.processWithData(text, data as Map<String, Any?>)

    private fun resolveFieldPath(obj: Any?, fieldPath: String): Any? {
        if (obj == null) return null

        val fields = fieldPath.split(".")
        var current: Any? = obj

        for (field in fields) {
            current = when (current) {
                is Map<*, *> -> current[field]
                else -> resolveField(current!!, field)
            }
            if (current == null) break
        }

        return current
    }

    /**
     * 리플렉션으로 필드/getter 값을 가져옴 (캐싱 적용)
     */
    private fun resolveField(obj: Any, field: String): Any? {
        val clazz = obj::class.java
        val cacheKey = clazz to field

        val cachedField = fieldCache.getOrPut(cacheKey) {
            runCatching {
                clazz.getDeclaredField(field).apply { isAccessible = true }
            }.getOrNull()
        }

        if (cachedField != null) {
            return runCatching { cachedField.get(obj) }.getOrNull()
        }

        val cachedGetter = getterCache.getOrPut(cacheKey) {
            runCatching {
                clazz.getMethod("get${field.replaceFirstChar { it.uppercase() }}")
            }.getOrNull()
        }

        return cachedGetter?.let { runCatching { it.invoke(obj) }.getOrNull() }
    }
}
