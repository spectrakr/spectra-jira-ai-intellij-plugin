package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class FilterPanel extends JPanel {
    
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
        issueTypeFilter.addItem("All");
        issueTypeFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(issueTypeFilter);
        
        // Assignee filter (no label)
        assigneeFilter = new JComboBox<>();
        assigneeFilter.addItem("All");
        assigneeFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(assigneeFilter);
        
        // Status filter (no label)
        statusFilter = new JComboBox<>();
        statusFilter.addItem("All");
        statusFilter.addActionListener(e -> {
            if (onFilterChanged != null) {
                onFilterChanged.accept(null);
            }
        });
        leftFilters.add(statusFilter);
        
        // Clear filters button
        JButton clearFiltersButton = new JButton("Clear");
        clearFiltersButton.addActionListener(e -> clearFilters());
        leftFilters.add(clearFiltersButton);
        
        add(leftFilters, BorderLayout.WEST);
        
        // Right side - Refresh and Create Issue buttons
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        
        JButton refreshFilterButton = new JButton("⟲");
        refreshFilterButton.setToolTipText("Refresh");
        refreshFilterButton.addActionListener(e -> {
            if (onRefresh != null) {
                onRefresh.accept(null);
            }
        });
        refreshFilterButton.setPreferredSize(new Dimension(30, refreshFilterButton.getPreferredSize().height));
        rightButtons.add(refreshFilterButton);
        
        createIssueButton = new JButton("Create Issue");
        createIssueButton.addActionListener(e -> {
            if (onCreateIssue != null) {
                onCreateIssue.accept(null);
            }
        });
        rightButtons.add(createIssueButton);
        
        add(rightButtons, BorderLayout.EAST);
    }
    
    public void clearFilters() {
        issueTypeFilter.setSelectedItem("All");
        assigneeFilter.setSelectedItem("All");
        statusFilter.setSelectedItem("All");
        if (onFilterChanged != null) {
            onFilterChanged.accept(null);
        }
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
        issueTypeFilter.addItem("All");
        
        assigneeFilter.removeAllItems();
        assigneeFilter.addItem("All");
        
        statusFilter.removeAllItems();
        statusFilter.addItem("All");
        
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
        return selected != null ? (String) selected : "All";
    }
    
    public String getSelectedAssignee() {
        Object selected = assigneeFilter.getSelectedItem();
        return selected != null ? (String) selected : "All";
    }
    
    public String getSelectedStatus() {
        Object selected = statusFilter.getSelectedItem();
        return selected != null ? (String) selected : "All";
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