package com.spectra.intellij.ai.model;

public class AccessLogRequest {
    private String app;
    private String title;
    private String content;
    private String ui;

    public AccessLogRequest() {
    }

    public AccessLogRequest(String app, String title, String content, String ui) {
        this.app = app;
        this.title = title;
        this.content = content;
        this.ui = ui;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUi() {
        return ui;
    }

    public void setUi(String ui) {
        this.ui = ui;
    }
}