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

// Maven Central에 배포되어 있으므로 별도의 저장소 설정이 필요하지 않습니다.
dependencies {
    implementation("io.github.jogakdal:tbeg:1.2.3")
}
```

#### Gradle (Groovy DSL)

```groovy
// build.gradle

// Maven Central에 배포되어 있으므로 별도의 저장소 설정이 필요하지 않습니다.
dependencies {
    implementation 'io.github.jogakdal:tbeg:1.2.3'
}
```

#### Maven

```xml
<!-- pom.xml -->

<!-- Maven Central에 배포되어 있으므로 별도의 저장소 설정이 필요하지 않습니다. -->
<dependencies>
    <dependency>
        <groupId>io.github.jogakdal</groupId>
        <artifactId>tbeg</artifactId>
        <version>1.2.3</version>
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

### 1.3 출력 방식

TBEG은 세 가지 출력 방식을 제공합니다.

```kotlin
ExcelGenerator().use { generator ->
    // 1. 바이트 배열로 받기
    val bytes = generator.generate(template, data)

    // 2. OutputStream에 직접 쓰기 (HTTP 응답 등)
    generator.generateToStream(template, data, outputStream)

    // 3. 파일로 직접 저장
    val path = generator.generateToFile(template, data, outputDir, "report")
}
```

| 메서드 | 반환 | 적합한 상황 |
|:------:|:----:|-----------|
| `generate()` | `ByteArray` | 후처리가 필요하거나 결과를 여러 곳에 전달할 때 |
| `generateToStream()` | - | HTTP 응답 스트림 등에 직접 쓸 때 |
| `generateToFile()` | `Path` | 파일로 저장할 때 (대용량 처리 시 권장) |

`generateToFile()` 사용 시 파일명은 다음 규칙으로 생성됩니다.

|   설정   |     기본값     | 결과 예시                           |
|:------:|:-----------:|---------------------------------|
| 파일명 모드 | `TIMESTAMP` | `report_20260115_143052.xlsx`   |
|  충돌 시  | `SEQUENCE`  | `report_20260115_143052_1.xlsx` |

파일명 모드, 타임스탬프 형식, 충돌 정책 등 상세 설정은 [설정 옵션 레퍼런스](./reference/configuration.md#filenamingmode)를 참조하세요.

---

## 2. 핵심 개념

TBEG은 Excel이 할 수 없는 동적 데이터 바인딩(변수 치환, 데이터 반복, 이미지 삽입)을 제공합니다. 수식, 조건부 서식, 차트 등은 Excel 기능을 그대로 활용하며, TBEG은 데이터가 확장되어도 이 기능들이 의도대로 동작하도록 자동 조정합니다.

### 2.1 템플릿 문법

TBEG은 Excel 템플릿에 특수 마커를 사용하여 데이터를 바인딩합니다.

|            문법            |    설명     |                      예시                       |
|:------------------------:|:---------:|:---------------------------------------------:|
|         `${변수명}`         | 단순 변수 치환  |                  `${title}`                   |
|        `${객체.필드}`        | 객체의 필드 치환 |                 `${emp.name}`                 |
| `${repeat(컬렉션, 범위, 변수)}` |   반복 처리   |      `${repeat(employees, A3:C3, emp)}`       |
|      `${image(이름)}`      |  이미지 삽입   |               `${image(logo)}`                |
|      `${size(컬렉션)}`      |  컬렉션 크기   |             `${size(employees)}명`             |
|    `${merge(객체.필드)}`     |  자동 셀 병합  |             `${merge(emp.dept)}`              |
|     `${bundle(범위)}`      |   요소 묶음   |              `${bundle(A5:H12)}`              |
|    `${hideable(...)}`    | 선택적 필드 노출 | `${hideable(value=emp.salary, bundle=C1:C3)}` |

자세한 문법은 [템플릿 문법 레퍼런스](./reference/template-syntax.md)를 참조하세요.

### 2.2 반복 처리

리스트 데이터를 템플릿의 지정된 범위에 반복 출력합니다. 기본적으로 아래 방향(DOWN)으로 확장되며, 오른쪽 방향(RIGHT) 확장도 지원합니다. 자세한 문법은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#33-오른쪽-방향-반복-right)를, 코드 예제는 [고급 예제](./examples/advanced-examples-kotlin.md#8-오른쪽-방향-반복) ([Java](./examples/advanced-examples-java.md#8-오른쪽-방향-반복))를 참조하세요.

#### 템플릿 (employees.xlsx)

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | 이름                                 | 직급              | 연봉            |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

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
> 반복 영역이 확장되면 수식 참조, 차트, 피벗 테이블 등 영향 받는 요소들의 좌표와 범위가 자동으로 조정됩니다. 자세한 내용은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#36-관련-요소-자동-조정)를 참조하세요.

> [!TIP]
> 컬렉션이 비어있을 때 대체 내용을 표시할 수 있습니다. `empty` 파라미터로 지정된 범위의 내용이 출력됩니다. 자세한 내용은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#35-빈-컬렉션-처리-empty)를 참조하세요.

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

        // URL로도 이미지를 지정할 수 있습니다 (TBEG 엔진이 다운로드하여 삽입)
        imageUrl("banner", "https://example.com/banner.png")
    }

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

> URL 이미지는 `generate()` 호출 시 다운로드되어 Excel 파일에 임베딩됩니다. 같은 호출 내에서 동일 URL은 한 번만 다운로드됩니다. 다운로드 실패 시에도 Excel 생성은 정상적으로 완료됩니다. 상세 설정은 [이미지 URL 문법](./reference/template-syntax.md#45-url-이미지) 및 [캐시 설정](./reference/configuration.md#imageurlcachettlseconds)을 참조하세요.

### 2.4 선택적 필드 노출

상황에 따라 반복 출력되는 필드 중 특정 필드의 노출을 제한할 수 있습니다. 템플릿에 `hideable` 마커를 배치하고, 코드에서 숨길 필드를 지정하면 해당 열이 자동으로 제거되거나 비활성화됩니다.

#### 템플릿 (employees.xlsx)

|   | A                                | B               | C                                           | D               |
|---|----------------------------------|-----------------|---------------------------------------------|-----------------|
| 1 | ${repeat(employees, A3:D3, emp)} |                 |                                             |                 |
| 2 | 이름                               | 직급              | 연봉                                          | 입사일             |
| 3 | ${emp.name}                      | ${emp.position} | ${hideable(value=emp.salary, bundle=C2:C3)} | ${emp.hireDate} |

#### Kotlin 코드

```kotlin
val provider = simpleDataProvider {
    items("employees", employeeList)
    hideFields("employees", "salary")  // salary 필드 숨김
}

ExcelGenerator().use { generator ->
    val bytes = generator.generate(templateStream, provider)
}
```

`hideFields`에 `"salary"`를 지정하면 표의 연봉 열 전체(타이틀 포함)가 제거되고, 나머지 열이 자동으로 당겨집니다. `hideFields`를 지정하지 않으면 일반 필드처럼 값이 출력됩니다.

#### 숨김 모드

| 모드 | 설명 |
|:----:|------|
| `DELETE` | 해당 열을 물리적으로 삭제하고 나머지 요소를 당깁니다 (기본값). |
| `DIM` | 데이터 영역에 비활성화 스타일(회색 배경 + 연한 글자색)을 적용하고 값을 제거합니다. 필드 타이틀 등 repeat 밖 bundle 영역은 글자색만 변경됩니다. |

> [!TIP]
> 자세한 문법과 수식에서의 사용법은 [템플릿 문법 레퍼런스](./reference/template-syntax.md#10-선택적-필드-노출-hideable)를 참조하세요.

---

## 3. DataProvider 사용하기

데이터 규모와 소스에 따라 적절한 방식을 선택하세요.

| 데이터 규모 | 권장 방식 | 비고 |
|-----------|----------|------|
| ~1,000행 | `Map<String, Any>` | 가장 간단, 별도 설정 불필요 |
| 1,000~10,000행 | `simpleDataProvider` + count | 지연 로딩으로 메모리 절약 |
| 10,000행 이상 | 커스텀 `DataProvider` + Stream | DB 스트리밍과 결합하여 최적 성능 |

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

    // 컬렉션 (지연 로딩 + count) - 대용량 데이터에 권장
    items("allEmployees", employeeRepository.count().toInt()) {
        employeeRepository.streamAll().iterator()
    }

    // 이미지 (ByteArray)
    image("logo", logoBytes)

    // 이미지 (URL - 렌더링 시 자동 다운로드)
    imageUrl("banner", "https://example.com/banner.png")

    // 선택적 필드 노출 (hideable 마커가 있는 필드 대상)
    hideFields("employees", "salary")

    // 문서 메타데이터
    metadata {
        title = "월간 보고서"
        author = "황용호"
        company = "(주)휴넷"
    }
}
```

> [!TIP]
> 대용량 컬렉션에는 위의 `items("allEmployees", employeeRepository.count().toInt()) { ... }` 예시처럼 count를 함께 제공하면 최적의 성능을 얻을 수 있습니다. count가 없으면 데이터를 먼저 순회하여 개수를 파악한 후 처리하지만, count를 제공하면 이 단계를 건너뛸 수 있습니다.

### 3.3 SimpleDataProvider.Builder (Java)

```java
import io.github.jogakdal.tbeg.SimpleDataProvider;

SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "보고서 제목")
    .value("date", LocalDate.now().toString())
    .items("departments", List.of(dept1, dept2, dept3))
    .itemsFromSupplier("employees", () -> employeeRepository.findAll().iterator())
    .hideFields("employees", "salary")  // 선택적 필드 노출
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

    // count 제공으로 대용량 처리 성능 최적화
    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> repository.count().toInt()
        else -> null
    }
}
```

> [!TIP]
> `getItemCount()`를 구현하면 대용량 데이터 처리 시 성능이 향상됩니다. 자세한 내용은 [5. 대용량 데이터 처리](#5-대용량-데이터-처리)를 참조하세요.

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
            data = mapOf("title" to "월간 매출 보고서"),
            outputDir = Path.of("./output"),
            baseFileName = "monthly_sales"
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
                Map.of("title", "월간 매출 보고서"),
                Path.of("./output"),
                "monthly_sales"
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
    baseFileName = "monthly_sales",
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

TBEG은 대용량 데이터를 최대한의 성능과 최소한의 리소스로 안정적으로 처리합니다. 100만 행을 약 9초에 생성하면서도 시스템 CPU의 9% 미만만 사용하므로, 서버에서 다른 서비스와 함께 운영해도 부담이 없습니다. 렌더링과 후처리 모두 스트리밍 방식으로 동작하여 데이터 크기에 관계없이 일정한 메모리 버퍼만 사용합니다. 별도의 설정 없이 기본 동작으로 최적의 성능을 제공합니다.

대용량 데이터 처리의 핵심은 DataProvider를 통한 **지연 로딩**과 **count 제공**입니다. 설정 방법은 [3. DataProvider 사용하기](#3-dataprovider-사용하기)를 참조하세요.

### 5.1 JPA Stream 연동

Spring Data JPA의 Stream을 활용한 대용량 처리 패턴입니다.

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

### 5.2 권장 설정

```kotlin
val config = TbegConfig(
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
