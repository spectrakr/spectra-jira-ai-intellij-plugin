package com.spectra.intellij.ai.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.ui.IssueTableCellRenderer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class JiraToolWindowContent {
    
    private final Project project;
    private JPanel contentPanel;
    private JLabel statusLabel;
    private JButton createIssueButton;
    private JButton refreshButton;
    private JButton settingsButton;
    
    // 3-panel layout components
    private JList<JiraSprint> sprintList;
    private DefaultListModel<JiraSprint> sprintListModel;
    private JBTable issueTable;
    private DefaultTableModel issueTableModel;
    private DefaultTableModel originalIssueTableModel; // Store unfiltered data
    private JTextArea issueDetailArea;
    
    // Filter components
    private JComboBox<String> issueTypeFilter;
    private JComboBox<String> assigneeFilter;
    private JComboBox<String> statusFilter;
    
    public JiraToolWindowContent(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        contentPanel = new JPanel(new BorderLayout());
        
        // Top panel with filters on left and buttons on right
        JPanel topPanel = createTopPanel();
        contentPanel.add(topPanel, BorderLayout.NORTH);
        
        // Main 3-panel layout
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Left panel - Sprint list
        JPanel sprintPanel = new JPanel(new BorderLayout());
        sprintPanel.setBorder(BorderFactory.createTitledBorder("Sprints"));
        sprintPanel.setPreferredSize(new Dimension(200, 0));
        
        sprintListModel = new DefaultListModel<>();
        sprintList = new JList<>(sprintListModel);
        sprintList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sprintList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                JiraSprint selectedSprint = sprintList.getSelectedValue();
                if (selectedSprint != null) {
                    loadSprintIssues(selectedSprint.getId());
                }
            }
        });
        
        JBScrollPane sprintScrollPane = new JBScrollPane(sprintList);
        sprintPanel.add(sprintScrollPane, BorderLayout.CENTER);
        
        mainPanel.add(sprintPanel, BorderLayout.WEST);
        
        // Center panel - Issue table
        JPanel issuePanel = new JPanel(new BorderLayout());
        issuePanel.setBorder(BorderFactory.createTitledBorder("Issues"));
        
        issueTableModel = new DefaultTableModel(
            new String[]{"Key", "Summary", "Status", "Story Points", "Assignee"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        originalIssueTableModel = new DefaultTableModel(
            new String[]{"Key", "Summary", "Status", "Story Points", "Assignee", "IssueType"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        issueTable = new JBTable(issueTableModel);
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Set custom renderer for Key column to show icons
        IssueTableCellRenderer renderer = new IssueTableCellRenderer();
        issueTable.getColumnModel().getColumn(0).setCellRenderer(renderer); // Key column
        
        issueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = issueTable.getSelectedRow();
                if (selectedRow >= 0) {
                    String issueKey = (String) issueTableModel.getValueAt(selectedRow, 0);
                    loadIssueDetail(issueKey);
                }
            }
        });
        
        JBScrollPane issueScrollPane = new JBScrollPane(issueTable);
        issuePanel.add(issueScrollPane, BorderLayout.CENTER);
        
        mainPanel.add(issuePanel, BorderLayout.CENTER);
        
        // Right panel - Issue detail
        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.setBorder(BorderFactory.createTitledBorder("Issue Detail"));
        detailPanel.setPreferredSize(new Dimension(300, 0));
        
        issueDetailArea = new JTextArea();
        issueDetailArea.setEditable(false);
        issueDetailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        issueDetailArea.setText("Select an issue to view details");
        
        JBScrollPane detailScrollPane = new JBScrollPane(issueDetailArea);
        detailPanel.add(detailScrollPane, BorderLayout.CENTER);
        
        mainPanel.add(detailPanel, BorderLayout.EAST);
        
        contentPanel.add(mainPanel, BorderLayout.CENTER);
        
        // Bottom status panel
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        contentPanel.add(statusLabel, BorderLayout.SOUTH);
        
        refreshStatus();
        loadSprints();
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Left side - Filter panel
        JPanel filterPanel = createFilterPanel();
        topPanel.add(filterPanel, BorderLayout.WEST);
        
        // Right side - Action buttons
        JPanel buttonPanel = createButtonPanel();
        topPanel.add(buttonPanel, BorderLayout.EAST);
        
        return topPanel;
    }
    
    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        
        // Issue Type filter
        filterPanel.add(new JLabel("Type:"));
        issueTypeFilter = new JComboBox<>();
        issueTypeFilter.addItem("All");
        issueTypeFilter.addActionListener(e -> applyFilters());
        filterPanel.add(issueTypeFilter);
        
        // Assignee filter
        filterPanel.add(new JLabel("Assignee:"));
        assigneeFilter = new JComboBox<>();
        assigneeFilter.addItem("All");
        assigneeFilter.addActionListener(e -> applyFilters());
        filterPanel.add(assigneeFilter);
        
        // Status filter
        filterPanel.add(new JLabel("Status:"));
        statusFilter = new JComboBox<>();
        statusFilter.addItem("All");
        statusFilter.addActionListener(e -> applyFilters());
        filterPanel.add(statusFilter);
        
        // Clear filters button
        JButton clearFiltersButton = new JButton("Clear");
        clearFiltersButton.addActionListener(e -> clearFilters());
        filterPanel.add(clearFiltersButton);
        
        return filterPanel;
    }
    
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        
        createIssueButton = new JButton("Create Issue");
        createIssueButton.addActionListener(e -> createIssue());
        buttonPanel.add(createIssueButton);
        
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> {
            refreshStatus();
            loadSprints();
        });
        buttonPanel.add(refreshButton);
        
        settingsButton = new JButton("⚙");
        settingsButton.setToolTipText("Jira Settings");
        settingsButton.addActionListener(e -> showSettingsDialog());
        settingsButton.setPreferredSize(new Dimension(30, settingsButton.getPreferredSize().height));
        buttonPanel.add(settingsButton);
        
        return buttonPanel;
    }
    
    private void applyFilters() {
        String selectedIssueType = (String) issueTypeFilter.getSelectedItem();
        String selectedAssignee = (String) assigneeFilter.getSelectedItem();
        String selectedStatus = (String) statusFilter.getSelectedItem();
        
        // Clear current table
        issueTableModel.setRowCount(0);
        IssueTableCellRenderer renderer = (IssueTableCellRenderer) issueTable.getColumnModel().getColumn(0).getCellRenderer();
        
        int displayRow = 0;
        for (int i = 0; i < originalIssueTableModel.getRowCount(); i++) {
            String issueType = (String) originalIssueTableModel.getValueAt(i, 5); // IssueType column
            String assignee = (String) originalIssueTableModel.getValueAt(i, 4);  // Assignee column
            String status = (String) originalIssueTableModel.getValueAt(i, 2);    // Status column
            
            // Apply filters
            boolean passesFilter = true;
            
            if (!"All".equals(selectedIssueType) && !selectedIssueType.equals(issueType)) {
                passesFilter = false;
            }
            
            if (!"All".equals(selectedAssignee) && !selectedAssignee.equals(assignee)) {
                passesFilter = false;
            }
            
            if (!"All".equals(selectedStatus) && !selectedStatus.equals(status)) {
                passesFilter = false;
            }
            
            if (passesFilter) {
                // Set issue type for renderer
                renderer.setIssueTypeForRow(displayRow, issueType);
                
                // Add row to display table (exclude IssueType column)
                issueTableModel.addRow(new Object[]{
                    originalIssueTableModel.getValueAt(i, 0), // Key
                    originalIssueTableModel.getValueAt(i, 1), // Summary
                    originalIssueTableModel.getValueAt(i, 2), // Status
                    originalIssueTableModel.getValueAt(i, 3), // Story Points
                    originalIssueTableModel.getValueAt(i, 4)  // Assignee
                });
                displayRow++;
            }
        }
        
        updateStatus("Filtered " + issueTableModel.getRowCount() + " issues");
    }
    
    private void clearFilters() {
        issueTypeFilter.setSelectedItem("All");
        assigneeFilter.setSelectedItem("All");
        statusFilter.setSelectedItem("All");
        applyFilters();
    }
    
    private void clearFilterOptions() {
        // Remove all items except "All"
        issueTypeFilter.removeAllItems();
        issueTypeFilter.addItem("All");
        
        assigneeFilter.removeAllItems();
        assigneeFilter.addItem("All");
        
        statusFilter.removeAllItems();
        statusFilter.addItem("All");
    }
    
    private void addToFilterOptions(String issueType, String assignee, String status) {
        // Add issue type to filter if not already present
        if (!issueType.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < issueTypeFilter.getItemCount(); i++) {
                if (issueType.equals(issueTypeFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                issueTypeFilter.addItem(issueType);
            }
        }
        
        // Add assignee to filter if not already present
        if (!assignee.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < assigneeFilter.getItemCount(); i++) {
                if (assignee.equals(assigneeFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                assigneeFilter.addItem(assignee);
            }
        }
        
        // Add status to filter if not already present
        if (!status.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < statusFilter.getItemCount(); i++) {
                if (status.equals(statusFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                statusFilter.addItem(status);
            }
        }
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
    
    private void loadSprints() {
        if (!isConfigured()) {
            updateStatus("Configure Jira connection using the settings button (⚙).");
            return;
        }
        
        JiraSettings settings = JiraSettings.getInstance();
        String boardId = settings.getDefaultBoardId();
        
        if (boardId == null || boardId.trim().isEmpty()) {
            updateStatus("Board ID not configured. Please set Board ID in settings.");
            return;
        }
        
        updateStatus("Loading sprints from board: " + boardId + "...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getSprintsAsync(boardId)
            .thenAccept(sprints -> {
                SwingUtilities.invokeLater(() -> {
                    sprintListModel.clear();
                    for (JiraSprint sprint : sprints) {
                        sprintListModel.addElement(sprint);
                    }
                    updateStatus("Loaded " + sprints.size() + " sprints");
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    sprintListModel.clear();
                    updateStatus("Error loading sprints: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void loadSprintIssues(String sprintId) {
        updateStatus("Loading issues from sprint: " + sprintId + "...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getSprintIssuesAsync(sprintId)
            .thenAccept(issues -> {
                SwingUtilities.invokeLater(() -> {
                    // Clear both tables
                    issueTableModel.setRowCount(0);
                    originalIssueTableModel.setRowCount(0);
                    
                    // Clear filter options
                    clearFilterOptions();
                    
                    IssueTableCellRenderer renderer = (IssueTableCellRenderer) issueTable.getColumnModel().getColumn(0).getCellRenderer();
                    
                    int row = 0;
                    for (JiraIssue issue : issues) {
                        String storyPointsStr = "";
                        if (issue.getStoryPoints() != null) {
                            storyPointsStr = issue.getStoryPoints().toString();
                        }
                        
                        String issueType = issue.getIssueType() != null ? issue.getIssueType() : "";
                        String assignee = issue.getAssignee() != null ? issue.getAssignee() : "";
                        String status = issue.getStatus() != null ? issue.getStatus() : "";
                        String summary = issue.getSummary() != null ? issue.getSummary() : "";
                        
                        // Add to original data model (includes IssueType column)
                        originalIssueTableModel.addRow(new Object[]{
                            issue.getKey(),
                            summary,
                            status,
                            storyPointsStr,
                            assignee,
                            issueType // Hidden column for filtering
                        });
                        
                        // Set issue type for renderer
                        renderer.setIssueTypeForRow(row, issueType);
                        
                        // Add to display table
                        issueTableModel.addRow(new Object[]{
                            issue.getKey(),
                            summary,
                            status,
                            storyPointsStr,
                            assignee
                        });
                        
                        // Populate filter options
                        addToFilterOptions(issueType, assignee, status);
                        
                        row++;
                    }
                    updateStatus("Loaded " + issues.size() + " issues from sprint");
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    issueTableModel.setRowCount(0);
                    updateStatus("Error loading sprint issues: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void loadIssueDetail(String issueKey) {
        updateStatus("Loading issue details: " + issueKey + "...");
        
        // Find the issue from current sprint issues
        JiraService jiraService = getConfiguredJiraService();
        // For now, we'll show basic info from the table
        // In a real implementation, you might want to fetch full issue details
        
        SwingUtilities.invokeLater(() -> {
            int selectedRow = issueTable.getSelectedRow();
            if (selectedRow >= 0) {
                StringBuilder detail = new StringBuilder();
                detail.append("Issue: ").append(issueTableModel.getValueAt(selectedRow, 0)).append("\n");
                detail.append("Summary: ").append(issueTableModel.getValueAt(selectedRow, 1)).append("\n");
                detail.append("Status: ").append(issueTableModel.getValueAt(selectedRow, 2)).append("\n");
                detail.append("Story Points: ").append(issueTableModel.getValueAt(selectedRow, 3)).append("\n");
                detail.append("Assignee: ").append(issueTableModel.getValueAt(selectedRow, 4)).append("\n");
                
                issueDetailArea.setText(detail.toString());
                updateStatus("Issue details loaded: " + issueKey);
            }
        });
    }
    
    private void showSettingsDialog() {
        JiraSettings settings = JiraSettings.getInstance();
        
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Jira URL
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Jira URL:"), gbc);
        
        JTextField urlField = new JTextField(settings.getJiraUrl() != null ? settings.getJiraUrl() : "", 30);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(urlField, gbc);
        
        // Username
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Username:"), gbc);
        
        JTextField userField = new JTextField(settings.getUsername() != null ? settings.getUsername() : "", 30);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(userField, gbc);
        
        // API Token
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("API Token:"), gbc);
        
        JPasswordField tokenField = new JPasswordField(settings.getApiToken() != null ? settings.getApiToken() : "", 30);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(tokenField, gbc);
        
        // Project ID
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Project ID:"), gbc);
        
        JTextField projectIdField = new JTextField(settings.getDefaultProjectKey() != null ? settings.getDefaultProjectKey() : "", 30);
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(projectIdField, gbc);
        
        // Board ID
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Board ID:"), gbc);
        
        JTextField boardIdField = new JTextField(settings.getDefaultBoardId() != null ? settings.getDefaultBoardId() : "", 30);
        gbc.gridx = 1; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(boardIdField, gbc);
        
        // Show dialog
        int result = JOptionPane.showConfirmDialog(
            contentPanel,
            panel,
            "Jira Settings",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            // Save settings
            settings.setJiraUrl(urlField.getText().trim());
            settings.setUsername(userField.getText().trim());
            settings.setApiToken(new String(tokenField.getPassword()).trim());
            settings.setDefaultProjectKey(projectIdField.getText().trim());
            settings.setDefaultBoardId(boardIdField.getText().trim());
            
            // Refresh status after saving
            refreshStatus();
            loadSprints();
            
            Messages.showInfoMessage(
                project,
                "Jira settings have been saved successfully.",
                "Settings Saved"
            );
        }
    }
    
    public JComponent getContent() {
        return contentPanel;
    }
}