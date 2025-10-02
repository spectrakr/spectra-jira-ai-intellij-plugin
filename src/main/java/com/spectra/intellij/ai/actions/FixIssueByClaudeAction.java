package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

public class FixIssueByClaudeAction extends AnAction {

    private String issueKey;
    private Project project;

    public FixIssueByClaudeAction() {
        super("Fix issue (by claude)");
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
            String basePath = project.getBasePath();
            if (basePath == null) {
                throw new Exception("Project path not found");
            }

            // Execute the command in terminal
            String command = "claude --dangerously-skip-permissions \"/fix_issue " + issueKey + "\"";

            // Open terminal and execute command
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // Use TerminalToolWindowManager to create terminal widget
                    TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);
                    if (terminalManager != null) {
                        // Create shell widget directly
                        var shellWidget = terminalManager.createShellWidget(basePath, "Fix Issue: " + issueKey, true, false);

                        // Execute command in the terminal widget
                        if (shellWidget != null) {
                            shellWidget.sendCommandToExecute(command);
                        }
                    }
                } catch (Exception ex) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Failed to open terminal: " + ex.getMessage(),
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
