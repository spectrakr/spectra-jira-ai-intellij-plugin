package com.spectra.intellij.ai.toolwindow.handlers;

import com.spectra.intellij.ai.model.JiraIssue;

public interface InlineEditHandler {
    void enterEditMode();
    void exitEditMode(boolean saveChanges);
    void setDisplayMode();
    void setCurrentIssue(JiraIssue issue);
    void setEnabled(boolean enabled);
}