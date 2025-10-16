package com.spectra.intellij.ai.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
@State(name = "JiraSettings", storages = @Storage("jira-settings.xml"))
public final class JiraSettings implements PersistentStateComponent<JiraSettings> {
    
    public String jiraUrl = "https://enomix.atlassian.net/";
    public String username = "";
    public String apiToken = "";
    public String defaultBoardId = "";
    public String defaultProjectKey = "";

    // Default command constants
    private static final String DEFAULT_CLAUDE_COMMAND = "claude --dangerously-skip-permissions \"fix-issue-agent sub agent를 이용하여 \\\"$issueKey\\\" 이슈 처리해줘\"";
    private static final String DEFAULT_CODEX_COMMAND = "codex --yolo \"$prompt\"";
    private static final String DEFAULT_GEMINI_COMMAND = "gemini --yolo \"/fix-issue $issueKey\"";

    public String claudeCommand = DEFAULT_CLAUDE_COMMAND;
    public String codexCommand = DEFAULT_CODEX_COMMAND;
    public String geminiCommand = DEFAULT_GEMINI_COMMAND;

    public static JiraSettings getInstance() {
        return ApplicationManager.getApplication().getService(JiraSettings.class);
    }

    public static String getDefaultClaudeCommand() {
        return DEFAULT_CLAUDE_COMMAND;
    }

    public static String getDefaultCodexCommand() {
        return DEFAULT_CODEX_COMMAND;
    }

    public static String getDefaultGeminiCommand() {
        return DEFAULT_GEMINI_COMMAND;
    }
    
    @Override
    public @Nullable JiraSettings getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull JiraSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    public String getJiraUrl() {
        return jiraUrl;
    }
    
    public void setJiraUrl(String jiraUrl) {
        this.jiraUrl = jiraUrl;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getApiToken() {
        return apiToken;
    }
    
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
    
    public String getDefaultBoardId() {
        return defaultBoardId;
    }
    
    public String getDefaultProjectKey() {
        return defaultProjectKey;
    }
    
    public void setDefaultProjectKey(String defaultProjectKey) {
        this.defaultProjectKey = defaultProjectKey;
    }

    public String getClaudeCommand() {
        return claudeCommand;
    }

    public void setClaudeCommand(String claudeCommand) {
        this.claudeCommand = claudeCommand;
    }

    public String getCodexCommand() {
        return codexCommand;
    }

    public void setCodexCommand(String codexCommand) {
        this.codexCommand = codexCommand;
    }

    public String getGeminiCommand() {
        return geminiCommand;
    }

    public void setGeminiCommand(String geminiCommand) {
        this.geminiCommand = geminiCommand;
    }
}