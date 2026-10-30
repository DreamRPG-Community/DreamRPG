package cn.mythicland.dreamrpg.starter;

import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.item.ItemStackArrayCodec;
import cn.mythicland.dreamlib.storage.AtomicYamlStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Persists the editable first-join reward inventory in one atomic YAML file.
 */
@InjectComponent
public final class FirstJoinRewardStore {

    private static final String RESOURCE_NAME = "first-join-rewards.yml";
    private static final String CONTENTS_PATH = "contents";

    private final JavaPlugin plugin;
    private final Path target;

    /**
     * Creates the first-join reward file store.
     *
     * @param plugin owning plugin
     */
    public FirstJoinRewardStore(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.target = plugin.getDataFolder().toPath().resolve(RESOURCE_NAME).toAbsolutePath().normalize();
    }

    /**
     * Loads the configured reward contents.
     *
     * @return immutable reward contents
     */
    public FirstJoinRewardContents load() {
        ensureTarget();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(target.toFile());
        String encoded = configuration.getString(CONTENTS_PATH, "");
        if (encoded == null || encoded.isBlank()) return FirstJoinRewardContents.empty();
        try {
            ItemStack[] contents = ItemStackArrayCodec.deserialize(
                    encoded,
                    FirstJoinRewardContents.SIZE
            );
            return new FirstJoinRewardContents(contents);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid first-join reward inventory in " + target, exception);
        }
    }

    /**
     * Saves reward contents through Lib's atomic YAML writer.
     *
     * @param contents contents to save
     */
    public void save(FirstJoinRewardContents contents) {
        FirstJoinRewardContents value = Objects.requireNonNull(contents, "contents");
        ensureTarget();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(target.toFile());
        configuration.set(CONTENTS_PATH, ItemStackArrayCodec.serialize(value.copyContents()));
        try {
            AtomicYamlStore.write(target, configuration);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save first-join rewards to " + target, exception);
        }
    }

    private void ensureTarget() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create DreamRPG data directory", exception);
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)) {
            plugin.saveResource(RESOURCE_NAME, false);
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("First-join reward file is not a regular file: " + target);
        }
    }
}
