package com.spectra.intellij.ai.toolwindow.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

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

    public boolean checkMcpConnection() {
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                return false;
            }

            Path mcpJsonPath = Paths.get(basePath, ".mcp.json");
            File mcpJsonFile = mcpJsonPath.toFile();

            if (!mcpJsonFile.exists()) {
                return false;
            }

            String mcpContent = Files.readString(mcpJsonPath, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            JsonObject mcpJson = gson.fromJson(mcpContent, JsonObject.class);

            if (mcpJson != null && mcpJson.has("mcpServers")) {
                JsonObject mcpServers = mcpJson.getAsJsonObject("mcpServers");
                return mcpServers.has("atlassian-jira");
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void removeMcpConnection(Runnable onComplete) {
        String script = "claude mcp remove atlassian-jira";

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
            // Check and create .claude/commands/fix_issue.md if needed
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

        Path targetPath = Paths.get(basePath, ".claude", "commands", "fix_issue.md");
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

        // Copy fix_issue.md from resources to .claude/commands/
        try (InputStream sourceStream = getClass().getResourceAsStream("/jira/commands/fix_issue.md")) {
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

        // Copy fix_issue.md from resources to .claude/commands/
        try (InputStream sourceStream = getClass().getResourceAsStream("/jira/agents/fix-issue.md")) {
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

        // Log diagnostic information
        System.out.println("=== UVX Path Detection Debug ===");
        System.out.println("OS Name: " + osName);
        System.out.println("Command: " + command);
        System.out.println("PATH Environment: " + System.getenv("PATH"));
        System.out.println("User Home: " + System.getProperty("user.home"));

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

        // Log command results
        System.out.println("Exit Code: " + exitCode);
        System.out.println("Command Output: '" + output.toString() + "'");
        System.out.println("Output Length: " + output.toString().trim().length());
        System.out.println("================================");

        if (exitCode != 0 || output.toString().trim().isEmpty()) {
            String errorMsg = "uvx가 설치되어 있지 않습니다.\n\n다음 명령어로 설치해주세요:\npip install uv\n\n" +
                "Debug Info:\n" +
                "- OS: " + osName + "\n" +
                "- Exit Code: " + exitCode + "\n" +
                "- Output: " + output.toString().trim() + "\n" +
                "- PATH: " + System.getenv("PATH");
            throw new Exception(errorMsg);
        }

        // Get first line (in case multiple paths are returned)
        String uvxPath = output.toString().trim().split("\n")[0].trim();
        System.out.println("Found uvx at: " + uvxPath);
        return uvxPath;
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

                if (mcpServers.has("atlassian-jira")) {
                    System.out.println("atlassian-jira already exists in .mcp.json, skipping setup");
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

        // Get uvx path
        System.out.println("Getting uvx path...");
        String uvxPath = getUvxPath();
        System.out.println("UVX Path found: " + uvxPath);

        // Read mcp-add-json.txt template from resources
        System.out.println("Reading MCP template from resources...");
        String mcpTemplate;
        try (InputStream templateStream = getClass().getResourceAsStream("/jira/mcp-add-json.txt")) {
            if (templateStream == null) {
                System.err.println("ERROR: MCP template file not found in plugin resources");
                throw new Exception("MCP template file not found in plugin resources");
            }
            mcpTemplate = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Template loaded");
        }

        // Replace placeholders with actual values
        System.out.println("Replacing template placeholders...");
        mcpTemplate = mcpTemplate.replace("<uvx_path>", uvxPath);
        mcpTemplate = mcpTemplate.replace("<jira_url>", jiraUrl.endsWith("/") ? jiraUrl : jiraUrl + "/");
        mcpTemplate = mcpTemplate.replace("<email>", username);
        mcpTemplate = mcpTemplate.replace("<access_token>", apiToken);

        // Escape single quotes in JSON content for shell command
        String escapedMcpTemplate = mcpTemplate.replace("'", "'\"'\"'");

        // Generate script
        String script = String.format("claude mcp add-json atlassian-jira '%s' --scope project",
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

            JOptionPane.showConfirmDialog(
                null,
                mainPanel,
                "Claude MCP 연결 스크립트",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
            );

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}
