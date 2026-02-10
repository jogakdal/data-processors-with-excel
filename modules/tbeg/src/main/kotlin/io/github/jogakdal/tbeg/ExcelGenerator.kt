package io.github.jogakdal.tbeg

import io.github.jogakdal.tbeg.async.DefaultGenerationJob
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationJob
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.engine.core.ChartProcessor
import io.github.jogakdal.tbeg.engine.core.PivotTableProcessor
import io.github.jogakdal.tbeg.engine.core.XmlVariableProcessor
import io.github.jogakdal.tbeg.engine.core.encryptExcel
import io.github.jogakdal.tbeg.engine.core.encryptExcelTo
import io.github.jogakdal.tbeg.engine.pipeline.TbegPipeline
import io.github.jogakdal.tbeg.engine.pipeline.ProcessingContext
import io.github.jogakdal.tbeg.engine.pipeline.processors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors

/**
 * 템플릿 기반 Excel 생성기.
 *
 * .xlsx 템플릿에 데이터를 바인딩하고 Excel 파일을 생성한다.
 *
 * ## 기본 사용법
 * ```kotlin
 * val generator = ExcelGenerator()
 * val data = mapOf("title" to "보고서", "items" to listOf(item1, item2))
 * val bytes = generator.generate(templateStream, data)
 * ```
 *
 * ## DataProvider 사용 (지연 로딩)
 * ```kotlin
 * val provider = simpleDataProvider {
 *     value("title", "보고서")
 *     items("employees") { repository.streamAll().iterator() }
 * }
 * val result = generator.generateToFile(template, provider, outputDir, "report")
 * ```
 *
 * ## 비동기 실행 (API 서버용)
 * ```kotlin
 * val job = generator.submitToFile(template, provider, outputDir, "report",
 *     listener = object : ExcelGenerationListener {
 *         override fun onCompleted(jobId: String, result: GenerationResult) {
 *             eventPublisher.publish(ReportReadyEvent(jobId, result.filePath))
 *         }
 *     }
 * )
 * return ResponseEntity.accepted().body(job.jobId)
 * ```
 *
 * @param config 생성기 설정
 */
class ExcelGenerator @JvmOverloads constructor(
    private val config: TbegConfig = TbegConfig()
) : Closeable {
    private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // 프로세서들
    private val pivotTableProcessor = PivotTableProcessor(config)
    private val xmlVariableProcessor = XmlVariableProcessor()
    private val chartProcessor = ChartProcessor()

    private val pipeline = TbegPipeline(
        ChartExtractProcessor(chartProcessor),
        PivotExtractProcessor(pivotTableProcessor),
        TemplateRenderProcessor(),
        NumberFormatProcessor(),
        XmlVariableReplaceProcessor(xmlVariableProcessor),
        PivotRecreateProcessor(pivotTableProcessor),
        ChartRestoreProcessor(chartProcessor),
        MetadataProcessor()
    )

    // ========== 동기 API ==========

    /**
     * 템플릿과 데이터 맵으로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열
     */
    @JvmOverloads
    fun generate(template: InputStream, data: Map<String, Any>, password: String? = null) =
        generate(template, SimpleDataProvider.of(data), password)

    /**
     * 템플릿과 DataProvider로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열
     */
    @JvmOverloads
    fun generate(template: InputStream, dataProvider: ExcelDataProvider, password: String? = null): ByteArray =
        ByteArrayOutputStream().use { output ->
            processTemplate(template, dataProvider, output)
            output.toByteArray()
        }.let { bytes ->
            password.takeUnless { it.isNullOrBlank() }?.let { bytes.encryptExcel(it) } ?: bytes
        }

    /**
     * 템플릿 파일과 DataProvider로 Excel을 생성한다.
     *
     * @param template 템플릿 파일
     * @param dataProvider 데이터 제공자
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열
     */
    @JvmOverloads
    fun generate(template: File, dataProvider: ExcelDataProvider, password: String? = null): ByteArray =
        template.inputStream().use { generate(it, dataProvider, password) }

    /**
     * Excel을 생성하여 파일로 저장한다.
     *
     * 파일명에 타임스탬프가 자동으로 추가된다.
     * 예: "report" -> "report_20240106_143052.xlsx"
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로
     */
    @JvmOverloads
    fun generateToFile(
        template: InputStream,
        data: Map<String, Any>,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ): Path = generateToFile(template, SimpleDataProvider.of(data), outputDir, baseFileName, password)

    /**
     * Excel을 생성하여 파일로 저장한다.
     *
     * 파일명에 타임스탬프가 자동으로 추가된다.
     * 예: "report" -> "report_20240106_143052.xlsx"
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로
     */
    @JvmOverloads
    fun generateToFile(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ): Path = generateToFileInternal(template, dataProvider, outputDir, baseFileName, password).first

    /**
     * 파일 생성 후 경로와 처리된 행 수를 함께 반환한다.
     * 예외 발생 시 생성된 파일을 삭제한다.
     */
    private fun generateToFileInternal(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ): Pair<Path, Int> {
        Files.createDirectories(outputDir)
        val outputPath = resolveOutputPath(outputDir, baseFileName)

        return runCatching {
            val effectivePassword = password.takeUnless { it.isNullOrBlank() }
            val rowsProcessed = effectivePassword?.let { pw ->
                ByteArrayOutputStream().use { buffer ->
                    processTemplate(template, dataProvider, buffer).also {
                        Files.newOutputStream(outputPath).use { out ->
                            buffer.toByteArray().encryptExcelTo(pw, out)
                        }
                    }
                }
            } ?: Files.newOutputStream(outputPath).use { output ->
                processTemplate(template, dataProvider, output)
            }
            outputPath to rowsProcessed
        }.onFailure {
            runCatching { Files.deleteIfExists(outputPath) }
                .onFailure { deleteError -> it.addSuppressed(deleteError) }
        }.getOrThrow()
    }

    // ========== 비동기 API (Kotlin Coroutines) ==========

    /**
     * 비동기로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열
     */
    suspend fun generateAsync(
        template: InputStream,
        data: Map<String, Any>,
        password: String? = null
    ): ByteArray = generateAsync(template, SimpleDataProvider.of(data), password)

    /**
     * 비동기로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열
     */
    suspend fun generateAsync(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        password: String? = null
    ): ByteArray = withContext(dispatcher) { generate(template, dataProvider, password) }

    /**
     * 비동기로 Excel을 생성하여 파일로 저장한다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로
     */
    suspend fun generateToFileAsync(
        template: InputStream,
        data: Map<String, Any>,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ): Path = generateToFileAsync(template, SimpleDataProvider.of(data), outputDir, baseFileName, password)

    /**
     * 비동기로 Excel을 생성하여 파일로 저장한다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로
     */
    suspend fun generateToFileAsync(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ): Path = withContext(dispatcher) {
        generateToFile(template, dataProvider, outputDir, baseFileName, password)
    }

    // ========== 비동기 API (Java CompletableFuture) ==========

    /**
     * CompletableFuture로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열을 담은 CompletableFuture
     */
    @JvmOverloads
    fun generateFuture(template: InputStream, data: Map<String, Any>, password: String? = null) =
        generateFuture(template, SimpleDataProvider.of(data), password)

    /**
     * CompletableFuture로 Excel을 생성한다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 Excel 파일의 바이트 배열을 담은 CompletableFuture
     */
    @JvmOverloads
    fun generateFuture(template: InputStream, dataProvider: ExcelDataProvider, password: String? = null) =
        scope.future { generate(template, dataProvider, password) }

    /**
     * CompletableFuture로 Excel을 생성하여 파일로 저장한다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로를 담은 CompletableFuture
     */
    @JvmOverloads
    fun generateToFileFuture(
        template: InputStream,
        data: Map<String, Any>,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ) = generateToFileFuture(template, SimpleDataProvider.of(data), outputDir, baseFileName, password)

    /**
     * CompletableFuture로 Excel을 생성하여 파일로 저장한다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @return 생성된 파일의 경로를 담은 CompletableFuture
     */
    @JvmOverloads
    fun generateToFileFuture(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        outputDir: Path,
        baseFileName: String,
        password: String? = null
    ) = scope.future { generateToFile(template, dataProvider, outputDir, baseFileName, password) }

    // ========== 비동기 API (작업 관리) ==========

    /**
     * 비동기 작업을 제출하고 작업 핸들을 반환한다.
     * 완료 시 바이트 배열을 GenerationResult.bytes로 받는다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @param listener 작업 진행 상태를 받을 리스너 (선택)
     * @return 작업 핸들 (취소, 완료 대기 등에 사용)
     */
    @JvmOverloads
    fun submit(
        template: InputStream,
        data: Map<String, Any>,
        password: String? = null,
        listener: ExcelGenerationListener? = null
    ): GenerationJob = submit(template, SimpleDataProvider.of(data), password, listener)

    /**
     * 비동기 작업을 제출하고 작업 핸들을 반환한다.
     * 완료 시 바이트 배열을 GenerationResult.bytes로 받는다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @param listener 작업 진행 상태를 받을 리스너 (선택)
     * @return 작업 핸들 (취소, 완료 대기 등에 사용)
     */
    @JvmOverloads
    fun submit(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        password: String? = null,
        listener: ExcelGenerationListener? = null
    ): GenerationJob {
        val jobId = UUID.randomUUID().toString()
        val job = DefaultGenerationJob(jobId)
        val startTime = System.currentTimeMillis()

        listener?.onStarted(jobId)

        scope.launch {
            runCatching {
                if (job.checkCancelled()) {
                    listener?.onCancelled(jobId)
                    return@launch
                }

                val bytes = generate(template, dataProvider, password)

                GenerationResult(
                    jobId = jobId,
                    bytes = bytes,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }.onSuccess { result ->
                job.complete(result)
                listener?.onCompleted(jobId, result)
            }.onFailure { error ->
                job.completeExceptionally(error as Exception)
                listener?.onFailed(jobId, error)
            }
        }

        return job
    }

    /**
     * 비동기 작업을 제출하고 파일로 저장한다.
     * 완료 시 파일 경로를 GenerationResult.filePath로 받는다.
     * 블로킹을 피해야 하는 경우 즉시 응답 후 백그라운드 처리에 적합하다.
     *
     * @param template 템플릿 입력 스트림
     * @param data 바인딩할 데이터 맵
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @param listener 작업 진행 상태를 받을 리스너 (선택)
     * @return 작업 핸들 (취소, 완료 대기 등에 사용)
     */
    @JvmOverloads
    fun submitToFile(
        template: InputStream,
        data: Map<String, Any>,
        outputDir: Path,
        baseFileName: String,
        password: String? = null,
        listener: ExcelGenerationListener? = null
    ): GenerationJob = submitToFile(template, SimpleDataProvider.of(data), outputDir, baseFileName, password, listener)

    /**
     * 비동기 작업을 제출하고 파일로 저장한다.
     * 완료 시 파일 경로를 GenerationResult.filePath로 받는다.
     * 블로킹을 피해야 하는 경우 즉시 응답 후 백그라운드 처리에 적합하다.
     *
     * @param template 템플릿 입력 스트림
     * @param dataProvider 데이터 제공자
     * @param outputDir 출력 디렉토리 경로
     * @param baseFileName 기본 파일명 (확장자 제외)
     * @param password 파일 열기 암호 (null 또는 빈 문자열이면 암호 없음)
     * @param listener 작업 진행 상태를 받을 리스너 (선택)
     * @return 작업 핸들 (취소, 완료 대기 등에 사용)
     */
    @JvmOverloads
    fun submitToFile(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        outputDir: Path,
        baseFileName: String,
        password: String? = null,
        listener: ExcelGenerationListener? = null
    ): GenerationJob {
        val jobId = UUID.randomUUID().toString()
        val job = DefaultGenerationJob(jobId)
        val startTime = System.currentTimeMillis()

        listener?.onStarted(jobId)

        scope.launch {
            runCatching {
                if (job.checkCancelled()) {
                    listener?.onCancelled(jobId)
                    return@launch
                }

                val (filePath, rowsProcessed) = generateToFileInternal(
                    template, dataProvider, outputDir, baseFileName, password
                )

                GenerationResult(
                    jobId = jobId,
                    filePath = filePath,
                    rowsProcessed = rowsProcessed,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }.onSuccess { result ->
                job.complete(result)
                listener?.onCompleted(jobId, result)
            }.onFailure { error ->
                job.completeExceptionally(error as Exception)
                listener?.onFailed(jobId, error)
            }
        }

        return job
    }

    // ========== 핵심 처리 로직 ==========

    /**
     * 템플릿 처리 (Pipeline Pattern)
     *
     * 파이프라인 처리 흐름:
     * - ChartExtractProcessor: 스트리밍 모드에서 차트 추출 (SXSSF에서 차트 손실 방지)
     * - PivotExtractProcessor: 피벗 테이블 정보 추출 및 템플릿에서 제거
     * - TemplateRenderProcessor: 반복 데이터 처리 (스트리밍 가능)
     * - NumberFormatProcessor: 숫자 서식 자동 적용
     * - XmlVariableReplaceProcessor: XML 변수 치환 (수식 내 변수 등)
     * - PivotRecreateProcessor: 확장된 데이터 소스로 피벗 테이블 재생성
     * - ChartRestoreProcessor: 차트 복원 (스트리밍 모드)
     * - MetadataProcessor: 문서 메타데이터 적용
     */
    private fun processTemplate(
        template: InputStream,
        dataProvider: ExcelDataProvider,
        output: OutputStream
    ): Int {
        // ProcessingContext 생성
        val context = ProcessingContext(
            templateBytes = template.readBytes(),
            dataProvider = dataProvider,
            config = config,
            metadata = dataProvider.getMetadata()
        )

        // 파이프라인 실행
        val result = pipeline.execute(context)

        // 결과 출력
        output.write(result.resultBytes)
        return result.processedRowCount
    }

    // ========== 유틸리티 ==========

    /**
     * 출력 파일 경로를 결정한다.
     * fileNamingMode에 따라 파일명을 생성하고, fileConflictPolicy에 따라 충돌을 처리한다.
     */
    private fun resolveOutputPath(outputDir: Path, baseFileName: String): Path {
        val basePath = outputDir.resolve(generateFileName(baseFileName))

        return basePath.takeUnless { Files.exists(it) }
            ?: when (config.fileConflictPolicy) {
                FileConflictPolicy.ERROR -> throw FileAlreadyExistsException(
                    basePath.toFile(), null, "파일이 이미 존재합니다: $basePath"
                )
                FileConflictPolicy.SEQUENCE -> findAvailablePathWithSequence(outputDir, baseFileName)
            }
    }

    /**
     * 시퀀스 번호를 붙여 사용 가능한 파일 경로를 찾는다.
     */
    private fun findAvailablePathWithSequence(outputDir: Path, baseFileName: String): Path {
        val baseNameWithSuffix = when (config.fileNamingMode) {
            FileNamingMode.NONE -> baseFileName
            FileNamingMode.TIMESTAMP -> "${baseFileName}_${currentTimestamp()}"
        }

        return generateSequence(1) { it + 1 }
            .take(10000)
            .map { outputDir.resolve("${baseNameWithSuffix}_$it.xlsx") }
            .firstOrNull { !Files.exists(it) }
            ?: throw IllegalStateException("시퀀스 번호가 한계(10000)를 초과했습니다: $baseNameWithSuffix")
    }

    private fun generateFileName(baseFileName: String) = when (config.fileNamingMode) {
        FileNamingMode.NONE -> "$baseFileName.xlsx"
        FileNamingMode.TIMESTAMP -> "${baseFileName}_${currentTimestamp()}.xlsx"
    }

    private fun currentTimestamp() =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern(config.timestampFormat))

    override fun close() {
        scope.cancel()
        dispatcher.close()
    }
}
