package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.ui.table.JBTable;
import com.spectra.intellij.ai.model.JiraIssue;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

public class IssueStatisticsPanel extends JPanel {

    private JBTable statisticsTable;
    private DefaultTableModel tableModel;
    private List<String> availableStatuses; // Dynamic list of statuses
    
    // Dynamic statistics data structure
    private static class UserStatistics {
        String assignee;
        int totalIssues = 0;
        double totalStoryPoints = 0.0;
        Map<String, Integer> statusIssueCount = new HashMap<>();
        Map<String, Double> statusStoryPoints = new HashMap<>();
        
        UserStatistics(String assignee) {
            this.assignee = assignee;
        }
        
        void addIssue(String status, double storyPoints) {
            totalIssues++;
            totalStoryPoints += storyPoints;
            
            // Update status-specific counts
            statusIssueCount.put(status, statusIssueCount.getOrDefault(status, 0) + 1);
            statusStoryPoints.put(status, statusStoryPoints.getOrDefault(status, 0.0) + storyPoints);
        }
        
        String getCountAndPointsForStatus(String status) {
            int count = statusIssueCount.getOrDefault(status, 0);
            double points = statusStoryPoints.getOrDefault(status, 0.0);
            return formatCountAndPoints(count, points);
        }
        
        String getTotalCountAndPoints() {
            return formatCountAndPoints(totalIssues, totalStoryPoints);
        }
        
        private String formatCountAndPoints(int count, double points) {
            if (points == 0.0) {
                return String.format("%d/0", count);
            } else if (points == Math.floor(points)) {
                return String.format("%d/%.0f", count, points);
            } else {
                return String.format("%d/%.1f", count, points);
            }
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
        createTableWithColumns(Arrays.asList("담당자", "전체"));
        
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
        customizeTable(columnNames.size());
        
        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(statisticsTable);
        scrollPane.setPreferredSize(new Dimension(0, 200));
        add(scrollPane, BorderLayout.CENTER);
        
        revalidate();
        repaint();
    }
    
    private void customizeTable(int columnCount) {
        // Set selection mode to match IssueTableManager
        statisticsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Set column widths dynamically
        for (int i = 0; i < columnCount; i++) {
            if (i == 0) { // 담당자 column
                statisticsTable.getColumnModel().getColumn(i).setPreferredWidth(120);
                statisticsTable.getColumnModel().getColumn(i).setMinWidth(100);
                statisticsTable.getColumnModel().getColumn(i).setMaxWidth(150);
            } else { // Status columns
                statisticsTable.getColumnModel().getColumn(i).setPreferredWidth(80);
                statisticsTable.getColumnModel().getColumn(i).setMinWidth(70);
                statisticsTable.getColumnModel().getColumn(i).setMaxWidth(120);
            }
        }
        
        // Set auto resize mode to match IssueTableManager
        statisticsTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        
        // Create custom renderer matching IssueTableManager style
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                // Set alignment based on column
                if (column == 0) { // 담당자 column - left align
                    setHorizontalAlignment(SwingConstants.LEFT);
                } else { // Data columns - center align
                    setHorizontalAlignment(SwingConstants.CENTER);
                }
                
                return this;
            }
        };
        
        // Apply renderer to all columns
        for (int i = 0; i < statisticsTable.getColumnCount(); i++) {
            statisticsTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
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
        
        // Create new column list: 담당자, 전체, then ordered statuses
        List<String> columnNames = new ArrayList<>();
        columnNames.add("담당자");
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
            rowData[0] = stats.assignee != null && !stats.assignee.isEmpty() ? stats.assignee : "미할당";
            rowData[1] = stats.getTotalCountAndPoints();
            
            // Fill in status columns
            for (int i = 0; i < availableStatuses.size(); i++) {
                String status = availableStatuses.get(i);
                rowData[i + 2] = stats.getCountAndPointsForStatus(status); // +2 because first two columns are 담당자, 전체
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
            UserStatistics stats = statisticsMap.computeIfAbsent(assignee, UserStatistics::new);
            
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
        createTableWithColumns(Arrays.asList("담당자", "전체"));
    }
}