package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;

import java.util.Objects;

/**
 * Publishes the experience and health configuration before DreamRPG services start.
 */
@ConfigComponent
public final class ExperienceConfiguration implements ConfigurableComponent {

    private volatile ExperienceSettings snapshot;

    /**
     * Reloads the immutable settings snapshot.
     *
     * @param configuration Lib-owned main configuration view
     */
    @Override
    public void reload(ConfigView configuration) {
        snapshot = ExperienceSettings.bind(Objects.requireNonNull(configuration, "configuration"));
    }

    /**
     * Returns the currently loaded settings.
     *
     * @return immutable settings
     */
    public ExperienceSettings snapshot() {
        ExperienceSettings value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG experience settings are not loaded");
        return value;
    }
}
