package org.buzizi.deepseekpet.premium.features;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.ai.LlmClient;
import org.buzizi.deepseekpet.ai.Message;
import org.buzizi.deepseekpet.premium.PremiumFeature;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 【Premium 专有】长期记忆摘要。
 *
 * <p>免费版在历史超过上限后直接丢弃最旧的消息；本功能会在丢弃前用大模型
 * 把较旧对话概括成一段「长期记忆」，沉淀到
 * plugins/DeepseekPet/memory/&lt;uuid&gt;.summary.txt，
 * 让鲸鱼娘记得更久，聊天更像在跟认识的人说话。</p>
 */
public final class LongTermMemoryFeature implements PremiumFeature {

    private static final long PERIOD_TICKS = 5L * 60L * 20L; // 每 5 分钟检查一次

    private static final String SUMMARY_SYSTEM =
            "你是鲸鱼娘的「记忆管家」。请把下面这段对话概括成一段简短的中文摘要（100 字以内），"
                    + "保留主人说过的重要信息（名字、喜好、正在做的事、约定等），"
                    + "用第三人称客观语气，不要编造。只输出摘要本身。";

    private final DeepseekPetCore core;
    private final LlmClient llm;
    private int taskId = -1;

    public LongTermMemoryFeature(DeepseekPetCore core) {
        this.core = core;
        this.llm = new LlmClient(
                core.config().llmBaseUrl(),
                core.config().llmApiKey(),
                core.config().llmModel(),
                0.3,
                1024,
                core.config().timeoutSeconds()
        );
    }

    @Override
    public String name() {
        return "long-term-memory";
    }

    @Override
    public void onEnable() {
        if (!llm.isConfigured()) {
            core.plugin().getLogger().info("[premium] 未配置大模型 api-key，长期记忆摘要功能跳过");
            return;
        }
        taskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(core.plugin(), this::summarizeAll, PERIOD_TICKS, PERIOD_TICKS)
                .getTaskId();
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        taskId = -1;
    }

    private void summarizeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                summarize(player.getUniqueId());
            } catch (Exception ignored) {
                // 单个玩家失败不影响其他玩家
            }
        }
    }

    private void summarize(UUID playerId) throws Exception {
        List<Message> history = core.memory().getHistory(playerId);
        int cap = Math.max(2, core.config().memoryMaxTurns() * 2);
        if (history.size() <= cap) {
            return;
        }

        int half = history.size() / 2;
        List<Message> old = new ArrayList<>(history.subList(0, half));
        List<Message> recent = new ArrayList<>(history.subList(half, history.size()));

        String summary = llm.complete(SUMMARY_SYSTEM, render(old));
        if (summary == null || summary.isBlank()) {
            return;
        }
        appendSummary(playerId, summary);
        core.memory().replace(playerId, recent);
    }

    private String render(List<Message> messages) {
        return messages.stream()
                .map(m -> (m.getRole().equals("assistant") ? "鲸鱼娘" : "主人") + "：" + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private void appendSummary(UUID playerId, String summary) {
        try {
            Path dir = core.plugin().getDataFolder().toPath().resolve("memory");
            Files.createDirectories(dir);
            Path file = dir.resolve(playerId + ".summary.txt");
            String line = System.lineSeparator() + "--- " + new java.util.Date() + " ---" + System.lineSeparator() + summary + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            core.plugin().getLogger().warning("[premium] 写入长期记忆失败：" + e.getMessage());
        }
    }
}
