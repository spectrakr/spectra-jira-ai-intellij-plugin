package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
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
            // Execute the command in terminal
            String command = "claude --dangerously-skip-permissions \"/fix_issue " + issueKey + "\"";
            System.out.println("Executing command: " + command);

            // Open terminal and execute command
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);

                    // Create new terminal tab and execute command
                    TerminalWidget widget = terminalManager.createShellWidget(
                        project.getBasePath(),
                        "Fix Issue: " + issueKey,
                        true,
                        true
                    );

                    // Execute command using sendCommandToExecute
                    widget.sendCommandToExecute(command);
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
