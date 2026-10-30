package cn.mythicland.dreamrpgitems;

import cn.mythicland.dreamlib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.dreamlib.config.ConfigValue;
import cn.mythicland.dreamlib.config.ConfigView;
import cn.mythicland.dreamlib.config.ConfigurableComponent;

import java.util.Objects;

/**
 * Binds the embedded web console configuration for DreamRPG item service.
 */
@ConfigComponent
final class DreamRPGItemConfiguration implements ConfigurableComponent {

    private volatile Snapshot snapshot;

    @Override
    public void reload(ConfigView configuration) {
        RawSettings raw = Objects.requireNonNull(configuration, "configuration")
                .bind(RawSettings.class);
        String bindAddress = raw.bindAddress();
        if (bindAddress.isBlank() || bindAddress.contains(" ")) bindAddress = "127.0.0.1";
        int port = raw.port();
        if (port < 1024 || port > 65535) port = 8765;
        snapshot = new Snapshot(raw.enabled(), bindAddress, port, raw.token());
    }

    Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG items configuration is not loaded");
        return value;
    }

    record Snapshot(boolean enabled, String bindAddress, int port, String token) {
    }

    private record RawSettings(
            @ConfigValue(
                    path = "web.enabled",
                    defaultValue = "true"
            )
            boolean enabled,
            @ConfigValue(
                    path = "web.bind-address",
                    defaultValue = "127.0.0.1",
                    nonBlank = true
            )
            String bindAddress,
            @ConfigValue(
                    path = "web.port",
                    defaultValue = "8765",
                    positive = true
            )
            int port,
            @ConfigValue(
                    path = "web.token",
                    defaultValue = "",
                    trim = false
            )
            String token
    ) {
    }
}
