package org.buzizi.deepseekpet.interaction.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.buzizi.deepseekpet.DeepseekPetCore;
import org.buzizi.deepseekpet.avatar.PetInstance;
import org.buzizi.deepseekpet.cognition.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 鲸鱼娘 GUI（Paper 1.21.7+ Dialog API）。
 *
 * <p>三个页面：主界面（状态 / 显示开关 / 对话 / 互动）、对话界面（消息列表 + 文本输入 + 发送）、
 * 互动界面（喂食教程 + 传送按钮）。全部使用 Dialog 的原生布局、输入控件与动作按钮。</p>
 */
public final class DialogGui {

    private final DeepseekPetCore core;
    /** 处于「GUI 对话模式」的玩家（收到回复后自动刷新对话页面）。 */
    private final java.util.Set<java.util.UUID> guiDialog = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DialogGui(DeepseekPetCore core) {
        this.core = core;
    }

    public boolean isGuiDialog(Player player) {
        return guiDialog.contains(player.getUniqueId());
    }

    public void markGuiDialog(Player player) {
        guiDialog.add(player.getUniqueId());
    }

    public void exitGuiDialog(Player player) {
        guiDialog.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------ 主页面
    public void openMain(Player player) {
        player.showDialog(buildMain(player));
    }

    private Dialog buildMain(Player player) {
        PetInstance pet = core.pets().byPlayer(player.getUniqueId());
        boolean visible = pet != null && pet.isValid();

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "🐋 状态：" + core.feeding().statusLine(player.getUniqueId()), NamedTextColor.GRAY)));
        body.add(DialogBody.plainMessage(Component.text(
                "显示：" + (visible ? "是" : "否"), NamedTextColor.GRAY)));
        body.add(DialogBody.item(new ItemStack(Material.HEART_OF_THE_SEA)).build());

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("鲸鱼娘 · 主界面", NamedTextColor.AQUA))
                        .body(body)
                        .inputs(List.of(
                                DialogInput.bool("show", Component.text("显示鲸鱼娘"), visible, "显示", "隐藏")
                        ))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(
                        button("与鲸鱼娘对话", NamedTextColor.GOLD, "打开对话界面", (view, audience) -> {
                            if (audience instanceof Player p) {
                                p.showDialog(buildDialog(p));
                            }
                        }),
                        button("与鲸鱼娘互动", NamedTextColor.GREEN, "喂食教程 / 传送", (view, audience) -> {
                            if (audience instanceof Player p) {
                                p.showDialog(buildInteract(p));
                            }
                        }),
                        button("应用显示开关", NamedTextColor.YELLOW, "按上面的开关显示 / 隐藏鲸鱼娘", (view, audience) -> {
                            if (!(audience instanceof Player p)) {
                                return;
                            }
                            Boolean show = view.getBoolean("show");
                            if (show == null) {
                                p.closeDialog();
                                return;
                            }
                            boolean now = core.pets().byPlayer(p.getUniqueId()) != null;
                            if (show != now) {
                                core.pets().toggle(p);
                            }
                            p.closeDialog();
                        })
                )).build()));
    }

    // ------------------------------------------------------------ 对话页面
    public void openDialogPage(Player player) {
        player.showDialog(buildDialog(player));
    }

    private Dialog buildDialog(Player player) {
        List<DialogBody> body = new ArrayList<>();
        List<Message> history = core.memory().getHistory(player.getUniqueId());
        int from = Math.max(0, history.size() - 6);
        if (from >= history.size()) {
            body.add(DialogBody.plainMessage(Component.text("还没有对话，试着说点什么吧～", NamedTextColor.GRAY)));
        }
        for (int i = from; i < history.size(); i++) {
            Message m = history.get(i);
            String who = "assistant".equals(m.getRole()) ? "鲸鱼娘：" : "你：";
            body.add(DialogBody.plainMessage(Component.text(who + m.getContent(), NamedTextColor.WHITE)));
        }

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("与鲸鱼娘对话", NamedTextColor.AQUA))
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text("message", 300, Component.text("要发送的消息"), true, "", 256, null)
                        ))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(
                        button("发送", NamedTextColor.GREEN, "把消息发给鲸鱼娘", (view, audience) -> {
                            if (!(audience instanceof Player p)) {
                                return;
                            }
                            String msg = view.getText("message");
                            p.closeDialog();
                            if (msg == null || msg.isBlank()) {
                                openDialogPage(p);
                                return;
                            }
                            core.sendGuiDialogMessage(p, msg);
                        }),
                        button("退出对话", NamedTextColor.RED, "结束与鲸鱼娘的对话", (view, audience) -> {
                            if (audience instanceof Player p) {
                                core.exitGuiDialog(p);
                                p.closeDialog();
                            }
                        })
                )).build()));
    }

    // ------------------------------------------------------------ 互动页面
    private Dialog buildInteract(Player player) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("与鲸鱼娘互动", NamedTextColor.AQUA))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("🍚 喂食教程", NamedTextColor.GOLD)),
                                DialogBody.plainMessage(Component.text(
                                        "手持可食用物品右键鲸鱼娘：第一次提示确认，第二次右键（默认 3 秒内）即可喂食。",
                                        NamedTextColor.GRAY)),
                                DialogBody.plainMessage(Component.text(
                                        "不同食物恢复的饱食度不同（金苹果恢复更多），饱食度归零后鲸鱼娘会拒绝对话。",
                                        NamedTextColor.GRAY))
                        ))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(
                        button("将鲸鱼娘传送至身边", NamedTextColor.YELLOW, "把鲸鱼娘拉到你身边", (view, audience) -> {
                            if (audience instanceof Player p) {
                                core.teleportPetToPlayer(p);
                                p.closeDialog();
                            }
                        }),
                        button("将自己传送至鲸鱼娘", NamedTextColor.YELLOW, "传送到鲸鱼娘身边", (view, audience) -> {
                            if (audience instanceof Player p) {
                                core.teleportPlayerToPet(p);
                                p.closeDialog();
                            }
                        }),
                        button("返回主界面", NamedTextColor.GRAY, "回到主界面", (view, audience) -> {
                            if (audience instanceof Player p) {
                                p.showDialog(buildMain(p));
                            }
                        })
                )).build()));
    }

    // ------------------------------------------------------------ 工具
    private ActionButton button(String label, NamedTextColor color, String tooltip, DialogActionCallback callback) {
        return ActionButton.create(
                Component.text(label, color),
                Component.text(tooltip, NamedTextColor.GRAY),
                150,
                DialogAction.customClick(callback, ClickCallback.Options.builder().uses(1).build())
        );
    }
}
