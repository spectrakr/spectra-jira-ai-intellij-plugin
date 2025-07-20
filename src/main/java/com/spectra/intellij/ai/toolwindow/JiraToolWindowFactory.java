package com.spectra.intellij.ai.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class JiraToolWindowFactory implements ToolWindowFactory {
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JiraToolWindowContent toolWindowContent = new JiraToolWindowContent(project);
        Content content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}