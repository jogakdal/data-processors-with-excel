# TBEG (Template-Based Excel Generator)

템플릿 기반 Excel 파일 생성 라이브러리

> 프로젝트 소개와 빠른 시작은 [README](../README.ko.md)를 참조하세요.

---

## 어디서부터 시작할까요?

### 처음 사용합니다
1. [README](../README.ko.md#빠른-시작)에서 첫 Excel을 생성해 보세요
2. [사용자 가이드](./user-guide.md)에서 핵심 개념을 학습하세요
3. [기본 예제](./examples/basic-examples.md)에서 다양한 사용 패턴을 확인하세요

### Spring Boot에 도입하려고 합니다
1. [Spring Boot 예제](./examples/spring-boot-examples.md)에서 통합 방법을 확인하세요
2. [설정 옵션](./reference/configuration.md)에서 `application.yml` 설정을 확인하세요
3. [고급 예제 - JPA 연동](./examples/advanced-examples.md#13-jpaspring-data-연동)을 참조하세요

### 대용량 데이터를 처리해야 합니다
1. [사용자 가이드 - 대용량 데이터 처리](./user-guide.md#5-대용량-데이터-처리)를 참조하세요
2. [고급 예제 - DataProvider](./examples/advanced-examples.md#1-dataprovider-활용)에서 지연 로딩 패턴을 확인하세요
3. [모범 사례 - 성능 최적화](./best-practices.md#2-성능-최적화)에서 단계별 가이드를 따르세요

### 복잡한 템플릿을 다루고 있습니다
1. [템플릿 문법](./reference/template-syntax.md)에서 전체 마커 문법을 확인하세요
2. [고급 예제](./examples/advanced-examples.md)에서 실전 패턴을 참조하세요
3. [문제 해결](./troubleshooting.md)에서 자주 발생하는 문제를 확인하세요

### 내부 구현을 이해하고 싶습니다
1. [개발자 가이드](./developer-guide.md)에서 아키텍처와 파이프라인을 학습하세요

---

## 문서 구조

### 사용자 가이드
- [사용자 가이드](./user-guide.md) - TBEG 사용법 전체 가이드

### 레퍼런스
- [템플릿 문법](./reference/template-syntax.md) - 템플릿에서 사용할 수 있는 문법
- [API 레퍼런스](./reference/api-reference.md) - 클래스 및 메서드 상세
- [설정 옵션](./reference/configuration.md) - TbegConfig 옵션

### 예제
- [기본 예제](./examples/basic-examples.md) - 간단한 사용 예제
- [고급 예제](./examples/advanced-examples.md) - 대용량 처리, 비동기 처리 등
- [Spring Boot 예제](./examples/spring-boot-examples.md) - Spring Boot 환경 통합

### 운영 가이드
- [모범 사례](./best-practices.md) - 템플릿 설계, 성능 최적화, 오류 방지
- [문제 해결](./troubleshooting.md) - 자주 발생하는 문제와 해결 방법
- [마이그레이션 가이드](./migration-guide.md) - 버전 업그레이드 안내

### 개발자 가이드
- [개발자 가이드](./developer-guide.md) - 내부 아키텍처 및 확장 방법

### 별첨
- [타 라이브러리 비교](./appendix/library-comparison.md) - Excel 보고서 라이브러리 간 기능 비교

---

## 호환성 정보

| 항목 | 값 |
|------|-----|
| Group ID | `io.github.jogakdal` |
| Artifact ID | `tbeg` |
| 패키지 | `io.github.jogakdal.tbeg` |
| Java | 21 이상 |
| Kotlin | 2.0 이상 |
| Apache POI | 5.2.5 (전이 의존성) |
| Spring Boot | 3.x (선택 사항) |
| 작성자 | [황용호 (Yongho Hwang)](https://github.com/jogakdal) (jogakdal@gmail.com) |
