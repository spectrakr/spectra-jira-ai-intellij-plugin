package com.spectra.intellij.ai.toolwindow;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.dialog.JiraIssueDetailDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.ui.IssueTableCellRenderer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    
    // Issue detail panel components
    private JPanel issueDetailPanel;
    private JTextField issueKeyField;
    private JTextField issueSummaryField;
    private JComboBox<String> issueStatusComboBox;
    private JTextArea issueDescriptionArea;
    private JComboBox<String> assigneeComboBox;
    private JLabel reporterLabel;
    private JTextField storyPointsField;
    private JButton saveIssueButton;
    private JButton cancelIssueButton;
    private JiraIssue currentEditingIssue;
    
    // Filter components
    private JComboBox<String> issueTypeFilter;
    private JComboBox<String> assigneeFilter;
    private JComboBox<String> statusFilter;
    
    // Cache for all project users for autocomplete
    private List<String> allProjectUsers = new ArrayList<>();
    private boolean isUpdatingAssigneeComboBox = false;
    private boolean isHandlingAssigneeSelection = false;
    
    private JsonObject currentUser;
    private String selectedAssigneeAccountId;
    private String selectedAssigneeDisplayName;
    
    public JiraToolWindowContent(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        contentPanel = new JPanel(new BorderLayout());
        
        // No top panel needed anymore
        
        // Main 3-panel layout with split panes
        
        // Left panel - Sprint list with settings button above
        JPanel sprintPanel = new JPanel(new BorderLayout());
        sprintPanel.setPreferredSize(new Dimension(180, 0));
        
        // Top section with settings button and sprints label
        JPanel topSection = new JPanel(new BorderLayout());
        
        // Add settings button at the top
        settingsButton = new JButton("Jira Setting");
        settingsButton.addActionListener(e -> showSettingsDialog());
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        settingsPanel.add(settingsButton);
        topSection.add(settingsPanel, BorderLayout.NORTH);
        
        // Add Sprints label below settings button
        JPanel sprintsLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        sprintsLabelPanel.setBorder(BorderFactory.createTitledBorder("Sprints"));
        topSection.add(sprintsLabelPanel, BorderLayout.CENTER);
        
        sprintPanel.add(topSection, BorderLayout.NORTH);
        
        sprintListModel = new DefaultListModel<>();
        sprintList = new JList<>(sprintListModel);
        sprintList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Set custom renderer to show only sprint name
        sprintList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof JiraSprint) {
                    JiraSprint sprint = (JiraSprint) value;
                    setText(sprint.getName()); // Only show name, not status
                }
                return this;
            }
        });
        
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
        
        // Center panel - Issue table with filter panel above
        JPanel issuePanel = new JPanel(new BorderLayout());
        issuePanel.setBorder(BorderFactory.createTitledBorder("Issues"));
        
        // Add filter panel above the issue table
        JPanel filterPanel = createFilterPanel();
        issuePanel.add(filterPanel, BorderLayout.NORTH);
        
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
        
        // Adjust column widths
        issueTable.getColumnModel().getColumn(0).setPreferredWidth(120);  // Key
        issueTable.getColumnModel().getColumn(0).setMaxWidth(120);
        issueTable.getColumnModel().getColumn(0).setMinWidth(120);
        
        // Summary column will take remaining space
        issueTable.getColumnModel().getColumn(1).setMinWidth(200);
        
        issueTable.getColumnModel().getColumn(2).setPreferredWidth(20);  // Status
        issueTable.getColumnModel().getColumn(2).setMaxWidth(100);
        issueTable.getColumnModel().getColumn(2).setMinWidth(60);
        
        issueTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Story Points
        issueTable.getColumnModel().getColumn(3).setMaxWidth(80);
        issueTable.getColumnModel().getColumn(3).setMinWidth(80);
        
        issueTable.getColumnModel().getColumn(4).setPreferredWidth(30);  // Assignee
        issueTable.getColumnModel().getColumn(4).setMaxWidth(120);
        issueTable.getColumnModel().getColumn(4).setMinWidth(80);
        
        // Set auto resize mode to make Summary column fill remaining space
        issueTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        
        issueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = issueTable.getSelectedRow();
                if (selectedRow >= 0) {
                    String issueKey = (String) issueTableModel.getValueAt(selectedRow, 0);
                    loadIssueForEditing(issueKey);
                } else {
                    clearIssueDetail();
                }
            }
        });
        
        // Add double-click handler to open detail dialog
        issueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.out.println("___ JiraToolWindowContent: Issue table mouse clicked, count: " + e.getClickCount());
                if (e.getClickCount() == 2) {
                    int selectedRow = issueTable.getSelectedRow();
                    System.out.println("___ JiraToolWindowContent: Double-click detected on row: " + selectedRow);
                    if (selectedRow >= 0) {
                        String issueKey = (String) issueTableModel.getValueAt(selectedRow, 0);
                        System.out.println("___ JiraToolWindowContent: Opening detail dialog for issue: " + issueKey);
                        openIssueDetailDialog(issueKey);
                    }
                }
            }
        });

        
        JBScrollPane issueScrollPane = new JBScrollPane(issueTable);
        issuePanel.add(issueScrollPane, BorderLayout.CENTER);
        
        // Right panel - Issue detail form with scroll
        issueDetailPanel = createIssueDetailPanel();
        JScrollPane detailScrollPane = new JScrollPane(issueDetailPanel);
        detailScrollPane.setPreferredSize(new Dimension(400, 0));
        detailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Create resizable split pane for issue list and detail
        JSplitPane issueDetailSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, issuePanel, detailScrollPane);
        issueDetailSplitPane.setResizeWeight(0.7); // 70% for issue list, 30% for detail
        issueDetailSplitPane.setDividerLocation(0.7);
        issueDetailSplitPane.setContinuousLayout(true);
        
        // Apply theme-appropriate colors for divider
        issueDetailSplitPane.setBackground(UIManager.getColor("Panel.background"));
        issueDetailSplitPane.setBorder(null);
        
        // Hide divider by making it transparent
        issueDetailSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        // Don't paint anything to make it invisible
                    }
                };
            }
        });
        
        // Create main split pane for sprint list and issue/detail panels
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sprintPanel, issueDetailSplitPane);
        mainSplitPane.setResizeWeight(0.0); // Keep sprint panel fixed size
        mainSplitPane.setDividerLocation(180);
        mainSplitPane.setContinuousLayout(true);
        
        // Apply theme-appropriate colors for main divider
        mainSplitPane.setBackground(UIManager.getColor("Panel.background"));
        mainSplitPane.setBorder(null);
        
        // Hide main divider by making it transparent
        mainSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        // Don't paint anything to make it invisible
                    }
                };
            }
        });
        
        contentPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        // Bottom status panel
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        contentPanel.add(statusLabel, BorderLayout.SOUTH);
        
        refreshStatus();
        loadSprints();
    }
    
    
    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Left side - Filter controls
        JPanel leftFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        // Issue Type filter (no label)
        issueTypeFilter = new JComboBox<>();
        issueTypeFilter.addItem("All");
        issueTypeFilter.addActionListener(e -> applyFilters());
        leftFilters.add(issueTypeFilter);
        
        // Assignee filter (no label)
        assigneeFilter = new JComboBox<>();
        assigneeFilter.addItem("All");
        assigneeFilter.addActionListener(e -> applyFilters());
        leftFilters.add(assigneeFilter);
        
        // Status filter (no label)
        statusFilter = new JComboBox<>();
        statusFilter.addItem("All");
        statusFilter.addActionListener(e -> applyFilters());
        leftFilters.add(statusFilter);
        
        // Clear filters button
        JButton clearFiltersButton = new JButton("Clear");
        clearFiltersButton.addActionListener(e -> clearFilters());
        leftFilters.add(clearFiltersButton);
        
        filterPanel.add(leftFilters, BorderLayout.WEST);
        
        // Right side - Refresh and Create Issue buttons
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton refreshFilterButton = new JButton("⟲");
        refreshFilterButton.setToolTipText("Refresh");
        refreshFilterButton.addActionListener(e -> {
            refreshStatus();
            loadSprints();
        });
        refreshFilterButton.setPreferredSize(new Dimension(30, refreshFilterButton.getPreferredSize().height));
        rightButtons.add(refreshFilterButton);
        
        createIssueButton = new JButton("Create Issue");
        createIssueButton.addActionListener(e -> createIssue());
        rightButtons.add(createIssueButton);
        
        filterPanel.add(rightButtons, BorderLayout.EAST);
        
        return filterPanel;
    }
    
    private JPanel createIssueDetailPanel() {
        JPanel detailPanel = new JPanel(new BorderLayout());
        
        // Main form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Issue Key (no label)
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueKeyField = new JTextField();
        issueKeyField.setEditable(false);
        issueKeyField.setFont(issueKeyField.getFont().deriveFont(Font.BOLD, 14f));
        issueKeyField.setBackground(formPanel.getBackground());
        issueKeyField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        formPanel.add(issueKeyField, gbc);
        
        // Summary (no label)
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueSummaryField = new JTextField();
        issueSummaryField.setFont(issueSummaryField.getFont().deriveFont(Font.BOLD, 12f));
        formPanel.add(issueSummaryField, gbc);
        
        // Status (no label)
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueStatusComboBox = new JComboBox<>();
        formPanel.add(issueStatusComboBox, gbc);
        
        // Description (no label)
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.gridwidth = 2;
        issueDescriptionArea = new JTextArea(10, 20);
        issueDescriptionArea.setLineWrap(true);
        issueDescriptionArea.setWrapStyleWord(true);
        JScrollPane descScrollPane = new JScrollPane(issueDescriptionArea);
        descScrollPane.setMinimumSize(new Dimension(0, 150));
        descScrollPane.setPreferredSize(new Dimension(0, 200));
        formPanel.add(descScrollPane, gbc);
        
        // 세부사항 영역
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.weighty = 0; gbc.gridwidth = 2;
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder("세부사항"));
        
        GridBagConstraints detailGbc = new GridBagConstraints();
        detailGbc.insets = new Insets(3, 5, 3, 5);
        detailGbc.anchor = GridBagConstraints.WEST;
        
        // 담당자
        detailGbc.gridx = 0; detailGbc.gridy = 0;
        detailsPanel.add(new JLabel("담당자:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        
        // Create simple assignee dropdown
        assigneeComboBox = new JComboBox<>();
        // Initialize with default items before adding action listener
        assigneeComboBox.addItem("Select");
        assigneeComboBox.addItem("me");
        assigneeComboBox.setSelectedItem("me");
        // Add action listener after initialization
        assigneeComboBox.addActionListener(e -> handleAssigneeSelection());
        detailsPanel.add(assigneeComboBox, detailGbc);
        
        // 보고자
        detailGbc.gridx = 0; detailGbc.gridy = 1; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0;
        detailsPanel.add(new JLabel("보고자:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        reporterLabel = new JLabel();
        detailsPanel.add(reporterLabel, detailGbc);
        
        // Story Points
        detailGbc.gridx = 0; detailGbc.gridy = 2; detailGbc.fill = GridBagConstraints.NONE; detailGbc.weightx = 0;
        detailsPanel.add(new JLabel("Story Points:"), detailGbc);
        detailGbc.gridx = 1; detailGbc.fill = GridBagConstraints.HORIZONTAL; detailGbc.weightx = 1.0;
        storyPointsField = new JTextField();
        storyPointsField.setPreferredSize(new Dimension(100, storyPointsField.getPreferredSize().height));
        detailsPanel.add(storyPointsField, detailGbc);
        
        formPanel.add(detailsPanel, gbc);
        
        detailPanel.add(formPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        
        saveIssueButton = new JButton("Save");
        saveIssueButton.addActionListener(e -> saveCurrentIssue());
        saveIssueButton.setEnabled(false);
        buttonPanel.add(saveIssueButton);
        
        cancelIssueButton = new JButton("Cancel");
        cancelIssueButton.addActionListener(e -> cancelIssueEditing());
        cancelIssueButton.setEnabled(false);
        buttonPanel.add(cancelIssueButton);
        
        detailPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Initially show "Select an issue" message
        clearIssueDetail();
        
        return detailPanel;
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
//            System.out.println("story point: " + originalIssueTableModel.getValueAt(i, 3));
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
                clearIssueDetail();
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
                    clearIssueDetail();
                    
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
                    clearIssueDetail();
                    updateStatus("Error loading sprint issues: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void loadIssueForEditing(String issueKey) {
        if (!isConfigured()) {
            clearIssueDetail();
            return;
        }
        
        updateStatus("Loading issue details: " + issueKey + "...");
        
        JiraService jiraService = getConfiguredJiraService();
        
        // First load current user info if not already loaded
        if (currentUser == null) {
            jiraService.getCurrentUserAsync()
                .thenAccept(user -> {
                    SwingUtilities.invokeLater(() -> {
                        currentUser = user;
                        loadIssueForEditingAfterUserLoaded(issueKey, jiraService);
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        System.err.println("Failed to load current user: " + throwable.getMessage());
                        loadIssueForEditingAfterUserLoaded(issueKey, jiraService);
                    });
                    return null;
                });
        } else {
            loadIssueForEditingAfterUserLoaded(issueKey, jiraService);
        }
    }
    
    private void loadIssueForEditingAfterUserLoaded(String issueKey, JiraService jiraService) {
        jiraService.getIssueAsync(issueKey)
            .thenAccept(issue -> {
                SwingUtilities.invokeLater(() -> {
                    currentEditingIssue = issue;
                    populateIssueForm(issue);
                    
                    // Load available statuses
                    jiraService.getIssueStatusesAsync(issueKey)
                        .thenAccept(statuses -> {
                            SwingUtilities.invokeLater(() -> {
                                issueStatusComboBox.removeAllItems();
                                for (String status : statuses) {
                                    issueStatusComboBox.addItem(status);
                                }
                                
                                // Set current status
                                if (issue.getStatus() != null) {
                                    issueStatusComboBox.setSelectedItem(issue.getStatus());
                                }
                                
                                updateStatus("Issue details loaded: " + issueKey);
                            });
                        })
                        .exceptionally(throwable -> {
                            SwingUtilities.invokeLater(() -> {
                                // Add common statuses as fallback
                                issueStatusComboBox.removeAllItems();
                                issueStatusComboBox.addItem("To Do");
                                issueStatusComboBox.addItem("In Progress");
                                issueStatusComboBox.addItem("Done");
                                if (issue.getStatus() != null) {
                                    issueStatusComboBox.setSelectedItem(issue.getStatus());
                                }
                                updateStatus("Issue details loaded (status list unavailable): " + issueKey);
                            });
                            return null;
                        });
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error loading issue: " + throwable.getMessage());
                    clearIssueDetail();
                });
                return null;
            });
    }
    
    private void populateIssueForm(JiraIssue issue) {
        issueKeyField.setText(issue.getKey() != null ? issue.getKey() : "");
        issueSummaryField.setText(issue.getSummary() != null ? issue.getSummary() : "");
        issueDescriptionArea.setText(issue.getDescription() != null ? issue.getDescription() : "");
        
        // Populate detail fields
        // Initialize assignee selection based on current issue assignee
        initializeAssigneeDropdown(issue.getAssignee());
        reporterLabel.setText(issue.getReporter() != null ? issue.getReporter() : "없음");
        storyPointsField.setText(issue.getStoryPoints() != null ? issue.getStoryPoints().toString() : "");
        
        // Enable editing controls
        issueSummaryField.setEnabled(true);
        issueStatusComboBox.setEnabled(true);
        issueDescriptionArea.setEnabled(true);
        assigneeComboBox.setEnabled(true);
        storyPointsField.setEnabled(true);
        saveIssueButton.setEnabled(true);
        cancelIssueButton.setEnabled(true);
    }
    
    private void initializeAssigneeDropdown(String currentAssignee) {
        // Temporarily disable the action listener to prevent triggering dialog
        isHandlingAssigneeSelection = true;
        try {
            // Clear combo box and add default options
            assigneeComboBox.removeAllItems();
            assigneeComboBox.addItem("Select");
            assigneeComboBox.addItem("me");
            
            if (currentAssignee != null && !currentAssignee.trim().isEmpty()) {
                selectedAssigneeDisplayName = currentAssignee;
                
                // Check if current assignee is the logged-in user
                if (currentUser != null && currentAssignee.equals(currentUser.get("displayName").getAsString())) {
                    assigneeComboBox.setSelectedItem("me");
                    selectedAssigneeAccountId = currentUser.get("accountId").getAsString();
                } else {
                    // Add current assignee to dropdown and select it
                    assigneeComboBox.addItem(currentAssignee);
                    assigneeComboBox.setSelectedItem(currentAssignee);
                    
                    // Find the account ID for the current assignee
                    findAccountIdForSelectedUser(currentAssignee);
                }
            } else {
                // No current assignee - set to "me" instead of "Select" to avoid dialog
                assigneeComboBox.setSelectedItem("me");
                setAssigneeToCurrentUser();
            }
        } finally {
            isHandlingAssigneeSelection = false;
        }
    }
    
    private void findAccountIdForSelectedUser(String displayName) {
        if (!isConfigured()) {
            return;
        }
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.searchUsersAsync(displayName)
            .thenAccept(users -> {
                SwingUtilities.invokeLater(() -> {
                    for (JsonObject user : users) {
                        if (user.has("displayName") && displayName.equals(user.get("displayName").getAsString())) {
                            selectedAssigneeAccountId = user.get("accountId").getAsString();
                            break;
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    System.err.println("Failed to find account ID for user: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void clearIssueDetail() {
        currentEditingIssue = null;
        issueKeyField.setText("이슈를 선택하세요");
        issueSummaryField.setText("");
        issueDescriptionArea.setText("");
        issueStatusComboBox.removeAllItems();
        
        // Clear detail fields - temporarily disable action listener
        isHandlingAssigneeSelection = true;
        try {
            assigneeComboBox.removeAllItems();
            assigneeComboBox.addItem("Select");
            assigneeComboBox.addItem("me");
            assigneeComboBox.setSelectedItem("me"); // Default to "me" instead of "Select"
            setAssigneeToCurrentUser();
        } finally {
            isHandlingAssigneeSelection = false;
        }
        reporterLabel.setText("");
        storyPointsField.setText("");
        
        // Disable editing controls
        issueSummaryField.setEnabled(false);
        issueStatusComboBox.setEnabled(false);
        issueDescriptionArea.setEnabled(false);
        assigneeComboBox.setEnabled(false);
        storyPointsField.setEnabled(false);
        saveIssueButton.setEnabled(false);
        cancelIssueButton.setEnabled(false);
    }
    
    private void saveCurrentIssue() {
        if (currentEditingIssue == null) {
            return;
        }
        
        // Check if anything was modified
        boolean summaryChanged = !issueSummaryField.getText().trim().equals(currentEditingIssue.getSummary() != null ? currentEditingIssue.getSummary() : "");
        boolean statusChanged = !issueStatusComboBox.getSelectedItem().toString().equals(currentEditingIssue.getStatus() != null ? currentEditingIssue.getStatus() : "");
        boolean descriptionChanged = !issueDescriptionArea.getText().trim().equals(currentEditingIssue.getDescription() != null ? currentEditingIssue.getDescription() : "");
        
        // Check assignee changes
        String newAssignee = selectedAssigneeDisplayName != null ? selectedAssigneeDisplayName : "";
        String currentAssignee = currentEditingIssue.getAssignee() != null ? currentEditingIssue.getAssignee() : "";
        boolean assigneeChanged = !newAssignee.equals(currentAssignee);
        
        // Check story points changes
        String newStoryPoints = storyPointsField.getText().trim();
        String currentStoryPointsStr = currentEditingIssue.getStoryPoints() != null ? currentEditingIssue.getStoryPoints().toString() : "";
        boolean storyPointsChanged = !newStoryPoints.equals(currentStoryPointsStr);
        
        if (!summaryChanged && !statusChanged && !descriptionChanged && !assigneeChanged && !storyPointsChanged) {
            updateStatus("No changes to save");
            return;
        }
        
        if (issueSummaryField.getText().trim().isEmpty()) {
            Messages.showErrorDialog(project, "Summary is required", "Validation Error");
            issueSummaryField.requestFocus();
            return;
        }
        
        // Validate story points
        Double storyPointsValue = null;
        if (!newStoryPoints.isEmpty()) {
            try {
                storyPointsValue = Double.parseDouble(newStoryPoints);
                if (storyPointsValue < 0) {
                    Messages.showErrorDialog(project, "Story points must be a positive number", "Validation Error");
                    storyPointsField.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                Messages.showErrorDialog(project, "Story points must be a valid number (e.g., 0.5, 1, 2)", "Validation Error");
                storyPointsField.requestFocus();
                return;
            }
        }
        
        // Update the issue object
        currentEditingIssue.setSummary(issueSummaryField.getText().trim());
        currentEditingIssue.setStatus((String) issueStatusComboBox.getSelectedItem());
        currentEditingIssue.setDescription(issueDescriptionArea.getText().trim());
        
        // Update assignee
        if (newAssignee.isEmpty()) {
            currentEditingIssue.setAssignee(null);
        } else {
            currentEditingIssue.setAssignee(newAssignee);
        }
        
        // Update story points
        currentEditingIssue.setStoryPoints(storyPointsValue);
        
        updateStatus("Saving issue " + currentEditingIssue.getKey() + "...");
        
        // Disable controls during save
        saveIssueButton.setEnabled(false);
        cancelIssueButton.setEnabled(false);
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueAsync(currentEditingIssue)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    // Refresh the current sprint issues to show updated data
                    JiraSprint selectedSprint = sprintList.getSelectedValue();
                    if (selectedSprint != null) {
                        loadSprintIssues(selectedSprint.getId());
                    }
                    updateStatus("Issue " + currentEditingIssue.getKey() + " updated successfully");
                    Messages.showInfoMessage(project, "Issue updated successfully", "Success");
                    
                    // Re-enable controls
                    saveIssueButton.setEnabled(true);
                    cancelIssueButton.setEnabled(true);
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error updating issue: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update issue: " + throwable.getMessage(), "Update Error");
                    
                    // Re-enable controls
                    saveIssueButton.setEnabled(true);
                    cancelIssueButton.setEnabled(true);
                });
                return null;
            });
    }
    
    private void cancelIssueEditing() {
        if (currentEditingIssue != null) {
            populateIssueForm(currentEditingIssue); // Restore original values
            updateStatus("Changes cancelled");
        }
    }
    
    
    private void handleAssigneeSelection() {
        if (isHandlingAssigneeSelection) {
            return; // Prevent recursion
        }
        
        String selected = (String) assigneeComboBox.getSelectedItem();
        if ("me".equals(selected)) {
            setAssigneeToCurrentUser();
        } else if ("Select".equals(selected)) {
            // Use SwingUtilities.invokeLater to prevent blocking the EDT
            SwingUtilities.invokeLater(this::showAssigneeSelectionDialog);
        }
    }
    
    private void setAssigneeToCurrentUser() {
        if (currentUser != null) {
            selectedAssigneeAccountId = currentUser.get("accountId").getAsString();
            selectedAssigneeDisplayName = currentUser.get("displayName").getAsString();
        }
    }
    
    private void showAssigneeSelectionDialog() {
        if (!isConfigured()) {
            resetAssigneeComboBoxSelection();
            return;
        }
        
        String query = JOptionPane.showInputDialog(
            contentPanel,
            "Enter assignee name to search:",
            "Select Assignee",
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (query != null && !query.trim().isEmpty()) {
            updateStatus("Searching for users...");
            JiraService jiraService = getConfiguredJiraService();
            jiraService.searchUsersAsync(query.trim())
                .thenAccept(users -> SwingUtilities.invokeLater(() -> {
                    if (users.isEmpty()) {
                        JOptionPane.showMessageDialog(contentPanel, "No users found matching: " + query);
                        resetAssigneeComboBoxSelection();
                        updateStatus("Ready");
                        return;
                    }
                    
                    String[] userNames = users.stream()
                        .filter(user -> user.has("displayName"))
                        .map(user -> user.get("displayName").getAsString())
                        .toArray(String[]::new);
                    
                    String selectedUser = (String) JOptionPane.showInputDialog(
                        contentPanel,
                        "Select assignee:",
                        "Select Assignee",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        userNames,
                        userNames.length > 0 ? userNames[0] : null
                    );
                    
                    if (selectedUser != null) {
                        assignSelectedUser(users, selectedUser);
                    } else {
                        resetAssigneeComboBoxSelection();
                    }
                    updateStatus("Ready");
                }))
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(contentPanel, "Error searching users: " + throwable.getMessage());
                        resetAssigneeComboBoxSelection();
                        updateStatus("Ready");
                    });
                    return null;
                });
        } else {
            resetAssigneeComboBoxSelection();
        }
    }
    
    private void assignSelectedUser(List<JsonObject> users, String selectedUser) {
        isHandlingAssigneeSelection = true;
        try {
            // Find the account ID for the selected user
            for (JsonObject user : users) {
                if (user.has("displayName") && selectedUser.equals(user.get("displayName").getAsString())) {
                    selectedAssigneeAccountId = user.get("accountId").getAsString();
                    selectedAssigneeDisplayName = selectedUser;
                    
                    // Add to combo box if not already present
                    boolean found = false;
                    for (int i = 0; i < assigneeComboBox.getItemCount(); i++) {
                        if (selectedUser.equals(assigneeComboBox.getItemAt(i))) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        assigneeComboBox.addItem(selectedUser);
                    }
                    assigneeComboBox.setSelectedItem(selectedUser);
                    break;
                }
            }
        } finally {
            isHandlingAssigneeSelection = false;
        }
    }
    
    private void resetAssigneeComboBoxSelection() {
        // Reset selection without triggering the action listener
        isHandlingAssigneeSelection = true;
        try {
            // Find a previous selection (not "Select") or default to "me"
            String previousSelection = null;
            for (int i = 0; i < assigneeComboBox.getItemCount(); i++) {
                String item = assigneeComboBox.getItemAt(i);
                if (!"Select".equals(item)) {
                    previousSelection = item;
                    break;
                }
            }
            
            if (previousSelection != null) {
                assigneeComboBox.setSelectedItem(previousSelection);
            } else {
                // Ensure "me" option exists
                boolean meExists = false;
                for (int i = 0; i < assigneeComboBox.getItemCount(); i++) {
                    if ("me".equals(assigneeComboBox.getItemAt(i))) {
                        meExists = true;
                        break;
                    }
                }
                if (!meExists) {
                    assigneeComboBox.addItem("me");
                }
                assigneeComboBox.setSelectedItem("me");
                setAssigneeToCurrentUser();
            }
        } finally {
            isHandlingAssigneeSelection = false;
        }
    }
    
    
    private void openIssueDetailDialog(String issueKey) {
        if (!isConfigured()) {
            showConfigurationError();
            return;
        }
        
        updateStatus("Loading issue details: " + issueKey + "...");
        System.out.println("___ JiraToolWindowContent: Loading issue details for: " + issueKey);
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getIssueAsync(issueKey)
            .thenAccept(issue -> SwingUtilities.invokeLater(() -> {
                System.out.println("___ JiraToolWindowContent: Creating JiraIssueDetailDialog for issue: " + issue.getKey());
                System.out.println("___ JiraToolWindowContent: Issue summary: " + issue.getSummary());
                JiraIssueDetailDialog dialog = new JiraIssueDetailDialog(project, jiraService, issue);
                System.out.println("___ JiraToolWindowContent: JiraIssueDetailDialog created, calling showAndGet()");
                if (dialog.showAndGet() && dialog.isModified()) {
                    // Refresh the current sprint issues to show updated data
                    JiraSprint selectedSprint = sprintList.getSelectedValue();
                    if (selectedSprint != null) {
                        loadSprintIssues(selectedSprint.getId());
                    }
                    updateStatus("Issue " + issueKey + " updated successfully");
                } else {
                    updateStatus("Issue details dialog closed");
                }
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error loading issue: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to load issue details: " + throwable.getMessage(), "Error");
                });
                return null;
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