package org.buzizi.deepseekpet.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.core.pet.PetInstance;
import org.buzizi.deepseekpet.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 主指令 /deepseekpet（别名 /dp）。
 */
public final class DeepseekPetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "show", "hide", "toggle", "end", "reload", "info"
    );

    private final DeepseekPetCore core;

    public DeepseekPetCommand(DeepseekPetCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "show", "spawn" -> handleShow(sender);
            case "hide", "remove" -> handleHide(sender);
            case "toggle" -> handleToggle(sender);
            case "end", "exit", "quit" -> handleEnd(sender);
            case "reload" -> handleReload(sender);
            case "info", "version" -> handleInfo(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleShow(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PetInstance pet = core.pets().spawnPet(player);
        if (pet != null) {
            player.sendMessage(Text.color(core.config().message("pet-spawned", "&b你的鲸鱼娘已经来到你身边啦～")));
        }
    }

    private void handleHide(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (core.pets().byPlayer(player.getUniqueId()) == null) {
            player.sendMessage(Text.color(core.config().message("no-pet", "&e你还没有鲸鱼娘，输入 /dp show 召唤一只吧")));
            return;
        }
        core.pets().despawnPet(player.getUniqueId());
        player.sendMessage(Text.color(core.config().message("pet-hidden", "&b鲸鱼娘已经隐藏啦（输入 /dp show 重新召唤）")));
    }

    private void handleToggle(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        boolean visible = core.pets().toggle(player);
        if (visible) {
            player.sendMessage(Text.color(core.config().message("pet-spawned", "&b你的鲸鱼娘已经来到你身边啦～")));
        } else {
            player.sendMessage(Text.color(core.config().message("pet-hidden", "&b鲸鱼娘已经隐藏啦（输入 /dp show 重新召唤）")));
        }
    }

    private void handleEnd(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        core.endDialog(player);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("deepseekpet.admin")) {
            sender.sendMessage(Text.color("&c你没有权限执行此操作"));
            return;
        }
        core.config().reload();
        core.ambient().reloadRules();
        sender.sendMessage(Text.color(core.config().message("reloaded", "&aDeepseekPet 配置已重载")));
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(Text.color("&bDeepseekPet &fv" + core.plugin().getDescription().getVersion()
                + (core.isPremium() ? " &6(Premium)" : " &7(Free)") + " &f作者：buzizi"));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Text.color("&c该指令只能由玩家执行"));
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.color("&b===== DeepseekPet 帮助 ====="));
        sender.sendMessage(Text.color("&f/dp show &7召唤鲸鱼娘"));
        sender.sendMessage(Text.color("&f/dp hide &7收起鲸鱼娘"));
        sender.sendMessage(Text.color("&f/dp toggle &7显示/隐藏切换"));
        sender.sendMessage(Text.color("&f/dp end &7结束与鲸鱼娘的对话"));
        sender.sendMessage(Text.color("&f/dp info &7查看版本"));
        if (sender.hasPermission("deepseekpet.admin")) {
            sender.sendMessage(Text.color("&f/dp reload &7重载配置"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
