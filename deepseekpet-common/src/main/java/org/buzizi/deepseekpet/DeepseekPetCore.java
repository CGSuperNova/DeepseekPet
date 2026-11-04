package org.buzizi.deepseekpet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.ai.ChatResult;
import org.buzizi.deepseekpet.ai.ChatService;
import org.buzizi.deepseekpet.ai.MemoryStore;
import org.buzizi.deepseekpet.ai.Message;
import org.buzizi.deepseekpet.ai.Persona;
import org.buzizi.deepseekpet.ambient.AmbientLineManager;
import org.buzizi.deepseekpet.command.DeepseekPetCommand;
import org.buzizi.deepseekpet.core.config.ConfigManager;
import org.buzizi.deepseekpet.core.pet.PetInstance;
import org.buzizi.deepseekpet.core.pet.PetManager;
import org.buzizi.deepseekpet.feeding.FeedingManager;
import org.buzizi.deepseekpet.gui.InteractionGui;
import org.buzizi.deepseekpet.listener.ChatInputListener;
import org.buzizi.deepseekpet.listener.GuiListener;
import org.buzizi.deepseekpet.listener.JoinQuitListener;
import org.buzizi.deepseekpet.listener.PetInteractListener;
import org.buzizi.deepseekpet.util.Text;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * DeepseekPet 核心：free 与 premium 共享的全部能力都从这里组装。
 */
public final class DeepseekPetCore {

    private final JavaPlugin plugin;
    private final boolean premium;

    private ConfigManager config;
    private Persona persona;
    private MemoryStore memory;
    private ChatService chat;
    private PetManager pets;
    private AmbientLineManager ambient;
    private FeedingManager feeding;

    /** 处于「对话模式」的玩家（其聊天内容会发给鲸鱼娘）。 */
    private final Set<UUID> dialogSessions = ConcurrentHashMap.newKeySet();

    public DeepseekPetCore(JavaPlugin plugin, boolean premium) {
        this.plugin = plugin;
        this.premium = premium;
    }

    public void enable() {
        this.config = new ConfigManager(plugin);
        this.config.reload();
        this.persona = new Persona(config);
        this.memory = new MemoryStore(plugin, config);
        this.chat = new ChatService(plugin, config, persona);
        this.pets = new PetManager(plugin, config);
        this.feeding = new FeedingManager(plugin, config, this);
        this.ambient = new AmbientLineManager(plugin, config, this);

        plugin.getServer().getPluginManager().registerEvents(new JoinQuitListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PetInteractListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GuiListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChatInputListener(this), plugin);

        registerCommand();

        this.feeding.enable();
        this.ambient.start();
        startPetSweeper();

        plugin.getLogger().info("DeepseekPet" + (premium ? " (premium)" : " (free)") + " 已启用，作者：buzizi");
    }

    public void disable() {
        ambient.shutdown();
        if (feeding != null) {
            feeding.disable();
        }
        pets.despawnAll();
        memory.flushAll();
        dialogSessions.clear();
    }

    private void registerCommand() {
        var cmd = plugin.getCommand("deepseekpet");
        if (cmd != null) {
            DeepseekPetCommand executor = new DeepseekPetCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private void startPetSweeper() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> pets.sweepInvalid(), 200L, 200L);
    }

    // ------------------------------------------------------------ 对话框
    public boolean isInDialog(UUID playerId) {
        return dialogSessions.contains(playerId);
    }

    public void startDialog(Player player) {
        dialogSessions.add(player.getUniqueId());
        player.sendMessage(Text.color(config.message("dialog-start",
                "&b现在可以和鲸鱼娘聊天啦，直接输入文字发送即可，输入 &f/dp end &b结束对话")));
    }

    public void endDialog(Player player) {
        dialogSessions.remove(player.getUniqueId());
        player.sendMessage(Text.color(config.message("dialog-end", "&b和鲸鱼娘的对话结束啦～")));
    }

    /** 静默退出对话模式（不发送提示），用于玩家下线等场景。 */
    public void clearDialog(UUID playerId) {
        dialogSessions.remove(playerId);
    }

    public void openInteractionGui(Player player) {
        InteractionGui gui = new InteractionGui(plugin, p -> startDialog(p));
        gui.open(player);
    }

    /** 处理一条来自玩家的对话消息（线程安全：内部会异步调用 AI）。 */
    public void handleDialogMessage(Player player, String text) {
        if (!feeding.canTalk(player.getUniqueId())) {
            player.sendMessage(Text.color(feeding.refuseLine()));
            return;
        }
        feeding.onDialog(player);

        UUID id = player.getUniqueId();
        List<Message> prior = List.copyOf(memory.getHistory(id));
        memory.append(id, "user", text);
        player.sendMessage(Text.color(config.message("thinking", "&7鲸鱼娘正在思考中…")));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ChatResult result = computeReply(text, prior);
            memory.append(id, "assistant", result.answer());

            Bukkit.getScheduler().runTask(plugin, () -> sendWhaleReply(player, result.answer()));
        });
    }

    /** 阻塞调用 AI 并兜底错误信息（仅在异步线程调用）。 */
    private ChatResult computeReply(String text, List<Message> prior) {
        try {
            return chat.ask(text, prior);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AI 调用失败", e);
            String err = config.message("ai-error", "&c鲸鱼娘暂时开小差啦：%error%")
                    .replace("%error%", e.getMessage() == null ? "未知错误" : e.getMessage());
            return new ChatResult(err, false, List.of(), "error");
        }
    }

    private void sendWhaleReply(Player player, String answer) {
        Component prefix = Component.text("[鲸鱼娘] ", NamedTextColor.AQUA);
        String[] lines = (answer == null ? "" : answer).split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            player.sendMessage(i == 0 ? prefix.append(Text.plain(line)) : Text.plain("　 " + line));
        }
        PetInstance pet = pets.byPlayer(player.getUniqueId());
        if (pet != null && pet.isValid()) {
            pet.playClickSound();
        }
    }

    // ------------------------------------------------------------ getters
    public JavaPlugin plugin() {
        return plugin;
    }

    public boolean isPremium() {
        return premium;
    }

    public ConfigManager config() {
        return config;
    }

    public Persona persona() {
        return persona;
    }

    public MemoryStore memory() {
        return memory;
    }

    public ChatService chat() {
        return chat;
    }

    public PetManager pets() {
        return pets;
    }

    public AmbientLineManager ambient() {
        return ambient;
    }

    public FeedingManager feeding() {
        return feeding;
    }
}
