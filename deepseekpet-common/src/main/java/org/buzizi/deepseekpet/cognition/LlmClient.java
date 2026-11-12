package org.buzizi.deepseekpet.cognition;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.buzizi.deepseekpet.platform.util.Http;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * 直连 OpenAI 兼容大模型（默认 DeepSeek）的客户端。
 * 用作 RAG 服务离线时的兜底聊天，以及 premium 记忆摘要等功能。
 */
public final class LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public LlmClient(String baseUrl, String apiKey, String model, double temperature, int maxTokens, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.http = Http.client();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 自由对话（带历史）。 */
    public String chat(String system, List<Message> history, String userMessage) throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        for (Message m : history) {
            messages.add(message(m.getRole(), m.getContent()));
        }
        messages.add(message("user", userMessage));
        return complete(messages);
    }

    /** 单轮：system + user（用于摘要等无历史场景）。 */
    public String complete(String system, String user) throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        return complete(messages);
    }

    private JsonObject message(String role, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.addProperty("content", content == null ? "" : content);
        return obj;
    }

    private String complete(JsonArray messages) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", temperature);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("stream", false);

        String url = baseUrl + "/chat/completions";
        HttpResponse<String> resp = Http.postJson(http, url, apiKey, gson.toJson(body), timeoutSeconds);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("大模型返回 HTTP " + resp.statusCode() + "：" + truncate(resp.body()));
        }
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "模型没有返回内容";
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            return "模型没有返回内容";
        }
        String content = message.get("content").isJsonNull() ? "" : message.get("content").getAsString();
        return content.isBlank() ? "模型没有返回内容" : content;
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s);
    }
}
