package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Loads and binds DreamRPG's separately persisted scoreboard.yml file.
 */
@ConfigComponent
public final class ScoreboardConfiguration implements ConfigurableComponent {

    private final JavaPlugin plugin;
    private volatile ScoreboardSettings snapshot;

    /**
     * Creates the configuration component for the plugin-owned scoreboard file.
     *
     * @param plugin owning plugin
     */
    public ScoreboardConfiguration(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Reloads scoreboard.yml through Lib's annotation binder. The main config view is not used
     * because this component owns a separate persisted file.
     *
     * @param ignored main config view supplied by Lib
     */
    @Override
    public void reload(ConfigView ignored) {
        Path target = plugin.getDataFolder().toPath().resolve("scoreboard.yml").normalize();
        saveResourceIfMissing();
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("DreamRPG scoreboard resource is not a regular file: " + target);
        }
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(target.toFile());
        snapshot = ScoreboardSettings.bind(configuration, plugin.getLogger()::warning);
    }

    /**
     * Returns the immutable scoreboard snapshot.
     *
     * @return current scoreboard settings
     */
    public ScoreboardSettings snapshot() {
        ScoreboardSettings value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG scoreboard settings are not loaded");
        return value;
    }

    private void saveResourceIfMissing() {
        Path target = plugin.getDataFolder().toPath().resolve("scoreboard.yml").normalize();
        if (Files.isSymbolicLink(target)) {
            throw new IllegalStateException("DreamRPG scoreboard resource cannot be a symbolic link: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
        plugin.saveResource("scoreboard.yml", false);
    }
}
