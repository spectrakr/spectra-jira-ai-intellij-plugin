package com.spectra.intellij.ai.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class IssueTableCellRenderer extends DefaultTableCellRenderer {
    
    private static final String KEY_COLUMN = "Key";
    
    // Store issue types for each row to display icons
    private java.util.Map<Integer, String> rowIssueTypes = new java.util.HashMap<>();
    
    public void setIssueTypeForRow(int row, String issueType) {
        rowIssueTypes.put(row, issueType);
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
                                                   boolean hasFocus, int row, int column) {
        
        Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
        String columnName = table.getColumnName(column);
        
        if (KEY_COLUMN.equals(columnName)) {
            // For Key column, show icon + key text
            JLabel label = (JLabel) component;
            String keyValue = value != null ? value.toString() : "";
            
            // Get issue type for this row
            String issueType = rowIssueTypes.get(row);
            if (issueType == null) issueType = "";
            
            // Set icon and text
            Icon icon = IssueTypeIcons.getIconForIssueType(issueType);
            label.setIcon(icon);
            label.setText(keyValue);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setIconTextGap(5);
        }
        
        return component;
    }
}