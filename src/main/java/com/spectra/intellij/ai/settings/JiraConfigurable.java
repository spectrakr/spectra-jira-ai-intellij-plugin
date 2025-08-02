package com.spectra.intellij.ai.settings;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class JiraConfigurable implements Configurable {
    
    private JiraSettingsPanel settingsPanel;
    
    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Spectra Jira Settings";
    }
    
    @Override
    public @Nullable JComponent createComponent() {
        settingsPanel = new JiraSettingsPanel();
        reset(); // Load saved settings when creating the component
        return settingsPanel.getPanel();
    }
    
    @Override
    public boolean isModified() {
        if (settingsPanel == null) {
            return false;
        }
        
        JiraSettings settings = JiraSettings.getInstance();
        return !settingsPanel.getJiraUrl().equals(settings.getJiraUrl()) ||
               !settingsPanel.getUsername().equals(settings.getUsername()) ||
               !settingsPanel.getApiToken().equals(settings.getApiToken()) ||
               !settingsPanel.getDefaultBoardId().equals(settings.getDefaultBoardId()) ||
               !settingsPanel.getDefaultProjectKey().equals(settings.getDefaultProjectKey());
    }
    
    @Override
    public void apply() throws ConfigurationException {
        if (settingsPanel != null) {
            JiraSettings settings = JiraSettings.getInstance();
            settings.setJiraUrl(settingsPanel.getJiraUrl());
            settings.setUsername(settingsPanel.getUsername());
            settings.setApiToken(settingsPanel.getApiToken());
            settings.setDefaultBoardId(settingsPanel.getDefaultBoardId());
            settings.setDefaultProjectKey(settingsPanel.getDefaultProjectKey());
        }
    }
    
    @Override
    public void reset() {
        if (settingsPanel != null) {
            JiraSettings settings = JiraSettings.getInstance();
            settingsPanel.setJiraUrl(settings.getJiraUrl());
            settingsPanel.setUsername(settings.getUsername());
            settingsPanel.setApiToken(settings.getApiToken());
            settingsPanel.setDefaultBoardId(settings.getDefaultBoardId());
            settingsPanel.setDefaultProjectKey(settings.getDefaultProjectKey());
        }
    }
    
    @Override
    public void disposeUIResources() {
        settingsPanel = null;
    }
    
    private static class JiraSettingsPanel {
        private JPanel panel;
        private JTextField jiraUrlField;
        private JTextField usernameField;
        private JPasswordField apiTokenField;
        private JTextField defaultBoardIdField;
        private JTextField defaultProjectKeyField;
        
        public JiraSettingsPanel() {
            createPanel();
        }
        
        private void createPanel() {
            panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            // Jira URL
            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new JLabel("Jira URL:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            jiraUrlField = new JTextField();
            panel.add(jiraUrlField, gbc);
            
            // Username
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Username:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            usernameField = new JTextField();
            panel.add(usernameField, gbc);
            
            // API Token
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("API Token:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            apiTokenField = new JPasswordField();
            panel.add(apiTokenField, gbc);
            
            // Default Board ID
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Default Board ID:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            defaultBoardIdField = new JTextField();
            panel.add(defaultBoardIdField, gbc);
            
            // Default Project Key
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Default Project Key:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            defaultProjectKeyField = new JTextField();
            panel.add(defaultProjectKeyField, gbc);
            
            // Check Version Button
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            JButton checkVersionButton = new JButton("Check Version");
            checkVersionButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showVersionInfo();
                }
            });
            panel.add(checkVersionButton, gbc);
            
            // Fill remaining space
            gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.weighty = 1.0;
            panel.add(new JPanel(), gbc);
        }
        
        public JPanel getPanel() {
            return panel;
        }
        
        public String getJiraUrl() {
            return jiraUrlField.getText().trim();
        }
        
        public void setJiraUrl(String jiraUrl) {
            jiraUrlField.setText(jiraUrl);
        }
        
        public String getUsername() {
            return usernameField.getText().trim();
        }
        
        public void setUsername(String username) {
            usernameField.setText(username);
        }
        
        public String getApiToken() {
            return new String(apiTokenField.getPassword());
        }
        
        public void setApiToken(String apiToken) {
            apiTokenField.setText(apiToken);
        }
        
        public String getDefaultBoardId() {
            return defaultBoardIdField.getText().trim();
        }
        
        public void setDefaultBoardId(String defaultBoardId) {
            defaultBoardIdField.setText(defaultBoardId);
        }
        
        public String getDefaultProjectKey() {
            return defaultProjectKeyField.getText().trim();
        }
        
        public void setDefaultProjectKey(String defaultProjectKey) {
            defaultProjectKeyField.setText(defaultProjectKey);
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
                // Try to get version from properties file
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("META-INF/plugin.xml");
                if (inputStream != null) {
                    // This is a fallback - we would need to parse XML for actual version
                    // For now, return the build.gradle version
                    return "1.0.2";
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
}