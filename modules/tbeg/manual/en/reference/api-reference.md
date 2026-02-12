> **[한국어](../../ko/reference/api-reference.md)** | English

# TBEG API Reference

## Table of Contents
1. [ExcelGenerator](#1-excelgenerator)
2. [ExcelDataProvider](#2-exceldataprovider)
3. [SimpleDataProvider](#3-simpledataprovider)
4. [DocumentMetadata](#4-documentmetadata)
5. [Async API](#5-async-api)

---

## 1. ExcelGenerator

The main class for template-based Excel generation.

### Package
```kotlin
io.github.jogakdal.tbeg.ExcelGenerator
```

### Constructor

```kotlin
class ExcelGenerator(config: TbegConfig = TbegConfig())
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| config | TbegConfig | TbegConfig() | Generator configuration |

### Method Selection Guide

| Method | Return Type | Use Case |
|--------|-------------|----------|
| `generate()` | `ByteArray` | Receive results in memory for direct processing (HTTP response, post-processing, etc.) |
| `generateToFile()` | `Path` | Save results directly to file (recommended for large-scale processing) |
| `generateAsync()` | `ByteArray` (suspend) | Async processing in Kotlin Coroutine environments |
| `generateToFileAsync()` | `Path` (suspend) | Async file saving in Kotlin Coroutine environments |
| `generateFuture()` | `CompletableFuture<ByteArray>` | Async processing in Java |
| `generateToFileFuture()` | `CompletableFuture<Path>` | Async file saving in Java |
| `submit()` / `submitToFile()` | `GenerationJob` | Background processing + progress listener (respond immediately from API server, process later) |

### Synchronous Methods

#### generate (Map)

```kotlin
fun generate(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null
): ByteArray
```

Generates an Excel file from a template and a data map.

| Parameter | Type | Description |
|-----------|------|-------------|
| template | InputStream | Template input stream |
| data | Map<String, Any> | Data map for binding |
| password | String? | File open password (optional) |
| **Returns** | ByteArray | Generated Excel file as a byte array |

#### generate (DataProvider - InputStream)

```kotlin
fun generate(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null
): ByteArray
```

Generates an Excel file from a template InputStream and a DataProvider.

| Parameter | Type | Description |
|-----------|------|-------------|
| template | InputStream | Template input stream |
| dataProvider | ExcelDataProvider | Data provider |
| password | String? | File open password (optional) |
| **Returns** | ByteArray | Generated Excel file as a byte array |

#### generate (DataProvider - File)

```kotlin
fun generate(
    template: File,
    dataProvider: ExcelDataProvider,
    password: String? = null
): ByteArray
```

Generates an Excel file from a template File and a DataProvider.

| Parameter | Type | Description |
|-----------|------|-------------|
| template | File | Template file |
| dataProvider | ExcelDataProvider | Data provider |
| password | String? | File open password (optional) |
| **Returns** | ByteArray | Generated Excel file as a byte array |

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

Generates an Excel file and saves it to disk.

| Parameter | Type | Description |
|-----------|------|-------------|
| template | InputStream | Template input stream |
| dataProvider | ExcelDataProvider | Data provider |
| outputDir | Path | Output directory path |
| baseFileName | String | Base filename (without extension) |
| password | String? | File open password (optional) |
| **Returns** | Path | Path to the generated file |

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

Generates an Excel file from a data map and saves it to disk.

| Parameter | Type | Description |
|-----------|------|-------------|
| template | InputStream | Template input stream |
| data | Map<String, Any> | Data map for binding |
| outputDir | Path | Output directory path |
| baseFileName | String | Base filename (without extension) |
| password | String? | File open password (optional) |
| **Returns** | Path | Path to the generated file |

### Async Methods

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

#### submit (Background - DataProvider)

```kotlin
fun submit(
    template: InputStream,
    dataProvider: ExcelDataProvider,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

#### submit (Background - Map)

```kotlin
fun submit(
    template: InputStream,
    data: Map<String, Any>,
    password: String? = null,
    listener: ExcelGenerationListener? = null
): GenerationJob
```

#### submitToFile (Background - DataProvider)

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

#### submitToFile (Background - Map)

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

### Possible Exceptions

| Exception | When Thrown | Cause |
|-----------|------------|-------|
| `TemplateProcessingException` | Template parsing | Marker syntax error (classified into 5 ErrorTypes) |
| `MissingTemplateDataException` | Data binding | Missing variable/collection/image (only in `THROW` mode) |
| `FormulaExpansionException` | Formula adjustment | Formula expansion failure (merged cells + function argument count exceeded) |

`TemplateProcessingException` ErrorTypes:

| ErrorType | Description |
|-----------|-------------|
| `INVALID_REPEAT_SYNTAX` | Repeat marker syntax error |
| `MISSING_REQUIRED_PARAMETER` | Required parameter missing |
| `INVALID_RANGE_FORMAT` | Invalid cell range format |
| `SHEET_NOT_FOUND` | Reference to non-existent sheet |
| `INVALID_PARAMETER_VALUE` | Invalid parameter value |

For detailed error handling, see the [Troubleshooting Guide](../troubleshooting.md#2-runtime-errors).

### Resource Management and Thread Safety

`ExcelGenerator` implements `Closeable`, so it must be closed after use.

```kotlin
ExcelGenerator().use { generator ->
    // use the generator
}
```

Internally, it holds a `CoroutineScope` backed by a `CachedThreadPool`, which is cleaned up when `close()` is called.

#### Thread Safety

| API | Concurrent Calls | Notes |
|-----|-------------------|-------|
| Synchronous `generate()` / `generateToFile()` | Not supported | Shares internal pipeline state; must not be called concurrently |
| Async `generateAsync()` / `generateFuture()` | Supported | Each task runs in isolation within the coroutine scope |
| Background `submit()` / `submitToFile()` | Supported | Each task is isolated in a separate coroutine |

In a Spring Boot environment, `ExcelGenerator` is registered as a singleton bean. If you need to call the synchronous API concurrently from multiple requests, either create a separate `ExcelGenerator` instance per request or use the async API.

---

## 2. ExcelDataProvider

An interface that provides data for binding to an Excel template.

### Package
```kotlin
io.github.jogakdal.tbeg.ExcelDataProvider
```

### Methods

#### getValue

```kotlin
fun getValue(name: String): Any?
```

Returns a single variable value.

| Parameter | Type | Description |
|-----------|------|-------------|
| name | String | Variable name |
| **Returns** | Any? | Variable value, or null if not found |

#### getItems

```kotlin
fun getItems(name: String): Iterator<Any>?
```

Returns an Iterator for collection data.

| Parameter | Type | Description |
|-----------|------|-------------|
| name | String | Collection name |
| **Returns** | Iterator<Any>? | Iterator for the collection, or null if not found |

#### getImage

```kotlin
fun getImage(name: String): ByteArray? = null
```

Returns image data.

| Parameter | Type | Description |
|-----------|------|-------------|
| name | String | Image name |
| **Returns** | ByteArray? | Image byte array, or null if not found |

#### getMetadata

```kotlin
fun getMetadata(): DocumentMetadata? = null
```

Returns document metadata.

| **Returns** | DocumentMetadata? | Document metadata, or null if not set |

#### getItemCount

```kotlin
fun getItemCount(name: String): Int? = null
```

Returns the number of items in a collection.

| Parameter | Type | Description |
|-----------|------|-------------|
| name | String | Collection name |
| **Returns** | Int? | Item count, or null if unknown |

> [!TIP]
> Implementing this method prevents double iteration of the data, enabling optimal performance when processing large datasets.

---

## 3. SimpleDataProvider

A default implementation of ExcelDataProvider.

### Package
```kotlin
io.github.jogakdal.tbeg.SimpleDataProvider
```

### Creation Methods

#### of (Map)

```kotlin
companion object {
    fun of(data: Map<String, Any>): SimpleDataProvider
}
```

Creates an instance from a Map. List/Collection values are automatically classified as collections, and ByteArray values as images.

```kotlin
val provider = SimpleDataProvider.of(mapOf(
    "title" to "보고서",
    "employees" to listOf(emp1, emp2),  // Classified as a collection
    "logo" to logoBytes                  // Classified as an image
))
```

#### builder

```kotlin
companion object {
    fun builder(): Builder
}
```

Returns a Builder.

#### simpleDataProvider (DSL)

```kotlin
fun simpleDataProvider(block: SimpleDataProvider.Builder.() -> Unit): SimpleDataProvider
```

Creates an instance using DSL syntax.

```kotlin
val provider = simpleDataProvider {
    value("title", "보고서")
    items("employees") { repository.findAll().iterator() }
}
```

### Builder Methods

#### value

```kotlin
fun value(name: String, value: Any): Builder
```

Adds a single value.

#### items (Eager Loading)

```kotlin
fun items(name: String, items: List<Any>): Builder
fun items(name: String, items: Iterable<Any>): Builder
```

Adds a collection. When the input is a List/Collection, the count is automatically set.

#### items (Lazy Loading)

```kotlin
fun items(name: String, itemsSupplier: () -> Iterator<Any>): Builder
```

Adds a lazily loaded collection.

#### items (Lazy Loading + count)

```kotlin
fun items(name: String, count: Int, itemsSupplier: () -> Iterator<Any>): Builder
```

Adds a lazily loaded collection with a known count.

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

Lazy loading using a Java Supplier.

#### image (Eager Loading)

```kotlin
fun image(name: String, imageData: ByteArray): Builder
```

Adds an image. The ByteArray is loaded into memory immediately.

#### image (Lazy Loading)

```kotlin
fun image(name: String, imageSupplier: () -> ByteArray): Builder
```

Adds a lazily loaded image. The lambda is invoked only when the image data is actually needed.

```kotlin
image("signature") {
    downloadSignatureImage(userId)
}
```

#### imageFromSupplier (Java)

```kotlin
fun imageFromSupplier(name: String, imageSupplier: Supplier<ByteArray>): Builder
```

Lazy loading image using a Java Supplier.

```java
.imageFromSupplier("signature", () -> downloadSignature())
```

#### metadata

```kotlin
fun metadata(block: DocumentMetadataBuilder.() -> Unit): Builder  // Kotlin DSL
fun metadata(configurer: Consumer<DocumentMetadata.Builder>): Builder  // Java
fun metadata(metadata: DocumentMetadata): Builder  // Direct
```

Sets the document metadata.

---

## 4. DocumentMetadata

Represents the metadata (properties) of an Excel document.

### Package
```kotlin
io.github.jogakdal.tbeg.DocumentMetadata
```

### Properties

| Property | Type | Description | Excel Location |
|----------|------|-------------|----------------|
| title | String? | Document title | File > Info > Title |
| author | String? | Author | File > Info > Author |
| subject | String? | Subject | File > Info > Subject |
| keywords | List<String>? | Keywords | File > Info > Tags |
| description | String? | Description | File > Info > Comments |
| category | String? | Category | File > Info > Category |
| company | String? | Company | File > Info > Company |
| manager | String? | Manager | File > Info > Manager |
| created | LocalDateTime? | Creation date/time | File > Info > Date Created |

### Usage Example (DSL)

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

### Usage Example (Builder)

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

## 5. Async API

### GenerationJob

A handle for an asynchronous task.

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

| Property/Method | Type | Description |
|-----------------|------|-------------|
| jobId | String | Unique job ID |
| isCompleted | Boolean | Whether the job has completed |
| isCancelled | Boolean | Whether the job has been cancelled |
| cancel() | Boolean | Attempts to cancel the job; returns success status |
| await() | GenerationResult | (suspend) Waits for job completion |
| awaitAsync() | GenerationResult | (suspend) Alias for await() |
| toCompletableFuture() | CompletableFuture | Converts to a Java CompletableFuture |

### ExcelGenerationListener

A listener that receives job progress updates.

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

The result of a completed job.

```kotlin
data class GenerationResult(
    val jobId: String,
    val filePath: Path? = null,      // For submitToFile
    val bytes: ByteArray? = null,    // For submit
    val rowsProcessed: Int = 0,
    val durationMs: Long = 0,
    val completedAt: Instant = Instant.now()
)
```

| Property | Type | Description |
|----------|------|-------------|
| jobId | String | Unique job ID |
| filePath | Path? | Path to the generated file (for submitToFile) |
| bytes | ByteArray? | Generated file bytes (for submit) |
| rowsProcessed | Int | Number of rows processed |
| durationMs | Long | Processing duration in milliseconds |
| completedAt | Instant | Time of job completion |

### ProgressInfo

Progress information.

```kotlin
data class ProgressInfo(
    val processedRows: Int,
    val totalRows: Int? = null
)
```

| Property | Type | Description |
|----------|------|-------------|
| processedRows | Int | Number of rows processed so far |
| totalRows | Int? | Total number of rows (null if count was not provided) |

---

## Next Steps

- [Configuration](./configuration.md) - TbegConfig options
- [Basic Examples](../examples/basic-examples.md) - Various usage examples
- [Troubleshooting](../troubleshooting.md) - Problem resolution
