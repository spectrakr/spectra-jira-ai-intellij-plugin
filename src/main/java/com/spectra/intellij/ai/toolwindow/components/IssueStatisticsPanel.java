package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.spectra.intellij.ai.model.JiraIssue;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Comparator;
import java.util.*;
import java.util.List;

public class IssueStatisticsPanel extends JPanel {

    private JBTable statisticsTable;
    private DefaultTableModel tableModel;
    private List<String> availableStatuses; // Dynamic list of statuses
    
    // Dynamic statistics data structure
    private static class UserStatistics {
        String assignee;
        String assigneeAvatarUrl;
        int totalIssues = 0;
        double totalStoryPoints = 0.0;
        double completedStoryPoints = 0.0;
        Map<String, Integer> statusIssueCount = new HashMap<>();
        Map<String, Double> statusStoryPoints = new HashMap<>();
        
        UserStatistics(String assignee, String assigneeAvatarUrl) {
            this.assignee = assignee;
            this.assigneeAvatarUrl = assigneeAvatarUrl;
        }
        
        void addIssue(String status, double storyPoints) {
            totalIssues++;
            totalStoryPoints += storyPoints;
            
            // Check if status indicates completion
            if (isCompletedStatus(status)) {
                completedStoryPoints += storyPoints;
            }
            
            // Update status-specific counts
            statusIssueCount.put(status, statusIssueCount.getOrDefault(status, 0) + 1);
            statusStoryPoints.put(status, statusStoryPoints.getOrDefault(status, 0.0) + storyPoints);
        }
        
        private boolean isCompletedStatus(String status) {
            if (status == null) return false;
            String lowerStatus = status.toLowerCase();
            return lowerStatus.contains("완료") || lowerStatus.contains("done") || 
                   lowerStatus.contains("closed") || lowerStatus.contains("resolved") ||
                   lowerStatus.contains("complete");
        }
        
        String getProgressPercentage() {
            if (totalStoryPoints == 0.0) {
                return "0%";
            }
            double percentage = (completedStoryPoints / totalStoryPoints) * 100;
            return String.format("%.0f%%", percentage);
        }
        
        String getCountAndPointsForStatus(String status) {
            int count = statusIssueCount.getOrDefault(status, 0);
            double points = statusStoryPoints.getOrDefault(status, 0.0);
            return formatPointsAndCount(points, count);
        }
        
        String getTotalCountAndPoints() {
            return formatPointsAndCount(totalStoryPoints, totalIssues);
        }
        
        private String formatPointsAndCount(double points, int count) {
            String pointsStr;
            if (points == 0.0) {
                pointsStr = "0";
            } else if (points == Math.floor(points)) {
                pointsStr = String.format("%.0f", points);
            } else {
                pointsStr = String.format("%.1f", points);
            }
            return String.format("<html><div style='text-align: center; line-height: 1.1;'><b>%s point</b><br><span style='font-size: 85%%; color: gray;'>%d 건</span></div></html>", pointsStr, count);
        }
    }
    
    public IssueStatisticsPanel() {
        this.availableStatuses = new ArrayList<>();
        initializeComponents();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Initialize with minimal columns - will be recreated dynamically
        createTableWithColumns(Arrays.asList("담당자", "진행률", "전체"));
        
        // Add header explanation
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JLabel explanationLabel = new JLabel("(이슈 개수/스토리 포인트)");
        explanationLabel.setFont(explanationLabel.getFont().deriveFont(Font.ITALIC));
        explanationLabel.setForeground(Color.GRAY);
        headerPanel.add(explanationLabel);
        
        add(headerPanel, BorderLayout.NORTH);
    }
    
    private void createTableWithColumns(List<String> columnNames) {
        // Remove existing table if present
        if (statisticsTable != null) {
            Container parent = statisticsTable.getParent();
            if (parent != null) {
                Container grandParent = parent.getParent();
                if (grandParent != null) {
                    remove(grandParent);
                }
            }
        }
        
        // Create new table model
        String[] columnArray = columnNames.toArray(new String[0]);
        tableModel = new DefaultTableModel(columnArray, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        
        statisticsTable = new JBTable(tableModel);
        
        // Enable sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        statisticsTable.setRowSorter(sorter);
        
        // Set custom comparators
        for (int i = 0; i < columnNames.size(); i++) {
            if (i == 0) {
                // Assignee column - sort by text
                sorter.setComparator(i, new AssigneeComparator());
            } else if (i == 1) {
                // Progress column - sort by percentage
                sorter.setComparator(i, new ProgressComparator());
            } else {
                // Story points columns
                sorter.setComparator(i, new StoryPointsComparator());
            }
        }
        
        customizeTable(columnNames.size());
        
        // Add table to scroll pane with default styling
        JScrollPane scrollPane = new JScrollPane(statisticsTable);
        scrollPane.setPreferredSize(new Dimension(0, 200));
        scrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1));
        add(scrollPane, BorderLayout.CENTER);
        
        revalidate();
        repaint();
    }
    
    private JLabel createAssigneeDisplay(String assigneeName, String avatarUrl) {
        JLabel label = new JLabel();
        label.setText(assigneeName);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        
        // Try to load avatar image
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            try {
                URL url = new URL(avatarUrl);
                ImageIcon originalIcon = new ImageIcon(url);
                
                // Resize avatar to 16x16
                Image scaledImage = originalIcon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaledImage);
                label.setIcon(scaledIcon);
            } catch (Exception e) {
                // If avatar loading fails, use default user icon or no icon
                label.setIcon(null);
            }
        }
        
        return label;
    }
    
    private void addHeaderClickListeners() {
        JTableHeader header = statisticsTable.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = header.columnAtPoint(e.getPoint());
                if (column >= 0) {
                    TableRowSorter<?> sorter = (TableRowSorter<?>) statisticsTable.getRowSorter();
                    if (sorter != null) {
                        java.util.List<? extends RowSorter.SortKey> sortKeys = sorter.getSortKeys();
                        
                        // Toggle sort order for the clicked column
                        if (!sortKeys.isEmpty() && sortKeys.get(0).getColumn() == column) {
                            // If clicking on already sorted column, toggle order
                            SortOrder currentOrder = sortKeys.get(0).getSortOrder();
                            SortOrder newOrder = currentOrder == SortOrder.ASCENDING ? 
                                SortOrder.DESCENDING : SortOrder.ASCENDING;
                            sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(column, newOrder)));
                        } else {
                            // If clicking on new column, sort ascending
                            sorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(column, SortOrder.ASCENDING)));
                        }
                    }
                }
            }
        });
    }

    private void customizeTable(int columnCount) {
        // Set selection mode to match IssueTableManager
        statisticsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Set column widths dynamically
        for (int i = 0; i < columnCount; i++) {
            if (i == 0) { // 담당자 column
                statisticsTable.getColumnModel().getColumn(i).setPreferredWidth(100);
                statisticsTable.getColumnModel().getColumn(i).setMinWidth(80);
                statisticsTable.getColumnModel().getColumn(i).setMaxWidth(120);
            } else if (i == 1) { // 진행률 column
                statisticsTable.getColumnModel().getColumn(i).setPreferredWidth(60);
                statisticsTable.getColumnModel().getColumn(i).setMinWidth(50);
                statisticsTable.getColumnModel().getColumn(i).setMaxWidth(80);
            } else { // Status columns
                statisticsTable.getColumnModel().getColumn(i).setPreferredWidth(100);
                statisticsTable.getColumnModel().getColumn(i).setMinWidth(80);
                statisticsTable.getColumnModel().getColumn(i).setMaxWidth(140);
            }
        }
        
        // Match IssueTableManager table settings - use default JBTable styling
        statisticsTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        // Remove custom styling to match default IntelliJ table appearance
        
        // Create custom renderer matching IssueTableManager style
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                if (column == 0 && value instanceof JLabel) {
                    // For assignee column with avatar, return the JLabel directly
                    JLabel label = (JLabel) value;
                    if (isSelected) {
                        label.setBackground(table.getSelectionBackground());
                        label.setForeground(table.getSelectionForeground());
                        label.setOpaque(true);
                    } else {
                        label.setBackground(table.getBackground());
                        label.setForeground(table.getForeground());
                        label.setOpaque(false);
                    }
                    return label;
                } else {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    
                    // Set alignment based on column
                    if (column == 0) { // 담당자 column - left align
                        setHorizontalAlignment(SwingConstants.LEFT);
                    } else { // Data columns - center align
                        setHorizontalAlignment(SwingConstants.CENTER);
                    }
                    
                    return this;
                }
            }
        };
        
        // Create custom header renderer for center alignment
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                return this;
            }
        };
        
        // Apply minimal renderer to all columns
        for (int i = 0; i < statisticsTable.getColumnCount(); i++) {
            statisticsTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
            statisticsTable.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
        
        // Add header click listeners for sorting
        addHeaderClickListeners();
    }
    
    public void updateStatistics(List<JiraIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            clearStatistics();
            return;
        }
        
        // Collect all unique statuses from issues
        Set<String> statusSet = new LinkedHashSet<>(); // LinkedHashSet preserves insertion order
        for (JiraIssue issue : issues) {
            String status = issue.getStatus();
            if (status != null && !status.trim().isEmpty()) {
                statusSet.add(status.trim());
            }
        }
        
        // Create ordered status list based on preferred order
        List<String> orderedStatuses = orderStatusesByPriority(statusSet);
        
        // Create new column list: 담당자, 진행률, 전체, then ordered statuses
        List<String> columnNames = new ArrayList<>();
        columnNames.add("담당자");
        columnNames.add("진행률");
        columnNames.add("전체");
        columnNames.addAll(orderedStatuses);
        
        // Update available statuses with ordered list
        this.availableStatuses = orderedStatuses;
        
        // Recreate table with dynamic columns
        createTableWithColumns(columnNames);
        
        // Calculate statistics by assignee
        Map<String, UserStatistics> statisticsMap = calculateDynamicStatistics(issues);
        
        // Populate table with dynamic columns
        for (UserStatistics stats : statisticsMap.values()) {
            Object[] rowData = new Object[columnNames.size()];
            // Create assignee display with avatar
            String assigneeName = stats.assignee != null && !stats.assignee.isEmpty() ? stats.assignee : "미할당";
            rowData[0] = createAssigneeDisplay(assigneeName, stats.assigneeAvatarUrl);
            rowData[1] = stats.getProgressPercentage();
            rowData[2] = stats.getTotalCountAndPoints();
            
            // Fill in status columns
            for (int i = 0; i < availableStatuses.size(); i++) {
                String status = availableStatuses.get(i);
                rowData[i + 3] = stats.getCountAndPointsForStatus(status); // +3 because first three columns are 담당자, 진행률, 전체
            }
            
            tableModel.addRow(rowData);
        }
    }
    
    private Map<String, UserStatistics> calculateDynamicStatistics(List<JiraIssue> issues) {
        Map<String, UserStatistics> statisticsMap = new HashMap<>();
        
        for (JiraIssue issue : issues) {
            String assignee = issue.getAssignee();
            if (assignee == null || assignee.trim().isEmpty()) {
                assignee = "미할당";
            }
            
            // Get or create user statistics
            UserStatistics stats = statisticsMap.computeIfAbsent(assignee, key -> {
                String avatarUrl = issue.getAssigneeAvatarUrl();
                return new UserStatistics(key, avatarUrl);
            });
            
            // Get issue data
            String status = issue.getStatus();
            double storyPoints = getStoryPoints(issue);
            
            // Add issue to statistics (status can be null or empty)
            if (status == null || status.trim().isEmpty()) {
                status = "상태 없음"; // Default status for issues without status
            } else {
                status = status.trim();
            }
            
            stats.addIssue(status, storyPoints);
        }
        
        return statisticsMap;
    }
    
    private double getStoryPoints(JiraIssue issue) {
        Double storyPoints = issue.getStoryPoints();
        return storyPoints != null ? storyPoints : 0.0;
    }
    
    private List<String> orderStatusesByPriority(Set<String> statusSet) {
        // Define preferred order for status columns
        List<String> preferredOrder = Arrays.asList(
            "해야할 일", "해야 할 일", "To Do", "TODO", "Open", "Backlog",
            "진행 중", "진행중", "In Progress", "In-Progress", "Doing", "Working",
            "개발완료", "개발 완료", "Development Complete", "Dev Complete",
            "완료", "Done", "Closed", "Resolved", "Complete", "Completed"
        );
        
        List<String> orderedStatuses = new ArrayList<>();
        Set<String> addedStatuses = new HashSet<>();
        
        // First, add statuses in preferred order if they exist
        for (String preferredStatus : preferredOrder) {
            for (String actualStatus : statusSet) {
                // Check for exact match or case-insensitive match
                if (actualStatus.equals(preferredStatus) || 
                    actualStatus.equalsIgnoreCase(preferredStatus)) {
                    if (!addedStatuses.contains(actualStatus)) {
                        orderedStatuses.add(actualStatus);
                        addedStatuses.add(actualStatus);
                    }
                }
            }
        }
        
        // Then add any remaining statuses that weren't in the preferred list
        for (String status : statusSet) {
            if (!addedStatuses.contains(status)) {
                orderedStatuses.add(status);
            }
        }
        
        return orderedStatuses;
    }
    
    public void clearStatistics() {
        // Reset to minimal columns when clearing
        this.availableStatuses = new ArrayList<>();
        createTableWithColumns(Arrays.asList("담당자", "진행률", "전체"));
    }
    
    // Custom comparator for assignee column
    private static class AssigneeComparator implements Comparator<Object> {
        @Override
        public int compare(Object o1, Object o2) {
            String name1 = "";
            String name2 = "";
            
            if (o1 instanceof JLabel) {
                name1 = ((JLabel) o1).getText();
            } else if (o1 instanceof String) {
                name1 = (String) o1;
            }
            
            if (o2 instanceof JLabel) {
                name2 = ((JLabel) o2).getText();
            } else if (o2 instanceof String) {
                name2 = (String) o2;
            }
            
            return name1.compareToIgnoreCase(name2);
        }
    }
    
    // Custom comparator for progress column
    private static class ProgressComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            double percentage1 = extractPercentage(s1);
            double percentage2 = extractPercentage(s2);
            return Double.compare(percentage1, percentage2);
        }
        
        private double extractPercentage(String value) {
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }
            
            try {
                String cleanValue = value.replace("%", "");
                return Double.parseDouble(cleanValue);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }
    
    // Custom comparator for story points columns
    private static class StoryPointsComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            // Extract story points from HTML formatted text
            double points1 = extractStoryPointsFromHtml(s1);
            double points2 = extractStoryPointsFromHtml(s2);
            return Double.compare(points1, points2);
        }
        
        private double extractStoryPointsFromHtml(String value) {
            if (value == null || value.trim().isEmpty()) {
                return 0.0;
            }
            
            try {
                // Extract points from HTML: <b>X point</b>
                String cleanValue = value.replaceAll("<[^>]*>", ""); // Remove HTML tags
                String[] parts = cleanValue.split(" ");
                if (parts.length > 0) {
                    return Double.parseDouble(parts[0]);
                }
                return 0.0;
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }
}