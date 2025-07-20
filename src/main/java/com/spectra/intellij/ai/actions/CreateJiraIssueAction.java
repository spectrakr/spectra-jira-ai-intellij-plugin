package com.spectra.intellij.ai.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import org.jetbrains.annotations.NotNull;

public class CreateJiraIssueAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        JiraSettings settings = JiraSettings.getInstance();
        if (!isConfigured(settings)) {
            Messages.showWarningDialog(
                project,
                "Please configure Jira settings first in File > Settings > Tools > Spectra Jira Settings",
                "Jira Not Configured"
            );
            return;
        }
        
        JiraService jiraService = new JiraService();
        jiraService.configure(settings.getJiraUrl(), settings.getUsername(), settings.getApiToken());
        if (settings.getDefaultProjectKey() != null && !settings.getDefaultProjectKey().trim().isEmpty()) {
            jiraService.setProjectKey(settings.getDefaultProjectKey());
        }
        
        CreateIssueDialog dialog = new CreateIssueDialog(project, jiraService);
        if (dialog.showAndGet()) {
            JiraIssue issue = dialog.getIssue();
            createIssue(project, issue, jiraService);
        }
    }
    
    private boolean isConfigured(JiraSettings settings) {
        return settings.getJiraUrl() != null && !settings.getJiraUrl().trim().isEmpty() &&
               settings.getUsername() != null && !settings.getUsername().trim().isEmpty() &&
               settings.getApiToken() != null && !settings.getApiToken().trim().isEmpty();
    }
    
    private void createIssue(Project project, JiraIssue issue, JiraService jiraService) {
        jiraService.createIssueAsync(issue)
            .thenAccept(createdIssue -> {
                Messages.showInfoMessage(
                    project,
                    "Issue created successfully: " + createdIssue.getKey(),
                    "Issue Created"
                );
            })
            .exceptionally(throwable -> {
                Messages.showErrorDialog(
                    project,
                    "Failed to create issue: " + throwable.getMessage(),
                    "Error"
                );
                return null;
            });
    }
}