package cn.mythicland.dreamrpg.config;

import cn.mythicland.dreamlib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.dreamlib.config.ConfigView;
import cn.mythicland.dreamlib.config.ConfigurableComponent;

import java.util.Objects;

/**
 * Binds DreamRPG's main config.yml before the infrastructure context is constructed.
 */
@ConfigComponent
public final class DreamRPGConfiguration implements ConfigurableComponent {

    private volatile DreamRPGSettings snapshot;

    /**
     * Binds the current main configuration and publishes a complete settings snapshot.
     *
     * @param configuration Lib-owned configuration view
     */
    @Override
    public void reload(ConfigView configuration) {
        snapshot = DreamRPGSettings.bind(Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Returns the current immutable DreamRPG settings.
     *
     * @return current settings
     */
    public DreamRPGSettings snapshot() {
        DreamRPGSettings value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG settings are not loaded");
        return value;
    }
}
