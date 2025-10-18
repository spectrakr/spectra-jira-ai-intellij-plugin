package com.spectra.intellij.ai.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.model.JiraEpic;
import com.spectra.intellij.ai.model.AIRecommendationResponse;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.settings.RecentJiraSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class CreateIssueDialog extends DialogWrapper {
    
    private JTextField summaryField;
    private JTextArea descriptionArea;
    private JButton aiGenerateButton;
    private JComboBox<String> priorityComboBox;
    private JComboBox<String> issueTypeComboBox;
    private JComboBox<JiraSprint> sprintComboBox;
    private JComboBox<JiraEpic> epicComboBox;
    private JButton aiRecommendEpicButton;
    private JComboBox<String> assigneeComboBox;
    private JLabel assigneeDisplayLabel;
    private JLabel epicDisplayLabel;
    private JTextField storyPointsField;
    private String selectedAssignee;
    private JiraEpic selectedEpic;
    private JiraIssue issue;
    private final JiraService jiraService;
    private final Project project;
    private Map<String, String> issueTypesMap; // name -> id mapping
    private final JiraSprint preselectedSprint;
    
    
    public CreateIssueDialog(Project project, JiraService jiraService) {
        this(project, jiraService, null);
    }
    
    public CreateIssueDialog(Project project, JiraService jiraService, JiraSprint preselectedSprint) {
        super(project);
        this.project = project;
        this.jiraService = jiraService;
        this.preselectedSprint = preselectedSprint;
        setTitle("Create Jira Issue");
        init();
        loadData();
        loadCurrentUser(); // Load and pre-select current user as assignee
        loadRecentEpic(); // Load and pre-select recent epic if available
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Summary
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Summary:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        summaryField = new JTextField(45);
        panel.add(summaryField, gbc);
        
        // Description
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        descriptionArea = new JTextArea(12, 45);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));
        JScrollPane descriptionScrollPane = new JScrollPane(descriptionArea);
        descriptionScrollPane.setBorder(summaryField.getBorder());
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(descriptionScrollPane, gbc);
        
        // AI Generate Button
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        aiGenerateButton = new JButton("(AI) 내용 자동 생성");
        aiGenerateButton.addActionListener(e -> performAIWorkGeneration());
        panel.add(aiGenerateButton, gbc);
        
        // Priority
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Priority:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        priorityComboBox = new JComboBox<>(new String[]{"High", "Medium", "Low", "Highest", "Lowest"});
        // Set default priority from recent settings
        RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
        String lastPriority = recentSettings.getLastUsedPriority();
        if (!lastPriority.isEmpty()) {
            priorityComboBox.setSelectedItem(lastPriority);
        } else {
            priorityComboBox.setSelectedItem("Medium");
        }
        panel.add(priorityComboBox, gbc);
        
        // Sprint
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Sprint:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        sprintComboBox = new JComboBox<>();
        sprintComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof JiraSprint) {
                    JiraSprint sprint = (JiraSprint) value;
                    setText(sprint.getName() + " (" + sprint.getState() + ")");
                }
                return this;
            }
        });
        panel.add(sprintComboBox, gbc);
        
        // Issue Type
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Issue Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        issueTypeComboBox = new JComboBox<>();
        panel.add(issueTypeComboBox, gbc);
        
        // Epic
        gbc.gridx = 0; gbc.gridy = 6; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("상위항목:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        
        JPanel epicPanel = new JPanel(new BorderLayout());
        
        // Epic display label (clickable)
        epicDisplayLabel = new JLabel("상위항목 선택");
        epicDisplayLabel.setOpaque(true);
        epicDisplayLabel.setBackground(UIManager.getColor("TextField.background"));
        epicDisplayLabel.setBorder(summaryField.getBorder());
        epicDisplayLabel.setPreferredSize(summaryField.getPreferredSize());
        epicDisplayLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        epicDisplayLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showEpicSearchDialog();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                epicDisplayLabel.setBackground(UIManager.getColor("TextField.selectionBackground"));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                epicDisplayLabel.setBackground(UIManager.getColor("TextField.background"));
            }
        });
        
        aiRecommendEpicButton = new JButton("AI 추천");
        aiRecommendEpicButton.addActionListener(e -> performAIRecommendation());
        
        epicPanel.add(epicDisplayLabel, BorderLayout.CENTER);
        epicPanel.add(aiRecommendEpicButton, BorderLayout.EAST);
        panel.add(epicPanel, gbc);
        
        // Story Points
        gbc.gridx = 0; gbc.gridy = 7; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Story Points:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        storyPointsField = new JTextField(10);
        storyPointsField.setToolTipText("스토리 포인트를 입력하세요 (예: 1, 2, 3, 5, 8)");
        panel.add(storyPointsField, gbc);
        
        // Assignee
        gbc.gridx = 0; gbc.gridy = 8; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("담당자:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        
        // Assignee display label (clickable)
        assigneeDisplayLabel = new JLabel("담당자 선택");
        assigneeDisplayLabel.setOpaque(true);
        assigneeDisplayLabel.setBackground(UIManager.getColor("TextField.background"));
        assigneeDisplayLabel.setBorder(summaryField.getBorder());
        assigneeDisplayLabel.setPreferredSize(summaryField.getPreferredSize());
        assigneeDisplayLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        assigneeDisplayLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAssigneeSearchDialog();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                assigneeDisplayLabel.setBackground(UIManager.getColor("TextField.selectionBackground"));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                assigneeDisplayLabel.setBackground(UIManager.getColor("TextField.background"));
            }
        });
        panel.add(assigneeDisplayLabel, gbc);
        
        return panel;
    }
    
    @Override
    protected void doOKAction() {
        if (isValid()) {
            issue = new JiraIssue();
            issue.setSummary(summaryField.getText().trim());
            issue.setDescription(descriptionArea.getText().trim());
            issue.setPriority((String) priorityComboBox.getSelectedItem());
            String selectedIssueTypeName = (String) issueTypeComboBox.getSelectedItem();
            issue.setIssueType(selectedIssueTypeName);
            if (issueTypesMap != null && selectedIssueTypeName != null) {
                issue.setIssueTypeId(issueTypesMap.get(selectedIssueTypeName));
            }
            
            JiraSprint selectedSprint = (JiraSprint) sprintComboBox.getSelectedItem();
            if (selectedSprint != null) {
                issue.setSprintId(selectedSprint.getId());
                issue.setSprintName(selectedSprint.getName());
            }
            
            if (selectedEpic != null) {
                issue.setEpicKey(selectedEpic.getKey());
                issue.setEpicName(selectedEpic.getSummary());
            }
            
            if (selectedAssignee != null && !"할당되지 않음".equals(selectedAssignee)) {
                issue.setAssignee(selectedAssignee);
            }
            
            // Set story points if provided
            String storyPointsText = storyPointsField.getText().trim();
            if (!storyPointsText.isEmpty()) {
                try {
                    Double storyPoints = Double.parseDouble(storyPointsText);
                    issue.setStoryPoints(storyPoints);
                } catch (NumberFormatException e) {
                    // Invalid story points input - show error
                    JOptionPane.showMessageDialog(
                        getContentPane(),
                        "Story Points must be a valid number",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    storyPointsField.requestFocus();
                    return;
                }
            }
            
            // Save recent values for next time
            RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
            recentSettings.updateRecentValues(
                (String) priorityComboBox.getSelectedItem(),
                selectedSprint != null ? selectedSprint.getId() : "",
                selectedSprint != null ? selectedSprint.getName() : "",
                issue.getIssueTypeId(),
                (String) issueTypeComboBox.getSelectedItem(),
                selectedEpic != null ? selectedEpic.getKey() : "",
                selectedEpic != null ? selectedEpic.getSummary() : "",
                selectedAssignee
            );
            
            super.doOKAction();
        }
    }
    
    private boolean isValid() {
        if (summaryField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                getContentPane(),
                "Summary is required",
                "Validation Error",
                JOptionPane.ERROR_MESSAGE
            );
            summaryField.requestFocus();
            return false;
        }
        return true;
    }
    
    public JiraIssue getIssue() {
        return issue;
    }
    
    private void loadData() {
        // Save current selections
        JiraSprint currentSprint = (JiraSprint) sprintComboBox.getSelectedItem();
        String currentIssueType = (String) issueTypeComboBox.getSelectedItem();
        
        // Load sprints
        jiraService.getSprintsAsync()
                .thenAccept(sprints -> {
                    SwingUtilities.invokeLater(() -> {
                        sprintComboBox.removeAllItems();
                        
                        // Filter to show only non-closed sprints
                        for (JiraSprint sprint : sprints) {
                            if (!"closed".equalsIgnoreCase(sprint.getState())) {
                                sprintComboBox.addItem(sprint);
                            }
                        }
                        
                        // Prioritize preselected sprint, then recent sprint selection
                        if (preselectedSprint != null) {
                            // First priority: preselected sprint from tool window
                            for (int i = 0; i < sprintComboBox.getItemCount(); i++) {
                                JiraSprint item = sprintComboBox.getItemAt(i);
                                if (item.getId().equals(preselectedSprint.getId())) {
                                    sprintComboBox.setSelectedItem(item);
                                    break;
                                }
                            }
                        } else {
                            // Second priority: recent sprint selection
                            RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
                            String lastSprintId = recentSettings.getLastUsedSprintId();
                            if (!lastSprintId.isEmpty()) {
                                for (int i = 0; i < sprintComboBox.getItemCount(); i++) {
                                    JiraSprint item = sprintComboBox.getItemAt(i);
                                    if (item.getId().equals(lastSprintId)) {
                                        sprintComboBox.setSelectedItem(item);
                                        break;
                                    }
                                }
                            } else if (currentSprint != null) {
                                // Try to restore current selection if it exists
                                for (int i = 0; i < sprintComboBox.getItemCount(); i++) {
                                    JiraSprint item = sprintComboBox.getItemAt(i);
                                    if (item.getId().equals(currentSprint.getId())) {
                                        sprintComboBox.setSelectedItem(item);
                                        break;
                                    }
                                }
                            }
                        }
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        Messages.showErrorDialog(project, "Failed to load sprints: " + throwable.getMessage(), "Error");
                    });
                    return null;
                });
        
        // Epic and Assignee will be loaded when user clicks on them (on-demand loading)

        // Load issue types
        jiraService.getIssueTypesAsync()
            .thenAccept(issueTypes -> {
                SwingUtilities.invokeLater(() -> {
                    this.issueTypesMap = issueTypes; // Store the map
                    issueTypeComboBox.removeAllItems();
                    for (String issueTypeName : issueTypes.keySet()) {
                        issueTypeComboBox.addItem(issueTypeName);
                    }
                    
                    // Restore recent issue type selection
                    RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
                    String lastIssueType = recentSettings.getLastUsedIssueTypeName();
                    if (!lastIssueType.isEmpty() && issueTypes.containsKey(lastIssueType)) {
                        issueTypeComboBox.setSelectedItem(lastIssueType);
                    } else if (currentIssueType != null && issueTypes.containsKey(currentIssueType)) {
                        issueTypeComboBox.setSelectedItem(currentIssueType);
                    } else if (issueTypes.containsKey("Task")) {
                        issueTypeComboBox.setSelectedItem("Task");
                    }
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(project, "Failed to load issue types: " + throwable.getMessage(), "Error");
                    // Add default issue types as fallback
                    issueTypeComboBox.addItem("Task");
                    issueTypeComboBox.addItem("Bug");
                    issueTypeComboBox.addItem("Story");
                    
                    // Restore recent issue type selection for fallback types
                    RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
                    String lastIssueType = recentSettings.getLastUsedIssueTypeName();
                    if (!lastIssueType.isEmpty()) {
                        for (int i = 0; i < issueTypeComboBox.getItemCount(); i++) {
                            if (issueTypeComboBox.getItemAt(i).equals(lastIssueType)) {
                                issueTypeComboBox.setSelectedItem(lastIssueType);
                                break;
                            }
                        }
                    } else if (currentIssueType != null) {
                        issueTypeComboBox.setSelectedItem(currentIssueType);
                    } else {
                        issueTypeComboBox.setSelectedItem("Task");
                    }
                });
                return null;
            });
    }

    private void showAssigneeSearchDialog() {
        jiraService.getProjectUsersAsync(jiraService.getProjectKey())
            .thenAccept(users -> SwingUtilities.invokeLater(() -> {
                showAssigneeSearchPopup(users);
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(project, "Failed to load project users: " + throwable.getMessage(), "Error");
                });
                return null;
            });
    }

    private void showAssigneeSearchPopup(List<String> allUsers) {
        // Create dialog panel
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(300, 180));
        
        // Create search field
        JTextField searchField = new JTextField();
        searchField.setBorder(BorderFactory.createTitledBorder("담당자 검색"));
        dialogPanel.add(searchField, BorderLayout.NORTH);
        
        // Create user list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setVisibleRowCount(4);
        
        JScrollPane scrollPane = new JScrollPane(userList);
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Initialize the list
        updateAssigneeList(listModel, allUsers, "");
        
        // Add search functionality
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterAssignees();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterAssignees();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterAssignees();
            }
            
            private void filterAssignees() {
                String searchText = searchField.getText().toLowerCase().trim();
                updateAssigneeList(listModel, allUsers, searchText);
                if (listModel.getSize() > 0) {
                    userList.setSelectedIndex(0);
                }
            }
        });
        
        // Add selection handlers
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectAssignee(userList);
                }
            }
        });
        
        // Create and show popup
        JPopupMenu popup = new JPopupMenu();
        popup.add(dialogPanel);
        popup.show(assigneeDisplayLabel, 0, assigneeDisplayLabel.getHeight());
        
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }

    private void updateAssigneeList(DefaultListModel<String> listModel, List<String> allUsers, String searchText) {
        listModel.clear();
        
        // Add "unassigned" option
        if (searchText.isEmpty() || "할당되지 않음".toLowerCase().contains(searchText)) {
            listModel.addElement("할당되지 않음");
        }
        
        // Add filtered users
        for (String user : allUsers) {
            if (searchText.isEmpty() || user.toLowerCase().contains(searchText)) {
                listModel.addElement(user);
            }
        }
    }

    private void selectAssignee(JList<String> userList) {
        String selectedUser = userList.getSelectedValue();
        if (selectedUser == null) return;
        
        // Close popup
        Component comp = userList;
        while (comp != null && !(comp instanceof JPopupMenu)) {
            comp = comp.getParent();
        }
        if (comp instanceof JPopupMenu) {
            ((JPopupMenu) comp).setVisible(false);
        }
        
        // Update selection
        if ("할당되지 않음".equals(selectedUser)) {
            selectedAssignee = null;
            assigneeDisplayLabel.setText("할당되지 않음");
        } else {
            selectedAssignee = selectedUser;
            assigneeDisplayLabel.setText(selectedUser);
        }
    }

    private void showEpicSearchDialog() {
        jiraService.getEpicListAsync()
            .thenAccept(epics -> SwingUtilities.invokeLater(() -> {
                showEpicSearchPopup(epics);
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(project, "Failed to load epics: " + throwable.getMessage(), "Error");
                });
                return null;
            });
    }

    private void showEpicSearchPopup(List<JiraEpic> allEpics) {
        // Create dialog panel
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(400, 180));
        
        // Create search field
        JTextField searchField = new JTextField();
        searchField.setBorder(BorderFactory.createTitledBorder("상위항목 검색"));
        dialogPanel.add(searchField, BorderLayout.NORTH);
        
        // Create epic list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> epicList = new JList<>(listModel);
        epicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        epicList.setVisibleRowCount(4);
        
        JScrollPane scrollPane = new JScrollPane(epicList);
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Initialize the list
        updateEpicList(listModel, allEpics, "");
        
        // Add search functionality
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filterEpics();
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filterEpics();
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filterEpics();
            }
            
            private void filterEpics() {
                String searchText = searchField.getText().toLowerCase().trim();
                updateEpicList(listModel, allEpics, searchText);
                if (listModel.getSize() > 0) {
                    epicList.setSelectedIndex(0);
                }
            }
        });
        
        // Add selection handlers
        epicList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectEpic(epicList, allEpics);
                }
            }
        });
        
        // Create and show popup
        JPopupMenu popup = new JPopupMenu();
        popup.add(dialogPanel);
        popup.show(epicDisplayLabel, 0, epicDisplayLabel.getHeight());
        
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }

    private void updateEpicList(DefaultListModel<String> listModel, List<JiraEpic> allEpics, String searchText) {
        listModel.clear();
        
        // Add "no epic" option
        if (searchText.isEmpty() || "없음".toLowerCase().contains(searchText)) {
            listModel.addElement("없음");
        }
        
        // Add filtered epics
        for (JiraEpic epic : allEpics) {
            String displayText = epic.getKey() + " - " + epic.getSummary();
            if (searchText.isEmpty() || displayText.toLowerCase().contains(searchText)) {
                listModel.addElement(displayText);
            }
        }
    }

    private void selectEpic(JList<String> epicList, List<JiraEpic> allEpics) {
        String selectedText = epicList.getSelectedValue();
        if (selectedText == null) return;
        
        // Close popup
        Component comp = epicList;
        while (comp != null && !(comp instanceof JPopupMenu)) {
            comp = comp.getParent();
        }
        if (comp instanceof JPopupMenu) {
            ((JPopupMenu) comp).setVisible(false);
        }
        
        // Update selection
        if ("없음".equals(selectedText)) {
            selectedEpic = null;
            epicDisplayLabel.setText("없음");
        } else {
            // Find the epic by matching the display text
            for (JiraEpic epic : allEpics) {
                String displayText = epic.getKey() + " - " + epic.getSummary();
                if (displayText.equals(selectedText)) {
                    selectedEpic = epic;
                    epicDisplayLabel.setText(epic.getKey() + " - " + epic.getSummary());
                    break;
                }
            }
        }
    }

    private void loadCurrentUser() {
        // Check if there's a recent assignee selection
        RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
        String lastAssignee = recentSettings.getLastUsedAssignee();

        if (!lastAssignee.isEmpty()) {
            // Use recent assignee
            selectedAssignee = lastAssignee;
            assigneeDisplayLabel.setText(lastAssignee);
        } else {
            // Fall back to current user
            jiraService.getCurrentUserAsync()
                .thenAccept(currentUser -> {
                    SwingUtilities.invokeLater(() -> {
                        if (currentUser != null && currentUser.has("displayName")) {
                            String displayName = currentUser.get("displayName").getAsString();
                            selectedAssignee = displayName;
                            assigneeDisplayLabel.setText(displayName);
                        }
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        // Silently fail - user can still manually select assignee
                        System.err.println("Warning: Failed to load current user: " + throwable.getMessage());
                    });
                    return null;
                });
        }
    }

    private void loadRecentEpic() {
        // Check if there's a recent epic selection
        RecentJiraSettings recentSettings = RecentJiraSettings.getInstance();
        String lastEpicKey = recentSettings.getLastUsedEpicKey();
        String lastEpicName = recentSettings.getLastUsedEpicName();

        if (!lastEpicKey.isEmpty() && !lastEpicName.isEmpty()) {
            // Load all epics to verify the recent epic still exists
            jiraService.getEpicListAsync()
                .thenAccept(epics -> SwingUtilities.invokeLater(() -> {
                    // Find the epic with matching key
                    for (JiraEpic epic : epics) {
                        if (lastEpicKey.equals(epic.getKey())) {
                            selectedEpic = epic;
                            epicDisplayLabel.setText(epic.getKey() + " - " + epic.getSummary());
                            return;
                        }
                    }
                    // If epic not found, it might have been deleted or archived
                    // Keep the default "상위항목 선택" text
                }))
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        // Silently fail - user can still manually select epic
                        System.err.println("Warning: Failed to load recent epic: " + throwable.getMessage());
                    });
                    return null;
                });
        }
    }

    private void performAIRecommendation() {
        String currentSummary = summaryField.getText().trim();
        if (currentSummary.isEmpty()) {
            Messages.showWarningDialog(project, "AI 추천을 받으려면 먼저 이슈 제목을 입력하세요.", "입력 필요");
            summaryField.requestFocus();
            return;
        }

        // Disable button during processing
        aiRecommendEpicButton.setEnabled(false);
        aiRecommendEpicButton.setText("추천 중...");

        // Load epics and get AI recommendation
        jiraService.getEpicListAsync()
            .thenCompose(epics -> {
                if (epics.isEmpty()) {
                    throw new RuntimeException("Epic이 없습니다. AI 추천을 받으려면 최소 하나의 Epic이 필요합니다.");
                }
                return jiraService.getEpicRecommendationAsync(currentSummary, epics);
            })
            .thenAccept(response -> SwingUtilities.invokeLater(() -> {
                handleAIRecommendationResponse(response);
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    aiRecommendEpicButton.setEnabled(true);
                    aiRecommendEpicButton.setText("AI 추천");
                    
                    String errorMessage = "AI 추천 중 오류가 발생했습니다: " + throwable.getMessage();
                    Messages.showErrorDialog(project, errorMessage, "AI 추천 오류");
                });
                return null;
            });
    }

    private void handleAIRecommendationResponse(AIRecommendationResponse response) {
        // Re-enable button
        aiRecommendEpicButton.setEnabled(true);
        aiRecommendEpicButton.setText("AI 추천");

        if (response == null || !"success".equals(response.getStatus()) || 
            response.getAi_result() == null || 
            response.getAi_result().getEpics() == null || 
            response.getAi_result().getEpics().isEmpty()) {
            
            Messages.showInfoMessage(project, "AI가 추천할 만한 적절한 Epic을 찾지 못했습니다.", "추천 결과 없음");
            return;
        }

        // Get the first recommended epic
        AIRecommendationResponse.RecommendedEpic recommendedEpic = response.getAi_result().getEpics().get(0);
        String recommendedKey = recommendedEpic.getKey();

        // Load all epics to find the matching one
        jiraService.getEpicListAsync()
            .thenAccept(allEpics -> SwingUtilities.invokeLater(() -> {
                JiraEpic matchingEpic = null;
                for (JiraEpic epic : allEpics) {
                    if (recommendedKey.equals(epic.getKey())) {
                        matchingEpic = epic;
                        break;
                    }
                }

                if (matchingEpic != null) {
                    // Auto-select the recommended epic
                    selectedEpic = matchingEpic;
                    epicDisplayLabel.setText(matchingEpic.getKey() + " - " + matchingEpic.getSummary());
                    
                    // Show success message
                    Messages.showInfoMessage(project, 
                        "AI가 추천한 Epic이 자동 선택되었습니다:\n" + 
                        matchingEpic.getKey() + " - " + matchingEpic.getSummary(), 
                        "AI 추천 완료");
                } else {
                    Messages.showWarningDialog(project, 
                        "AI가 추천한 Epic(" + recommendedKey + ")을 찾을 수 없습니다.\n" + 
                        "수동으로 Epic을 선택해 주세요.", 
                        "Epic 찾기 실패");
                }
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(project, 
                        "Epic 목록을 다시 로드하는 중 오류가 발생했습니다: " + throwable.getMessage(), 
                        "오류");
                });
                return null;
            });
    }

    private void performAIWorkGeneration() {
        String summary = summaryField.getText().trim();
        if (summary.isEmpty()) {
            Messages.showWarningDialog(project, "AI 작업 생성을 위해 먼저 Summary를 입력하세요.", "입력 필요");
            summaryField.requestFocus();
            return;
        }

        // Disable button during processing
        aiGenerateButton.setEnabled(false);
        aiGenerateButton.setText("생성 중...");

        // Call AI service to generate work description based on summary
        jiraService.generateWorkDescriptionAsync(summary)
            .thenAccept(generatedDescription -> SwingUtilities.invokeLater(() -> {
                // Re-enable button
                aiGenerateButton.setEnabled(true);
                aiGenerateButton.setText("(AI) 내용 자동 생성");
                
                if (generatedDescription != null && !generatedDescription.trim().isEmpty()) {
                    // Append AI-generated content to the end of description
                    String currentDescription = descriptionArea.getText().trim();
                    if (currentDescription.isEmpty()) {
                        descriptionArea.setText(generatedDescription);
                    } else {
                        descriptionArea.setText(currentDescription + "\n\n" + generatedDescription);
                    }
                    // Show success message
                    Messages.showInfoMessage(project, "Summary를 기반으로 작업 내용이 자동 생성되어 Description에 추가되었습니다.", "AI 작업 생성 완료");
                } else {
                    Messages.showWarningDialog(project, "AI가 작업 내용을 생성하지 못했습니다. 다시 시도해 주세요.", "생성 실패");
                }
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    aiGenerateButton.setEnabled(true);
                    aiGenerateButton.setText("(AI) 내용 자동 생성");
                    
                    String errorMessage = "AI 작업 생성 중 오류가 발생했습니다: " + throwable.getMessage();
                    Messages.showErrorDialog(project, errorMessage, "AI 작업 생성 오류");
                });
                return null;
            });
    }
}
