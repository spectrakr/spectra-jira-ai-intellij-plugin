package com.spectra.intellij.ai.model;

public class AccessLogRequest {
    private String app;
    private String title;
    private String url;
    private String ui;

    public AccessLogRequest() {
    }

    public AccessLogRequest(String app, String title, String url, String ui) {
        this.app = app;
        this.title = title;
        this.url = url;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUi() {
        return ui;
    }

    public void setUi(String ui) {
        this.ui = ui;
    }
}