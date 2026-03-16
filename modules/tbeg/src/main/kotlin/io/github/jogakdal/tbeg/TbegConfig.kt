package io.github.jogakdal.tbeg

/**
 * TBEG 설정.
 *
 * @property streamingMode 스트리밍 모드 (1.2.0부터 deprecated, 항상 스트리밍 모드로 동작)
 * @property fileNamingMode 파일명 생성 모드 (기본: TIMESTAMP)
 * @property timestampFormat 파일명에 추가되는 타임스탬프 형식 (기본: yyyyMMdd_HHmmss)
 * @property fileConflictPolicy 파일명 충돌 시 처리 정책 (기본: SEQUENCE)
 * @property progressReportInterval 진행률 콜백 호출 간격 (행 수, 기본: 100)
 * @property preserveTemplateLayout 반복 영역 확장 시 원본 템플릿의 열 폭과 행 높이를 보존할지 여부 (기본: true)
 * @property pivotIntegerFormatIndex 숫자 자동 서식의 정수 필드에 적용할 Excel 내장 포맷 인덱스 (기본: 3, "#,##0")
 * @property pivotDecimalFormatIndex 숫자 자동 서식의 소수점 필드에 적용할 Excel 내장 포맷 인덱스 (기본: 4, "#,##0.00")
 * @property missingDataBehavior 템플릿에 정의된 데이터가 없을 때의 동작 (기본: WARN)
 * @property imageUrlCacheTtlSeconds 이미지 URL 다운로드 결과의 캐시 TTL (초, 기본: 0 = 호출 간 캐싱 안 함)
 */
data class TbegConfig(
    @Deprecated("Since 1.2.0, streaming mode is always used. This property will be removed in a future version.")
    val streamingMode: StreamingMode = StreamingMode.ENABLED,
    val fileNamingMode: FileNamingMode = FileNamingMode.TIMESTAMP,
    val timestampFormat: String = "yyyyMMdd_HHmmss",
    val fileConflictPolicy: FileConflictPolicy = FileConflictPolicy.SEQUENCE,
    val progressReportInterval: Int = 100,
    val preserveTemplateLayout: Boolean = true,
    val pivotIntegerFormatIndex: Short = 3,
    val pivotDecimalFormatIndex: Short = 4,
    val missingDataBehavior: MissingDataBehavior = MissingDataBehavior.WARN,
    val imageUrlCacheTtlSeconds: Long = 0,
    val unmarkedHidePolicy: UnmarkedHidePolicy = UnmarkedHidePolicy.WARN_AND_HIDE
) {
    companion object {
        /**
         * 기본 설정을 반환한다.
         */
        @JvmStatic
        fun default(): TbegConfig = TbegConfig()

        /**
         * 대용량 처리에 최적화된 설정을 반환한다.
         */
        @JvmStatic
        fun forLargeData(): TbegConfig = TbegConfig(
            progressReportInterval = 500
        )

        /**
         * 소량 데이터 처리에 최적화된 설정을 반환한다.
         *
         * 1.2.0부터 `default()`와 동일하게 동작한다.
         */
        @Deprecated("Since 1.2.0, this behaves identically to default(). Use default() instead.", ReplaceWith("default()"))
        @JvmStatic
        fun forSmallData(): TbegConfig = TbegConfig()

        /**
         * Builder를 반환한다. (Java에서 사용하기 편리)
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * Java용 빌더 클래스.
     */
    class Builder {
        private var streamingMode: StreamingMode = StreamingMode.ENABLED
        private var fileNamingMode: FileNamingMode = FileNamingMode.TIMESTAMP
        private var timestampFormat: String = "yyyyMMdd_HHmmss"
        private var fileConflictPolicy: FileConflictPolicy = FileConflictPolicy.SEQUENCE
        private var progressReportInterval: Int = 100
        private var preserveTemplateLayout: Boolean = true
        private var pivotIntegerFormatIndex: Short = 3
        private var pivotDecimalFormatIndex: Short = 4
        private var missingDataBehavior: MissingDataBehavior = MissingDataBehavior.WARN
        private var imageUrlCacheTtlSeconds: Long = 0
        private var unmarkedHidePolicy: UnmarkedHidePolicy = UnmarkedHidePolicy.WARN_AND_HIDE

        @Deprecated("Since 1.2.0, streaming mode is always used. This method will be removed in a future version.")
        fun streamingMode(mode: StreamingMode) = apply { this.streamingMode = mode }
        fun fileNamingMode(mode: FileNamingMode) = apply { this.fileNamingMode = mode }
        fun timestampFormat(format: String) = apply { this.timestampFormat = format }
        fun fileConflictPolicy(policy: FileConflictPolicy) = apply { this.fileConflictPolicy = policy }
        fun progressReportInterval(interval: Int) = apply { this.progressReportInterval = interval }
        fun preserveTemplateLayout(preserve: Boolean) = apply { this.preserveTemplateLayout = preserve }
        fun pivotIntegerFormatIndex(index: Short) = apply { this.pivotIntegerFormatIndex = index }
        fun pivotDecimalFormatIndex(index: Short) = apply { this.pivotDecimalFormatIndex = index }
        fun missingDataBehavior(behavior: MissingDataBehavior) = apply { this.missingDataBehavior = behavior }
        fun imageUrlCacheTtlSeconds(ttl: Long) = apply { this.imageUrlCacheTtlSeconds = ttl }
        fun unmarkedHidePolicy(policy: UnmarkedHidePolicy) = apply { this.unmarkedHidePolicy = policy }

        fun build() = TbegConfig(
            streamingMode = streamingMode,
            fileNamingMode = fileNamingMode,
            timestampFormat = timestampFormat,
            fileConflictPolicy = fileConflictPolicy,
            progressReportInterval = progressReportInterval,
            preserveTemplateLayout = preserveTemplateLayout,
            pivotIntegerFormatIndex = pivotIntegerFormatIndex,
            pivotDecimalFormatIndex = pivotDecimalFormatIndex,
            missingDataBehavior = missingDataBehavior,
            imageUrlCacheTtlSeconds = imageUrlCacheTtlSeconds,
            unmarkedHidePolicy = unmarkedHidePolicy
        )
    }

    /**
     * 설정을 수정한 새 인스턴스를 반환한다.
     */
    @Deprecated("Since 1.2.0, streaming mode is always used. This method will be removed in a future version.")
    fun withStreamingMode(mode: StreamingMode): TbegConfig =
        copy(streamingMode = mode)

    fun withFileNamingMode(mode: FileNamingMode): TbegConfig =
        copy(fileNamingMode = mode)

    fun withTimestampFormat(format: String): TbegConfig =
        copy(timestampFormat = format)

    fun withFileConflictPolicy(policy: FileConflictPolicy): TbegConfig =
        copy(fileConflictPolicy = policy)

    fun withProgressReportInterval(interval: Int): TbegConfig =
        copy(progressReportInterval = interval)

    fun withPreserveTemplateLayout(preserve: Boolean): TbegConfig =
        copy(preserveTemplateLayout = preserve)

    fun withPivotIntegerFormatIndex(index: Short): TbegConfig =
        copy(pivotIntegerFormatIndex = index)

    fun withPivotDecimalFormatIndex(index: Short): TbegConfig =
        copy(pivotDecimalFormatIndex = index)

    fun withMissingDataBehavior(behavior: MissingDataBehavior): TbegConfig =
        copy(missingDataBehavior = behavior)

    fun withImageUrlCacheTtlSeconds(ttl: Long): TbegConfig =
        copy(imageUrlCacheTtlSeconds = ttl)

    fun withUnmarkedHidePolicy(policy: UnmarkedHidePolicy): TbegConfig =
        copy(unmarkedHidePolicy = policy)
}
