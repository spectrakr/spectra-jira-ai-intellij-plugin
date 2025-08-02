package com.spectra.intellij.ai.toolwindow;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.toolwindow.components.*;
import com.spectra.intellij.ai.toolwindow.handlers.*;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class JiraToolWindowContent {
    
    private final Project project;
    private JPanel contentPanel;
    private JLabel statusLabel;
    
    // Components
    private SprintListPanel sprintListPanel;
    private FilterPanel filterPanel;
    private IssueTableManager issueTableManager;
    private IssueDetailPanel issueDetailPanel;
    
    // Inline edit handlers
    private SummaryInlineEditHandler summaryHandler;
    private DescriptionInlineEditHandler descriptionHandler;
    private StoryPointsInlineEditHandler storyPointsHandler;
    private StatusInlineEditHandler statusHandler;
    
    // Selection handlers
    private AssigneeSelectionHandler assigneeHandler;
    private EpicSelectionHandler epicHandler;
    
    // State
    private String currentSprintId;
    private JiraIssue currentEditingIssue;
    private JsonObject currentUser;
    
    public JiraToolWindowContent(Project project) {
        this.project = project;
        initializeComponents();
        setupEventHandlers();
        refreshStatus();
        loadSprints();
    }
    
    private void initializeComponents() {
        contentPanel = new JPanel(new BorderLayout());
        
        // Create components
        sprintListPanel = new SprintListPanel(project);
        filterPanel = new FilterPanel(project);
        issueTableManager = new IssueTableManager();
        issueDetailPanel = new IssueDetailPanel(project);
        
        // Setup main layout
        setupMainLayout();
        
        // Create handlers
        setupInlineEditHandlers();
        setupSelectionHandlers();
        
        // Bottom status panel
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        contentPanel.add(statusLabel, BorderLayout.SOUTH);
    }
    
    private void setupMainLayout() {
        // Center panel - Issue table with filter panel above
        JPanel issuePanel = new JPanel(new BorderLayout());
        issuePanel.setBorder(BorderFactory.createTitledBorder("Issues"));
        issuePanel.add(filterPanel, BorderLayout.NORTH);
        issuePanel.add(new JScrollPane(issueTableManager.getTable()), BorderLayout.CENTER);
        
        // Right panel - Issue detail form with scroll
        JScrollPane detailScrollPane = new JScrollPane(issueDetailPanel);
        detailScrollPane.setPreferredSize(new Dimension(400, 0));
        detailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Create resizable split pane for issue list and detail
        JSplitPane issueDetailSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, issuePanel, detailScrollPane);
        issueDetailSplitPane.setResizeWeight(0.7); // 70% for issue list, 30% for detail
        issueDetailSplitPane.setDividerLocation(0.7);
        issueDetailSplitPane.setContinuousLayout(true);
        
        // Remove divider visibility
        issueDetailSplitPane.setBackground(UIManager.getColor("Panel.background"));
        issueDetailSplitPane.setBorder(null);
        issueDetailSplitPane.setDividerSize(5);
        issueDetailSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics g) {
                        // Paint nothing to make divider invisible
                    }
                };
            }
        });
        
        // Create main split pane for sprint list and issue/detail panels
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sprintListPanel, issueDetailSplitPane);
        mainSplitPane.setResizeWeight(0.0); // Keep sprint panel fixed size
        mainSplitPane.setDividerLocation(200); // Increase divider location for better visibility
        mainSplitPane.setContinuousLayout(true);
        
        // Remove divider visibility
        mainSplitPane.setBackground(UIManager.getColor("Panel.background"));
        mainSplitPane.setBorder(null);
        mainSplitPane.setDividerSize(5);
        mainSplitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics g) {
                        // Paint nothing to make divider invisible
                    }
                };
            }
        });
        
        contentPanel.add(mainSplitPane, BorderLayout.CENTER);
    }
    
    private void setupInlineEditHandlers() {
        JiraService jiraService = getConfiguredJiraService();
        
        // Summary handler
        summaryHandler = new SummaryInlineEditHandler(project, issueDetailPanel.getIssueSummaryField(), jiraService);
        summaryHandler.setOnStatusUpdate(this::updateStatus);
        
        // Description handler
        descriptionHandler = new DescriptionInlineEditHandler(
            project, 
            issueDetailPanel.getIssueDescriptionField(),
            issueDetailPanel.getDescriptionScrollPane(),
            issueDetailPanel.getDescriptionButtonPanel(),
            issueDetailPanel.getSaveDescriptionButton(),
            issueDetailPanel.getCancelDescriptionButton(),
            jiraService
        );
        descriptionHandler.setOnStatusUpdate(this::updateStatus);
        
        // Story points handler
        storyPointsHandler = new StoryPointsInlineEditHandler(project, issueDetailPanel.getStoryPointsField(), jiraService);
        storyPointsHandler.setOnStatusUpdate(this::updateStatus);
        
        // Status handler
        statusHandler = new StatusInlineEditHandler(project, issueDetailPanel.getIssueStatusComboBox(), jiraService);
        statusHandler.setOnStatusUpdate(this::updateStatus);
    }
    
    private void setupSelectionHandlers() {
        JiraService jiraService = getConfiguredJiraService();
        
        // Assignee handler
        assigneeHandler = new AssigneeSelectionHandler(project, issueDetailPanel.getAssigneeLabel(), jiraService);
        assigneeHandler.setOnStatusUpdate(this::updateStatus);
        
        // Epic handler
        epicHandler = new EpicSelectionHandler(project, issueDetailPanel.getEpicLabel(), jiraService);
        epicHandler.setOnStatusUpdate(this::updateStatus);
    }
    
    private void setupEventHandlers() {
        // Sprint selection
        sprintListPanel.setOnSprintSelected(this::onSprintSelected);
        sprintListPanel.setOnSettingsClick(v -> showSettingsDialog());
        
        // Filter events
        filterPanel.setOnFilterChanged(v -> applyFilters());
        filterPanel.setOnRefresh(v -> {
            refreshStatus();
            loadSprints();
        });
        filterPanel.setOnCreateIssue(v -> createIssue());
        
        // Issue selection
        issueTableManager.setOnIssueSelected(this::onIssueSelected);
        
        // Issue detail events
        issueDetailPanel.setOnHamburgerMenuClick(v -> showHamburgerMenu());
        issueDetailPanel.setOnAssigneeLabelClick(v -> {
            // Assignee handler manages its own clicks
        });
        issueDetailPanel.setOnEpicLabelClick(v -> {
            // Epic handler manages its own clicks
        });
    }
    
    private void onSprintSelected(JiraSprint sprint) {
        if (sprint != null) {
            loadSprintIssues(sprint.getId());
        }
    }
    
    private void onIssueSelected(String issueKey) {
        if (issueKey != null) {
            loadIssueForEditing(issueKey);
        } else {
            clearIssueDetail();
        }
    }
    
    private void applyFilters() {
        String selectedIssueType = filterPanel.getSelectedIssueType();
        String selectedAssignee = filterPanel.getSelectedAssignee();
        String selectedStatus = filterPanel.getSelectedStatus();
        
        issueTableManager.applyFilters(selectedIssueType, selectedAssignee, selectedStatus);
        updateStatus("Filtered " + issueTableManager.getIssueCount() + " issues");
    }
    
    private void createIssue() {
        if (!isConfigured()) {
            showConfigurationError();
            return;
        }
        
        JiraService jiraService = getConfiguredJiraService();
        JiraSprint selectedSprint = sprintListPanel.getSelectedSprint();
        CreateIssueDialog dialog = new CreateIssueDialog(project, jiraService, selectedSprint);
        if (dialog.showAndGet()) {
            JiraIssue issue = dialog.getIssue();
            updateStatus("Creating issue...");
            
            jiraService.createIssueAsync(issue)
                .thenAccept(createdIssue -> {
                    SwingUtilities.invokeLater(() -> {
                        String successMessage = "이슈가 등록되었습니다. (" + createdIssue.getKey() + ")";
                        updateStatus(successMessage);
                        Messages.showInfoMessage(project, successMessage, "이슈 등록 성공");
                        // Refresh current sprint issues to show the new issue
                        refreshCurrentSprintIssues();
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Error creating issue");
                        Messages.showErrorDialog(project, "Failed to create issue: " + throwable.getMessage(), "Error");
                    });
                    return null;
                });
        }
    }
    
    private void showHamburgerMenu() {
        if (currentEditingIssue == null) return;
        
        JPopupMenu popupMenu = new JPopupMenu();
        
        // Delete menu item
        JMenuItem deleteItem = new JMenuItem("삭제");
        deleteItem.setForeground(Color.RED);
        deleteItem.addActionListener(e -> deleteCurrentIssue());
        popupMenu.add(deleteItem);
        
        // Show popup menu below the hamburger button
        Component hamburgerButton = issueDetailPanel.getComponents()[0]; // First component should be the hamburger menu panel
        popupMenu.show(hamburgerButton, 0, hamburgerButton.getHeight());
    }
    
    private void deleteCurrentIssue() {
        if (currentEditingIssue == null) return;
        
        // Show confirmation dialog
        int result = Messages.showYesNoDialog(
            project,
            currentEditingIssue.getKey() + " 이슈를 정말 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.",
            "이슈 삭제 확인",
            "삭제",
            "취소",
            Messages.getQuestionIcon()
        );
        
        if (result == Messages.YES) {
            performIssueDelete();
        }
    }
    
    private void performIssueDelete() {
        if (currentEditingIssue == null) return;
        
        String issueKey = currentEditingIssue.getKey();
        updateStatus("이슈 삭제 중: " + issueKey);
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.deleteIssueAsync(issueKey)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("이슈 삭제 완료: " + issueKey);
                    clearIssueDetail();
                    refreshCurrentSprintIssues(); // Refresh the issue list
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = "이슈 삭제 중 오류가 발생했습니다";
                    updateStatus("이슈 삭제 실패: " + errorMessage);
                    Messages.showErrorDialog(project, "이슈 삭제에 실패했습니다: " + errorMessage, "삭제 오류");
                });
                return null;
            });
    }
    
    private void refreshCurrentSprintIssues() {
        if (currentSprintId != null) {
            loadSprintIssues(currentSprintId);
        }
    }
    
    private void refreshStatus() {
        updateStatus("Checking configuration...");
        
        SwingUtilities.invokeLater(() -> {
            JiraSettings settings = JiraSettings.getInstance();
            if (isConfigured()) {
                updateStatus("Connected to: " + settings.getJiraUrl());
                filterPanel.setCreateIssueButtonEnabled(true);
            } else {
                updateStatus("Not configured - Configure in Settings");
                filterPanel.setCreateIssueButtonEnabled(false);
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
                    sprintListPanel.updateSprints(sprints);
                    updateStatus("Loaded " + sprints.size() + " sprints");
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    sprintListPanel.clearSprints();
                    updateStatus("Error loading sprints: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void loadSprintIssues(String sprintId) {
        loadSprintIssues(sprintId, null);
    }
    
    private void loadSprintIssues(String sprintId, String preserveSelectedIssueKey) {
        this.currentSprintId = sprintId; // Track current sprint
        updateStatus("Loading issues from sprint: " + sprintId + "...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getSprintIssuesAsync(sprintId)
            .thenAccept(issues -> {
                SwingUtilities.invokeLater(() -> {
                    issueTableManager.updateIssues(issues);
                    
                    // Only clear issue detail if we're not preserving selection
                    if (preserveSelectedIssueKey == null) {
                        clearIssueDetail();
                    }
                    
                    // Clear and populate filter options
                    filterPanel.clearFilterOptions();
                    for (JiraIssue issue : issues) {
                        String issueType = issue.getIssueType() != null ? issue.getIssueType() : "";
                        String assignee = issue.getAssignee() != null ? issue.getAssignee() : "";
                        String status = issue.getStatus() != null ? issue.getStatus() : "";
                        filterPanel.addToFilterOptions(issueType, assignee, status);
                    }
                    
                    // Restore selection if requested
                    if (preserveSelectedIssueKey != null) {
                        issueTableManager.selectIssueByKey(preserveSelectedIssueKey);
                    }
                    
                    updateStatus("Loaded " + issues.size() + " issues from sprint");
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    issueTableManager.clearIssues();
                    if (preserveSelectedIssueKey == null) {
                        clearIssueDetail();
                    }
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
                        assigneeHandler.setCurrentUser(currentUser);
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
                    
                    // Update all handlers with the current issue
                    summaryHandler.setCurrentIssue(issue);
                    descriptionHandler.setCurrentIssue(issue);
                    storyPointsHandler.setCurrentIssue(issue);
                    statusHandler.setCurrentIssue(issue);
                    assigneeHandler.setCurrentIssue(issue);
                    epicHandler.setCurrentIssue(issue);
                    
                    // Populate the form
                    issueDetailPanel.populateIssueForm(issue);
                    
                    // Load available statuses
                    jiraService.getIssueStatusesAsync(issueKey)
                        .thenAccept(statuses -> {
                            SwingUtilities.invokeLater(() -> {
                                statusHandler.updateStatusOptions(statuses);
                                updateStatus("Issue details loaded: " + issueKey);
                            });
                        })
                        .exceptionally(throwable -> {
                            SwingUtilities.invokeLater(() -> {
                                statusHandler.addFallbackStatusOptions();
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
    
    private void clearIssueDetail() {
        currentEditingIssue = null;
        
        // Clear all handlers
        summaryHandler.setCurrentIssue(null);
        descriptionHandler.setCurrentIssue(null);
        storyPointsHandler.setCurrentIssue(null);
        statusHandler.setCurrentIssue(null);
        assigneeHandler.setCurrentIssue(null);
        epicHandler.setCurrentIssue(null);
        
        // Clear the form
        issueDetailPanel.clearIssueDetail();
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