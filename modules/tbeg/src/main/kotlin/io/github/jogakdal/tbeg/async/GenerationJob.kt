package io.github.jogakdal.tbeg.async

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Excel 생성 작업의 핸들.
 *
 * 비동기로 실행 중인 작업의 상태를 확인하고, 결과를 대기하거나, 취소할 수 있다.
 */
interface GenerationJob {
    /**
     * 작업 고유 ID
     */
    val jobId: String

    /**
     * 작업이 완료되었는지 여부 (성공/실패/취소 모두 포함)
     */
    val isCompleted: Boolean

    /**
     * 작업이 취소되었는지 여부
     */
    val isCancelled: Boolean

    /**
     * 작업을 취소한다.
     *
     * @return 취소 요청이 성공하면 true, 이미 완료되었거나 취소할 수 없으면 false
     */
    fun cancel(): Boolean

    /**
     * 작업 완료를 블로킹 방식으로 대기하고 결과를 반환한다.
     *
     * @return 생성 결과
     * @throws Exception 작업이 실패한 경우
     * @throws java.util.concurrent.CancellationException 작업이 취소된 경우
     */
    fun await(): GenerationResult

    /**
     * 작업 완료를 비동기 방식으로 대기하고 결과를 반환한다.
     *
     * @return 생성 결과
     * @throws Exception 작업이 실패한 경우
     * @throws java.util.concurrent.CancellationException 작업이 취소된 경우
     */
    suspend fun awaitAsync(): GenerationResult

    /**
     * Java에서 사용할 수 있는 CompletableFuture를 반환한다.
     *
     * @return 결과를 담은 CompletableFuture
     */
    fun toCompletableFuture(): CompletableFuture<GenerationResult>
}

/**
 * [GenerationJob]의 기본 구현체.
 */
internal class DefaultGenerationJob(
    override val jobId: String
) : GenerationJob {

    private val deferred = CompletableDeferred<GenerationResult>()
    private val cancelled = AtomicBoolean(false)

    override val isCompleted: Boolean
        get() = deferred.isCompleted

    override val isCancelled: Boolean
        get() = cancelled.get()

    override fun cancel() = cancelled.compareAndSet(false, true).also { success ->
        if (success) deferred.cancel()
    }

    override fun await(): GenerationResult =
        toCompletableFuture().get()

    override suspend fun awaitAsync(): GenerationResult =
        deferred.await()

    override fun toCompletableFuture(): CompletableFuture<GenerationResult> =
        deferred.asCompletableFuture()

    /**
     * 작업을 성공으로 완료한다. (내부용)
     */
    internal fun complete(result: GenerationResult) {
        deferred.complete(result)
    }

    /**
     * 작업을 실패로 완료한다. (내부용)
     */
    internal fun completeExceptionally(exception: Exception) {
        deferred.completeExceptionally(exception)
    }

    /**
     * 취소 여부를 확인한다. (내부용)
     */
    internal fun checkCancelled(): Boolean = cancelled.get()
}
