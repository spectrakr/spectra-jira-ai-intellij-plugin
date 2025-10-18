package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import com.spectra.intellij.ai.actions.FixIssueByClaudeAction;
import com.spectra.intellij.ai.actions.FixIssueByCodexAction;
import com.spectra.intellij.ai.actions.FixIssueByGeminiAction;
import com.spectra.intellij.ai.actions.FixIssueByChatGPTAction;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.settings.JiraSettings;
import com.spectra.intellij.ai.toolwindow.handlers.ClaudeMcpConnectionHandler;
import com.spectra.intellij.ai.toolwindow.handlers.CodexMcpConnectionHandler;
import com.spectra.intellij.ai.toolwindow.handlers.GeminiMcpConnectionHandler;
import com.spectra.intellij.ai.ui.IssueTableCellRenderer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class IssueTableManager {

    private final Project project;
    private final JBTable issueTable;
    private final DefaultTableModel issueTableModel;
    private final DefaultTableModel originalIssueTableModel; // Store unfiltered data
    private final Map<Integer, String> priorityIconUrlMap = new HashMap<>();
    private final TableRowSorter<DefaultTableModel> tableSorter;

    private Consumer<String> onIssueSelected;

    // MCP connection handlers
    private final ClaudeMcpConnectionHandler claudeMcpConnectionHandler;
    private final CodexMcpConnectionHandler codexMcpConnectionHandler;
    private final GeminiMcpConnectionHandler geminiMcpConnectionHandler;

    public IssueTableManager(Project project) {
        this.project = project;
        this.claudeMcpConnectionHandler = new ClaudeMcpConnectionHandler(project);
        this.codexMcpConnectionHandler = new CodexMcpConnectionHandler(project);
        this.geminiMcpConnectionHandler = new GeminiMcpConnectionHandler(project);
        issueTableModel = new DefaultTableModel(
            new String[]{"Key", "Summary", "Status", "Story Points", "Priority", "Assignee"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        originalIssueTableModel = new DefaultTableModel(
            new String[]{"Key", "Summary", "Status", "Story Points", "Priority", "Assignee", "IssueType"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        issueTable = new JBTable(issueTableModel);
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Setup table sorter
        tableSorter = new TableRowSorter<>(issueTableModel);
        issueTable.setRowSorter(tableSorter);
        
        // Setup custom comparator for Priority column (column index 4)
        setupPriorityComparator();

        setupTable();
        setupSelectionListener();
        setupContextMenu();
    }
    
    private void setupPriorityComparator() {
        // Create priority value mapping
        Map<String, Integer> priorityValues = new HashMap<>();
        priorityValues.put("Highest", 5);
        priorityValues.put("High", 4);
        priorityValues.put("Medium", 3);
        priorityValues.put("Low", 2);
        priorityValues.put("Lowest", 1);
        
        // Set custom comparator for Priority column (index 4)
        tableSorter.setComparator(4, new Comparator<String>() {
            @Override
            public int compare(String priority1, String priority2) {
                // Handle null values
                if (priority1 == null && priority2 == null) return 0;
                if (priority1 == null) return -1;
                if (priority2 == null) return 1;
                
                // Get priority values, default to 0 for unknown priorities
                Integer value1 = priorityValues.getOrDefault(priority1, 0);
                Integer value2 = priorityValues.getOrDefault(priority2, 0);
                
                // Compare by numeric values (higher value = higher priority)
                return Integer.compare(value1, value2);
            }
        });
    }
    
    private void setupTable() {
        // Set custom renderer for Key column to show icons
        IssueTableCellRenderer renderer = new IssueTableCellRenderer();
        issueTable.getColumnModel().getColumn(0).setCellRenderer(renderer); // Key column
        
        // Set custom renderer for Priority column to show icons
        issueTable.getColumnModel().getColumn(4).setCellRenderer(new PriorityTableCellRenderer()); // Priority column
        
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
        
        issueTable.getColumnModel().getColumn(4).setPreferredWidth(50);  // Priority
        issueTable.getColumnModel().getColumn(4).setMaxWidth(50);
        issueTable.getColumnModel().getColumn(4).setMinWidth(50);
        
        issueTable.getColumnModel().getColumn(5).setPreferredWidth(30);  // Assignee
        issueTable.getColumnModel().getColumn(5).setMaxWidth(120);
        issueTable.getColumnModel().getColumn(5).setMinWidth(80);
        
        // Set auto resize mode to make Summary column fill remaining space
        issueTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    }
    
    private void setupSelectionListener() {
        issueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = issueTable.getSelectedRow();
                if (selectedRow >= 0) {
                    // Convert view row index to model row index to handle sorting
                    int modelRow = issueTable.convertRowIndexToModel(selectedRow);
                    String issueKey = (String) issueTableModel.getValueAt(modelRow, 0);
                    if (onIssueSelected != null) {
                        onIssueSelected.accept(issueKey);
                    }
                } else {
                    if (onIssueSelected != null) {
                        onIssueSelected.accept(null);
                    }
                }
            }
        });
    }

    private void setupContextMenu() {
        issueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(MouseEvent e) {
                int row = issueTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < issueTable.getRowCount()) {
                    issueTable.setRowSelectionInterval(row, row);

                    // Get issue key from selected row
                    int modelRow = issueTable.convertRowIndexToModel(row);
                    String issueKey = (String) issueTableModel.getValueAt(modelRow, 0);

                    // Create popup menu
                    JPopupMenu popupMenu = new JPopupMenu();

                    // Create "Open in Browser" menu item
                    JMenuItem openInBrowserMenuItem = new JMenuItem("웹브라우저로 보기");
                    openInBrowserMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                    openInBrowserMenuItem.addActionListener(ev -> {
                        try {
                            JiraSettings settings = JiraSettings.getInstance();
                            String jiraUrl = settings.getJiraUrl();
                            if (!jiraUrl.endsWith("/")) {
                                jiraUrl += "/";
                            }
                            String issueUrl = jiraUrl + "browse/" + issueKey;

                            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                                desktop.browse(new java.net.URI(issueUrl));
                            }
                        } catch (Exception ex) {
                            com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                        }
                    });

                    // Create "Copy Link" menu item
                    JMenuItem copyLinkMenuItem = new JMenuItem("링크 복사");
                    copyLinkMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                    copyLinkMenuItem.addActionListener(ev -> {
                        try {
                            JiraSettings settings = JiraSettings.getInstance();
                            String jiraUrl = settings.getJiraUrl();
                            if (!jiraUrl.endsWith("/")) {
                                jiraUrl += "/";
                            }
                            String issueUrl = jiraUrl + "browse/" + issueKey;

                            java.awt.datatransfer.StringSelection stringSelection =
                                new java.awt.datatransfer.StringSelection(issueUrl);
                            java.awt.Toolkit.getDefaultToolkit()
                                .getSystemClipboard()
                                .setContents(stringSelection, null);
                        } catch (Exception ex) {
                            com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                        }
                    });

                    // Create "Copy ID" menu item
                    JMenuItem copyIdMenuItem = new JMenuItem("아이디 복사 (" + issueKey + ")");
                    copyIdMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                    copyIdMenuItem.addActionListener(ev -> {
                        try {
                            java.awt.datatransfer.StringSelection stringSelection =
                                new java.awt.datatransfer.StringSelection(issueKey);
                            java.awt.Toolkit.getDefaultToolkit()
                                .getSystemClipboard()
                                .setContents(stringSelection, null);
                        } catch (Exception ex) {
                            com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                        }
                    });

                    // Add menu items
                    popupMenu.add(openInBrowserMenuItem);
                    popupMenu.add(copyLinkMenuItem);
                    popupMenu.add(copyIdMenuItem);

                    // Check connection status for each AI service
                    boolean isClaudeConnected = claudeMcpConnectionHandler.checkMcpConnection();
                    boolean isCodexConnected = codexMcpConnectionHandler.checkMcpConnection();
                    boolean isGeminiConnected = geminiMcpConnectionHandler.checkMcpConnection();

                    // Only add separator if at least one AI service is connected
                    if (isClaudeConnected || isCodexConnected || isGeminiConnected) {
                        popupMenu.addSeparator();
                    }

                    // Add Claude menu item only if connected
                    if (isClaudeConnected) {
                        JMenuItem claudeMenuItem = new JMenuItem("Fix issue (by Claude)         ");
                        claudeMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                        claudeMenuItem.addActionListener(ev -> {
                            FixIssueByClaudeAction claudeAction = new FixIssueByClaudeAction();
                            claudeAction.setIssueKey(issueKey);
                            try {
                                claudeAction.execute(project);
                            } catch (Exception ex) {
                                com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                            }
                        });
                        popupMenu.add(claudeMenuItem);
                    }

                    // Add Codex menu item only if connected
                    if (isCodexConnected) {
                        JMenuItem codexMenuItem = new JMenuItem("Fix issue (by Codex)          ");
                        codexMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                        codexMenuItem.addActionListener(ev -> {
                            FixIssueByCodexAction codexAction = new FixIssueByCodexAction();
                            codexAction.setIssueKey(issueKey);
                            try {
                                codexAction.execute(project);
                            } catch (Exception ex) {
                                com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                            }
                        });
                        popupMenu.add(codexMenuItem);
                    }

                    // Add Gemini menu item only if connected
                    if (isGeminiConnected) {
                        JMenuItem geminiMenuItem = new JMenuItem("Fix issue (by Gemini)         ");
                        geminiMenuItem.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                        geminiMenuItem.addActionListener(ev -> {
                            FixIssueByGeminiAction geminiAction = new FixIssueByGeminiAction();
                            geminiAction.setIssueKey(issueKey);
                            try {
                                geminiAction.execute(project);
                            } catch (Exception ex) {
                                com.intellij.openapi.diagnostic.Logger.getInstance(IssueTableManager.class).error(ex);
                            }
                        });
                        popupMenu.add(geminiMenuItem);
                    }

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }
    
    public void updateIssues(List<JiraIssue> issues) {
        // Clear both tables
        issueTableModel.setRowCount(0);
        originalIssueTableModel.setRowCount(0);
        priorityIconUrlMap.clear();
        
        IssueTableCellRenderer renderer = (IssueTableCellRenderer) issueTable.getColumnModel().getColumn(0).getCellRenderer();
        
        int row = 0;
        for (JiraIssue issue : issues) {
            String storyPointsStr = "";
            if (issue.getStoryPoints() != null) {
                storyPointsStr = issue.getStoryPoints().toString();
            }
            
            String issueType = issue.getIssueType() != null ? issue.getIssueType() : "";
            String assignee = issue.getAssignee() != null ? issue.getAssignee() : "";
            String status = issue.getStatus() != null ? issue.getStatus() : "";
            String summary = issue.getSummary() != null ? issue.getSummary() : "";
            String priority = issue.getPriority() != null ? issue.getPriority() : "";
            String priorityIconUrl = issue.getPriorityIconUrl();
            
            // Add to original data model (includes IssueType column)
            originalIssueTableModel.addRow(new Object[]{
                issue.getKey(),
                summary,
                status,
                storyPointsStr,
                priority,
                assignee,
                issueType // Hidden column for filtering
            });
            
            // Set issue type for renderer
            renderer.setIssueTypeForRow(row, issueType);
            
            // Store priority icon URL for this row
            if (priorityIconUrl != null) {
                priorityIconUrlMap.put(row, priorityIconUrl);
            }
            
            // Add to display table
            issueTableModel.addRow(new Object[]{
                issue.getKey(),
                summary,
                status,
                storyPointsStr,
                priority,
                assignee
            });
            
            row++;
        }
    }
    
    public void applyFilters(String selectedIssueType, String selectedAssignee, String selectedStatus) {
        // Clear current table
        issueTableModel.setRowCount(0);
        priorityIconUrlMap.clear(); // Clear priority icon URL map
        IssueTableCellRenderer renderer = (IssueTableCellRenderer) issueTable.getColumnModel().getColumn(0).getCellRenderer();

        int displayRow = 0;
        for (int i = 0; i < originalIssueTableModel.getRowCount(); i++) {
            String issueType = (String) originalIssueTableModel.getValueAt(i, 6); // IssueType column
            String assignee = (String) originalIssueTableModel.getValueAt(i, 5);  // Assignee column
            String status = (String) originalIssueTableModel.getValueAt(i, 2);    // Status column

            // Apply filters with null checks
            boolean passesFilter = true;

            if (selectedIssueType != null && !"All".equals(selectedIssueType) && !selectedIssueType.equals(issueType)) {
                passesFilter = false;
            }

            if (selectedAssignee != null && !"All".equals(selectedAssignee) && !selectedAssignee.equals(assignee)) {
                passesFilter = false;
            }

            if (selectedStatus != null && !"All".equals(selectedStatus) && !selectedStatus.equals(status)) {
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
                    originalIssueTableModel.getValueAt(i, 4), // Priority
                    originalIssueTableModel.getValueAt(i, 5)  // Assignee
                });
                displayRow++;
            }
        }
    }
    
    public void clearIssues() {
        issueTableModel.setRowCount(0);
        originalIssueTableModel.setRowCount(0);
        priorityIconUrlMap.clear();
    }
    
    public void selectIssueByKey(String issueKey) {
        if (issueKey == null) return;
        
        for (int i = 0; i < issueTableModel.getRowCount(); i++) {
            String tableIssueKey = (String) issueTableModel.getValueAt(i, 0);
            if (issueKey.equals(tableIssueKey)) {
                // Convert model row index to view row index to handle sorting
                int viewRow = issueTable.convertRowIndexToView(i);
                issueTable.setRowSelectionInterval(viewRow, viewRow);
                // Ensure the selected row is visible
                issueTable.scrollRectToVisible(issueTable.getCellRect(viewRow, 0, true));
                break;
            }
        }
    }
    
    public JBTable getTable() {
        return issueTable;
    }
    
    public int getIssueCount() {
        return issueTableModel.getRowCount();
    }
    
    public void setOnIssueSelected(Consumer<String> onIssueSelected) {
        this.onIssueSelected = onIssueSelected;
    }
    
    // Custom renderer for Priority column to show priority icons
    private class PriorityTableCellRenderer extends DefaultTableCellRenderer {
        private final Map<String, ImageIcon> iconCache = new HashMap<>();
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            String priority = value != null ? value.toString() : "";
            
            // Try to get actual row index from filtered view
            int actualRowIndex = getActualRowIndex(row);
            String iconUrl = priorityIconUrlMap.get(actualRowIndex);
            
            if (iconUrl != null && !iconUrl.isEmpty()) {
                ImageIcon icon = loadPriorityIcon(iconUrl);

                if (icon != null) {
                    setIcon(icon);
                    setText(""); // Clear text when showing icon
                } else {
                    setIcon(null);
                    setText("-");
                }
            } else {
                setIcon(null);
                setText("-");
            }
            
            setHorizontalAlignment(SwingConstants.CENTER);
            setToolTipText(priority); // Show full priority name on hover
            
            return this;
        }
        
        private int getActualRowIndex(int displayRow) {
            // Convert view row index to model row index when sorting is active
            if (issueTable.getRowSorter() != null && displayRow < issueTableModel.getRowCount()) {
                int modelRow = issueTable.convertRowIndexToModel(displayRow);
                String issueKey = (String) issueTableModel.getValueAt(modelRow, 0);
                for (int i = 0; i < originalIssueTableModel.getRowCount(); i++) {
                    if (issueKey.equals(originalIssueTableModel.getValueAt(i, 0))) {
                        return i;
                    }
                }
            }
            return displayRow;
        }
        
        private ImageIcon loadPriorityIcon(String iconUrl) {
            // Check cache first
            if (iconCache.containsKey(iconUrl)) {
                return iconCache.get(iconUrl);
            }
            
            try {
                // Extract filename from URL (e.g., medium_new.svg from https://enomix.atlassian.net/images/icons/priorities/medium_new.svg)
                String filename = iconUrl.substring(iconUrl.lastIndexOf('/') + 1);
                
                // Load icon from local resources using IconLoader (handles SVG files properly)
                String resourcePath = "/icons/" + filename;
                
                try {
                    Icon icon = com.intellij.openapi.util.IconLoader.getIcon(resourcePath, getClass());
                    if (icon != null) {
                        // IconLoader can handle scaling automatically, but we need to ensure 16x16 size
                        // Create a scaled ImageIcon for consistent table rendering
                        BufferedImage bufferedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = bufferedImage.createGraphics();
                        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        
                        // Scale and draw the icon
                        icon.paintIcon(null, g2d, 0, 0);
                        g2d.dispose();
                        
                        ImageIcon scaledIcon = new ImageIcon(bufferedImage);
                        iconCache.put(iconUrl, scaledIcon);
                        return scaledIcon;
                    }
                } catch (Exception iconLoadException) {
                    System.err.println("IconLoader failed for: " + resourcePath + " - " + iconLoadException.getMessage());
                }
                
                System.err.println("Priority icon resource not found: " + resourcePath);
            } catch (Exception e) {
                System.err.println("Failed to load priority icon from resources: " + iconUrl + " - " + e.getMessage());
            }
            
            return null;
        }
    }
}