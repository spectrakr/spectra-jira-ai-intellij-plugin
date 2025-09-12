# 변경 이력

Spectra IntelliJ AI 플러그인의 모든 주요 변경사항이 이 파일에 기록됩니다.

형식은 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)을 기반으로 하며,
이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을 따릅니다.

## [1.0.0] - 2025-01-02

### 추가됨
- 기본 Jira 통합 기능을 갖춘 초기 릴리스
- 스프린트 선택을 통한 Jira 이슈 생성
- 프로젝트 구성 기반 동적 이슈 유형 선택
- IntelliJ 설정 시스템을 사용한 자동 구성 저장
- Jira Cloud API v3 통합
- 이슈 설명을 위한 Atlassian Document Format (ADF) 지원
- Jira 기능에 쉽게 접근할 수 있는 툴 윈도우
- Tools 메뉴 하위 메뉴 통합
- Jira 연결 구성을 위한 설정 페이지
- 사용자 친화적인 메시지와 함께하는 오류 처리
- 자동 스프린트 할당 (할당 실패 시 대체 방안 제공)
- 자동 업데이트를 위한 사용자 정의 플러그인 저장소 지원
- 자동화된 릴리스를 위한 GitHub Actions 워크플로우

### 기술 세부사항
- IntelliJ IDEA 2023.3+ (빌드 233+)용으로 제작
- Java 17+ 필요
- HTTP 통신을 위한 OkHttp 사용
- JSON 처리를 위한 Gson 사용
- 설정 저장을 위한 PersistentStateComponent 구현

### 구성
- 사전 구성된 Jira URL: `https://enomix.atlassian.net/`
- API 토큰 인증 지원
- 구성 가능한 기본 보드 ID 및 프로젝트 키

