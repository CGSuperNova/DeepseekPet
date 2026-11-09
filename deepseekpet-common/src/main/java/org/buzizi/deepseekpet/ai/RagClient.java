package org.buzizi.deepseekpet.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.buzizi.deepseekpet.util.Http;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 RAG 检索服务（rag-bridge）客户端。
 *
 * <p>rag-bridge 由 local_rag_qa 思路改写而来，提供 {@code /chat} 接口：
 * 本地检索 + 云端生成，并且始终以鲸鱼娘角色卡作为 system prompt 回答。</p>
 */
public final class RagClient {

    private final String baseUrl;
    private final String apiKey;
    private final int timeoutSeconds;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public RagClient(String baseUrl, String apiKey, int timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.http = Http.client();
    }

    /** 探测服务是否在线。 */
    public boolean isHealthy() {
        try {
            HttpResponse<String> resp = Http.get(http, baseUrl + "/health", apiKey, 3);
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 提问并获取鲸鱼娘风格的回复。
     *
     * @param playerContext 玩家当前信息（可为空），由 rag-bridge 注入 system prompt
     * @throws Exception 网络异常 / 服务返回错误
     */
    public ChatResult chat(String question, List<Message> history, String playerContext) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("question", question);
        JsonArray historyArr = new JsonArray();
        for (Message m : history) {
            JsonObject turn = new JsonObject();
            turn.addProperty("role", m.getRole());
            turn.addProperty("content", m.getContent());
            historyArr.add(turn);
        }
        body.add("history", historyArr);
        if (playerContext != null && !playerContext.isBlank()) {
            body.addProperty("player_context", playerContext);
        }

        HttpResponse<String> resp = Http.postJson(http, baseUrl + "/chat", apiKey, gson.toJson(body), timeoutSeconds);
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("RAG 服务返回 HTTP " + resp.statusCode());
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            String err = root.has("error") ? root.get("error").getAsString() : "未知错误";
            throw new IllegalStateException(err);
        }

        JsonObject data = root.getAsJsonObject("data");
        String answer = data.has("answer") ? data.get("answer").getAsString() : "";
        boolean fromKb = data.has("from_knowledge_base") && data.get("from_knowledge_base").getAsBoolean();
        String mode = data.has("mode") ? data.get("mode").getAsString() : "free";

        List<String> sources = new ArrayList<>();
        if (data.has("sources") && data.get("sources").isJsonArray()) {
            data.getAsJsonArray("sources").forEach(e -> sources.add(e.toString()));
        }
        return new ChatResult(answer, fromKb, sources, mode);
    }
}
