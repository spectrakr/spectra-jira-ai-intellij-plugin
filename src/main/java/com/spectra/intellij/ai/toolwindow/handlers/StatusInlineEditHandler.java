package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class StatusInlineEditHandler implements InlineEditHandler {
    
    private final Project project;
    private final JComboBox<String> statusComboBox;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private boolean isEditing = false;
    private String originalValue = "";
    private Consumer<String> onStatusUpdate;
    
    public StatusInlineEditHandler(Project project, JComboBox<String> statusComboBox, JiraService jiraService) {
        this.project = project;
        this.statusComboBox = statusComboBox;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Status combo box doesn't need display mode like text fields
        // Just add action listener to handle immediate changes
        statusComboBox.addActionListener(e -> {
            if (currentIssue != null && !isEditing) {
                String newStatus = (String) statusComboBox.getSelectedItem();
                if (newStatus != null && !newStatus.equals(originalValue)) {
                    saveStatusChange(newStatus);
                }
            }
        });
    }
    
    @Override
    public void enterEditMode() {
        // ComboBox doesn't need explicit edit mode
    }
    
    @Override
    public void exitEditMode(boolean saveChanges) {
        // ComboBox doesn't need explicit edit mode
    }
    
    @Override
    public void setDisplayMode() {
        // ComboBox doesn't need display mode
    }
    
    @Override
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        if (issue != null) {
            originalValue = issue.getStatus() != null ? issue.getStatus() : "";
            if (originalValue != null && !originalValue.isEmpty()) {
                statusComboBox.setSelectedItem(originalValue);
            }
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        statusComboBox.setEnabled(enabled);
    }
    
    public void updateStatusOptions(java.util.List<String> statuses) {
        statusComboBox.removeAllItems();
        for (String status : statuses) {
            statusComboBox.addItem(status);
        }
        
        // Set current status if available
        if (currentIssue != null && currentIssue.getStatus() != null) {
            statusComboBox.setSelectedItem(currentIssue.getStatus());
        }
    }
    
    public void addFallbackStatusOptions() {
        statusComboBox.removeAllItems();
        statusComboBox.addItem("To Do");
        statusComboBox.addItem("In Progress");
        statusComboBox.addItem("Done");
        if (currentIssue != null && currentIssue.getStatus() != null) {
            statusComboBox.setSelectedItem(currentIssue.getStatus());
        }
    }
    
    private void saveStatusChange(String newStatus) {
        if (currentIssue == null) return;
        
        isEditing = true; // Prevent recursive calls
        String oldStatus = currentIssue.getStatus();
        currentIssue.setStatus(newStatus);
        
        updateStatus("Saving status change...");
        
        jiraService.updateIssueStatusAsync(currentIssue.getKey(), newStatus)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    isEditing = false;
                    originalValue = newStatus; // Update original value
                    updateStatus("Status updated successfully for " + currentIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    isEditing = false;
                    // Restore old value on error
                    currentIssue.setStatus(oldStatus);
                    statusComboBox.setSelectedItem(oldStatus);
                    updateStatus("Error updating status: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update status: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    private void updateStatus(String status) {
        if (onStatusUpdate != null) {
            onStatusUpdate.accept(status);
        }
    }
    
    public void setOnStatusUpdate(Consumer<String> onStatusUpdate) {
        this.onStatusUpdate = onStatusUpdate;
    }
}