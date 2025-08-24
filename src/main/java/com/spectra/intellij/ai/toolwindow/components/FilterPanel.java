package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class FilterPanel extends JPanel {
    
    // Constants for filter labels
    private static final String ISSUE_TYPE_ALL_LABEL = "이슈 유형";
    private static final String ASSIGNEE_ALL_LABEL = "담당자";
    private static final String STATUS_ALL_LABEL = "이슈 상태";
    
    private final Project project;
    private JComboBox<String> issueTypeFilter;
    private JComboBox<String> assigneeFilter;
    private JComboBox<String> statusFilter;
    private JButton createIssueButton;
    
    private Consumer<Void> onFilterChanged;
    private Consumer<Void> onRefresh;
    private Consumer<Void> onCreateIssue;
    
    public FilterPanel(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Left side - Filter controls
        JPanel leftFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        // Issue Type filter (no label)
        issueTypeFilter = new JComboBox<>();
        issueTypeFilter.addItem(ISSUE_TYPE_ALL_LABEL);
        issueTypeFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(issueTypeFilter);
        
        // Assignee filter (no label)
        assigneeFilter = new JComboBox<>();
        assigneeFilter.addItem(ASSIGNEE_ALL_LABEL);
        assigneeFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(assigneeFilter);
        
        // Status filter (no label)
        statusFilter = new JComboBox<>();
        statusFilter.addItem(STATUS_ALL_LABEL);
        statusFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(statusFilter);
        
        
        add(leftFilters, BorderLayout.WEST);
        
        // Right side - Refresh and Create Issue buttons
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton refreshFilterButton = new JButton();
        // Use theme-aware icon loading
        Color textColor = UIUtil.getLabelForeground();
        boolean isDark = textColor.getRed() > 127; // Light text indicates dark theme
        System.out.println("____ isDark : " + isDark);
        if (isDark) {
            refreshFilterButton.setIcon(IconLoader.getIcon("/icons/refresh_dark.svg", getClass()));
        } else {
            refreshFilterButton.setIcon(IconLoader.getIcon("/icons/refresh.svg", getClass()));
        }
        refreshFilterButton.setToolTipText("선택된 스프린트의 이슈 목록 새로고침");
        refreshFilterButton.addActionListener(e -> {
            if (onRefresh != null) {
                onRefresh.accept(null);
            }
        });
        refreshFilterButton.setPreferredSize(new Dimension(30, refreshFilterButton.getPreferredSize().height));
        refreshFilterButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        refreshFilterButton.setContentAreaFilled(false);
        refreshFilterButton.setFocusPainted(false);
        rightButtons.add(refreshFilterButton);
        
        createIssueButton = new JButton("이슈 생성");
        createIssueButton.addActionListener(e -> {
            if (onCreateIssue != null) {
                onCreateIssue.accept(null);
            }
        });
        rightButtons.add(createIssueButton);
        
        add(rightButtons, BorderLayout.EAST);
    }
    
    
    public void clearFilterOptions() {
        // Temporarily disable action listeners to prevent triggering events during clear
        ActionListener[] issueTypeListeners = issueTypeFilter.getActionListeners();
        ActionListener[] assigneeListeners = assigneeFilter.getActionListeners();
        ActionListener[] statusListeners = statusFilter.getActionListeners();
        
        // Remove listeners
        for (ActionListener listener : issueTypeListeners) {
            issueTypeFilter.removeActionListener(listener);
        }
        for (ActionListener listener : assigneeListeners) {
            assigneeFilter.removeActionListener(listener);
        }
        for (ActionListener listener : statusListeners) {
            statusFilter.removeActionListener(listener);
        }
        
        // Clear and reset items
        issueTypeFilter.removeAllItems();
        issueTypeFilter.addItem(ISSUE_TYPE_ALL_LABEL);
        
        assigneeFilter.removeAllItems();
        assigneeFilter.addItem(ASSIGNEE_ALL_LABEL);
        
        statusFilter.removeAllItems();
        statusFilter.addItem(STATUS_ALL_LABEL);
        
        // Re-add listeners
        for (ActionListener listener : issueTypeListeners) {
            issueTypeFilter.addActionListener(listener);
        }
        for (ActionListener listener : assigneeListeners) {
            assigneeFilter.addActionListener(listener);
        }
        for (ActionListener listener : statusListeners) {
            statusFilter.addActionListener(listener);
        }
    }
    
    public void addToFilterOptions(String issueType, String assignee, String status) {
        // Add issue type to filter if not already present
        if (!issueType.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < issueTypeFilter.getItemCount(); i++) {
                if (issueType.equals(issueTypeFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                issueTypeFilter.addItem(issueType);
            }
        }
        
        // Add assignee to filter if not already present
        if (!assignee.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < assigneeFilter.getItemCount(); i++) {
                if (assignee.equals(assigneeFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                assigneeFilter.addItem(assignee);
            }
        }
        
        // Add status to filter if not already present
        if (!status.isEmpty()) {
            boolean found = false;
            for (int i = 0; i < statusFilter.getItemCount(); i++) {
                if (status.equals(statusFilter.getItemAt(i))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                statusFilter.addItem(status);
            }
        }
    }
    
    // Getters for filter values
    public String getSelectedIssueType() {
        Object selected = issueTypeFilter.getSelectedItem();
        if (selected == null || ISSUE_TYPE_ALL_LABEL.equals(selected)) {
            return "All";
        }
        return (String) selected;
    }
    
    public String getSelectedAssignee() {
        Object selected = assigneeFilter.getSelectedItem();
        if (selected == null || ASSIGNEE_ALL_LABEL.equals(selected)) {
            return "All";
        }
        return (String) selected;
    }
    
    public String getSelectedStatus() {
        Object selected = statusFilter.getSelectedItem();
        if (selected == null || STATUS_ALL_LABEL.equals(selected)) {
            return "All";
        }
        return (String) selected;
    }
    
    // Event handlers setters
    public void setOnFilterChanged(Consumer<Void> onFilterChanged) {
        this.onFilterChanged = onFilterChanged;
    }
    
    public void setOnRefresh(Consumer<Void> onRefresh) {
        this.onRefresh = onRefresh;
    }
    
    public void setOnCreateIssue(Consumer<Void> onCreateIssue) {
        this.onCreateIssue = onCreateIssue;
    }
    
    public void setCreateIssueButtonEnabled(boolean enabled) {
        createIssueButton.setEnabled(enabled);
    }
}