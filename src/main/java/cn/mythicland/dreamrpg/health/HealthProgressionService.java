package cn.mythicland.dreamrpg.health;

import cn.mythicland.dreamrpg.api.HealthApi;
import cn.mythicland.dreamrpg.api.HealthSnapshot;
import cn.mythicland.dreamrpg.config.ExperienceConfiguration;
import cn.mythicland.dreamrpg.config.ExperienceSettings;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpg.event.RpgLevelUpEvent;
import cn.mythicland.dreamrpg.experience.ExperienceService;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronizes configurable maximum health from DreamRPG RPG level changes.
 */
@InjectComponent
@ListenerComponent
@ServiceComponent(HealthApi.class)
public final class HealthProgressionService implements Listener, HealthApi {

    private final ExperienceService experience;
    private final ExperienceConfiguration configuration;
    private final PluginTaskScope tasks;
    private final Logger logger;

    /**
     * Creates the health progression service.
     *
     * @param experience    authoritative experience service
     * @param configuration experience and health configuration
     * @param tasks         plugin task scope
     * @param logger        DreamRPG logger
     */
    public HealthProgressionService(
            ExperienceService experience,
            ExperienceConfiguration configuration,
            PluginTaskScope tasks,
            Logger logger
    ) {
        this.experience = Objects.requireNonNull(experience, "experience");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("DreamRPG health requires the main thread");
    }

    /**
     * Recalculates every online ready player's maximum health after configuration reload.
     */
    public void reload() {
        ensurePrimaryThread();
        syncAllOnline();
    }

    /**
     * Synchronizes all online ready players.
     */
    public void syncAllOnline() {
        ensurePrimaryThread();
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (experience.isReady(player.getUniqueId())) apply(player);
        }
    }

    /**
     * Applies the formula to one online player.
     *
     * @param player target player
     */
    @SuppressWarnings("deprecation")
    public void apply(Player player) {
        ensurePrimaryThread();
        Player target = Objects.requireNonNull(player, "player");
        ExperienceSettings settings = configuration.snapshot();
        if (!settings.healthEnabled() || !experience.isReady(target.getUniqueId())) return;
        long level = experience.snapshot(target.getUniqueId()).level();
        double oldMaximum = target.getMaxHealth();
        double current = target.getHealth();
        double newMaximum = HealthProgression.maximumHealth(settings, level);
        double newCurrent = HealthProgression.adjustedCurrentHealth(current, oldMaximum, newMaximum);
        try {
            target.setMaxHealth(newMaximum);
            target.setHealth(newCurrent);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Failed to synchronize DreamRPG maximum health for " + target.getName(),
                    exception
            );
        }
    }

    /**
     * Returns the configured health progression for a ready player.
     *
     * @param uniqueId player UUID
     * @return immutable health snapshot when the experience data is ready
     */
    @Override
    public Optional<HealthSnapshot> snapshot(UUID uniqueId) {
        UUID id = Objects.requireNonNull(uniqueId, "uniqueId");
        if (!experience.isReady(id)) return Optional.empty();
        ExperienceSettings settings = configuration.snapshot();
        long level = experience.snapshot(id).level();
        double maximumHealth = HealthProgression.maximumHealth(settings, level);
        return Optional.of(new HealthSnapshot(
                id,
                level,
                settings.baseHealth(),
                maximumHealth - settings.baseHealth(),
                maximumHealth,
                settings.healthEnabled()
        ));
    }

    /**
     * Applies health once all player data has been installed.
     *
     * @param event ready event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataReady(PlayerDataReadyEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player != null && player.isOnline()) apply(player);
    }

    /**
     * Applies health after the authoritative RPG level-up event.
     *
     * @param event RPG level-up event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRpgLevelUp(RpgLevelUpEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player != null && player.isOnline()) apply(player);
    }

    /**
     * Reapplies health after Bukkit has completed respawn handling.
     *
     * @param event respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        tasks.runLater(1L, () -> {
            if (player.isOnline()) apply(player);
        });
    }
}
