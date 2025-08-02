package com.spectra.intellij.ai.toolwindow.handlers;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class AssigneeSelectionHandler {
    
    private final Project project;
    private final JLabel assigneeLabel;
    private final JiraService jiraService;
    private JiraIssue currentIssue;
    private JsonObject currentUser;
    private Consumer<String> onStatusUpdate;
    
    public AssigneeSelectionHandler(Project project, JLabel assigneeLabel, JiraService jiraService) {
        this.project = project;
        this.assigneeLabel = assigneeLabel;
        this.jiraService = jiraService;
        setupEventHandlers();
    }
    
    private void setupEventHandlers() {
        assigneeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentIssue != null && assigneeLabel.isEnabled()) {
                    showAssigneeSelectionPopup();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentIssue != null && assigneeLabel.isEnabled()) {
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
                if (currentIssue != null && assigneeLabel.isEnabled()) {
                    assigneeLabel.setBackground(UIManager.getColor("Label.background"));
                    assigneeLabel.repaint();
                }
            }
        });
    }
    
    public void setCurrentIssue(JiraIssue issue) {
        this.currentIssue = issue;
        if (issue != null) {
            if (issue.getAssignee() != null && !issue.getAssignee().trim().isEmpty()) {
                assigneeLabel.setText(issue.getAssignee());
            } else {
                assigneeLabel.setText("할당되지 않음");
            }
        }
    }
    
    public void setCurrentUser(JsonObject currentUser) {
        this.currentUser = currentUser;
    }
    
    private void showAssigneeSelectionPopup() {
        updateStatus("Loading assignable users...");
        
        jiraService.getProjectUsersAsync(jiraService.getProjectKey())
            .thenAccept(users -> SwingUtilities.invokeLater(() -> {
                showAssigneeSearchDialog(users);
                updateStatus("Ready");
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = throwable.getMessage();
                    if (throwable.getCause() != null) {
                        errorMessage = throwable.getCause().getMessage();
                    }
                    updateStatus("Error loading users: " + errorMessage);
                    Messages.showErrorDialog(project, "Failed to fetch project users. " + errorMessage, "Fetch Users Error");
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
        if (currentIssue == null) return;
        
        String oldAssignee = currentIssue.getAssignee();
        String newAssignee = displayName != null ? displayName : null;
        
        if ((oldAssignee == null && newAssignee == null) || 
            (oldAssignee != null && oldAssignee.equals(newAssignee))) {
            updateStatus("No changes to assignee");
            return;
        }
        
        updateStatus("Updating assignee...");
        currentIssue.setAssignee(newAssignee);
        
        jiraService.updateIssueAssigneeAsync(currentIssue.getKey(), accountId)
            .thenRun(() -> SwingUtilities.invokeLater(() -> {
                // Update UI
                if (displayName != null) {
                    assigneeLabel.setText(displayName);
                } else {
                    assigneeLabel.setText("할당되지 않음");
                }
                
                updateStatus("Assignee updated successfully for " + currentIssue.getKey());
            }))
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    // Restore old value on error
                    currentIssue.setAssignee(oldAssignee);
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
    
    private void updateStatus(String status) {
        if (onStatusUpdate != null) {
            onStatusUpdate.accept(status);
        }
    }
    
    public void setOnStatusUpdate(Consumer<String> onStatusUpdate) {
        this.onStatusUpdate = onStatusUpdate;
    }
}