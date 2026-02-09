package io.github.jogakdal.tbeg

import java.util.function.Consumer
import java.util.function.Supplier

/**
 * [ExcelDataProvider]의 기본 구현체.
 *
 * Map 기반으로 데이터를 제공하며, 컬렉션 타입(List, Set 등)은 자동으로
 * [getItems]에서 Iterator로 반환된다.
 *
 * @param values 단일 값 맵 (Iterable이 아닌 값들)
 * @param collections 컬렉션 제공 함수 맵 (지연 로딩)
 * @param collectionCounts 컬렉션별 아이템 수 맵 (선택적)
 * @param images 이미지 제공 함수 맵 (지연 로딩)
 * @param metadata 문서 메타데이터
 */
class SimpleDataProvider private constructor(
    private val values: Map<String, Any>,
    private val collections: Map<String, () -> Iterator<Any>>,
    private val collectionCounts: Map<String, Int>,
    private val images: Map<String, () -> ByteArray>,
    private val metadata: DocumentMetadata?
) : ExcelDataProvider {
    override fun getValue(name: String): Any? = values[name]
    override fun getItems(name: String): Iterator<Any>? = collections[name]?.invoke()
    override fun getImage(name: String): ByteArray? = images[name]?.invoke()
    override fun getMetadata(): DocumentMetadata? = metadata
    override fun getItemCount(name: String): Int? = collectionCounts[name]

    companion object {
        /**
         * Map으로부터 SimpleDataProvider를 생성한다.
         *
         * Map의 값이 Iterable인 경우 자동으로 컬렉션으로, ByteArray인 경우 이미지로 분류된다.
         * List나 Collection인 경우 자동으로 count가 설정된다.
         *
         * ```kotlin
         * val provider = SimpleDataProvider.of(mapOf(
         *     "title" to "월별 보고서",
         *     "employees" to listOf(emp1, emp2, emp3),
         *     "logo" to logoBytes  // ByteArray는 이미지로 분류
         * ))
         * ```
         *
         * @param data 데이터 맵
         * @return SimpleDataProvider 인스턴스
         */
        @JvmStatic
        fun of(data: Map<String, Any>): SimpleDataProvider {
            val values = mutableMapOf<String, Any>()
            val collections = mutableMapOf<String, () -> Iterator<Any>>()
            val collectionCounts = mutableMapOf<String, Int>()
            val images = mutableMapOf<String, () -> ByteArray>()

            data.forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is ByteArray -> images[key] = { value }
                    is List<*> -> {
                        collections[key] = { (value as List<Any>).iterator() }
                        collectionCounts[key] = value.size
                    }
                    is Collection<*> -> {
                        collections[key] = { (value as Collection<Any>).iterator() }
                        collectionCounts[key] = value.size
                    }
                    is Iterable<*> -> collections[key] = { (value as Iterable<Any>).iterator() }
                    is Iterator<*> -> collections[key] = { value as Iterator<Any> }
                    is Sequence<*> -> collections[key] = { (value as Sequence<Any>).iterator() }
                    else -> values[key] = value
                }
            }

            return SimpleDataProvider(values, collections, collectionCounts, images, null)
        }

        /**
         * 빈 SimpleDataProvider를 반환한다.
         */
        @JvmStatic
        fun empty(): SimpleDataProvider = SimpleDataProvider(emptyMap(), emptyMap(), emptyMap(), emptyMap(), null)

        /**
         * Builder를 반환한다. (Java에서 사용하기 편리)
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * SimpleDataProvider 빌더.
     */
    class Builder {
        private val values = mutableMapOf<String, Any>()
        private val collections = mutableMapOf<String, () -> Iterator<Any>>()
        private val collectionCounts = mutableMapOf<String, Int>()
        private val images = mutableMapOf<String, () -> ByteArray>()
        private var metadata: DocumentMetadata? = null

        /** 단일 값을 추가한다. */
        fun value(name: String, value: Any) = apply { values[name] = value }

        /** 컬렉션을 추가한다. (즉시 로딩, count 자동 설정) */
        fun items(name: String, items: List<Any>) = apply {
            collections[name] = { items.iterator() }
            collectionCounts[name] = items.size
        }

        /** 컬렉션을 추가한다. (즉시 로딩) */
        fun items(name: String, items: Iterable<Any>) = apply {
            collections[name] = { items.iterator() }
            if (items is Collection<*>) {
                collectionCounts[name] = items.size
            }
        }

        /** 컬렉션을 추가한다. (지연 로딩 - Kotlin) */
        fun items(name: String, itemsSupplier: () -> Iterator<Any>) = apply {
            collections[name] = itemsSupplier
        }

        /**
         * 컬렉션과 개수를 함께 추가한다. (지연 로딩 + count 제공)
         *
         * 대용량 데이터 처리 시 count를 제공하면 최적의 성능을 얻을 수 있다.
         *
         * ```kotlin
         * items("employees", employeeCount) {
         *     employeeRepository.streamAll().iterator()
         * }
         * ```
         */
        fun items(name: String, count: Int, itemsSupplier: () -> Iterator<Any>) = apply {
            collections[name] = itemsSupplier
            collectionCounts[name] = count
        }

        /** 컬렉션을 추가한다. (지연 로딩 - Java Supplier) */
        @Suppress("unused") // Java API
        fun itemsFromSupplier(name: String, itemsSupplier: Supplier<Iterator<Any>>) = apply {
            collections[name] = { itemsSupplier.get() }
        }

        /**
         * 컬렉션과 개수를 함께 추가한다. (지연 로딩 - Java Supplier + count)
         */
        @Suppress("unused") // Java API
        fun itemsFromSupplier(name: String, count: Int, itemsSupplier: Supplier<Iterator<Any>>) = apply {
            collections[name] = { itemsSupplier.get() }
            collectionCounts[name] = count
        }

        /** 이미지를 추가한다. (즉시 로딩) */
        fun image(name: String, imageData: ByteArray) = apply { images[name] = { imageData } }

        /** 이미지를 추가한다. (지연 로딩 - Kotlin) */
        fun image(name: String, imageSupplier: () -> ByteArray) = apply { images[name] = imageSupplier }

        /** 이미지를 추가한다. (지연 로딩 - Java Supplier) */
        @Suppress("unused") // Java API
        fun imageFromSupplier(name: String, imageSupplier: Supplier<ByteArray>) = apply {
            images[name] = { imageSupplier.get() }
        }

        /** 문서 메타데이터를 설정한다. (Kotlin DSL) */
        fun metadata(block: DocumentMetadataBuilder.() -> Unit) = apply {
            this.metadata = DocumentMetadataBuilder().apply(block).build()
        }

        /** 문서 메타데이터를 설정한다. (Java Consumer) */
        fun metadata(configurer: Consumer<DocumentMetadata.Builder>) = apply {
            this.metadata = DocumentMetadata.Builder().also { configurer.accept(it) }.build()
        }

        /** 문서 메타데이터를 설정한다. (직접 설정) */
        fun metadata(metadata: DocumentMetadata) = apply { this.metadata = metadata }

        /** SimpleDataProvider를 빌드한다. */
        fun build() = SimpleDataProvider(values, collections, collectionCounts, images, metadata)
    }
}

/**
 * SimpleDataProvider를 DSL 방식으로 생성한다.
 *
 * ```kotlin
 * val provider = simpleDataProvider {
 *     value("title", "월별 보고서")
 *     items("employees") { employeeRepository.streamAll().iterator() }
 *     image("logo", logoBytes)
 * }
 * ```
 */
fun simpleDataProvider(block: SimpleDataProvider.Builder.() -> Unit): SimpleDataProvider =
    SimpleDataProvider.Builder().apply(block).build()
