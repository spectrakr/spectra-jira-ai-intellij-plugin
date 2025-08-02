package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class StoryPointsInlineEditHandler implements InlineEditHandler {
    
    private final Project project;
    private final JTextField storyPointsField;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private boolean isEditing = false;
    private String originalValue = "";
    private Consumer<String> onStatusUpdate;
    
    public StoryPointsInlineEditHandler(Project project, JTextField storyPointsField, JiraService jiraService) {
        this.project = project;
        this.storyPointsField = storyPointsField;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Initially make it look like a label (non-editable)
        setDisplayMode();
        
        // Add mouse click listener to enter edit mode
        storyPointsField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentIssue != null && storyPointsField.isEnabled()) {
                    enterEditMode();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentIssue != null && storyPointsField.isEnabled() && !isEditing) {
                    Color currentBg = UIManager.getColor("Panel.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        storyPointsField.setBackground(new Color(r, g, b));
                    } else {
                        storyPointsField.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    storyPointsField.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentIssue != null && storyPointsField.isEnabled() && !isEditing) {
                    storyPointsField.setBackground(UIManager.getColor("Panel.background"));
                    storyPointsField.repaint();
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
                    exitEditMode(true); // Save changes
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    exitEditMode(false); // Cancel changes
                }
            }
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Add focus listener to save changes when focus is lost
        storyPointsField.addFocusListener(new FocusAdapter() {
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
        originalValue = storyPointsField.getText();
        storyPointsField.setEditable(true);
        storyPointsField.setBorder(UIManager.getBorder("TextField.border"));
        storyPointsField.setBackground(UIManager.getColor("TextField.background"));
        storyPointsField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        storyPointsField.requestFocus();
        storyPointsField.selectAll();
    }
    
    @Override
    public void exitEditMode(boolean saveChanges) {
        if (!isEditing) return;
        
        if (saveChanges) {
            String newStoryPoints = storyPointsField.getText().trim();
            if (!newStoryPoints.equals(originalValue)) {
                // Validate story points
                try {
                    if (!newStoryPoints.isEmpty()) {
                        Double.parseDouble(newStoryPoints);
                    }
                    // Save the changes immediately
                    saveStoryPointsChange(newStoryPoints);
                } catch (NumberFormatException e) {
                    Messages.showErrorDialog(project, "Story points must be a valid number (e.g., 0.5, 1, 2)", "Validation Error");
                    storyPointsField.setText(originalValue);
                }
            }
        } else {
            // Restore original value
            storyPointsField.setText(originalValue);
        }
        
        setDisplayMode();
    }
    
    @Override
    public void setDisplayMode() {
        isEditing = false;
        storyPointsField.setEditable(false);
        storyPointsField.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        storyPointsField.setBackground(UIManager.getColor("Panel.background"));
        storyPointsField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    
    @Override
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        if (issue != null) {
            storyPointsField.setText(issue.getStoryPoints() != null ? issue.getStoryPoints().toString() : "");
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        storyPointsField.setEnabled(enabled);
        if (enabled) {
            setDisplayMode();
        }
    }
    
    private void saveStoryPointsChange(String newStoryPoints) {
        if (currentIssue == null) return;
        
        Double oldStoryPoints = currentIssue.getStoryPoints();
        Double newStoryPointsValue = newStoryPoints.isEmpty() ? null : Double.parseDouble(newStoryPoints);
        currentIssue.setStoryPoints(newStoryPointsValue);
        
        updateStatus("Saving story points change...");
        
        jiraService.updateIssueStoryPointsAsync(currentIssue.getKey(), newStoryPointsValue)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Story points updated successfully for " + currentIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentIssue.setStoryPoints(oldStoryPoints);
                    storyPointsField.setText(oldStoryPoints != null ? oldStoryPoints.toString() : "");
                    updateStatus("Error updating story points: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update story points: " + throwable.getMessage(), "Update Error");
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