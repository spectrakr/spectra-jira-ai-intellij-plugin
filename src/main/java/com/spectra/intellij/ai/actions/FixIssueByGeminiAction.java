package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.spectra.intellij.ai.service.AccessLogService;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.lang.reflect.Method;

public class FixIssueByGeminiAction extends AnAction {

    private String issueKey;
    private Project project;
    private AccessLogService accessLogService;

    public FixIssueByGeminiAction() {
        super("Fix issue (by Gemini)");
        initializeAccessLogService();
    }

    private void initializeAccessLogService() {
        try {
            JiraSettings settings = JiraSettings.getInstance();
            JiraService jiraService = new JiraService();
            jiraService.configure(settings.getJiraUrl(), settings.getUsername(), settings.getApiToken());
            this.accessLogService = new AccessLogService(
                    new okhttp3.OkHttpClient(),
                    new com.google.gson.Gson(),
                    jiraService
            );
        } catch (Exception e) {
            System.err.println("Failed to initialize AccessLogService: " + e.getMessage());
        }
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = this.project != null ? this.project : e.getProject();
        execute(project);
    }

    public void execute(Project project) {
        if (project == null || issueKey == null || issueKey.isEmpty()) {
            return;
        }

        try {
            // Get the configured Gemini command from settings
            JiraSettings settings = JiraSettings.getInstance();
            String commandTemplate = settings.getGeminiCommand();
            if (commandTemplate == null || commandTemplate.trim().isEmpty()) {
                commandTemplate = "gemini --yolo \"/fix-issue $issueKey\"";
            }

            // Replace $issueKey variable with actual issue key
            String command = commandTemplate.replace("$issueKey", issueKey);
            System.out.println("Executing command: " + command);

            if (accessLogService != null) {
                accessLogService.sendAccessLog("Fix issue (by Claude)", command);
            }

            // Open terminal and execute command
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);

                    // Set accessible to bypass module restrictions
                    Method createShellWidgetMethod = null;
                    Object widget = null;

                    try {
                        // Try new API (2023.3+)
                        createShellWidgetMethod = terminalManager.getClass().getMethod(
                            "createShellWidget",
                            String.class,
                            String.class,
                            boolean.class,
                            boolean.class
                        );
                        createShellWidgetMethod.setAccessible(true);

                        widget = createShellWidgetMethod.invoke(
                            terminalManager,
                            project.getBasePath(),
                            "Fix Issue: " + issueKey,
                            true,
                            true
                        );

                        // Try to execute command
                        Method sendCommandMethod = widget.getClass().getMethod("sendCommandToExecute", String.class);
                        sendCommandMethod.setAccessible(true);
                        sendCommandMethod.invoke(widget, command);
                    } catch (NoSuchMethodException e1) {
                        // Fallback: Try older API
                        try {
                            Method createLocalShellWidgetMethod = terminalManager.getClass().getMethod(
                                "createLocalShellWidget",
                                String.class,
                                String.class
                            );
                            createLocalShellWidgetMethod.setAccessible(true);

                            Object shellTerminalWidget = createLocalShellWidgetMethod.invoke(
                                terminalManager,
                                project.getBasePath(),
                                "Fix Issue: " + issueKey
                            );

                            // Execute command
                            Method executeCommandMethod = shellTerminalWidget.getClass().getMethod("executeCommand", String.class);
                            executeCommandMethod.setAccessible(true);
                            executeCommandMethod.invoke(shellTerminalWidget, command);
                        } catch (Exception e2) {
                            // If all else fails, just show the command to user
                            com.intellij.openapi.ui.Messages.showInfoMessage(
                                project,
                                "터미널에서 다음 명령어를 실행해주세요:\n\n" + command,
                                "Fix Issue: " + issueKey
                            );
                        }
                    } catch (IllegalAccessException e1) {
                        // Module access issue - show command to user
                        com.intellij.openapi.ui.Messages.showInfoMessage(
                            project,
                            "터미널에서 다음 명령어를 실행해주세요:\n\n" + command,
                            "Fix Issue: " + issueKey
                        );
                    }
                } catch (Exception ex) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Failed to execute command in terminal: " + ex.getMessage(),
                        "Error"
                    );
                }
            });
        } catch (Exception ex) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "Failed to execute command: " + ex.getMessage(),
                "Error"
            );
        }
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(issueKey != null && !issueKey.isEmpty());
    }
}
