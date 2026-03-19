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

/**
 * hideable 마커의 숨김 모드.
 */
enum class HideMode {
    /** 물리적 삭제 후 나머지 요소를 당긴다 (기본값). */
    DELETE,

    /** 비활성화 스타일(회색 배경 + 연한 글자색)을 적용하고 셀 값만 제거한다. */
    DIM
}

/**
 * hideable 마커 없이 hideFields에 지정된 필드의 처리 정책.
 */
enum class UnmarkedHidePolicy {
    /**
     * 경고 로그를 출력하고 해당 셀을 DIM 모드로 숨긴다 (기본값).
     *
     * 템플릿에 hideable 마커가 없는 필드가 hideFields에 지정된 경우,
     * 경고를 출력하고 해당 셀에 비활성화 스타일을 적용한다.
     */
    WARN_AND_HIDE,

    /**
     * 예외를 발생시킨다.
     *
     * 템플릿과 hideFields 간의 불일치를 엄격하게 검증할 때 사용한다.
     */
    ERROR
}
