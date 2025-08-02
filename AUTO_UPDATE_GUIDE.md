# 🔄 Auto-Update Setup Guide

이 가이드는 Spectra Jira AI IntelliJ 플러그인의 자동 업데이트 시스템을 설정하는 방법을 설명합니다.

## 📋 개요

이 플러그인은 **GitHub Releases + Custom Repository** 방식을 사용하여 자동 업데이트를 지원합니다. 사용자는 IntelliJ IDEA에서 플러그인 업데이트 알림을 받고, 원클릭으로 업데이트할 수 있습니다.

## 🚀 사용자 설치 방법

### Method 1: Custom Repository (권장)

1. **IntelliJ IDEA 열기**
2. **Settings 이동**: `File → Settings → Plugins`
3. **Repository 관리**: ⚙️ 아이콘 클릭 → `Manage Plugin Repositories`
4. **Repository 추가**: `+` 버튼 클릭 후 다음 URL 입력:
   ```
   https://[YOUR_GITHUB_USERNAME].github.io/spectra-jira-ai-intellij-plugin/updatePlugins.xml
   ```
5. **OK 클릭**
6. **플러그인 검색**: `Marketplace` 탭에서 "Spectra Jira AI" 검색
7. **설치**: `Install` 버튼 클릭

### Method 2: 직접 다운로드

1. [GitHub Releases](https://github.com/[YOUR_REPO]/releases/latest)에서 최신 ZIP 파일 다운로드
2. `File → Settings → Plugins → ⚙️ → Install Plugin from Disk`
3. 다운로드한 ZIP 파일 선택
4. IntelliJ IDEA 재시작

## ⚙️ 개발자 설정 (릴리스 자동화)

### 1. GitHub Pages 활성화

1. GitHub 리포지토리 → **Settings** 탭
2. **Pages** 섹션으로 이동
3. **Source**: `GitHub Actions` 선택
4. **Save** 클릭

### 2. 릴리스 생성 방법

#### 자동 릴리스 (Git Tag 기반)
```bash
# 버전 태그 생성 및 푸시
git tag v1.0.1
git push origin v1.0.1
```

#### 수동 릴리스 (GitHub Actions)
1. GitHub 리포지토리 → **Actions** 탭
2. **Release Plugin** 워크플로우 선택
3. **Run workflow** 클릭
4. 버전 입력 (예: `1.0.1`)
5. **Run workflow** 실행

### 3. 자동화된 프로세스

릴리스가 생성되면 다음이 자동으로 실행됩니다:

1. **플러그인 빌드**: Gradle로 플러그인 ZIP 파일 생성
2. **GitHub Release 생성**: ZIP 파일과 릴리스 노트 포함
3. **updatePlugins.xml 생성**: 플러그인 메타데이터 파일 생성
4. **GitHub Pages 배포**: 사용자용 설치 페이지와 updatePlugins.xml 배포

## 📁 생성되는 파일들

### GitHub Release Assets
- `spectra-jira-ai-intellij-plugin-[VERSION].zip`: 플러그인 설치 파일

### GitHub Pages
- `https://[USERNAME].github.io/[REPO]/updatePlugins.xml`: 플러그인 업데이트 메타데이터
- `https://[USERNAME].github.io/[REPO]/`: 사용자용 설치 가이드 페이지

## 🔧 업데이트 메커니즘

### IntelliJ Plugin Manager 연동
1. **주기적 체크**: IntelliJ가 등록된 Custom Repository에서 주기적으로 업데이트 확인
2. **버전 비교**: `updatePlugins.xml`의 버전과 현재 설치된 버전 비교
3. **알림 표시**: 새 버전이 있으면 사용자에게 업데이트 알림
4. **원클릭 업데이트**: 사용자가 업데이트 버튼 클릭시 자동 다운로드 및 설치

### updatePlugins.xml 구조
```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
    <plugin id="com.spectra.jira.ai" 
            url="https://github.com/[REPO]/releases/latest/download/[PLUGIN].zip" 
            version="1.0.1">
        <idea-version since-build="233" />
        <name>Spectra Jira AI</name>
        <description>...</description>
        <change-notes>...</change-notes>
        <vendor>Spectra</vendor>
        <depends>com.intellij.modules.platform</depends>
        <depends>com.intellij.modules.java</depends>
    </plugin>
</plugins>
```

## 🛠️ 트러블슈팅

### Repository URL 오류
- GitHub Pages가 활성화되어 있는지 확인
- URL이 정확한지 확인: `https://[USERNAME].github.io/[REPO]/updatePlugins.xml`

### 업데이트가 감지되지 않는 경우
1. IntelliJ 재시작
2. `File → Settings → Plugins → ⚙️ → Check for Updates` 수동 실행
3. Repository URL 다시 추가

### GitHub Actions 실패
1. **Permissions 확인**: Repository Settings → Actions → General에서 Workflow permissions 확인
2. **Pages 설정**: GitHub Pages가 활성화되어 있는지 확인
3. **Secrets 확인**: `GITHUB_TOKEN`이 자동으로 제공되는지 확인

## 📈 버전 관리 전략

### Semantic Versioning 사용
- **Major (1.x.x)**: 호환성이 깨지는 변경사항
- **Minor (x.1.x)**: 새로운 기능 추가
- **Patch (x.x.1)**: 버그 수정

### 릴리스 주기
- **Hot Fix**: 긴급 버그 수정시 즉시 릴리스
- **Minor Release**: 월 1-2회 기능 업데이트
- **Major Release**: 분기별 대규모 업데이트

## 🔐 보안 고려사항

- GitHub Token은 자동으로 제공되므로 별도 설정 불필요
- Private repository의 경우 릴리스를 Public으로 설정해야 함
- updatePlugins.xml은 Public으로 접근 가능해야 함

## 📞 지원

업데이트 관련 문제가 발생하면:
1. [GitHub Issues](https://github.com/[YOUR_REPO]/issues)에 문제 신고
2. 에러 로그와 함께 상세한 상황 설명 제공
3. IntelliJ 버전과 플러그인 버전 명시