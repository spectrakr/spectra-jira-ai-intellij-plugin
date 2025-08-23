package com.spectra.intellij.ai.toolwindow.handlers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;
import com.spectra.intellij.ai.settings.JiraSettings;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class EpicSelectionHandler {
    
    private final Project project;
    private final JLabel epicLabel;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private Consumer<String> onStatusUpdate;
    
    public EpicSelectionHandler(Project project, JLabel epicLabel, JiraService jiraService) {
        this.project = project;
        this.epicLabel = epicLabel;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        epicLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentIssue != null && epicLabel.isEnabled()) {
                    showEpicSelectionPopup();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentIssue != null && epicLabel.isEnabled()) {
                    Color currentBg = epicLabel.getBackground();
                    if (currentBg != null) {
                        // Make it slightly brighter by adding 15 to each RGB component
                        int r = Math.min(255, currentBg.getRed() + 15);
                        int g = Math.min(255, currentBg.getGreen() + 15);
                        int b = Math.min(255, currentBg.getBlue() + 15);
                        epicLabel.setBackground(new Color(r, g, b));
                    } else {
                        epicLabel.setBackground(new Color(245, 245, 245)); // Fallback
                    }
                    epicLabel.repaint();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (currentIssue != null && epicLabel.isEnabled()) {
                    // Restore original epic color or default
                    if (currentIssue.getEpicColor() != null) {
                        try {
                            Color epicColor = Color.decode(currentIssue.getEpicColor());
                            epicLabel.setBackground(epicColor);
                        } catch (NumberFormatException ex) {
                            epicLabel.setBackground(UIManager.getColor("Label.background"));
                        }
                    } else if (currentIssue.getParentKey() != null) {
                        epicLabel.setBackground(new Color(230, 230, 250)); // Light lavender for epics
                    } else {
                        epicLabel.setBackground(UIManager.getColor("Label.background"));
                    }
                    epicLabel.repaint();
                }
            }
        });
    }
    
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        updateEpicLabelDisplay();
    }
    
    private void showEpicSelectionPopup() {
        updateStatus("Loading epics...");
        
        // Get board ID from currently selected sprint
        String boardId = getCurrentBoardId();
        
        if (boardId == null) {
            updateStatus("No sprint selected or no board information available.");
            return;
        }
        
        // Get epics from the board using Agile API
        jiraService.getEpicsAsync(boardId)
            .thenAccept(epics -> SwingUtilities.invokeLater(() -> {
                showEpicSearchDialog(epics);
                updateStatus("Ready");
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Error loading epics: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    private String getCurrentBoardId() {
        // Try to get boardId from the currently selected sprint via the tool window content
        // This is a simple approach - in a more robust implementation, we might want to 
        // track the current sprint selection more explicitly
        
        // For now, use fallback to the default boardId from settings if available
        JiraSettings settings = JiraSettings.getInstance();
        String boardId = settings.getDefaultBoardId();
        
        if (boardId != null && !boardId.trim().isEmpty()) {
            return boardId;
        }
        
        // If no boardId available, return null to indicate we need sprint selection
        return null;
    }
    
    private void showEpicSearchDialog(List<JiraIssue> allEpics) {
        // Create dialog panel
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(400, 200));
        
        // Create search field
        JTextField searchField = new JTextField();
        searchField.setBorder(BorderFactory.createTitledBorder("상위항목 검색"));
        dialogPanel.add(searchField, BorderLayout.NORTH);
        
        // Create epic list with limited visible rows
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> epicList = new JList<>(listModel);
        epicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        epicList.setVisibleRowCount(6); // Limit to 6 visible rows
        
        // Enable hover selection
        epicList.putClientProperty("List.mouseHoverSelection", true);
        epicList.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
        epicList.setSelectionForeground(UIManager.getColor("List.selectionForeground"));
        
        // Custom renderer for epic display
        epicList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, 
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                String displayText = (String) value;
                
                // Set colors for better visual feedback
                if (isSelected) {
                    setBackground(UIManager.getColor("List.selectionBackground"));
                    setForeground(UIManager.getColor("List.selectionForeground"));
                } else {
                    setBackground(UIManager.getColor("List.background"));
                    setForeground(UIManager.getColor("List.foreground"));
                }
                
                // Special formatting for "None" option
                if (displayText.equals("없음")) {
                    setFont(getFont().deriveFont(Font.ITALIC));
                    if (!isSelected) {
                        setForeground(new Color(100, 100, 100)); // Gray for "none"
                    }
                } else {
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                
                // Add padding for better spacing
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                
                return this;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(epicList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Enable mouse wheel scrolling
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setBlockIncrement(64);
        scrollPane.setWheelScrollingEnabled(true);
        
        // Add mouse wheel listener to ensure scrolling works in popup
        scrollPane.addMouseWheelListener(e -> {
            if (scrollPane.getVerticalScrollBar().isVisible()) {
                JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
                int direction = e.getWheelRotation();
                int increment = scrollBar.getUnitIncrement(direction);
                int newValue = scrollBar.getValue() + (direction * increment * 3); // Multiply by 3 for faster scrolling
                scrollBar.setValue(Math.max(0, Math.min(newValue, scrollBar.getMaximum() - scrollBar.getVisibleAmount())));
            }
        });
        
        dialogPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Initialize the list with all options
        updateEpicList(listModel, allEpics, "");
        
        // Set initial selection to first item if available
        if (listModel.getSize() > 0) {
            epicList.setSelectedIndex(0);
            epicList.ensureIndexIsVisible(0);
        }
        
        // Add search functionality
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterEpics();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterEpics();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterEpics();
            }
            
            private void filterEpics() {
                String searchText = searchField.getText().toLowerCase().trim();
                updateEpicList(listModel, allEpics, searchText);
                // Auto-select first item if list is not empty
                if (listModel.getSize() > 0) {
                    epicList.setSelectedIndex(0);
                }
            }
        });
        
        // Add keyboard navigation support
        searchField.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (listModel.getSize() > 0) {
                        int selectedIndex = epicList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            epicList.setSelectedIndex(0);
                        }
                        selectEpic(epicList, allEpics);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (listModel.getSize() > 0) {
                        epicList.requestFocus();
                        int selectedIndex = epicList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            epicList.setSelectedIndex(0);
                        } else {
                            int nextIndex = Math.min(selectedIndex + 1, listModel.getSize() - 1);
                            epicList.setSelectedIndex(nextIndex);
                        }
                        epicList.ensureIndexIsVisible(epicList.getSelectedIndex());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (listModel.getSize() > 0) {
                        epicList.requestFocus();
                        int selectedIndex = epicList.getSelectedIndex();
                        if (selectedIndex == -1) {
                            epicList.setSelectedIndex(listModel.getSize() - 1);
                        } else {
                            int prevIndex = Math.max(selectedIndex - 1, 0);
                            epicList.setSelectedIndex(prevIndex);
                        }
                        epicList.ensureIndexIsVisible(epicList.getSelectedIndex());
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
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
        
        // Add double-click support
        epicList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectEpic(epicList, allEpics);
                }
            }
        });
        
        epicList.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectEpic(epicList, allEpics);
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    Component comp = epicList;
                    while (comp != null && !(comp instanceof JPopupMenu)) {
                        comp = comp.getParent();
                    }
                    if (comp instanceof JPopupMenu) {
                        ((JPopupMenu) comp).setVisible(false);
                    }
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {
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
        popup.show(epicLabel, 0, epicLabel.getHeight());
        
        // Focus on search field
        SwingUtilities.invokeLater(() -> searchField.requestFocus());
    }
    
    private void updateEpicList(DefaultListModel<String> listModel, List<JiraIssue> allEpics, String searchText) {
        listModel.clear();
        
        // Add "None" option at the top
        if (searchText.isEmpty() || "없음".toLowerCase().contains(searchText) || 
            "none".toLowerCase().contains(searchText)) {
            listModel.addElement("없음");
        }
        
        // Add filtered epics
        for (JiraIssue epic : allEpics) {
            String epicDisplay = epic.getKey() + " " + epic.getSummary();
            if (searchText.isEmpty() || 
                epic.getKey().toLowerCase().contains(searchText) ||
                epic.getSummary().toLowerCase().contains(searchText)) {
                listModel.addElement(epicDisplay);
            }
        }
    }
    
    private void selectEpic(JList<String> epicList, List<JiraIssue> allEpics) {
        String selectedEpic = epicList.getSelectedValue();
        if (selectedEpic == null) return;
        
        // Close the popup
        Component comp = epicList;
        while (comp != null && !(comp instanceof JPopupMenu)) {
            comp = comp.getParent();
        }
        if (comp instanceof JPopupMenu) {
            ((JPopupMenu) comp).setVisible(false);
        }
        
        // Handle selection
        if (selectedEpic.equals("없음")) {
            // Remove parent/epic
            updateParent(null);
        } else {
            // Extract epic key from display string (format: "EPIC-123 Epic Summary")
            String epicKey = selectedEpic.split(" ")[0];
            updateParent(epicKey);
        }
    }
    
    private void updateParent(String parentKey) {
        if (currentIssue == null) return;
        
        String oldParentKey = currentIssue.getParentKey();
        String oldParentSummary = currentIssue.getParentSummary();
        String oldEpicColor = currentIssue.getEpicColor();
        
        if (Objects.equals(oldParentKey, parentKey)) {
            updateStatus("No changes to parent");
            return;
        }
        
        updateStatus("Updating parent...");
        
        jiraService.updateIssueParentAsync(currentIssue.getKey(), parentKey)
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                // Update the current issue model
                currentIssue.setParentKey(parentKey);
                
                if (parentKey != null && !parentKey.isEmpty()) {
                    // Fetch the new epic details to get summary and color
                    jiraService.getIssueAsync(parentKey)
                        .thenAccept(parentIssue -> SwingUtilities.invokeLater(() -> {
                            currentIssue.setParentSummary(parentIssue.getSummary());
                            currentIssue.setEpicColor(parentIssue.getEpicColor());
                            updateEpicLabelDisplay();
                            updateStatus("Parent updated successfully for " + currentIssue.getKey());
                        }))
                        .exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> {
                                // Even if we can't get epic details, still update the key
                                currentIssue.setParentSummary(null);
                                currentIssue.setEpicColor(null);
                                updateEpicLabelDisplay();
                                updateStatus("Parent updated (details unavailable) for " + currentIssue.getKey());
                            });
                            return null;
                        });
                } else {
                    // No parent - clear epic info
                    currentIssue.setParentSummary(null);
                    currentIssue.setEpicColor(null);
                    updateEpicLabelDisplay();
                    updateStatus("Parent removed successfully for " + currentIssue.getKey());
                }
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old values on error
                    currentIssue.setParentKey(oldParentKey);
                    currentIssue.setParentSummary(oldParentSummary);
                    currentIssue.setEpicColor(oldEpicColor);
                    updateEpicLabelDisplay();
                    updateStatus("Error updating parent: " + throwable.getMessage());
                    Messages.showErrorDialog(project, "Failed to update parent: " + throwable.getMessage(), "Update Error");
                });
                return null;
            });
    }
    
    private void updateEpicLabelDisplay() {
        if (currentIssue == null) return;
        
        // Update Epic information display
        if (currentIssue.getParentKey() != null && currentIssue.getParentSummary() != null) {
            String epicText = currentIssue.getParentKey() + " " + currentIssue.getParentSummary();
            epicLabel.setText(epicText);
            
            // Apply colored background if Epic color is available
            if (currentIssue.getEpicColor() != null) {
                try {
                    Color epicColor = Color.decode(currentIssue.getEpicColor());
                    epicLabel.setBackground(epicColor);
                    // Set text color based on background brightness
                    int brightness = (int) (0.299 * epicColor.getRed() + 0.587 * epicColor.getGreen() + 0.114 * epicColor.getBlue());
                    epicLabel.setForeground(brightness > 128 ? Color.BLACK : Color.WHITE);
                } catch (NumberFormatException e) {
                    // If color parsing fails, use default background
                    epicLabel.setBackground(UIManager.getColor("Label.background"));
                    epicLabel.setForeground(UIManager.getColor("Label.foreground"));
                }
            } else {
                // Use default Epic color
                epicLabel.setBackground(new Color(230, 230, 250)); // Light lavender
                epicLabel.setForeground(Color.BLACK);
            }
        } else {
            epicLabel.setText("없음");
            epicLabel.setBackground(UIManager.getColor("Label.background"));
            epicLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
        
        // Force repaint to ensure changes are visible
        epicLabel.revalidate();
        epicLabel.repaint();
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