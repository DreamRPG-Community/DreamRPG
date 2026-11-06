package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.config.*;
import cn.mythicland.dreamrpg.database.PlayerProfileRepository;
import cn.mythicland.dreamrpg.database.PlayerProfileStore;
import cn.mythicland.dreamrpg.display.WorldLocationService;
import cn.mythicland.dreamrpg.member.MythicMemberRuntime;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.dreamworld.api.DreamWorldApi;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.location.LocationSnapper;
import cn.mythicland.framework.bukkit.text.TemplateRenderer;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.database.MigrationSpec;
import cn.mythicland.framework.database.SqlDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

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
    private final SqlDatabase database;
    private final PlayerProfileStore repository;
    private final MythicMemberRuntime membership;
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
        try {
            lib.content().resource(plugin, "careers.yml", "careers.yml");
            DreamRPGSettings loadedSettings = configuration.snapshot();
            ScoreboardSettings loadedScoreboard = scoreboardConfiguration.snapshot();
            lib.containerAnimationService().verifyCompatibility();
            validateIntegrations(loadedSettings, loadedScoreboard);
            CareerCatalog loadedCatalog = CareerCatalog.load(plugin);
            openedDatabase = lib.sharedDatabase().database();
            lib.sharedDatabase().migrate(
                    plugin,
                    "dreamrpg_schema_history",
                    List.of(
                            new MigrationSpec(1, "V1__create_player_profiles.sql"),
                            new MigrationSpec(2, "V2__create_player_coins.sql"),
                            new MigrationSpec(3, "V3__create_player_storage.sql"),
                            new MigrationSpec(4, "V4__create_player_experience.sql"),
                            new MigrationSpec(5, "V5__create_first_join_rewards.sql"),
                            new MigrationSpec(6, "V6__create_player_vitals.sql"),
                            new MigrationSpec(7, "V7__create_player_settings.sql"),
                            new MigrationSpec(8, "V8__add_auto_pickup.sql"),
                            new MigrationSpec(9, "V9__create_memberships.sql"),
                            new MigrationSpec(10, "V10__create_member_names.sql"),
                            new MigrationSpec(11, "V11__index_member_names.sql"),
                            new MigrationSpec(12, "V12__create_player_titles.sql")
                    )
            );
            MythicMemberRuntime openedMembership = new MythicMemberRuntime(
                    plugin,
                    lib.sharedDatabase()
            );
            this.database = openedDatabase;
            this.repository = new PlayerProfileRepository(openedDatabase);
            this.membership = openedMembership;
            this.spawnService = new SpawnService(loadedSettings.spawn());
            this.templates = new TemplateRenderer(plugin, lib.placeholderService());
            this.settings = loadedSettings;
            this.scoreboardSettings = loadedScoreboard;
            this.careerCatalog = loadedCatalog;
        } catch (IOException | SQLException | RuntimeException exception) {
            throw new IllegalStateException("Failed to initialize DreamRPG infrastructure", exception);
        }
    }

    private static void validateRegularFile(Path target) {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("DreamRPG resource is not a regular file: " + target);
        }
    }

    /**
     * Reloads mutable configuration and career definitions without touching JDBC or runtime JARs.
     */
    public void reload() {
        DreamRPGSettings refreshedSettings = configuration.snapshot();
        ScoreboardSettings refreshedScoreboard = scoreboardConfiguration.snapshot();
        validateIntegrations(refreshedSettings, refreshedScoreboard);
        CareerCatalog refreshedCatalog = CareerCatalog.load(plugin);
        spawnService.reload(refreshedSettings.spawn());
        settings = refreshedSettings;
        scoreboardSettings = refreshedScoreboard;
        careerCatalog = refreshedCatalog;
    }

    void replaceCareerCatalog(CareerCatalog catalog) {
        careerCatalog = Objects.requireNonNull(catalog, "catalog");
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
     * Returns the initialized MythicMemberLib runtime owned by DreamRPG.
     *
     * @return membership runtime
     */
    public MythicMemberRuntime membership() {
        return membership;
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
        MainThread.require("Main spawn must be set on the primary thread");
        Location snapped = LocationSnapper.snapBlockAndHorizontalView(requestedLocation);
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

    @Override
    public void close() {
        membership.close();
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

}
