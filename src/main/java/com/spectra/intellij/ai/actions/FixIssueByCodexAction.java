package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.lang.reflect.Method;

public class FixIssueByCodexAction extends AnAction {

    private String issueKey;
    private Project project;

    public FixIssueByCodexAction() {
        super("Fix issue (by Codex)");
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
            // Execute the command in terminal
            String command = "codex --dangerously-skip-permissions \"/fix_issue " + issueKey + "\"";
            System.out.println("Executing command: " + command);

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
