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
@State(
    name = "RecentJiraSettings",
    storages = @Storage("recent-jira-settings.xml")
)
public final class RecentJiraSettings implements PersistentStateComponent<RecentJiraSettings> {
    
    public String lastUsedPriority = "";
    public String lastUsedSprintId = "";
    public String lastUsedSprintName = "";
    public String lastUsedIssueTypeId = "";
    public String lastUsedIssueTypeName = "";
    
    public static RecentJiraSettings getInstance() {
        return ApplicationManager.getApplication().getService(RecentJiraSettings.class);
    }
    
    @Override
    public @Nullable RecentJiraSettings getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull RecentJiraSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    public void updateRecentValues(String priority, String sprintId, String sprintName, 
                                   String issueTypeId, String issueTypeName) {
        this.lastUsedPriority = priority != null ? priority : "";
        this.lastUsedSprintId = sprintId != null ? sprintId : "";
        this.lastUsedSprintName = sprintName != null ? sprintName : "";
        this.lastUsedIssueTypeId = issueTypeId != null ? issueTypeId : "";
        this.lastUsedIssueTypeName = issueTypeName != null ? issueTypeName : "";
    }
    
    public String getLastUsedPriority() {
        return lastUsedPriority != null ? lastUsedPriority : "";
    }
    
    public String getLastUsedSprintId() {
        return lastUsedSprintId != null ? lastUsedSprintId : "";
    }
    
    public String getLastUsedSprintName() {
        return lastUsedSprintName != null ? lastUsedSprintName : "";
    }
    
    public String getLastUsedIssueTypeId() {
        return lastUsedIssueTypeId != null ? lastUsedIssueTypeId : "";
    }
    
    public String getLastUsedIssueTypeName() {
        return lastUsedIssueTypeName != null ? lastUsedIssueTypeName : "";
    }
}