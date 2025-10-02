package com.spectra.intellij.ai.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
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
            String basePath = project.getBasePath();
            if (basePath == null) {
                throw new Exception("Project path not found");
            }

            // Execute the command in terminal
            String command = "claude --dangerously-skip-permissions \"/fix_issue " + issueKey + "\"";

            // Open terminal and execute command
            ApplicationManager.getApplication().invokeLater(() -> {
                try {
                    // Activate terminal window first
                    ToolWindow terminalWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
                    if (terminalWindow != null) {
                        terminalWindow.activate(() -> {
                            // Execute command in background
                            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                                try {
                                    // Create command line
                                    String osName = System.getProperty("os.name").toLowerCase();
                                    GeneralCommandLine commandLine = new GeneralCommandLine();
                                    if (osName.contains("win")) {
                                        commandLine.setExePath("cmd");
                                        commandLine.addParameter("/c");
                                    } else {
                                        commandLine.setExePath("/bin/sh");
                                        commandLine.addParameter("-l");
                                        commandLine.addParameter("-c");
                                    }

                                    commandLine.addParameter(command);
                                    commandLine.setWorkDirectory(basePath);

                                    // Start process
                                    OSProcessHandler processHandler = new OSProcessHandler(commandLine);
                                    processHandler.addProcessListener(new ProcessAdapter() {
                                        @Override
                                        public void processTerminated(@NotNull ProcessEvent event) {
                                            ApplicationManager.getApplication().invokeLater(() -> {
                                                if (event.getExitCode() != 0) {
                                                    com.intellij.openapi.ui.Messages.showErrorDialog(
                                                        project,
                                                        "Command failed with exit code: " + event.getExitCode(),
                                                        "Error"
                                                    );
                                                }
                                            });
                                        }
                                    });
                                    processHandler.startNotify();
                                } catch (Exception ex) {
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        com.intellij.openapi.ui.Messages.showErrorDialog(
                                            project,
                                            "Failed to execute command: " + ex.getMessage(),
                                            "Error"
                                        );
                                    });
                                }
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
