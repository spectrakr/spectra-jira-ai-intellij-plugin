package com.spectra.intellij.ai.model;

public class JiraEpic {
    private String key;
    private String summary;
    private String name;
    private String color;
    private boolean done;

    public JiraEpic() {}

    public JiraEpic(String key, String summary, String name) {
        this.key = key;
        this.summary = summary;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    @Override
    public String toString() {
        return key + " - " + summary;
    }
}