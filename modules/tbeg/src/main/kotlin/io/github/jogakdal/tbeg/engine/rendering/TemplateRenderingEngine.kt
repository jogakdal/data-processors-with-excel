package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.engine.core.CollectionSizes
import io.github.jogakdal.tbeg.engine.core.buildCollectionSizes
import io.github.jogakdal.tbeg.engine.rendering.ChartRangeAdjuster.RepeatExpansionInfo
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
 * 명세 기반 순차 생성 (청사진 기반) 방식으로 동작한다.
 * 내부적으로 [StreamingRenderingStrategy]를 사용한다.
 */
class TemplateRenderingEngine(
    private val imageUrlCacheTtlSeconds: Long = 0
) {
    private val analyzer = TemplateAnalyzer()
    private val variableProcessor = VariableProcessor(emptyList())
    private val imageInserter = ImageInserter()
    private val sheetLayoutApplier = SheetLayoutApplier()

    private val fieldCache = mutableMapOf<Pair<Class<*>, String>, Field?>()
    private val getterCache = mutableMapOf<Pair<Class<*>, String>, Method?>()

    private val strategy: RenderingStrategy = StreamingRenderingStrategy()

    /**
     * 마지막 렌더링에서 수집된 시트별 repeat 확장 정보.
     * 차트 범위 조정에 사용된다.
     */
    internal var lastRepeatExpansionInfos: Map<String, List<RepeatExpansionInfo>> = emptyMap()
        private set

    /**
     * 기본 렌더링 컨텍스트 생성
     */
    private fun createRenderingContext(
        streamingDataSource: StreamingDataSource? = null,
        collectionSizes: CollectionSizes = CollectionSizes.EMPTY
    ) = RenderingContext(
        analyzer = analyzer,
        imageInserter = imageInserter,
        sheetLayoutApplier = sheetLayoutApplier,
        evaluateText = ::evaluateText,
        resolveFieldPath = ::resolveFieldPath,
        streamingDataSource = streamingDataSource,
        collectionSizes = collectionSizes,
        imageUrlCacheTtlSeconds = imageUrlCacheTtlSeconds
    )

    /**
     * 템플릿에 데이터를 바인딩하여 Excel 생성
     */
    fun process(template: InputStream, data: Map<String, Any>): ByteArray {
        val renderingContext = createRenderingContext()
        return strategy.render(template.readBytes(), data, renderingContext).also {
            lastRepeatExpansionInfos = renderingContext.repeatExpansionInfos.toMap()
        }
    }

    /**
     * 템플릿에 DataProvider 데이터를 바인딩하여 Excel 생성
     *
     * Iterator를 순차적으로 소비하여 메모리 사용을 최소화한다.
     *
     * **DataProvider 조건:**
     * - 같은 컬렉션이 여러 repeat에서 사용될 경우 getItems()가 다시 호출됨
     * - getItems()는 같은 데이터를 다시 제공할 수 있어야 함
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param requiredNames 템플릿에서 필요로 하는 데이터 이름 (선택적)
     */
    fun process(template: InputStream, dataProvider: ExcelDataProvider, requiredNames: RequiredNames? = null): ByteArray {
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
                val imageData = dataProvider.getImage(name)
                    ?: dataProvider.getValue("image.$name")  // URL 문자열 등 대체 소스
                imageData?.let { put("image.$name", it) }
            }
        }

        // StreamingDataSource 생성 (expectedSizes 전달: count 불일치 경고용)
        val streamingDataSource = StreamingDataSource(dataProvider, collectionSizes)

        return streamingDataSource.use { streamingDataSource ->
            val renderingContext = createRenderingContext(
                streamingDataSource = streamingDataSource,
                collectionSizes = collectionSizes
            )
            strategy.render(templateBytes, simpleData, renderingContext).also {
                lastRepeatExpansionInfos = renderingContext.repeatExpansionInfos.toMap()
            }
        }
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
