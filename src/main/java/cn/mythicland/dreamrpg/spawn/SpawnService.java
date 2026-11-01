package cn.mythicland.dreamrpg.spawn;

import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import org.bukkit.Bukkit;
import cn.mythicland.dreamworld.api.DreamWorldApi;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Resolves and applies the configured DreamRPG main-city spawn.
 */
public final class SpawnService {

    private final DreamWorldApi worlds = Objects.requireNonNull(
            Bukkit.getServicesManager().load(DreamWorldApi.class), "DreamWorld service is unavailable"
    );
    private DreamRPGSettings.SpawnSettings settings;

    /**
     * Creates a spawn service and validates the configured world immediately.
     *
     * @param settings spawn settings
     */
    public SpawnService(DreamRPGSettings.SpawnSettings settings) {
        reload(settings);
    }

    /**
     * Reloads and validates spawn settings.
     *
     * @param refreshedSettings new settings
     */
    public void reload(DreamRPGSettings.SpawnSettings refreshedSettings) {
        Objects.requireNonNull(refreshedSettings, "refreshedSettings");
        World world = worlds.find(refreshedSettings.worldName()).orElse(null);
        if (world == null)
            throw new IllegalStateException("Main spawn world is unavailable: " + refreshedSettings.worldName());
        settings = refreshedSettings;
    }

    /**
     * Returns a defensive copy of the spawn location.
     *
     * @return spawn location
     */
    public Location location() {
        String name = settings.worldName();
        World world = worlds.find(name)
                .orElseThrow(() -> new IllegalStateException("Main spawn world is unavailable: " + name));
        return worlds.findSpawn(name)
                .orElseThrow(() -> new IllegalStateException("DreamWorld/spawns.yml requires a spawn for: " + name))
                .toLocation(world);
    }

    /**
     * Resolves a world's configured DreamWorld spawn, or its native spawn when none is configured.
     * Must be called on the server thread while DreamWorld is available.
     *
     * @param world the world where the player died
     * @return a fresh location in that world
     */
    public Location location(World world) {
        return worlds.findLogicalName(world)
                .flatMap(worlds::findSpawn)
                .map(spawn -> spawn.toLocation(world))
                .orElseGet(world::getSpawnLocation);
    }

    /**
     * Teleports a player synchronously.
     *
     * @param player player to teleport
     * @return Bukkit teleport result
     */
    public boolean teleport(Player player) {
        MainThread.require("Spawn teleport must run on the main thread");
        return Objects.requireNonNull(player, "player").teleport(location());
    }

    /**
     * Returns current spawn settings.
     *
     * @return spawn settings
     */
    public DreamRPGSettings.SpawnSettings settings() {
        return Objects.requireNonNull(settings, "Spawn settings are unavailable");
    }
}
