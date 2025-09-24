package com.spectra.intellij.ai.toolwindow.components;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
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
    private JButton refreshButton;
    private JButton checkVersionButton;
    
    private Consumer<JiraSprint> onSprintSelected;
    private Consumer<Void> onSettingsClick;
    private Consumer<Void> onRefreshClick;
    
    public SprintListPanel(Project project) {
        this.project = project;
        initializeComponents();
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(200, 0));
        setMinimumSize(new Dimension(180, 0));
        
        // Top section with header layout
        JPanel topSection = new JPanel(new BorderLayout());
        
        // Header panel with sprint title and icon buttons
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        
        // Sprint list title on the left
        JLabel sprintsLabel = new JLabel("스프린트 목록");
        sprintsLabel.setFont(sprintsLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(sprintsLabel, BorderLayout.WEST);
        
        // Icon buttons on the right
        JPanel iconButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        
        // Refresh button with icon
        refreshButton = new JButton();
        // Use theme-aware icon loading
        Color textColor = UIUtil.getLabelForeground();
        boolean isDark = textColor.getRed() > 127; // Light text indicates dark theme
        if (isDark) {
            refreshButton.setIcon(IconLoader.getIcon("/icons/refresh_dark.svg", getClass()));
        } else {
            refreshButton.setIcon(IconLoader.getIcon("/icons/refresh.svg", getClass()));
        }
        refreshButton.setToolTipText("Refresh Sprint List");
        refreshButton.setPreferredSize(new Dimension(24, 24));
        refreshButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        refreshButton.setContentAreaFilled(false);
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(e -> {
            if (onRefreshClick != null) {
                onRefreshClick.accept(null);
            }
        });
        
        // Settings button with icon
        settingsButton = new JButton();
        // Use theme-aware icon loading for settings button
        if (isDark) {
            settingsButton.setIcon(IconLoader.getIcon("/icons/settings_dark.svg", getClass()));
        } else {
            settingsButton.setIcon(IconLoader.getIcon("/icons/settings.svg", getClass()));
        }
        settingsButton.setToolTipText("Jira Setting");
        settingsButton.setPreferredSize(new Dimension(24, 24));
        settingsButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        settingsButton.setContentAreaFilled(false);
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> {
            if (onSettingsClick != null) {
                onSettingsClick.accept(null);
            }
        });


        iconButtonPanel.add(refreshButton);
        iconButtonPanel.add(settingsButton);
        headerPanel.add(iconButtonPanel, BorderLayout.EAST);
        
        topSection.add(headerPanel, BorderLayout.NORTH);
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
        sprintScrollPane.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1));
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
    
    public void setOnRefreshClick(Consumer<Void> onRefreshClick) {
        this.onRefreshClick = onRefreshClick;
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