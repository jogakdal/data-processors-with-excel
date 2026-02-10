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
| `streamingMode`           | `StreamingMode`       | `ENABLED`           | 스트리밍 모드 설정               |
| `fileNamingMode`          | `FileNamingMode`      | `TIMESTAMP`         | 파일명 생성 모드                |
| `timestampFormat`         | `String`              | `"yyyyMMdd_HHmmss"` | 파일명 타임스탬프 형식             |
| `fileConflictPolicy`      | `FileConflictPolicy`  | `SEQUENCE`          | 파일명 충돌 시 처리 정책           |
| `progressReportInterval`  | `Int`                 | `100`               | 진행률 콜백 호출 간격 (행 수)       |
| `preserveTemplateLayout`  | `Boolean`             | `true`              | 템플릿 레이아웃(열 폭, 행 높이) 보존   |
| `pivotIntegerFormatIndex` | `Short`               | `3`                 | 정수 숫자 서식 인덱스 (`#,##0`)   |
| `pivotDecimalFormatIndex` | `Short`               | `4`                 | 소수 숫자 서식 인덱스 (`#,##0.00`) |
| `missingDataBehavior`     | `MissingDataBehavior` | `WARN`              | 데이터 누락 시 동작              |

---

### 옵션 상세

#### streamingMode

스트리밍 모드를 설정합니다.

| 값          | 설명                               |
|------------|----------------------------------|
| `ENABLED`  | 메모리 효율적, 대용량 데이터에 최적화 (기본값)      |
| `DISABLED` | POI 원본 API 사용, shiftRows 기반 행 삽입 |

```kotlin
TbegConfig(streamingMode = StreamingMode.ENABLED)
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

> **참고**: Excel 내장 포맷 인덱스는 [Microsoft 문서](https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.numberingformat)를 참조하세요.

#### missingDataBehavior

템플릿에 정의된 변수/컬렉션에 대응하는 데이터가 없을 때의 동작입니다.

| 값       | 동작                                   |
|---------|--------------------------------------|
| `WARN`  | 경고 로그를 출력하고 마커를 그대로 유지 (기본값)         |
| `THROW` | `MissingTemplateDataException` 예외 발생 |

```kotlin
TbegConfig(missingDataBehavior = MissingDataBehavior.THROW)
```

---

### 생성 방법

#### Kotlin - 직접 생성

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,
    progressReportInterval = 500
)
val generator = ExcelGenerator(config)
```

#### Java - Builder 사용

```java
TbegConfig config = TbegConfig.builder()
    .streamingMode(StreamingMode.ENABLED)
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
hunet:
  tbeg:
    # 스트리밍 모드: enabled, disabled
    streaming-mode: enabled

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
```

### 프로퍼티 매핑

| application.yml 키             | TbegConfig 속성    |
|-------------------------------|----------------------------|
| `streaming-mode`              | `streamingMode`            |
| `file-naming-mode`            | `fileNamingMode`           |
| `timestamp-format`            | `timestampFormat`          |
| `file-conflict-policy`        | `fileConflictPolicy`       |
| `progress-report-interval`    | `progressReportInterval`   |
| `preserve-template-layout`    | `preserveTemplateLayout`   |
| `pivot-integer-format-index`  | `pivotIntegerFormatIndex`  |
| `pivot-decimal-format-index`  | `pivotDecimalFormatIndex`  |
| `missing-data-behavior`       | `missingDataBehavior`      |

---

## 3. Enum 타입

### StreamingMode

```kotlin
enum class StreamingMode {
    DISABLED,  // POI 원본 API 사용
    ENABLED    // 메모리 효율적 (기본값)
}
```

| 값          | 권장 상황                          |
|------------|--------------------------------|
| `DISABLED` | 1,000행 이하의 소량 데이터              |
| `ENABLED`  | 10,000행 이상의 대용량 데이터, 메모리 제약 환경 |

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
// streamingMode = ENABLED
// progressReportInterval = 500
```

### forSmallData()

소량 데이터 처리에 최적화된 설정입니다.

```kotlin
val config = TbegConfig.forSmallData()
// streamingMode = DISABLED
```

---

## 설정 예시

### 대용량 보고서 생성

```kotlin
val config = TbegConfig(
    streamingMode = StreamingMode.ENABLED,
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
