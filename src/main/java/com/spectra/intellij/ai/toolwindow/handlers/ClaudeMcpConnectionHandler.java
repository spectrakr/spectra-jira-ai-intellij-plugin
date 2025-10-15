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

public class ClaudeMcpConnectionHandler {

    private final Project project;

    public ClaudeMcpConnectionHandler(Project project) {
        this.project = project;
    }

    public static final String JIRA_MCP_NAME = "spectra-jira";

    public boolean checkMcpConnection() {
        try {
            String basePath = project.getBasePath();
            boolean checkMcpProject = checkMcpConnectionForFile(basePath, ".mcp.json");

            String userHome = System.getProperty("user.home");
            boolean checkMcpUser = checkMcpConnectionForFile(userHome, ".claude.json");

            return checkMcpProject || checkMcpUser;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkMcpConnectionForFile(String path, String fileName) {
        try {
//            Path mcpJsonPath = Paths.get(basePath, ".mcp.json");
            Path mcpJsonPath = Paths.get(path, fileName);
            File mcpJsonFile = mcpJsonPath.toFile();

            if (!mcpJsonFile.exists()) {
                return false;
            }

            String mcpContent = Files.readString(mcpJsonPath, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            JsonObject mcpJson = gson.fromJson(mcpContent, JsonObject.class);

            if (mcpJson != null && mcpJson.has("mcpServers")) {
                JsonObject mcpServers = mcpJson.getAsJsonObject("mcpServers");
                return mcpServers.has(JIRA_MCP_NAME);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void removeMcpConnection(Runnable onComplete) {
        String script = "claude mcp remove " + JIRA_MCP_NAME;

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
            JLabel additionalInfoLabel = new JLabel("만일 연결 해제가 되지 않을 경우 .mcp.json 또는 .claude.json 에서 직접 " + JIRA_MCP_NAME + "를 제거해주세요.");
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
                "Claude MCP 연결 해제 스크립트",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void setupClaudeMcp(String jiraUrl, String username, String apiToken, Runnable onComplete) {
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
            // Send access log for Claude MCP connection
            try {
                JiraService jiraService = new JiraService();
                jiraService.configure(jiraUrl, username, apiToken);
                AccessLogService accessLogService = new AccessLogService(
                    new okhttp3.OkHttpClient(),
                    new Gson(),
                    jiraService
                );
                String connectionInfo = String.format("URL: %s, Username: %s", jiraUrl, username);
                accessLogService.sendAccessLog("Claude MCP Connection", connectionInfo);
            } catch (Exception e) {
                System.err.println("Failed to send access log for Claude MCP connection: " + e.getMessage());
            }

            // Check and create .claude/commands/fix-issue.md if needed
//            ensureFixIssueCommandExists();
            ensureFixIssueAgentsExists();

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

    private void ensureFixIssueCommandExists() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }

        Path targetPath = Paths.get(basePath, ".claude", "commands", "fix-issue.md");
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

        // Copy fix-issue.md from resources to .claude/commands/
        try (InputStream sourceStream = getClass().getResourceAsStream("/.claude/commands/fix-issue.md")) {
            if (sourceStream != null) {
                Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void ensureFixIssueAgentsExists() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }

        Path targetPath = Paths.get(basePath, ".claude", "agents", "fix-issue.md");
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

        // Copy fix-issue.md from resources to .claude/agents/
        try (InputStream sourceStream = getClass().getResourceAsStream("/.claude/agents/fix-issue.md")) {
            if (sourceStream != null) {
                Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }


    private void showMcpConnectionScript(String jiraUrl, String username, String apiToken, Runnable onComplete) throws Exception {
        System.out.println("=== MCP Connection Script Generation ===");

        String basePath = project.getBasePath();
        System.out.println("Project Base Path: " + basePath);

        if (basePath == null) {
            System.err.println("ERROR: Project base path is null");
            return;
        }

        Path mcpJsonPath = Paths.get(basePath, ".mcp.json");
        File mcpJsonFile = mcpJsonPath.toFile();
        System.out.println("MCP JSON Path: " + mcpJsonPath);

        // Check if .mcp.json exists and contains atlassian-jira
        boolean needsSetup = true;
        if (mcpJsonFile.exists()) {
            System.out.println(".mcp.json exists, checking content...");
            String mcpContent = Files.readString(mcpJsonPath, StandardCharsets.UTF_8);
            System.out.println(".mcp.json content: " + mcpContent);

            Gson gson = new Gson();
            JsonObject mcpJson = gson.fromJson(mcpContent, JsonObject.class);

            if (mcpJson != null && mcpJson.has("mcpServers")) {
                JsonObject mcpServers = mcpJson.getAsJsonObject("mcpServers");
                System.out.println("mcpServers content: " + mcpServers.toString());

                if (mcpServers.has(JIRA_MCP_NAME)) {
                    System.out.println(JIRA_MCP_NAME + " already exists in .mcp.json, skipping setup");
                    needsSetup = false;
                }
            }
        } else {
            System.out.println(".mcp.json does not exist, will create new connection");
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


        // Read mcp-add-json.txt template from resources
        System.out.println("Reading MCP template from resources...");
        String mcpTemplate;
        try (InputStream templateStream = getClass().getResourceAsStream("/.claude/mcp-add-json.txt")) {
            if (templateStream == null) {
                System.err.println("ERROR: MCP template file not found in plugin resources");
                throw new Exception("MCP template file not found in plugin resources");
            }
            mcpTemplate = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Template loaded");
        }

        // Replace placeholders with actual values
        System.out.println("Replacing template placeholders...");
        mcpTemplate = mcpTemplate.replace("<jira_url>", jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/");
        mcpTemplate = mcpTemplate.replace("<email>", username);
        mcpTemplate = mcpTemplate.replace("<access_token>", apiToken);

        // Get the directory where jira-mcp.js should be located
        String mcpDirectory = basePath + "/.claude/mcp";
        mcpTemplate = mcpTemplate.replace("<mcp_directory>", mcpDirectory);

        // Escape single quotes in JSON content for shell command
        String escapedMcpTemplate = mcpTemplate.replace("'", "'\"'\"'");

        // Generate script
        String script = String.format("claude mcp add-json " + JIRA_MCP_NAME + " '%s' --scope project",
            escapedMcpTemplate);

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

            // Create toggle button for scope information
            JButton toggleButton = new JButton("user scope로 변경방법");
            toggleButton.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            // Create additional description panel for scope information (initially hidden)
            JPanel additionalDescPanel = new JPanel();
            additionalDescPanel.setLayout(new BoxLayout(additionalDescPanel, BoxLayout.Y_AXIS));
            additionalDescPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            additionalDescPanel.setVisible(false);

            JLabel scopeLabel1 = new JLabel("기본 연결은 project scope입니다. user scope으로 연결하려면 아래와 같이 수정하여 실행하세요.");
            JLabel scopeLabel2 = new JLabel("1. jira mcp 연결: claude mcp add-json ...의 마지막에 --scope project를 user로 변경 (--scope user)");
            JLabel scopeLabel3 = new JLabel("2. sub agent 생성: <project>/.claude/agents/fix-issue.md 파일을 <user-home>/.claude/agents/ 하위에 복사");

            additionalDescPanel.add(Box.createVerticalStrut(5));
            additionalDescPanel.add(scopeLabel1);
            additionalDescPanel.add(Box.createVerticalStrut(5));
            additionalDescPanel.add(scopeLabel2);
            additionalDescPanel.add(Box.createVerticalStrut(3));
            additionalDescPanel.add(scopeLabel3);

            // Add toggle functionality
            toggleButton.addActionListener(e -> {
                boolean isVisible = additionalDescPanel.isVisible();
                additionalDescPanel.setVisible(!isVisible);
                toggleButton.setText(isVisible ? "project scope 변경방법" : "project scope 변경방법 (숨기기)");
                SwingUtilities.getWindowAncestor(mainPanel).pack();
            });

            mainPanel.add(toggleButton);
            mainPanel.add(additionalDescPanel);

            int result = JOptionPane.showConfirmDialog(
                null,
                mainPanel,
                "Claude MCP 연결 스크립트",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            // If user clicked OK, copy jira-mcp.js to .claude/mcp/
            if (result == JOptionPane.OK_OPTION) {
                try {
                    copyJiraMcpToClaudeDirectory();
                    Messages.showInfoMessage(
                        project,
                        "jira-mcp.js 파일이 .claude/mcp/jira-mcp.js에 복사되었습니다.\n\n이제 터미널에서 복사한 스크립트를 실행해주세요.",
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

    private void copyJiraMcpToClaudeDirectory() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            throw new Exception("프로젝트 경로를 찾을 수 없습니다.");
        }

        // Create .claude/mcp directory if it doesn't exist
        Path mcpDirPath = Paths.get(basePath, ".claude", "mcp");
        File mcpDir = mcpDirPath.toFile();
        if (!mcpDir.exists()) {
            if (!mcpDir.mkdirs()) {
                throw new Exception(".claude/mcp 디렉토리를 생성할 수 없습니다.");
            }
        }

        // Target path for jira-mcp.js
        Path targetPath = Paths.get(basePath, ".claude", "mcp", "jira-mcp.js");

        // Copy jira-mcp.js from resources to .claude/mcp/
        try (InputStream sourceStream = getClass().getResourceAsStream("/jiramcp/jira-mcp.js")) {
            if (sourceStream == null) {
                throw new Exception("jira-mcp.js 파일을 플러그인 리소스에서 찾을 수 없습니다.");
            }
            Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
