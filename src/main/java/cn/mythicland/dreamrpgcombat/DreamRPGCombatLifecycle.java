package cn.mythicland.dreamrpgcombat;

import cn.mythicland.dreamrpg.DreamRPGPlugin;

import cn.mythicland.dreamrpgcombat.actionbar.CombatHealthBarService;
import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.config.CombatConfiguration;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.integration.dreamrpg.DreamRPGExperienceIntegration;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.dreamrpgcombat.listener.CombatListener;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ServiceComponent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Owns DreamRPG combat construction, Bukkit registration, tasks, and reload state.
 */
@LifecycleComponent
@ServiceComponent(CombatApi.class)
public final class DreamRPGCombatLifecycle implements DreamLibPluginLifecycle, CombatApi {

    private final DreamRPGPlugin plugin;
    private final PluginTaskScope tasks;
    private final CombatConfiguration configuration;
    private CombatSettings settings;
    private CombatStatsService statsService;
    private CombatHealthBarService healthBarService;
    private CombatListener listener;
    private DreamRPGExperienceIntegration experienceIntegration;
    private BukkitTask regenerationTask;
    private BukkitTask healthBarTask;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin plugin entry point
     */
    public DreamRPGCombatLifecycle(
            DreamRPGPlugin plugin,
            PluginTaskScope tasks,
            CombatConfiguration configuration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /**
     * Builds and registers the combat system.
     */
    @Override
    public void enable() {
        settings = configuration.snapshot();
        experienceIntegration = null;
        if (plugin.getServer().getPluginManager().isPluginEnabled("DreamRPG")) {
            experienceIntegration = DreamRPGExperienceIntegration.detect(plugin);
        }
        statsService = new CombatStatsService(
                plugin,
                settings,
                experienceIntegration == null ? null : experienceIntegration::rpgLevel,
                experienceIntegration == null ? null : experienceIntegration::rpgHealth
        );
        MythicMobsAdapter mythicMobsAdapter = MythicMobsAdapter.detect();
        healthBarService = new CombatHealthBarService(mythicMobsAdapter);
        if (mythicMobsAdapter.isAvailable()) plugin.getLogger().info("MythicMobs compatibility enabled.");
        listener = new CombatListener(plugin, tasks, statsService, healthBarService);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        if (experienceIntegration != null) experienceIntegration.enable(listener, statsService);
        regenerationTask = tasks.runTimer(20L, 20L, listener::regenerateOnlinePlayers);
        healthBarTask = tasks.runTimer(10L, 10L, healthBarService::refreshOnlinePlayers);
        listener.refreshOnlinePlayers();
        plugin.getLogger().info("DreamRPG combat enabled.");
    }

    /**
     * Reloads settings and refreshes online player attributes.
     */
    @Override
    public void reload() {
        reloadConfiguration();
    }

    /**
     * Cancels tasks, unregisters the service, and removes health modifiers.
     */
    @Override
    public void disable() {
        tasks.cancel(regenerationTask);
        tasks.cancel(healthBarTask);
        regenerationTask = null;
        healthBarTask = null;
        if (healthBarService != null) healthBarService.clearAll();
        if (experienceIntegration != null) experienceIntegration.close();
        experienceIntegration = null;
        if (listener != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) listener.removeHealthModifier(player);
        }
        listener = null;
        healthBarService = null;
        statsService = null;
        settings = null;
    }

    /**
     * Returns the current settings.
     *
     * @return combat settings
     */
    public CombatSettings settings() {
        return Objects.requireNonNull(settings, "Combat settings are unavailable");
    }

    /**
     * Returns the active stats service for annotation-driven commands.
     *
     * @return active combat stats service
     */
    public CombatStatsService statsService() {
        return Objects.requireNonNull(statsService, "Combat stats service is unavailable");
    }

    @Override
    public CombatStatsSnapshot getStats(Player player) {
        return statsService().getStats(player);
    }

    @Override
    public CombatStatsSnapshot getStats(LivingEntity entity) {
        return statsService().getStats(entity);
    }

    @Override
    public CombatItemStats parseItem(ItemStack itemStack) {
        return statsService().parseItem(itemStack);
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        CombatSettings refreshedSettings = configuration.snapshot();
        settings = refreshedSettings;
        Objects.requireNonNull(statsService, "Combat stats service is unavailable")
                .reload(refreshedSettings);
        Objects.requireNonNull(listener, "Combat listener is unavailable").refreshOnlinePlayers();
    }

}
