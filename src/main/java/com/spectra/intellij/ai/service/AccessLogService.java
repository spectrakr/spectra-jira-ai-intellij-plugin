package com.spectra.intellij.ai.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.spectra.intellij.ai.model.AccessLogRequest;
import com.spectra.intellij.ai.model.SimpleUserInfo;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class AccessLogService {
    private static final String ACCESS_LOG_URL = "http://172.16.120.182:8001/accesslog";

    private final OkHttpClient client;
    private final Gson gson;
    private final JiraService jiraService;

    public AccessLogService(OkHttpClient client, Gson gson, JiraService jiraService) {
        this.client = client;
        this.gson = gson;
        this.jiraService = jiraService;
    }

    public CompletableFuture<Void> sendAccessLog(String title, String content) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Get current user information
                JsonObject currentUser = jiraService.getCurrentUser();
                String emailAddress = currentUser.has("emailAddress") ? currentUser.get("emailAddress").getAsString() : "";
                String displayName = currentUser.has("displayName") ? currentUser.get("displayName").getAsString() : "";

                SimpleUserInfo userInfo = new SimpleUserInfo(emailAddress, displayName);
                String encodedUserInfo = encodeUserInfo(userInfo);

                AccessLogRequest accessLog = new AccessLogRequest("intellij", title, content, encodedUserInfo);

                RequestBody body = RequestBody.create(
                    gson.toJson(accessLog),
                    MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                    .url(ACCESS_LOG_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

                logRequest("POST", ACCESS_LOG_URL, gson.toJson(accessLog));

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

    private void logRequest(String method, String url, String body) {
        System.out.println("##### [HTTP Request] #####");
        System.out.println("[url] " + method + " " + url);
        System.out.println("[body] " + body);
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
}
