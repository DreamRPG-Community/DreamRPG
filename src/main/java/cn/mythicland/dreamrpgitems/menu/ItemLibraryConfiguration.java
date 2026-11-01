package cn.mythicland.dreamrpgitems.menu;

import cn.mythicland.framework.pagination.FolderLayout;

import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Loads the configured item-library entries from independent YAML files.
 */
@InjectComponent
public final class ItemLibraryConfiguration {
    private static final List<String> DEFAULT_FILES = List.of(
            "currency.yml", "weapons.yml", "armor.yml", "mainline.yml", "other.yml"
    );

    private final DreamRPGPlugin plugin;
    private final DreamLibApi lib;
    private final MythicItemService items;
    private final Path directory;
    private final Object loadLock = new Object();
    private FolderLayout layout;
    private long generation;
    private boolean closed;

    public ItemLibraryConfiguration(DreamRPGPlugin plugin, DreamLibApi lib, MythicItemService items) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.items = Objects.requireNonNull(items, "items");
        this.directory = lib.content().resolve("DreamRPG/item-library", "item-library");
    }

    public Optional<FolderLayout> layout() {
        return Optional.ofNullable(layout);
    }

    public void reload() {
        long request = ++generation;
        lib.supplyAsync(this::loadOrCreate).whenComplete((loaded, error) -> lib.runOnMain(() -> {
            if (closed || request != generation) return;
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Cannot load item library configuration from " + directory, error);
                if (layout == null) Bukkit.getPluginManager().disablePlugin(plugin);
                return;
            }
            layout = loaded;
            warnMissingItems();
        }));
    }

    public void close() {
        closed = true;
        generation++;
    }

    /** @return validated item-library layout and its inverse, without scheduling another read */
    public ContentUpdate prepareContentReload() {
        var next = loadOrCreate();
        var previous = layout;
        return new ContentUpdate(() -> {
            generation++;
            layout = next;
        }, () -> {
            generation++;
            layout = previous;
        });
    }

    public void warnMissingItems() {
        if (layout == null) return;
        for (var entry : layout.entries()) {
            for (String id : entry.members()) {
                if (items.configuration(id).isEmpty()) {
                    plugin.getLogger().warning("Item library entry " + entry.id() + " references missing item " + id);
                }
            }
        }
    }

    private FolderLayout loadOrCreate() {
        synchronized (loadLock) {
            return loadOrCreateLocked();
        }
    }

    private FolderLayout loadOrCreateLocked() {
        try {
            if (contentFiles(lib.content()).isEmpty()) {
                Files.createDirectories(directory);
                for (String name : DEFAULT_FILES) {
                    try (InputStream input = ItemLibraryConfiguration.class.getClassLoader()
                            .getResourceAsStream("item-library/" + name)) {
                        if (input == null) throw new IOException("Missing bundled item library file: " + name);
                        Files.copy(input, directory.resolve(name));
                    }
                }
            }
            return load(contentFiles(lib.content()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (InvalidConfigurationException exception) {
            throw new CompletionException(exception);
        }
    }

    static FolderLayout load(Path directory) throws IOException, InvalidConfigurationException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid item library directory: " + directory);
        }
        return load(ContentWorkspace.yamlFiles(directory));
    }

    /** @return global and active package-owned library files */
    public static List<Path> contentFiles(ContentWorkspace content) {
        List<Path> result = new ArrayList<>();
        for (Path root : content.sourceRoots()) {
            Path directory = root.resolve("item-library");
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) result.addAll(ContentWorkspace.yamlFiles(directory));
        }
        result.addAll(content.documents("item-library.yml"));
        return List.copyOf(result);
    }

    static FolderLayout load(List<Path> paths) throws IOException, InvalidConfigurationException {
        List<FolderLayout.Entry> entries = new ArrayList<>();
        {
            for (Path path : paths) {
                String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!name.endsWith(".yml") && !name.endsWith(".yaml")) continue;
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Invalid item library file: " + path);
                }
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(path.toFile());
                ConfigurationSection root = yaml.getConfigurationSection("entries");
                if (root == null) throw new IllegalArgumentException("Missing entries section in " + path);
                for (String id : root.getKeys(false)) {
                    ConfigurationSection entry = root.getConfigurationSection(id);
                    if (entry == null || !entry.isInt("order")) {
                        throw new IllegalArgumentException("Missing integer order for " + id + " in " + path);
                    }
                    boolean folder = entry.isList("items");
                    boolean single = entry.isString("item");
                    if (folder == single) {
                        throw new IllegalArgumentException("Specify item or items for " + id + " in " + path);
                    }
                    List<String> items = new ArrayList<>();
                    if (folder) {
                        for (Object value : entry.getList("items")) {
                            if (!(value instanceof String text)) {
                                throw new IllegalArgumentException("Item ID must be text in " + id + " in " + path);
                            }
                            items.add(text);
                        }
                    } else {
                        items.add(entry.getString("item"));
                    }
                    if (items.size() > 64) throw new IllegalArgumentException("Too many items in library entry: " + id);
                    entries.add(new FolderLayout.Entry(id, entry.getInt("order"), folder, items));
                }
            }
        }
        return new FolderLayout(entries);
    }

    /**
     * Validates a library layout without changing its loaded state.
     *
     * @param directory content-owned library directory
     */
    public static void validateContent(List<Path> paths) {
        try { load(paths); }
        catch (IOException | InvalidConfigurationException exception) { throw new IllegalArgumentException("Invalid item library", exception); }
    }

    public static void validateContent(Path directory) {
        try {
            load(directory);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid item library: " + directory, exception);
        }
    }
}
