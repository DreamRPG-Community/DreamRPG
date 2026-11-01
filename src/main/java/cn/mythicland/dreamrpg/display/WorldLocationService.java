package cn.mythicland.dreamrpg.display;

import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Resolves DreamRPG location text from WorldRegion and DreamWorld public services.
 */
@InjectComponent
public final class WorldLocationService {

    private static final String WORLD_MANAGER_NAME = "DreamWorld";
    private static final String WORLD_MANAGER_API = "cn.mythicland.dreamworld.api.DreamWorldApi";
    private static final String WORLD_REGION_NAME = "DreamWorld";
    private static final String WORLD_REGION_API = "cn.mythicland.dreamworldregion.api.DreamWorldRegionApi";

    private final JavaPlugin plugin;
    private Object worldManager;
    private Method findLogicalName;
    private Object worldRegion;
    private Method findRegion;
    private Method regionDisplayName;
    private boolean worldManagerLookupFailed;
    private boolean worldRegionLookupFailed;
    private boolean worldManagerFailureLogged;
    private boolean worldRegionFailureLogged;

    /**
     * Creates the class-loader-safe location adapter.
     *
     * @param plugin owning plugin
     */
    public WorldLocationService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Validates that both location services required by {@code {location}} are ready.
     */
    public void requireAvailable() {
        requireDreamWorldAvailable("{location}");
        if (worldRegionUnavailable()) {
            throw new IllegalStateException("DreamWorld region service is required by scoreboard {location}");
        }
    }

    /**
     * Validates the DreamWorld portion used by the legacy {@code {world}} placeholder.
     *
     * @throws IllegalStateException when DreamWorld is not ready
     */
    public void requireDreamWorldAvailable() {
        requireDreamWorldAvailable("{world}");
    }

    /**
     * Returns the region display name or logical world name for a player.
     *
     * @param player player to inspect
     * @return colored region name or world name
     */
    public String resolve(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        Optional<String> regionName = regionName(target.getLocation());
        return regionName.orElseGet(() -> worldName(target.getWorld()));
    }

    /**
     * Returns the DreamWorld logical name for a loaded world.
     *
     * @param world loaded world
     * @return logical world name
     */
    public String worldName(World world) {
        World targetWorld = Objects.requireNonNull(world, "world");
        if (worldManagerUnavailable()) throw new IllegalStateException("DreamWorld service is unavailable");
        try {
            Object result = findLogicalName.invoke(worldManager, targetWorld);
            if (!(result instanceof Optional<?> optional)) {
                throw new IllegalStateException("DreamWorld returned a non-optional logical name");
            }
            return optional.filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElseThrow(() -> new IllegalStateException(
                            "DreamWorld has no logical name for world: " + targetWorld.getName()
                    ));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("DreamWorld logical-name lookup failed", exception);
        }
    }

    private Optional<String> regionName(Location location) {
        if (worldRegionUnavailable()) throw new IllegalStateException("DreamWorld region service is unavailable");
        try {
            Object result = findRegion.invoke(worldRegion, location);
            if (!(result instanceof Optional<?> optional)) {
                throw new IllegalStateException("DreamWorld region returned a non-optional region");
            }
            if (optional.isEmpty()) return Optional.empty();
            Object region = optional.orElseThrow();
            Object displayName = regionDisplayName.invoke(region);
            if (!(displayName instanceof String value) || value.isBlank()) {
                throw new IllegalStateException("DreamWorld region returned a blank display name");
            }
            return Optional.of(value);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("DreamWorld region lookup failed", exception);
        }
    }

    private boolean worldManagerUnavailable() {
        if (worldManager != null && findLogicalName != null) return false;
        if (worldManagerLookupFailed) return true;
        Plugin plugin = this.plugin.getServer().getPluginManager().getPlugin(WORLD_MANAGER_NAME);
        if (plugin == null || !plugin.isEnabled()) return true;
        try {
            Class<?> apiType = Class.forName(WORLD_MANAGER_API, true, plugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> registration = registration(apiType);
            if (registration == null || registration.getProvider() == null) return false;
            worldManager = registration.getProvider();
            findLogicalName = apiType.getMethod("findLogicalName", World.class);
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            worldManagerLookupFailed = true;
            logFailure("DreamWorld", exception, true);
            return true;
        }
    }

    private boolean worldRegionUnavailable() {
        if (worldRegion != null && findRegion != null && regionDisplayName != null) return false;
        if (worldRegionLookupFailed) return true;
        Plugin plugin = this.plugin.getServer().getPluginManager().getPlugin(WORLD_REGION_NAME);
        if (plugin == null || !plugin.isEnabled()) return true;
        try {
            Class<?> apiType = Class.forName(WORLD_REGION_API, true, plugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> registration = registration(apiType);
            if (registration == null || registration.getProvider() == null) return false;
            worldRegion = registration.getProvider();
            findRegion = apiType.getMethod("findRegion", Location.class);
            Class<?> regionType = Class.forName(
                    "cn.mythicland.dreamworldregion.api.RegionDefinition",
                    true,
                    plugin.getClass().getClassLoader()
            );
            regionDisplayName = regionType.getMethod("displayName");
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            worldRegionLookupFailed = true;
            logFailure("DreamWorld region", exception, false);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private RegisteredServiceProvider<?> registration(Class<?> apiType) {
        return plugin.getServer().getServicesManager().getRegistration((Class<Object>) apiType);
    }

    private void logFailure(String serviceName, Throwable exception, boolean worldManagerFailure) {
        if (worldManagerFailure && worldManagerFailureLogged) return;
        if (!worldManagerFailure && worldRegionFailureLogged) return;
        if (worldManagerFailure) worldManagerFailureLogged = true;
        else worldRegionFailureLogged = true;
        plugin.getLogger().log(
                Level.WARNING,
                serviceName + " is enabled but its public API could not be used.",
                exception
        );
    }

    private void requireDreamWorldAvailable(String placeholder) {
        if (worldManagerUnavailable()) {
            throw new IllegalStateException("DreamWorld service is required by scoreboard " + placeholder);
        }
    }
}
