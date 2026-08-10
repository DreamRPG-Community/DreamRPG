package cn.mythicland.dreamrpg.profile;

import cn.mythicland.dreamrpg.api.CareerDefinition;
import cn.mythicland.dreamrpg.api.DreamRpgApi;
import cn.mythicland.dreamrpg.api.PlayerPresentation;
import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.config.CareerCatalog;
import cn.mythicland.dreamrpg.database.PlayerProfileStore;
import cn.mythicland.dreamrpg.event.CareerChangedEvent;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the cached player profile boundary and future internal career writes.
 */
@InjectComponent
@ServiceComponent(DreamRpgApi.class)
public final class PlayerProfileService implements DreamRpgApi {

    private final LibApi lib;
    private final PlayerProfileStore repository;
    private final SpawnService spawnService;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> loadedProfiles = ConcurrentHashMap.newKeySet();
    private volatile CareerCatalog careerCatalog;

    /**
     * Creates a profile service.
     *
     * @param context initialized DreamRPG context
     */
    public PlayerProfileService(DreamRpgContext context) {
        DreamRpgContext initializedContext = Objects.requireNonNull(context, "context");
        this.lib = initializedContext.lib();
        this.repository = initializedContext.repository();
        this.careerCatalog = initializedContext.careerCatalog();
        this.spawnService = initializedContext.spawnService();
    }

    /**
     * Returns a cached snapshot or an in-memory default snapshot.
     *
     * @param uniqueId player UUID
     * @return cached or default profile
     */
    @Override
    public PlayerProfile getProfile(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return profiles.computeIfAbsent(uniqueId, ignored -> defaultProfile(uniqueId));
    }

    /**
     * Loads a profile from the configured JDBC store on Lib's asynchronous executor.
     *
     * @param uniqueId player UUID
     * @return asynchronous profile future
     */
    @Override
    public CompletableFuture<PlayerProfile> loadProfile(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        PlayerProfile cached = profiles.get(uniqueId);
        if (cached != null && loadedProfiles.contains(uniqueId)) {
            return CompletableFuture.completedFuture(cached);
        }
        return lib.supplyAsync(() -> {
            try {
                String careerId = repository.loadOrCreate(uniqueId, CareerCatalog.DEFAULT_CAREER_ID);
                CareerDefinition career = careerCatalog.require(careerId);
                PlayerProfile profile = new PlayerProfile(uniqueId, career.id(), career);
                profiles.put(uniqueId, profile);
                loadedProfiles.add(uniqueId);
                return profile;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load DreamRPG profile: " + uniqueId, exception);
            }
        });
    }

    /**
     * Finds a career definition.
     *
     * @param id career ID
     * @return definition when configured
     */
    @Override
    public Optional<CareerDefinition> findCareer(String id) {
        return careerCatalog.find(id);
    }

    /**
     * Returns the current immutable catalog.
     *
     * @return configured careers
     */
    @Override
    public Collection<CareerDefinition> careers() {
        return careerCatalog.all();
    }

    /**
     * Builds a presentation snapshot for an online or offline UUID.
     *
     * @param uniqueId player UUID
     * @return presentation snapshot
     */
    @Override
    public PlayerPresentation presentation(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        PlayerProfile profile = getProfile(uniqueId);
        Player player = Bukkit.getPlayer(uniqueId);
        String playerName = player == null ? uniqueId.toString() : player.getName();
        String coloredName = profile.career().nameColor() + playerName;
        return new PlayerPresentation(
                profile.career().prefix(),
                profile.career().nameColor(),
                coloredName,
                profile.career().prefix() + coloredName + PlayerPresentation.TAB_NAME_SUFFIX
        );
    }

    /**
     * Returns a defensive copy of the configured spawn.
     *
     * @return main spawn location
     */
    @Override
    public Location mainSpawn() {
        return spawnService.location();
    }

    /**
     * Teleports a player on the Bukkit primary thread.
     *
     * @param player player to teleport
     * @return teleport result future
     */
    @Override
    public CompletableFuture<Boolean> teleportToSpawn(Player player) {
        Objects.requireNonNull(player, "player");
        return lib.supplyOnMain(() -> spawnService.teleport(player));
    }

    /**
     * Changes a career after validating and persisting it. This method is intentionally internal
     * to DreamRPG; it is not part of the registered read-only API.
     *
     * @param uniqueId player UUID
     * @param careerId target career ID
     * @return future completed after the Bukkit event is published
     */
    CompletableFuture<PlayerProfile> changeCareer(UUID uniqueId, String careerId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        CareerDefinition targetCareer = careerCatalog.require(careerId);
        return loadProfile(uniqueId).thenCompose(previousProfile -> lib.supplyAsync(() -> {
            try {
                repository.updateCareer(uniqueId, targetCareer.id());
                return new PlayerProfile(uniqueId, targetCareer.id(), targetCareer);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to update DreamRPG career: " + uniqueId, exception);
            }
        }).thenCompose(currentProfile -> lib.supplyOnMain(() -> {
            profiles.put(uniqueId, currentProfile);
            loadedProfiles.add(uniqueId);
            Bukkit.getPluginManager().callEvent(new CareerChangedEvent(previousProfile, currentProfile));
            return currentProfile;
        })));
    }

    /**
     * Rebinds cached profiles to a newly loaded definition catalog.
     *
     * @param refreshedCatalog new catalog
     */
    public void reloadCatalog(CareerCatalog refreshedCatalog) {
        Objects.requireNonNull(refreshedCatalog, "refreshedCatalog");
        Map<UUID, PlayerProfile> reboundProfiles = new LinkedHashMap<>();
        profiles.forEach((uniqueId, profile) -> reboundProfiles.put(
                uniqueId,
                new PlayerProfile(uniqueId, profile.careerId(), refreshedCatalog.require(profile.careerId()))
        ));
        profiles.putAll(reboundProfiles);
        careerCatalog = refreshedCatalog;
    }

    /**
     * Removes one cached profile after a player leaves or a load is abandoned.
     *
     * @param uniqueId player UUID
     */
    public void remove(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        profiles.remove(uniqueId);
        loadedProfiles.remove(uniqueId);
    }

    /**
     * Clears cached snapshots during shutdown.
     */
    public void clear() {
        profiles.clear();
        loadedProfiles.clear();
    }

    private PlayerProfile defaultProfile(UUID uniqueId) {
        CareerDefinition defaultCareer = careerCatalog.defaultCareer();
        return new PlayerProfile(uniqueId, defaultCareer.id(), defaultCareer);
    }
}
