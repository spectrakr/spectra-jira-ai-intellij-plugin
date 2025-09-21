package com.spectra.intellij.ai.model;

public class SimpleUserInfo {
    private String emailAddress;
    private String displayName;

    public SimpleUserInfo() {
    }

    public SimpleUserInfo(String emailAddress, String displayName) {
        this.emailAddress = emailAddress;
        this.displayName = displayName;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}