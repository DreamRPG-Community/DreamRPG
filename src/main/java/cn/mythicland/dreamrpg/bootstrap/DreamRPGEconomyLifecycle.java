package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.economy.VaultEconomyProvider;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;

import java.util.Objects;

/**
 * Owns Vault economy registration for the DreamRPG coin ledger.
 */
@LifecycleComponent
public final class DreamRPGEconomyLifecycle implements DreamLibPluginLifecycle {

    private final VaultEconomyProvider provider;

    /**
     * Creates the economy lifecycle.
     *
     * @param provider Vault adapter
     */
    public DreamRPGEconomyLifecycle(VaultEconomyProvider provider) {
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
