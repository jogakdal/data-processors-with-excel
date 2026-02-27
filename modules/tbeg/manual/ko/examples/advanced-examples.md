# TBEG 고급 예제

## 목차
1. [DataProvider 활용](#1-dataprovider-활용)
   - [1.1 simpleDataProvider DSL 사용법](#11-simpledataprovider-dsl-사용법)
   - [1.2 ExcelDataProvider 직접 구현](#12-exceldataprovider-직접-구현)
   - [1.3 JPA/Spring Data 연동](#13-jpaspring-data-연동)
   - [1.4 MyBatis 연동](#14-mybatis-연동)
   - [1.5 외부 API 연동](#15-외부-api-연동)
2. [비동기 처리](#2-비동기-처리)
3. [수식에서 변수 사용](#3-수식에서-변수-사용)
4. [하이퍼링크](#4-하이퍼링크)
5. [다중 시트](#5-다중-시트)
6. [대용량 데이터 처리](#6-대용량-데이터-처리)
7. [다중 반복 영역](#7-다중-반복-영역)
8. [오른쪽 방향 반복](#8-오른쪽-방향-반복)
9. [빈 컬렉션 처리](#9-빈-컬렉션-처리)
10. [다국어 지원 (I18N)](#10-다국어-지원-i18n)
11. [종합 예제 — 분기 매출 실적 보고서](#11-종합-예제--분기-매출-실적-보고서)

> [!NOTE]
> 이 문서의 예제는 `resources/templates/` 디렉토리에서 템플릿을 로드합니다.
> 파일 시스템에서 직접 읽으려면 `File("template.xlsx").inputStream()`을 사용하세요.

---

## 1. DataProvider 활용

DataProvider는 TBEG의 핵심 개념입니다. 대용량 데이터를 효율적으로 처리하기 위해 **지연 로딩**과 **스트리밍** 방식을 지원합니다.

### 데이터 제공 방식 비교

| 방식 | 메모리 사용 | 적합한 상황 |
|------|-----------|------------|
| `Map<String, Any>` | 전체 로드 | 소량 데이터, 간단한 보고서 |
| `simpleDataProvider` DSL | 지연 로딩 | 중간 규모, 일반적인 사용 |
| `ExcelDataProvider` 구현 | 완전 제어 | 대용량, DB 직접 연동 |

---

### 1.1 simpleDataProvider DSL 사용법

Kotlin DSL을 사용한 가장 간편한 방법입니다.

#### 사용할 템플릿 (template.xlsx)

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${title}                         |                 |               |
| 2 | 작성일: ${date}                     | 작성자: ${author}  |               |
| 3 | ${repeat(employees, A5:C5, emp)} |                 |               |
| 4 | 이름                               | 직급              | 연봉            |
| 5 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

- **단일 값**: `${title}`, `${date}`, `${author}` → DataProvider의 `value()`로 제공
- **반복 영역**: `${repeat(employees, A5:C5, emp)}` → DataProvider의 `items()`로 제공
- **아이템 속성**: `${emp.name}`, `${emp.position}`, `${emp.salary}` → 각 아이템의 필드 참조

#### 기본 사용법

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider
import java.time.LocalDate

// 데이터 클래스 정의
data class Employee(val name: String, val position: String, val salary: Int)

val provider = simpleDataProvider {
    // 단일 값
    value("title", "직원 현황 보고서")
    value("date", LocalDate.now())
    value("author", "황용호")

    // 컬렉션 (List)
    items("employees", listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500)
    ))
}
```

#### 지연 로딩 (Lambda)

데이터가 실제로 필요할 때까지 로드를 지연합니다.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.time.LocalDate

data class Employee(val name: String, val position: String, val salary: Int)

// 직원 수를 조회하는 함수 (SELECT COUNT(*) 쿼리)
fun countEmployees(): Int {
    // JPA 사용 시:
    // return employeeRepository.count().toInt()
    // return employeeRepository.countByDepartmentId(deptId)

    // 예시용 더미 데이터
    return 3
}

// 직원 목록을 스트리밍으로 조회하는 함수
fun streamEmployees(): Iterator<Employee> {
    // JPA 사용 시:
    // return employeeRepository.findAll().iterator()
    // return employeeRepository.streamAll().iterator()  // 대용량

    // 예시용 더미 데이터
    return listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500),
        Employee("홍용호", "대리", 4500)
    ).iterator()
}

// 1. count를 먼저 조회 (가벼운 쿼리)
val employeeCount = countEmployees()

// 2. DataProvider 생성 (이 시점에 컬렉션 데이터는 로드되지 않음)
val provider = simpleDataProvider {
    // 단일 값
    value("title", "직원 현황 보고서")
    value("date", LocalDate.now())
    value("author", "황용호")

    // 컬렉션: count와 함께 지연 로딩 제공
    items("employees", employeeCount) {
        // 이 블록은 Excel 생성 시점에 실행됨
        streamEmployees()
    }
}

// 3. Excel 생성 (이 시점에 Lambda가 호출되어 데이터 로드)
ExcelGenerator().use { generator ->
    val template = javaClass.getResourceAsStream("/templates/template.xlsx")
        ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

    val result = generator.generate(template, provider)
    File("output.xlsx").writeBytes(result)
}
```

**동작 흐름:**
1. `countEmployees()` 호출 → count만 먼저 조회 (가벼운 쿼리)
2. `simpleDataProvider { ... }` 호출 → Provider 객체 생성 (컬렉션 데이터는 로드 안 함)
3. `generator.generate(template, provider)` 호출
4. 템플릿에서 `employees` 데이터가 필요한 시점에 Lambda 실행, DB 조회
5. 생성된 Excel 바이트 배열을 파일로 저장

**count 제공을 권장하는 이유:**
- TBEG이 미리 전체 행 수를 알 수 있어 수식 범위를 즉시 계산 가능
- 데이터를 2번 순회할 필요 없음
- DB의 `SELECT COUNT(*)` 쿼리는 인덱스만 사용하므로 매우 빠름

> [!NOTE]
> count를 제공하지 않아도 동작에는 문제가 없습니다. 다만 TBEG이 전체 행 수를 파악하기 위해 컬렉션을 먼저 순회해야 하므로, 이중 순회로 인한 성능 저하가 발생할 수 있습니다.

#### 이미지 포함

```kotlin
import java.io.File
import java.net.URL

// 서버에서 이미지를 다운로드하는 함수 (예시)
fun downloadImage(imageUrl: String): ByteArray {
    // 방법 1: Java URL (간단한 경우)
    return URL(imageUrl).readBytes()

    // 방법 2: Spring RestTemplate
    // return restTemplate.getForObject(imageUrl, ByteArray::class.java)!!

    // 방법 3: Spring WebClient (리액티브)
    // return webClient.get().uri(imageUrl).retrieve().bodyToMono<ByteArray>().block()!!
}

val provider = simpleDataProvider {
    value("company", "(주)휴넷")

    // 이미지 - 즉시 로딩 (resources 디렉토리에서 로드)
    image("logo", javaClass.getResourceAsStream("/images/logo.png")!!.readBytes())
    // 파일에서 직접 읽는 경우: image("logo", File("logo.png").readBytes())

    // 이미지 - 지연 로딩 (Lambda)
    image("signature") {
        // 이 블록은 이미지가 실제로 필요할 때 호출됨
        // 예: 서버에서 사용자 서명 이미지 다운로드
        downloadImage("https://example.com/signatures/user123.png")
    }
}
```

**지연 로딩이 유용한 경우:**
- 외부 서버에서 이미지를 다운로드해야 할 때
- 이미지 생성에 시간이 걸릴 때 (예: 차트 이미지 렌더링)
- 조건에 따라 이미지가 필요 없을 수 있을 때

#### 문서 메타데이터

```kotlin
val provider = simpleDataProvider {
    value("title", "보고서")

    metadata {
        title = "2026년 월간 보고서"
        author = "황용호"
        subject = "월간 실적"
        keywords("월간", "보고서", "실적")
        company = "(주)휴넷"
    }
}
```

#### Java Builder 패턴

```java
SimpleDataProvider provider = SimpleDataProvider.builder()
    .value("title", "직원 현황 보고서")
    .value("date", LocalDate.now())
    .items("employees", employeeList)
    .items("employees", employeeCount, () -> fetchEmployees())  // count + lambda
    .image("logo", logoBytes)  // 즉시 로딩
    .imageFromSupplier("signature", () -> downloadSignature())  // 지연 로딩
    .metadata(meta -> meta
        .title("보고서")
        .author("황용호"))
    .build();
```

---

### 1.2 ExcelDataProvider 직접 구현

`simpleDataProvider` DSL이 대부분의 경우에 충분하지만, 다음과 같은 상황에서는 인터페이스를 직접 구현하는 것이 유리합니다.

#### SimpleDataProvider와 비교

| 관점 | SimpleDataProvider | 직접 구현 |
|------|-------------------|----------|
| 데이터 간 의존성 | 불가 | 가능 (메서드 간 호출) |
| 조회 결과 캐싱 | Lambda 외부 변수로 우회 | 클래스 필드로 자연스럽게 |
| 조건부 데이터 제공 | Lambda 내부로 제한 | 자유로운 분기 처리 |
| DB 커서 등 리소스 정리 | 불가 | `Closeable` 구현 가능 |
| 단위 테스트 | 전체 교체 필요 | Repository Mock 주입 용이 |

#### 인터페이스 구조

```kotlin
interface ExcelDataProvider {
    fun getValue(name: String): Any?           // 단일 값
    fun getItems(name: String): Iterator<Any>? // 컬렉션 (Iterator)
    fun getImage(name: String): ByteArray?     // 이미지 (선택)
    fun getMetadata(): DocumentMetadata?       // 메타데이터 (선택)
    fun getItemCount(name: String): Int?       // 아이템 수 (선택, 성능 최적화)
}
```

#### Kotlin 구현 예제

```kotlin
import io.github.jogakdal.tbeg.ExcelDataProvider
import io.github.jogakdal.tbeg.DocumentMetadata
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EmployeeReportDataProvider(
    private val departmentId: Long,
    private val reportDate: LocalDate,
    private val employeeRepository: EmployeeRepository
) : ExcelDataProvider, Closeable {

    // [장점 1] 조회 결과 캐싱 - 클래스 필드로 자연스럽게 관리
    private var cachedCount: Int? = null
    private var cachedDepartmentName: String? = null

    // [장점 2] 리소스 정리 - Stream/Cursor 등 정리 가능
    private var employeeStream: java.util.stream.Stream<Employee>? = null

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "부서별 직원 현황"

        // 캐싱: 같은 값이 여러 셀에서 참조될 때 DB 조회 1회만 수행
        "departmentName" -> cachedDepartmentName
            ?: employeeRepository.getDepartmentName(departmentId)
                .also { cachedDepartmentName = it }

        "reportDate" -> reportDate.toString()

        // 동적 값: 호출 시점의 현재 시간
        "generatedAt" -> LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        // [장점 3] 데이터 간 의존성 - 다른 메서드 결과 활용
        "summary" -> "${cachedDepartmentName ?: "부서"} 소속 총 ${getOrLoadCount()}명"

        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> {
            // Stream을 필드에 저장하여 close()에서 정리
            employeeStream = employeeRepository.streamByDepartmentId(departmentId)
            employeeStream!!.iterator()
        }

        // [장점 4] 조건부 데이터 제공 - 대규모 부서만 관리자 목록 별도 제공
        "managers" -> if (getOrLoadCount() > 50) {
            employeeRepository.findManagersByDepartmentId(departmentId).iterator()
        } else {
            null  // 소규모 부서는 관리자 목록 미제공
        }

        else -> null
    }

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> getOrLoadCount()
        "managers" -> if (getOrLoadCount() > 50) {
            employeeRepository.countManagersByDepartmentId(departmentId)
        } else null
        else -> null
    }

    override fun getMetadata(): DocumentMetadata = DocumentMetadata(
        title = "${cachedDepartmentName ?: "부서"} 직원 현황 보고서",
        author = "HR 시스템",
        subject = "직원 현황",
        company = "(주)휴넷"
    )

    // 내부 헬퍼: count 캐싱 로직
    private fun getOrLoadCount(): Int =
        cachedCount ?: employeeRepository.countByDepartmentId(departmentId)
            .also { cachedCount = it }

    // [장점 2] 리소스 정리 구현
    override fun close() {
        employeeStream?.close()
    }
}
```

#### 사용 예제

```kotlin
@Service
class ReportService(
    private val employeeRepository: EmployeeRepository,
    private val resourceLoader: ResourceLoader
) {
    @Transactional(readOnly = true)
    fun generateDepartmentReport(departmentId: Long): ByteArray {
        // Closeable 구현체이므로 use 블록으로 리소스 자동 정리
        EmployeeReportDataProvider(
            departmentId = departmentId,
            reportDate = LocalDate.now(),
            employeeRepository = employeeRepository
        ).use { provider ->
            return ExcelGenerator().use { generator ->
                val template = resourceLoader.getResource("classpath:templates/department_report.xlsx")
                generator.generate(template.inputStream, provider)
            }
        }
    }
}
```

> [!WARNING]
> `@Transactional` 범위 내에서 Stream을 사용해야 합니다. 트랜잭션이 종료되면 DB 연결이 닫혀 Stream도 무효화됩니다.

#### Java 구현 예제

```java
public class EmployeeReportDataProvider implements ExcelDataProvider, Closeable {

    private final Long departmentId;
    private final LocalDate reportDate;
    private final EmployeeRepository repository;

    // [장점 1] 조회 결과 캐싱
    private Integer cachedCount = null;
    private String cachedDepartmentName = null;

    // [장점 2] 리소스 정리용
    private Stream<Employee> employeeStream = null;

    public EmployeeReportDataProvider(Long departmentId, LocalDate reportDate,
                                      EmployeeRepository repository) {
        this.departmentId = departmentId;
        this.reportDate = reportDate;
        this.repository = repository;
    }

    @Override
    public Object getValue(String name) {
        return switch (name) {
            case "title" -> "부서별 직원 현황";
            case "departmentName" -> getOrLoadDepartmentName();
            case "reportDate" -> reportDate.toString();
            case "generatedAt" -> LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            // [장점 3] 데이터 간 의존성
            case "summary" -> getOrLoadDepartmentName() + " 소속 총 " + getOrLoadCount() + "명";
            default -> null;
        };
    }

    @Override
    public Iterator<Object> getItems(String name) {
        return switch (name) {
            case "employees" -> {
                employeeStream = repository.streamByDepartmentId(departmentId);
                yield employeeStream.map(e -> (Object) e).iterator();
            }
            // [장점 4] 조건부 데이터 제공
            case "managers" -> getOrLoadCount() > 50
                ? repository.findManagersByDepartmentId(departmentId)
                    .stream().map(e -> (Object) e).iterator()
                : null;
            default -> null;
        };
    }

    @Override
    public Integer getItemCount(String name) {
        return switch (name) {
            case "employees" -> getOrLoadCount();
            case "managers" -> getOrLoadCount() > 50
                ? repository.countManagersByDepartmentId(departmentId)
                : null;
            default -> null;
        };
    }

    private int getOrLoadCount() {
        if (cachedCount == null) {
            cachedCount = repository.countByDepartmentId(departmentId);
        }
        return cachedCount;
    }

    private String getOrLoadDepartmentName() {
        if (cachedDepartmentName == null) {
            cachedDepartmentName = repository.getDepartmentName(departmentId);
        }
        return cachedDepartmentName;
    }

    // [장점 2] 리소스 정리
    @Override
    public void close() {
        if (employeeStream != null) {
            employeeStream.close();
        }
    }
}
```

#### Java 사용 예제

```java
@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final ResourceLoader resourceLoader;

    @Transactional(readOnly = true)
    public byte[] generateDepartmentReport(Long departmentId) throws IOException {
        try (var provider = new EmployeeReportDataProvider(
                departmentId, LocalDate.now(), employeeRepository);
             var generator = new ExcelGenerator();
             var template = resourceLoader.getResource("classpath:templates/department_report.xlsx")
                .getInputStream()) {

            return generator.generate(template, provider);
        }
    }
}
```

---

### 1.3 JPA/Spring Data 연동

#### Repository 인터페이스

```kotlin
interface EmployeeRepository : JpaRepository<Employee, Long> {

    // count 쿼리 (성능 최적화용)
    fun countByDepartmentId(departmentId: Long): Int

    // Stream 반환 (대용량 처리)
    @QueryHints(QueryHint(name = HINT_FETCH_SIZE, value = "100"))
    fun streamByDepartmentId(departmentId: Long): Stream<Employee>

    // 또는 Slice 기반 페이징
    fun findByDepartmentId(departmentId: Long, pageable: Pageable): Slice<Employee>
}
```

#### Stream 기반 DataProvider

```kotlin
@Service
class ReportService(
    private val employeeRepository: EmployeeRepository,
    private val excelGenerator: ExcelGenerator
) {
    @Transactional(readOnly = true)
    fun generateReport(departmentId: Long): ByteArray {
        val count = employeeRepository.countByDepartmentId(departmentId)

        val provider = simpleDataProvider {
            value("title", "직원 현황")
            value("date", LocalDate.now())

            items("employees", count) {
                // @Transactional 내에서 Stream 사용
                employeeRepository.streamByDepartmentId(departmentId).iterator()
            }
        }

        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        return excelGenerator.generate(template.inputStream, provider)
    }
}
```

#### 페이징 기반 Iterator (메모리 효율적)

대용량 데이터를 페이지 단위로 가져오는 Iterator 구현:

```kotlin
class PagedIterator<T>(
    private val pageSize: Int = 1000,
    private val fetcher: (Pageable) -> Slice<T>
) : Iterator<T> {

    private var currentPage = 0
    private var currentIterator: Iterator<T> = emptyList<T>().iterator()
    private var hasMorePages = true

    override fun hasNext(): Boolean {
        if (currentIterator.hasNext()) return true
        if (!hasMorePages) return false

        // 다음 페이지 로드
        val slice = fetcher(PageRequest.of(currentPage++, pageSize))
        currentIterator = slice.content.iterator()
        hasMorePages = slice.hasNext()

        return currentIterator.hasNext()
    }

    override fun next(): T = currentIterator.next()
}
```

사용 예제:

```kotlin
val provider = simpleDataProvider {
    value("title", "대용량 보고서")

    items("employees", employeeCount) {
        PagedIterator(pageSize = 1000) { pageable ->
            employeeRepository.findByDepartmentId(departmentId, pageable)
        }
    }
}
```

---

### 1.4 MyBatis 연동

#### Mapper 인터페이스

```kotlin
@Mapper
interface EmployeeMapper {

    fun countByDepartmentId(departmentId: Long): Int

    // Cursor 기반 조회 (스트리밍)
    @Options(fetchSize = 100)
    fun selectByDepartmentIdWithCursor(departmentId: Long): Cursor<Employee>
}
```

#### DataProvider 구현

```kotlin
class MyBatisEmployeeDataProvider(
    private val departmentId: Long,
    private val employeeMapper: EmployeeMapper
) : ExcelDataProvider {

    private var cursor: Cursor<Employee>? = null

    override fun getValue(name: String): Any? = when (name) {
        "title" -> "직원 현황"
        else -> null
    }

    override fun getItems(name: String): Iterator<Any>? = when (name) {
        "employees" -> {
            cursor = employeeMapper.selectByDepartmentIdWithCursor(departmentId)
            cursor!!.iterator()
        }
        else -> null
    }

    override fun getItemCount(name: String): Int? = when (name) {
        "employees" -> employeeMapper.countByDepartmentId(departmentId)
        else -> null
    }

    fun close() {
        cursor?.close()
    }
}
```

#### Cursor 리소스 정리

MyBatis Cursor는 데이터베이스 연결을 유지하므로 사용 후 반드시 닫아야 합니다.

```kotlin
@Transactional(readOnly = true)
fun generateReport(departmentId: Long): ByteArray {
    val provider = MyBatisEmployeeDataProvider(departmentId, employeeMapper)

    try {
        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        return excelGenerator.generate(template.inputStream, provider)
    } finally {
        provider.close()  // Cursor를 닫지 않으면 DB 연결이 누수됨
    }
}
```

> [!WARNING]
> `@Transactional` 범위 내에서 Cursor를 사용해야 합니다. 트랜잭션이 종료되면 Cursor도 무효화됩니다.

---

### 1.5 외부 API 연동

마이크로서비스 아키텍처에서 다른 서비스의 API를 호출하여 데이터를 **페이지 단위로** 가져온 후 Excel로 변환하는 경우입니다.

#### Spring Data Page 기반 Iterator

Spring Data의 `Page<T>` 타입을 활용하여 페이징 API 응답을 Iterator로 변환합니다.

```kotlin
import org.springframework.data.domain.Page

class PagedApiIterator<T>(
    private val pageSize: Int = 100,
    private val fetcher: (page: Int, size: Int) -> Page<T>
) : Iterator<T> {

    private var currentPage = 0
    private var currentIterator: Iterator<T> = emptyList<T>().iterator()
    private var hasMorePages = true

    override fun hasNext(): Boolean {
        if (currentIterator.hasNext()) return true
        if (!hasMorePages) return false

        // 다음 페이지 로드 (API 호출)
        val result = fetcher(currentPage++, pageSize)
        currentIterator = result.content.iterator()
        hasMorePages = result.hasNext()

        return currentIterator.hasNext()
    }

    override fun next(): T = currentIterator.next()
}
```

#### 사용 예제

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.io.File

data class EmployeeDto(val name: String, val salary: Int)

// Feign Client 인터페이스 정의
// @FeignClient(name = "employee-service")
// interface EmployeeApiClient {
//     @GetMapping("/api/employees")
//     fun getEmployees(
//         @RequestParam("page") page: Int,
//         @RequestParam("size") size: Int
//     ): Page<EmployeeDto>
// }

// 다른 마이크로서비스의 API를 호출하여 데이터를 가져옴
fun fetchEmployeesFromApi(page: Int, size: Int): Page<EmployeeDto> {
    // Feign Client 사용 시:
    // return employeeApiClient.getEmployees(page, size)

    // RestTemplate 사용 시:
    // return restTemplate.exchange(
    //     "/api/employees?page=$page&size=$size",
    //     HttpMethod.GET,
    //     null,
    //     object : ParameterizedTypeReference<RestPageImpl<EmployeeDto>>() {}
    // ).body ?: throw Exception("API 호출 실패")

    // WebClient 사용 시:
    // return webClient.get()
    //     .uri("/api/employees?page=$page&size=$size")
    //     .retrieve()
    //     .bodyToMono<RestPageImpl<EmployeeDto>>()
    //     .block() ?: throw Exception("API 호출 실패")

    // 예시용 더미 응답
    val content = listOf(EmployeeDto("황용호", 8000), EmployeeDto("한용호", 6500))
    return PageImpl(content, PageRequest.of(page, size), 100)
}

fun main() {
    // 먼저 totalElements 조회 (첫 페이지 호출 또는 별도 count API)
    val firstPage = fetchEmployeesFromApi(0, 1)
    val totalCount = firstPage.totalElements.toInt()

    val provider = simpleDataProvider {
        value("title", "API 데이터 보고서")

        items("employees", totalCount) {
            PagedApiIterator(pageSize = 50) { page, size ->
                fetchEmployeesFromApi(page, size)
            }
        }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val result = generator.generate(template, provider)
        File("api_report.xlsx").writeBytes(result)
    }
}
```

> [!NOTE]
> Spring Data의 `Page<T>`는 0부터 시작하는 페이지 번호를 사용합니다. REST API에서 `Page`를 역직렬화할 때 `PageImpl`은 기본 생성자가 없으므로, `RestPageImpl` 등 커스텀 클래스를 사용하거나 `@JsonCreator`를 정의해야 합니다.

---

## 2. 비동기 처리

### 2.1 Kotlin Coroutines

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    val provider = simpleDataProvider {
        value("title", "비동기 보고서")
        items("data") { generateData().iterator() }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        // 비동기 생성
        val path = generator.generateToFileAsync(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("./output"),
            baseFileName = "async_report"
        )

        println("파일 생성됨: $path")
    }
}

fun generateData() = (1..1000).map { mapOf("id" to it, "value" to it * 10) }
```

### 2.2 Java CompletableFuture

```java
import io.github.jogakdal.tbeg.ExcelGenerator;
import io.github.jogakdal.tbeg.SimpleDataProvider;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AsyncWithFuture {
    public static void main(String[] args) throws Exception {
        SimpleDataProvider provider = SimpleDataProvider.builder()
            .value("title", "비동기 보고서")
            .items("data", generateData())
            .build();

        try (ExcelGenerator generator = new ExcelGenerator();
             InputStream template = AsyncWithFuture.class.getResourceAsStream("/templates/template.xlsx")) {

            CompletableFuture<Path> future = generator.generateToFileFuture(
                template,
                provider,
                Path.of("./output"),
                "async_report"
            );

            // 완료 시 콜백
            future.thenAccept(path -> {
                System.out.println("파일 생성됨: " + path);
            });

            // 완료 대기
            Path result = future.get();
        }
    }

    private static List<Map<String, Object>> generateData() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            data.add(Map.of("id", i, "value", i * 10));
        }
        return data;
    }
}
```

### 2.3 백그라운드 작업 + 리스너

API 서버에서 즉시 응답하고 백그라운드에서 처리합니다.

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.async.ExcelGenerationListener
import io.github.jogakdal.tbeg.async.GenerationResult
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main() {
    val latch = CountDownLatch(1)

    val provider = simpleDataProvider {
        value("title", "백그라운드 보고서")
        items("data") { (1..5000).map { mapOf("id" to it) }.iterator() }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

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
                    println("[완료] 소요 시간: ${result.durationMs}ms")
                    latch.countDown()
                }

                override fun onFailed(jobId: String, error: Exception) {
                    println("[실패] ${error.message}")
                    latch.countDown()
                }

                override fun onCancelled(jobId: String) {
                    println("[취소됨]")
                    latch.countDown()
                }
            }
        )

        println("작업 제출됨: ${job.jobId}")
        println("(API 서버에서는 여기서 HTTP 202 반환)")

        // 작업 취소 예시
        // job.cancel()

        latch.await(60, TimeUnit.SECONDS)
    }
}
```

---

## 3. 수식에서 변수 사용

### 템플릿 (formula_template.xlsx)

|   | A      | B                             |
|---|--------|-------------------------------|
| 1 | 시작 행   | ${startRow}                   |
| 2 | 종료 행   | ${endRow}                     |
| 3 |        |                               |
| 4 | 데이터1   | 100                           |
| 5 | 데이터2   | 200                           |
| 6 | 데이터3   | 300                           |
| 7 |        |                               |
| 8 | 합계     | =SUM(B${startRow}:B${endRow}) |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

fun main() {
    val data = mapOf(
        "startRow" to 4,
        "endRow" to 6
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/formula_template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("formula_output.xlsx").writeBytes(bytes)
    }
}
```

### 결과

|   | A      | B                  |
|---|--------|--------------------|
| 8 | 합계     | =SUM(B4:B6) -> 600 |

---

## 4. 하이퍼링크

### 템플릿 (link_template.xlsx)

셀 A1에 HYPERLINK 수식 설정:
```
=HYPERLINK("${url}", "${text}")
```

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

fun main() {
    val data = mapOf(
        "text" to "휴넷 홈페이지 바로가기",
        "url" to "https://www.hunet.co.kr"
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/link_template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("link_output.xlsx").writeBytes(bytes)
    }
}
```

---

## 5. 다중 시트

### 템플릿 (multi_sheet_template.xlsx)

**Summary 시트**:

|   | A      | B                  |
|---|--------|--------------------|
| 1 | 제목     | ${title}           |
| 2 | 총 직원 수 | ${size(employees)} |

**Employees 시트**:

|   | A                                  | B               | C             |
|---|------------------------------------|-----------------|---------------|
| 1 | ${repeat(employees, A3:C3, emp)}   |                 |               |
| 2 | 이름                                 | 직급              | 연봉            |
| 3 | ${emp.name}                        | ${emp.position} | ${emp.salary} |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val employees = listOf(
        Employee("황용호", "부장", 8000),
        Employee("한용호", "과장", 6500),
        Employee("홍용호", "대리", 4500)
    )

    val data = mapOf(
        "title" to "직원 현황",
        "employees" to employees
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_sheet_template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("multi_sheet_output.xlsx").writeBytes(bytes)
    }
}
```

---

## 6. 대용량 데이터 처리

### 권장 설정

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path

fun main() {
    // 대용량 데이터용 설정
    val config = TbegConfig(
        streamingMode = StreamingMode.ENABLED,  // 스트리밍 모드 활성화
        progressReportInterval = 1000           // 1000행마다 진행률 보고
    )

    // 데이터 개수 (DB COUNT 쿼리로 조회)
    val dataCount = 1_000_000

    // 지연 로딩으로 데이터 제공
    val provider = simpleDataProvider {
        value("title", "대용량 보고서")

        // count와 함께 지연 로딩 제공 (최적 성능)
        items("data", dataCount) {
            // 100만 건 데이터 시뮬레이션
            (1..dataCount).asSequence().map {
                mapOf("id" to it, "value" to it * 10)
            }.iterator()
        }
    }

    ExcelGenerator(config).use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val path = generator.generateToFile(
            template = template,
            dataProvider = provider,
            outputDir = Path.of("./output"),
            baseFileName = "large_report"
        )

        println("파일 생성됨: $path")
    }
}
```

---

## 7. 다중 반복 영역

한 시트에 여러 개의 반복 영역을 사용할 수 있습니다.

### 템플릿 (multi_repeat.xlsx)

|   | A                                | B             | C | D                                   | E              |
|---|----------------------------------|---------------|---|-------------------------------------|----------------|
| 1 | ${repeat(employees, A3:B3, emp)} |               |   | ${repeat(departments, D3:E3, dept)} |                |
| 2 | 이름                               | 연봉            |   | 부서명                                 | 예산             |
| 3 | ${emp.name}                      | ${emp.salary} |   | ${dept.name}                        | ${dept.budget} |

### Kotlin 코드 (Map 방식)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class Employee(val name: String, val salary: Int)
data class Department(val name: String, val budget: Int)

fun main() {
    val data = mapOf(
        "employees" to listOf(
            Employee("황용호", 8000),
            Employee("한용호", 6500),
            Employee("홍용호", 4500)
        ),
        "departments" to listOf(
            Department("공통플랫폼팀", 50000),
            Department("IT전략기획팀", 30000)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Kotlin 코드 (simpleDataProvider DSL - 지연 로딩)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider

data class Employee(val name: String, val salary: Int)
data class Department(val name: String, val budget: Int)

fun main() {
    // 각 컬렉션의 count 조회
    val employeeCount = 3   // employeeRepository.count().toInt()
    val departmentCount = 2 // departmentRepository.count().toInt()

    val provider = simpleDataProvider {
        // 첫 번째 컬렉션: 직원
        items("employees", employeeCount) {
            // employeeRepository.findAll().iterator()
            listOf(
                Employee("황용호", 8000),
                Employee("한용호", 6500),
                Employee("홍용호", 4500)
            ).iterator()
        }

        // 두 번째 컬렉션: 부서
        items("departments", departmentCount) {
            // departmentRepository.findAll().iterator()
            listOf(
                Department("공통플랫폼팀", 50000),
                Department("IT전략기획팀", 30000)
            ).iterator()
        }
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/multi_repeat.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### 결과

|   | A    | B     | C | D        | E      |
|---|------|-------|---|----------|--------|
| 1 |      |       |   |          |        |
| 2 | 이름   | 연봉    |   | 부서명      | 예산     |
| 3 | 황용호  | 8,000 |   | 공통플랫폼팀   | 50,000 |
| 4 | 한용호  | 6,500 |   | IT전략기획팀  | 30,000 |
| 5 | 홍용호  | 4,500 |   |          |        |

> [!NOTE]
> 각 repeat 영역은 독립적으로 확장됩니다. 위 예시에서 직원은 3명, 부서는 2개이므로 각각 다른 행 수만큼 확장됩니다.

> [!IMPORTANT]
> 반복 영역은 2D 공간에서 겹치면 안 됩니다.

---

## 8. 오른쪽 방향 반복

### 템플릿 (right_repeat.xlsx)

|   | A                                       | B             |
|---|-----------------------------------------|---------------|
| 1 | ${repeat(months, B1:B2, m, RIGHT)}      | ${m.month}월   |
| 2 |                                         | ${m.sales}    |

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator

data class MonthData(val month: Int, val sales: Int)

fun main() {
    val data = mapOf(
        "months" to listOf(
            MonthData(1, 1000),
            MonthData(2, 1500),
            MonthData(3, 2000),
            MonthData(4, 1800)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/right_repeat.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### 결과

|   | A  | B      | C      | D      | E      |
|---|----|--------|--------|--------|--------|
| 1 |    | 1월     | 2월     | 3월     | 4월     |
| 2 |    | 1,000  | 1,500  | 2,000  | 1,800  |

---

## 9. 빈 컬렉션 처리

컬렉션이 비어있을 때 "데이터가 없습니다" 같은 안내 메시지를 표시할 수 있습니다.

### 템플릿 (empty_collection.xlsx)

|   | A                                              | B               | C             |
|---|------------------------------------------------|-----------------|---------------|
| 1 | 직원 현황                                          |                 |               |
| 2 | ${repeat(employees, A4:C4, emp, DOWN, A7:C7)}  |                 |               |
| 3 | 이름                                             | 직급              | 연봉            |
| 4 | ${emp.name}                                    | ${emp.position} | ${emp.salary} |
| 5 |                                                |                 |               |
| 6 |                                                |                 |               |
| 7 | 조회된 직원이 없습니다.                                  |                 |               |

- **A2**: repeat 마커에 `empty` 파라미터로 `A7:C7` 지정
- **A7:C7**: 빈 컬렉션일 때 표시할 내용 (병합 셀 가능)

### Kotlin 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    // 빈 컬렉션
    val provider = simpleDataProvider {
        items("employees", emptyList<Employee>())
    }

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/empty_collection.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, provider)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### 결과 (데이터가 있는 경우)

|   | A    | B    | C     |
|---|------|------|-------|
| 1 | 직원 현황 |      |       |
| 2 |      |      |       |
| 3 | 이름   | 직급   | 연봉    |
| 4 | 황용호  | 부장   | 8,000 |
| 5 | 한용호  | 과장   | 6,500 |

- 7행의 안내 메시지는 결과에서 제거됨

### 결과 (데이터가 없는 경우)

|   | A              | B    | C    |
|---|----------------|------|------|
| 1 | 직원 현황          |      |      |
| 2 |                |      |      |
| 3 | 이름             | 직급   | 연봉   |
| 4 | 조회된 직원이 없습니다. |      |      |

- 반복 영역에 `empty` 범위의 내용이 표시됨
- `empty` 범위가 단일 셀이면 반복 영역 전체를 병합하여 표시

### 명시적 파라미터 형식

```
${repeat(collection=employees, range=A4:C4, var=emp, direction=DOWN, empty=A7:C7)}
```

### 수식 형식

```
=TBEG_REPEAT(collection=employees, range=A4:C4, var=emp, direction=DOWN, empty=A7:C7)
```

> [!NOTE]
> `empty` 범위는 반복 영역과 다른 위치에 있어야 합니다. 같은 시트의 다른 영역 또는 다른 시트에서 참조할 수 있습니다.

---

## 10. 다국어 지원 (I18N)

TBEG의 변수 치환을 활용하면 별도의 I18N 전용 기능 없이 다국어 보고서를 생성할 수 있습니다.

### 템플릿 (i18n_template.xlsx)

|   | A                                | B               | C             |
|---|----------------------------------|-----------------|---------------|
| 1 | ${label.title}                   |                 |               |
| 2 | ${repeat(employees, A4:C4, emp)} |                 |               |
| 3 | ${label.name}                    | ${label.position} | ${label.salary} |
| 4 | ${emp.name}                      | ${emp.position} | ${emp.salary} |

하나의 템플릿으로 모든 언어를 지원합니다. 고정 텍스트 대신 `${label.*}` 변수를 사용합니다.

### 리소스 번들 준비

**messages_ko.properties**
```properties
report.title=직원 현황 보고서
label.name=이름
label.position=직급
label.salary=연봉
```

**messages_en.properties**
```properties
report.title=Employee Report
label.name=Name
label.position=Position
label.salary=Salary
```

### Kotlin 코드 (ResourceBundle)

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.util.Locale
import java.util.ResourceBundle

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val locale = Locale.KOREAN  // 또는 Locale.ENGLISH
    val bundle = ResourceBundle.getBundle("messages", locale)

    val data = mapOf(
        "label" to mapOf(
            "title" to bundle.getString("report.title"),
            "name" to bundle.getString("label.name"),
            "position" to bundle.getString("label.position"),
            "salary" to bundle.getString("label.salary")
        ),
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("한용호", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = object {}.javaClass.getResourceAsStream("/templates/i18n_template.xlsx")
            ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

        val bytes = generator.generate(template, data)
        File("report_${locale.language}.xlsx").writeBytes(bytes)
    }
}
```

### Kotlin 코드 (Spring MessageSource)

```kotlin
import io.github.jogakdal.tbeg.simpleDataProvider
import org.springframework.context.MessageSource
import java.util.Locale

fun buildI18nProvider(messageSource: MessageSource, locale: Locale) = simpleDataProvider {
    // 라벨 변수를 MessageSource에서 일괄 로드
    val keys = listOf("report.title", "label.name", "label.position", "label.salary")
    value("label", keys.associate { key ->
        key.substringAfter(".") to messageSource.getMessage(key, null, locale)
    })

    items("employees") {
        // DB 조회 등
        emptyList<Any>().iterator()
    }
}
```

### 결과 (한국어)

|   | A         | B    | C     |
|---|-----------|------|-------|
| 1 | 직원 현황 보고서 |      |       |
| 2 |           |      |       |
| 3 | 이름        | 직급   | 연봉    |
| 4 | 황용호       | 부장   | 8,000 |
| 5 | 한용호       | 과장   | 6,500 |

### 결과 (영어)

|   | A               | B        | C      |
|---|-----------------|----------|--------|
| 1 | Employee Report |          |        |
| 2 |                 |          |        |
| 3 | Name            | Position | Salary |
| 4 | 황용호             | 부장       | 8,000  |
| 5 | 한용호             | 과장       | 6,500  |

> [!TIP]
> TBEG은 I18N을 위한 별도 문법을 제공하지 않습니다. Java/Spring의 `ResourceBundle`이나 `MessageSource`로 번역을 처리하고, 그 결과를 변수로 전달하면 됩니다. 하나의 템플릿으로 모든 언어를 지원할 수 있습니다.

---

## 11. 종합 예제 — 분기 매출 실적 보고서

변수 치환, 이미지 삽입, 반복 데이터 확장, 수식 자동 조정, 조건부 서식 복제, 차트 데이터 반영을 하나의 보고서에서 활용하는 예제입니다.

### 템플릿

> [!TIP]
> [템플릿 다운로드 (rich_sample_template.xlsx)](../../src/test/resources/templates/rich_sample_template.xlsx)

![템플릿](../../src/main/resources/sample/screenshot_template.png)

템플릿 구성:
- **변수 마커**: `${reportTitle}`, `${period}`, `${author}`, `${reportDate}`
- **이미지 마커**: `${image(logo)}`, `${image(ci)}`
- **반복 마커**: `${repeat(depts, B7:G7, d)}` (부서별 실적), `${repeat(products, I7:K7, p)}` (제품 카테고리)
- **수식**: SUM, AVERAGE (합계/평균 행), 셀 간 계산 (Profit = Revenue - Cost, Achievement = Revenue / Target)
- **조건부 서식**: Achievement ≥ 100% → 초록, < 100% → 빨강 / Share ≥ 30% → 초록, < 30% → 빨강
- **차트**: 부서별 Revenue/Cost/Profit 막대 차트, 제품 카테고리 파이 차트

### 코드

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.io.File
import java.nio.file.Path
import java.time.LocalDate

data class DeptResult(val deptName: String, val revenue: Long, val cost: Long, val target: Long)
data class ProductCategory(val category: String, val revenue: Long)

fun main() {
    val data = simpleDataProvider {
        value("reportTitle", "Q1 2026 Sales Performance Report")
        value("period", "Jan 2026 ~ Mar 2026")
        value("author", "Yongho Hwang")
        value("reportDate", LocalDate.now().toString())
        image("logo", File("hunet_logo.png").readBytes())
        image("ci", File("hunet_ci.png").readBytes())

        items("depts") {
            listOf(
                DeptResult("Common Platform", 52000, 31000, 50000),
                DeptResult("IT Strategy",     38000, 22000, 40000),
                DeptResult("HR Management",   28000, 19000, 30000),
                DeptResult("Education Biz",   95000, 61000, 90000),
                DeptResult("Content Dev",     42000, 28000, 45000),
            ).iterator()
        }

        items("products") {
            listOf(
                ProductCategory("Online Courses", 128000),
                ProductCategory("Consulting", 67000),
                ProductCategory("Certification", 45000),
                ProductCategory("Contents License", 15000),
            ).iterator()
        }
    }

    ExcelGenerator().use { generator ->
        val template = File("rich_sample_template.xlsx").inputStream()
        generator.generateToFile(template, data, Path.of("output"), "quarterly_report")
    }
}
```

### 결과

![결과](../../src/main/resources/sample/screenshot_result.png)

TBEG이 자동으로 처리한 항목:
- **변수 치환** — 제목, 기간, 작성자, 날짜
- **이미지 삽입** — 로고, CI
- **반복 데이터 확장** — 부서 5행, 제품 4행으로 확장
- **수식 범위 자동 조정** — `SUM(C7:C7)` → `SUM(C7:C11)`, `AVERAGE(C7:C7)` → `AVERAGE(C7:C11)`
- **조건부 서식 복제** — 달성률/점유율 색상이 모든 행에 적용
- **차트 데이터 범위 반영** — 차트가 확장된 데이터 범위를 참조

---

## 다음 단계

- [Spring Boot 예제](./spring-boot-examples.md) - Spring Boot 환경 통합
- [설정 옵션 레퍼런스](../reference/configuration.md) - 상세 설정
- [API 레퍼런스](../reference/api-reference.md) - API 상세
