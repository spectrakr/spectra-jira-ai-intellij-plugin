package com.spectra.intellij.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class JiraService {
    private static final String JIRA_API_VERSION = "2";
    private static final String JIRA_API_VERSION_3 = "3";
    private static final String AGILE_API_VERSION = "1.0";
    
    private final OkHttpClient client;
    private final Gson gson;
    private String baseUrl;
    private String username;
    private String apiToken;
    private String projectKey;

    public JiraService() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    public void configure(String baseUrl, String username, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.username = username;
        this.apiToken = apiToken;
        this.projectKey = "PROJ"; // Default project key - should be configurable
    }
    
    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }
    
    public String getProjectKey() {
        return StringUtils.isNotBlank(this.projectKey) ? this.projectKey : "PROJ";
    }

    public CompletableFuture<List<JiraSprint>> getSprintsAsync(String boardId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getSprints(boardId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch sprints", e);
            }
        });
    }

    public List<JiraSprint> getSprints(String boardId) throws IOException {
        String url = baseUrl + "rest/agile/" + AGILE_API_VERSION + "/board/" + boardId + "/sprint";
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get sprints: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray sprintsArray = responseJson.getAsJsonArray("values");
            
            List<JiraSprint> sprints = new ArrayList<>();
            for (int i = 0; i < sprintsArray.size(); i++) {
                JsonObject sprintJson = sprintsArray.get(i).getAsJsonObject();
                JiraSprint sprint = new JiraSprint();
                sprint.setId(sprintJson.get("id").getAsString());
                sprint.setName(sprintJson.get("name").getAsString());
                sprint.setState(sprintJson.get("state").getAsString());
                sprint.setBoardId(boardId);
                sprints.add(sprint);
            }
            
            return sprints;
        }
    }

    public CompletableFuture<List<JiraIssue>> getSprintIssuesAsync(String sprintId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getSprintIssues(sprintId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch sprint issues", e);
            }
        });
    }

    public List<JiraIssue> getSprintIssues(String sprintId) throws IOException {
        String url = baseUrl + "rest/agile/" + AGILE_API_VERSION + "/sprint/" + sprintId + "/issue";
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get sprint issues: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray issuesArray = responseJson.getAsJsonArray("issues");
            
            List<JiraIssue> issues = new ArrayList<>();
            for (int i = 0; i < issuesArray.size(); i++) {
                JsonObject issueJson = issuesArray.get(i).getAsJsonObject();
                JiraIssue issue = parseIssue(issueJson);
                issues.add(issue);
            }
            
            return issues;
        }
    }

    public CompletableFuture<JiraIssue> createIssueAsync(JiraIssue issue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createIssue(issue);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create issue", e);
            }
        });
    }

    public JiraIssue createIssue(JiraIssue issue) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue";
        
        JsonObject issuePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        // Required fields: project, issuetype, summary
        fields.addProperty("summary", issue.getSummary());
        
        JsonObject project = new JsonObject();
        project.addProperty("key", getProjectKey());
        fields.add("project", project);
        
        JsonObject issuetype = new JsonObject();
        if (StringUtils.isNotBlank(issue.getIssueTypeId())) {
            issuetype.addProperty("id", issue.getIssueTypeId());
        } else {
            issuetype.addProperty("name", StringUtils.isNotBlank(issue.getIssueType()) ? issue.getIssueType() : "Task");
        }
        fields.add("issuetype", issuetype);
        
        // Optional fields - use ADF format for description in API v3
        if (StringUtils.isNotBlank(issue.getDescription())) {
            JsonObject description = createADFDescription(issue.getDescription());
            fields.add("description", description);
        }
        
        if (StringUtils.isNotBlank(issue.getPriority())) {
            JsonObject priority = new JsonObject();
            priority.addProperty("name", issue.getPriority());
            fields.add("priority", priority);
        }
        
        issuePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(issuePayload),
            MediaType.parse("application/json")
        );

        System.out.println("# createIssue");
        System.out.println("url : " + url);
        System.out.println("body : " + gson.toJson(issuePayload));
        
        // Debug: Print just the description field to verify ADF structure
//        if (issuePayload.getAsJsonObject("fields").has("description")) {
//            System.out.println("description ADF : " + gson.toJson(issuePayload.getAsJsonObject("fields").get("description")));
//        }

        Request request = buildRequest(url).newBuilder()
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("Response code: " + response.code());
            System.out.println("Response body: " + responseBody);
            
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create issue: " + response.code() + " - " + responseBody);
            }
            
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            issue.setKey(responseJson.get("key").getAsString());
            
            // If sprint is specified, try to add the issue to the sprint
            // Don't fail the entire operation if sprint assignment fails
            if (StringUtils.isNotBlank(issue.getSprintId())) {
                try {
                    addIssueToSprint(issue.getKey(), issue.getSprintId());
                    System.out.println("Successfully ranked issue " + issue.getKey() + " in sprint " + issue.getSprintId());
                } catch (IOException e) {
                    System.err.println("Warning: Failed to rank issue " + issue.getKey() + " in sprint " + issue.getSprintId() + ": " + e.getMessage());
                    System.err.println("Issue was created successfully, but sprint ranking failed. You can manually assign it to the sprint in Jira.");
                    // Don't re-throw the exception - issue creation was successful
                }
            }
            
            return issue;
        }
    }

    private void addIssueToSprint(String issueKey, String sprintId) throws IOException {
        String url = baseUrl + "rest/greenhopper/1.0/sprint/rank";
        
        JsonObject payload = new JsonObject();
        JsonArray idOrKeys = new JsonArray();
        idOrKeys.add(issueKey);
        payload.add("idOrKeys", idOrKeys);
        payload.addProperty("sprintId", Integer.parseInt(sprintId));
        
        RequestBody body = RequestBody.create(
            gson.toJson(payload),
            MediaType.parse("application/json")
        );

        System.out.println("# addIssueToSprint (rank API)");
        System.out.println("url : " + url);
        System.out.println("payload : " + gson.toJson(payload));
        
        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            System.out.println("Sprint rank response code: " + response.code());
            System.out.println("Sprint rank response body: " + responseBody);
            
            if (!response.isSuccessful()) {
                throw new IOException("Failed to rank issue in sprint: " + response.code() + " - " + responseBody);
            }
        }
    }

    private JsonObject createADFDescription(String text) {
        // Following the exact structure from Jira official Java documentation:
        // ObjectNode description = fields.putObject("description");
        // {
        //   ArrayNode content = description.putArray("content");
        //   ObjectNode content0 = content.addObject();
        //   {
        //     ArrayNode content = content0.putArray("content");
        //     ObjectNode content0 = content.addObject();
        //     {
        //       content0.put("text", "Order entry fails when selecting supplier.");
        //       content0.put("type", "text");
        //     }
        //     content0.put("type", "paragraph");
        //   }
        //   description.put("type", "doc");
        //   description.put("version", 1);
        // }
        
        JsonObject description = new JsonObject();
        
        // Create content array
        JsonArray content = new JsonArray();
        JsonObject paragraph = new JsonObject();
        
        // Create paragraph content array
        JsonArray paragraphContent = new JsonArray();
        JsonObject textNode = new JsonObject();
        textNode.addProperty("text", text);
        textNode.addProperty("type", "text");
        paragraphContent.add(textNode);
        
        // Set paragraph properties
        paragraph.add("content", paragraphContent);
        paragraph.addProperty("type", "paragraph");
        
        // Add paragraph to content array
        content.add(paragraph);
        
        // Set description properties
        description.add("content", content);
        description.addProperty("type", "doc");
        description.addProperty("version", 1);
        
        return description;
    }

    private JiraIssue parseIssue(JsonObject issueJson) {
        JiraIssue issue = new JiraIssue();
        issue.setKey(issueJson.get("key").getAsString());
        
        JsonObject fields = issueJson.getAsJsonObject("fields");
        issue.setSummary(fields.get("summary").getAsString());
        
        if (fields.has("description") && !fields.get("description").isJsonNull()) {
            issue.setDescription(fields.get("description").getAsString());
        }
        
        if (fields.has("status")) {
            JsonObject status = fields.getAsJsonObject("status");
            issue.setStatus(status.get("name").getAsString());
        }
        
        if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
            JsonObject assignee = fields.getAsJsonObject("assignee");
            issue.setAssignee(assignee.get("displayName").getAsString());
        }
        
        if (fields.has("priority")) {
            JsonObject priority = fields.getAsJsonObject("priority");
            issue.setPriority(priority.get("name").getAsString());
        }
        
        if (fields.has("issuetype")) {
            JsonObject issuetype = fields.getAsJsonObject("issuetype");
            issue.setIssueType(issuetype.get("name").getAsString());
            issue.setIssueTypeId(issuetype.get("id").getAsString());
        }
        
        return issue;
    }

    private Request buildRequest(String url) {
        return new Request.Builder()
            .url(url)
            .addHeader("Authorization", Credentials.basic(username, apiToken))
            .addHeader("Content-Type", "application/json")
            .build();
    }

    public CompletableFuture<Map<String, String>> getIssueTypesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getIssueTypes();
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch issue types", e);
            }
        });
    }

    public Map<String, String> getIssueTypes() throws IOException {
        // First get project ID from project key
        String projectId = getProjectId(getProjectKey());
        
        // Then get issue types for that specific project
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issuetype/project?projectId=" + projectId;
        Request request = buildRequest(url);

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get issue types: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "[]";
            JsonArray issueTypesArray = gson.fromJson(responseBody, JsonArray.class);
            Map<String, String> issueTypes = new HashMap<>();
            
            for (int i = 0; i < issueTypesArray.size(); i++) {
                JsonObject issueTypeJson = issueTypesArray.get(i).getAsJsonObject();
                
                // Filter by hierarchyLevel = 0 (top-level issue types)
                if (issueTypeJson.has("hierarchyLevel") && 
                    issueTypeJson.get("hierarchyLevel").getAsInt() == 0) {
                    String id = issueTypeJson.get("id").getAsString();
                    String name = issueTypeJson.get("name").getAsString();
                    issueTypes.put(name, id); // name -> id mapping for UI display
                }
            }
            
            return issueTypes;
        }
    }

    public CompletableFuture<String> getProjectIdAsync(String projectKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getProjectId(projectKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch project ID", e);
            }
        });
    }

    public String getProjectId(String projectKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/project/" + projectKey;
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get project: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject projectJson = gson.fromJson(responseBody, JsonObject.class);
            return projectJson.get("id").getAsString();
        }
    }

    public boolean isConfigured() {
        return StringUtils.isNotBlank(baseUrl) && 
               StringUtils.isNotBlank(username) && 
               StringUtils.isNotBlank(apiToken);
    }
}