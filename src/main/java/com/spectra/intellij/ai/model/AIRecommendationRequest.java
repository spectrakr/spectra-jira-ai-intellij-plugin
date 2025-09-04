package com.spectra.intellij.ai.model;

public class AIRecommendationRequest {
    private String summary;
    private String epic_list;
    
    public AIRecommendationRequest() {}
    
    public AIRecommendationRequest(String summary, String epic_list) {
        this.summary = summary;
        this.epic_list = epic_list;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public String getEpic_list() {
        return epic_list;
    }
    
    public void setEpic_list(String epic_list) {
        this.epic_list = epic_list;
    }
}