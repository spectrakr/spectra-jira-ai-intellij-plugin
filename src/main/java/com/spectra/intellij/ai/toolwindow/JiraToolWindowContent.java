package com.spectra.intellij.ai.toolwindow;

import com.google.gson.JsonObject;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
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
    private String currentSprintId;
    private JBTable issueTable;
    private DefaultTableModel issueTableModel;
    private DefaultTableModel originalIssueTableModel; // Store unfiltered data
    
    // Issue detail panel components
    private JPanel issueDetailPanel;
    private JTextField issueKeyField;
    private JButton hamburgerMenuButton;
    private JTextField issueSummaryField;
    private boolean isSummaryEditing = false;
    private String originalSummaryValue = "";
    private JComboBox<String> issueStatusComboBox;
    private boolean isStatusEditing = false;
    private String originalStatusValue = "";
    private JTextArea issueDescriptionField;
    private JScrollPane descriptionScrollPane;
    private JLabel assigneeLabel;
    private JLabel reporterLabel;
    private JTextField storyPointsField;
    private boolean isStoryPointsEditing = false;
    private String originalStoryPointsValue = "";
    private boolean isDescriptionEditing = false;
    private String originalDescriptionValue = "";
    private JPanel descriptionButtonPanel;
    private JButton saveDescriptionButton;
    private JButton cancelDescriptionButton;
    // Removed saveIssueButton and cancelIssueButton - using inline editing instead
    private JiraIssue currentEditingIssue;
    
    // Filter components
    private JComboBox<String> issueTypeFilter;
    private JComboBox<String> assigneeFilter;
    private JComboBox<String> statusFilter;
    
    private JsonObject currentUser;
    
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
        
        // Issue Key with hamburger menu
        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        JPanel issueKeyPanel = new JPanel(new BorderLayout());
        
        issueKeyField = new JTextField();
        issueKeyField.setEditable(false);
        issueKeyField.setFont(issueKeyField.getFont().deriveFont(Font.BOLD, 14f));
        issueKeyField.setBackground(formPanel.getBackground());
        issueKeyField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        issueKeyPanel.add(issueKeyField, BorderLayout.CENTER);
        
        // Hamburger menu button
        hamburgerMenuButton = new JButton("☰");
        hamburgerMenuButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        hamburgerMenuButton.setPreferredSize(new Dimension(30, 30));
        hamburgerMenuButton.setMargin(new Insets(2, 2, 2, 2));
        hamburgerMenuButton.setBorderPainted(false);
        hamburgerMenuButton.setFocusPainted(false);
        hamburgerMenuButton.setContentAreaFilled(false);
        hamburgerMenuButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setupHamburgerMenu();
        issueKeyPanel.add(hamburgerMenuButton, BorderLayout.EAST);
        
        formPanel.add(issueKeyPanel, gbc);
        
        // Summary (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueSummaryField = new JTextField();
        issueSummaryField.setFont(issueSummaryField.getFont().deriveFont(Font.BOLD, 12f));
        setupInlineEditForSummary();
        formPanel.add(issueSummaryField, gbc);
        
        // Status (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        issueStatusComboBox = new JComboBox<>();
        setupInlineEditForStatus();
        formPanel.add(issueStatusComboBox, gbc);
        
        // Description (no label) - with inline edit capability
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3; gbc.gridwidth = 2;
        
        // Create description panel container
        JPanel descriptionPanel = new JPanel(new BorderLayout());
        
        issueDescriptionField = new JTextArea();
        issueDescriptionField.setLineWrap(true);
        issueDescriptionField.setWrapStyleWord(true);
        issueDescriptionField.setRows(4);
        setupInlineEditForDescription();
        descriptionScrollPane = new JScrollPane(issueDescriptionField);
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionPanel.add(descriptionScrollPane, BorderLayout.CENTER);
        
        // Create button panel for description editing (initially hidden)
        descriptionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        saveDescriptionButton = new JButton("Save");
        cancelDescriptionButton = new JButton("Cancel");
        
        saveDescriptionButton.addActionListener(e -> saveDescriptionAndExit());
        cancelDescriptionButton.addActionListener(e -> cancelDescriptionEditing());
        
        descriptionButtonPanel.add(saveDescriptionButton);
        descriptionButtonPanel.add(cancelDescriptionButton);
        descriptionButtonPanel.setVisible(false); // Initially hidden
        
        descriptionPanel.add(descriptionButtonPanel, BorderLayout.SOUTH);
        formPanel.add(descriptionPanel, gbc);
        
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
        
        // Create assignee label with click functionality
        assigneeLabel = new JLabel("할당되지 않음");
        assigneeLabel.setOpaque(true);
        assigneeLabel.setBackground(UIManager.getColor("Label.background"));
        assigneeLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        assigneeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setupAssigneeLabelHandlers();
        detailsPanel.add(assigneeLabel, detailGbc);
        
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
        setupInlineEditForStoryPoints();
        detailsPanel.add(storyPointsField, detailGbc);
        
        formPanel.add(detailsPanel, gbc);
        
        detailPanel.add(formPanel, BorderLayout.CENTER);

        // Initially show "Select an issue" message
        clearIssueDetail();
        
        return detailPanel;
    }
    
    private void setupInlineEditForSummary() {
        // Initially make it look like a label (non-editable)
        setSummaryDisplayMode();
        
        // Add mouse click listener to enter edit mode
        issueSummaryField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentEditingIssue != null && issueSummaryField.isEnabled()) {
                    enterSummaryEditMode();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentEditingIssue != null && issueSummaryField.isEnabled() && !isSummaryEditing) {
                    Color currentBg = UIManager.getColor("Panel.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        issueSummaryField.setBackground(new Color(r, g, b));
                    } else {
                        issueSummaryField.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    issueSummaryField.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentEditingIssue != null && issueSummaryField.isEnabled() && !isSummaryEditing) {
                    issueSummaryField.setBackground(UIManager.getColor("Panel.background"));
                    issueSummaryField.repaint();
                }
            }
        });
        
        // Add key listener for Enter key and Escape key
        issueSummaryField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    exitSummaryEditMode(true); // Save changes
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exitSummaryEditMode(false); // Cancel changes
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add focus listener to save changes when focus is lost
        issueSummaryField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (isSummaryEditing) {
                    exitSummaryEditMode(true); // Save changes when focus is lost
                }
            }
        });
    }
    
    private void setSummaryDisplayMode() {
        isSummaryEditing = false;
        issueSummaryField.setEditable(false);
        issueSummaryField.setFocusable(false); // Prevent focus
        issueSummaryField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        issueSummaryField.setBackground(UIManager.getColor("Panel.background"));
        issueSummaryField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    
    private void enterSummaryEditMode() {
        if (currentEditingIssue == null) return;
        
        isSummaryEditing = true;
        originalSummaryValue = issueSummaryField.getText();
        issueSummaryField.setEditable(true);
        issueSummaryField.setFocusable(true); // Re-enable focus for editing
        issueSummaryField.setBorder(UIManager.getBorder("TextField.border"));
        issueSummaryField.setBackground(UIManager.getColor("TextField.background"));
        issueSummaryField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        issueSummaryField.requestFocus();
        issueSummaryField.setCaretPosition(issueSummaryField.getText().length());
    }
    
    private void exitSummaryEditMode(boolean saveChanges) {
        if (!isSummaryEditing) return;
        
        if (saveChanges) {
            String newSummary = issueSummaryField.getText().trim();
            if (!newSummary.isEmpty() && !newSummary.equals(originalSummaryValue)) {
                // Save the changes immediately
                saveSummaryChange(newSummary);
            }
        } else {
            // Restore original value
            issueSummaryField.setText(originalSummaryValue);
        }
        
        setSummaryDisplayMode();
    }
    
    private void saveSummaryChange(String newSummary) {
        if (currentEditingIssue == null) return;
        
        String oldSummary = currentEditingIssue.getSummary();
        currentEditingIssue.setSummary(newSummary);
        
        updateStatus("Saving summary change...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueSummaryAsync(currentEditingIssue.getKey(), newSummary)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Summary updated successfully for " + currentEditingIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentEditingIssue.setSummary(oldSummary);
                    issueSummaryField.setText(oldSummary);
                    updateStatus("Error updating summary: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update summary: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    private void setupInlineEditForDescription() {
        // Initially make it look like a label (non-editable)
        setDescriptionDisplayMode();
        
        // Add mouse click listener to enter edit mode
        issueDescriptionField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentEditingIssue != null && issueDescriptionField.isEnabled()) {
                    enterDescriptionEditMode();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentEditingIssue != null && issueDescriptionField.isEnabled() && !isDescriptionEditing) {
                    Color currentBg = UIManager.getColor("Panel.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        issueDescriptionField.setBackground(new Color(r, g, b));
                    } else {
                        issueDescriptionField.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    issueDescriptionField.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentEditingIssue != null && issueDescriptionField.isEnabled() && !isDescriptionEditing) {
                    issueDescriptionField.setBackground(UIManager.getColor("Panel.background"));
                    issueDescriptionField.repaint();
                }
            }
        });
        
        // Add key listener for Enter key and Escape key
        issueDescriptionField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    saveDescriptionAndExit(); // Save changes with Ctrl+Enter
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelDescriptionEditing(); // Cancel changes
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add focus listener (no auto-save on focus loss for description)
        issueDescriptionField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Do not auto-save for description - user must use Save/Cancel buttons
            }
        });
    }
    
    private void setDescriptionDisplayMode() {
        isDescriptionEditing = false;
        issueDescriptionField.setEditable(false);
        issueDescriptionField.setFocusable(true); // Keep focusable to show cursor on hover
        
        // Make it look like plain text instead of a textarea
        issueDescriptionField.setBackground(UIManager.getColor("Panel.background"));
        issueDescriptionField.setBorder(null);
        issueDescriptionField.setOpaque(false);
        issueDescriptionField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Hide scroll pane border to make it look like plain text
        if (descriptionScrollPane != null) {
            descriptionScrollPane.setBorder(null);
            descriptionScrollPane.setOpaque(false);
            descriptionScrollPane.getViewport().setOpaque(false);
        }
        
        if (descriptionButtonPanel != null) {
            descriptionButtonPanel.setVisible(false);
        }
    }
    
    private void enterDescriptionEditMode() {
        if (currentEditingIssue == null) return;
        
        isDescriptionEditing = true;
        originalDescriptionValue = issueDescriptionField.getText();
        issueDescriptionField.setEditable(true);
        issueDescriptionField.setFocusable(true);
        
        // Restore textarea appearance for editing
        issueDescriptionField.setBackground(UIManager.getColor("TextArea.background"));
        issueDescriptionField.setBorder(UIManager.getBorder("TextField.border"));
        issueDescriptionField.setOpaque(true);
        issueDescriptionField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        
        // Restore scroll pane border and appearance for editing
        if (descriptionScrollPane != null) {
            descriptionScrollPane.setBorder(UIManager.getBorder("ScrollPane.border"));
            descriptionScrollPane.setOpaque(true);
            descriptionScrollPane.getViewport().setOpaque(true);
        }
        
        // Do not automatically focus or set caret position
        // Let user click where they want the cursor to be
        
        // Show save/cancel buttons
        if (descriptionButtonPanel != null) {
            descriptionButtonPanel.setVisible(true);
            descriptionButtonPanel.revalidate();
            descriptionButtonPanel.repaint();
        }
    }
    
    private void exitDescriptionEditMode(boolean saveChanges) {
        if (!isDescriptionEditing) return;
        
        if (saveChanges) {
            String newDescription = issueDescriptionField.getText().trim();
            if (!newDescription.equals(originalDescriptionValue)) {
                // Save the changes immediately
                saveDescriptionChange(newDescription);
            }
        } else {
            // Restore original value
            issueDescriptionField.setText(originalDescriptionValue);
        }
        
        setDescriptionDisplayMode();
    }
    
    private void saveDescriptionAndExit() {
        if (!isDescriptionEditing) return;
        
        String newDescription = issueDescriptionField.getText().trim();
        if (!newDescription.equals(originalDescriptionValue)) {
            // Save the changes immediately
            saveDescriptionChange(newDescription);
        } else {
            // No changes, just exit edit mode
            setDescriptionDisplayMode();
        }
    }
    
    private void cancelDescriptionEditing() {
        if (!isDescriptionEditing) return;
        
        // Restore original value
        issueDescriptionField.setText(originalDescriptionValue);
        setDescriptionDisplayMode();
    }
    
    private void saveDescriptionChange(String newDescription) {
        if (currentEditingIssue == null) return;
        
        String oldDescription = currentEditingIssue.getDescription();
        currentEditingIssue.setDescription(newDescription);
        
        updateStatus("Saving description change...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueDescriptionAsync(currentEditingIssue.getKey(), newDescription)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    // Exit edit mode
                    setDescriptionDisplayMode();
                    updateStatus("Description updated successfully for " + currentEditingIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentEditingIssue.setDescription(oldDescription);
                    issueDescriptionField.setText(oldDescription != null ? oldDescription : "");
                    updateStatus("Error updating description: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update description: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    private void setupHamburgerMenu() {
        hamburgerMenuButton.addActionListener(e -> showHamburgerMenu());
        
        // Show/hide button based on whether an issue is selected
        hamburgerMenuButton.setVisible(false);
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
        popupMenu.show(hamburgerMenuButton, 0, hamburgerMenuButton.getHeight());
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
    
    private void setupInlineEditForStoryPoints() {
        // Initially make it look like a label (non-editable)
        setStoryPointsDisplayMode();
        
        // Add mouse click listener to enter edit mode
        storyPointsField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentEditingIssue != null && storyPointsField.isEnabled()) {
                    enterStoryPointsEditMode();
                }
            }
        });
        
        // Add key listener for Enter key and Escape key
        storyPointsField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    exitStoryPointsEditMode(true); // Save changes
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exitStoryPointsEditMode(false); // Cancel changes
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add focus listener to save changes when focus is lost
        storyPointsField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (isStoryPointsEditing) {
                    exitStoryPointsEditMode(true); // Save changes when focus is lost
                }
            }
        });
    }
    
    private void setStoryPointsDisplayMode() {
        isStoryPointsEditing = false;
        storyPointsField.setEditable(false);
        storyPointsField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        storyPointsField.setBackground(UIManager.getColor("Panel.background"));
        storyPointsField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    
    private void enterStoryPointsEditMode() {
        if (currentEditingIssue == null) return;
        
        isStoryPointsEditing = true;
        originalStoryPointsValue = storyPointsField.getText();
        storyPointsField.setEditable(true);
        storyPointsField.setBorder(UIManager.getBorder("TextField.border"));
        storyPointsField.setBackground(UIManager.getColor("TextField.background"));
        storyPointsField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        storyPointsField.requestFocus();
        storyPointsField.selectAll();
    }
    
    private void exitStoryPointsEditMode(boolean saveChanges) {
        if (!isStoryPointsEditing) return;
        
        if (saveChanges) {
            String newStoryPoints = storyPointsField.getText().trim();
            if (!newStoryPoints.equals(originalStoryPointsValue)) {
                // Validate story points
                try {
                    if (!newStoryPoints.isEmpty()) {
                        Double.parseDouble(newStoryPoints);
                    }
                    // Save the changes immediately
                    saveStoryPointsChange(newStoryPoints);
                } catch (NumberFormatException e) {
                    Messages.showErrorDialog(project, "Story points must be a valid number (e.g., 0.5, 1, 2)", "Validation Error");
                    storyPointsField.setText(originalStoryPointsValue);
                }
            }
        } else {
            // Restore original value
            storyPointsField.setText(originalStoryPointsValue);
        }
        
        setStoryPointsDisplayMode();
    }
    
    private void saveStoryPointsChange(String newStoryPoints) {
        if (currentEditingIssue == null) return;
        
        Double oldStoryPoints = currentEditingIssue.getStoryPoints();
        Double newStoryPointsValue = newStoryPoints.isEmpty() ? null : Double.parseDouble(newStoryPoints);
        currentEditingIssue.setStoryPoints(newStoryPointsValue);
        
        updateStatus("Saving story points change...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueStoryPointsAsync(currentEditingIssue.getKey(), newStoryPointsValue)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Story points updated successfully for " + currentEditingIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentEditingIssue.setStoryPoints(oldStoryPoints);
                    storyPointsField.setText(oldStoryPoints != null ? oldStoryPoints.toString() : "");
                    updateStatus("Error updating story points: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update story points: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    private void setupInlineEditForStatus() {
        // Status combo box doesn't need display mode like text fields
        // Just add action listener to handle immediate changes
        issueStatusComboBox.addActionListener(e -> {
            if (currentEditingIssue != null && !isStatusEditing) {
                String newStatus = (String) issueStatusComboBox.getSelectedItem();
                if (newStatus != null && !newStatus.equals(originalStatusValue)) {
                    saveStatusChange(newStatus);
                }
            }
        });
    }
    
    private void saveStatusChange(String newStatus) {
        if (currentEditingIssue == null) return;
        
        isStatusEditing = true; // Prevent recursive calls
        String oldStatus = currentEditingIssue.getStatus();
        currentEditingIssue.setStatus(newStatus);
        
        updateStatus("Saving status change...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueStatusAsync(currentEditingIssue.getKey(), newStatus)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    isStatusEditing = false;
                    originalStatusValue = newStatus; // Update original value
                    updateStatus("Status updated successfully for " + currentEditingIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    isStatusEditing = false;
                    // Restore old value on error
                    currentEditingIssue.setStatus(oldStatus);
                    issueStatusComboBox.setSelectedItem(oldStatus);
                    updateStatus("Error updating status: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update status: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
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
        loadSprintIssues(sprintId, null);
    }
    
    private void loadSprintIssues(String sprintId, String preserveSelectedIssueKey) {
        this.currentSprintId = sprintId; // Track current sprint
        updateStatus("Loading issues from sprint: " + sprintId + "...");
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.getSprintIssuesAsync(sprintId)
            .thenAccept(issues -> {
                SwingUtilities.invokeLater(() -> {
                    // Clear both tables
                    issueTableModel.setRowCount(0);
                    originalIssueTableModel.setRowCount(0);
                    
                    // Only clear issue detail if we're not preserving selection
                    if (preserveSelectedIssueKey == null) {
                        clearIssueDetail();
                    }
                    
                    // Clear filter options
                    clearFilterOptions();
                    
                    IssueTableCellRenderer renderer = (IssueTableCellRenderer) issueTable.getColumnModel().getColumn(0).getCellRenderer();
                    
                    int row = 0;
                    int selectedRowIndex = -1;
                    for (JiraIssue issue : issues) {
                        String storyPointsStr = "";
                        if (issue.getStoryPoints() != null) {
                            storyPointsStr = issue.getStoryPoints().toString();
                        }
                        
                        String issueType = issue.getIssueType() != null ? issue.getIssueType() : "";
                        String assignee = issue.getAssignee() != null ? issue.getAssignee() : "";
                        String status = issue.getStatus() != null ? issue.getStatus() : "";
                        String summary = issue.getSummary() != null ? issue.getSummary() : "";
                        
                        // Check if this is the issue we want to preserve selection for
                        if (preserveSelectedIssueKey != null && issue.getKey().equals(preserveSelectedIssueKey)) {
                            selectedRowIndex = row;
                        }
                        
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
                    
                    // Restore selection if requested
                    if (preserveSelectedIssueKey != null && selectedRowIndex >= 0) {
                        issueTable.setRowSelectionInterval(selectedRowIndex, selectedRowIndex);
                        // Ensure the selected row is visible
                        issueTable.scrollRectToVisible(issueTable.getCellRect(selectedRowIndex, 0, true));
                    }
                    
                    updateStatus("Loaded " + issues.size() + " issues from sprint");
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    issueTableModel.setRowCount(0);
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
        issueDescriptionField.setText(issue.getDescription() != null ? issue.getDescription() : "");
        
        // Populate detail fields
        // Initialize assignee label based on current issue assignee
        if (issue.getAssignee() != null && !issue.getAssignee().trim().isEmpty()) {
            assigneeLabel.setText(issue.getAssignee());
        } else {
            assigneeLabel.setText("할당되지 않음");
        }
        reporterLabel.setText(issue.getReporter() != null ? issue.getReporter() : "없음");
        storyPointsField.setText(issue.getStoryPoints() != null ? issue.getStoryPoints().toString() : "");
        
        // Enable editing controls and set display modes for inline editing
        issueSummaryField.setEnabled(true);
        setSummaryDisplayMode(); // Set to display mode for inline editing
        
        issueStatusComboBox.setEnabled(true);
        originalStatusValue = issue.getStatus() != null ? issue.getStatus() : "";
        
        issueDescriptionField.setEnabled(true);
        setDescriptionDisplayMode(); // Set to display mode for inline editing
        
        assigneeLabel.setEnabled(true);
        
        storyPointsField.setEnabled(true);
        setStoryPointsDisplayMode(); // Set to display mode for inline editing
        
        // Show hamburger menu when issue is loaded
        hamburgerMenuButton.setVisible(true);
    }
    
    
    private void clearIssueDetail() {
        currentEditingIssue = null;
        issueKeyField.setText("이슈를 선택하세요");
        issueSummaryField.setText("");
        issueDescriptionField.setText("");
        issueStatusComboBox.removeAllItems();
        
        // Clear detail fields
        assigneeLabel.setText("할당되지 않음");
        reporterLabel.setText("");
        storyPointsField.setText("");
        
        // Disable editing controls and set display modes
        issueSummaryField.setEnabled(false);
        setSummaryDisplayMode(); // Ensure proper display mode when disabled
        
        issueStatusComboBox.setEnabled(false);
        originalStatusValue = "";
        
        issueDescriptionField.setEnabled(false);
        setDescriptionDisplayMode(); // Ensure proper display mode when disabled
        
        assigneeLabel.setEnabled(false);
        
        storyPointsField.setEnabled(false);
        setStoryPointsDisplayMode(); // Ensure proper display mode when disabled
        
        assigneeLabel.setEnabled(false);
        
        // Hide hamburger menu when no issue is selected
        hamburgerMenuButton.setVisible(false);
    }
    
    private void setupAssigneeLabelHandlers() {
        assigneeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentEditingIssue != null && assigneeLabel.isEnabled()) {
                    showAssigneeSelectionPopup();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentEditingIssue != null && assigneeLabel.isEnabled()) {
                    Color currentBg = UIManager.getColor("Label.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        assigneeLabel.setBackground(new Color(r, g, b));
                    } else {
                        assigneeLabel.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    assigneeLabel.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentEditingIssue != null && assigneeLabel.isEnabled()) {
                    assigneeLabel.setBackground(UIManager.getColor("Label.background"));
                    assigneeLabel.repaint();
                }
            }
        });
    }
    
    private void showAssigneeSelectionPopup() {
        if (!isConfigured()) {
            return;
        }
        
        updateStatus("Loading assignable users...");
        JiraService jiraService = getConfiguredJiraService();
        
        jiraService.getProjectUsersAsync(jiraService.getProjectKey())
            .thenAccept(users -> SwingUtilities.invokeLater(() -> {
                showAssigneeSearchDialog(users);
                updateStatus("Ready");
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error loading users: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void showAssigneeSearchDialog(List<String> allUsers) {
        // Create dialog panel
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(300, 180));
        
        // Create search field
        JTextField searchField = new JTextField();
        searchField.setBorder(BorderFactory.createTitledBorder("담당자 검색"));
        dialogPanel.add(searchField, BorderLayout.NORTH);
        
        // Create user list with limited visible rows
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setVisibleRowCount(4); // Limit to 4 visible rows
        
        // Enable hover selection for better user experience
        userList.putClientProperty("List.mouseHoverSelection", true);
        userList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
        userList.setSelectionForeground(UIManager.getColor("List.selectionForeground"));
        
        // Ensure selection is always visible
        userList.setFocusable(true);
        
        // Custom renderer with enhanced visual feedback
        userList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                String displayName = (String) value;
                
                // Set colors for better visual feedback
                if (isSelected) {
                    // Selected item - use IntelliJ theme colors
                    setBackground(UIManager.getColor("List.selectionBackground"));
                    setForeground(UIManager.getColor("List.selectionForeground"));
                } else {
                    // Normal item
                    setBackground(UIManager.getColor("List.background"));
                    setForeground(UIManager.getColor("List.foreground"));
                }
                
                // Add special formatting for special items
                if (displayName.contains("(나에게 할당)")) {
                    setFont(getFont().deriveFont(Font.BOLD));
                    if (!isSelected) {
                        setForeground(new Color(0, 100, 0)); // Dark green for "assign to me"
                    }
                } else if (displayName.equals("할당되지 않음")) {
                    setFont(getFont().deriveFont(Font.ITALIC));
                    if (!isSelected) {
                        setForeground(new Color(100, 100, 100)); // Gray for "unassigned"
                    }
                } else {
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                
                // Add padding for better spacing
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                
                return this;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Enable mouse wheel scrolling
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);
        
        // Fix mouse wheel scrolling in popup menu
        userList.addMouseWheelListener(e -> {
            // Forward mouse wheel events to the scroll pane
            if (scrollPane.getVerticalScrollBar().isVisible()) {
                JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                int scrollAmount = e.getUnitsToScroll() * scrollBar.getUnitIncrement();
                int newValue = scrollBar.getValue() + scrollAmount;
                newValue = Math.max(scrollBar.getMinimum(), Math.min(scrollBar.getMaximum() - scrollBar.getVisibleAmount(), newValue));
                scrollBar.setValue(newValue);
            }
        });
        
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Initialize the list with all options
        updateUserList(listModel, allUsers, "");
        
        // Set initial selection to first item if available
        if (listModel.getSize() > 0) {
            userList.setSelectedIndex(0);
            userList.ensureIndexIsVisible(0);
        }
        
        // Add search functionality
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterUsers();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterUsers();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterUsers();
            }
            
            private void filterUsers() {
                String searchText = searchField.getText().toLowerCase().trim();
                updateUserList(listModel, allUsers, searchText);
                // Auto-select first item if list is not empty
                if (listModel.getSize() > 0) {
                    userList.setSelectedIndex(0);
                }
            }
        });
        
        // Add keyboard navigation support to search field
        searchField.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // Select currently selected item in the list if available
                    if (listModel.getSize() > 0) {
                        int selectedIndex = userList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            userList.setSelectedIndex(0);
                        }
                        selectUser(userList);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // Move focus to list and navigate down
                    if (listModel.getSize() > 0) {
                        userList.requestFocus();
                        int selectedIndex = userList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            userList.setSelectedIndex(0);
                        } else {
                            int nextIndex = Math.min(selectedIndex + 1, listModel.getSize() - 1);
                            userList.setSelectedIndex(nextIndex);
                        }
                        userList.ensureIndexIsVisible(userList.getSelectedIndex());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // Move focus to list and navigate up
                    if (listModel.getSize() > 0) {
                        userList.requestFocus();
                        int selectedIndex = userList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            userList.setSelectedIndex(listModel.getSize() - 1);
                        } else {
                            int prevIndex = Math.max(selectedIndex - 1, 0);
                            userList.setSelectedIndex(prevIndex);
                        }
                        userList.ensureIndexIsVisible(userList.getSelectedIndex());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Close popup on Escape
                    Component comp = searchField;
                    while (comp != null && !(comp instanceof JPopupMenu)) {
                        comp = comp.getParent();
                    }
                    if (comp instanceof JPopupMenu) {
                        ((JPopupMenu) comp).setVisible(false);
                    }
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add double-click and Enter key support
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectUser(userList);
                }
            }
        });
        
        userList.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectUser(userList);
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    // Handle UP arrow key
                    int selectedIndex = userList.getSelectedIndex();
                    if (selectedIndex > 0) {
                        userList.setSelectedIndex(selectedIndex - 1);
                        userList.ensureIndexIsVisible(selectedIndex - 1);
                    } else if (selectedIndex == 0) {
                        // Stay at top, but ensure it's visible
                        userList.ensureIndexIsVisible(0);
                    }
                    e.consume(); // Prevent default behavior
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    // Handle DOWN arrow key
                    int selectedIndex = userList.getSelectedIndex();
                    if (selectedIndex < listModel.getSize() - 1) {
                        userList.setSelectedIndex(selectedIndex + 1);
                        userList.ensureIndexIsVisible(selectedIndex + 1);
                    } else if (selectedIndex == listModel.getSize() - 1) {
                        // Stay at bottom, but ensure it's visible
                        userList.ensureIndexIsVisible(listModel.getSize() - 1);
                    }
                    e.consume(); // Prevent default behavior
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // Close popup on Escape
                    Component comp = userList;
                    while (comp != null && !(comp instanceof JPopupMenu)) {
                        comp = comp.getParent();
                    }
                    if (comp instanceof JPopupMenu) {
                        ((JPopupMenu) comp).setVisible(false);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    // Return focus to search field for backspace
                    searchField.requestFocus();
                    searchField.dispatchEvent(e);
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
                // Forward printable characters to search field
                if (!Character.isISOControl(e.getKeyChar()) && searchField != null) {
                    searchField.requestFocus();
                    searchField.dispatchEvent(e);
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Create and show popup
        JPopupMenu popup = new JPopupMenu();
        popup.add(dialogPanel);
        popup.show(assigneeLabel, 0, assigneeLabel.getHeight());
        
        // Focus on search field
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }
    
    private void updateUserList(DefaultListModel<String> listModel, List<String> allUsers, String searchText) {
        listModel.clear();
        
        // Add "me" option at the top
        if (currentUser != null) {
            String myDisplayName = currentUser.get("displayName").getAsString();
            String meOption = myDisplayName + " (나에게 할당)";
            if (searchText.isEmpty() || myDisplayName.toLowerCase().contains(searchText)) {
                listModel.addElement(meOption);
            }
        }
        
        // Add "unassign" option
        if (searchText.isEmpty() || "할당되지 않음".toLowerCase().contains(searchText) || 
            "unassigned".toLowerCase().contains(searchText)) {
            listModel.addElement("할당되지 않음");
        }
        
        // Add filtered users
        for (String userDisplayName : allUsers) {
            if (currentUser != null && userDisplayName.equals(currentUser.get("displayName").getAsString())) {
                continue; // Skip current user as it's already at the top
            }
            
            if (searchText.isEmpty() || userDisplayName.toLowerCase().contains(searchText)) {
                listModel.addElement(userDisplayName);
            }
        }
    }
    
    private void selectUser(JList<String> userList) {
        String selectedUser = userList.getSelectedValue();
        if (selectedUser == null) return;
        
        // Close the popup
        Component comp = userList;
        while (comp != null && !(comp instanceof JPopupMenu)) {
            comp = comp.getParent();
        }
        if (comp instanceof JPopupMenu) {
            ((JPopupMenu) comp).setVisible(false);
        }
        
        // Handle selection
        if (selectedUser.contains("(나에게 할당)")) {
            // Assign to me
            if (currentUser != null) {
                String myDisplayName = currentUser.get("displayName").getAsString();
                String myAccountId = currentUser.get("accountId").getAsString();
                updateAssignee(myAccountId, myDisplayName);
            }
        } else if (selectedUser.equals("할당되지 않음")) {
            // Unassign
            updateAssignee(null, null);
        } else {
            // Regular user
            findAccountIdAndUpdateAssignee(selectedUser);
        }
    }
    
    private void findAccountIdAndUpdateAssignee(String displayName) {
        updateStatus("Finding user account...");
        JiraService jiraService = getConfiguredJiraService();
        
        jiraService.searchUsersAsync(displayName)
            .thenAccept(users -> SwingUtilities.invokeLater(() -> {
                for (com.google.gson.JsonObject user : users) {
                    if (user.has("displayName") && displayName.equals(user.get("displayName").getAsString())) {
                        String accountId = user.get("accountId").getAsString();
                        updateAssignee(accountId, displayName);
                        return;
                    }
                }
                updateStatus("User not found: " + displayName);
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error finding user: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private void updateAssignee(String accountId, String displayName) {
        if (currentEditingIssue == null) return;
        
        String oldAssignee = currentEditingIssue.getAssignee();
        String newAssignee = displayName != null ? displayName : null;
        
        if ((oldAssignee == null && newAssignee == null) || 
            (oldAssignee != null && oldAssignee.equals(newAssignee))) {
            updateStatus("No changes to assignee");
            return;
        }
        
        updateStatus("Updating assignee...");
        currentEditingIssue.setAssignee(newAssignee);
        
        JiraService jiraService = getConfiguredJiraService();
        jiraService.updateIssueAssigneeAsync(currentEditingIssue.getKey(), accountId)
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                // Update UI
                if (displayName != null) {
                    assigneeLabel.setText(displayName);
                } else {
                    assigneeLabel.setText("할당되지 않음");
                }
                
                updateStatus("Assignee updated successfully for " + currentEditingIssue.getKey());
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentEditingIssue.setAssignee(oldAssignee);
                    if (oldAssignee != null) {
                        assigneeLabel.setText(oldAssignee);
                    } else {
                        assigneeLabel.setText("할당되지 않음");
                    }
                    updateStatus("Error updating assignee: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update assignee: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    // Removed saveCurrentIssue() and cancelIssueEditing() methods
    // All editing is now handled inline with individual field save functionality
    
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
