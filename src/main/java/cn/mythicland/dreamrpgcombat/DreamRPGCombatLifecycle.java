package cn.mythicland.dreamrpgcombat;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.actionbar.CombatHealthBarService;
import cn.mythicland.dreamrpgcombat.autoclick.AutoClickerService;
import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.dreamrpgcombat.api.CombatDamageSpec;
import cn.mythicland.dreamrpgcombat.combat.CombatDamageService;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.config.CombatConfiguration;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.integration.dreamrpg.DreamRPGExperienceIntegration;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsDamageBridge;
import cn.mythicland.dreamrpgcombat.listener.CombatListener;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.framework.bukkit.component.ServiceComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns DreamRPG combat construction, Bukkit registration, tasks, and reload state.
 */
@LifecycleComponent
@ServiceComponent(CombatApi.class)
public final class DreamRPGCombatLifecycle implements ManagedLifecycle, CombatApi {

    private final DreamRPGPlugin plugin;
    private final PluginTaskScope tasks;
    private final CombatConfiguration configuration;
    private CombatSettings settings;
    private CombatStatsService statsService;
    private CombatHealthBarService healthBarService;
    private CombatListener listener;
    private CombatDamageService damageService;
    private DreamRPGExperienceIntegration experienceIntegration;
    private BukkitTask regenerationTask;
    private BukkitTask healthBarTask;
    private BukkitTask autoClickerTask;
    private AutoClickerService autoClicker;

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
    public void load() {
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
        damageService = new CombatDamageService(statsService);
        listener = new CombatListener(plugin, tasks, statsService, healthBarService, damageService, mythicMobsAdapter);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        if (mythicMobsAdapter.isAvailable()) {
            plugin.getServer().getPluginManager().registerEvents(new MythicMobsDamageBridge(damageService), plugin);
        }
        if (experienceIntegration != null) experienceIntegration.enable(listener, statsService);
        regenerationTask = tasks.runTimer(20L, 20L, listener::regenerateOnlinePlayers);
        healthBarTask = tasks.runTimer(10L, 10L, healthBarService::refreshOnlinePlayers);
        autoClicker = new AutoClickerService(mythicMobsAdapter);
        plugin.getServer().getPluginManager().registerEvents(autoClicker, plugin);
        autoClickerTask = tasks.runTimer(1L, 1L, autoClicker::tick);
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
    public void unload() {
        tasks.cancel(regenerationTask);
        tasks.cancel(healthBarTask);
        tasks.cancel(autoClickerTask);
        autoClickerTask = null;
        if (autoClicker != null) autoClicker.close();
        autoClicker = null;
        regenerationTask = null;
        healthBarTask = null;
        if (healthBarService != null) healthBarService.clearAll();
        if (experienceIntegration != null) experienceIntegration.close();
        experienceIntegration = null;
        if (listener != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                listener.removeHealthModifier(player);
                listener.removeMovementModifier(player);
            }
            listener.close();
        }
        listener = null;
        damageService = null;
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
    public void refresh(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Combat refresh requires the server thread");
        Objects.requireNonNull(listener, "Combat listener is unavailable").refreshPlayer(player);
    }

    @Override
    public void damage(LivingEntity source, LivingEntity target, CombatDamageSpec specification) {
        Objects.requireNonNull(damageService, "Combat damage service is unavailable")
                .damage(source, target, specification);
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
