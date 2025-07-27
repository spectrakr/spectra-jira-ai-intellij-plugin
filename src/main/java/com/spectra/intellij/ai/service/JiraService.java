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
        logRequest("GET", url);
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
                if ("closed".equals(sprintJson.get("state").getAsString())) {
                    continue;
                }
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
        String url = baseUrl + "rest/agile/" + AGILE_API_VERSION + "/sprint/" + sprintId + "/issue?maxResults=500";
        logRequest("GET", url);
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

        logRequest("POST", url, gson.toJson(issuePayload));
        
        Request request = buildRequest(url).newBuilder()
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

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

        logRequest("PUT", url, gson.toJson(payload));

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

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

    private String extractTextFromADF(JsonObject adfObject) {
        if (adfObject == null || !adfObject.has("content")) {
            return "";
        }
        
        StringBuilder text = new StringBuilder();
        JsonArray contentArray = adfObject.getAsJsonArray("content");
        
        for (int i = 0; i < contentArray.size(); i++) {
            JsonObject contentItem = contentArray.get(i).getAsJsonObject();
            extractTextFromContentItem(contentItem, text);
        }
        
        return text.toString().trim();
    }
    
    private void extractTextFromContentItem(JsonObject contentItem, StringBuilder text) {
        if (contentItem.has("type")) {
            String type = contentItem.get("type").getAsString();
            
            if ("text".equals(type) && contentItem.has("text")) {
                text.append(contentItem.get("text").getAsString());
            } else if (contentItem.has("content")) {
                // Recursively process nested content
                JsonArray nestedContent = contentItem.getAsJsonArray("content");
                for (int i = 0; i < nestedContent.size(); i++) {
                    extractTextFromContentItem(nestedContent.get(i).getAsJsonObject(), text);
                }
                
                // Add line breaks for block elements
                if ("paragraph".equals(type) || "heading".equals(type)) {
                    text.append("\n");
                }
            }
        }
    }

    private JiraIssue parseIssue(JsonObject issueJson) {
        JiraIssue issue = new JiraIssue();
        issue.setKey(issueJson.get("key").getAsString());
        
        JsonObject fields = issueJson.getAsJsonObject("fields");
        issue.setSummary(fields.get("summary").getAsString());
        
        if (fields.has("description") && !fields.get("description").isJsonNull()) {
            // Handle both string and ADF (Atlassian Document Format) descriptions
            try {
                // Try to get as string first (for simple text descriptions)
                issue.setDescription(fields.get("description").getAsString());
            } catch (UnsupportedOperationException | IllegalStateException e) {
                // If it's an ADF object, extract text content
                try {
                    JsonObject descriptionObj = fields.getAsJsonObject("description");
                    String extractedText = extractTextFromADF(descriptionObj);
                    issue.setDescription(extractedText);
                } catch (Exception ex) {
                    // If all parsing fails, set empty description
                    System.err.println("Failed to parse description: " + ex.getMessage());
                    issue.setDescription("");
                }
            }
        }
        
        if (fields.has("status")) {
            JsonObject status = fields.getAsJsonObject("status");
            issue.setStatus(status.get("name").getAsString());
        }
        
        if (fields.has("assignee") && !fields.get("assignee").isJsonNull()) {
            JsonObject assignee = fields.getAsJsonObject("assignee");
            issue.setAssignee(assignee.get("displayName").getAsString());
        }
        
        if (fields.has("creator") && !fields.get("creator").isJsonNull()) {
            JsonObject creator = fields.getAsJsonObject("creator");
            issue.setReporter(creator.get("displayName").getAsString());
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
        
        // Parse story points from customfield_10105
        if (fields.has("customfield_10105") && !fields.get("customfield_10105").isJsonNull()) {
            issue.setStoryPoints(fields.get("customfield_10105").getAsDouble());
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
    
    private void logRequest(String method, String url) {
        System.out.println("[HTTP Request]");
        System.out.println("[url] " + method + " " + url);
    }
    
    private void logRequest(String method, String url, String body) {
        System.out.println("[HTTP Request]");
        System.out.println("[url] " + method + " " + url);
        System.out.println("[body]");
        System.out.println(body);
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
        logRequest("GET", url);
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
        logRequest("GET", url);
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

    public CompletableFuture<List<JiraIssue>> getProjectIssuesAsync(String projectKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getProjectIssues(projectKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch project issues", e);
            }
        });
    }

    public List<JiraIssue> getProjectIssues(String projectKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/search";
        
        // JQL to get issues from specific project
        String jql = "project = \"" + projectKey + "\" ORDER BY updated DESC";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("jql", jql);
        requestBody.addProperty("maxResults", 500); // Limit to 50 issues
        requestBody.addProperty("startAt", 0);
        
        // Specify fields to retrieve
        JsonArray fields = new JsonArray();
        fields.add("key");
        fields.add("summary");
        fields.add("description");
        fields.add("status");
        fields.add("assignee");
        fields.add("creator");
        fields.add("priority");
        fields.add("issuetype");
        fields.add("updated");
        fields.add("customfield_10105"); // Story Points
        requestBody.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        logRequest("POST", url, gson.toJson(requestBody));
        
        Request request = buildRequest(url).newBuilder()
            .post(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get project issues: " + response.code());
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

    public CompletableFuture<Void> updateIssueAsync(JiraIssue issue) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssue(issue);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue", e);
            }
        });
    }

    public void updateIssue(JiraIssue issue) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issue.getKey();
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        // Update summary
        if (StringUtils.isNotBlank(issue.getSummary())) {
            fields.addProperty("summary", issue.getSummary());
        }
        
        // Update description using ADF format
        if (StringUtils.isNotBlank(issue.getDescription())) {
            JsonObject description = createADFDescription(issue.getDescription());
            fields.add("description", description);
        }
        
        // Update assignee
        if (issue.getAssignee() != null) {
            if (issue.getAssignee().isEmpty()) {
                // Unassign by setting assignee to null
                fields.add("assignee", null);
            } else {
                // Try to find user by display name first
                try {
                    String accountId = findUserAccountIdByDisplayName(issue.getAssignee());
                    if (accountId != null) {
                        JsonObject assignee = new JsonObject();
                        assignee.addProperty("accountId", accountId);
                        fields.add("assignee", assignee);
                    }
                } catch (IOException e) {
                    System.err.println("Failed to find user account ID for: " + issue.getAssignee());
                    // Fallback to displayName (might not work in some Jira instances)
                    JsonObject assignee = new JsonObject();
                    assignee.addProperty("displayName", issue.getAssignee());
                    fields.add("assignee", assignee);
                }
            }
        }
        
        // Update story points (custom field) - must be in fields object
        if (issue.getStoryPoints() != null) {
            fields.addProperty("customfield_10105", issue.getStoryPoints());
        } else {
            // Explicitly set to null to clear the field
            fields.add("customfield_10105", null);
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue: " + response.code() + " - " + responseBody);
            }
        }
        
        // Update status separately if changed
        if (StringUtils.isNotBlank(issue.getStatus())) {
            updateIssueStatus(issue.getKey(), issue.getStatus());
        }
    }

    public CompletableFuture<List<String>> getIssueStatusesAsync(String issueKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getIssueStatuses(issueKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch issue statuses", e);
            }
        });
    }

    public List<String> getIssueStatuses(String issueKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey + "/transitions";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get issue transitions: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray transitionsArray = responseJson.getAsJsonArray("transitions");
            
            List<String> statuses = new ArrayList<>();
            
            // Add current status first (get current issue status)
            JiraIssue currentIssue = getIssue(issueKey);
            if (currentIssue != null && StringUtils.isNotBlank(currentIssue.getStatus())) {
                statuses.add(currentIssue.getStatus());
            }
            
            // Add available transition statuses
            for (int i = 0; i < transitionsArray.size(); i++) {
                JsonObject transitionJson = transitionsArray.get(i).getAsJsonObject();
                JsonObject toStatus = transitionJson.getAsJsonObject("to");
                String statusName = toStatus.get("name").getAsString();
                if (!statuses.contains(statusName)) {
                    statuses.add(statusName);
                }
            }
            
            return statuses;
        }
    }

    public CompletableFuture<JiraIssue> getIssueAsync(String issueKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getIssue(issueKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch issue", e);
            }
        });
    }

    public JiraIssue getIssue(String issueKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get issue: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject issueJson = gson.fromJson(responseBody, JsonObject.class);
//            System.out.println("__ issueJson: " + issueJson);
            return parseIssue(issueJson);
        }
    }

    public CompletableFuture<List<String>> getProjectUsersAsync(String projectKey) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getProjectUsers(projectKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch project users", e);
            }
        });
    }

    public List<String> getProjectUsers(String projectKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/user/assignable/search?project=" + projectKey + "&maxResults=100";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get project users: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "[]";
            JsonArray usersArray = gson.fromJson(responseBody, JsonArray.class);
            
            List<String> users = new ArrayList<>();
            for (int i = 0; i < usersArray.size(); i++) {
                JsonObject userJson = usersArray.get(i).getAsJsonObject();
                if (userJson.has("displayName")) {
                    users.add(userJson.get("displayName").getAsString());
                }
            }
            
            return users;
        }
    }

    private String findUserAccountIdByDisplayName(String displayName) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/user/assignable/search?project=" + getProjectKey() + "&query=" + displayName + "&maxResults=50";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search for user: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "[]";
            JsonArray usersArray = gson.fromJson(responseBody, JsonArray.class);
            
            for (int i = 0; i < usersArray.size(); i++) {
                JsonObject userJson = usersArray.get(i).getAsJsonObject();
                if (userJson.has("displayName") && displayName.equals(userJson.get("displayName").getAsString())) {
                    return userJson.get("accountId").getAsString();
                }
            }
            
            return null; // User not found
        }
    }

    public CompletableFuture<JsonObject> getCurrentUserAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getCurrentUser();
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch current user", e);
            }
        });
    }

    public JsonObject getCurrentUser() throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/myself";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get current user: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    public CompletableFuture<List<JsonObject>> searchUsersAsync(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return searchUsers(query);
            } catch (IOException e) {
                throw new RuntimeException("Failed to search users", e);
            }
        });
    }

    public List<JsonObject> searchUsers(String query) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/user/assignable/search?project=" + getProjectKey() + "&query=" + query + "&maxResults=20";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search users: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "[]";
            JsonArray usersArray = gson.fromJson(responseBody, JsonArray.class);
            
            List<JsonObject> users = new ArrayList<>();
            for (int i = 0; i < usersArray.size(); i++) {
                JsonObject userJson = usersArray.get(i).getAsJsonObject();
                users.add(userJson);
            }
            
            return users;
        }
    }

    private void updateIssueStatus(String issueKey, String newStatus) throws IOException {
        // First get available transitions
        String transitionsUrl = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey + "/transitions";
        logRequest("GET", transitionsUrl);
        Request transitionsRequest = buildRequest(transitionsUrl);
        
        try (Response transitionsResponse = client.newCall(transitionsRequest).execute()) {
            if (!transitionsResponse.isSuccessful()) {
                throw new IOException("Failed to get transitions: " + transitionsResponse.code());
            }
            
            String responseBody = transitionsResponse.body() != null ? transitionsResponse.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray transitionsArray = responseJson.getAsJsonArray("transitions");
            
            // Find the transition ID for the target status
            String transitionId = null;
            for (int i = 0; i < transitionsArray.size(); i++) {
                JsonObject transitionJson = transitionsArray.get(i).getAsJsonObject();
                JsonObject toStatus = transitionJson.getAsJsonObject("to");
                if (newStatus.equals(toStatus.get("name").getAsString())) {
                    transitionId = transitionJson.get("id").getAsString();
                    break;
                }
            }
            
            if (transitionId != null) {
                // Execute the transition
                JsonObject transitionPayload = new JsonObject();
                JsonObject transition = new JsonObject();
                transition.addProperty("id", transitionId);
                transitionPayload.add("transition", transition);
                
                RequestBody body = RequestBody.create(
                    gson.toJson(transitionPayload),
                    MediaType.parse("application/json")
                );
                
                logRequest("POST", transitionsUrl, gson.toJson(transitionPayload));
                
                Request transitionRequest = buildRequest(transitionsUrl).newBuilder()
                    .post(body)
                    .build();
                
                try (Response transitionResponse = client.newCall(transitionRequest).execute()) {
                    if (!transitionResponse.isSuccessful()) {
                        String errorBody = transitionResponse.body() != null ? transitionResponse.body().string() : "";
                        throw new IOException("Failed to transition issue: " + transitionResponse.code() + " - " + errorBody);
                    }
                }
            }
        }
    }
}