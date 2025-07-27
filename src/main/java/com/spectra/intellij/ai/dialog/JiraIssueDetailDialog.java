package com.spectra.intellij.ai.dialog;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.service.JiraService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JiraIssueDetailDialog extends DialogWrapper {
    
    private InlineEditableField summaryField;
    private InlineEditableTextArea descriptionArea;
    private JComboBox<String> statusComboBox;
    private JComboBox<String> assigneeComboBox;
    private JTextField assigneeSearchField;
    private JList<String> assigneeSearchResults;
    private JPanel assigneeSearchPanel;
    private JiraIssue issue;
    private final JiraService jiraService;
    private final Project project;
    private boolean isModified = false;
    private JsonObject currentUser;
    private String selectedAssigneeAccountId;
    private String selectedAssigneeDisplayName;
    
    public JiraIssueDetailDialog(Project project, JiraService jiraService, JiraIssue issue) {
        super(project);
        this.project = project;
        this.jiraService = jiraService;
        this.issue = issue;
        System.out.println("___ JiraIssueDetailDialog constructor started for issue: " + issue.getKey());
        setTitle("Edit Issue: " + issue.getKey());
        System.out.println("___ JiraIssueDetailDialog calling init()");
        init();
        System.out.println("___ JiraIssueDetailDialog init() completed");
        loadData();
        System.out.println("___ JiraIssueDetailDialog constructor completed");
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        System.out.println("___ JiraIssueDetailDialog createCenterPanel started");
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        System.out.println("___ JiraIssueDetailDialog main panel created: " + panel);
        System.out.println("___ JiraIssueDetailDialog panel layout manager: " + panel.getLayout());
        
        // Issue Key (read-only)
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Issue Key:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField keyField = new JTextField(issue.getKey());
        keyField.setEditable(false);
        keyField.setBackground(panel.getBackground());
        panel.add(keyField, gbc);
        
        // Summary
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Summary:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        System.out.println("___ Creating InlineEditableField for summary with text: '" + (issue.getSummary() != null ? issue.getSummary() : "") + "'");
        summaryField = new InlineEditableField(issue.getSummary() != null ? issue.getSummary() : "");
        System.out.println("___ InlineEditableField created: " + summaryField);
        System.out.println("___ Adding summaryField to panel with constraints: " + gbc);
        panel.add(summaryField, gbc);
        System.out.println("___ summaryField added to panel successfully");
        
        // Issue Status
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        statusComboBox = new JComboBox<>();
        panel.add(statusComboBox, gbc);
        
        // Assignee
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Assignee:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        
        // Create assignee selection panel
        JPanel assigneePanel = createAssigneeSelectionPanel();
        panel.add(assigneePanel, gbc);
        
        // Description
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        descriptionArea = new InlineEditableTextArea(issue.getDescription() != null ? issue.getDescription() : "");
        panel.add(descriptionArea, gbc);
        
        System.out.println("___ JiraIssueDetailDialog createCenterPanel completed");
        System.out.println("___ JiraIssueDetailDialog final panel component count: " + panel.getComponentCount());
        System.out.println("___ JiraIssueDetailDialog summaryField in panel: " + summaryField);
        System.out.println("___ JiraIssueDetailDialog summaryField parent: " + summaryField.getParent());
        
        // Print all components in the panel
        for (int i = 0; i < panel.getComponentCount(); i++) {
            Component comp = panel.getComponent(i);
            System.out.println("___ JiraIssueDetailDialog panel component[" + i + "]: " + comp + 
                " (bounds: " + comp.getBounds() + ", visible: " + comp.isVisible() + ", enabled: " + comp.isEnabled() + ")");
        }
        
        return panel;
    }
    
    @Override
    public void show() {
        System.out.println("___ JiraIssueDetailDialog show() called");
        super.show();
        System.out.println("___ JiraIssueDetailDialog show() completed");
        
        // Add global mouse event listener for debugging
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event instanceof MouseEvent) {
                    MouseEvent me = (MouseEvent) event;
                    Component source = me.getComponent();
                    
                    // Only log events related to our summary field or its parent
                    if (source == summaryField || (source != null && SwingUtilities.isDescendingFrom(source, summaryField))) {
                        System.out.println("___ AWTEventListener MouseEvent: " + me.getID() + 
                            " on " + source + " at " + me.getPoint());
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        
        // Check component states after dialog is shown
        SwingUtilities.invokeLater(() -> {
            System.out.println("___ JiraIssueDetailDialog post-show component check:");
            System.out.println("___ JiraIssueDetailDialog summaryField bounds: " + summaryField.getBounds());
            System.out.println("___ JiraIssueDetailDialog summaryField visible: " + summaryField.isVisible());
            System.out.println("___ JiraIssueDetailDialog summaryField enabled: " + summaryField.isEnabled());
            System.out.println("___ JiraIssueDetailDialog summaryField showing: " + summaryField.isShowing());
            System.out.println("___ JiraIssueDetailDialog summaryField displayable: " + summaryField.isDisplayable());
            
            // Test if we can find the summary field by walking the component tree
            Container parent = summaryField.getParent();
            while (parent != null) {
                System.out.println("___ JiraIssueDetailDialog parent: " + parent + 
                    " (bounds: " + parent.getBounds() + ", visible: " + parent.isVisible() + ")");
                parent = parent.getParent();
            }
        });
    }
    
    private JPanel createAssigneeSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Create combo box with "me" and "Select" options
        assigneeComboBox = new JComboBox<>(new String[]{"Select option...", "me", "Select"});
        panel.add(assigneeComboBox, BorderLayout.NORTH);
        
        // Create search panel (initially hidden)
        assigneeSearchPanel = new JPanel(new BorderLayout());
        assigneeSearchPanel.setVisible(false);
        
        assigneeSearchField = new JTextField();
        assigneeSearchPanel.add(new JLabel("Search assignee:"), BorderLayout.WEST);
        assigneeSearchPanel.add(assigneeSearchField, BorderLayout.CENTER);
        
        // Create search results list
        assigneeSearchResults = new JList<>();
        assigneeSearchResults.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assigneeSearchResults.setVisibleRowCount(5);
        JScrollPane scrollPane = new JScrollPane(assigneeSearchResults);
        scrollPane.setPreferredSize(new Dimension(300, 100));
        assigneeSearchPanel.add(scrollPane, BorderLayout.SOUTH);
        
        panel.add(assigneeSearchPanel, BorderLayout.SOUTH);
        
        // Add action listener to combo box
        assigneeComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selected = (String) assigneeComboBox.getSelectedItem();
                if ("me".equals(selected)) {
                    assigneeSearchPanel.setVisible(false);
                    setAssigneeToCurrentUser();
                } else if ("Select".equals(selected)) {
                    assigneeSearchPanel.setVisible(true);
                    assigneeSearchField.requestFocus();
                } else {
                    assigneeSearchPanel.setVisible(false);
                    selectedAssigneeAccountId = null;
                    selectedAssigneeDisplayName = null;
                }
                panel.revalidate();
                panel.repaint();
            }
        });
        
        // Add document listener to search field
        assigneeSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchAssignees();
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                searchAssignees();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                searchAssignees();
            }
        });
        
        // Add selection listener to search results
        assigneeSearchResults.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = assigneeSearchResults.getSelectedValue();
                if (selectedUser != null) {
                    selectedAssigneeDisplayName = selectedUser;
                    // Find the account ID for the selected user
                    findAccountIdForSelectedUser(selectedUser);
                }
            }
        });
        
        return panel;
    }
    
    private void setAssigneeToCurrentUser() {
        if (currentUser != null) {
            selectedAssigneeAccountId = currentUser.get("accountId").getAsString();
            selectedAssigneeDisplayName = currentUser.get("displayName").getAsString();
        }
    }
    
    private void searchAssignees() {
        String query = assigneeSearchField.getText().trim();
        if (query.isEmpty()) {
            assigneeSearchResults.setListData(new String[0]);
            return;
        }
        
        jiraService.searchUsersAsync(query)
            .thenAccept(users -> {
                SwingUtilities.invokeLater(() -> {
                    String[] userNames = users.stream()
                        .filter(user -> user.has("displayName"))
                        .map(user -> user.get("displayName").getAsString())
                        .toArray(String[]::new);
                    assigneeSearchResults.setListData(userNames);
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    System.err.println("Failed to search users: " + throwable.getMessage());
                    assigneeSearchResults.setListData(new String[0]);
                });
                return null;
            });
    }
    
    private void findAccountIdForSelectedUser(String displayName) {
        jiraService.searchUsersAsync(displayName)
            .thenAccept(users -> {
                SwingUtilities.invokeLater(() -> {
                    for (JsonObject user : users) {
                        if (user.has("displayName") && displayName.equals(user.get("displayName").getAsString())) {
                            selectedAssigneeAccountId = user.get("accountId").getAsString();
                            break;
                        }
                    }
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    System.err.println("Failed to find account ID for user: " + throwable.getMessage());
                });
                return null;
            });
    }
    
    @Override
    protected void doOKAction() {
        if (isValid()) {
            // Check if anything was modified
            boolean summaryChanged = !summaryField.getText().trim().equals(issue.getSummary() != null ? issue.getSummary() : "");
            boolean statusChanged = !statusComboBox.getSelectedItem().toString().equals(issue.getStatus() != null ? issue.getStatus() : "");
            boolean descriptionChanged = !descriptionArea.getText().trim().equals(issue.getDescription() != null ? issue.getDescription() : "");
            
            // Check if assignee was changed
            boolean assigneeChanged = false;
            String currentAssignee = issue.getAssignee();
            if (selectedAssigneeDisplayName != null) {
                assigneeChanged = !selectedAssigneeDisplayName.equals(currentAssignee);
            } else if (currentAssignee != null) {
                assigneeChanged = true; // Assignee was cleared
            }
            
            if (summaryChanged || statusChanged || descriptionChanged || assigneeChanged) {
                // Update the issue object
                issue.setSummary(summaryField.getText().trim());
                issue.setStatus((String) statusComboBox.getSelectedItem());
                issue.setDescription(descriptionArea.getText().trim());
                
                // Update assignee
                if (assigneeChanged) {
                    issue.setAssignee(selectedAssigneeDisplayName);
                }
                
                // Update via API
                updateIssue().thenRun(() -> {
                    SwingUtilities.invokeLater(() -> {
                        isModified = true;
                        super.doOKAction();
                    });
                }).exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        Messages.showErrorDialog(project, "Failed to update issue: " + throwable.getMessage(), "Update Error");
                    });
                    return null;
                });
            } else {
                super.doOKAction();
            }
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
    
    public boolean isModified() {
        return isModified;
    }
    
    public JiraIssue getIssue() {
        return issue;
    }
    
    private void loadData() {
        // Load current user info
        jiraService.getCurrentUserAsync()
            .thenAccept(user -> {
                SwingUtilities.invokeLater(() -> {
                    currentUser = user;
                    // Set initial assignee selection based on current issue assignee
                    initializeAssigneeSelection();
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    System.err.println("Failed to load current user: " + throwable.getMessage());
                });
                return null;
            });
            
        // Load available statuses for this issue
        jiraService.getIssueStatusesAsync(issue.getKey())
            .thenAccept(statuses -> {
                SwingUtilities.invokeLater(() -> {
                    statusComboBox.removeAllItems();
                    for (String status : statuses) {
                        statusComboBox.addItem(status);
                    }
                    
                    // Set current status
                    if (issue.getStatus() != null) {
                        statusComboBox.setSelectedItem(issue.getStatus());
                    }
                });
            })
            .exceptionally(throwable -> {
                SwingUtilities.invokeLater(() -> {
                    Messages.showErrorDialog(project, "Failed to load statuses: " + throwable.getMessage(), "Error");
                    // Add common statuses as fallback
                    statusComboBox.addItem("To Do");
                    statusComboBox.addItem("In Progress");
                    statusComboBox.addItem("Done");
                    if (issue.getStatus() != null) {
                        statusComboBox.setSelectedItem(issue.getStatus());
                    }
                });
                return null;
            });
    }
    
    private void initializeAssigneeSelection() {
        String currentAssignee = issue.getAssignee();
        if (currentAssignee != null) {
            selectedAssigneeDisplayName = currentAssignee;
            
            // Check if current assignee is the logged-in user
            if (currentUser != null && currentAssignee.equals(currentUser.get("displayName").getAsString())) {
                assigneeComboBox.setSelectedItem("me");
                selectedAssigneeAccountId = currentUser.get("accountId").getAsString();
            } else {
                // Set to "Select" and show the current assignee
                assigneeComboBox.setSelectedItem("Select");
                assigneeSearchPanel.setVisible(true);
                assigneeSearchField.setText(currentAssignee);
                assigneeSearchResults.setListData(new String[]{currentAssignee});
                assigneeSearchResults.setSelectedValue(currentAssignee, true);
                
                // Find the account ID for the current assignee
                findAccountIdForSelectedUser(currentAssignee);
            }
        }
    }
    
    private CompletableFuture<Void> updateIssue() {
        return jiraService.updateIssueAsync(issue);
    }
    
    // Inner class for inline editable field that mimics Jira web behavior
    private static class InlineEditableField extends JPanel {
        private JLabel displayLabel;
        private JTextField editField;
        private boolean isEditing = false;
        private String text;
        
        public InlineEditableField(String initialText) {
            this.text = initialText != null ? initialText : "";
            System.out.println("___ InlineEditableField created with text: '" + this.text + "'");
            setLayout(new BorderLayout());
            
            // Enable all mouse events and make sure component is interactive
            setEnabled(true);
            setVisible(true);
            setFocusable(true);
            setOpaque(true);
            
            initComponents();
            setupMouseListener();
            showDisplayMode();
            
            // Add a simple test mouse listener to the panel itself
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mouseClicked: " + e.getPoint());
                }
                
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mousePressed: " + e.getPoint());
                }
                
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mouseReleased: " + e.getPoint());
                }
                
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mouseEntered: " + e.getPoint());
                }
                
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mouseExited: " + e.getPoint());
                }
            });
            
            // Also add to panel to catch any events
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField PANEL mouseMoved: " + e.getPoint());
                }
            });
            
            System.out.println("___ InlineEditableField initialization completed");
        }
        
        private void initComponents() {
            System.out.println("___ InlineEditableField initComponents started");
            displayLabel = new JLabel(text);
            displayLabel.setOpaque(true);
            displayLabel.setBackground(getBackground());
            displayLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            
            // Explicitly enable mouse events for the label
            displayLabel.setEnabled(true);
            displayLabel.setVisible(true);
            displayLabel.setFocusable(false); // Labels don't need focus
            
            System.out.println("___ InlineEditableField displayLabel created: " + displayLabel);
            System.out.println("___ InlineEditableField displayLabel bounds: " + displayLabel.getBounds());
            System.out.println("___ InlineEditableField displayLabel enabled: " + displayLabel.isEnabled());
            System.out.println("___ InlineEditableField displayLabel visible: " + displayLabel.isVisible());
            
            editField = new JTextField(text);
            editField.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
            
            // Add focus lost listener to exit edit mode with delay
            editField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    System.out.println("___ InlineEditableField focusLost");
                    // Use SwingUtilities.invokeLater to avoid conflicts with other UI events
                    SwingUtilities.invokeLater(() -> {
                        if (isEditing && !editField.hasFocus()) {
                            exitEditMode();
                        }
                    });
                }
                
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    System.out.println("___ InlineEditableField focusGained");
                }
            });
            
            // Add enter key listener to confirm edit
            editField.addActionListener(e -> {
                System.out.println("___ InlineEditableField Enter key pressed");
                exitEditMode();
            });
            
            // Add escape key listener to cancel edit
            editField.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    System.out.println("___ keypress");
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        cancelEdit();
                    }
                }
            });
        }
        
        private void setupMouseListener() {
            System.out.println("___ InlineEditableField setupMouseListener started");
            System.out.println("___ InlineEditableField panel enabled: " + isEnabled());
            System.out.println("___ InlineEditableField panel visible: " + isVisible());
            System.out.println("___ InlineEditableField panel focusable: " + isFocusable());
            
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    System.out.println("___ InlineEditableField mouseClicked, isEditing: " + isEditing);
                    System.out.println("___ InlineEditableField mouseClicked source: " + e.getSource());
                    System.out.println("___ InlineEditableField mouseClicked button: " + e.getButton());
                    System.out.println("___ InlineEditableField mouseClicked clickCount: " + e.getClickCount());
                    if (!isEditing) {
                        enterEditMode();
                    }
                }
                
                @Override
                public void mouseEntered(MouseEvent e) {
                    System.out.println("___ InlineEditableField mouseEntered, isEditing: " + isEditing);
                    if (!isEditing) {
                        displayLabel.setBackground(new Color(240, 245, 255)); // Light blue hover
                        displayLabel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(200, 220, 255), 1),
                            BorderFactory.createEmptyBorder(3, 5, 3, 5)
                        ));
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        repaint();
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    System.out.println("___ InlineEditableField mouseExited, isEditing: " + isEditing);
                    if (!isEditing) {
                        displayLabel.setBackground(getBackground());
                        displayLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
            };
            
            // Only add to the display label to avoid conflicts
            System.out.println("___ InlineEditableField adding mouse listener to displayLabel");
            System.out.println("___ InlineEditableField displayLabel before adding listener: " + displayLabel);
            System.out.println("___ InlineEditableField displayLabel size: " + displayLabel.getSize());
            System.out.println("___ InlineEditableField displayLabel preferredSize: " + displayLabel.getPreferredSize());
            displayLabel.addMouseListener(mouseAdapter);
            System.out.println("___ InlineEditableField mouse listener added successfully");
            
            // Also add a simple test mouse listener to the label itself
            displayLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField LABEL SIMPLE mouseClicked: " + e.getPoint());
                }
            });
            
            // Also add mouse motion listener for debugging
            displayLabel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    System.out.println("___ InlineEditableField mouseMoved: " + e.getPoint());
                }
            });
        }
        
        private void enterEditMode() {
            System.out.println("___ InlineEditableField enterEditMode called");
            isEditing = true;
            removeAll();
            add(editField, BorderLayout.CENTER);
            editField.setText(text);
            editField.selectAll();
            editField.requestFocus();
            revalidate();
            repaint();
            System.out.println("___ InlineEditableField enterEditMode completed");
        }
        
        private void exitEditMode() {
            System.out.println("___ exitEditMode");
            if (isEditing) {
                text = editField.getText();
                displayLabel.setText(text);
                showDisplayMode();
            }
        }
        
        private void cancelEdit() {
            if (isEditing) {
                showDisplayMode();
            }
        }
        
        private void showDisplayMode() {
            System.out.println("___ InlineEditableField showDisplayMode called");
            isEditing = false;
            removeAll();
            add(displayLabel, BorderLayout.CENTER);
            System.out.println("___ InlineEditableField displayLabel added to panel");
            System.out.println("___ InlineEditableField panel component count: " + getComponentCount());
            System.out.println("___ InlineEditableField panel bounds: " + getBounds());
            System.out.println("___ InlineEditableField displayLabel bounds after add: " + displayLabel.getBounds());
            revalidate();
            repaint();
            System.out.println("___ InlineEditableField after revalidate/repaint:");
            System.out.println("___ InlineEditableField panel bounds: " + getBounds());
            System.out.println("___ InlineEditableField displayLabel bounds: " + displayLabel.getBounds());
            System.out.println("___ InlineEditableField displayLabel visible: " + displayLabel.isVisible());
            System.out.println("___ InlineEditableField panel visible: " + isVisible());
            System.out.println("___ InlineEditableField showDisplayMode completed");
        }
        
        public String getText() {
            return isEditing ? editField.getText() : text;
        }
        
        public void setText(String text) {
            this.text = text != null ? text : "";
            displayLabel.setText(this.text);
            if (isEditing) {
                editField.setText(this.text);
            }
        }
        
        @Override
        public void requestFocus() {
            if (isEditing) {
                editField.requestFocus();
            } else {
                enterEditMode();
            }
        }
        
        @Override
        public void paintComponent(java.awt.Graphics g) {
            System.out.println("___ InlineEditableField paintComponent called");
            System.out.println("___ InlineEditableField paintComponent bounds: " + getBounds());
            System.out.println("___ InlineEditableField paintComponent visible: " + isVisible());
            super.paintComponent(g);
        }
        
        @Override
        public Dimension getPreferredSize() {
            if (isEditing) {
                return editField.getPreferredSize();
            } else {
                Dimension labelSize = displayLabel.getPreferredSize();
                return new Dimension(Math.max(200, labelSize.width), Math.max(25, labelSize.height));
            }
        }
    }
    
    // Inner class for inline editable text area that mimics Jira web behavior
    private static class InlineEditableTextArea extends JPanel {
        private JLabel displayLabel;
        private JTextArea editArea;
        private JScrollPane editScrollPane;
        private boolean isEditing = false;
        private String text;
        
        public InlineEditableTextArea(String initialText) {
            this.text = initialText != null ? initialText : "";
            setLayout(new BorderLayout());
            initComponents();
            setupMouseListener();
            showDisplayMode();
        }
        
        private void initComponents() {
            // Create display label with HTML support for line breaks
            displayLabel = new JLabel();
            displayLabel.setOpaque(true);
            displayLabel.setBackground(getBackground());
            displayLabel.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
            displayLabel.setVerticalAlignment(SwingConstants.TOP);
            updateDisplayText();
            
            // Create edit area
            editArea = new JTextArea(text);
            editArea.setLineWrap(true);
            editArea.setWrapStyleWord(true);
            editArea.setRows(6);
            editArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            
            editScrollPane = new JScrollPane(editArea);
            editScrollPane.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
            editScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            editScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            
            // Enable wheel scrolling
            editScrollPane.setWheelScrollingEnabled(true);
            editScrollPane.getVerticalScrollBar().setUnitIncrement(16);
            editScrollPane.getVerticalScrollBar().setBlockIncrement(64);
            
            // Add focus lost listener to exit edit mode
            editArea.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    exitEditMode();
                }
            });
            
            // Add escape key listener to cancel edit
            editArea.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    System.out.println("__ keyPressed");
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                        cancelEdit();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && e.isControlDown()) {
                        // Ctrl+Enter to confirm edit
                        exitEditMode();
                    }
                }
            });
        }
        
        private void updateDisplayText() {
            System.out.println("__ updateDisplayText");
            if (text.isEmpty()) {
                displayLabel.setText("<html><i style='color: #999;'>Click to add description...</i></html>");
            } else {
                // Convert line breaks to HTML
                String htmlText = text.replace("\n", "<br>");
                displayLabel.setText("<html>" + htmlText + "</html>");
            }
        }
        
        private void setupMouseListener() {
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    System.out.println("___ mouseClicked");
                    if (!isEditing) {
                        enterEditMode();
                    }
                }
                
                @Override
                public void mouseEntered(MouseEvent e) {
                    System.out.println("___ mouseEntered");
                    if (!isEditing) {
                        displayLabel.setBackground(new Color(240, 245, 255)); // Light blue hover
                        displayLabel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(200, 220, 255), 1),
                            BorderFactory.createEmptyBorder(7, 5, 7, 5)
                        ));
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        repaint();
                    }
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    if (!isEditing) {
                        displayLabel.setBackground(getBackground());
                        displayLabel.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
                        setCursor(Cursor.getDefaultCursor());
                        repaint();
                    }
                }
                
                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    // Only handle mouse wheel in non-editing mode to avoid interfering with scroll pane
                    if (!isEditing && getParent() != null) {
                        getParent().dispatchEvent(e);
                    }
                    // In editing mode, let the scroll pane handle it naturally
                }
            };
            
            addMouseListener(mouseAdapter);
            // Only add wheel listener for display mode
            addMouseWheelListener(mouseAdapter);
            displayLabel.addMouseListener(mouseAdapter);
        }
        
        private void enterEditMode() {
            isEditing = true;
            removeAll();
            add(editScrollPane, BorderLayout.CENTER);
            editArea.setText(text);
            editArea.requestFocus();
            editArea.setCaretPosition(text.length()); // Move cursor to end
            revalidate();
            repaint();
        }
        
        private void exitEditMode() {
            if (isEditing) {
                text = editArea.getText();
                updateDisplayText();
                showDisplayMode();
            }
        }
        
        private void cancelEdit() {
            if (isEditing) {
                showDisplayMode();
            }
        }
        
        private void showDisplayMode() {
            isEditing = false;
            removeAll();
            add(displayLabel, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
        
        public String getText() {
            return isEditing ? editArea.getText() : text;
        }
        
        public void setText(String text) {
            this.text = text != null ? text : "";
            updateDisplayText();
            if (isEditing) {
                editArea.setText(this.text);
            }
        }
        
        @Override
        public void requestFocus() {
            if (isEditing) {
                editArea.requestFocus();
            } else {
                enterEditMode();
            }
        }
        
        @Override
        public Dimension getPreferredSize() {
            if (isEditing) {
                return new Dimension(400, 150);
            } else {
                Dimension labelSize = displayLabel.getPreferredSize();
                return new Dimension(Math.max(400, labelSize.width), Math.max(80, labelSize.height));
            }
        }
        
        @Override
        public Dimension getMinimumSize() {
            return new Dimension(300, 60);
        }
    }
}