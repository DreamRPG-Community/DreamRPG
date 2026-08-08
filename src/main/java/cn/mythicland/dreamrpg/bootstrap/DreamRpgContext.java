package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.config.CareerCatalog;
import cn.mythicland.dreamrpg.config.DreamRpgSettings;
import cn.mythicland.dreamrpg.config.RuntimeLibraryManifest;
import cn.mythicland.dreamrpg.config.ScoreboardSettings;
import cn.mythicland.dreamrpg.database.PlayerProfileRepository;
import cn.mythicland.dreamrpg.database.PlayerProfileStore;
import cn.mythicland.dreamrpg.display.WorldLocationService;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.lib.database.MigrationRunner;
import cn.mythicland.lib.database.MigrationSpec;
import cn.mythicland.lib.database.SqlDatabase;
import cn.mythicland.lib.library.LibraryLoadResult;
import cn.mythicland.lib.library.LibrarySpec;
import cn.mythicland.lib.location.LocationSnapper;
import cn.mythicland.lib.text.TemplateRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;

/**
 * DreamRPG's injected infrastructure context.
 *
 * <p>The context is the only DreamRPG component that assembles configuration, runtime drivers,
 * migrations, storage, and shared integration bridges. Other components receive ready contracts
 * through Lib's constructor injection.</p>
 */
@InjectComponent
public final class DreamRpgContext implements AutoCloseable {

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final LibraryLoadResult libraries;
    private final SqlDatabase database;
    private final PlayerProfileStore repository;
    private final SpawnService spawnService;
    private final TemplateRenderer templates;
    private final WorldLocationService worldLocation;
    private volatile DreamRpgSettings settings;
    private volatile ScoreboardSettings scoreboardSettings;
    private volatile CareerCatalog careerCatalog;

    /**
     * Builds the complete DreamRPG infrastructure context.
     *
     * @param plugin owning plugin
     * @param lib shared Lib service
     * @param worldLocation location integration contract
     */
    public DreamRpgContext(
            JavaPlugin plugin,
            LibApi lib,
            WorldLocationService worldLocation
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.worldLocation = Objects.requireNonNull(worldLocation, "worldLocation");

        SqlDatabase openedDatabase = null;
        LibraryLoadResult loadedLibraries = null;
        try {
            saveResourceIfMissing("careers.yml");
            FileConfiguration configuration = loadMainConfiguration();
            FileConfiguration scoreboardConfiguration = loadScoreboardConfiguration();
            DreamRpgSettings loadedSettings = DreamRpgSettings.load(configuration);
            ScoreboardSettings loadedScoreboard = ScoreboardSettings.load(scoreboardConfiguration);
            lib.containerAnimationService().verifyCompatibility();
            validateIntegrations(loadedSettings, loadedScoreboard);
            CareerCatalog loadedCatalog = CareerCatalog.load(plugin);
            RuntimeLibraryManifest libraryManifest = RuntimeLibraryManifest.embedded();
            List<LibrarySpec> librariesToLoad = librariesFor(
                    loadedSettings,
                    libraryManifest
            );
            loadedLibraries = lib.libraryService().load(
                    plugin,
                    librariesToLoad,
                    loadedSettings.libraryRepository()
            );
            openedDatabase = openDatabase(loadedSettings, libraryManifest, loadedLibraries);
            new MigrationRunner().migrate(
                    openedDatabase,
                    plugin,
                    List.of(
                            new MigrationSpec(1, "db/sqlite/V1__create_player_profiles.sql"),
                            new MigrationSpec(2, "db/sqlite/V2__create_player_coins.sql"),
                            new MigrationSpec(3, "db/sqlite/V3__create_player_storage.sql")
                    )
            );
            this.libraries = loadedLibraries;
            this.database = openedDatabase;
            this.repository = new PlayerProfileRepository(openedDatabase);
            this.spawnService = new SpawnService(loadedSettings.spawn());
            this.templates = new TemplateRenderer(plugin, lib.placeholderService());
            this.settings = loadedSettings;
            this.scoreboardSettings = loadedScoreboard;
            this.careerCatalog = loadedCatalog;
        } catch (IOException | SQLException | RuntimeException exception) {
            closeFailedDatabase(openedDatabase, exception);
            closeFailedLibraries(loadedLibraries, exception);
            throw new IllegalStateException("Failed to initialize DreamRPG infrastructure", exception);
        }
    }

    /**
     * Reloads mutable configuration and career definitions without touching JDBC or runtime JARs.
     */
    public void reload() {
        FileConfiguration configuration = loadMainConfiguration();
        FileConfiguration scoreboardConfiguration = loadScoreboardConfiguration();
        DreamRpgSettings refreshedSettings = DreamRpgSettings.load(configuration);
        if (refreshedSettings.database().mode() != settings.database().mode()) {
            throw new IllegalStateException("Changing database.mode requires a full restart");
        }
        ScoreboardSettings refreshedScoreboard = ScoreboardSettings.load(scoreboardConfiguration);
        validateIntegrations(refreshedSettings, refreshedScoreboard);
        CareerCatalog refreshedCatalog = CareerCatalog.load(plugin);
        spawnService.reload(refreshedSettings.spawn());
        settings = refreshedSettings;
        scoreboardSettings = refreshedScoreboard;
        careerCatalog = refreshedCatalog;
    }

    /**
     * Returns the shared Lib service.
     *
     * @return Lib service
     */
    public LibApi lib() {
        return lib;
    }

    /**
     * Returns current DreamRPG settings.
     *
     * @return immutable settings snapshot
     */
    public DreamRpgSettings settings() {
        return settings;
    }

    /**
     * Returns current scoreboard settings.
     *
     * @return immutable scoreboard settings snapshot
     */
    public ScoreboardSettings scoreboard() {
        return scoreboardSettings;
    }

    /**
     * Returns current career catalog.
     *
     * @return immutable career catalog
     */
    public CareerCatalog careerCatalog() {
        return careerCatalog;
    }

    /**
     * Returns the storage contract for player profiles.
     *
     * @return profile storage
     */
    public PlayerProfileStore repository() {
        return repository;
    }

    /**
     * Returns the initialized shared database for DreamRPG storage components.
     *
     * @return open DreamRPG database
     */
    public SqlDatabase database() {
        return database;
    }

    /**
     * Returns the main spawn service.
     *
     * @return main spawn service
     */
    public SpawnService spawnService() {
        return spawnService;
    }

    /**
     * Snaps and persists a new main spawn from an administrator's current location.
     *
     * @param requestedLocation administrator location captured on the Bukkit primary thread
     * @return the snapped and active main spawn location
     * @throws IllegalStateException if called off the primary thread or if the location is invalid
     */
    public Location setMainSpawn(Location requestedLocation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Main spawn must be set on the primary thread");
        }
        Location snapped = LocationSnapper.snapBlockAndView(requestedLocation);
        DreamRpgSettings currentSettings = settings();
        DreamRpgSettings.SpawnSettings current = currentSettings.spawn();
        DreamRpgSettings.SpawnSettings refreshed = new DreamRpgSettings.SpawnSettings(
                snapped.getWorld().getName(),
                snapped.getX(),
                snapped.getY(),
                snapped.getZ(),
                snapped.getYaw(),
                snapped.getPitch(),
                current.teleportOnJoin(),
                current.teleportOnRespawn()
        );
        writeSpawnConfiguration(plugin.getConfig(), refreshed);
        plugin.saveConfig();
        spawnService.reload(refreshed);
        settings = new DreamRpgSettings(
                currentSettings.libraryRepository(),
                currentSettings.database(),
                refreshed,
                currentSettings.chat(),
                currentSettings.display()
        );
        return spawnService.location();
    }

    /**
     * Returns the shared template renderer.
     *
     * @return template renderer
     */
    public TemplateRenderer templates() {
        return templates;
    }

    /**
     * Closes the runtime JDBC registration.
     */
    @Override
    public void close() {
        try {
            database.close();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close DreamRPG database", exception);
        }
        try {
            libraries.close();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close DreamRPG dependency classloader", exception);
        }
    }

    private void validateIntegrations(
            DreamRpgSettings settings,
            ScoreboardSettings scoreboard
    ) {
        if ((settings.containsPlaceholderApiToken() || scoreboard.containsPlaceholderApiToken())
                && !lib.placeholderService().isAvailable()) {
            throw new IllegalStateException(
                    "DreamRPG config.yml or scoreboard.yml uses PlaceholderAPI tokens, "
                            + "but PlaceholderAPI is unavailable"
            );
        }
        if (!scoreboard.normal().enabled()) return;
        if (scoreboard.containsNativePlaceholder("points") && !lib.playerPointsService().isAvailable()) {
            throw new IllegalStateException("scoreboard.yml uses {points}, but PlayerPoints is unavailable");
        }
        if (scoreboard.containsNativePlaceholder("location")) worldLocation.requireAvailable();
        if (scoreboard.containsNativePlaceholder("world")
                && !scoreboard.containsNativePlaceholder("location")) {
            worldLocation.requireWorldManagerAvailable();
        }
    }

    private SqlDatabase openDatabase(
            DreamRpgSettings settings,
            RuntimeLibraryManifest manifest,
            LibraryLoadResult libraries
    ) {
        if (settings.database().mode() == DreamRpgSettings.DatabaseMode.SQLITE) {
            return lib.databaseService().openSqlite(
                    plugin,
                    settings.databasePath(plugin.getDataFolder().toPath()),
                    manifest.driverClassName(),
                    libraries.classLoader()
            );
        }
        Properties properties = new Properties();
        properties.setProperty("user", settings.database().mysql().username());
        properties.setProperty("password", settings.database().mysql().password());
        return lib.databaseService().openJdbc(
                plugin,
                settings.database().mysql().jdbcUrl(),
                properties,
                manifest.mysqlDriverClassName(),
                libraries.classLoader()
        );
    }

    private static List<LibrarySpec> librariesFor(
            DreamRpgSettings settings,
            RuntimeLibraryManifest manifest
    ) {
        return settings.database().mode() == DreamRpgSettings.DatabaseMode.SQLITE
                ? List.of(manifest.sqlite())
                : List.of(manifest.mysql());
    }

    private FileConfiguration loadMainConfiguration() {
        return ConfigSupport.loadDefault(plugin);
    }

    private FileConfiguration loadScoreboardConfiguration() {
        Path target = plugin.getDataFolder().toPath().resolve("scoreboard.yml").normalize();
        saveResourceIfMissing("scoreboard.yml");
        validateRegularFile(target);
        return YamlConfiguration.loadConfiguration(target.toFile());
    }

    private static void writeSpawnConfiguration(
            FileConfiguration configuration,
            DreamRpgSettings.SpawnSettings spawn
    ) {
        configuration.set("spawn.world", spawn.worldName());
        configuration.set("spawn.x", spawn.x());
        configuration.set("spawn.y", spawn.y());
        configuration.set("spawn.z", spawn.z());
        configuration.set("spawn.yaw", spawn.yaw());
        configuration.set("spawn.pitch", spawn.pitch());
    }

    private void saveResourceIfMissing(String resourcePath) {
        Path target = plugin.getDataFolder().toPath().resolve(resourcePath).normalize();
        if (Files.isSymbolicLink(target)) {
            throw new IllegalStateException("DreamRPG resource cannot be a symbolic link: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            validateRegularFile(target);
            return;
        }
        plugin.saveResource(resourcePath, false);
        validateRegularFile(target);
    }

    private static void validateRegularFile(Path target) {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("DreamRPG resource is not a regular file: " + target);
        }
    }

    private static void closeFailedDatabase(SqlDatabase openedDatabase, Throwable failure) {
        if (openedDatabase == null) return;
        try {
            openedDatabase.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeFailedLibraries(LibraryLoadResult loadedLibraries, Throwable failure) {
        if (loadedLibraries == null) return;
        try {
            loadedLibraries.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
