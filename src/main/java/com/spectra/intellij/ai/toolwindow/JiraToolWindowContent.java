package com.spectra.intellij.ai.toolwindow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.dialog.CreateIssueDialog;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.toolwindow.components.*;
import com.spectra.intellij.ai.toolwindow.handlers.*;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

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
        
        // Jira URL
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Jira URL:"), gbc);
        
        JTextField urlField = new JTextField(settings.getJiraUrl() != null ? settings.getJiraUrl() : "", 30);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(urlField, gbc);
        
        // Email
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Email:"), gbc);
        
        JTextField userField = new JTextField(settings.getUsername() != null ? settings.getUsername() : "", 30);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(userField, gbc);
        
        // API Token
        gbc.gridx = 0; gbc.gridy = 2;
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
        
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(apiTokenPanel, gbc);
        
        // Project ID
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Project ID:"), gbc);
        
        // Project ID field and example panel
        JPanel projectPanel = new JPanel(new BorderLayout(5, 0));
        JTextField projectIdField = new JTextField(settings.getDefaultProjectKey() != null ? settings.getDefaultProjectKey() : "", 10);
        JLabel exampleLabel = new JLabel("예) AVGRS");
        exampleLabel.setForeground(Color.GRAY);
        projectPanel.add(projectIdField, BorderLayout.WEST);
        projectPanel.add(exampleLabel, BorderLayout.CENTER);

        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(projectPanel, gbc);

        // Jira MCP
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Jira MCP:"), gbc);

        // Check if MCP is already connected
        boolean isMcpConnected = checkMcpConnection();

        if (isMcpConnected) {
            // Show disconnect button only
            JButton disconnectMcpButton = new JButton("Claude 연결 해제");
            disconnectMcpButton.addActionListener(e -> {
                removeMcpConnection();
                // Refresh the dialog after removal
                SwingUtilities.getWindowAncestor(panel).dispose();
                showSettingsDialog();
            });
            gbc.gridx = 1; gbc.gridy = 4;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(disconnectMcpButton, gbc);
        } else {
            // Show connect button only
            JButton connectMcpButton = new JButton("Claude 연결");
            connectMcpButton.addActionListener(e -> {
                setupClaudeMcp(urlField.getText().trim(), userField.getText().trim(), new String(tokenField.getPassword()).trim());
                // Refresh the dialog after setup
                SwingUtilities.getWindowAncestor(panel).dispose();
                showSettingsDialog();
            });
            gbc.gridx = 1; gbc.gridy = 4;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(connectMcpButton, gbc);
        }

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

    private boolean checkMcpConnection() {
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                return false;
            }

            Path mcpJsonPath = Paths.get(basePath, ".mcp.json");
            File mcpJsonFile = mcpJsonPath.toFile();

            if (!mcpJsonFile.exists()) {
                return false;
            }

            String mcpContent = Files.readString(mcpJsonPath, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            JsonObject mcpJson = gson.fromJson(mcpContent, JsonObject.class);

            if (mcpJson != null && mcpJson.has("mcpServers")) {
                JsonObject mcpServers = mcpJson.getAsJsonObject("mcpServers");
                return mcpServers.has("atlassian-jira");
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void removeMcpConnection() {
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                throw new Exception("Project path not found");
            }

            // Execute command in background without showing terminal
            String command = "claude mcp remove atlassian-jira";
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);
            processBuilder.directory(new File(basePath));
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Wait for completion in background thread
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    int exitCode = process.waitFor();
                    SwingUtilities.invokeLater(() -> {
                        if (exitCode == 0) {
                            Messages.showInfoMessage(
                                project,
                                "Jira MCP 연결 해제가 완료되었습니다.",
                                "Jira MCP 연결 해제"
                            );
                        } else {
                            Messages.showErrorDialog(
                                project,
                                "Jira MCP 연결 해제에 실패했습니다.",
                                "오류"
                            );
                        }
                    });
                } catch (InterruptedException e) {
                    SwingUtilities.invokeLater(() -> {
                        Messages.showErrorDialog(
                            project,
                            "Jira MCP 연결 해제 중 오류가 발생했습니다: " + e.getMessage(),
                            "오류"
                        );
                    });
                }
            });
        } catch (Exception ex) {
            Messages.showErrorDialog(
                project,
                "Jira MCP 연결 해제 중 오류가 발생했습니다:\n" + ex.getMessage(),
                "오류"
            );
        }
    }

    private void setupClaudeMcp(String jiraUrl, String username, String apiToken) {
        // Validate settings
        if (jiraUrl.isEmpty() || username.isEmpty() || apiToken.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Jira URL, Email, API Token을 모두 입력해주세요.",
                "설정 확인"
            );
            return;
        }

        try {
            // Check and create .claude/commands/fix_issue.md if needed
            ensureFixIssueCommandExists();

            // Check and setup MCP connection if needed
            ensureMcpConnectionExists(jiraUrl, username, apiToken);

            Messages.showInfoMessage(
                project,
                "Jira MCP 연결이 완료되었습니다.\n터미널을 확인하세요.",
                "Jira MCP 연결"
            );
        } catch (Exception ex) {
            Messages.showErrorDialog(
                project,
                "Jira MCP 연결 중 오류가 발생했습니다:\n" + ex.getMessage(),
                "오류"
            );
        }
    }

    private void ensureFixIssueCommandExists() throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }

        Path targetPath = Paths.get(basePath, ".claude", "commands", "fix_issue.md");
        File targetFile = targetPath.toFile();

        // If file already exists, skip
        if (targetFile.exists()) {
            return;
        }

        // Create parent directories if they don't exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Copy fix_issue.md from resources to .claude/commands/
        try (InputStream sourceStream = getClass().getResourceAsStream("/jira/fix_issue.md")) {
            if (sourceStream != null) {
                Files.copy(sourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String getUvxPath() throws Exception {
        String osName = System.getProperty("os.name").toLowerCase();
        String command;

        if (osName.contains("win")) {
            // Windows: use 'where' command
            command = "where uvx";
        } else {
            // macOS/Linux: use 'which' command
            command = "which uvx";
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
            osName.contains("win") ? new String[]{"cmd", "/c", command} : new String[]{"sh", "-c", command}
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0 || output.toString().trim().isEmpty()) {
            throw new Exception("uvx가 설치되어 있지 않습니다.\n\n다음 명령어로 설치해주세요:\npip install uv");
        }

        // Get first line (in case multiple paths are returned)
        String uvxPath = output.toString().trim().split("\n")[0].trim();
        return uvxPath;
    }

    private void ensureMcpConnectionExists(String jiraUrl, String username, String apiToken) throws Exception {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return;
        }

        Path mcpJsonPath = Paths.get(basePath, ".mcp.json");
        File mcpJsonFile = mcpJsonPath.toFile();

        // Check if .mcp.json exists and contains atlassian-jira
        boolean needsSetup = true;
        if (mcpJsonFile.exists()) {
            String mcpContent = Files.readString(mcpJsonPath, StandardCharsets.UTF_8);
            Gson gson = new Gson();
            JsonObject mcpJson = gson.fromJson(mcpContent, JsonObject.class);

            // Check if atlassian-jira exists in mcpServers
            if (mcpJson != null && mcpJson.has("mcpServers")) {
                JsonObject mcpServers = mcpJson.getAsJsonObject("mcpServers");
                if (mcpServers.has("atlassian-jira")) {
                    needsSetup = false;
                }
            }
        }

        if (!needsSetup) {
            return;
        }

        // Get uvx path
        String uvxPath = getUvxPath();

        // Read mcp-add-json.txt template from resources
        String mcpTemplate;
        try (InputStream templateStream = getClass().getResourceAsStream("/jira/mcp-add-json.txt")) {
            if (templateStream == null) {
                throw new Exception("MCP template file not found in plugin resources");
            }
            mcpTemplate = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Replace placeholders with actual values
        mcpTemplate = mcpTemplate.replace("<uvx_path>", uvxPath);
        mcpTemplate = mcpTemplate.replace("<jira_url>", jiraUrl);
        mcpTemplate = mcpTemplate.replace("<email>", username);
        mcpTemplate = mcpTemplate.replace("<access_token>", apiToken);

        // Ensure jiraUrl is properly formatted
        if (!jiraUrl.endsWith("/")) {
            jiraUrl += "/";
        }
        mcpTemplate = mcpTemplate.replace("https://enomix.atlassian.net/", jiraUrl);

        // Escape single quotes in JSON content for shell command
        String escapedMcpTemplate = mcpTemplate.replace("'", "'\"'\"'");

        String osName = System.getProperty("os.name").toLowerCase();
        String command = String.format("claude mcp add-json atlassian-jira '%s' --scope project",
            escapedMcpTemplate);

        // Execute command in background without showing terminal
        ProcessBuilder processBuilder = new ProcessBuilder(
            osName.contains("win") ? new String[]{"cmd", "/c", command} : new String[]{"sh", "-c", command}
        );
        processBuilder.directory(new File(basePath));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Wait for completion in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode != 0) {
                        Messages.showErrorDialog(
                            project,
                            "Jira MCP 연결에 실패했습니다.",
                            "오류"
                        );
                    }
                });
            } catch (InterruptedException e) {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(
                        project,
                        "Jira MCP 연결 중 오류가 발생했습니다: " + e.getMessage(),
                        "오류"
                    );
                });
            }
        });
    }
}