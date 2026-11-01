package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.economy.VaultEconomyProvider;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.Objects;

/**
 * Owns Vault economy registration for the DreamRPG coin ledger.
 */
@LifecycleComponent
public final class DreamRPGEconomyLifecycle implements ManagedLifecycle {

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
    public void load() {
        provider.register();
    }

    @Override
    public void reload() {
        provider.reload();
    }

    @Override
    public void unload() {
        provider.unregister();
    }
}
