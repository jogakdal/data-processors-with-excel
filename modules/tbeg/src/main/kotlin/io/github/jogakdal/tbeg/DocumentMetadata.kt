package io.github.jogakdal.tbeg

import java.time.LocalDateTime

/**
 * Excel 문서 메타데이터.
 *
 * 생성된 Excel 파일의 문서 속성(제목, 작성자 등)을 설정한다.
 * Excel에서 "파일 > 정보 > 속성"에서 확인할 수 있다.
 *
 * ## 사용 예시 (Kotlin)
 * ```kotlin
 * val provider = simpleDataProvider {
 *     value("title", "보고서")
 *     metadata {
 *         title = "월간 실적 보고서"
 *         author = "황용호"
 *         company = "(주)휴넷"
 *     }
 * }
 * ```
 *
 * ## 사용 예시 (Java)
 * ```java
 * var provider = SimpleDataProvider.builder()
 *     .value("title", "보고서")
 *     .metadata(meta -> meta
 *         .title("월간 실적 보고서")
 *         .author("황용호")
 *         .company("(주)휴넷"))
 *     .build();
 * ```
 *
 * @property title 문서 제목
 * @property author 작성자
 * @property subject 주제
 * @property keywords 키워드 목록
 * @property description 설명
 * @property category 범주
 * @property company 회사명
 * @property manager 관리자
 * @property created 작성 일시 (null이면 현재 시간)
 */
data class DocumentMetadata(
    val title: String? = null,
    val author: String? = null,
    val subject: String? = null,
    val keywords: List<String>? = null,
    val description: String? = null,
    val category: String? = null,
    val company: String? = null,
    val manager: String? = null,
    val created: LocalDateTime? = null
) {
    companion object {
        /**
         * 빈 메타데이터를 반환한다.
         */
        @JvmStatic
        fun empty(): DocumentMetadata = DocumentMetadata()

        /**
         * Java용 빌더를 반환한다.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * 메타데이터가 비어있는지 확인한다.
     */
    fun isEmpty(): Boolean =
        title == null && author == null && subject == null &&
        keywords == null && description == null && category == null &&
        company == null && manager == null && created == null

    /**
     * Java용 빌더 클래스.
     */
    class Builder {
        private var title: String? = null
        private var author: String? = null
        private var subject: String? = null
        private var keywords: List<String>? = null
        private var description: String? = null
        private var category: String? = null
        private var company: String? = null
        private var manager: String? = null
        private var created: LocalDateTime? = null

        fun title(title: String?) = apply { this.title = title }
        fun author(author: String?) = apply { this.author = author }
        fun subject(subject: String?) = apply { this.subject = subject }
        fun keywords(keywords: List<String>?) = apply { this.keywords = keywords }
        fun keywords(vararg keywords: String) = apply { this.keywords = keywords.toList() }
        fun description(description: String?) = apply { this.description = description }
        fun category(category: String?) = apply { this.category = category }
        fun company(company: String?) = apply { this.company = company }
        fun manager(manager: String?) = apply { this.manager = manager }
        fun created(created: LocalDateTime?) = apply { this.created = created }

        fun build() = DocumentMetadata(
            title = title,
            author = author,
            subject = subject,
            keywords = keywords,
            description = description,
            category = category,
            company = company,
            manager = manager,
            created = created
        )
    }
}

/**
 * Kotlin DSL용 메타데이터 빌더.
 */
class DocumentMetadataBuilder {
    var title: String? = null
    var author: String? = null
    var subject: String? = null
    var keywords: List<String>? = null
    var description: String? = null
    var category: String? = null
    var company: String? = null
    var manager: String? = null
    var created: LocalDateTime? = null

    /**
     * 키워드를 가변 인자로 설정한다.
     */
    fun keywords(vararg keywords: String) {
        this.keywords = keywords.toList()
    }

    internal fun build() = DocumentMetadata(
        title = title,
        author = author,
        subject = subject,
        keywords = keywords,
        description = description,
        category = category,
        company = company,
        manager = manager,
        created = created
    )
}
