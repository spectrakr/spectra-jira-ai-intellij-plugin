## Fix Issue Agent 사용방법

Fix issue Agent는 아래와 같은 방식으로 동작합니다.

1. mcp를 활용해서 jira의 description 정보를 조회합니다.
2. 작업 전 feature 브랜치를 생성합니다.
3. 이슈 내용을 확인하고 이슈 처리작업을 실행합니다.
4. 이슈 처리를 완료하고 commit 및 push를 합니다.
5. 이슈 내용에 대해 pull request를 요청합니다.



> **중요**
>
> ---
>
> Fix issue (by claude)를 사용하기 위해서는 아래 사항이 설치되어 있어야 합니다.
>
> 1. **node 설치**: jira mcp를 실행하기 위해서는 node가 필요합니다.
>
> 2. **gh 설치**: 터미널에서 GitHub 기능(이슈, PR, 리포지토리, 릴리스 등)을 직접 다룰 수 있는 도구입니다.



#### 1. node 설치

https://nodejs.org/en 사이트에 접속해서 node를 설치



#### 2. gh 설치

Mac에서 설치

```bash
brew install gh
```

Windows에서 설치

방법 1. Scoop (권장)

```bash
scoop install gh
```

방법 2. Winget

```bash
winget install --id GitHub.cli
```

방법 3. MSI 설치

1. [GitHub CLI Releases](https://github.com/cli/cli/releases) 페이지에서 `gh_<version>_windows_amd64.msi` 다운로드

2. 설치 마법사 실행

3. PowerShell에서:

```bash
gh --version
```



gh 설치 후 gh auth login으로 로그인을 완료합니다.

```bash
 gh auth login --with-token <GITHUB_TOKEN>
```



> GITHUB_TOKEN은 https://github.com/settings/personal-access-tokens에서 토큰 발급받으시면 됩니다.





### 3. Claude 연결

Claude로 Fix Issue Agent를 사용하기 위해서는 Claude 연결 버튼을 눌러 스크립트를 실행해야 합니다. Claude 연결 버튼 클릭 시 표시되는 스크립트를 복사하고 intellij 터미널에서 실행하면 됩니다.

```bash
claude mcp add-json spectra-jira '{
    "command": "node",
    "args": [
        "<project_home>/jira-mcp.js",
        "--token", "<jira token>",
        "--email", "<email>",
        "--baseUrl", "https://enomix.atlassian.net/"
    ],
    "env": {}
}' --scope project
```

기본적으로 project scope으로 mcp가 연결이 됩니다.

설정 시 아래의 파일이 생성됩니다.

- <project_home>/.claude/agents/fix-issue.md
- <project_home>/.claude/mcp/jira-mcp.js

> **user scope로 변경하는 방법**
>
> Project scope는 해당 프로젝트 내에서만 실행되는 설정입니다. 만일 사용자 계정 모두 실행되게 하려면 아래와 같이 하시면 됩니다.
>
> 1. jira mcp 연결 시 --scope user로 변경하여 실행
> 2. sub agent 파일을 사용자 계정으로 이동
>    1. <project_home/.claude/agents/fix-issue.md를 \<user-home>/.claude/agents/fix-issue.md로 이동



### 4. Gemini 연결

Gemini로 Fix Issue Agent를 사용하기 위해서는 Gemini 연결 버튼을 눌러 스크립트를 실행해야 합니다. Gemini 연결 버튼 클릭 시 표시되는 스크립트를 복사하고 intellij 터미널에서 실행하면 됩니다.

```bash
gemini mcp add spectra-jira "node" --args "<project_home>/jira-mcp.js" --args "--token" --args "<token>" --args "--email" --args "kmhan@spectra.co.kr" --args "--baseUrl" --args "https://enomix.atlassian.net/" --scope project
```

