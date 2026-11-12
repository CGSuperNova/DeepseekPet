package org.buzizi.deepseekpet.cognition;

import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.communication.persona.Persona;
import org.buzizi.deepseekpet.platform.config.ConfigManager;

import java.util.List;
import java.util.logging.Level;

/**
 * 对话编排：优先走本地 RAG（检索 + 云端生成 + 角色卡），
 * 失败时直连大模型兜底，保证日常聊天始终可用。
 */
public final class ChatService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Persona persona;
    private final RagClient rag;
    private final LlmClient llm;

    /** RAG 服务被判定为离线时，在这个时间点之前都直接走兜底，避免反复超时。 */
    private volatile long ragDownUntil = 0L;
    private static final long RAG_RETRY_INTERVAL_MS = 30_000L;

    public ChatService(JavaPlugin plugin, ConfigManager config, Persona persona) {
        this.plugin = plugin;
        this.config = config;
        this.persona = persona;
        this.rag = new RagClient(config.ragUrl(), config.ragApiKey(), config.timeoutSeconds());
        this.llm = new LlmClient(
                config.llmBaseUrl(),
                config.llmApiKey(),
                config.llmModel(),
                config.llmTemperature(),
                config.llmMaxTokens(),
                config.timeoutSeconds()
        );
    }

    /** 是否有可用的 AI 通道（RAG 在线，或已配置直连大模型）。 */
    public boolean available() {
        return true; // 实际可用性由 ask() 抛异常判断，这里先乐观返回
    }

    /**
     * 阻塞调用，应在异步线程执行。
     *
     * @param question      玩家本次输入
     * @param history       之前的对话历史（不含本次输入）
     * @param playerContext 玩家当前信息（金币/背包/PAPI 等），可为空
     */
    public ChatResult ask(String question, List<Message> history, String playerContext) throws Exception {
        long now = System.currentTimeMillis();

        // 1) 优先本地 RAG（检索 + 生成，角色卡与玩家信息在 rag-bridge 内注入）
        if (now >= ragDownUntil) {
            try {
                ChatResult result = rag.chat(question, history, playerContext);
                ragDownUntil = 0L;
                return result;
            } catch (Exception e) {
                ragDownUntil = now + RAG_RETRY_INTERVAL_MS;
                plugin.getLogger().log(Level.WARNING, "RAG 服务调用失败，转入直连大模型兜底：" + e.getMessage());
            }
        }

        // 2) 兜底：直连 DeepSeek（用角色卡 + 玩家信息做 system prompt）
        if (config.fallbackToLlm() && llm.isConfigured()) {
            String system = persona.systemPrompt();
            if (playerContext != null && !playerContext.isBlank()) {
                system = system + "\n\n【主人当前信息】（回答问题时参考，不要逐字复述）：\n" + playerContext;
            }
            String answer = llm.chat(system, history, question);
            return new ChatResult(answer, false, List.of(), "free");
        }

        throw new IllegalStateException("AI 服务不可用：本地 RAG 离线且未配置直连大模型 api-key");
    }
}
