package io.github.jogakdal.tbeg.engine.pipeline

/**
 * Excel 처리 파이프라인의 프로세서 인터페이스.
 *
 * 각 프로세서는 파이프라인의 한 단계를 담당하며,
 * ProcessingContext를 입력받아 처리 후 반환한다.
 *
 * ## 구현 예시
 * ```kotlin
 * class MyProcessor : ExcelProcessor {
 *     override val name = "MyProcessor"
 *
 *     override fun shouldProcess(context: ProcessingContext): Boolean {
 *         return context.config.someOption
 *     }
 *
 *     override fun process(context: ProcessingContext): ProcessingContext {
 *         // 처리 로직
 *         context.resultBytes = processedBytes
 *         return context
 *     }
 * }
 * ```
 */
internal interface ExcelProcessor {
    /**
     * 프로세서 이름 (로깅 및 디버깅용).
     */
    val name: String

    /**
     * 이 프로세서를 실행할지 여부를 결정한다.
     *
     * 조건에 맞지 않으면 프로세서를 건너뜁니다.
     * 기본적으로 true를 반환하여 항상 실행된다.
     *
     * @param context 현재 처리 컨텍스트
     * @return 실행 여부
     */
    fun shouldProcess(context: ProcessingContext): Boolean = true

    /**
     * 처리를 수행한다.
     *
     * 컨텍스트를 수정하고 반환한다.
     * 결과 바이트 배열은 context.resultBytes에 저장해야 한다.
     *
     * @param context 현재 처리 컨텍스트
     * @return 처리된 컨텍스트 (보통 입력과 동일한 객체)
     */
    fun process(context: ProcessingContext): ProcessingContext
}
