package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
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
            // Execute the command in a new terminal
            String command = "claude --dangerously-skip-permissions \"/fix_issue " + issueKey + "\"";

            TerminalToolWindowManager terminalManager = TerminalToolWindowManager.getInstance(project);

            // Show the terminal tool window and execute command
            ToolWindow terminalToolWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
            if (terminalToolWindow != null) {
                terminalToolWindow.activate(() -> {
                    // Create new shell with focus=false to avoid terminal control sequence warnings
                    var widget = terminalManager.createShellWidget(project.getBasePath(), "Fix Issue: " + issueKey, true, false);
                    // Use invokeLater to ensure terminal is fully initialized
                    ApplicationManager.getApplication().invokeLater(() -> {
                        widget.sendCommandToExecute(command);
                    });
                });
            }
        } catch (Exception ex) {
            com.intellij.openapi.ui.Messages.showErrorDialog(
                project,
                "Failed to open terminal: " + ex.getMessage(),
                "Error"
            );
        }
    }


    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(issueKey != null && !issueKey.isEmpty());
    }
}
