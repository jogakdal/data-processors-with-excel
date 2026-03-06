package io.github.jogakdal.tbeg

/**
 * Excel 생성 시 스트리밍 모드 설정.
 *
 * 1.2.0부터 항상 스트리밍 모드로 동작하며, 이 enum은 향후 버전에서 제거된다.
 */
@Deprecated("Since 1.2.0, only streaming mode is supported. This enum will be removed in a future version.")
enum class StreamingMode {
    /**
     * 스트리밍 모드 비활성화.
     *
     * 1.2.0부터 DISABLED는 무시되며 항상 스트리밍 모드로 동작한다.
     */
    @Deprecated("Since 1.2.0, DISABLED is ignored and streaming mode is always used.")
    DISABLED,

    /**
     * 스트리밍 모드 활성화 (기본값).
     */
    ENABLED
}

/**
 * 파일명 생성 모드.
 */
enum class FileNamingMode {
    /**
     * 기본 파일명만 사용 (suffix 없음).
     * 예: report.xlsx
     */
    NONE,

    /**
     * 타임스탬프를 suffix로 추가.
     * 예: report_20240107_143052.xlsx
     */
    TIMESTAMP
}

/**
 * 파일명 충돌 시 처리 정책.
 */
enum class FileConflictPolicy {
    /**
     * 동일 파일명이 존재하면 예외 발생.
     */
    ERROR,

    /**
     * 동일 파일명이 존재하면 시퀀스 번호를 추가.
     * 예: report.xlsx -> report_1.xlsx -> report_2.xlsx
     */
    SEQUENCE
}

/**
 * 템플릿에 정의된 변수/컬렉션/이미지에 대응하는 데이터가 없을 때의 동작.
 */
enum class MissingDataBehavior {
    /**
     * 경고 로그를 출력하고 마커를 그대로 유지 (기본값).
     *
     * 누락된 데이터에 대해 WARNING 레벨 로그를 출력한다.
     * 디버깅 및 데이터 누락 감지에 유용하다.
     */
    WARN,

    /**
     * 예외를 발생시킴.
     *
     * 데이터 무결성이 중요한 경우 사용한다.
     * [io.github.jogakdal.tbeg.exception.MissingTemplateDataException]이 발생한다.
     */
    THROW
}
