package com.spectra.intellij.ai.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class IssueTypeIcons {
    
    private static final Icon STORY_ICON = IconLoader.getIcon("/icons/jira_issue_story.svg", IssueTypeIcons.class);
    private static final Icon BUG_ICON = IconLoader.getIcon("/icons/jira_issue_bug.svg", IssueTypeIcons.class);
    private static final Icon TASK_ICON = IconLoader.getIcon("/icons/jira_issue_task.svg", IssueTypeIcons.class);
    
    public static Icon getIconForIssueType(String issueType) {
        if (issueType == null || issueType.isEmpty()) {
            return TASK_ICON;
        }
        
        String lowerType = issueType.toLowerCase();
        if (lowerType.contains("스토리")) {
            return STORY_ICON;
        } else if (lowerType.contains("버그")) {
            return BUG_ICON;
        } else {
            return TASK_ICON;
        }
    }
}
