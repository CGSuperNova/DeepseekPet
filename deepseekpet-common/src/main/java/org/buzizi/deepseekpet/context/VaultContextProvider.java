package org.buzizi.deepseekpet.context;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.buzizi.deepseekpet.core.config.ConfigManager;

/**
 * 金币（Vault 经济）。free 可用。
 */
public final class VaultContextProvider implements PlayerContextProvider {

    private final ConfigManager config;
    private volatile Economy economy;

    public VaultContextProvider(ConfigManager config) {
        this.config = config;
    }

    @Override
    public String id() {
        return "vault";
    }

    @Override
    public boolean enabled() {
        return config.infoVault() && economy() != null;
    }

    private Economy economy() {
        Economy local = economy;
        if (local == null) {
            try {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                local = rsp == null ? null : rsp.getProvider();
                economy = local;
            } catch (Throwable ignored) {
                local = null;
            }
        }
        return local;
    }

    @Override
    public String provide(Player player) {
        Economy eco = economy();
        if (eco == null) {
            return null;
        }
        double balance = eco.getBalance(player);
        return "- 金币：" + eco.format(balance);
    }
}
