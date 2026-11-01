package cn.mythicland.mythicmobs.content;

import org.bukkit.plugin.java.JavaPlugin;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import io.lumine.xikage.mythicmobs.spawning.spawners.SpawnerManager;
import io.lumine.xikage.mythicmobs.spawning.spawners.MythicSpawner;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Directory adapter embedded in the pinned MM JAR. No files are published or copied. */
public final class ContentPaths {
    private static final Map<Path, Map<String, Path>> SPAWNER_SOURCES = new HashMap<>();
    private static final Map<Path, String> SPAWNER_CONTENT = new HashMap<>();
    private static final Map<String, String> KINDS = Map.of(
            "Items", "items", "Mobs", "mobs", "Skills", "skills", "DropTables", "droptables"
    );

    private ContentPaths() {
    }

    public static String directory(JavaPlugin plugin, String folder) {
        if (folder == null || !KINDS.containsKey(folder)) return folder;
        String relative = "../DreamRPG-content/" + KINDS.get(folder);
        Path target = plugin.getDataFolder().toPath().resolve(relative).toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(target)) throw new IOException("Content directory cannot be a symbolic link: " + target);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot initialize MM content directory " + target, failure);
        }
        return relative;
    }

    public static boolean allowDefaults(boolean missing, String folder) {
        return missing && (folder == null || !folder.replace('\\', '/').startsWith("../DreamRPG-content/"));
    }

    public static boolean packExists(File ignored) {
        return false;
    }

    /** Captures source ownership together with the native registries for transactional reload. */
    public static synchronized Runnable snapshotSpawnerSources() {
        Map<Path, Map<String, Path>> accepted = new HashMap<>();
        SPAWNER_SOURCES.forEach((root, index) -> accepted.put(root, new LinkedHashMap<>(index)));
        Map<Path, String> content = new HashMap<>(SPAWNER_CONTENT);
        return () -> {
            synchronized (ContentPaths.class) {
                SPAWNER_SOURCES.clear();
                accepted.forEach((root, index) -> SPAWNER_SOURCES.put(root, new LinkedHashMap<>(index)));
                SPAWNER_CONTENT.clear();
                SPAWNER_CONTENT.putAll(content);
            }
        };
    }

    public static boolean ensureDirectory(File directory) {
        Path path = directory.toPath().toAbsolutePath().normalize();
        if (path.getParent() != null && path.getParent().getFileName().toString().equals("DreamRPG-content")
                && KINDS.containsValue(path.getFileName().toString())) return true;
        return directory.mkdir();
    }

    /** Reads the exact active package sources used by suite validation. */
    public static synchronized List<File> files(String directory) {
        Path path = Path.of(directory).toAbsolutePath().normalize();
        String kind = path.getFileName().toString();
        Path root = path.getParent();
        if (root != null && root.getFileName().toString().equals("DreamRPG-content")
                && List.of("items", "mobs", "skills", "droptables", "spawners").contains(kind)) {
            ContentWorkspace workspace = new ContentWorkspace(root, "dungeons", "dungeon.yml");
            List<Path> sources = workspace.definitions(List.of(kind)).get(kind);
            if (kind.equals("spawners")) {
                Map<String, Path> index = new LinkedHashMap<>();
                for (Path source : sources) for (String id : ContentWorkspace.readYaml(source).getKeys(false)) {
                    if (!source.getFileName().toString().equals(id + ".yml"))
                        throw new IllegalArgumentException("Spawner requires <id>.yml: " + source);
                    index.put(id, source);
                }
                SPAWNER_SOURCES.put(root, index);
                SPAWNER_CONTENT.keySet().removeIf(pathInRoot -> pathInRoot.startsWith(root));
                for (Path source : sources) {
                    try { SPAWNER_CONTENT.put(source, fingerprint(source)); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                }
            }
            return new ArrayList<>(sources.stream().map(Path::toFile).toList());
        }
        if (!Files.exists(path)) return new ArrayList<>();
        return new ArrayList<>(ContentWorkspace.yamlFiles(path).stream().map(Path::toFile).toList());
    }

    /** Native spawner edits and autosaves keep the file selected during loading. */
    public static synchronized File sourceFile(File candidate) {
        Path path = candidate.toPath().toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (path.getFileName().toString().equals("spawners") && parent != null
                && parent.getFileName().toString().equals("DreamRPG-content")) {
            Path shared = parent.resolve("common/spawners");
            try { Files.createDirectories(shared); }
            catch (IOException exception) { throw new UncheckedIOException(exception); }
            return shared.toFile();
        }
        if (parent == null || !parent.getFileName().toString().equals("spawners")
                || parent.getParent() == null || !parent.getParent().getFileName().toString().equals("DreamRPG-content")) return candidate;
        String name = path.getFileName().toString();
        if (!name.endsWith(".yml")) return candidate;
        String id = name.substring(0, name.length() - 4);
        if (!id.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid spawner ID: " + id);
        Path root = parent.getParent();
        if (!SPAWNER_SOURCES.containsKey(root)) files(parent.toString());
        return SPAWNER_SOURCES.get(root).computeIfAbsent(id, ignored -> root.resolve("common/spawners/" + name)).toFile();
    }

    /** Native autosaves may persist runtime edits, but never overwrite pending source-file edits. */
    public static synchronized void saveSpawners(SpawnerManager manager) {
        try {
            var save = SpawnerManager.class.getDeclaredMethod("saveSpawner", MythicSpawner.class);
            save.setAccessible(true);
            for (MythicSpawner spawner : manager.getSpawners()) {
                Path source = SPAWNER_SOURCES.values().stream().map(index -> index.get(spawner.getName()))
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null);
                if (source != null && SPAWNER_CONTENT.containsKey(source)
                        && (!Files.isRegularFile(source) || !SPAWNER_CONTENT.get(source).equals(fingerprint(source)))) continue;
                save.invoke(manager, spawner);
                if (source != null && Files.isRegularFile(source)) SPAWNER_CONTENT.put(source, fingerprint(source));
            }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Incompatible MM spawner persistence", exception); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private static String fingerprint(Path source) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
