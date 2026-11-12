package org.buzizi.deepseekpet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.buzizi.deepseekpet.cognition.ChatResult;
import org.buzizi.deepseekpet.cognition.ChatService;
import org.buzizi.deepseekpet.cognition.MemoryStore;
import org.buzizi.deepseekpet.cognition.Message;
import org.buzizi.deepseekpet.communication.persona.Persona;
import org.buzizi.deepseekpet.communication.ambient.AmbientLineManager;
import org.buzizi.deepseekpet.platform.command.DeepseekPetCommand;
import org.buzizi.deepseekpet.platform.extension.BoardRegistry;
import org.buzizi.deepseekpet.context.PlayerContextBuilder;
import org.buzizi.deepseekpet.platform.config.ConfigManager;
import org.buzizi.deepseekpet.avatar.PetInstance;
import org.buzizi.deepseekpet.avatar.PetManager;
import org.buzizi.deepseekpet.interaction.feeding.FeedingManager;
import org.buzizi.deepseekpet.interaction.gui.InteractionGui;
import org.buzizi.deepseekpet.communication.listener.ChatInputListener;
import org.buzizi.deepseekpet.interaction.listener.CitizensInteractListener;
import org.buzizi.deepseekpet.interaction.gui.GuiListener;
import org.buzizi.deepseekpet.avatar.listener.JoinQuitListener;
import org.buzizi.deepseekpet.interaction.listener.PetInteractListener;
import org.buzizi.deepseekpet.interaction.listener.PetDamageListener;
import org.buzizi.deepseekpet.platform.util.PluginLog;
import org.buzizi.deepseekpet.platform.util.Text;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeepseekPet 核心：free 与 premium 共享的全部能力都从这里组装。
 */
public final class DeepseekPetCore {

    private final JavaPlugin plugin;
    private final boolean premium;

    private ConfigManager config;
    private PluginLog log;
    private Persona persona;
    private MemoryStore memory;
    private ChatService chat;
    private PetManager pets;
    private AmbientLineManager ambient;
    private FeedingManager feeding;
    private PlayerContextBuilder context;
    private BoardRegistry boards;

    /** 处于「对话模式」的玩家（其聊天内容会发给鲸鱼娘）。 */
    private final Set<UUID> dialogSessions = ConcurrentHashMap.newKeySet();

    public DeepseekPetCore(JavaPlugin plugin, boolean premium) {
        this.plugin = plugin;
        this.premium = premium;
    }

    public void enable() {
        this.log = new PluginLog(plugin);
        this.config = new ConfigManager(plugin);
        this.config.reload();
        this.persona = new Persona(config);
        this.memory = new MemoryStore(plugin, config);
        this.chat = new ChatService(plugin, config, persona);
        this.pets = new PetManager(plugin, config);
        this.feeding = new FeedingManager(plugin, config, this);
        this.ambient = new AmbientLineManager(plugin, config, this);
        this.context = new PlayerContextBuilder(config);
        this.boards = new BoardRegistry();

        plugin.getServer().getPluginManager().registerEvents(new JoinQuitListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PetInteractListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PetDamageListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GuiListener(this), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ChatInputListener(this), plugin);
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getServer().getPluginManager().registerEvents(new CitizensInteractListener(this), plugin);
        }

        registerCommand();

        this.feeding.enable();
        this.ambient.start();
        startPetSweeper();
        startFollowTicker();

        log.info("DeepseekPet" + (premium ? " (premium)" : " (free)") + " 已启用，作者：buzizi；宠物后端：" + backendName());
    }

    public void disable() {
        if (boards != null) {
            boards.disableAll(this);
        }
        ambient.shutdown();
        if (feeding != null) {
            feeding.disable();
        }
        pets.despawnAll();
        memory.flushAll();
        dialogSessions.clear();
        log.info("DeepseekPet 已禁用");
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

    private void startFollowTicker() {
        // 每 10 tick 驱动一次跟随逻辑（Citizens 导航 / 盔甲架传送；狼 AI 自动跟随无需处理）
        Bukkit.getScheduler().runTaskTimer(plugin, () -> pets.tickFollow(), 10L, 10L);
    }

    private String backendName() {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return "ItemsAdder";
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return "Citizens";
        }
        return "ArmorStand";
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
            log.event(player.getName(), "对话被拒绝", "饥饿值为 0");
            return;
        }
        feeding.onDialog(player);

        UUID id = player.getUniqueId();
        List<Message> prior = List.copyOf(memory.getHistory(id));
        memory.append(id, "user", text);
        player.sendMessage(Text.color(config.message("thinking", "&7鲸鱼娘正在思考中…")));
        log.event(player.getName(), "发送对话", truncate(text));

        // 主线程构建玩家信息（背包 / PAPI 等必须在主线程访问）
        Bukkit.getScheduler().runTask(plugin, () -> {
            String playerContext = context.build(player);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                ChatResult result = computeReply(text, prior, playerContext);
                memory.append(id, "assistant", result.answer());
                log.event(player.getName(), "收到回复", truncate(result.answer()));

                Bukkit.getScheduler().runTask(plugin, () -> sendWhaleReply(player, result.answer()));
            });
        });
    }

    /** 阻塞调用 AI 并兜底错误信息（仅在异步线程调用）。 */
    private ChatResult computeReply(String text, List<Message> prior, String playerContext) {
        try {
            return chat.ask(text, prior, playerContext);
        } catch (Exception e) {
            log.error("AI 调用失败", e);
            String err = config.message("ai-error", "&c鲸鱼娘暂时开小差啦：%error%")
                    .replace("%error%", e.getMessage() == null ? "未知错误" : e.getMessage());
            return new ChatResult(err, false, List.of(), "error");
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
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

    public PluginLog log() {
        return log;
    }

    public PlayerContextBuilder context() {
        return context;
    }

    public BoardRegistry boards() {
        return boards;
    }
}
