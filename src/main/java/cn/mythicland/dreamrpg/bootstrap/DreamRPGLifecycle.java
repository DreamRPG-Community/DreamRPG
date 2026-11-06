package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.dreamrpg.config.CareerCatalog;
import cn.mythicland.dreamrpg.display.DreamRPGDisplayService;
import cn.mythicland.dreamrpg.enderchest.EnderChestService;
import cn.mythicland.dreamrpg.experience.ExperienceService;
import cn.mythicland.dreamrpg.health.HealthProgressionService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.dreamrpg.starter.FirstJoinRewardService;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.dreamrpg.trade.PlayerTradeService;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the mutable DreamRPG lifecycle after Lib has injected every component.
 */
@LifecycleComponent
public final class DreamRPGLifecycle implements ManagedLifecycle {

    private final JavaPlugin plugin;
    private final PluginTaskScope tasks;
    private final DreamRPGContext context;
    private final PlayerProfileService profiles;
    private final DreamRPGDisplayService display;
    private final PlayerStorageService storage;
    private final ExperienceService experience;
    private final HealthProgressionService health;
    private final EnderChestService enderChest;
    private final FirstJoinRewardService firstJoinRewards;
    private final PlayerTradeService trades;
    private BukkitTask scoreboardTask;
    private BukkitTask titleTask;

    /**
     * Creates the DreamRPG lifecycle module.
     *
     * @param plugin           plugin entry point
     * @param context          initialized DreamRPG context
     * @param profiles         profile service
     * @param display          display service
     * @param storage          player storage service
     * @param experience       experience service
     * @param health           health progression service
     * @param enderChest       ender-chest service
     * @param firstJoinRewards first-join reward service
     */
    public DreamRPGLifecycle(
            JavaPlugin plugin,
            PluginTaskScope tasks,
            DreamRPGContext context,
            PlayerProfileService profiles,
            DreamRPGDisplayService display,
            PlayerStorageService storage,
            ExperienceService experience,
            HealthProgressionService health,
            EnderChestService enderChest,
            FirstJoinRewardService firstJoinRewards,
            PlayerTradeService trades
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.context = Objects.requireNonNull(context, "context");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.display = Objects.requireNonNull(display, "display");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.health = Objects.requireNonNull(health, "health");
        this.enderChest = Objects.requireNonNull(enderChest, "enderChest");
        this.firstJoinRewards = Objects.requireNonNull(firstJoinRewards, "firstJoinRewards");
        this.trades = Objects.requireNonNull(trades, "trades");
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) return next;
        current.addSuppressed(next);
        return current;
    }

    /**
     * Starts the scoreboard refresh loop and initial display state.
     */
    @Override
    public void load() {
        firstJoinRewards.start();
        experience.start();
        storage.start();
        trades.start();
        restartDisplayTasks();
        display.refreshAll();
        plugin.getLogger().info("DreamRPG enabled.");
    }

    /**
     * Reloads configuration, careers, presentation, and the scoreboard task.
     */
    @Override
    public void reload() {
        context.reload();
        profiles.reloadCatalog(context.careerCatalog());
        experience.reload();
        health.reload();
        display.reload(context.settings().display(), context.scoreboard());
        restartDisplayTasks();
        plugin.getLogger().info("DreamRPG configuration reloaded; runtime libraries remain unchanged.");
    }

    /** Reloads editable career definitions while keeping database and display settings intact. */
    public void reloadContent() {
        prepareContentReload().apply().run();
    }

    /** @return validated career definitions and the inverse profile catalog update */
    public ContentUpdate prepareContentReload() {
        var previous = context.careerCatalog();
        var next = CareerCatalog.load(plugin);
        return new ContentUpdate(() -> {
            context.replaceCareerCatalog(next);
            profiles.reloadCatalog(next);
        }, () -> {
            context.replaceCareerCatalog(previous);
            profiles.reloadCatalog(previous);
        });
    }

    /**
     * Cancels tasks, closes display sessions, clears profiles, and closes the configured database.
     */
    @Override
    public void unload() {
        tasks.cancel(scoreboardTask);
        tasks.cancel(titleTask);
        scoreboardTask = null;
        titleTask = null;
        RuntimeException failure = null;
        try {
            trades.close();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            enderChest.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            firstJoinRewards.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            display.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            storage.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        }
        try {
            experience.close();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, exception);
        } finally {
            profiles.clear();
            context.close();
        }
        if (failure != null) throw failure;
    }

    private void restartDisplayTasks() {
        tasks.cancel(scoreboardTask);
        tasks.cancel(titleTask);
        scoreboardTask = null;
        titleTask = null;
        if (!context.scoreboard().normal().enabled()) return;
        scoreboardTask = tasks.runTimer(
                1L,
                context.scoreboard().normal().updateTicks(),
                display::refreshAll
        );
        titleTask = tasks.runTimer(
                1L,
                context.scoreboard().normal().titleUpdateTicks(),
                display::advanceTitle
        );
    }
}
