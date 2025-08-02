package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class SummaryInlineEditHandler implements InlineEditHandler {
    
    private final Project project;
    private final JTextField summaryField;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private boolean isEditing = false;
    private String originalValue = "";
    private Consumer<String> onStatusUpdate;
    
    public SummaryInlineEditHandler(Project project, JTextField summaryField, JiraService jiraService) {
        this.project = project;
        this.summaryField = summaryField;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Initially make it look like a label (non-editable)
        setDisplayMode();
        
        // Add mouse click listener to enter edit mode
        summaryField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentIssue != null && summaryField.isEnabled()) {
                    enterEditMode();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentIssue != null && summaryField.isEnabled() && !isEditing) {
                    Color currentBg = UIManager.getColor("Panel.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        summaryField.setBackground(new Color(r, g, b));
                    } else {
                        summaryField.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    summaryField.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentIssue != null && summaryField.isEnabled() && !isEditing) {
                    summaryField.setBackground(UIManager.getColor("Panel.background"));
                    summaryField.repaint();
                }
            }
        });
        
        // Add key listener for Enter key and Escape key
        summaryField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    exitEditMode(true); // Save changes
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exitEditMode(false); // Cancel changes
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add focus listener to save changes when focus is lost
        summaryField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (isEditing) {
                    exitEditMode(true); // Save changes when focus is lost
                }
            }
        });
    }
    
    @Override
    public void enterEditMode() {
        if (currentIssue == null) return;
        
        isEditing = true;
        originalValue = summaryField.getText();
        summaryField.setEditable(true);
        summaryField.setFocusable(true); // Re-enable focus for editing
        summaryField.setBorder(UIManager.getBorder("TextField.border"));
        summaryField.setBackground(UIManager.getColor("TextField.background"));
        summaryField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        summaryField.requestFocus();
        summaryField.setCaretPosition(summaryField.getText().length());
    }
    
    @Override
    public void exitEditMode(boolean saveChanges) {
        if (!isEditing) return;
        
        if (saveChanges) {
            String newSummary = summaryField.getText().trim();
            if (!newSummary.isEmpty() && !newSummary.equals(originalValue)) {
                // Save the changes immediately
                saveSummaryChange(newSummary);
            }
        } else {
            // Restore original value
            summaryField.setText(originalValue);
        }
        
        setDisplayMode();
    }
    
    @Override
    public void setDisplayMode() {
        isEditing = false;
        summaryField.setEditable(false);
        summaryField.setFocusable(false); // Prevent focus
        summaryField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        summaryField.setBackground(UIManager.getColor("Panel.background"));
        summaryField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    
    @Override
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        if (issue != null) {
            summaryField.setText(issue.getSummary() != null ? issue.getSummary() : "");
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        summaryField.setEnabled(enabled);
        if (enabled) {
            setDisplayMode();
        }
    }
    
    private void saveSummaryChange(String newSummary) {
        if (currentIssue == null) return;
        
        String oldSummary = currentIssue.getSummary();
        currentIssue.setSummary(newSummary);
        
        updateStatus("Saving summary change...");
        
        jiraService.updateIssueSummaryAsync(currentIssue.getKey(), newSummary)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Summary updated successfully for " + currentIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentIssue.setSummary(oldSummary);
                    summaryField.setText(oldSummary);
                    updateStatus("Error updating summary: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update summary: " + throwable.getMessage(), "Update Error");
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