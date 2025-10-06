다음 순서에 따라 이슈를 처리하세요:
1. atlassian-jira mcp를 활용해서 이슈 내용 확인 (issueKey: $1)
2. git branch -b develop feature/$1
3. 이슈 내용에 따라 작업 실행
4. 작업 완료 후 git commit
5. gh pr create --template pull_request_template.md 명령어로 pr 생성

