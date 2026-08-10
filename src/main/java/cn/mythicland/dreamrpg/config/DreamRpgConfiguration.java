package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;

import java.util.Objects;

/**
 * Binds DreamRPG's main config.yml before the infrastructure context is constructed.
 */
@ConfigComponent
public final class DreamRpgConfiguration implements ConfigurableComponent {

    private volatile DreamRpgSettings snapshot;

    /**
     * Binds the current main configuration and publishes a complete settings snapshot.
     *
     * @param configuration Lib-owned configuration view
     */
    @Override
    public void reload(ConfigView configuration) {
        snapshot = DreamRpgSettings.bind(Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Returns the current immutable DreamRPG settings.
     *
     * @return current settings
     */
    public DreamRpgSettings snapshot() {
        DreamRpgSettings value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG settings are not loaded");
        return value;
    }
}
