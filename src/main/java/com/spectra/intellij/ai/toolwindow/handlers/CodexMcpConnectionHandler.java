package com.spectra.intellij.ai.toolwindow.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.service.AccessLogService;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CodexMcpConnectionHandler {

    private final Project project;

    public CodexMcpConnectionHandler(Project project) {
        this.project = project;
    }

    public static final String JIRA_MCP_NAME = "spectra-jira";

    public boolean checkMcpConnection() {
        try {
            String userHome = System.getProperty("user.home");
            return checkMcpConnectionForFile(userHome + "/.codex", "config.toml");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkMcpConnectionForFile(String path, String fileName) {
        try {
            Path settingsJsonPath = Paths.get(path, fileName);
            File settingsJsonFile = settingsJsonPath.toFile();

            System.out.println("settingsJsonPath path: " + settingsJsonPath.toString());
            System.out.println("settingsJsonFile.exists: " + settingsJsonFile.exists());
            if (!settingsJsonFile.exists()) {
                return false;
            }

            String settingsContent = Files.readString(settingsJsonPath, StandardCharsets.UTF_8);

            System.out.println("[mcp_servers.\" + JIRA_MCP_NAME + \"]\": " + settingsContent.contains("[mcp_servers." + JIRA_MCP_NAME + "]"));
            return settingsContent.contains("[mcp_servers." + JIRA_MCP_NAME + "]");
        } catch (Exception e) {
            return false;
        }
    }

    public void removeMcpConnection(Runnable onComplete) {
        String script = "codex mcp remove " + JIRA_MCP_NAME;

        // Show script in dialog
        SwingUtilities.invokeLater(() -> {
            // Create main panel
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

            // Create description panel with label and copy button
            JPanel descriptionPanel = new JPanel();
            descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.X_AXIS));

            JLabel descriptionLabel = new JLabel("Jira MCP 연결 해제를 위해 아래 스크립트를 IntelliJ Terminal에서 실행해주세요.");
            descriptionPanel.add(descriptionLabel);
            descriptionPanel.add(Box.createHorizontalStrut(10));

            JButton copyButton = new JButton("스크립트 복사");
            copyButton.addActionListener(e -> {
                java.awt.datatransfer.StringSelection stringSelection = new java.awt.datatransfer.StringSelection(script);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                JOptionPane.showMessageDialog(null, "스크립트가 클립보드에 복사되었습니다.", "복사 완료", JOptionPane.INFORMATION_MESSAGE);
            });
            descriptionPanel.add(copyButton);
            descriptionPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            mainPanel.add(descriptionPanel);
            mainPanel.add(Box.createVerticalStrut(5));

            // Add additional info label
            JLabel additionalInfoLabel = new JLabel("만일 연결 해제가 되지 않을 경우 .codex/settings.json 에서 직접 " + JIRA_MCP_NAME + " 부분을 제거해주세요.");
            additionalInfoLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            mainPanel.add(additionalInfoLabel);
            mainPanel.add(Box.createVerticalStrut(10));

            // Create text area with script
            JTextArea textArea = new JTextArea(script, 10, 80);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            mainPanel.add(scrollPane);

            JOptionPane.showConfirmDialog(
                null,
                mainPanel,
                "Codex MCP 연결 해제 스크립트",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void setupCodexMcp(String jiraUrl, String username, String apiToken, Runnable onComplete) {
        // Validate settings
        if (jiraUrl.isEmpty() || username.isEmpty() || apiToken.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Jira URL, Email, API Token을 모두 입력해주세요.",
                "설정 확인"
            );
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        try {
            // Send access log for Codex MCP connection
            try {
                JiraService jiraService = new JiraService();
                jiraService.configure(jiraUrl, username, apiToken);
                AccessLogService accessLogService = new AccessLogService(
                    new okhttp3.OkHttpClient(),
                    new Gson(),
                    jiraService
                );
                String connectionInfo = String.format("URL: %s, Username: %s", jiraUrl, username);
                accessLogService.sendAccessLog("Codex MCP Connection", connectionInfo);
            } catch (Exception e) {
                System.err.println("Failed to send access log for Codex MCP connection: " + e.getMessage());
            }

            // Check and create .codex/commands/fix-issue.md if needed
            ensureFixIssuePromptsExists();

            // Generate and show script instead of executing directly
            showMcpConnectionScript(jiraUrl, username, apiToken, onComplete);
        } catch (Exception ex) {
            Messages.showErrorDialog(
                project,
                "Jira MCP 연결 중 오류가 발생했습니다:\n" + ex.getMessage(),
                "오류"
            );
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void ensureFixIssuePromptsExists() throws Exception {
        String userHome = System.getProperty("user.home");

        Path targetPath = Paths.get(userHome, ".codex", "prompts", "fix-issue.md");
        File targetFile = targetPath.toFile();

        // If file already exists, skip

        System.out.println("___ targetFile.exists() : " + targetFile.exists());
        if (targetFile.exists()) {
            return;
        }

        // Create parent directories if they don't exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Copy fix-issue.md from resources to .codex/prompts/
        try (InputStream sourceStream = getClass().getResourceAsStream("/codex/prompts/fix-issue.md")) {
            if (sourceStream != null) {
                Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Successfully copied fix-issue.md to " + targetPath);
            } else {
                System.err.println("Failed to load fix-issue.md from resources");
            }
        }
    }

    private void showMcpConnectionScript(String jiraUrl, String username, String apiToken, Runnable onComplete) throws Exception {
        System.out.println("=== Codex MCP Connection Script Generation ===");

        String basePath = project.getBasePath();
        System.out.println("Project Base Path: " + basePath);

        if (basePath == null) {
            System.err.println("ERROR: Project base path is null");
            return;
        }

        Path settingsJsonPath = Paths.get(basePath, ".codex", "settings.json");
        File settingsJsonFile = settingsJsonPath.toFile();
        System.out.println("Settings JSON Path: " + settingsJsonPath);

        // Check if settings.json exists and contains spectra-jira
        boolean needsSetup = true;
        if (settingsJsonFile.exists()) {
            System.out.println("settings.json exists, checking content...");
            String settingsContent = Files.readString(settingsJsonPath, StandardCharsets.UTF_8);
            System.out.println("settings.json content: " + settingsContent);

            Gson gson = new Gson();
            JsonObject settingsJson = gson.fromJson(settingsContent, JsonObject.class);

            if (settingsJson != null && settingsJson.has("mcpServers")) {
                JsonObject mcpServers = settingsJson.getAsJsonObject("mcpServers");
                System.out.println("mcpServers content: " + mcpServers.toString());

                if (mcpServers.has(JIRA_MCP_NAME)) {
                    System.out.println(JIRA_MCP_NAME + " already exists in settings.json, skipping setup");
                    needsSetup = false;
                }
            }
        } else {
            System.out.println("settings.json does not exist, will create new connection");
        }

        if (!needsSetup) {
            System.out.println("Setup not needed, completing...");
            Messages.showInfoMessage(
                project,
                "Jira MCP 연결이 이미 설정되어 있습니다.",
                "Jira MCP 연결"
            );
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Get the path where jira-mcp.js will be located
        String mcpJsPath = basePath + "/.codex/mcp/jira-mcp.js";

        // Generate script with direct command format
        String script = String.format(
            "codex mcp add %s \"node\" \"%s\" \"--token\" \"%s\" \"--email\" \"%s\" \"--baseUrl\" \"%s\"",
            JIRA_MCP_NAME,
            mcpJsPath,
            apiToken,
            username,
            jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/"
        );

        // Show script in dialog
        SwingUtilities.invokeLater(() -> {
            // Create main panel
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

            // Create description panel with label and copy button
            JPanel descriptionPanel = new JPanel();
            descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.X_AXIS));

            JLabel descriptionLabel = new JLabel("Jira MCP를 사용하기 위해서 아래 스크립트를 IntelliJ Terminal에서 실행해주세요.");
            descriptionPanel.add(descriptionLabel);
            descriptionPanel.add(Box.createHorizontalStrut(10));

            JButton copyButton = new JButton("스크립트 복사");
            copyButton.addActionListener(e -> {
                java.awt.datatransfer.StringSelection stringSelection = new java.awt.datatransfer.StringSelection(script);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                JOptionPane.showMessageDialog(null, "스크립트가 클립보드에 복사되었습니다.", "복사 완료", JOptionPane.INFORMATION_MESSAGE);
            });
            descriptionPanel.add(copyButton);
            descriptionPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            mainPanel.add(descriptionPanel);
            mainPanel.add(Box.createVerticalStrut(10));

            // Create text area with script
            JTextArea textArea = new JTextArea(script, 10, 80);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            mainPanel.add(scrollPane);
            mainPanel.add(Box.createVerticalStrut(10));

            int result = JOptionPane.showConfirmDialog(
                null,
                mainPanel,
                "Codex MCP 연결 스크립트",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            // If user clicked OK, copy jira-mcp.js to .codex/mcp/
            if (result == JOptionPane.OK_OPTION) {
                try {
                    copyJiraMcpToCodexDirectory();
                    Messages.showInfoMessage(
                        project,
                        "jira-mcp.js 파일이 .codex/mcp/jira-mcp.js에 복사되었습니다.\n\n이제 터미널에서 복사한 스크립트를 실행해주세요.",
                        "파일 복사 완료"
                    );
                } catch (Exception ex) {
                    Messages.showErrorDialog(
                        project,
                        "jira-mcp.js 파일 복사 중 오류가 발생했습니다:\n" + ex.getMessage(),
                        "파일 복사 실패"
                    );
                }
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void copyJiraMcpToCodexDirectory() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new Exception("프로젝트 경로를 찾을 수 없습니다.");
        }

        // Create .codex/mcp directory if it doesn't exist
        Path mcpDirPath = Paths.get(basePath, ".codex", "mcp");
        File mcpDir = mcpDirPath.toFile();
        if (!mcpDir.exists()) {
            if (!mcpDir.mkdirs()) {
                throw new Exception(".codex/mcp 디렉토리를 생성할 수 없습니다.");
            }
        }

        // Target path for jira-mcp.js
        Path targetPath = Paths.get(basePath, ".codex", "mcp", "jira-mcp.js");

        // Copy jira-mcp.js from resources to .codex/mcp/
        try (InputStream sourceStream = getClass().getResourceAsStream("/jiramcp/jira-mcp.js")) {
            if (sourceStream == null) {
                throw new Exception("jira-mcp.js 파일을 플러그인 리소스에서 찾을 수 없습니다.");
            }
            Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
