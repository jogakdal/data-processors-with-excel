# TBEG 설정 옵션 레퍼런스

## 목차
1. [TbegConfig](#1-tbegconfig)
2. [Spring Boot 프로퍼티](#2-spring-boot-프로퍼티)
3. [Enum 타입](#3-enum-타입)
4. [프리셋 설정](#4-프리셋-설정)

---

## 1. TbegConfig

### 패키지
```kotlin
io.github.jogakdal.tbeg.TbegConfig
```

### 전체 옵션

| 옵션                        | 타입                    | 기본값                 | 설명                       |
|---------------------------|-----------------------|---------------------|--------------------------|
| ~~`streamingMode`~~       | `StreamingMode`       | `ENABLED`           | **deprecated** -- 값이 무시됩니다 |
| `fileNamingMode`          | `FileNamingMode`      | `TIMESTAMP`         | 파일명 생성 모드                |
| `timestampFormat`         | `String`              | `"yyyyMMdd_HHmmss"` | 파일명 타임스탬프 형식             |
| `fileConflictPolicy`      | `FileConflictPolicy`  | `SEQUENCE`          | 파일명 충돌 시 처리 정책           |
| `progressReportInterval`  | `Int`                 | `100`               | 진행률 콜백 호출 간격 (행 수)       |
| `preserveTemplateLayout`  | `Boolean`             | `true`              | 템플릿 레이아웃(열 폭, 행 높이) 보존   |
| `pivotIntegerFormatIndex` | `Short`               | `3`                 | 정수 숫자 서식 인덱스 (`#,##0`)   |
| `pivotDecimalFormatIndex` | `Short`               | `4`                 | 소수 숫자 서식 인덱스 (`#,##0.00`) |
| `missingDataBehavior`     | `MissingDataBehavior` | `WARN`              | 데이터 누락 시 동작              |
| `imageUrlCacheTtlSeconds` | `Long`                | `0`                 | 이미지 URL 캐시 TTL (초, 0=캐싱 안 함) |
| `unmarkedHidePolicy`      | `UnmarkedHidePolicy`  | `WARN_AND_HIDE`     | hideable 마커 없는 필드의 숨김 요청 시 처리 정책 |

---

### 옵션 상세

#### streamingMode (deprecated)

> **deprecated**: 1.2.0부터 항상 스트리밍 모드로 동작합니다. 이 옵션은 무시됩니다.

```kotlin
// deprecated -- 설정할 필요 없음
// TbegConfig(streamingMode = StreamingMode.ENABLED)
```

#### fileNamingMode

`generateToFile()` 사용 시 파일명 생성 모드입니다.

| 값           | 예시                                  |
|-------------|-------------------------------------|
| `NONE`      | `report.xlsx`                       |
| `TIMESTAMP` | `report_20260115_143052.xlsx` (기본값) |

```kotlin
TbegConfig(fileNamingMode = FileNamingMode.NONE)
```

#### timestampFormat

`fileNamingMode = TIMESTAMP`일 때 사용되는 타임스탬프 형식입니다.

```kotlin
TbegConfig(
    fileNamingMode = FileNamingMode.TIMESTAMP,
    timestampFormat = "yyyy-MM-dd_HH-mm"  // report_2026-01-15_14-30.xlsx
)
```

#### fileConflictPolicy

동일한 파일명이 이미 존재할 때 처리 정책입니다.

| 값          | 동작                                                |
|------------|---------------------------------------------------|
| `ERROR`    | `FileAlreadyExistsException` 발생                   |
| `SEQUENCE` | 시퀀스 번호 추가: `report_1.xlsx`, `report_2.xlsx` (기본값) |

```kotlin
TbegConfig(fileConflictPolicy = FileConflictPolicy.ERROR)
```

#### progressReportInterval

비동기 작업에서 `onProgress` 콜백이 호출되는 간격 (행 수)입니다.

```kotlin
TbegConfig(progressReportInterval = 500)  // 500행마다 콜백
```

#### preserveTemplateLayout

`${repeat(...)}`으로 행이 확장될 때 원본 템플릿의 열 폭과 행 높이를 보존할지 여부입니다.

```kotlin
TbegConfig(preserveTemplateLayout = true)
```

#### pivotIntegerFormatIndex / pivotDecimalFormatIndex

자동 숫자 서식에 사용할 Excel 내장 포맷 인덱스입니다. 라이브러리가 생성한 숫자 셀의 표시 형식이 "일반"인 경우 자동으로 적용됩니다.

| 옵션                        | 기본값 | 형식           | 예시 출력      |
|---------------------------|-----|--------------|------------|
| `pivotIntegerFormatIndex` | `3` | `#,##0`      | `1,234`    |
| `pivotDecimalFormatIndex` | `4` | `#,##0.00`   | `1,234.56` |

```kotlin
TbegConfig(
    pivotIntegerFormatIndex = 3,   // 정수: #,##0
    pivotDecimalFormatIndex = 4    // 소수: #,##0.00
)
```

> [!NOTE]
> Excel 내장 포맷 인덱스는 [Microsoft 문서](https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.numberingformat)를 참조하세요.

#### missingDataBehavior

템플릿에 정의된 변수/컬렉션에 대응하는 데이터가 없을 때의 동작입니다.

| 값       | 동작                                   |
|---------|--------------------------------------|
| `WARN`  | 경고 로그를 출력하고 마커를 그대로 유지 (기본값)         |
| `THROW` | `MissingTemplateDataException` 예외 발생 |

```kotlin
TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)
```

#### imageUrlCacheTtlSeconds

이미지 데이터로 URL을 지정했을 때, 다운로드 결과를 호출 간에 캐싱하는 TTL(초)입니다.

| 값 | 동작 |
|---|------|
| `0` | 호출 간 캐싱 안 함 (기본값). 동일 URL이라도 매번 다운로드 |
| `> 0` | 지정 시간 동안 캐싱. 같은 URL을 여러 번 생성하는 경우 효과적 |

> [!NOTE]
> 단일 `generate()` 호출 내에서는 TTL 설정과 무관하게 같은 URL을 중복 다운로드하지 않습니다.

> [!NOTE]
> 캐시는 JVM 인스턴스의 메모리에 저장됩니다. 멀티 인스턴스 환경에서는 각 인스턴스가 독립적인 캐시를 유지합니다.

```kotlin
TbegConfig(imageUrlCacheTtlSeconds = 60)  // 60초간 캐싱
```

#### unmarkedHidePolicy

`hideFields`에 지정된 필드가 템플릿에 hideable 마커 없이 일반 필드(`${item.field}`)로만 존재할 때의 처리 정책입니다.

| 값               | 동작                                              |
|-----------------|---------------------------------------------------|
| `WARN_AND_HIDE` | 경고 로그를 출력하고 해당 셀만 숨김 (기본값)           |
| `ERROR`         | `MarkerValidationException` 예외 발생               |

```kotlin
TbegConfig(unmarkedHidePolicy = UnmarkedHidePolicy.ERROR)
```

> [!NOTE]
> hideable 마커가 있는 필드는 이 정책과 무관하게 정상적으로 처리됩니다. 이 설정은 마커 없이 hideFields에만 지정된 필드에 대한 동작을 제어합니다.

---

### 생성 방법

#### Kotlin - 직접 생성

```kotlin
val config = TbegConfig(
    progressReportInterval = 500
)
val generator = ExcelGenerator(config)
```

#### Java - Builder 사용

```java
TbegConfig config = TbegConfig.builder()
    .progressReportInterval(500)
    .build();

ExcelGenerator generator = new ExcelGenerator(config);
```

---

## 2. Spring Boot 프로퍼티

### 패키지
```kotlin
io.github.jogakdal.tbeg.spring.TbegProperties
```

### application.yml 예시

```yaml
tbeg:
  # streaming-mode: deprecated (1.2.0부터 값이 무시됩니다)
  # streaming-mode: enabled

  # 파일명 생성 모드: none, timestamp
  file-naming-mode: timestamp

  # 파일명 타임스탬프 형식
  timestamp-format: yyyyMMdd_HHmmss

  # 파일명 충돌 정책: error, sequence
  file-conflict-policy: sequence

  # 진행률 콜백 호출 간격
  progress-report-interval: 100

  # 템플릿 레이아웃 보존
  preserve-template-layout: true

  # 정수 숫자 서식 인덱스 (기본: 3, #,##0)
  pivot-integer-format-index: 3

  # 소수 숫자 서식 인덱스 (기본: 4, #,##0.00)
  pivot-decimal-format-index: 4

  # 데이터 누락 시 동작: warn, throw
  missing-data-behavior: warn

  # 이미지 URL 캐시 TTL (초, 0=캐싱 안 함)
  image-url-cache-ttl-seconds: 0

  # hideable 마커 없는 필드의 숨김 요청 시 처리 정책: warn-and-hide, error
  unmarked-hide-policy: warn-and-hide
```

### application.properties 예시

```properties
tbeg.file-naming-mode=timestamp
tbeg.timestamp-format=yyyyMMdd_HHmmss
tbeg.file-conflict-policy=sequence
tbeg.progress-report-interval=100
tbeg.preserve-template-layout=true
tbeg.pivot-integer-format-index=3
tbeg.pivot-decimal-format-index=4
tbeg.missing-data-behavior=warn
tbeg.image-url-cache-ttl-seconds=0
tbeg.unmarked-hide-policy=warn-and-hide
```

### 프로퍼티 매핑

| application.yml 키             | TbegConfig 속성    |
|-------------------------------|----------------------------|
| ~~`streaming-mode`~~          | ~~`streamingMode`~~ (deprecated) |
| `file-naming-mode`            | `fileNamingMode`           |
| `timestamp-format`            | `timestampFormat`          |
| `file-conflict-policy`        | `fileConflictPolicy`       |
| `progress-report-interval`    | `progressReportInterval`   |
| `preserve-template-layout`    | `preserveTemplateLayout`   |
| `pivot-integer-format-index`  | `pivotIntegerFormatIndex`  |
| `pivot-decimal-format-index`  | `pivotDecimalFormatIndex`  |
| `missing-data-behavior`       | `missingDataBehavior`      |
| `image-url-cache-ttl-seconds` | `imageUrlCacheTtlSeconds`  |
| `unmarked-hide-policy`        | `unmarkedHidePolicy`       |

---

## 3. Enum 타입

### StreamingMode (deprecated)

> **deprecated**: 1.2.0부터 항상 스트리밍 모드로 동작합니다. 이 enum은 향후 버전에서 제거될 예정입니다.

```kotlin
@Deprecated("항상 스트리밍 모드로 동작합니다")
enum class StreamingMode {
    DISABLED,
    ENABLED
}
```

### FileNamingMode

```kotlin
enum class FileNamingMode {
    NONE,      // 기본 파일명만 사용
    TIMESTAMP  // 타임스탬프 추가 (기본값)
}
```

| 값           | 결과 예시                         |
|-------------|-------------------------------|
| `NONE`      | `report.xlsx`                 |
| `TIMESTAMP` | `report_20260115_143052.xlsx` |

### FileConflictPolicy

```kotlin
enum class FileConflictPolicy {
    ERROR,     // 예외 발생
    SEQUENCE   // 시퀀스 번호 추가 (기본값)
}
```

| 값          | 동작                                                |
|------------|---------------------------------------------------|
| `ERROR`    | 파일 존재 시 `FileAlreadyExistsException` 발생           |
| `SEQUENCE` | `report.xlsx` -> `report_1.xlsx` -> `report_2.xlsx` |

### MissingDataBehavior

```kotlin
enum class MissingDataBehavior {
    WARN,   // 경고 로그 출력 (기본값)
    THROW   // 예외 발생
}
```

| 값       | 동작                                              |
|---------|-------------------------------------------------|
| `WARN`  | 경고 로그 출력 후 마커 유지, 디버깅에 유용                       |
| `THROW` | `MissingTemplateDataException` 발생, 데이터 무결성 중요 시 |

### HideMode

```kotlin
enum class HideMode {
    DELETE,  // 물리적 삭제 후 나머지 요소를 당김 (기본값)
    DIM      // 비활성화 스타일 적용 + 셀 값 제거
}
```

| 값       | 동작                                                        |
|---------|-----------------------------------------------------------|
| `DELETE` | 해당 영역을 삭제하고 나머지 요소를 당김. 수식, 병합 셀, 조건부 서식 자동 조정 |
| `DIM`    | 회색 배경(#D9D9D9) + 연한 글자색(#BFBFBF) 스타일 적용, 셀 값 제거. 필드 타이틀 등 repeat 밖 bundle 영역은 글자색만 변경 |

### UnmarkedHidePolicy

```kotlin
enum class UnmarkedHidePolicy {
    WARN_AND_HIDE,  // 경고 로그 출력 후 해당 셀만 숨김 (기본값)
    ERROR           // 예외 발생
}
```

| 값               | 동작                                                          |
|-----------------|-------------------------------------------------------------|
| `WARN_AND_HIDE` | 경고 로그를 출력하고 bundle 없는 hideable처럼 동작                  |
| `ERROR`         | `MarkerValidationException` 발생, 템플릿-데이터 간 불일치를 엄격하게 검증할 때 |

---

## 4. 프리셋 설정

### default()

기본 설정을 반환합니다.

```kotlin
val config = TbegConfig.default()
// 또는
val config = TbegConfig()
```

### forLargeData()

대용량 데이터 처리에 최적화된 설정입니다.

```kotlin
val config = TbegConfig.forLargeData()
// progressReportInterval = 500
```

### forSmallData() (deprecated)

> **deprecated**: 1.2.0부터 `default()`와 동일하게 동작합니다. `default()` 또는 `TbegConfig()`를 사용하세요.

```kotlin
// deprecated
val config = TbegConfig.forSmallData()

// 권장
val config = TbegConfig.default()
```

---

## 설정 예시

### 대용량 보고서 생성

```kotlin
val config = TbegConfig(
    progressReportInterval = 1000
)
```

### 파일 중복 방지 (오류 발생)

```kotlin
val config = TbegConfig(
    fileNamingMode = FileNamingMode.NONE,
    fileConflictPolicy = FileConflictPolicy.ERROR
)
```

### 커스텀 타임스탬프 형식

```kotlin
val config = TbegConfig(
    fileNamingMode = FileNamingMode.TIMESTAMP,
    timestampFormat = "yyyy-MM-dd"  // report_2026-01-15.xlsx
)
```

### 데이터 누락 시 예외 발생

```kotlin
val config = TbegConfig(
    missingDataBehavior = MissingDataBehavior.THROW
)
```

---

## 다음 단계

- [API 레퍼런스](./api-reference.md) - ExcelGenerator API 상세
- [사용자 가이드](../user-guide.md) - 전체 가이드
