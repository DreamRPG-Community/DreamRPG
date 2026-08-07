package cn.mythicland.dreamrpg.bootstrap;

import cn.mythicland.dreamrpg.display.DreamRpgDisplayService;
import cn.mythicland.dreamrpg.enderchest.EnderChestService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Owns the mutable DreamRPG lifecycle after Lib has injected every component.
 */
@InjectComponent
public final class DreamRpgLifecycle implements LibPluginLifecycle {

    private final JavaPlugin plugin;
    private final DreamRpgContext context;
    private final PlayerProfileService profiles;
    private final DreamRpgDisplayService display;
    private final PlayerStorageService storage;
    private final EnderChestService enderChest;
    private BukkitTask scoreboardTask;
    private BukkitTask titleTask;

    /**
     * Creates the DreamRPG lifecycle module.
     *
     * @param plugin plugin entry point
     * @param context initialized DreamRPG context
     * @param profiles profile service
     * @param display display service
     * @param storage player storage service
     * @param enderChest ender-chest service
     */
    public DreamRpgLifecycle(
            JavaPlugin plugin,
            DreamRpgContext context,
            PlayerProfileService profiles,
            DreamRpgDisplayService display,
            PlayerStorageService storage,
            EnderChestService enderChest
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.context = Objects.requireNonNull(context, "context");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.display = Objects.requireNonNull(display, "display");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.enderChest = Objects.requireNonNull(enderChest, "enderChest");
    }

    /**
     * Starts the scoreboard refresh loop and initial display state.
     */
    @Override
    public void enable() {
        storage.start();
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
        display.reload(context.scoreboard());
        restartDisplayTasks();
        plugin.getLogger().info("DreamRPG configuration reloaded; runtime libraries remain unchanged.");
    }

    /**
     * Cancels tasks, closes display sessions, clears profiles, and closes the configured database.
     */
    @Override
    public void disable() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (titleTask != null) titleTask.cancel();
        scoreboardTask = null;
        titleTask = null;
        RuntimeException failure = null;
        try {
            enderChest.close();
        } catch (RuntimeException exception) {
            failure = exception;
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
        } finally {
            profiles.clear();
            context.close();
        }
        if (failure != null) throw failure;
    }

    private void restartDisplayTasks() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (titleTask != null) titleTask.cancel();
        scoreboardTask = null;
        titleTask = null;
        if (!context.scoreboard().normal().enabled()) return;
        scoreboardTask = context.lib().runTimer(
                1L,
                context.scoreboard().normal().updateTicks(),
                display::refreshAll
        );
        titleTask = context.lib().runTimer(
                1L,
                context.scoreboard().normal().titleUpdateTicks(),
                display::advanceTitle
        );
    }

    private static RuntimeException appendFailure(RuntimeException current, RuntimeException next) {
        if (current == null) return next;
        current.addSuppressed(next);
        return current;
    }
}
