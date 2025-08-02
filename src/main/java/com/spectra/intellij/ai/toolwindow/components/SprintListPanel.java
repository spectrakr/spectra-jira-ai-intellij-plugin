package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.spectra.intellij.ai.model.JiraSprint;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class SprintListPanel extends JPanel {
    
    private final Project project;
    private JList<JiraSprint> sprintList;
    private DefaultListModel<JiraSprint> sprintListModel;
    private JButton settingsButton;
    
    private Consumer<JiraSprint> onSprintSelected;
    private Consumer<Void> onSettingsClick;
    
    public SprintListPanel(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, 0));
        setMinimumSize(new Dimension(180, 0));
        
        // Top section with settings button and sprints label
        JPanel topSection = new JPanel(new BorderLayout());
        
        // Add settings button at the top
        settingsButton = new JButton("Jira Setting");
        settingsButton.addActionListener(e -> {
            if (onSettingsClick != null) {
                onSettingsClick.accept(null);
            }
        });
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        settingsPanel.add(settingsButton);
        topSection.add(settingsPanel, BorderLayout.NORTH);
        
        // Add Sprints label below settings button
        JPanel sprintsLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        sprintsLabelPanel.setBorder(BorderFactory.createTitledBorder("Sprints"));
        topSection.add(sprintsLabelPanel, BorderLayout.CENTER);
        
        add(topSection, BorderLayout.NORTH);
        
        sprintListModel = new DefaultListModel<>();
        sprintList = new JList<>(sprintListModel);
        sprintList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Set custom renderer to show only sprint name
        sprintList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof JiraSprint) {
                    JiraSprint sprint = (JiraSprint) value;
                    setText(sprint.getName()); // Only show name, not status
                }
                return this;
            }
        });
        
        sprintList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                JiraSprint selectedSprint = sprintList.getSelectedValue();
                if (selectedSprint != null && onSprintSelected != null) {
                    onSprintSelected.accept(selectedSprint);
                }
            }
        });
        
        JBScrollPane sprintScrollPane = new JBScrollPane(sprintList);
        add(sprintScrollPane, BorderLayout.CENTER);
    }
    
    public void updateSprints(List<JiraSprint> sprints) {
        sprintListModel.clear();
        for (JiraSprint sprint : sprints) {
            sprintListModel.addElement(sprint);
        }
    }
    
    public void clearSprints() {
        sprintListModel.clear();
    }
    
    public JiraSprint getSelectedSprint() {
        return sprintList.getSelectedValue();
    }
    
    // Event handlers setters
    public void setOnSprintSelected(Consumer<JiraSprint> onSprintSelected) {
        this.onSprintSelected = onSprintSelected;
    }
    
    public void setOnSettingsClick(Consumer<Void> onSettingsClick) {
        this.onSettingsClick = onSettingsClick;
    }
}