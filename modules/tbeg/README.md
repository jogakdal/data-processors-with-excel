# TBEG (Template Based Excel Generator)

Excel 템플릿에 데이터를 바인딩하여 보고서를 생성하는 라이브러리입니다.

## 주요 기능

- **템플릿 기반 생성**: Excel 템플릿에 데이터를 바인딩하여 보고서 생성
- **반복 데이터 처리**: `${repeat(...)}` 문법으로 리스트 데이터를 행/열로 확장
- **변수 치환**: `${변수명}` 문법으로 셀, 차트, 도형, 머리글/바닥글, 수식 인자 등에 값 바인딩
- **이미지 삽입**: 템플릿 셀에 동적 이미지 삽입
- **파일 암호화**: 생성된 Excel 파일에 열기 암호 설정
- **문서 메타데이터**: 제목, 작성자, 키워드 등 문서 속성 설정
- **비동기 처리**: 대용량 데이터를 백그라운드에서 처리
- **지연 로딩**: DataProvider를 통한 메모리 효율적 데이터 처리

## 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.jogakdal:tbeg:1.0.0")
}
```

## 빠른 시작

### Kotlin

```kotlin
import io.github.jogakdal.tbeg.ExcelGenerator
import java.io.File

data class Employee(val name: String, val position: String, val salary: Int)

fun main() {
    val data = mapOf(
        "title" to "직원 현황",
        "employees" to listOf(
            Employee("황용호", "부장", 8000),
            Employee("한용호", "과장", 6500)
        )
    )

    ExcelGenerator().use { generator ->
        val template = File("template.xlsx").inputStream()
        val bytes = generator.generate(template, data)
        File("output.xlsx").writeBytes(bytes)
    }
}
```

### Spring Boot

```kotlin
@Service
class ReportService(
    private val excelGenerator: ExcelGenerator,
    private val resourceLoader: ResourceLoader
) {
    fun generateReport(): ByteArray {
        val template = resourceLoader.getResource("classpath:templates/report.xlsx")
        val data = mapOf("title" to "보고서", "items" to listOf(...))
        return excelGenerator.generate(template.inputStream, data)
    }
}
```

Spring Boot 환경에서는 `ExcelGenerator`가 자동으로 Bean으로 등록됩니다.

## 템플릿 문법

### 변수 치환
```
${title}
${employee.name}
```

### 반복 데이터
```
${repeat(employees, A3:C3, emp, DOWN)}
```

### 이미지
```
${image(logo)}
${image(logo, B5)}
${image(logo, B5, 100:50)}
```

## 스트리밍 모드

메모리 효율성과 처리 속도를 개선하는 모드입니다. 기본값(`enabled`)을 권장합니다.

| 모드              | 설명                   |
|-----------------|----------------------|
| `enabled` (기본값) | 메모리 효율적, 빠른 처리 속도    |
| `disabled`      | 모든 행을 메모리에 유지(장점 없음) |

### 성능 벤치마크

**테스트 환경**: Java 21, macOS, 3개 컬럼 repeat + SUM 수식

| 데이터 크기   | disabled | enabled | 속도 향상    |
|----------|----------|---------|----------|
| 1,000행   | 172ms    | 147ms   | 1.2배     |
| 10,000행  | 1,801ms  | 663ms   | **2.7배** |
| 30,000행  | -        | 1,057ms | -        |
| 50,000행  | -        | 1,202ms | -        |
| 100,000행 | -        | 3,154ms | -        |

### 타 라이브러리와 비교 (30,000행)

| 라이브러리    | 소요 시간    | 비고                                                          |
|----------|----------|-------------------------------------------------------------|
| **TBEG** | **1.1초** | 스트리밍 모드                                                     |
| JXLS     | 5.2초     | [벤치마크 출처](https://github.com/jxlsteam/jxls/discussions/203) |

> 벤치마크 실행: `./gradlew :tbeg:runBenchmark`

## 설정 (application.yml)

```yaml
tbeg:
  streaming-mode: enabled   # enabled, disabled
  file-naming-mode: timestamp
  preserve-template-layout: true
```

## 프로젝트 구조

```
src/main/kotlin/io/github/jogakdal/tbeg/
├── ExcelGenerator.kt                       # 메인 진입점 (Public API)
├── ExcelDataProvider.kt                    # 데이터 제공 인터페이스
├── SimpleDataProvider.kt                   # 간단한 DataProvider 구현
├── TbegConfig.kt                           # 설정 클래스
├── DocumentMetadata.kt                     # 문서 메타데이터
├── Enums.kt                                # StreamingMode, FileNamingMode 등 열거형
│
├── async/                                  # 비동기 처리
│   ├── ExcelGenerationListener.kt          # 생성 이벤트 리스너
│   ├── GenerationJob.kt                    # 비동기 작업 핸들
│   ├── GenerationResult.kt                 # 생성 결과
│   └── ProgressInfo.kt                     # 진행률 정보
│
├── engine/                                 # 내부 엔진 (internal)
│   ├── core/                               # 핵심 프로세서
│   │   ├── ChartProcessor.kt               # 차트 추출/복원
│   │   ├── PivotTableProcessor.kt          # 피벗 테이블 처리
│   │   ├── XmlVariableProcessor.kt         # XML 내 변수 치환
│   │   └── ExcelUtils.kt                   # 유틸리티 함수
│   │
│   ├── pipeline/                           # 처리 파이프라인
│   │   ├── TbegPipeline.kt                 # 파이프라인 정의
│   │   ├── ExcelProcessor.kt               # 프로세서 인터페이스
│   │   ├── ProcessingContext.kt            # 처리 컨텍스트
│   │   └── processors/                     # 개별 프로세서
│   │       ├── ChartExtractProcessor.kt
│   │       ├── ChartRestoreProcessor.kt
│   │       ├── MetadataProcessor.kt
│   │       ├── NumberFormatProcessor.kt
│   │       ├── PivotExtractProcessor.kt
│   │       ├── PivotRecreateProcessor.kt
│   │       ├── TemplateRenderProcessor.kt
│   │       └── XmlVariableReplaceProcessor.kt
│   │
│   └── rendering/                          # 렌더링 전략
│       ├── RenderingStrategy.kt            # 렌더링 전략 인터페이스
│       ├── AbstractRenderingStrategy.kt    # 공통 로직
│       ├── XssfRenderingStrategy.kt        # XSSF (비스트리밍)
│       ├── SxssfRenderingStrategy.kt       # SXSSF (스트리밍)
│       ├── TemplateRenderingEngine.kt      # 렌더링 엔진
│       ├── TemplateAnalyzer.kt             # 템플릿 분석기
│       ├── WorkbookSpec.kt                 # 워크북/시트/셀 명세
│       ├── PositionCalculator.kt           # 반복 확장 위치 계산
│       ├── StreamingDataSource.kt          # 스트리밍 데이터 소스
│       ├── ImageInserter.kt                # 이미지 삽입
│       ├── FormulaAdjuster.kt              # 수식 조정
│       ├── RepeatExpansionProcessor.kt     # 반복 영역 확장
│       ├── SheetLayoutApplier.kt           # 레이아웃 적용
│       └── parser/                         # 마커 파서 (내부)
│           ├── MarkerDefinition.kt         # 마커 정의 및 파라미터 스펙
│           ├── UnifiedMarkerParser.kt      # 통합 마커 파서
│           ├── ParameterParser.kt          # 파라미터 값 파서
│           └── ParsedMarker.kt             # 파싱된 마커 결과
│
├── exception/                              # 예외 클래스
│   ├── TemplateProcessingException.kt
│   ├── MissingTemplateDataException.kt
│   └── FormulaExpansionException.kt
│
└── spring/                                 # Spring Boot 통합
    ├── TbegAutoConfiguration.kt            # 자동 설정
    └── TbegProperties.kt                   # 설정 속성
```

### 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                     ExcelGenerator                          │
│                      (Public API)                           │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      TbegPipeline                           │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐     │
│  │ChartExtract  │ → │PivotExtract  │ → │TemplateRender│     │
│  └──────────────┘   └──────────────┘   └──────┬───────┘     │
│                                               │             │
│                                               ▼             │
│                                    ┌──────────────────┐     │
│                                    │ RenderingEngine  │     │
│                                    │ ┌──────┬───────┐ │     │
│                                    │ │ XSSF │ SXSSF │ │     │
│                                    │ └──────┴───────┘ │     │
│                                    └──────────────────┘     │
│                                               │             │
│  ┌──────────────┐   ┌──────────────┐   ┌──────▼───────┐     │
│  │  Metadata    │ ← │ ChartRestore │ ← │ NumberFormat │     │
│  └──────────────┘   └──────────────┘   └──────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**파이프라인 처리 순서:**
1. **ChartExtract** - 차트 정보 추출 및 임시 제거
2. **PivotExtract** - 피벗 테이블 정보 추출
3. **TemplateRender** - 템플릿 렌더링 (XSSF/SXSSF 전략 선택)
4. **NumberFormat** - 숫자 서식 자동 적용
5. **XmlVariableReplace** - XML 내 변수 치환 (차트, 머리글/바닥글 등)
6. **PivotRecreate** - 피벗 테이블 재생성
7. **ChartRestore** - 차트 복원 및 데이터 범위 조정
8. **Metadata** - 문서 메타데이터 적용

## 문서

상세 문서는 아래 링크를 참고하세요:

- [사용자 가이드](./manual/user-guide.md)
- [템플릿 문법 레퍼런스](./manual/reference/template-syntax.md)
- [API 레퍼런스](./manual/reference/api-reference.md)
- [설정 옵션 레퍼런스](./manual/reference/configuration.md)
- [기본 예제](./manual/examples/basic-examples.md)
- [고급 예제](./manual/examples/advanced-examples.md)
- [Spring Boot 예제](./manual/examples/spring-boot-examples.md)
- [유지보수 개발자 가이드](./manual/developer-guide.md)

## 샘플 실행

샘플은 `src/test/resources/templates/template.xlsx` 템플릿을 사용합니다.

```bash
# Kotlin 샘플
./gradlew :tbeg:runSample
# 결과: build/samples/

# Java 샘플
./gradlew :tbeg:runJavaSample
# 결과: build/samples-java/

# Spring Boot 샘플
./gradlew :tbeg:runSpringBootSample
# 결과: build/samples-spring/
```
