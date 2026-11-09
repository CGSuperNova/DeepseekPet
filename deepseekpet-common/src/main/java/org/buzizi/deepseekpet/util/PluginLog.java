package org.buzizi.deepseekpet.util;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * 插件日志：写入 plugins/DeepseekPet/logs/deepseekpet.log，同时转发到控制台。
 *
 * <p>用于记录插件运行时与玩家使用插件的各项事件。文件日志用简单的追加写，
 * 线程安全，不做轮转（初期规模足够）。</p>
 */
public final class PluginLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final java.util.logging.Logger console;
    private final Path file;
    private final Object lock = new Object();

    public PluginLog(JavaPlugin plugin) {
        this.console = plugin.getLogger();
        this.file = plugin.getDataFolder().toPath().resolve("logs").resolve("deepseekpet.log");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            console.log(Level.WARNING, "无法创建日志目录 " + file.getParent(), e);
        }
    }

    public void info(String message) {
        console.info(message);
        write("INFO", message);
    }

    public void warn(String message) {
        console.warning(message);
        write("WARN", message);
    }

    public void error(String message) {
        console.severe(message);
        write("ERROR", message);
    }

    public void error(String message, Throwable throwable) {
        console.log(Level.SEVERE, message, throwable);
        write("ERROR", message + " -> " + throwable);
    }

    /** 记录一条玩家行为事件（只写文件，不刷控制台）。 */
    public void event(String player, String action, String detail) {
        StringBuilder sb = new StringBuilder(player == null ? "-" : player).append(" | ").append(action);
        if (detail != null && !detail.isEmpty()) {
            sb.append(" | ").append(detail);
        }
        write("EVENT", sb.toString());
    }

    private void write(String level, String message) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + message;
        synchronized (lock) {
            try {
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // 写日志失败不应影响主流程
            }
        }
    }
}
