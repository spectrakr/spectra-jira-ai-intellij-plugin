package com.spectra.intellij.ai.dialog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.service.JiraService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AIPromptDialog extends DialogWrapper {

    // Command templates
    private static final String CLAUDE_COMMAND = "claude -p --permission-mode plan";
    private static final String GEMINI_COMMAND = "gemini --yolo";
    private static final String CODEX_COMMAND = "codex exec --skip-git-repo-check --yolo --json";

    private JTextArea promptArea;
    private JTextArea resultArea;
    private JTextArea responseArea;
    private JButton executeButton;
    private JComboBox<String> aiServiceComboBox;
    private JTextField commandField;
    private final JiraService jiraService;
    private final Project project;

    public AIPromptDialog(Project project, JiraService jiraService) {
        super(project);
        this.project = project;
        this.jiraService = jiraService;
        setTitle("AI 프롬프트로 생성");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;

        // Set preferred size for the dialog - increased height for response area
        panel.setPreferredSize(new Dimension(700, 700));

        // Prompt Label
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel("프롬프트 입력"), gbc);

        // Prompt Description
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel descriptionLabel = new JLabel("작업할 내용을 구체적으로 입력하세요. 내용을 기반으로 소스에서 작업할 내용을 생성합니다.");
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descriptionLabel.setForeground(new Color(100, 100, 100));
        panel.add(descriptionLabel, gbc);

        // Prompt TextArea
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0; gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        promptArea = new JTextArea(4, 50);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));
        JScrollPane promptScrollPane = new JScrollPane(promptArea);
        promptScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        promptScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(promptScrollPane, gbc);

        // AI Service Selection and Execute Button Panel
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 1.0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JPanel buttonPanel = new JPanel(new BorderLayout(5, 0));

        // Command field (left side)
        commandField = new JTextField();
        commandField.setEditable(true);
        commandField.setBackground(new Color(30, 30, 30)); // Dark gray background like terminal
        commandField.setForeground(new Color(220, 220, 220)); // Light gray text
        commandField.setCaretColor(new Color(220, 220, 220)); // Light caret color
        commandField.setFont(new Font("Monospaced", Font.PLAIN, 11));
        buttonPanel.add(commandField, BorderLayout.CENTER);

        // Right panel for ComboBox and Execute button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        // AI Service ComboBox
        aiServiceComboBox = new JComboBox<>(new String[]{"Codex", "Claude", "Gemini"});
        aiServiceComboBox.setPreferredSize(new Dimension(120, 25));
        aiServiceComboBox.addActionListener(e -> updateCommandField());
        rightPanel.add(aiServiceComboBox);

        // Execute Button
        executeButton = new JButton("확인");
        executeButton.addActionListener(e -> executePrompt());
        rightPanel.add(executeButton);

        buttonPanel.add(rightPanel, BorderLayout.EAST);

        panel.add(buttonPanel, gbc);

        // Initialize command field with default AI service
        updateCommandField();

        // Result Label
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 1.0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("실행 로그"), gbc);

        // Result TextArea
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 1.0; gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        resultArea = new JTextArea(6, 50);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setEditable(false);
        resultArea.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));

        // Terminal-like colors: dark background with light text
        resultArea.setBackground(new Color(30, 30, 30)); // Dark gray background like terminal
        resultArea.setForeground(new Color(220, 220, 220)); // Light gray text
        resultArea.setCaretColor(new Color(220, 220, 220)); // Light caret color

        // Use monospace font like terminal
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane resultScrollPane = new JScrollPane(resultArea);
        resultScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(resultScrollPane, gbc);

        // Response Label
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 1.0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("응답 내용:"), gbc);

        // Response TextArea
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 1.0; gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        responseArea = new JTextArea(8, 50);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        responseArea.setEditable(false);
        responseArea.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));

        // Terminal-like colors: dark background with light text
        responseArea.setBackground(new Color(30, 30, 30)); // Dark gray background like terminal
        responseArea.setForeground(new Color(220, 220, 220)); // Light gray text
        responseArea.setCaretColor(new Color(220, 220, 220)); // Light caret color

        // Use monospace font like terminal
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane responseScrollPane = new JScrollPane(responseArea);
        responseScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        responseScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(responseScrollPane, gbc);

        return panel;
    }

    @Override
    protected Action[] createActions() {
        // Only show Close button at the bottom
        return new Action[]{new DialogWrapperAction("닫기") {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                close(OK_EXIT_CODE);
            }
        }};
    }

    private void updateCommandField() {
        String selectedAI = (String) aiServiceComboBox.getSelectedItem();
        if (selectedAI == null) {
            return;
        }

        String commandTemplate;
        switch (selectedAI) {
            case "Claude":
                commandTemplate = CLAUDE_COMMAND + " \"프롬프트\"";
                break;
            case "Gemini":
                commandTemplate = GEMINI_COMMAND + " \"프롬프트\"";
                break;
            case "Codex":
            default:
                commandTemplate = CODEX_COMMAND + " \"프롬프트\"";
                break;
        }

        commandField.setText(commandTemplate);
    }

    private void executePrompt() {
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            Messages.showWarningDialog(project, "프롬프트를 입력하세요.", "입력 필요");
            promptArea.requestFocus();
            return;
        }

        // Disable button during processing
        executeButton.setEnabled(false);
        executeButton.setText("처리 중...");
        resultArea.setText("AI가 프롬프트를 처리하고 있습니다...");

        // Execute command based on selected AI
        String selectedAI = (String) aiServiceComboBox.getSelectedItem();
        switch (selectedAI) {
            case "Claude":
                executeClaudeCommand(prompt);
                break;
            case "Gemini":
                executeGeminiCommand(prompt);
                break;
            case "Codex":
            default:
                executeCodexCommand(prompt);
                break;
        }
    }

    private void executeCodexCommand(String prompt) {
        try {
            // Get command from command field (user may have modified it)
            String baseCommand = commandField.getText().replace("\"프롬프트\"", "").trim();

            // Escape double quotes in prompt
            String escapedPrompt = prompt.replace("\"", "\\\"");

            // Build the command using shell to properly handle terminal requirements
            ProcessBuilder processBuilder = new ProcessBuilder();
            String command = String.format("%s \"%s\"", baseCommand, escapedPrompt);

            System.out.println("command: " + command);
            System.out.println("Base command from field: " + baseCommand);
            processBuilder.command("sh", "-c", command);

            // Set working directory to project root
            if (project.getBasePath() != null) {
                processBuilder.directory(new java.io.File(project.getBasePath()));
            }

            // Set environment variables to simulate terminal
            java.util.Map<String, String> env = processBuilder.environment();
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("FORCE_COLOR", "1");

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);

            // Start the process
            Process process = processBuilder.start();

            // Read the output in a separate thread to avoid blocking
            new Thread(() -> {
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );

                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");

                        // Update result area in real-time
                        final String currentOutput = output.toString();
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(currentOutput);
                            // Auto-scroll to bottom
                            resultArea.setCaretPosition(resultArea.getDocument().getLength());
                        });
                    }

                    // Wait for process to complete
                    int exitCode = process.waitFor();

                    final String finalOutput = output.toString();
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");

                        if (exitCode == 0) {
                            if (finalOutput.trim().isEmpty()) {
                                resultArea.setText("Codex 명령어가 실행되었지만 출력이 없습니다.");
                            } else {
                                resultArea.setText(finalOutput);
                                // Parse JSON and extract agent_message.text
                                parseAndDisplayResponse(finalOutput);
                            }
                        } else {
                            resultArea.setText("Codex 명령어 실행 실패 (종료 코드: " + exitCode + ")\n\n" + finalOutput);
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");
                        resultArea.setText("Codex 명령어 실행 중 오류 발생:\n" + e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                executeButton.setEnabled(true);
                executeButton.setText("확인");
                resultArea.setText("Codex 명령어 실행 중 오류 발생:\n" + e.getMessage());
            });
        }
    }

    private void parseClaudeResponse(String jsonOutput) {
        try {
            System.out.println("Claude raw output: " + jsonOutput);

            // Find the first '{' to start JSON parsing (skip any non-JSON prefix)
            int jsonStart = jsonOutput.indexOf('{');
            if (jsonStart == -1) {
                // If no JSON found, treat the entire output as the response
                responseArea.setText(jsonOutput.trim().isEmpty() ? "응답이 비어있습니다." : jsonOutput);
                return;
            }

            // Find the last '}' to get complete JSON
            int jsonEnd = jsonOutput.lastIndexOf('}');
            if (jsonEnd == -1) {
                responseArea.setText("JSON 형식이 완전하지 않습니다.");
                return;
            }

            String jsonString = jsonOutput.substring(jsonStart, jsonEnd + 1);
            System.out.println("Extracted JSON: " + jsonString);

            // Parse JSON
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();

            // Extract "result" field
            if (jsonObject.has("result")) {
                String resultText = jsonObject.get("result").getAsString();
                System.out.println("Extracted result: " + resultText);
                responseArea.setText(resultText);
            } else {
                // If no "result" field, show all fields for debugging
                StringBuilder allFields = new StringBuilder("응답에 result 필드가 없습니다.\n사용 가능한 필드:\n");
                jsonObject.keySet().forEach(key -> {
                    allFields.append("- ").append(key).append(": ").append(jsonObject.get(key)).append("\n");
                });
                responseArea.setText(allFields.toString());
            }
        } catch (Exception e) {
            String errorMsg = "JSON 파싱 오류: " + e.getMessage() + "\n\n원본 출력:\n" + jsonOutput;
            responseArea.setText(errorMsg);
            System.err.println("Claude JSON 파싱 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseAndDisplayResponse(String jsonOutput) {
        try {
            // Split by lines (JSON Lines format)
            String[] lines = jsonOutput.split("\n");
            StringBuilder agentMessages = new StringBuilder();

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("{")) {
                    continue;
                }

                try {
                    // Parse each JSON line
                    JsonObject jsonObject = JsonParser.parseString(line).getAsJsonObject();

                    // Check if this is an item.completed event
                    if (jsonObject.has("type") && "item.completed".equals(jsonObject.get("type").getAsString())) {
                        // Check if it has an item with type agent_message
                        if (jsonObject.has("item")) {
                            JsonObject item = jsonObject.getAsJsonObject("item");
                            if (item.has("type") && "agent_message".equals(item.get("type").getAsString())) {
                                // Extract the text
                                if (item.has("text")) {
                                    String text = item.get("text").getAsString();
                                    if (agentMessages.length() > 0) {
                                        agentMessages.append("\n\n");
                                    }
                                    agentMessages.append(text);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid JSON lines
                    System.err.println("라인 파싱 실패: " + line + " - " + e.getMessage());
                }
            }

            if (agentMessages.length() > 0) {
                responseArea.setText(agentMessages.toString());
            } else {
                responseArea.setText("agent_message를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            responseArea.setText("JSON 파싱 오류: " + e.getMessage());
            System.err.println("JSON 파싱 실패: " + e.getMessage());
        }
    }

    private void executeClaudeCommand(String prompt) {
        try {
            // Get command from command field (user may have modified it)
            String baseCommand = commandField.getText().replace("\"프롬프트\"", "").trim();

            // Escape double quotes in prompt
            String escapedPrompt = prompt.replace("\"", "\\\"");

            // Build the command using shell
            ProcessBuilder processBuilder = new ProcessBuilder();
            String command = String.format("%s \"%s\"", baseCommand, escapedPrompt);

            System.out.println("=== Claude Command Execution ===");
            System.out.println("command: " + command);
            System.out.println("Base command from field: " + baseCommand);
            System.out.println("Original prompt: " + prompt);
            System.out.println("Escaped prompt: " + escapedPrompt);

            processBuilder.command("sh", "-c", command);

            // Set working directory to project root
            if (project.getBasePath() != null) {
                processBuilder.directory(new java.io.File(project.getBasePath()));
                System.out.println("Setting working directory to project root: " + project.getBasePath());
            }

            // Set environment variables to simulate terminal
            java.util.Map<String, String> env = processBuilder.environment();
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("FORCE_COLOR", "1");

            System.out.println("Working directory: " + processBuilder.directory());
            System.out.println("Command array: " + processBuilder.command());

            // Redirect error stream to output stream (same as Codex)
            processBuilder.redirectErrorStream(true);

            // Start the process
            System.out.println("Starting process...");
            Process process = processBuilder.start();
            System.out.println("Process started successfully");

            // Close stdin to signal that no input will be provided
            process.getOutputStream().close();
            System.out.println("Process stdin closed");

            // Capture output stream before starting thread
            final java.io.InputStream inputStream = process.getInputStream();

            // Read the output in a separate thread
            new Thread(() -> {
                try {
                    System.out.println("Claude process started, reading output with timeout...");

                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                    );

                    StringBuilder output = new StringBuilder();
                    char[] buffer = new char[1024];
                    int charsRead;
                    long startTime = System.currentTimeMillis();
                    long timeout = 60000; // 60 seconds timeout
                    int idleCount = 0;
                    int maxIdleCount = 100; // 10 seconds of idle (100 * 100ms)

                    System.out.println("Starting output read loop...");

                    // Read output with non-blocking approach
                    while (process.isAlive()) {
                        if (reader.ready()) {
                            charsRead = reader.read(buffer);
                            if (charsRead > 0) {
                                String chunk = new String(buffer, 0, charsRead);
                                output.append(chunk);
                                System.out.print(chunk);
                                idleCount = 0; // Reset idle counter when data received

                                // Update UI in real-time
                                final String currentOutput = output.toString();
                                SwingUtilities.invokeLater(() -> {
                                    resultArea.setText(currentOutput);
                                    resultArea.setCaretPosition(resultArea.getDocument().getLength());
                                });
                            }
                        } else {
                            // Wait a bit for more output
                            Thread.sleep(100);
                            idleCount++;

                            // Check for idle timeout - if no output for 10 seconds and we have some output, break
                            if (idleCount >= maxIdleCount && output.length() > 0) {
                                System.out.println("\nIdle timeout reached with output, stopping read loop");
                                break;
                            }

                            // Check for total timeout
                            if (System.currentTimeMillis() - startTime > timeout) {
                                System.out.println("\nTotal timeout reached, stopping read loop");
                                break;
                            }
                        }
                    }

                    System.out.println("\nProcess alive: " + process.isAlive());

                    // Final read to catch any remaining output
                    while (reader.ready()) {
                        charsRead = reader.read(buffer);
                        if (charsRead > 0) {
                            String chunk = new String(buffer, 0, charsRead);
                            output.append(chunk);
                            System.out.print(chunk);
                        }
                    }

                    // Try to wait for process to complete
                    boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    int exitCode = finished ? process.exitValue() : -1;

                    if (!finished) {
                        System.out.println("\nProcess did not complete, destroying...");
                        process.destroyForcibly();
                    }

                    System.out.println("\nClaude process exit code: " + exitCode);
                    System.out.println("Output reading completed. Total length: " + output.length());

                    final String finalOutput = output.toString();
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");

                        System.out.println("exitCode : " + exitCode);

                        if (exitCode == 0 || exitCode == -1) {  // Accept -1 for timeout cases
                            if (finalOutput.trim().isEmpty()) {
                                resultArea.setText("Claude 명령어가 실행되었지만 출력이 없습니다.");
                                responseArea.setText("출력이 비어있습니다.");
                            } else {
                                resultArea.setText(finalOutput);
                                // Display plain text output directly in response area
                                responseArea.setText(finalOutput);
                            }
                        } else {
                            resultArea.setText("Claude 명령어 실행 실패 (종료 코드: " + exitCode + ")\n\n" + finalOutput);
                            responseArea.setText("실행 실패");
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");
                        resultArea.setText("Claude 명령어 실행 중 오류 발생:\n" + e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                executeButton.setEnabled(true);
                executeButton.setText("확인");
                resultArea.setText("Claude 명령어 실행 중 오류 발생:\n" + e.getMessage());
            });
        }
    }

    private void executeGeminiCommand(String prompt) {
        try {
            // Get command from command field (user may have modified it)
            String baseCommand = commandField.getText().replace("\"프롬프트\"", "").trim();

            // Escape double quotes in prompt
            String escapedPrompt = prompt.replace("\"", "\\\"");

            // Build the command using shell
            ProcessBuilder processBuilder = new ProcessBuilder();
            String command = String.format("%s \"%s\"", baseCommand, escapedPrompt);

            System.out.println("command: " + command);
            System.out.println("Base command from field: " + baseCommand);
            processBuilder.command("sh", "-c", command);

            // Set working directory to project root
            if (project.getBasePath() != null) {
                processBuilder.directory(new java.io.File(project.getBasePath()));
            }

            // Set environment variables to simulate terminal
            java.util.Map<String, String> env = processBuilder.environment();
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("FORCE_COLOR", "1");

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);

            // Start the process
            Process process = processBuilder.start();

            // Read the output in a separate thread
            new Thread(() -> {
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );

                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");

                        // Update result area in real-time
                        final String currentOutput = output.toString();
                        SwingUtilities.invokeLater(() -> {
                            resultArea.setText(currentOutput);
                            resultArea.setCaretPosition(resultArea.getDocument().getLength());
                        });
                    }

                    // Wait for process to complete
                    int exitCode = process.waitFor();

                    final String finalOutput = output.toString();
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");

                        if (exitCode == 0) {
                            resultArea.setText(finalOutput);
                            // For Gemini, the text output is the response
                            responseArea.setText(finalOutput);
                        } else {
                            resultArea.setText("Gemini 명령어 실행 실패 (종료 코드: " + exitCode + ")\n\n" + finalOutput);
                        }
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        executeButton.setEnabled(true);
                        executeButton.setText("확인");
                        resultArea.setText("Gemini 명령어 실행 중 오류 발생:\n" + e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                executeButton.setEnabled(true);
                executeButton.setText("확인");
                resultArea.setText("Gemini 명령어 실행 중 오류 발생:\n" + e.getMessage());
            });
        }
    }

    public String getGeneratedResult() {
        // Return the parsed response text instead of raw output
        String response = responseArea.getText();
        if (response != null && !response.trim().isEmpty() &&
            !response.contains("오류") && !response.contains("없습니다")) {
            return response;
        }
        return resultArea.getText();
    }
}
