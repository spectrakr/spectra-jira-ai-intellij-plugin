# Deployment Guide

이 문서는 Spectra IntelliJ AI Plugin을 GitHub에 배포하는 방법을 설명합니다.

## 배포 방법

### 1. 태그 기반 자동 배포 (권장)

가장 간단한 배포 방법입니다. Git 태그를 생성하면 GitHub Actions가 자동으로 빌드하고 릴리즈를 생성합니다.

```bash
# 새 버전 태그 생성
git tag v1.0.0
git push origin v1.0.0
```

### 2. 수동 워크플로우 트리거

GitHub 웹사이트에서 수동으로 배포를 실행할 수 있습니다.

1. GitHub 저장소의 **Actions** 탭으로 이동
2. **Release Plugin** 워크플로우 선택
3. **Run workflow** 버튼 클릭
4. 버전 번호 입력 (예: 1.0.0)
5. **Run workflow** 실행

## 배포 프로세스

GitHub Actions 워크플로우는 다음 단계를 수행합니다:

1. **코드 체크아웃** - 최신 소스코드 다운로드
2. **JDK 17 설정** - Java 개발 환경 구성
3. **테스트 실행** - `./gradlew test`
4. **플러그인 빌드** - `./gradlew buildPlugin`
5. **플러그인 검증** - `./gradlew verifyPlugin`
6. **업데이트 저장소 준비** - `./gradlew preparePluginRepository`
7. **GitHub Release 생성** - 릴리즈 노트와 함께 릴리즈 생성
8. **파일 업로드**:
   - 플러그인 JAR 파일
   - updatePlugins.xml (자동 업데이트용)
9. **JetBrains Marketplace 배포** (선택사항)

## 필요한 설정

### GitHub Secrets

다음 secret들을 GitHub 저장소 설정에서 구성해야 합니다:

1. **GITHUB_TOKEN** (자동 생성됨)
   - GitHub Actions에서 자동으로 제공
   - 릴리즈 생성 및 파일 업로드에 사용

2. **PUBLISH_TOKEN** (선택사항)
   - JetBrains Marketplace 배포용
   - 설정 방법: Repository Settings → Secrets → New repository secret

### JetBrains Marketplace Token 생성

1. [JetBrains Hub](https://hub.jetbrains.com)에 로그인
2. **My Organizations** → **Permissions** → **Generate Token**
3. **Scope**: `Plugin Repository: Upload`
4. 생성된 토큰을 GitHub Secrets에 `PUBLISH_TOKEN`으로 저장

## 자동 업데이트 설정

배포된 플러그인은 다음 URL을 통해 자동 업데이트를 지원합니다:

```
https://github.com/rudaks-han/spectra-jira-ai-intellij-plugin/releases/latest/download/updatePlugins.xml
```

### 사용자 설정 방법

1. IntelliJ IDEA에서 `File > Settings > Plugins`
2. 톱니바퀴 아이콘 → `Manage Plugin Repositories...`
3. 위 URL 추가
4. `Marketplace` 탭에서 "Spectra IntelliJ AI" 검색 및 설치

## 배포 후 확인사항

1. **GitHub Release 페이지 확인**
   - JAR 파일이 업로드되었는지 확인
   - updatePlugins.xml이 업로드되었는지 확인

2. **자동 업데이트 테스트**
   - 이전 버전이 설치된 IntelliJ에서 업데이트 확인
   - `Help > Check for Updates` 실행

3. **설치 테스트**
   - 새로운 IntelliJ 환경에서 플러그인 설치 테스트
   - 기본 기능 동작 확인

## 문제 해결

### 빌드 실패
- Gradle 캐시 문제: `./gradlew clean` 후 재시도
- Java 버전 확인: JDK 17 사용 여부 확인

### 업로드 실패
- GitHub token 권한 확인
- 파일 경로 및 이름 확인

### 자동 업데이트 실패
- updatePlugins.xml URL 접근 가능 여부 확인
- 버전 번호 형식 확인

## 버전 관리

버전 번호는 [Semantic Versioning](https://semver.org/) 규칙을 따릅니다:

- **MAJOR.MINOR.PATCH** (예: 1.0.0)
- **Major**: 호환성이 깨지는 변경
- **Minor**: 기능 추가 (하위 호환)
- **Patch**: 버그 수정

태그 생성 시 반드시 `v` 접두사를 사용하세요 (예: `v1.0.0`).
