package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class GeminiMcpConnectionHandler {

    private final Project project;

    public GeminiMcpConnectionHandler(Project project) {
        this.project = project;
    }

    public void setupGeminiMcp(String jiraUrl, String username, String apiToken) {
        if (jiraUrl.isEmpty() || username.isEmpty() || apiToken.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "먼저 Jira URL, Email, API Token을 입력해주세요.",
                "입력 확인"
            );
            return;
        }

        try {
            // Check and create .gemini/commands/fix-issue.toml if needed
            ensureFixIssueCommandExists();

            // Get uvx path
            String uvxPath = getUvxPath();

            // Create formatted JSON configuration with uvx path
            String mcpConfig = String.format(
                "{\n" +
                "  \"mcpServers\": {\n" +
                "    \"atlassian-jira\": {\n" +
                "      \"command\": \"%s\",\n" +
                "      \"args\": [\n" +
                "        \"mcp-atlassian\",\n" +
                "        \"--jira-url\",\n" +
                "        \"%s\",\n" +
                "        \"--jira-username\",\n" +
                "        \"%s\",\n" +
                "        \"--jira-token\",\n" +
                "        \"%s\"\n" +
                "      ],\n" +
                "      \"env\": {}\n" +
                "    }\n" +
                "  }\n" +
                "}",
                uvxPath,
                jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/",
                username,
                apiToken
            );

            // Get project base path
            String basePath = project.getBasePath();
            if (basePath == null) {
                Messages.showErrorDialog(project, "프로젝트 경로를 찾을 수 없습니다.", "오류");
                return;
            }

            // Create .gemini directory if it doesn't exist
            java.io.File geminiDir = new java.io.File(basePath, ".gemini");
            if (!geminiDir.exists()) {
                geminiDir.mkdirs();
            }

            // Create or update settings.json
            java.io.File settingsFile = new java.io.File(geminiDir, "settings.json");

            // Write the configuration to the file
            Files.write(settingsFile.toPath(), mcpConfig.getBytes(StandardCharsets.UTF_8));

            Messages.showInfoMessage(
                project,
                "Gemini MCP 설정이 .gemini/settings.json에 추가되었습니다.\n\n" +
                "파일 경로: " + settingsFile.getAbsolutePath() + "\n\n",
                "Gemini MCP 연결 완료"
            );

        } catch (Exception e) {
            Messages.showErrorDialog(
                project,
                "설정 파일 생성 중 오류가 발생했습니다: " + e.getMessage(),
                "오류"
            );
        }
    }

    private void ensureFixIssueCommandExists() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }

        Path targetPath = Paths.get(basePath, ".gemini", "commands", "fix-issue.toml");
        File targetFile = targetPath.toFile();

        // If file already exists, skip
        if (targetFile.exists()) {
            return;
        }

        // Create parent directories if they don't exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Copy fix-issue.toml from resources to .gemini/commands/
        try (InputStream sourceStream = getClass().getResourceAsStream("/.gemini/commands/fix-issue.toml")) {
            if (sourceStream != null) {
                Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String getUvxPath() throws Exception {
        String osName = System.getProperty("os.name").toLowerCase();
        String command;

        if (osName.contains("win")) {
            // Windows: use 'where' command
            command = "where uvx";
        } else {
            // macOS/Linux: use 'which' command
            command = "which uvx";
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
            osName.contains("win") ? new String[]{"cmd", "/c", command} : new String[]{"/bin/sh", "-l", "-c", command}
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0 || output.toString().trim().isEmpty()) {
            String errorMsg = "uvx가 설치되어 있지 않습니다.\n\n다음 명령어로 설치해주세요:\npip install uv";
            throw new Exception(errorMsg);
        }

        // Get first line (in case multiple paths are returned)
        return output.toString().trim().split("\n")[0].trim();
    }
}
