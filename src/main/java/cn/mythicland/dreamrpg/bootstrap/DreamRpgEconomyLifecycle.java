package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.economy.VaultEconomyProvider;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;

import java.util.Objects;

/**
 * Owns Vault economy registration for the DreamRPG coin ledger.
 */
@InjectComponent
public final class DreamRpgEconomyLifecycle implements LibPluginLifecycle {

    private final VaultEconomyProvider provider;

    /**
     * Creates the economy lifecycle.
     *
     * @param provider Vault adapter
     */
    public DreamRpgEconomyLifecycle(VaultEconomyProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public void enable() {
        provider.register();
    }

    @Override
    public void reload() {
        provider.reload();
    }

    @Override
    public void disable() {
        provider.unregister();
    }
}
