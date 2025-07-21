package com.spectra.intellij.ai.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class CreateIssueDialog extends DialogWrapper {
    
    private JTextField summaryField;
    private JTextArea descriptionArea;
    private JComboBox<String> priorityComboBox;
    private JComboBox<String> issueTypeComboBox;
    private JComboBox<JiraSprint> sprintComboBox;
    private JiraIssue issue;
    private final JiraService jiraService;
    private final Project project;
    private Map<String, String> issueTypesMap; // name -> id mapping
    
    // Static variables to persist selections across dialog instances
    private static JiraSprint lastSelectedSprint;
    private static String lastSelectedIssueType;
    
    public CreateIssueDialog(Project project, JiraService jiraService) {
        super(project);
        this.project = project;
        this.jiraService = jiraService;
        setTitle("Create Jira Issue");
        init();
        loadData();
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
        panel.add(new JScrollPane(descriptionArea), gbc);
        
        // Priority
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Priority:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        priorityComboBox = new JComboBox<>(new String[]{"High", "Medium", "Low", "Highest", "Lowest"});
        priorityComboBox.setSelectedItem("Medium");
        panel.add(priorityComboBox, gbc);
        
        // Sprint
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
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
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Issue Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        issueTypeComboBox = new JComboBox<>();
        panel.add(issueTypeComboBox, gbc);
        
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
                // Save for next time
                lastSelectedSprint = selectedSprint;
            }
            
            // Save selected issue type for next time
            lastSelectedIssueType = (String) issueTypeComboBox.getSelectedItem();
            
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
        JiraSettings settings = JiraSettings.getInstance();
        if (settings.getDefaultBoardId() != null && !settings.getDefaultBoardId().trim().isEmpty()) {
            jiraService.getSprintsAsync(settings.getDefaultBoardId())
                .thenAccept(sprints -> {
                    SwingUtilities.invokeLater(() -> {
                        sprintComboBox.removeAllItems();
                        
                        // Filter to show only non-closed sprints
                        for (JiraSprint sprint : sprints) {
                            if (!"closed".equalsIgnoreCase(sprint.getState())) {
                                sprintComboBox.addItem(sprint);
                            }
                        }
                        
                        // Restore previous selection
                        if (lastSelectedSprint != null) {
                            for (int i = 0; i < sprintComboBox.getItemCount(); i++) {
                                JiraSprint item = sprintComboBox.getItemAt(i);
                                if (item.getId().equals(lastSelectedSprint.getId())) {
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
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        Messages.showErrorDialog(project, "Failed to load sprints: " + throwable.getMessage(), "Error");
                    });
                    return null;
                });
        }
        
        // Load issue types
        jiraService.getIssueTypesAsync()
            .thenAccept(issueTypes -> {
                SwingUtilities.invokeLater(() -> {
                    this.issueTypesMap = issueTypes; // Store the map
                    issueTypeComboBox.removeAllItems();
                    for (String issueTypeName : issueTypes.keySet()) {
                        issueTypeComboBox.addItem(issueTypeName);
                    }
                    
                    // Restore previous selection
                    if (lastSelectedIssueType != null && issueTypes.containsKey(lastSelectedIssueType)) {
                        issueTypeComboBox.setSelectedItem(lastSelectedIssueType);
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
                    
                    // Restore previous selection for fallback types
                    if (lastSelectedIssueType != null) {
                        issueTypeComboBox.setSelectedItem(lastSelectedIssueType);
                    } else if (currentIssueType != null) {
                        issueTypeComboBox.setSelectedItem(currentIssueType);
                    } else {
                        issueTypeComboBox.setSelectedItem("Task");
                    }
                });
                return null;
            });
    }
}