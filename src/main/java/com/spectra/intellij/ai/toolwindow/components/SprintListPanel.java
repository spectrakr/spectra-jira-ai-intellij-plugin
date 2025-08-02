package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.spectra.intellij.ai.model.JiraSprint;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class SprintListPanel extends JPanel {
    
    private final Project project;
    private JList<JiraSprint> sprintList;
    private DefaultListModel<JiraSprint> sprintListModel;
    private JButton settingsButton;
    private JButton checkVersionButton;
    
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
        
        // Add buttons at the top
        settingsButton = new JButton("Jira Setting");
        settingsButton.setPreferredSize(new Dimension(80, 25));
        settingsButton.addActionListener(e -> {
            if (onSettingsClick != null) {
                onSettingsClick.accept(null);
            }
        });
        
        checkVersionButton = new JButton("Version");
        checkVersionButton.setPreferredSize(new Dimension(65, 25));
        checkVersionButton.setToolTipText("Check Plugin Version");
        checkVersionButton.addActionListener(e -> showVersionInfo());
        
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 3));
        buttonsPanel.add(settingsButton);
        buttonsPanel.add(checkVersionButton);
        topSection.add(buttonsPanel, BorderLayout.NORTH);
        
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
    
    private void showVersionInfo() {
        String version = getPluginVersion();
        String buildDate = getBuildDate();
        
        StringBuilder message = new StringBuilder();
        message.append("Spectra Jira AI Plugin\n\n");
        message.append("Version: ").append(version != null ? version : "Unknown").append("\n");
        message.append("Build Date: ").append(buildDate != null ? buildDate : "Unknown").append("\n");
        message.append("Check Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        Messages.showInfoMessage(
            project,
            message.toString(),
            "Plugin Version Information"
        );
    }
    
    private String getPluginVersion() {
        try {
            // Try to get version from plugin descriptor
            PluginId pluginId = PluginId.getId("com.spectra.jira.ai");
            IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
            if (plugin != null) {
                return plugin.getVersion();
            }
        } catch (Exception e) {
            // Ignore and try alternative methods
        }
        
        try {
            // Try to get version from package
            Package pkg = getClass().getPackage();
            if (pkg != null) {
                String version = pkg.getImplementationVersion();
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return "1.0.2"; // Fallback version
    }
    
    private String getBuildDate() {
        try {
            // Try to get build timestamp from manifest
            Package pkg = getClass().getPackage();
            if (pkg != null) {
                String buildTime = pkg.getImplementationTitle();
                if (buildTime != null) {
                    return buildTime;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Fallback to current date for development builds
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}