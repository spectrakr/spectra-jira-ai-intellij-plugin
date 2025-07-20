package com.spectra.intellij.ai.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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
            
            // Fill remaining space
            gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.weighty = 1.0;
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
    }
}