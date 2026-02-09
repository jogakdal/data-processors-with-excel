package io.github.jogakdal.tbeg.spring

import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.FileConflictPolicy
import io.github.jogakdal.tbeg.FileNamingMode
import io.github.jogakdal.tbeg.MissingDataBehavior
import io.github.jogakdal.tbeg.StreamingMode
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * TBEG (Template Based Excel Generator) 설정 프로퍼티.
 *
 * application.yml 예시:
 * ```yaml
 * tbeg:
 *     streaming-mode: enabled         # enabled, disabled
 *     file-naming-mode: timestamp     # none, timestamp
 *     timestamp-format: yyyyMMdd_HHmmss
 *     file-conflict-policy: sequence  # error, sequence
 *     progress-report-interval: 100   # 진행률 콜백 호출 간격
 *     preserve-template-layout: true  # 템플릿 레이아웃 보존
 *     pivot-integer-format-index: 37  # 피벗 테이블 정수 필드 포맷 인덱스
 *     pivot-decimal-format-index: 39  # 피벗 테이블 소수점 필드 포맷 인덱스
 *     missing-data-behavior: warn     # warn, throw
 * ```
 */
@ConfigurationProperties(prefix = "tbeg")
data class TbegProperties(
    /**
     * 스트리밍 모드 설정.
     * - enabled: SXSSF 스트리밍 사용 (기본값, 대용량 최적화)
     * - disabled: 항상 XSSF 사용
     */
    var streamingMode: StreamingModeProperty = StreamingModeProperty.ENABLED,

    /**
     * 파일명 생성 모드.
     * - none: suffix 없이 기본 파일명만 사용
     * - timestamp: 타임스탬프를 suffix로 추가 (기본값)
     */
    var fileNamingMode: FileNamingModeProperty = FileNamingModeProperty.TIMESTAMP,

    /**
     * 파일명에 추가되는 타임스탬프 형식.
     */
    var timestampFormat: String = "yyyyMMdd_HHmmss",

    /**
     * 파일명 충돌 시 처리 정책.
     * - error: 동일 파일명이 존재하면 예외 발생
     * - sequence: 동일 파일명이 존재하면 시퀀스 번호 추가 (기본값)
     */
    var fileConflictPolicy: FileConflictPolicyProperty = FileConflictPolicyProperty.SEQUENCE,

    /**
     * 진행률 콜백 호출 간격 (행 수).
     */
    var progressReportInterval: Int = 100,

    /**
     * 반복 영역 확장 시 원본 템플릿의 열 폭과 행 높이를 보존할지 여부.
     */
    var preserveTemplateLayout: Boolean = true,

    /**
     * 숫자 자동 서식의 정수 필드에 적용할 Excel 내장 포맷 인덱스.
     */
    var pivotIntegerFormatIndex: Short = 3,

    /**
     * 숫자 자동 서식의 소수점 필드에 적용할 Excel 내장 포맷 인덱스.
     */
    var pivotDecimalFormatIndex: Short = 4,

    /**
     * 템플릿에 정의된 데이터가 없을 때의 동작.
     * - warn: 경고 로그 출력 후 마커 그대로 유지 (기본값)
     * - throw: MissingTemplateDataException 발생
     */
    var missingDataBehavior: MissingDataBehaviorProperty = MissingDataBehaviorProperty.WARN
) {
    /**
     * TbegConfig로 변환한다.
     */
    fun toConfig(): TbegConfig = TbegConfig(
        streamingMode = streamingMode.toStreamingMode(),
        fileNamingMode = fileNamingMode.toFileNamingMode(),
        timestampFormat = timestampFormat,
        fileConflictPolicy = fileConflictPolicy.toFileConflictPolicy(),
        progressReportInterval = progressReportInterval,
        preserveTemplateLayout = preserveTemplateLayout,
        pivotIntegerFormatIndex = pivotIntegerFormatIndex,
        pivotDecimalFormatIndex = pivotDecimalFormatIndex,
        missingDataBehavior = missingDataBehavior.toMissingDataBehavior()
    )
}

/**
 * 스트리밍 모드 프로퍼티 (application.yml 바인딩용).
 */
enum class StreamingModeProperty {
    ENABLED, DISABLED;

    fun toStreamingMode(): StreamingMode = when (this) {
        ENABLED -> StreamingMode.ENABLED
        DISABLED -> StreamingMode.DISABLED
    }
}

/**
 * 파일명 생성 모드 프로퍼티 (application.yml 바인딩용).
 */
enum class FileNamingModeProperty {
    NONE, TIMESTAMP;

    fun toFileNamingMode(): FileNamingMode = when (this) {
        NONE -> FileNamingMode.NONE
        TIMESTAMP -> FileNamingMode.TIMESTAMP
    }
}

/**
 * 파일 충돌 정책 프로퍼티 (application.yml 바인딩용).
 */
enum class FileConflictPolicyProperty {
    ERROR, SEQUENCE;

    fun toFileConflictPolicy(): FileConflictPolicy = when (this) {
        ERROR -> FileConflictPolicy.ERROR
        SEQUENCE -> FileConflictPolicy.SEQUENCE
    }
}

/**
 * 누락 데이터 동작 프로퍼티 (application.yml 바인딩용).
 */
enum class MissingDataBehaviorProperty {
    WARN, THROW;

    fun toMissingDataBehavior(): MissingDataBehavior = when (this) {
        WARN -> MissingDataBehavior.WARN
        THROW -> MissingDataBehavior.THROW
    }
}
