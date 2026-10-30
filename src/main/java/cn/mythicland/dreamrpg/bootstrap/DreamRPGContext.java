package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.config.*;
import cn.mythicland.dreamrpg.database.PlayerProfileRepository;
import cn.mythicland.dreamrpg.database.PlayerProfileStore;
import cn.mythicland.dreamrpg.display.WorldLocationService;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.database.MigrationRunner;
import cn.mythicland.dreamlib.database.MigrationSpec;
import cn.mythicland.dreamlib.database.SqlDatabase;
import cn.mythicland.dreamlib.library.LibraryLoadResult;
import cn.mythicland.dreamlib.library.LibrarySpec;
import cn.mythicland.dreamlib.location.LocationSnapper;
import cn.mythicland.dreamlib.text.TemplateRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import cn.mythicland.dreamworld.api.DreamWorldApi;

import java.util.logging.Level;

/**
 * DreamRPG's injected infrastructure context.
 *
 * <p>The context is the only DreamRPG component that assembles configuration, runtime drivers,
 * migrations, storage, and shared integration bridges. Other components receive ready contracts
 * through Lib's constructor injection.</p>
 */
@InjectComponent
public final class DreamRPGContext implements AutoCloseable {

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final LibraryLoadResult libraries;
    private final SqlDatabase database;
    private final PlayerProfileStore repository;
    private final SpawnService spawnService;
    private final TemplateRenderer templates;
    private final WorldLocationService worldLocation;
    private final DreamRPGConfiguration configuration;
    private final ScoreboardConfiguration scoreboardConfiguration;
    private volatile DreamRPGSettings settings;
    private volatile ScoreboardSettings scoreboardSettings;
    private volatile CareerCatalog careerCatalog;

    /**
     * Builds the complete DreamRPG infrastructure context.
     *
     * @param plugin        owning plugin
     * @param lib           shared Lib service
     * @param worldLocation location integration contract
     */
    public DreamRPGContext(
            JavaPlugin plugin,
            DreamLibApi lib,
            WorldLocationService worldLocation,
            DreamRPGConfiguration configuration,
            ScoreboardConfiguration scoreboardConfiguration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.worldLocation = Objects.requireNonNull(worldLocation, "worldLocation");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.scoreboardConfiguration = Objects.requireNonNull(scoreboardConfiguration, "scoreboardConfiguration");

        SqlDatabase openedDatabase = null;
        LibraryLoadResult loadedLibraries = null;
        try {
            saveResourceIfMissing("careers.yml");
            DreamRPGSettings loadedSettings = configuration.snapshot();
            ScoreboardSettings loadedScoreboard = scoreboardConfiguration.snapshot();
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
                            new MigrationSpec(3, "db/sqlite/V3__create_player_storage.sql"),
                            new MigrationSpec(4, "db/sqlite/V4__create_player_experience.sql"),
                            new MigrationSpec(5, "db/sqlite/V5__create_first_join_rewards.sql"),
                            new MigrationSpec(6, "db/sqlite/V6__create_player_vitals.sql")
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

    private static List<LibrarySpec> librariesFor(
            DreamRPGSettings settings,
            RuntimeLibraryManifest manifest
    ) {
        return settings.database().mode() == DreamRPGSettings.DatabaseMode.SQLITE
                ? List.of(manifest.sqlite())
                : List.of(manifest.mysql());
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

    /**
     * Reloads mutable configuration and career definitions without touching JDBC or runtime JARs.
     */
    public void reload() {
        DreamRPGSettings refreshedSettings = configuration.snapshot();
        if (refreshedSettings.database().mode() != settings.database().mode()) {
            throw new IllegalStateException("Changing database.mode requires a full restart");
        }
        ScoreboardSettings refreshedScoreboard = scoreboardConfiguration.snapshot();
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
    public DreamLibApi lib() {
        return lib;
    }

    /**
     * Returns current DreamRPG settings.
     *
     * @return immutable settings snapshot
     */
    public DreamRPGSettings settings() {
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
    public CompletableFuture<Location> setMainSpawn(Location requestedLocation) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Main spawn must be set on the primary thread");
        Location snapped = LocationSnapper.snapBlockAndView(requestedLocation);
        var worlds = Objects.requireNonNull(Bukkit.getServicesManager().load(DreamWorldApi.class),
                "DreamWorld service is unavailable");
        String logicalName = worlds.findLogicalName(snapped.getWorld()).orElseThrow();
        if (!logicalName.equals(settings().spawn().worldName())) {
            throw new IllegalArgumentException(
                    "Set the main city in DreamRPG/config.yml spawn.world before changing its spawn");
        }
        return worlds.setSpawn(logicalName, snapped).thenApply(ignored -> snapped);
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
            DreamRPGSettings settings,
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
            worldLocation.requireDreamWorldAvailable();
        }
    }

    private SqlDatabase openDatabase(
            DreamRPGSettings settings,
            RuntimeLibraryManifest manifest,
            LibraryLoadResult libraries
    ) {
        if (settings.database().mode() == DreamRPGSettings.DatabaseMode.SQLITE) {
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
}
