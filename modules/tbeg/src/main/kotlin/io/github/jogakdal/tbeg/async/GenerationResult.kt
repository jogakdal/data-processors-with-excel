package io.github.jogakdal.tbeg.async

import java.nio.file.Path
import java.time.Instant

/**
 * Excel 생성 작업의 결과.
 *
 * @property jobId 작업 고유 ID
 * @property filePath 생성된 파일 경로 (파일로 저장한 경우)
 * @property bytes 생성된 Excel 바이트 배열 (메모리에 저장한 경우)
 * @property rowsProcessed 처리된 총 행 수
 * @property durationMs 작업 소요 시간 (밀리초)
 * @property completedAt 작업 완료 시각
 */
data class GenerationResult(
    val jobId: String,
    val filePath: Path? = null,
    val bytes: ByteArray? = null,
    val rowsProcessed: Int = 0,
    val durationMs: Long = 0,
    val completedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GenerationResult

        if (jobId != other.jobId) return false
        if (filePath != other.filePath) return false
        if (bytes != null) {
            if (other.bytes == null) return false
            if (!bytes.contentEquals(other.bytes)) return false
        } else if (other.bytes != null) return false
        if (rowsProcessed != other.rowsProcessed) return false
        if (durationMs != other.durationMs) return false
        if (!completedAt.equals(other.completedAt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = jobId.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        result = 31 * result + rowsProcessed
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + completedAt.hashCode()
        return result
    }
}
