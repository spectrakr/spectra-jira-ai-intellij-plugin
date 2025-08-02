package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

public class DescriptionInlineEditHandler implements InlineEditHandler {
    
    private final Project project;
    private final JTextArea descriptionField;
    private final JScrollPane descriptionScrollPane;
    private final JPanel descriptionButtonPanel;
    private final JButton saveButton;
    private final JButton cancelButton;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private boolean isEditing = false;
    private String originalValue = "";
    private Consumer<String> onStatusUpdate;
    
    public DescriptionInlineEditHandler(Project project, JTextArea descriptionField, 
                                     JScrollPane descriptionScrollPane, JPanel descriptionButtonPanel,
                                     JButton saveButton, JButton cancelButton, JiraService jiraService) {
        this.project = project;
        this.descriptionField = descriptionField;
        this.descriptionScrollPane = descriptionScrollPane;
        this.descriptionButtonPanel = descriptionButtonPanel;
        this.saveButton = saveButton;
        this.cancelButton = cancelButton;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        // Initially make it look like a label (non-editable)
        setDisplayMode();
        
        // Add mouse click listener to enter edit mode
        descriptionField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentIssue != null && descriptionField.isEnabled()) {
                    enterEditMode();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentIssue != null && descriptionField.isEnabled() && !isEditing) {
                    Color currentBg = UIManager.getColor("Panel.background");
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        descriptionField.setBackground(new Color(r, g, b));
                    } else {
                        descriptionField.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    descriptionField.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentIssue != null && descriptionField.isEnabled() && !isEditing) {
                    descriptionField.setBackground(UIManager.getColor("Panel.background"));
                    descriptionField.repaint();
                }
            }
        });
        
        // Add key listener for Ctrl+Enter and Escape key
        descriptionField.addKeyListener(new KeyListener() {
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
        descriptionField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Do not auto-save for description - user must use Save/Cancel buttons
            }
        });
        
        // Setup button actions
        saveButton.addActionListener(e -> saveDescriptionAndExit());
        cancelButton.addActionListener(e -> cancelDescriptionEditing());
    }
    
    @Override
    public void enterEditMode() {
        if (currentIssue == null) return;
        
        isEditing = true;
        originalValue = descriptionField.getText();
        
        // If showing placeholder text, clear it and use actual description from issue
        if ("설명 편집".equals(originalValue)) {
            String actualDescription = currentIssue.getDescription();
            originalValue = actualDescription != null ? actualDescription : "";
            descriptionField.setText(originalValue);
        }
        
        descriptionField.setForeground(UIManager.getColor("TextField.foreground"));
        descriptionField.setEditable(true);
        descriptionField.setFocusable(true);
        
        // Restore textarea appearance for editing
        descriptionField.setBackground(UIManager.getColor("TextArea.background"));
        descriptionField.setBorder(UIManager.getBorder("TextField.border"));
        descriptionField.setOpaque(true);
        descriptionField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        
        // Restore scroll pane border and appearance for editing
        if (descriptionScrollPane != null) {
            descriptionScrollPane.setBorder(UIManager.getBorder("ScrollPane.border"));
            descriptionScrollPane.setOpaque(true);
            descriptionScrollPane.getViewport().setOpaque(true);
        }
        
        // Show save/cancel buttons
        if (descriptionButtonPanel != null) {
            descriptionButtonPanel.setVisible(true);
            descriptionButtonPanel.revalidate();
            descriptionButtonPanel.repaint();
        }
    }
    
    @Override
    public void exitEditMode(boolean saveChanges) {
        if (!isEditing) return;
        
        if (saveChanges) {
            String newDescription = descriptionField.getText().trim();
            if (!newDescription.equals(originalValue)) {
                // Save the changes immediately
                saveDescriptionChange(newDescription);
            }
        } else {
            // Restore original value
            descriptionField.setText(originalValue);
        }
        
        setDisplayMode();
    }
    
    @Override
    public void setDisplayMode() {
        isEditing = false;
        descriptionField.setEditable(false);
        descriptionField.setFocusable(true); // Keep focusable to show cursor on hover
        
        // Make it look like plain text instead of a textarea
        descriptionField.setBackground(UIManager.getColor("Panel.background"));
        descriptionField.setBorder(null);
        descriptionField.setOpaque(false);
        descriptionField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Set default text color to match other text fields (unless it's placeholder text which should stay gray)
        if (!"설명 편집".equals(descriptionField.getText())) {
            descriptionField.setForeground(UIManager.getColor("TextField.foreground"));
        }
        
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
    
    @Override
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        if (issue != null) {
            String description = issue.getDescription();
            if (description == null || description.trim().isEmpty()) {
                descriptionField.setText("설명 편집");
                descriptionField.setForeground(Color.GRAY);
            } else {
                descriptionField.setText(description);
                descriptionField.setForeground(UIManager.getColor("TextField.foreground"));
            }
        }
    }
    
    @Override
    public void setEnabled(boolean enabled) {
        descriptionField.setEnabled(enabled);
        if (enabled) {
            setDisplayMode();
        }
    }
    
    private void saveDescriptionAndExit() {
        if (!isEditing) return;
        
        String newDescription = descriptionField.getText().trim();
        if (!newDescription.equals(originalValue)) {
            // Save the changes immediately
            saveDescriptionChange(newDescription);
        } else {
            // No changes, just exit edit mode
            setDisplayMode();
        }
    }
    
    private void cancelDescriptionEditing() {
        if (!isEditing) return;
        
        // Restore original value or placeholder
        if (originalValue == null || originalValue.trim().isEmpty()) {
            descriptionField.setText("설명 편집");
            descriptionField.setForeground(Color.GRAY);
        } else {
            descriptionField.setText(originalValue);
            descriptionField.setForeground(UIManager.getColor("TextField.foreground"));
        }
        setDisplayMode();
    }
    
    private void saveDescriptionChange(String newDescription) {
        if (currentIssue == null) return;
        
        String oldDescription = currentIssue.getDescription();
        currentIssue.setDescription(newDescription);
        
        updateStatus("Saving description change...");
        
        jiraService.updateIssueDescriptionAsync(currentIssue.getKey(), newDescription)
            .thenRun(() -> {
                SwingUtilities.invokeLater(() -> {
                    // Exit edit mode
                    setDisplayMode();
                    updateStatus("Description updated successfully for " + currentIssue.getKey());
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentIssue.setDescription(oldDescription);
                    if (oldDescription == null || oldDescription.trim().isEmpty()) {
                        descriptionField.setText("설명 편집");
                        descriptionField.setForeground(Color.GRAY);
                    } else {
                        descriptionField.setText(oldDescription);
                        descriptionField.setForeground(UIManager.getColor("TextField.foreground"));
                    }
                    updateStatus("Error updating description: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update description: " + throwable.getMessage(), "Update Error");
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