# TBEG 사용자 가이드

## 목차
1. [빠른 시작](#1-빠른-시작)
2. [핵심 개념](#2-핵심-개념)
3. [DataProvider 사용하기](#3-dataprovider-사용하기)
4. [비동기 처리](#4-비동기-처리)
5. [대용량 데이터 처리](#5-대용량-데이터-처리)

---

## 1. 빠른 시작

### 1.1 의존성 추가

#### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jogakdal:tbeg:1.1.1")
}
```

#### Gradle (Groovy DSL)

```groovy
// build.gradle

repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.jogakdal:tbeg:1.1.1'
}
```

#### Maven

```xml
<!-- pom.xml -->

<dependencies>
    <dependency>
        <groupId>io.github.jogakdal</groupId>
        <artifactId>tbeg</artifactId>
        <version>1.1.1</version>
    </dependency>
</dependencies>
```

### 1.2 첫 번째 Excel 생성

#### 템플릿 (template.xlsx)

|   | A      | B         |
|---|--------|-----------|
| 1 | 제목     | ${title}  |
| 2 | 작성일    | ${date}   |

#### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File
import java.time.LocalDate

fun main() {
    val data = mapOf(
        "title" to "월간 보고서",
        "date" to LocalDate.now().toString()
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

#### Java 코드

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("title", "월간 보고서");
        data.put("date", LocalDate.now().toString());

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {
            byte[] bytes = generator.generate(template, data);
            try (FileOutputStream output = new FileOutputStream("output.xlsx")) {
                output.write(bytes);
            }
        }
    }
}
```

---

## 2. 핵심 개념

### 2.1 템플릿 문법

TBEG은 Excel 템플릿에 특수 마커를 사용하여 데이터를 바인딩합니다.

| 문법                       | 설명        | 예시                                 |
|--------------------------|-----------|------------------------------------|
| `${변수명}`                 | 단순 변수 치환  | `${title}`                         |
| `${item.필드}`             | 객체의 필드 치환 | `${emp.name}`                      |
| `${repeat(컬렉션, 범위, 변수)}` | 반복 처리     | `${repeat(employees, A3:C3, emp)}` |
| `${image(이름)}`           | 이미지 삽입    | `${image(logo)}`                   |
| `${size(컬렉션)}`           | 컬렉션 크기    | `${size(employees)}명`              |

자세한 문법은 [템플릿 문법 레퍼런스](./reference/template-syntax.md)를 참조하세요.

### 2.2 반복 처리

리스트 데이터를 템플릿의 지정된 범위에 반복 출력합니다. 기본적으로 아래 방향(DOWN)으로 확장되며, 오른쪽 방향(RIGHT) 확장도 지원합니다. 자세한 문법은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#23-오른쪽-방향-반복-right)를, 코드 예제는 [고급 예제](./examples/advanced-examples.md#8-오른쪽-방향-반복)를 참조하세요.

#### 템플릿 (employees.xlsx)

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | 이름                                 | 직급              | 연봉            |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

> [!NOTE]
> `${repeat(...)}` 마커는 반복 범위 밖이라면 워크북 내 어디에 있어도 됩니다(다른 시트도 가능). 범위 파라미터로 지정된 영역이 반복됩니다.

#### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("한용호", "과장", 6500),
            Employee("홍용호", "대리", 4500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("employees.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

#### 결과


|   | A    | B  | C     |
|---|------|----|-------|
| 1 |      |    |       |
| 2 | 이름   | 직급 | 연봉    |
| 3 | 황용호  | 부장 | 8,000 |
| 4 | 한용호  | 과장 | 6,500 |
| 5 | 홍용호  | 대리 | 4,500 |

> [!TIP]
> 반복 영역이 확장되면 수식 참조, 차트, 피벗 테이블 등 영향 받는 요소들의 좌표와 범위가 자동으로 조정됩니다. 자세한 내용은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#28-관련-요소-자동-조정)를 참조하세요.

> [!TIP]
> 컬렉션이 비어있을 때 대체 내용을 표시할 수 있습니다. `empty` 파라미터로 지정된 범위의 내용이 출력됩니다. 자세한 내용은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#27-빈-컬렉션-처리-empty)를 참조하세요.

### 2.3 이미지 삽입

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File

fun main() {
    val logoBytes = File("logo.png").readBytes()

    val provider = simpleDataProvider {
        value("company", "(주)휴넷")
        image("logo", logoBytes)
    }

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### 2.4 파일 저장

`generate()`는 바이트 배열을 반환하고, `generateToFile()`은 파일로 직접 저장합니다.

```kotlin
ExcelGenerator().use { generator ->
    // 바이트 배열로 받기
    val bytes = generator.generate(template, data)

    // 파일로 직접 저장
    val path = generator.generateToFile(template, data, outputDir, "report")
}
```

`generateToFile()` 사용 시 파일명은 다음 규칙으로 생성됩니다.

| 설정 | 기본값 | 결과 예시 |
|------|--------|----------|
| 파일명 모드 | `TIMESTAMP` | `report_20260115_143052.xlsx` |
| 충돌 시 | `SEQUENCE` | `report_20260115_143052_1.xlsx` |

파일명 모드, 타임스탬프 형식, 충돌 정책 등 상세 설정은 [설정 옵션 레퍼런스](./reference/configuration.md#filenamemode)를 참조하세요.

---

## 3. DataProvider 사용하기

### 3.1 Map vs DataProvider

| 방식 | 장점 | 적합한 상황 |
|------|------|-----------|
| Map | 간단함, 코드량 적음 | 소량 데이터, 단순 보고서 |
| DataProvider | 지연 로딩, 메모리 효율 | 대용량 데이터, DB 연동 |

### 3.2 simpleDataProvider DSL (Kotlin)

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider

val provider = simpleDataProvider {
    // 단순 변수
    value("title", "보고서 제목")
    value("date", LocalDate.now().toString())

    // 컬렉션 (즉시 로딩)
    items("departments", listOf(dept1, dept2, dept3))

    // 컬렉션 (지연 로딩) - 데이터가 필요할 때 호출됨
    items("employees") {
        employeeRepository.findAll().iterator()
    }

    // 이미지
    image("logo", logoBytes)

    // 문서 메타데이터
    metadata {
        title = "월간 보고서"
        author = "황용호"
        company = "(주)휴넷"
    }
}
```

### 3.3 SimpleDataProvider.Builder (Java)

```java
import io.github.jogakdal.tbeg.SimpleDataProvider;

SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "보고서 제목")
    .value("date", LocalDate.now().toString())
    .items("departments", List.of(dept1, dept2, dept3))
    .itemsFromSupplier("employees", () -> employeeRepository.findAll().iterator())
    .image("logo", logoBytes)
    .metadata(meta -> meta
        .title("월간 보고서")
        .author("황용호")
        .company("(주)휴넷"))
    .build();
```

### 3.4 커스텀 DataProvider 구현

특수한 데이터 소스가 필요한 경우 `ExcelDataProvider` 인터페이스를 직접 구현할 수 있습니다.

```kotlin
import io.github.jogakdal.tbeg.ExcelDataProvider

class MyDataProvider(
    private val repository: EmployeeRepository
) : ExcelDataProvider {

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "직원 현황"
        "date" -> LocalDate.now().toString()
        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> repository.streamAll().iterator()
        else -> null
    }

    override fun getImage(name: String): ByteArray? = null

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> repository.count().toInt()
        else -> null
    }
}
```

---

## 4. 비동기 처리

### 4.1 Kotlin Coroutines

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()

        // 비동기 생성
        val path = generator.generateToFileAsync(
            template = template,
            data = mapOf("title" to "비동기 보고서"),
            outputDir = Path.of("./output"),
            baseFileName = "async_report"
        )

        println("파일 생성됨: $path")
    }
}
```

### 4.2 Java CompletableFuture

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class AsyncExample {
    public static void main(String[] args) throws Exception {
        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = new FileInputStream("template.xlsx")) {

            CompletableFuture<Path> future = generator.generateToFileFuture(
                template,
                Map.of("title", "비동기 보고서"),
                Path.of("./output"),
                "async_report"
            );

            future.thenAccept(path -> System.out.println("파일 생성됨: " + path));

            // 완료 대기
            Path result = future.get();
        }
    }
}
```

### 4.3 백그라운드 작업 + 리스너

API 서버 등에서 즉시 응답 후 백그라운드 처리에 적합합니다.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import java.nio.file.Path

val job = generator.submitToFile(
    template = template,
    dataProvider = provider,
    outputDir = Path.of("./output"),
    baseFileName = "background_report",
    listener = object : ExcelGenerationListener {
        override fun onStarted(jobId: String) {
            println("[시작] Job ID: $jobId")
        }

        override fun onCompleted(jobId: String, result: GenerationResult) {
            println("[완료] 파일: ${result.filePath}")
            println("[완료] 처리 행: ${result.rowsProcessed}")
        }

        override fun onFailed(jobId: String, error: Exception) {
            println("[실패] ${error.message}")
        }
    }
)

// API 서버에서는 여기서 즉시 응답
return ResponseEntity.accepted().body(mapOf("jobId" to job.jobId))
```

---

## 5. 대용량 데이터 처리

### 5.1 스트리밍 모드

TBEG은 기본적으로 스트리밍 모드(SXSSF)를 사용하여 대용량 데이터를 메모리 효율적으로 처리합니다.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode

// 스트리밍 모드 (기본값)
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED
)

// 비스트리밍 모드 (소량 데이터, 복잡한 수식)
val configNonStreaming = TbegConfig(
    streamingMode = StreamingMode.DISABLED
)
```

### 5.2 지연 로딩 + count 제공 (권장)

대용량 데이터 처리 시 count(전체 데이터 수)를 함께 제공하면 최적의 성능을 얻을 수 있습니다.

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider

val employeeCount = employeeRepository.count().toInt()

val provider = simpleDataProvider {
    value("title", "전체 직원 현황")

    // count와 함께 지연 로딩 제공
    items("employees", employeeCount) {
        employeeRepository.streamAll().iterator()
    }
}
```

### 5.3 커스텀 DataProvider에서 count 제공

```kotlin
class OptimizedDataProvider(
    private val repository: EmployeeRepository
) : ExcelDataProvider {

    override fun getValue(name: String): Any? = /* ... */

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> repository.streamAll().iterator()
        else -> null
    }

    // count 제공으로 성능 최적화
    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> repository.count().toInt()
        else -> null
    }
}
```

### 5.4 JPA Stream 연동

Spring Data JPA의 Stream을 활용한 대용량 처리:

```kotlin
interface EmployeeRepository : JpaRepository<Employee, Long> {
    @Query("SELECT e FROM Employee e")
    fun streamAll(): Stream<Employee>
}

@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val employeeRepository: EmployeeRepository
) {
    @Transactional(readOnly = true)  // Stream 유지를 위해 필수
    fun generateLargeReport(): Path {
        val count = employeeRepository.count().toInt()

        val provider = simpleDataProvider {
            value("title", "전체 직원 현황")
            items("employees", count) {
                employeeRepository.streamAll().iterator()
            }
        }

        return excelGenerator.generateToFile(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("/var/reports"),
            baseFileName = "all_employees"
        )
    }
}
```

> [!WARNING]
> JPA Stream을 사용할 때는 `@Transactional` 어노테이션이 필수입니다. Stream은 트랜잭션이 끝나면 닫히므로 Excel 생성이 완료될 때까지 트랜잭션이 유지되어야 합니다.

### 5.5 대용량 처리 권장 설정

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,    // 스트리밍 모드 활성화
    progressReportInterval = 1000              // 1000행마다 진행률 보고
)

val generator = ExcelGenerator(config)
```

---

## 다음 단계

- [템플릿 문법 레퍼런스](./reference/template-syntax.md) - 상세 템플릿 문법
- [API 레퍼런스](./reference/api-reference.md) - 클래스 및 메서드 상세
- [기본 예제](./examples/basic-examples.md) - 다양한 사용 예제
- [문제 해결](./troubleshooting.md) - 자주 발생하는 문제와 해결 방법
