package org.buzizi.deepseekpet.platform.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.avatar.PetInstance;
import org.buzizi.deepseekpet.platform.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 主指令 /deepseekpet（别名 /dp）。
 */
public final class DeepseekPetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "show", "hide", "toggle", "follow", "gui", "tp", "end", "hunger", "reload", "info"
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
            case "follow" -> handleFollow(sender, args);
            case "gui", "menu" -> handleGui(sender);
            case "tp" -> handleTp(sender, args);
            case "end", "exit", "quit" -> handleEnd(sender);
            case "hunger" -> handleHunger(sender);
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

    private void handleFollow(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        PetInstance pet = core.pets().byPlayer(player.getUniqueId());
        if (pet == null) {
            player.sendMessage(Text.color(core.config().message("no-pet", "&e你还没有鲸鱼娘，输入 /dp show 召唤一只吧")));
            return;
        }
        boolean next;
        if (args.length >= 2 && args[1].equalsIgnoreCase("on")) {
            next = true;
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            next = false;
        } else {
            next = !pet.isFollowEnabled();
        }
        pet.setFollow(next);
        player.sendMessage(Text.color(next ? "&b鲸鱼娘开始跟随你啦～" : "&e鲸鱼娘已停止跟随"));
        core.log().event(player.getName(), "跟随切换", next ? "开" : "关");
    }

    private void handleGui(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        core.openInteractionGui(player);
        core.log().event(player.getName(), "打开主界面", "命令");
    }

    private void handleTp(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase() : "";
        switch (mode) {
            case "pettoplayer" -> core.teleportPetToPlayer(player);
            case "playertopet" -> core.teleportPlayerToPet(player);
            default -> player.sendMessage(Text.color("&f/dp tp pettoplayer &7把鲸鱼娘传送到你身边；&f/dp tp playertopet &7把自己传送到鲸鱼娘身边"));
        }
    }

    private void handleEnd(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        core.endDialog(player);
    }

    private void handleHunger(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        int hunger = core.feeding().hunger(player.getUniqueId());
        int max = core.feeding().maxHunger();
        player.sendMessage(Text.color("&b鲸鱼娘的饱食度：&f" + hunger + "/" + max));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("deepseekpet.admin")) {
            sender.sendMessage(Text.color("&c你没有权限执行此操作"));
            return;
        }
        core.config().reload();
        core.ambient().reloadRules();
        core.feeding().reload();
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
        sender.sendMessage(Text.color("&f/dp follow [on|off] &7切换跟随"));
        sender.sendMessage(Text.color("&f/dp gui &7打开鲸鱼娘主界面"));
        sender.sendMessage(Text.color("&f/dp tp pettoplayer|playertopet &7传送鲸鱼娘 / 传送到鲸鱼娘"));
        sender.sendMessage(Text.color("&f/dp end &7结束与鲸鱼娘的对话"));
        sender.sendMessage(Text.color("&f/dp hunger &7查看鲸鱼娘的饱食度"));
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
