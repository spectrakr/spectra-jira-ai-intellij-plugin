# Spectra IntelliJ AI Plugin 설치 및 자동 업데이트 가이드

## 플러그인 설치 방법

### 방법 1: 직접 다운로드 (수동 설치)

1. [GitHub Releases 페이지](https://github.com/yourusername/spectra-jira-ai-intellij-plugin/releases)에서 최신 버전의 JAR 파일을 다운로드합니다.
2. IntelliJ IDEA를 열고 `File > Settings > Plugins`로 이동합니다.
3. 우상단의 톱니바퀴 아이콘을 클릭하고 `Install Plugin from Disk...`를 선택합니다.
4. 다운로드한 JAR 파일을 선택합니다.
5. IntelliJ IDEA를 재시작합니다.

### 방법 2: 커스텀 저장소 (자동 업데이트 지원) ⭐ 권장

#### 커스텀 저장소 추가

1. IntelliJ IDEA를 열고 `File > Settings > Plugins`로 이동합니다.
2. 우상단의 톱니바퀴 아이콘을 클릭하고 `Manage Plugin Repositories...`를 선택합니다.
3. `+` 버튼을 클릭하고 다음 URL을 입력합니다:
   ```
   https://github.com/rudaks-han/spectra-jira-ai-intellij-plugin/releases/latest/download/updatePlugins.xml
   ```
4. `OK`를 클릭하여 저장합니다.

#### 플러그인 설치

1. 플러그인 마켓플레이스에서 "Spectra IntelliJ AI"를 검색합니다.
2. `Install` 버튼을 클릭합니다.
3. IntelliJ IDEA를 재시작합니다.

## 자동 업데이트 설정

### 자동 업데이트 활성화

1. `File > Settings > Appearance & Behavior > System Settings > Updates`로 이동합니다.
2. `Check for plugin updates` 체크박스가 활성화되어 있는지 확인합니다.
3. `Enable auto-update` 체크박스를 활성화합니다 (IntelliJ IDEA 2025.1 이상).

### 업데이트 확인 주기

- IntelliJ IDEA는 기본적으로 24시간마다 플러그인 업데이트를 확인합니다.
- 수동으로 업데이트를 확인하려면 `Help > Check for Updates`를 선택합니다.

## 플러그인 설정

### Jira 연결 설정

1. `File > Settings > Tools > Spectra Jira Settings`로 이동합니다.
2. 다음 정보를 입력합니다:
   - **Jira URL**: `https://enomix.atlassian.net/` (기본값)
   - **Username**: Jira 계정 이메일
   - **API Token**: Jira API 토큰
   - **Default Board ID**: 기본 보드 ID (선택사항)
   - **Default Project Key**: 기본 프로젝트 키 (선택사항)

### API 토큰 생성 방법

1. [Atlassian Account 설정](https://id.atlassian.com/manage-profile/security/api-tokens)으로 이동합니다.
2. `Create API token`을 클릭합니다.
3. 토큰 이름을 입력하고 `Create`를 클릭합니다.
4. 생성된 토큰을 복사하여 플러그인 설정에 입력합니다.

## 플러그인 사용법

### 이슈 생성

1. `Tools > Create Jira Issue` 메뉴를 선택하거나
2. 우측 사이드바의 `Spectra Jira` 도구창에서 `Create Issue` 버튼을 클릭합니다.
3. 이슈 정보를 입력하고 `OK`를 클릭합니다.

### 주요 기능

- **자동 설정 저장**: 한 번 설정하면 정보가 자동으로 저장됩니다.
- **스프린트 선택**: 활성 스프린트 목록에서 선택 가능합니다.
- **이슈 타입 선택**: 프로젝트에 맞는 이슈 타입을 동적으로 로드합니다.
- **성공 알림**: 이슈 생성 완료 시 성공 메시지를 표시합니다.

## 문제 해결

### 커스텀 저장소가 작동하지 않는 경우

1. URL이 정확한지 확인합니다.
2. 인터넷 연결을 확인합니다.
3. IntelliJ IDEA를 재시작합니다.
4. `Help > Check for Updates`로 수동 업데이트를 시도합니다.

### 플러그인 설치 실패

1. IntelliJ IDEA 버전이 233 이상인지 확인합니다.
2. 다른 방법으로 설치를 시도합니다.
3. IntelliJ IDEA 로그를 확인합니다 (`Help > Show Log in Explorer/Finder`).

### Jira 연결 오류

1. Jira URL이 올바른지 확인합니다.
2. API 토큰이 유효한지 확인합니다.
3. 네트워크 연결을 확인합니다.
4. Jira 계정의 권한을 확인합니다.

## 지원 및 문의

문제가 발생하거나 기능 요청이 있으시면 [GitHub Issues](https://github.com/yourusername/spectra-jira-ai-intellij-plugin/issues)에 등록해주세요.

---

> **참고**: 이 플러그인은 Jira Cloud API v3를 사용합니다. Jira Server는 지원되지 않습니다.
