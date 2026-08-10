package cn.mythicland.dreamrpg.spawn;

import cn.mythicland.dreamrpg.config.DreamRpgSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Resolves and applies the configured DreamRPG main-city spawn.
 */
public final class SpawnService {

    private Location location;
    private DreamRpgSettings.SpawnSettings settings;

    /**
     * Creates a spawn service and validates the configured world immediately.
     *
     * @param settings spawn settings
     */
    public SpawnService(DreamRpgSettings.SpawnSettings settings) {
        reload(settings);
    }

    /**
     * Reloads and validates spawn settings.
     *
     * @param refreshedSettings new settings
     */
    public void reload(DreamRpgSettings.SpawnSettings refreshedSettings) {
        Objects.requireNonNull(refreshedSettings, "refreshedSettings");
        World world = Bukkit.getWorld(refreshedSettings.worldName());
        if (world == null)
            throw new IllegalStateException("Main spawn world is unavailable: " + refreshedSettings.worldName());
        settings = refreshedSettings;
        location = new Location(
                world,
                refreshedSettings.x(),
                refreshedSettings.y(),
                refreshedSettings.z(),
                refreshedSettings.yaw(),
                refreshedSettings.pitch()
        );
    }

    /**
     * Returns a defensive copy of the spawn location.
     *
     * @return spawn location
     */
    public Location location() {
        return Objects.requireNonNull(location, "Main spawn is not initialized").clone();
    }

    /**
     * Teleports a player synchronously.
     *
     * @param player player to teleport
     * @return Bukkit teleport result
     */
    public boolean teleport(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Spawn teleport must run on the main thread");
        return Objects.requireNonNull(player, "player").teleport(location());
    }

    /**
     * Returns current spawn settings.
     *
     * @return spawn settings
     */
    public DreamRpgSettings.SpawnSettings settings() {
        return Objects.requireNonNull(settings, "Spawn settings are unavailable");
    }
}
