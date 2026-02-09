package io.github.jogakdal.tbeg.async

/**
 * Excel 생성 작업의 이벤트 리스너.
 *
 * API 서버에서 비동기 Excel 생성 작업의 상태를 추적하고
 * 완료 시 이벤트를 발행하는 데 사용된다.
 *
 * 모든 메서드는 기본 구현이 제공되므로 필요한 메서드만 오버라이드할 수 있다.
 *
 * ```kotlin
 * val listener = object : ExcelGenerationListener {
 *     override fun onCompleted(jobId: String, result: GenerationResult) {
 *         eventPublisher.publish(ReportReadyEvent(jobId, result.filePath))
 *     }
 * }
 * ```
 */
interface ExcelGenerationListener {

    /**
     * 작업이 시작되었을 때 호출된다.
     *
     * @param jobId 작업 고유 ID
     */
    fun onStarted(jobId: String) {}

    /**
     * 작업 진행 상황이 업데이트되었을 때 호출된다.
     *
     * 대용량 처리 시 주기적으로 호출되어 진행률을 보고한다.
     *
     * @param jobId 작업 고유 ID
     * @param progress 진행률 정보
     */
    fun onProgress(jobId: String, progress: ProgressInfo) {}

    /**
     * 작업이 성공적으로 완료되었을 때 호출된다.
     *
     * @param jobId 작업 고유 ID
     * @param result 생성 결과
     */
    fun onCompleted(jobId: String, result: GenerationResult) {}

    /**
     * 작업이 실패했을 때 호출된다.
     *
     * @param jobId 작업 고유 ID
     * @param error 발생한 예외
     */
    fun onFailed(jobId: String, error: Exception) {}

    /**
     * 작업이 취소되었을 때 호출된다.
     *
     * @param jobId 작업 고유 ID
     */
    fun onCancelled(jobId: String) {}
}
