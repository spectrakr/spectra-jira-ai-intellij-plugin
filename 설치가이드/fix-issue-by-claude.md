## Fix Issue (by claude) 

Fix issue (by Claude)는 아래와 같은 방식으로 동작합니다.

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
> 1. **uvx 설치**: Python 환경 및 패키지 실행을 초간단하게 관리하는 명령어 도구입니다.
>
> 2. **gh 설치**: 터미널에서 GitHub 기능(이슈, PR, 리포지토리, 릴리스 등)을 직접 다룰 수 있는 도구입니다.



#### 1. uvx 설치

**Mac에서 설치** (아래 2가지 중 선택)

```bash
# 1. curl로 설치
curl -LsSf https://astral.sh/uv/install.sh | sh

# 2. brew로 설치
brew install uv
```

설치된 버전 확인

```bash
uvx --version
```



**Windows에서 설치**

PowerShell을 **관리자 권한으로 실행**한 뒤 아래 입력:

```bash
irm https://astral.sh/uv/install.ps1 | iex
```

설치된 버전 확인

```bash
uvx --version
```



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







cursor



```
cursor-agent -p "다음 순서에 따라 이슈를 처리하세요:
1. atlassian-jira mcp를 활용해서 이슈 내용 확인 (issueKey: HICZ-5)
2. git branch -b develop feature/HICZ-5
3. 이슈 내용에 따라 작업 실행
4. git commit and push
5. gh pr create를 사용하여 pull request 생성
    - pull_request_template.md를 활용
    - 아래 2가지 내용은 필수 입력사항
        - 해결하려는 문제가 무엇인가요? (필수)
        - 어떻게 해결했나요? (필수)"
```















### 설치

```
# uv 설치

# gh 설치
brew install gh

gh auth login 필요
```





### Jira mcp 연결 (by claude)

```bash
{
    "mcpServers": {
        "atlassian-jira": {
		    "command": "python",
		    "args": [
		    	"mcp-atlassian",
		        "--jira-url", "https://enomix.atlassian.net/",
		        "--jira-username", "kmhan@spectra.co.kr",
		        "--jira-token", "xxx"
		    ],
		    "env": {}
	    }
      }
}
```





### Claude mcp add 방법

```
claude mcp add-json atlassian-jira  '{
		    "command": "/opt/homebrew/bin/uvx",
		    "args": [
		    	"mcp-atlassian",
		        "--jira-url", "https://enomix.atlassian.net/",
		        "--jira-username", "kmhan@spectra.co.kr",
		        "--jira-token", "xxx"
		    ],
		    "env": {}
	    }'  --scope project
```

###  

### Claude mcp remove 방법

```
claude mcp remove atlassian-jira
```



### custom commands

```
mkdir .claude/commands
```



### claude 실행

```
claude --dangerously-skip-permissions "/fix_issue HICZ-4"
```



### gemini 실행

```
gemini --yolo "/fix_issue HICZ-4"
```



### pr 생성

```bash
gh pr create --title "feat: HICZ-4 readme 파일 수정" --body "HICZ-4 이슈에 따라 README.md 파일을 수정합니다.
```



----

claude --dangerously-skip-permissions -p "fix_issue_agent sugagent를 이용하여 HICZ-5 이슈 처리해줘"

claude --dangerously-skip-permissions -p "fix_issue_agent sugagent를 이용하여 HICZ-6 이슈 처리해줘"



claude --dangerously-skip-permissions "fix-issue-agent sugagent를 이용하여 HICZ-6 이슈 처리해줘"
