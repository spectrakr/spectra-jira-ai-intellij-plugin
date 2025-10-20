### claude add-json
```bash
claude mcp add-json spectra-jira '{
    "command": "node",
    "args": [
        "/Users/rudaks/jira-mcp.js",
        "--token", "<token>",
        "--email", "kmhan@spectra.co.kr",
        "--baseUrl", "https://enomix.atlassian.net/"
    ]
}' --scope project
```

### gemini add mcp
```bash
gemini mcp add spectra-jira --command "node" --args "/Users/rudaks/jira-mcp.js" --args "--token" --args "<token>" --args --email --args "kmhan@spectra.co.kr" --args --baseUrl --args "https://"
```



## Codex
### 실행
codex --yolo "prompt 내용"

#### mcp add
codex mcp add spectra-jira "node" "/Users/rudaks/IdeaProjects/untitled1/.codex/mcp/jira-mcp.js" "--token" "<token>" "--email" "kmhan@spectra.co.kr" "--baseUrl" "https://enomix.atlassian.net/"

#### mcp remove
codex mcp remove spectra-jira






