package com.spectra.intellij.ai.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;

import javax.swing.*;
import java.awt.*;

public class JiraToolWindowContent {
    
    private final Project project;
    private JPanel contentPanel;
    private JLabel statusLabel;
    private JButton createIssueButton;
    private JButton refreshButton;
    private JTextArea infoArea;
    
    public JiraToolWindowContent(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        contentPanel = new JPanel(new BorderLayout());
        
        // Top panel with buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        createIssueButton = new JButton("Create Issue");
        createIssueButton.addActionListener(e -> createIssue());
        buttonPanel.add(createIssueButton);
        
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshStatus());
        buttonPanel.add(refreshButton);
        
        contentPanel.add(buttonPanel, BorderLayout.NORTH);
        
        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        contentPanel.add(statusLabel, BorderLayout.SOUTH);
        
        // Info area
        infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoArea.setText("Spectra Jira Plugin\n\nFeatures:\n- Create Jira issues with sprint selection\n\nConfigure your Jira connection in:\nFile > Settings > Tools > Spectra Jira Settings");
        
        JBScrollPane scrollPane = new JBScrollPane(infoArea);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        
        refreshStatus();
    }
    
    private void createIssue() {
        if (!isConfigured()) {
            showConfigurationError();
            return;
        }
        
        JiraService jiraService = getConfiguredJiraService();
        CreateIssueDialog dialog = new CreateIssueDialog(project, jiraService);
        if (dialog.showAndGet()) {
            JiraIssue issue = dialog.getIssue();
            System.out.println("Creating issue:");
            System.out.println("  Key: " + issue.getKey());
            System.out.println("  Summary: " + issue.getSummary());
            System.out.println("  Description: " + issue.getDescription());
            System.out.println("  Status: " + issue.getStatus());
            System.out.println("  Assignee: " + issue.getAssignee());
            System.out.println("  Reporter: " + issue.getReporter());
            System.out.println("  Priority: " + issue.getPriority());
            System.out.println("  IssueType: " + issue.getIssueType());
            System.out.println("  IssueTypeId: " + issue.getIssueTypeId());
            System.out.println("  SprintId: " + issue.getSprintId());
            System.out.println("  SprintName: " + issue.getSprintName());
            updateStatus("Creating issue...");
//            project in (10452)
//            {cf[10020]: 838}
            
            jiraService.createIssueAsync(issue)
                .thenAccept(createdIssue -> {
                    System.out.println("Issue created: " + createdIssue);
                    SwingUtilities.invokeLater(() -> {
                        String successMessage = "이슈가 등록되었습니다. (" + createdIssue.getKey() + ")";
                        updateStatus(successMessage);
                        updateInfoArea("Last created issue: " + createdIssue.getKey() + " - " + createdIssue.getSummary());
                        Messages.showInfoMessage(project, successMessage, "이슈 등록 성공");
                    });
                })
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Error creating issue");
                        Messages.showErrorDialog(project, "Failed to create issue: " + throwable.getMessage(), "Error");
                    });
                    return null;
                });
        }
    }
    
    private void refreshStatus() {
        updateStatus("Checking configuration...");
        
        SwingUtilities.invokeLater(() -> {
            JiraSettings settings = JiraSettings.getInstance();
            if (isConfigured()) {
                updateStatus("Connected to: " + settings.getJiraUrl());
                createIssueButton.setEnabled(true);
            } else {
                updateStatus("Not configured - Configure in Settings");
                createIssueButton.setEnabled(false);
            }
        });
    }
    
    private void updateStatus(String status) {
        statusLabel.setText(status);
    }
    
    private void updateInfoArea(String info) {
        infoArea.setText(info);
    }
    
    private boolean isConfigured() {
        JiraSettings settings = JiraSettings.getInstance();
        return settings.getJiraUrl() != null && !settings.getJiraUrl().trim().isEmpty() &&
               settings.getUsername() != null && !settings.getUsername().trim().isEmpty() &&
               settings.getApiToken() != null && !settings.getApiToken().trim().isEmpty();
    }
    
    private void showConfigurationError() {
        Messages.showWarningDialog(
            project,
            "Please configure Jira settings first in File > Settings > Tools > Spectra Jira Settings",
            "Jira Not Configured"
        );
    }
    
    private JiraService getConfiguredJiraService() {
        JiraSettings settings = JiraSettings.getInstance();
        JiraService jiraService = new JiraService();
        jiraService.configure(settings.getJiraUrl(), settings.getUsername(), settings.getApiToken());
        if (settings.getDefaultProjectKey() != null && !settings.getDefaultProjectKey().trim().isEmpty()) {
            jiraService.setProjectKey(settings.getDefaultProjectKey());
        }
        return jiraService;
    }
    
    public JComponent getContent() {
        return contentPanel;
    }
}