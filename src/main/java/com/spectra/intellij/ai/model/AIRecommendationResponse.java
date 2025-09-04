package com.spectra.intellij.ai.model;

import java.util.List;

public class AIRecommendationResponse {
    private String status;
    private AIResult ai_result;
    
    public AIRecommendationResponse() {}
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public AIResult getAi_result() {
        return ai_result;
    }
    
    public void setAi_result(AIResult ai_result) {
        this.ai_result = ai_result;
    }
    
    public static class AIResult {
        private List<RecommendedEpic> epics;
        
        public AIResult() {}
        
        public List<RecommendedEpic> getEpics() {
            return epics;
        }
        
        public void setEpics(List<RecommendedEpic> epics) {
            this.epics = epics;
        }
    }
    
    public static class RecommendedEpic {
        private String key;
        private String summary;
        
        public RecommendedEpic() {}
        
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
    }
}