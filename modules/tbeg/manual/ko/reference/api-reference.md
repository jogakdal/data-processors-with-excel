# TBEG API 레퍼런스

## 목차
1. [ExcelGenerator](#1-excelgenerator)
2. [ExcelDataProvider](#2-exceldataprovider)
3. [SimpleDataProvider](#3-simpledataprovider)
4. [DocumentMetadata](#4-documentmetadata)
5. [비동기 API](#5-비동기-api)

---

## 1. ExcelGenerator

템플릿 기반 Excel 생성기의 메인 클래스입니다.

### 패키지
```kotlin
io.github.jogakdal.tbeg.ExcelGenerator
```

### 생성자

```kotlin
class ExcelGenerator(config: TbegConfig = TbegConfig())
```

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| config | TbegConfig | TbegConfig() | 생성기 설정 |

### 동기 메서드

#### generate (Map)

```kotlin
fun generate(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null
): ByteArray
```

템플릿과 데이터 맵으로 Excel을 생성합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| template | InputStream | 템플릿 입력 스트림 |
| data | Map<String, Any> | 바인딩할 데이터 맵 |
| password | String? | 파일 열기 암호 (선택) |
| **반환** | ByteArray | 생성된 Excel 파일 바이트 배열 |

#### generate (DataProvider - InputStream)

```kotlin
fun generate(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null
): ByteArray
```

템플릿 InputStream과 DataProvider로 Excel을 생성합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| template | InputStream | 템플릿 입력 스트림 |
| dataProvider | ExcelDataProvider | 데이터 제공자 |
| password | String? | 파일 열기 암호 (선택) |
| **반환** | ByteArray | 생성된 Excel 파일 바이트 배열 |

#### generate (DataProvider - File)

```kotlin
fun generate(
    template: File,
    dataProvider: ExcelDataProvider,
    password: String? = null
): ByteArray
```

템플릿 파일과 DataProvider로 Excel을 생성합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| template | File | 템플릿 파일 |
| dataProvider | ExcelDataProvider | 데이터 제공자 |
| password | String? | 파일 열기 암호 (선택) |
| **반환** | ByteArray | 생성된 Excel 파일 바이트 배열 |

#### generateToFile (DataProvider)

```kotlin
fun generateToFile(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): Path
```

Excel을 생성하여 파일로 저장합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| template | InputStream | 템플릿 입력 스트림 |
| dataProvider | ExcelDataProvider | 데이터 제공자 |
| outputDir | Path | 출력 디렉토리 경로 |
| baseFileName | String | 기본 파일명 (확장자 제외) |
| password | String? | 파일 열기 암호 (선택) |
| **반환** | Path | 생성된 파일의 경로 |

#### generateToFile (Map)

```kotlin
fun generateToFile(
    template: InputStream,
    data: Map<String, Any>,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): Path
```

데이터 맵으로 Excel을 생성하여 파일로 저장합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| template | InputStream | 템플릿 입력 스트림 |
| data | Map<String, Any> | 바인딩할 데이터 맵 |
| outputDir | Path | 출력 디렉토리 경로 |
| baseFileName | String | 기본 파일명 (확장자 제외) |
| password | String? | 파일 열기 암호 (선택) |
| **반환** | Path | 생성된 파일의 경로 |

### 비동기 메서드

#### generateAsync (Coroutines - DataProvider)

```kotlin
suspend fun generateAsync(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null
): ByteArray
```

#### generateAsync (Coroutines - Map)

```kotlin
suspend fun generateAsync(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null
): ByteArray
```

#### generateToFileAsync (Coroutines - DataProvider)

```kotlin
suspend fun generateToFileAsync(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): Path
```

#### generateToFileAsync (Coroutines - Map)

```kotlin
suspend fun generateToFileAsync(
    template: InputStream,
    data: Map<String, Any>,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): Path
```

#### generateFuture (CompletableFuture - DataProvider)

```kotlin
fun generateFuture(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null
): CompletableFuture<ByteArray>
```

#### generateFuture (CompletableFuture - Map)

```kotlin
fun generateFuture(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null
): CompletableFuture<ByteArray>
```

#### generateToFileFuture (CompletableFuture - DataProvider)

```kotlin
fun generateToFileFuture(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): CompletableFuture<Path>
```

#### generateToFileFuture (CompletableFuture - Map)

```kotlin
fun generateToFileFuture(
    template: InputStream,
    data: Map<String, Any>,
    outputDir: Path,
    baseFileName: String,
    password: String? = null
): CompletableFuture<Path>
```

#### submit (백그라운드 - DataProvider)

```kotlin
fun submit(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

#### submit (백그라운드 - Map)

```kotlin
fun submit(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

#### submitToFile (백그라운드 - DataProvider)

```kotlin
fun submitToFile(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    outputDir: Path,
    baseFileName: String,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

#### submitToFile (백그라운드 - Map)

```kotlin
fun submitToFile(
    template: InputStream,
    data: Map<String, Any>,
    outputDir: Path,
    baseFileName: String,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

### 리소스 관리

`ExcelGenerator`는 `Closeable`을 구현하므로 사용 후 반드시 닫아야 합니다.

```kotlin
ExcelGenerator().use { generator ->
    // 사용
}
```

---

## 2. ExcelDataProvider

Excel 템플릿에 바인딩할 데이터를 제공하는 인터페이스입니다.

### 패키지
```kotlin
io.github.jogakdal.tbeg.ExcelDataProvider
```

### 메서드

#### getValue

```kotlin
fun getValue(name: String): Any?
```

단일 변수 값을 반환합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| name | String | 변수 이름 |
| **반환** | Any? | 변수 값, 없으면 null |

#### getItems

```kotlin
fun getItems(name: String): Iterator<Any>?
```

컬렉션 데이터의 Iterator를 반환합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| name | String | 컬렉션 이름 |
| **반환** | Iterator<Any>? | 컬렉션의 Iterator, 없으면 null |

#### getImage

```kotlin
fun getImage(name: String): ByteArray? = null
```

이미지 데이터를 반환합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| name | String | 이미지 이름 |
| **반환** | ByteArray? | 이미지 바이트 배열, 없으면 null |

#### getMetadata

```kotlin
fun getMetadata(): DocumentMetadata? = null
```

문서 메타데이터를 반환합니다.

| **반환** | DocumentMetadata? | 문서 메타데이터, 없으면 null |

#### getItemCount

```kotlin
fun getItemCount(name: String): Int? = null
```

컬렉션의 아이템 수를 반환합니다.

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| name | String | 컬렉션 이름 |
| **반환** | Int? | 아이템 수, 알 수 없으면 null |

> **성능 권장**: 대용량 데이터 처리 시 이 메서드를 구현하면 최적의 성능을 얻을 수 있습니다.

---

## 3. SimpleDataProvider

ExcelDataProvider의 기본 구현체입니다.

### 패키지
```kotlin
io.github.jogakdal.tbeg.SimpleDataProvider
```

### 생성 방법

#### of (Map)

```kotlin
companion object {
    fun of(data: Map<String, Any>): SimpleDataProvider
}
```

Map으로부터 생성합니다. List/Collection은 자동으로 컬렉션으로, ByteArray는 이미지로 분류됩니다.

```kotlin
val provider = SimpleDataProvider.of(mapOf(
    "title" to "보고서",
    "employees" to listOf(emp1, emp2),  // 컬렉션으로 분류
    "logo" to logoBytes                  // 이미지로 분류
))
```

#### builder

```kotlin
companion object {
    fun builder(): Builder
}
```

Builder를 반환합니다.

#### simpleDataProvider (DSL)

```kotlin
fun simpleDataProvider(block: SimpleDataProvider.Builder.() -> Unit): SimpleDataProvider
```

DSL 방식으로 생성합니다.

```kotlin
val provider = simpleDataProvider {
    value("title", "보고서")
    items("employees") { repository.findAll().iterator() }
}
```

### Builder 메서드

#### value

```kotlin
fun value(name: String, value: Any): Builder
```

단일 값을 추가합니다.

#### items (즉시 로딩)

```kotlin
fun items(name: String, items: List<Any>): Builder
fun items(name: String, items: Iterable<Any>): Builder
```

컬렉션을 추가합니다. List/Collection인 경우 count가 자동 설정됩니다.

#### items (지연 로딩)

```kotlin
fun items(name: String, itemsSupplier: () -> Iterator<Any>): Builder
```

지연 로딩 컬렉션을 추가합니다.

#### items (지연 로딩 + count)

```kotlin
fun items(name: String, count: Int, itemsSupplier: () -> Iterator<Any>): Builder
```

count와 함께 지연 로딩 컬렉션을 추가합니다.

```kotlin
items("employees", employeeCount) {
    employeeRepository.streamAll().iterator()
}
```

#### itemsFromSupplier (Java)

```kotlin
fun itemsFromSupplier(name: String, itemsSupplier: Supplier<Iterator<Any>>): Builder
fun itemsFromSupplier(name: String, count: Int, itemsSupplier: Supplier<Iterator<Any>>): Builder
```

Java Supplier를 사용한 지연 로딩입니다.

#### image (즉시 로딩)

```kotlin
fun image(name: String, imageData: ByteArray): Builder
```

이미지를 추가합니다. ByteArray가 즉시 메모리에 로드됩니다.

#### image (지연 로딩)

```kotlin
fun image(name: String, imageSupplier: () -> ByteArray): Builder
```

지연 로딩 이미지를 추가합니다. 이미지 데이터가 실제로 필요할 때 Lambda가 호출됩니다.

```kotlin
image("signature") {
    downloadSignatureImage(userId)
}
```

#### imageFromSupplier (Java)

```kotlin
fun imageFromSupplier(name: String, imageSupplier: Supplier<ByteArray>): Builder
```

Java Supplier를 사용한 지연 로딩 이미지입니다.

```java
.imageFromSupplier("signature", () -> downloadSignature())
```

#### metadata

```kotlin
fun metadata(block: DocumentMetadataBuilder.() -> Unit): Builder  // Kotlin DSL
fun metadata(configurer: Consumer<DocumentMetadata.Builder>): Builder  // Java
fun metadata(metadata: DocumentMetadata): Builder  // 직접 설정
```

문서 메타데이터를 설정합니다.

---

## 4. DocumentMetadata

Excel 문서의 메타데이터(속성)를 나타냅니다.

### 패키지
```kotlin
io.github.jogakdal.tbeg.DocumentMetadata
```

### 속성

| 속성 | 타입 | 설명 | Excel 위치 |
|------|------|------|-----------|
| title | String? | 문서 제목 | 파일 > 정보 > 제목 |
| author | String? | 작성자 | 파일 > 정보 > 작성자 |
| subject | String? | 주제 | 파일 > 정보 > 주제 |
| keywords | List<String>? | 키워드 | 파일 > 정보 > 태그 |
| description | String? | 설명 | 파일 > 정보 > 메모 |
| category | String? | 범주 | 파일 > 정보 > 범주 |
| company | String? | 회사 | 파일 > 정보 > 회사 |
| manager | String? | 관리자 | 파일 > 정보 > 관리자 |
| created | LocalDateTime? | 작성 일시 | 파일 > 정보 > 만든 날짜 |

### 사용 예시 (DSL)

```kotlin
val provider = simpleDataProvider {
    value("title", "보고서")
    metadata {
        title = "2026년 1월 월간 보고서"
        author = "황용호"
        company = "(주)휴넷"
        keywords("월간", "보고서", "2026년")
        created = LocalDateTime.now()
    }
}
```

### 사용 예시 (Builder)

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "보고서")
    .metadata(meta -> meta
        .title("2026년 1월 월간 보고서")
        .author("황용호")
        .company("(주)휴넷")
        .keywords("월간", "보고서", "2026년")
        .created(LocalDateTime.now()))
    .build();
```

---

## 5. 비동기 API

### GenerationJob

비동기 작업의 핸들입니다.

```kotlin
interface GenerationJob {
    val jobId: String
    val isCompleted: Boolean
    val isCancelled: Boolean
    fun cancel(): Boolean
    suspend fun await(): GenerationResult
    suspend fun awaitAsync(): GenerationResult
    fun toCompletableFuture(): CompletableFuture<GenerationResult>
}
```

| 속성/메서드 | 타입 | 설명 |
|------------|------|------|
| jobId | String | 작업 고유 ID |
| isCompleted | Boolean | 작업 완료 여부 |
| isCancelled | Boolean | 작업 취소 여부 |
| cancel() | Boolean | 작업 취소 시도, 성공 여부 반환 |
| await() | GenerationResult | (suspend) 작업 완료 대기 |
| awaitAsync() | GenerationResult | (suspend) await()의 별칭 |
| toCompletableFuture() | CompletableFuture | Java용 Future 변환 |

### ExcelGenerationListener

작업 진행 상태를 수신하는 리스너입니다.

```kotlin
interface ExcelGenerationListener {
    fun onStarted(jobId: String) {}
    fun onProgress(jobId: String, progress: ProgressInfo) {}
    fun onCompleted(jobId: String, result: GenerationResult)
    fun onFailed(jobId: String, error: Exception)
    fun onCancelled(jobId: String) {}
}
```

### GenerationResult

작업 완료 결과입니다.

```kotlin
data class GenerationResult(
    val jobId: String,
    val filePath: Path? = null,      // submitToFile인 경우
    val bytes: ByteArray? = null,    // submit인 경우
    val rowsProcessed: Int = 0,
    val durationMs: Long = 0,
    val completedAt: Instant = Instant.now()
)
```

| 속성 | 타입 | 설명 |
|-----|------|------|
| jobId | String | 작업 고유 ID |
| filePath | Path? | 생성된 파일 경로 (submitToFile인 경우) |
| bytes | ByteArray? | 생성된 파일 바이트 (submit인 경우) |
| rowsProcessed | Int | 처리된 행 수 |
| durationMs | Long | 처리 소요 시간 (밀리초) |
| completedAt | Instant | 작업 완료 시각 |

### ProgressInfo

진행률 정보입니다.

```kotlin
data class ProgressInfo(
    val processedRows: Int,
    val totalRows: Int? = null
)
```

| 속성 | 타입 | 설명 |
|-----|------|------|
| processedRows | Int | 현재 처리된 행 수 |
| totalRows | Int? | 전체 행 수 (count 미제공 시 null)

---

## 다음 단계

- [설정 옵션](./configuration.md) - TbegConfig 옵션
- [기본 예제](../examples/basic-examples.md) - 다양한 사용 예제
