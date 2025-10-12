package com.spectra.intellij.ai.settings;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.spectra.intellij.ai.service.AccessLogService;
import com.spectra.intellij.ai.service.JiraService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.InputStream;
import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
               !settingsPanel.getDefaultProjectKey().equals(settings.getDefaultProjectKey());
    }
    
    @Override
    public void apply() throws ConfigurationException {
        if (settingsPanel != null) {
            JiraSettings settings = JiraSettings.getInstance();
            settings.setJiraUrl(settingsPanel.getJiraUrl());
            settings.setUsername(settingsPanel.getUsername());
            settings.setApiToken(settingsPanel.getApiToken());
            settings.setDefaultProjectKey(settingsPanel.getDefaultProjectKey());

            // Send access log for settings confirmation
            try {
                JiraService jiraService = new JiraService();
                jiraService.configure(settings.getJiraUrl(), settings.getUsername(), settings.getApiToken());
            } catch (Exception e) {
                System.err.println("Failed to send access log for settings confirmation: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void reset() {
        if (settingsPanel != null) {
            JiraSettings settings = JiraSettings.getInstance();
            settingsPanel.setJiraUrl(settings.getJiraUrl());
            settingsPanel.setUsername(settings.getUsername());
            settingsPanel.setApiToken(settings.getApiToken());
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
            gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Jira URL:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            jiraUrlField = new JTextField(30); // Set preferred width
            panel.add(jiraUrlField, gbc);
            
            // Email
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Email:"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            usernameField = new JTextField(30); // Set preferred width
            panel.add(usernameField, gbc);
            
            // API Token
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("API Token:"), gbc);
            
            // API Token field and button panel
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            JPanel apiTokenPanel = new JPanel(new BorderLayout(5, 0));
            apiTokenField = new JPasswordField(30); // Set preferred width
            
            // Button panel for token actions
            JPanel tokenButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            JButton tokenLookupButton = new JButton("토큰 조회");
            tokenLookupButton.addActionListener(e -> openTokenUrl());
            JButton tokenValidateButton = new JButton("토큰 확인");
            tokenValidateButton.addActionListener(e -> validateToken());
            tokenButtonsPanel.add(tokenValidateButton);
            tokenButtonsPanel.add(tokenLookupButton);
            
            apiTokenPanel.add(apiTokenField, BorderLayout.CENTER);
            apiTokenPanel.add(tokenButtonsPanel, BorderLayout.EAST);
            panel.add(apiTokenPanel, gbc);
            
            // Default Project Key
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Project ID:"), gbc);
            
            // Project ID field and example panel
            gbc.gridx = 1; gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            JPanel projectPanel = new JPanel(new BorderLayout(5, 0));
            defaultProjectKeyField = new JTextField();
            defaultProjectKeyField.setColumns(10); // Reduce text field length
            JLabel exampleLabel = new JLabel("예) AVGRS");
            exampleLabel.setForeground(Color.GRAY);
            projectPanel.add(defaultProjectKeyField, BorderLayout.WEST);
            projectPanel.add(exampleLabel, BorderLayout.CENTER);
            panel.add(projectPanel, gbc);

            // Buttons Panel
            gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.weighty = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            
            JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
            
            JButton checkVersionButton = new JButton("Check Version");
            checkVersionButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showVersionInfo();
                }
            });
            
            JButton setupAutoUpdateButton = new JButton("자동 업데이트 설정");
            setupAutoUpdateButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showAutoUpdateGuide();
                }
            });
            
            buttonsPanel.add(checkVersionButton);
            buttonsPanel.add(setupAutoUpdateButton);
            panel.add(buttonsPanel, gbc);
            
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
        
        private void openTokenUrl() {
            try {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI("https://id.atlassian.com/manage-profile/security/api-tokens"));
                } else {
                    Messages.showInfoMessage(
                        "브라우저를 자동으로 열 수 없습니다.\n\n다음 URL을 수동으로 방문하세요:\nhttps://id.atlassian.com/manage-profile/security/api-tokens",
                        "API 토큰 생성"
                    );
                }
            } catch (Exception e) {
                Messages.showErrorDialog(
                    "브라우저를 열 수 없습니다.\n\n다음 URL을 수동으로 방문하세요:\nhttps://id.atlassian.com/manage-profile/security/api-tokens",
                    "오류"
                );
            }
        }

        private void validateToken() {
            String jiraUrl = jiraUrlField.getText().trim();
            String username = usernameField.getText().trim();
            String apiToken = new String(apiTokenField.getPassword()).trim();

            if (jiraUrl.isEmpty() || username.isEmpty() || apiToken.isEmpty()) {
                Messages.showWarningDialog(
                    "Jira URL, Email, API Token을 모두 입력해주세요.",
                    "입력 확인"
                );
                return;
            }

            // Show progress and validate token in background thread
            JiraService jiraService = new JiraService();
            jiraService.configure(jiraUrl, username, apiToken);

            jiraService.getCurrentUserAsync()
                .thenAccept(userJson -> {
                    SwingUtilities.invokeLater(() -> {
                        String displayName = userJson.has("displayName") ? 
                            userJson.get("displayName").getAsString() : "Unknown";
                        String emailAddress = userJson.has("emailAddress") ? 
                            userJson.get("emailAddress").getAsString() : "Unknown";
                        
                        Messages.showInfoMessage(
                            "토큰이 유효합니다!\n\n" +
                            "사용자: " + displayName + "\n" +
                            "이메일: " + emailAddress,
                            "토큰 확인 성공"
                        );
                    });
                })
                .exceptionally(throwable -> {
                    SwingUtilities.invokeLater(() -> {
                        String errorMessage = "토큰이 유효하지 않습니다.\n\n";
                        if (throwable.getCause() != null) {
                            errorMessage += "오류: " + throwable.getCause().getMessage();
                        } else {
                            errorMessage += "Jira URL, Email, API Token을 확인해주세요.";
                        }
                        
                        Messages.showErrorDialog(errorMessage, "토큰 확인 실패");
                    });
                    return null;
                });
        }

        private void showAutoUpdateGuide() {
            String repositoryUrl = "https://spectra-team.github.io/spectra-jira-ai-intellij-plugin/updatePlugins.xml";

            StringBuilder message = new StringBuilder();
            message.append("자동 업데이트를 설정하려면 다음 단계를 따르세요:\n\n");
            message.append("1. File → Settings → Plugins로 이동\n");
            message.append("2. ⚙️ 톱니바퀴 아이콘 클릭 → 'Manage Plugin Repositories' 선택\n");
            message.append("3. '+' 버튼을 클릭하고 다음 URL을 추가:\n\n");
            message.append(repositoryUrl).append("\n\n");
            message.append("4. 'OK' 버튼 클릭\n");
            message.append("5. 이제 새 버전이 출시되면 자동으로 알림을 받게 됩니다!\n\n");
            message.append("💡 URL이 클립보드에 복사되었습니다.");

            // Copy URL to clipboard
            try {
                java.awt.Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(repositoryUrl), null);
            } catch (Exception e) {
                // Ignore clipboard errors
            }

            Messages.showInfoMessage(
                message.toString(),
                "자동 업데이트 설정 가이드"
            );
        }

    }
}