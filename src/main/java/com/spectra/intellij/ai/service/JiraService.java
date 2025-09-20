package com.spectra.intellij.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.spectra.intellij.ai.model.JiraIssue;
import com.spectra.intellij.ai.model.JiraSprint;
import com.spectra.intellij.ai.model.JiraEpic;
import com.spectra.intellij.ai.model.AIRecommendationRequest;
import com.spectra.intellij.ai.model.AIRecommendationResponse;
import com.spectra.intellij.ai.model.AccessLogRequest;
import com.spectra.intellij.ai.model.SimpleUserInfo;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class JiraService {
    private static final String JIRA_API_VERSION = "2";
    private static final String JIRA_API_VERSION_3 = "3";
    private static final String AGILE_API_VERSION = "1.0";
    
    // Jira Custom Fields
    private static final String CUSTOMFIELD_EPIC_COLOR = "customfield_10013";
    private static final String CUSTOMFIELD_EPIC_LINK = "customfield_10014"; 
    private static final String CUSTOMFIELD_STORY_POINTS = "customfield_10105";
    private static final String CUSTOMFIELD_STORY_POINTS_ESTIMATE = "customfield_10016";
    
    // Project-specific constants - projects that use CUSTOMFIELD_STORY_POINTS_ESTIMATE
    private static final String[] PROJECTS_USING_STORY_POINTS_ESTIMATE = {"DWFLOW"};

    private final OkHttpClient client;
    private final Gson gson;
    private String baseUrl;
    private String username;
    private String apiToken;
    private String projectKey;
    
    // Epic color cache - key: epicKey, value: CacheEntry
    private final Map<String, EpicColorCacheEntry> epicColorCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes TTL
    
    private static class EpicColorCacheEntry {
        final String color;
        final long timestamp;
        
        EpicColorCacheEntry(String color) {
            this.color = color;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

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
    
    /**
     * Returns the appropriate story points custom field based on project key
     * @return the custom field ID for story points
     */
    private String getStoryPointsField() {
        String currentProjectKey = getProjectKey();
        for (String projectKey : PROJECTS_USING_STORY_POINTS_ESTIMATE) {
            if (projectKey.equals(currentProjectKey)) {
                return CUSTOMFIELD_STORY_POINTS_ESTIMATE;
            }
        }
        return CUSTOMFIELD_STORY_POINTS;
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
    
    public CompletableFuture<List<JiraSprint>> getSprintsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getSprintsFromProjectBoards();
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch sprints from project boards", e);
            }
        });
    }

    public CompletableFuture<List<JiraIssue>> getEpicsAsync(String boardId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getEpics(boardId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch epics", e);
            }
        });
    }

    public CompletableFuture<List<JiraEpic>> getEpicListAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getEpicList();
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch epics", e);
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
    
    public List<JiraSprint> getSprintsFromProjectBoards() throws IOException {
        // First get all boards for the project
        List<String> boardIds = getProjectBoardIds();
        
        List<JiraSprint> allSprints = new ArrayList<>();
        for (String boardId : boardIds) {
            try {
                List<JiraSprint> boardSprints = getSprints(boardId);
                allSprints.addAll(boardSprints);
            } catch (IOException e) {
                // Log and continue with other boards
                System.err.println("Failed to get sprints for board " + boardId + ": " + e.getMessage());
            }
        }
        
        return allSprints;
    }
    
    public List<String> getProjectBoardIds() throws IOException {
        String url = baseUrl + "rest/agile/" + AGILE_API_VERSION + "/board?projectKeyOrId=" + getProjectKey();
        logRequest("GET", url);
        sendAccessLog("스프린트 목록 조회", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get project boards: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray boardsArray = responseJson.getAsJsonArray("values");
            
            List<String> boardIds = new ArrayList<>();
            if (boardsArray != null) {
                for (int i = 0; i < boardsArray.size(); i++) {
                    JsonObject boardJson = boardsArray.get(i).getAsJsonObject();
                    String boardId = boardJson.get("id").getAsString();
                    boardIds.add(boardId);
                }
            }
            
            return boardIds;
        }
    }

    public List<JiraIssue> getEpics(String boardId) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/search";
        
        // JQL to get Epics from project
        String jql = "project = " + getProjectKey() + " AND issuetype = Epic ORDER BY updated DESC";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("jql", jql);
        requestBody.addProperty("startAt", 0);
        requestBody.addProperty("maxResults", 50);
        
        // Specify fields to retrieve
        JsonArray fields = new JsonArray();
        fields.add("summary");
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
                throw new IOException("Failed to get epics: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray epicsArray = responseJson.getAsJsonArray("issues");
            
            List<JiraIssue> epics = new ArrayList<>();
            for (int i = 0; i < epicsArray.size(); i++) {
                JsonObject epicJson = epicsArray.get(i).getAsJsonObject();
                JiraIssue epic = new JiraIssue();
                epic.setKey(epicJson.get("key").getAsString());
                
                JsonObject fieldsObj = epicJson.getAsJsonObject("fields");
                epic.setSummary(fieldsObj.get("summary").getAsString());
                epic.setIssueType("Epic");
                
                epics.add(epic);
            }
            
            return epics;
        }
    }

    public List<JiraEpic> getEpicList() throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/search/jql";
        
        // JQL to get Epics from project
        String jql = "project = " + getProjectKey() + " AND issuetype = Epic ORDER BY updated DESC";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("jql", jql);
        requestBody.addProperty("maxResults", 50);
        
        // Specify fields to retrieve
        JsonArray fields = new JsonArray();
        fields.add("summary");
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
                throw new IOException("Failed to get epics: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            JsonArray epicsArray = responseJson.getAsJsonArray("issues");
            
            List<JiraEpic> epics = new ArrayList<>();
            for (int i = 0; i < epicsArray.size(); i++) {
                JsonObject epicJson = epicsArray.get(i).getAsJsonObject();
                JiraEpic epic = new JiraEpic();
                epic.setKey(epicJson.get("key").getAsString());
                
                JsonObject fieldsObj = epicJson.getAsJsonObject("fields");
                epic.setSummary(fieldsObj.get("summary").getAsString());
                epic.setName(fieldsObj.get("summary").getAsString());
                
                epics.add(epic);
            }
            
            return epics;
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
        String url = baseUrl + "rest/agile/" + AGILE_API_VERSION + "/sprint/" + sprintId + "/issue?maxResults=500&expand=renderedFields";
        logRequest("GET", url);
        
        // Send access log asynchronously
        sendAccessLog("이슈 조회", url);
        
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
                JiraIssue issue = parseIssueForList(issueJson);
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

        // Add assignee if specified
        if (StringUtils.isNotBlank(issue.getAssignee())) {
            try {
                String accountId = findUserAccountIdByDisplayName(issue.getAssignee());
                if (accountId != null) {
                    JsonObject assignee = new JsonObject();
                    assignee.addProperty("accountId", accountId);
                    fields.add("assignee", assignee);
                }
            } catch (IOException e) {
                System.err.println("Warning: Failed to find user account ID for assignee: " + issue.getAssignee());
                // Don't fail the issue creation if assignee lookup fails
            }
        }

        // Add epic link if specified
        if (StringUtils.isNotBlank(issue.getEpicKey())) {
            fields.addProperty(CUSTOMFIELD_EPIC_LINK, issue.getEpicKey()); // Epic Link field
        }

        // Add story points if specified
        if (issue.getStoryPoints() != null) {
            fields.addProperty(getStoryPointsField(), issue.getStoryPoints()); // Story Points field
        }
        
        issuePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(issuePayload),
            MediaType.parse("application/json")
        );

        logRequest("POST", url, gson.toJson(issuePayload));
        sendAccessLog("이슈 생성", url);
        
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
            System.out.println("___ priority : " + priority);
            issue.setPriority(priority.get("name").getAsString());
        }
        
        if (fields.has("issuetype")) {
            JsonObject issuetype = fields.getAsJsonObject("issuetype");
            issue.setIssueType(issuetype.get("name").getAsString());
            issue.setIssueTypeId(issuetype.get("id").getAsString());
        }
        
        // Parse story points from appropriate custom field based on project
        String storyPointsField = getStoryPointsField();
        if (fields.has(storyPointsField) && !fields.get(storyPointsField).isJsonNull()) {
            issue.setStoryPoints(fields.get(storyPointsField).getAsDouble());
        }
        
        // Parse parent (Epic) information
        if (fields.has("parent") && !fields.get("parent").isJsonNull()) {
            JsonObject parent = fields.getAsJsonObject("parent");
            issue.setParentKey(parent.get("key").getAsString());
            JsonObject parentFields = parent.getAsJsonObject("fields");
            if (parentFields.has("summary")) {
                issue.setParentSummary(parentFields.get("summary").getAsString());
            }
            // Get Epic color by making a separate API call to get renderedFields
            try {
                String epicColor = getEpicColor(parent.get("key").getAsString());
                if (epicColor != null) {
                    issue.setEpicColor(epicColor);
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch Epic color for " + parent.get("key").getAsString() + ": " + e.getMessage());
            }
        }
        
        // Also check for Epic Link (customfield_10014) for issues linked to Epics
        if (fields.has(CUSTOMFIELD_EPIC_LINK) && !fields.get(CUSTOMFIELD_EPIC_LINK).isJsonNull()) {
            // Epic Link field contains the Epic key
            String epicKey = fields.get(CUSTOMFIELD_EPIC_LINK).getAsString();
            if (issue.getParentKey() == null) { // Only set if parent wasn't already found
                issue.setParentKey(epicKey);
                // We'll need to fetch the Epic details separately for summary and color
                try {
                    JiraIssue epicIssue = getIssue(epicKey);
                    if (epicIssue != null) {
                        issue.setParentSummary(epicIssue.getSummary());
                        // Get Epic color by making a separate API call
                        try {
                            String epicColor = getEpicColor(epicKey);
                            if (epicColor != null) {
                                issue.setEpicColor(epicColor);
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to fetch Epic color for " + epicKey + ": " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // If we can't fetch Epic details, just use the key
                    System.err.println("Failed to fetch Epic details for " + epicKey + ": " + e.getMessage());
                }
            }
        }
        
        // Parse priority iconUrl and Epic color from renderedFields (if available)
        if (issueJson.has("renderedFields")) {
            JsonObject renderedFields = issueJson.getAsJsonObject("renderedFields");
            
            // Parse priority iconUrl from renderedFields
            if (renderedFields.has("priority") && !renderedFields.get("priority").isJsonNull()) {
                JsonObject renderedPriority = renderedFields.getAsJsonObject("priority");
                if (renderedPriority.has("iconUrl")) {
                    issue.setPriorityIconUrl(renderedPriority.get("iconUrl").getAsString());
                }
            }
            
            // For Epic issues, try to get Epic color from renderedFields
            if ("Epic".equals(issue.getIssueType())) {
                if (renderedFields.has(CUSTOMFIELD_EPIC_COLOR) && !renderedFields.get(CUSTOMFIELD_EPIC_COLOR).isJsonNull()) {
                    String epicColorCode = renderedFields.get(CUSTOMFIELD_EPIC_COLOR).getAsString();
                    String hexColor = mapGhxLabelToHex(epicColorCode);
                    issue.setEpicColor(hexColor);
                }
            }
        } else {
            // For Epic issues, make a separate API call to get the epic color if renderedFields not available
            if ("Epic".equals(issue.getIssueType())) {
                try {
                    String epicColor = getEpicColor(issue.getKey());
                    if (epicColor != null) {
                        issue.setEpicColor(epicColor);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to fetch Epic color for " + issue.getKey() + ": " + e.getMessage());
                }
            }
        }
        
        return issue;
    }

    private JiraIssue parseIssueForList(JsonObject issueJson) {
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
            if (priority.has("iconUrl")) {
                issue.setPriorityIconUrl(priority.get("iconUrl").getAsString());
            }
        }
        
        if (fields.has("issuetype")) {
            JsonObject issuetype = fields.getAsJsonObject("issuetype");
            issue.setIssueType(issuetype.get("name").getAsString());
            issue.setIssueTypeId(issuetype.get("id").getAsString());
        }
        
        // Parse story points from appropriate custom field based on project
        String storyPointsField = getStoryPointsField();
        if (fields.has(storyPointsField) && !fields.get(storyPointsField).isJsonNull()) {
            issue.setStoryPoints(fields.get(storyPointsField).getAsDouble());
        }
        
        // Parse priority iconUrl from renderedFields (if available)
        if (issueJson.has("renderedFields")) {
            JsonObject renderedFields = issueJson.getAsJsonObject("renderedFields");
            if (renderedFields.has("priority") && !renderedFields.get("priority").isJsonNull()) {
                JsonObject renderedPriority = renderedFields.getAsJsonObject("priority");
                if (renderedPriority.has("iconUrl")) {
                    issue.setPriorityIconUrl(renderedPriority.get("iconUrl").getAsString());
                }
            }
        }
        
        // Skip parent/epic information for list views to improve performance
        // Parent information will only be fetched in detail views
        
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
        System.out.println("##### [HTTP Request] #####");
        System.out.println("[url] " + method + " " + url);
    }
    
    private void logRequest(String method, String url, String body) {
        System.out.println("##### [HTTP Request] #####");
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
        } else {
            // Clear description with empty ADF structure
            JsonObject emptyDescription = createADFDescription("");
            fields.add("description", emptyDescription);
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
        String storyPointsField = getStoryPointsField();
        if (issue.getStoryPoints() != null) {
            fields.addProperty(storyPointsField, issue.getStoryPoints());
        } else {
            // Explicitly set to null to clear the field
            fields.add(storyPointsField, null);
        }
        
        // Update parent/epic link
        if (issue.getParentKey() != null) {
            if (issue.getParentKey().isEmpty()) {
                // Remove parent/epic link by setting to null
                fields.add("parent", null);
                fields.add(CUSTOMFIELD_EPIC_LINK, null);
            } else {
                // Set parent (for sub-tasks) or epic link (for stories/tasks)
                // Try parent field first (for sub-tasks)
                JsonObject parent = new JsonObject();
                parent.addProperty("key", issue.getParentKey());
                fields.add("parent", parent);
                
                // Also set epic link field (customfield_10014) for epic associations
                fields.addProperty(CUSTOMFIELD_EPIC_LINK, issue.getParentKey());
            }
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 수정", url);

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
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey + "?expand=names,schema,renderedFields";
        logRequest("GET", url);
        sendAccessLog("이슈 조회", url);
        
        // Send access log asynchronously
        sendAccessLog("이슈 조회", url);
        
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get issue: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            System.out.println("getIssue: " + responseBody);
            JsonObject issueJson = gson.fromJson(responseBody, JsonObject.class);
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
        // Try different approaches to get assignable users
        List<String> users;
        
        // First try: Use assignable search with project key
        try {
            users = tryGetAssignableUsers(projectKey);
            if (!users.isEmpty()) {
                return users;
            }
        } catch (IOException e) {
            logRequest("WARN", "Assignable search failed: " + e.getMessage());
        }
        
        // Second try: Use user search without project restriction
        try {
            users = tryGetAllUsers();
            if (!users.isEmpty()) {
                return users;
            }
        } catch (IOException e) {
            logRequest("WARN", "User search failed: " + e.getMessage());
        }
        
        // Third try: Use project role members
        try {
            users = tryGetProjectRoleMembers(projectKey);
            if (!users.isEmpty()) {
                return users;
            }
        } catch (IOException e) {
            logRequest("WARN", "Project role members failed: " + e.getMessage());
        }
        
        throw new IOException("All methods to fetch users failed. Check project key '" + projectKey + "' and permissions.");
    }
    
    private List<String> tryGetAssignableUsers(String projectKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/user/assignable/search?project=" + projectKey + "&maxResults=300";
        return fetchUsersFromUrl(url, "assignable users");
    }
    
    private List<String> tryGetAllUsers() throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/user/search?maxResults=300";
        return fetchUsersFromUrl(url, "all users");
    }
    
    private List<String> tryGetProjectRoleMembers(String projectKey) throws IOException {
        // Get project roles first
        String rolesUrl = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/project/" + projectKey + "/role";
        logRequest("GET", rolesUrl);
        Request rolesRequest = buildRequest(rolesUrl);
        
        Set<String> allUsers = new HashSet<>();
        
        try (Response rolesResponse = client.newCall(rolesRequest).execute()) {
            if (!rolesResponse.isSuccessful()) {
                throw new IOException("Failed to get project roles: HTTP " + rolesResponse.code());
            }
            
            String rolesBody = rolesResponse.body() != null ? rolesResponse.body().string() : "{}";
            JsonObject rolesJson = gson.fromJson(rolesBody, JsonObject.class);
            
            // For each role, get its members
            for (String roleName : rolesJson.keySet()) {
                try {
                    String roleUrl = rolesJson.get(roleName).getAsString();
                    Request roleRequest = buildRequest(roleUrl);
                    
                    try (Response roleResponse = client.newCall(roleRequest).execute()) {
                        if (roleResponse.isSuccessful()) {
                            String roleBody = roleResponse.body() != null ? roleResponse.body().string() : "{}";
                            JsonObject roleJson = gson.fromJson(roleBody, JsonObject.class);
                            
                            if (roleJson.has("actors")) {
                                JsonArray actors = roleJson.getAsJsonArray("actors");
                                for (int i = 0; i < actors.size(); i++) {
                                    JsonObject actor = actors.get(i).getAsJsonObject();
                                    if (actor.has("displayName")) {
                                        allUsers.add(actor.get("displayName").getAsString());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip this role if it fails
                    logRequest("WARN", "Failed to get role " + roleName + ": " + e.getMessage());
                }
            }
        }
        
        return new ArrayList<>(allUsers);
    }
    
    private List<String> fetchUsersFromUrl(String url, String source) throws IOException {
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                String errorMessage = "Failed to get " + source + ": HTTP " + response.code() + " - " + response.message();
                if (!errorBody.isEmpty()) {
                    errorMessage += " - " + errorBody;
                }
                throw new IOException(errorMessage);
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
                sendAccessLog("이슈 상태 변경", transitionsUrl);
                
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

    // Individual field update methods
    public CompletableFuture<Void> updateIssueSummaryAsync(String issueKey, String summary) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueSummary(issueKey, summary);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue summary", e);
            }
        });
    }

    public void updateIssueSummary(String issueKey, String summary) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        fields.addProperty("summary", summary);
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 summary 수정", url);

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue summary: " + response.code() + " - " + responseBody);
            }
        }
    }

    public CompletableFuture<Void> updateIssueDescriptionAsync(String issueKey, String description) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueDescription(issueKey, description);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue description", e);
            }
        });
    }

    public void updateIssueDescription(String issueKey, String description) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        if (StringUtils.isNotBlank(description)) {
            JsonObject descriptionADF = createADFDescription(description);
            fields.add("description", descriptionADF);
        } else {
            // Clear description with empty ADF structure
            JsonObject emptyDescriptionADF = createADFDescription("");
            fields.add("description", emptyDescriptionADF);
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 description 수정", url);

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue description: " + response.code() + " - " + responseBody);
            }
        }
    }

    public CompletableFuture<Void> updateIssueStoryPointsAsync(String issueKey, Double storyPoints) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueStoryPoints(issueKey, storyPoints);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue story points", e);
            }
        });
    }

    public void updateIssueStoryPoints(String issueKey, Double storyPoints) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        String storyPointsField = getStoryPointsField();
        if (storyPoints != null) {
            fields.addProperty(storyPointsField, storyPoints);
        } else {
            // Clear story points
            fields.add(storyPointsField, null);
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 story point 수정", url);

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue story points: " + response.code() + " - " + responseBody);
            }
        }
    }

    public CompletableFuture<Void> updateIssueStatusAsync(String issueKey, String status) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueStatus(issueKey, status);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue status", e);
            }
        });
    }

    public CompletableFuture<Void> updateIssueAssigneeAsync(String issueKey, String assigneeAccountId) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueAssignee(issueKey, assigneeAccountId);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue assignee", e);
            }
        });
    }

    public CompletableFuture<Void> updateIssueParentAsync(String issueKey, String parentKey) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateIssueParent(issueKey, parentKey);
            } catch (IOException e) {
                throw new RuntimeException("Failed to update issue parent", e);
            }
        });
    }

    public void updateIssueAssignee(String issueKey, String assigneeAccountId) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        if (assigneeAccountId != null && !assigneeAccountId.isEmpty()) {
            JsonObject assignee = new JsonObject();
            assignee.addProperty("accountId", assigneeAccountId);
            fields.add("assignee", assignee);
        } else {
            // Unassign by setting assignee to null
            fields.add("assignee", null);
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 assignee 수정", url);

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue assignee: " + response.code() + " - " + responseBody);
            }
        }
    }

    public void updateIssueParent(String issueKey, String parentKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + issueKey;
        
        JsonObject updatePayload = new JsonObject();
        JsonObject fields = new JsonObject();
        
        if (parentKey != null && !parentKey.isEmpty()) {
            // Set parent (for sub-tasks) or epic link (for stories/tasks)
            JsonObject parent = new JsonObject();
            parent.addProperty("key", parentKey);
            fields.add("parent", parent);
            
            // Also set epic link field (customfield_10014) for epic associations
            fields.addProperty(CUSTOMFIELD_EPIC_LINK, parentKey);
        } else {
            // Remove parent/epic link by setting to null
            fields.add("parent", null);
            fields.add(CUSTOMFIELD_EPIC_LINK, null);
        }
        
        updatePayload.add("fields", fields);
        
        RequestBody body = RequestBody.create(
            gson.toJson(updatePayload),
            MediaType.parse("application/json")
        );

        logRequest("PUT", url, gson.toJson(updatePayload));
        sendAccessLog("이슈 parent 수정", url);

        Request request = buildRequest(url).newBuilder()
            .put(body)
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update issue parent: " + response.code() + " - " + responseBody);
            }
        }
    }
    
    public CompletableFuture<Void> deleteIssueAsync(String issueKey) {
        return CompletableFuture.runAsync(() -> {
            try {
                deleteIssue(issueKey);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private void deleteIssue(String issueKey) throws IOException {
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION + "/issue/" + issueKey;
        
        Request request = new Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", Credentials.basic(username, apiToken))
            .header("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                throw new IOException("Failed to delete issue: " + response.code() + " - " + responseBody);
            }
        }
    }
    
    private String getEpicColor(String epicKey) throws IOException {
        // Check cache first
        EpicColorCacheEntry cacheEntry = epicColorCache.get(epicKey);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            return cacheEntry.color;
        }
        
        // Cache miss or expired - fetch from API
        String url = baseUrl + "rest/api/" + JIRA_API_VERSION_3 + "/issue/" + epicKey + "?expand=names,schema,renderedFields";
        logRequest("GET", url);
        Request request = buildRequest(url);
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get epic color: " + response.code());
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonObject issueJson = gson.fromJson(responseBody, JsonObject.class);
            
            String epicColor = null;
            if (issueJson.has("renderedFields")) {
                JsonObject renderedFields = issueJson.getAsJsonObject("renderedFields");
                if (renderedFields.has(CUSTOMFIELD_EPIC_COLOR) && !renderedFields.get(CUSTOMFIELD_EPIC_COLOR).isJsonNull()) {
                    String epicColorCode = renderedFields.get(CUSTOMFIELD_EPIC_COLOR).getAsString();
                    epicColor = mapGhxLabelToHex(epicColorCode);
                }
            }
            
            // Cache the result (even if null)
            epicColorCache.put(epicKey, new EpicColorCacheEntry(epicColor));
            
            // Clean up expired entries periodically
            cleanupExpiredCacheEntries();
            
            return epicColor;
        }
    }
    
    private void cleanupExpiredCacheEntries() {
        // Only cleanup periodically to avoid performance impact
        if (epicColorCache.size() > 100) {
            epicColorCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
    
    private String mapGhxLabelToHex(String ghxLabel) {
        if (ghxLabel == null) {
            return null;
        }

        return switch (ghxLabel) {
            case "ghx-label-1" -> "#243859";
            case "ghx-label-2" -> "#FA9920";
            case "ghx-label-3" -> "#FAC304";
            case "ghx-label-4" -> "#2A53CC";
            case "ghx-label-5" -> "#2AA3BF";
            case "ghx-label-6" -> "#58D8A4";
            case "ghx-label-7" -> "#8677D9";
            case "ghx-label-8" -> "#5244AA";
            case "ghx-label-9" -> "#FA7353";
            case "ghx-label-10" -> "#3884FF";
            case "ghx-label-11" -> "#34C7E6";
            case "ghx-label-12" -> "#6B778C";
            case "ghx-label-13" -> "#128759";
            case "ghx-label-14" -> "#DD350D";
            default -> null;
        };
    }

    public CompletableFuture<AIRecommendationResponse> getEpicRecommendationAsync(String summary, List<JiraEpic> availableEpics) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getEpicRecommendation(summary, availableEpics);
            } catch (IOException e) {
                throw new RuntimeException("Failed to get AI recommendation", e);
            }
        });
    }

    public CompletableFuture<String> generateDescriptionAsync(String aiRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateDescription(aiRequest);
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate description", e);
            }
        });
    }

    public CompletableFuture<String> generateWorkDescriptionAsync(String summary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateWorkDescription(summary);
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate work description", e);
            }
        });
    }

    public AIRecommendationResponse getEpicRecommendation(String summary, List<JiraEpic> availableEpics) throws IOException {
        String url = "http://172.16.120.182:8001/jira/epic/suggest";
        
        // Build epic list string - limit to 10 epics
        StringBuilder epicListBuilder = new StringBuilder();
        int count = 0;
        for (JiraEpic epic : availableEpics) {
            if (count >= 20) break;
            if (count > 0) epicListBuilder.append("\n");
            epicListBuilder.append("- ").append(epic.getKey()).append(": ").append(epic.getSummary());
            count++;
        }
        
        AIRecommendationRequest request = new AIRecommendationRequest(summary, epicListBuilder.toString());
        
        RequestBody body = RequestBody.create(
            gson.toJson(request),
            MediaType.parse("application/json")
        );

        logRequest("POST", url, gson.toJson(request));
        sendAccessLog("이슈 epic 추천", url);
        
        Request httpRequest = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to get AI recommendation: " + response.code() + " - " + responseBody);
            }
            
            return gson.fromJson(responseBody, AIRecommendationResponse.class);
        }
    }

    public String generateDescription(String aiRequest) throws IOException {
        String url = "http://172.16.120.182:8001/jira/description/generate";
        
        // Create request body with AI request
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("request", aiRequest);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestJson),
            MediaType.parse("application/json")
        );
        logRequest("POST", url, gson.toJson(requestJson));
        sendAccessLog("AI 내용 생성", url);
        
        Request httpRequest = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to generate description: " + response.code() + " - " + responseBody);
            }
            
            // Parse response and extract description
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            if (responseJson.has("description")) {
                return responseJson.get("description").getAsString();
            } else if (responseJson.has("result")) {
                return responseJson.get("result").getAsString();
            } else {
                throw new IOException("Invalid response format: missing 'description' or 'result' field");
            }
        }
    }

    public String generateWorkDescription(String summary) throws IOException {
        String url = "http://172.16.120.182:8001/jira/issues/generate_description";
        
        // Create request body with actionType and summary
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("actionType", "task_create");
        requestJson.addProperty("summary", summary);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestJson),
            MediaType.parse("application/json")
        );
        logRequest("POST", url, gson.toJson(requestJson));
        sendAccessLog("AI 작업 내용 생성", url);
        
        Request httpRequest = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = client.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to generate work description: " + response.code() + " - " + responseBody);
            }
            
            // Parse response and extract ai_result
            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            if (responseJson.has("ai_result")) {
                return responseJson.get("ai_result").getAsString();
            } else {
                throw new IOException("Invalid response format: missing 'ai_result' field");
            }
        }
    }

    private String encodeUserInfo(SimpleUserInfo userInfo) {
        try {
            String userInfoJson = gson.toJson(userInfo);
            String encodedUserInfo = URLEncoder.encode(userInfoJson, StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(encodedUserInfo.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Failed to encode user info: " + e.getMessage());
            return "";
        }
    }

    private CompletableFuture<Void> sendAccessLog(String title, String url) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Get current user information
                JsonObject currentUser = getCurrentUser();
                String emailAddress = currentUser.has("emailAddress") ? currentUser.get("emailAddress").getAsString() : "";
                String displayName = currentUser.has("displayName") ? currentUser.get("displayName").getAsString() : "";
                
                SimpleUserInfo userInfo = new SimpleUserInfo(emailAddress, displayName);
                String encodedUserInfo = encodeUserInfo(userInfo);
                
                AccessLogRequest accessLog = new AccessLogRequest("intellij", title, url, encodedUserInfo);
                
                String accessLogUrl = "http://172.16.120.182:8001/accesslog";
                
                RequestBody body = RequestBody.create(
                    gson.toJson(accessLog),
                    MediaType.parse("application/json")
                );
                
                Request request = new Request.Builder()
                    .url(accessLogUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        System.err.println("Failed to send access log: " + response.code());
                    }
                }
            } catch (Exception e) {
                System.err.println("Error sending access log: " + e.getMessage());
            }
        });
    }
}