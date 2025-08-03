package com.spectra.intellij.ai.model;

public class JiraIssue {
    private String key;
    private String summary;
    private String description;
    private String status;
    private String assignee;
    private String reporter;
    private String priority;
    private String issueType;
    private String issueTypeId;
    private String sprintId;
    private String sprintName;
    private String epicKey;
    private String epicName;
    private Double storyPoints;
    private String parentKey;
    private String parentSummary;
    private String epicColor;
    private String priorityIconUrl;

    public JiraIssue() {}

    public JiraIssue(String key, String summary, String description) {
        this.key = key;
        this.summary = summary;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getIssueTypeId() {
        return issueTypeId;
    }

    public void setIssueTypeId(String issueTypeId) {
        this.issueTypeId = issueTypeId;
    }

    public String getSprintId() {
        return sprintId;
    }

    public void setSprintId(String sprintId) {
        this.sprintId = sprintId;
    }

    public String getSprintName() {
        return sprintName;
    }

    public void setSprintName(String sprintName) {
        this.sprintName = sprintName;
    }

    public String getEpicKey() {
        return epicKey;
    }

    public void setEpicKey(String epicKey) {
        this.epicKey = epicKey;
    }

    public String getEpicName() {
        return epicName;
    }

    public void setEpicName(String epicName) {
        this.epicName = epicName;
    }

    public Double getStoryPoints() {
        return storyPoints;
    }

    public void setStoryPoints(Double storyPoints) {
        this.storyPoints = storyPoints;
    }

    public String getParentKey() {
        return parentKey;
    }

    public void setParentKey(String parentKey) {
        this.parentKey = parentKey;
    }

    public String getParentSummary() {
        return parentSummary;
    }

    public void setParentSummary(String parentSummary) {
        this.parentSummary = parentSummary;
    }

    public String getEpicColor() {
        return epicColor;
    }

    public void setEpicColor(String epicColor) {
        this.epicColor = epicColor;
    }

    public String getPriorityIconUrl() {
        return priorityIconUrl;
    }

    public void setPriorityIconUrl(String priorityIconUrl) {
        this.priorityIconUrl = priorityIconUrl;
    }

    @Override
    public String toString() {
        return key + " - " + summary;
    }
}