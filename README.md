# data-processors-with-excel

Excel 데이터 처리를 위한 Kotlin 라이브러리 모음입니다.

## 모듈

### TBEG (Template Based Excel Generator)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 라이브러리입니다.

**주요 기능:**
- 템플릿 기반 Excel 보고서 생성
- `${repeat(...)}` 문법으로 리스트 데이터 확장
- 스트리밍 모드(SXSSF)로 대용량 처리
- 차트, 수식, 조건부 서식 자동 조정
- Spring Boot Auto-configuration 지원

### 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.0.0")
}
```

### 빠른 시작

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider

val generator = ExcelGenerator()
val output = generator.generate(
    templateStream = File("template.xlsx").inputStream(),
    dataProvider = simpleDataProvider {
        value("title", "월간 보고서")
        repeat("employees", listOf(
            mapOf("name" to "홍길동", "dept" to "개발팀"),
            mapOf("name" to "김철수", "dept" to "기획팀")
        ))
    },
    baseFileName = "report"
)
```

상세 사용법은 [TBEG 문서](modules/tbeg/README.md)를 참조하세요.

## 요구사항

- Kotlin 2.1.20+
- Java 21+

## 라이선스

[Apache License 2.0](LICENSE)
