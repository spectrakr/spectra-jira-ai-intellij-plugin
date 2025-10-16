package com.spectra.intellij.ai.toolwindow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.AccessLogService;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.toolwindow.components.*;
import com.spectra.intellij.ai.toolwindow.handlers.*;
import com.spectra.intellij.ai.toolwindow.handlers.ClaudeMcpConnectionHandler;
import com.spectra.intellij.ai.toolwindow.handlers.CodexMcpConnectionHandler;
import com.spectra.intellij.ai.toolwindow.handlers.GeminiMcpConnectionHandler;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class JiraToolWindowContent {
    
    private final Project project;
    private JPanel contentPanel;
    private JLabel statusLabel;
    
    // Components
    private SprintListPanel sprintListPanel;
    private FilterPanel filterPanel;
    private IssueTableManager issueTableManager;
    private IssueStatisticsPanel issueStatisticsPanel;
    private IssueDetailPanel issueDetailPanel;
    
    // Inline edit handlers
    private SummaryInlineEditHandler summaryHandler;
    private DescriptionInlineEditHandler descriptionHandler;
    private StoryPointsInlineEditHandler storyPointsHandler;
    private StatusInlineEditHandler statusHandler;
    
    // Selection handlers
    private AssigneeSelectionHandler assigneeHandler;
    private EpicSelectionHandler epicHandler;

    // MCP connection handlers
    private ClaudeMcpConnectionHandler claudeMcpConnectionHandler;
    private CodexMcpConnectionHandler codexMcpConnectionHandler;
    private GeminiMcpConnectionHandler geminiMcpConnectionHandler;

    // State
    private String currentSprintId;
    private JiraIssue currentEditingIssue;
    private JsonObject currentUser;
    
    public JiraToolWindowContent(Project project) {
        this.project = project;
        this.claudeMcpConnectionHandler = new ClaudeMcpConnectionHandler(project);
        this.codexMcpConnectionHandler = new CodexMcpConnectionHandler(project);
        this.geminiMcpConnectionHandler = new GeminiMcpConnectionHandler(project);
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
        issueTableManager = new IssueTableManager(project);
        issueStatisticsPanel = new IssueStatisticsPanel();
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
        // Create tabbed pane for Issues
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Issue List Tab
        JPanel issueListPanel = new JPanel(new BorderLayout());
        issueListPanel.add(filterPanel, BorderLayout.NORTH);
        
        // Create scroll pane with custom border
        JScrollPane tableScrollPane = new JScrollPane(issueTableManager.getTable());
        tableScrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1));
        issueListPanel.add(tableScrollPane, BorderLayout.CENTER);
        tabbedPane.addTab("이슈 목록", issueListPanel);
        
        // Issue Statistics Tab
        tabbedPane.addTab("이슈 통계", issueStatisticsPanel);
        
        // Center panel with tabs
        JPanel issuePanel = new JPanel(new BorderLayout());
        issuePanel.setBorder(BorderFactory.createEmptyBorder());
        issuePanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Right panel - Issue detail form with scroll
        JScrollPane detailScrollPane = new JScrollPane(issueDetailPanel);
        detailScrollPane.setPreferredSize(new Dimension(400, 0));
        detailScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        detailScrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1));
        
        // Add component listener to handle manual resizing
        detailScrollPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (issueDetailPanel != null) {
                        issueDetailPanel.triggerResizeAdjustment();
                    }
                });
            }
        });
        
        // Create resizable split pane for issue list and detail
        JSplitPane issueDetailSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, issuePanel, detailScrollPane);
        issueDetailSplitPane.setResizeWeight(0.7); // 70% for issue list, 30% for detail
        issueDetailSplitPane.setDividerLocation(0.7);
        issueDetailSplitPane.setContinuousLayout(true);
        
        // Add property change listener to handle split pane resize
        issueDetailSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // Trigger resize adjustment on the detail panel
                SwingUtilities.invokeLater(() -> {
                    if (issueDetailPanel != null) {
                        issueDetailPanel.triggerResizeAdjustment();
                    }
                });
            }
        });
        
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
        sprintListPanel.setOnRefreshClick(v -> loadSprints());
        
        // Filter events
        filterPanel.setOnFilterChanged(v -> applyFilters());
        filterPanel.setOnRefresh(v -> {
            refreshStatus();
            // Refresh current sprint issues instead of reloading all sprints
            refreshCurrentSprintIssues();
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
        JButton hamburgerButton = issueDetailPanel.getHamburgerMenuButton();
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
                updateStatus("Jira 사용자 계정 설정이 필요합니다. 스프린트 목록 우측에 [설정] 버튼을 클릭하여 정보를 입력하세요.");
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
               settings.getApiToken() != null && !settings.getApiToken().trim().isEmpty() &&
               settings.getDefaultProjectKey() != null && !settings.getDefaultProjectKey().trim().isEmpty();
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
        
        updateStatus("Loading sprints from project boards...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getSprintsAsync()
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
                    issueStatisticsPanel.updateStatistics(issues);

                    // Only clear issue detail if we're not preserving selection
                    if (preserveSelectedIssueKey == null) {
                        clearIssueDetail();
                    }

                    // Save current filter selections before clearing
                    String selectedIssueType = filterPanel.getSelectedIssueType();
                    String selectedAssignee = filterPanel.getSelectedAssignee();
                    String selectedStatus = filterPanel.getSelectedStatus();

                    // Clear and populate filter options
                    filterPanel.clearFilterOptions();
                    for (JiraIssue issue : issues) {
                        String issueType = issue.getIssueType() != null ? issue.getIssueType() : "";
                        String assignee = issue.getAssignee() != null ? issue.getAssignee() : "";
                        String status = issue.getStatus() != null ? issue.getStatus() : "";
                        filterPanel.addToFilterOptions(issueType, assignee, status);
                    }

                    // Restore previous filter selections
                    filterPanel.restoreFilterSelections(selectedIssueType, selectedAssignee, selectedStatus);

                    // Apply the restored filters to the table
                    applyFilters();

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
                    issueStatisticsPanel.clearStatistics();
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
    
    private void openTokenUrl() {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI("https://id.atlassian.com/manage-profile/security/api-tokens"));
            } else {
                Messages.showInfoMessage(
                    project,
                    "브라우저를 자동으로 열 수 없습니다.\n\n다음 URL을 수동으로 방문하세요:\nhttps://id.atlassian.com/manage-profile/security/api-tokens",
                    "API 토큰 생성"
                );
            }
        } catch (Exception e) {
            Messages.showErrorDialog(
                project,
                "브라우저를 열 수 없습니다.\n\n다음 URL을 수동으로 방문하세요:\nhttps://id.atlassian.com/manage-profile/security/api-tokens",
                "오류"
            );
        }
    }

    private void validateToken(JTextField urlField, JTextField userField, JPasswordField tokenField) {
        String jiraUrl = urlField.getText().trim();
        String username = userField.getText().trim();
        String apiToken = new String(tokenField.getPassword()).trim();

        if (jiraUrl.isEmpty() || username.isEmpty() || apiToken.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Jira URL, Email, API Token을 모두 입력해주세요.",
                "입력 확인"
            );
            return;
        }

        // Show progress and validate token in background thread
        JiraService jiraService = new JiraService();
        jiraService.configure(jiraUrl, username, apiToken);

        jiraService.getCurrentUserAsync()
            .thenAccept(userJson -> {
                SwingUtilities.invokeLater(() -> {
                    String displayName = userJson.has("displayName") ? 
                        userJson.get("displayName").getAsString() : "Unknown";
                    String emailAddress = userJson.has("emailAddress") ? 
                        userJson.get("emailAddress").getAsString() : "Unknown";
                    
                    Messages.showInfoMessage(
                        project,
                        "토큰이 유효합니다!\n\n" +
                        "사용자: " + displayName + "\n" +
                        "이메일: " + emailAddress,
                        "토큰 확인 성공"
                    );
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = "토큰이 유효하지 않습니다.\n\n";
                    if (throwable.getCause() != null) {
                        errorMessage += "오류: " + throwable.getCause().getMessage();
                    } else {
                        errorMessage += "Jira URL, Email, API Token을 확인해주세요.";
                    }
                    
                    Messages.showErrorDialog(
                        project,
                        errorMessage, 
                        "토큰 확인 실패"
                    );
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

        // Jira 설정 Section Header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5, 5, 10, 5);
        JLabel jiraSettingsHeaderLabel = new JLabel("Jira 설정");
        jiraSettingsHeaderLabel.setFont(jiraSettingsHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(jiraSettingsHeaderLabel, gbc);

        // Reset insets and gridwidth for fields
        gbc.gridwidth = 1;
        gbc.insets = JBUI.insets(5);

        // Jira URL
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Jira URL:"), gbc);

        JTextField urlField = new JTextField(settings.getJiraUrl() != null ? settings.getJiraUrl() : "", 30);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(urlField, gbc);

        // Email
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Email:"), gbc);

        JTextField userField = new JTextField(settings.getUsername() != null ? settings.getUsername() : "", 30);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(userField, gbc);

        // API Token
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("API Token:"), gbc);
        
        // API Token field and button panel
        JPanel apiTokenPanel = new JPanel(new BorderLayout(5, 0));
        JPasswordField tokenField = new JPasswordField(settings.getApiToken() != null ? settings.getApiToken() : "", 30);
        
        // Button panel for token actions
        JButton tokenLookupButton = new JButton("토큰 생성");
        tokenLookupButton.addActionListener(e -> openTokenUrl());
        JPanel tokenButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton tokenValidateButton = new JButton("토큰 확인");
        tokenValidateButton.addActionListener(e -> validateToken(urlField, userField, tokenField));

        tokenButtonsPanel.add(tokenLookupButton);
        tokenButtonsPanel.add(tokenValidateButton);

        apiTokenPanel.add(tokenField, BorderLayout.CENTER);
        apiTokenPanel.add(tokenButtonsPanel, BorderLayout.EAST);

        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(apiTokenPanel, gbc);

        // Project ID
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Project ID:"), gbc);

        // Project ID field and example panel
        JPanel projectPanel = new JPanel(new BorderLayout(5, 0));
        JTextField projectIdField = new JTextField(settings.getDefaultProjectKey() != null ? settings.getDefaultProjectKey() : "", 10);
        JLabel exampleLabel = new JLabel("예) AVGRS");
        exampleLabel.setForeground(Color.GRAY);
        projectPanel.add(projectIdField, BorderLayout.WEST);
        projectPanel.add(exampleLabel, BorderLayout.CENTER);

        gbc.gridx = 1; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(projectPanel, gbc);

        // Separator line
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(15, 5, 15, 5);
        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        panel.add(separator, gbc);

        // Reset insets and gridwidth
        gbc.gridwidth = 1;
        gbc.insets = JBUI.insets(5);

        // Fix Issue Agent 설정 Section Header
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5, 5, 10, 5);
        JLabel agentSettingsHeaderLabel = new JLabel("Fix Issue Agent 설정");
        agentSettingsHeaderLabel.setFont(agentSettingsHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(agentSettingsHeaderLabel, gbc);

        // Reset insets and gridwidth for fields
        gbc.gridwidth = 1;
        gbc.insets = JBUI.insets(5);

        // Claude Command
        gbc.gridx = 0; gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Claude 실행 명령어:"), gbc);

        // Claude command field and example panel
        JPanel commandPanel = new JPanel(new BorderLayout(5, 0));

        // Command field with reset button
        JPanel commandFieldPanel = new JPanel(new BorderLayout(5, 0));
        JTextField claudeCommandField = new JTextField(settings.getClaudeCommand() != null ? settings.getClaudeCommand() : "", 30);
        JButton resetClaudeButton = new JButton("초기화");
        resetClaudeButton.addActionListener(e -> {
            claudeCommandField.setText("claude --dangerously-skip-permissions \"fix-issue-agent sub agent를 이용하여 \\\"$issueKey\\\" 이슈 처리해줘\"");
        });
        commandFieldPanel.add(claudeCommandField, BorderLayout.CENTER);
        commandFieldPanel.add(resetClaudeButton, BorderLayout.EAST);

        JLabel commandExampleLabel = new JLabel("예) $issueKey 변수 사용 가능");
        commandExampleLabel.setForeground(Color.GRAY);
        commandPanel.add(commandFieldPanel, BorderLayout.CENTER);
        commandPanel.add(commandExampleLabel, BorderLayout.SOUTH);

        gbc.gridx = 1; gbc.gridy = 7;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(commandPanel, gbc);

        // Codex Command
        gbc.gridx = 0; gbc.gridy = 8;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Codex 실행 명령어:"), gbc);

        // Codex command field and example panel
        JPanel codexCommandPanel = new JPanel(new BorderLayout(5, 0));

        // Codex command field with reset button
        JPanel codexCommandFieldPanel = new JPanel(new BorderLayout(5, 0));
        JTextField codexCommandField = new JTextField(settings.getCodexCommand() != null ? settings.getCodexCommand() : "", 30);
        JButton resetCodexButton = new JButton("초기화");
        resetCodexButton.addActionListener(e -> {
            codexCommandField.setText("codex \"/fix-issue $issueKey\"");
        });
        codexCommandFieldPanel.add(codexCommandField, BorderLayout.CENTER);
        codexCommandFieldPanel.add(resetCodexButton, BorderLayout.EAST);

        JLabel codexCommandExampleLabel = new JLabel("예) $issueKey 변수 사용 가능");
        codexCommandExampleLabel.setForeground(Color.GRAY);
        codexCommandPanel.add(codexCommandFieldPanel, BorderLayout.CENTER);
        codexCommandPanel.add(codexCommandExampleLabel, BorderLayout.SOUTH);

        gbc.gridx = 1; gbc.gridy = 8;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(codexCommandPanel, gbc);

        // Gemini Command
        gbc.gridx = 0; gbc.gridy = 9;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Gemini 실행 명령어:"), gbc);

        // Gemini command field and example panel
        JPanel geminiCommandPanel = new JPanel(new BorderLayout(5, 0));

        // Gemini command field with reset button
        JPanel geminiCommandFieldPanel = new JPanel(new BorderLayout(5, 0));
        JTextField geminiCommandField = new JTextField(settings.getGeminiCommand() != null ? settings.getGeminiCommand() : "", 30);
        JButton resetGeminiButton = new JButton("초기화");
        resetGeminiButton.addActionListener(e -> {
            geminiCommandField.setText("gemini --yolo \"/fix-issue $issueKey\"");
        });
        geminiCommandFieldPanel.add(geminiCommandField, BorderLayout.CENTER);
        geminiCommandFieldPanel.add(resetGeminiButton, BorderLayout.EAST);

        JLabel geminiCommandExampleLabel = new JLabel("예) $issueKey 변수 사용 가능");
        geminiCommandExampleLabel.setForeground(Color.GRAY);
        geminiCommandPanel.add(geminiCommandFieldPanel, BorderLayout.CENTER);
        geminiCommandPanel.add(geminiCommandExampleLabel, BorderLayout.SOUTH);

        gbc.gridx = 1; gbc.gridy = 9;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(geminiCommandPanel, gbc);

        // Jira MCP
        gbc.gridx = 0; gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Jira MCP:"), gbc);

        // Check if MCP is already connected
        boolean isMcpConnected = claudeMcpConnectionHandler.checkMcpConnection();

        // Create button panel that will be updated
        JPanel mcpButtonContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        // jira-mcp download button


        // Claude connection button
        JButton claudeButton = new JButton(isMcpConnected ? "Claude 연결 해제" : "Claude 연결");
        claudeButton.addActionListener(e -> {
            boolean isConnected = claudeMcpConnectionHandler.checkMcpConnection();
            if (isConnected) {
                // Disconnect
                claudeMcpConnectionHandler.removeMcpConnection(() -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = claudeMcpConnectionHandler.checkMcpConnection();
                        claudeButton.setText(newStatus ? "Claude 연결 해제" : "Claude 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            } else {
                // Connect
                claudeMcpConnectionHandler.setupClaudeMcp(urlField.getText().trim(), userField.getText().trim(), new String(tokenField.getPassword()).trim(), () -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = claudeMcpConnectionHandler.checkMcpConnection();
                        claudeButton.setText(newStatus ? "Claude 연결 해제" : "Claude 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            }
        });

        // Check if Codex MCP is already connected
        boolean isCodexMcpConnected = codexMcpConnectionHandler.checkMcpConnection();

        // Codex connection button
        JButton codexButton = new JButton(isCodexMcpConnected ? "Codex 연결 해제" : "Codex 연결");
        codexButton.addActionListener(e -> {
            boolean isConnected = codexMcpConnectionHandler.checkMcpConnection();
            if (isConnected) {
                // Disconnect
                codexMcpConnectionHandler.removeMcpConnection(() -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = codexMcpConnectionHandler.checkMcpConnection();
                        codexButton.setText(newStatus ? "Codex 연결 해제" : "Codex 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            } else {
                // Connect
                codexMcpConnectionHandler.setupCodexMcp(urlField.getText().trim(), userField.getText().trim(), new String(tokenField.getPassword()).trim(), () -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = codexMcpConnectionHandler.checkMcpConnection();
                        codexButton.setText(newStatus ? "Codex 연결 해제" : "Codex 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            }
        });

        // Check if Gemini MCP is already connected
        boolean isGeminiMcpConnected = geminiMcpConnectionHandler.checkMcpConnection();

        // Gemini connection button
        JButton geminiButton = new JButton(isGeminiMcpConnected ? "Gemini 연결 해제" : "Gemini 연결");
        geminiButton.addActionListener(e -> {
            boolean isConnected = geminiMcpConnectionHandler.checkMcpConnection();
            if (isConnected) {
                // Disconnect
                geminiMcpConnectionHandler.removeMcpConnection(() -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = geminiMcpConnectionHandler.checkMcpConnection();
                        geminiButton.setText(newStatus ? "Gemini 연결 해제" : "Gemini 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            } else {
                // Connect
                geminiMcpConnectionHandler.setupGeminiMcp(urlField.getText().trim(), userField.getText().trim(), new String(tokenField.getPassword()).trim(), () -> {
                    // Re-check MCP connection status and update button
                    SwingUtilities.invokeLater(() -> {
                        boolean newStatus = geminiMcpConnectionHandler.checkMcpConnection();
                        geminiButton.setText(newStatus ? "Gemini 연결 해제" : "Gemini 연결");
                        mcpButtonContainer.revalidate();
                        mcpButtonContainer.repaint();
                    });
                });
            }
        });

        mcpButtonContainer.add(claudeButton);
        mcpButtonContainer.add(codexButton);
        mcpButtonContainer.add(geminiButton);
        gbc.gridx = 1; gbc.gridy = 10;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(mcpButtonContainer, gbc);

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
            settings.setClaudeCommand(claudeCommandField.getText().trim());
            settings.setCodexCommand(codexCommandField.getText().trim());
            settings.setGeminiCommand(geminiCommandField.getText().trim());
            
            // Refresh status after saving
            refreshStatus();
            loadSprints();

            // Send access log for settings confirmation
            try {
                JiraService jiraService = new JiraService();
                jiraService.configure(settings.getJiraUrl(), settings.getUsername(), settings.getApiToken());
                AccessLogService accessLogService = new AccessLogService(
                    new okhttp3.OkHttpClient(),
                    new Gson(),
                    jiraService
                );
                String settingsInfo = String.format("URL: %s, Username: %s, Project: %s",
                    settings.getJiraUrl(), settings.getUsername(), settings.getDefaultProjectKey());
                accessLogService.sendAccessLog("Jira Settings 설정", settingsInfo);
            } catch (Exception e) {
                System.err.println("Failed to send access log for settings confirmation: " + e.getMessage());
            }

            Messages.showInfoMessage(
                project,
                "Jira settings have been saved successfully.",
                "Settings Saved"
            );
        }
    }
    
    private void downloadJiraMcp() {
        try {
            // Load jira-mcp.js from resources
            InputStream resourceStream = getClass().getClassLoader().getResourceAsStream("jiramcp/jira-mcp.js");

            if (resourceStream == null) {
                Messages.showErrorDialog(
                    project,
                    "jira-mcp.js 파일을 찾을 수 없습니다.\n플러그인 리소스에서 파일을 로드할 수 없습니다.",
                    "파일 로드 실패"
                );
                return;
            }

            // Create file chooser with default filename
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("jira-mcp.js 저장 위치 선택");
            fileChooser.setSelectedFile(new File("jira-mcp.js"));
            FileNameExtensionFilter filter = new FileNameExtensionFilter("JavaScript Files (*.js)", "js");
            fileChooser.setFileFilter(filter);

            int userSelection = fileChooser.showSaveDialog(contentPanel);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();

                // Ensure .js extension
                if (!fileToSave.getName().endsWith(".js")) {
                    fileToSave = new File(fileToSave.getAbsolutePath() + ".js");
                }

                // Copy resource to selected file
                Files.copy(resourceStream, fileToSave.toPath(), StandardCopyOption.REPLACE_EXISTING);
                resourceStream.close();

                Messages.showInfoMessage(
                    project,
                    "jira-mcp.js 파일이 성공적으로 다운로드되었습니다.\n\n저장 위치: " + fileToSave.getAbsolutePath(),
                    "다운로드 완료"
                );
            }
        } catch (IOException e) {
            Messages.showErrorDialog(
                project,
                "파일 다운로드 중 오류가 발생했습니다:\n" + e.getMessage(),
                "다운로드 실패"
            );
        }
    }

    public JComponent getContent() {
        return contentPanel;
    }
}