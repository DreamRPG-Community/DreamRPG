package cn.mythicland.dreamrpg.listener;

import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.display.DreamRpgDisplayService;
import cn.mythicland.dreamrpg.event.CareerChangedEvent;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpg.experience.ExperienceService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.loading.PlayerLoadingGate;
import cn.mythicland.lib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Connects Bukkit player lifecycle events to DreamRPG's profile, spawn, and display services.
 */
@InjectComponent
@ListenerComponent
public final class DreamRpgListener implements Listener {

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final PluginTaskScope tasks;
    private final PlayerProfileService profiles;
    private final SpawnService spawnService;
    private final DreamRpgDisplayService display;
    private final DreamRpgContext context;
    private final PlayerStorageService storage;
    private final ExperienceService experience;
    private final PlayerLoadingGate loadingGate;

    /**
     * Creates the main player lifecycle listener.
     *
     * @param plugin      owning plugin
     * @param tasks       plugin-owned task scope
     * @param context     initialized DreamRPG context
     * @param profiles    profile service
     * @param display     display service
     * @param storage     player storage service
     * @param experience  experience service
     * @param loadingGate player loading gate
     */
    public DreamRpgListener(
            JavaPlugin plugin,
            PluginTaskScope tasks,
            DreamRpgContext context,
            PlayerProfileService profiles,
            DreamRpgDisplayService display,
            PlayerStorageService storage,
            ExperienceService experience,
            PlayerLoadingGate loadingGate
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.context = Objects.requireNonNull(context, "context");
        this.lib = context.lib();
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.spawnService = context.spawnService();
        this.display = Objects.requireNonNull(display, "display");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.loadingGate = Objects.requireNonNull(loadingGate, "loadingGate");
    }

    /**
     * Loads a profile and enforces the configured join spawn.
     *
     * @param event Bukkit join event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void beginLoading(PlayerJoinEvent event) {
        loadingGate.begin(event.getPlayer());
        display.refreshAll();
    }

    /**
     * Loads a player's profile and storage without applying partial data.
     *
     * @param event Bukkit join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void loadPlayerData(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (context.settings().spawn().teleportOnJoin()) {
            tasks.runLater(1L, () -> {
                if (player.isOnline()) spawnService.teleport(player);
            });
        }
        profiles.loadProfile(player.getUniqueId())
                .thenCombine(
                        storage.load(player.getUniqueId()),
                        (ignoredProfile, ignoredStorage) -> null
                )
                .thenCombine(
                        experience.load(player.getUniqueId()),
                        (ignored, ignoredExperience) -> null
                )
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        lib.runOnMain(() -> handleDataFailure(player, failure));
                        return;
                    }
                    lib.runOnMain(() -> {
                        if (!player.isOnline() || !loadingGate.isLoading(player)) {
                            storage.discard(player.getUniqueId());
                            experience.discard(player.getUniqueId());
                            profiles.remove(player.getUniqueId());
                            return;
                        }
                        storage.apply(player);
                        experience.activate(player);
                        loadingGate.ready(player);
                        PlayerDataReadyEvent readyEvent = new PlayerDataReadyEvent(
                                player.getUniqueId(),
                                storage.findLoaded(player.getUniqueId()).orElseThrow(
                                        () -> new IllegalStateException(
                                                "Player storage disappeared before ready event: "
                                                        + player.getUniqueId()
                                        )
                                ),
                                experience.snapshot(player.getUniqueId())
                        );
                        Bukkit.getPluginManager().callEvent(readyEvent);
                        display.refreshAll();
                    });
                });
    }

    /**
     * Applies DreamRPG's green join marker.
     *
     * @param event Bukkit join event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoinMessage(PlayerJoinEvent event) {
        event.setJoinMessage(LegacyText.colorize("&7[&a+&7] " + event.getPlayer().getName()));
    }

    /**
     * Forces the main-city spawn after a death.
     *
     * @param event Bukkit respawn event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (context.settings().spawn().teleportOnRespawn()) {
            event.setRespawnLocation(spawnService.location());
        }
    }

    /**
     * Removes the departing player from the display lifecycle.
     *
     * @param event Bukkit quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        if (storage.isLoaded(uniqueId)) {
            storage.capture(player);
            storage.flush(uniqueId).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to save DreamRPG player storage for " + player.getName() + ": "
                                    + LibApi.rootCauseMessage(failure),
                            failure
                    );
                }
                storage.release(uniqueId);
            });
        } else {
            storage.discard(uniqueId);
        }
        if (experience.isLoaded(uniqueId)) {
            experience.flush(uniqueId).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Failed to save DreamRPG player experience for " + player.getName() + ": "
                                    + LibApi.rootCauseMessage(failure),
                            failure
                    );
                }
                experience.release(uniqueId);
            });
        } else {
            experience.discard(uniqueId);
        }
        loadingGate.cancel(player);
        profiles.remove(uniqueId);
        display.remove(player);
    }

    /**
     * Applies DreamRPG's red leave marker.
     *
     * @param event Bukkit quit event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuitMessage(PlayerQuitEvent event) {
        event.setQuitMessage(LegacyText.colorize("&7[&c-&7] " + event.getPlayer().getName()));
    }

    /**
     * Refreshes all visible presentations after an internal career change.
     *
     * @param event DreamRPG career event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCareerChanged(CareerChangedEvent event) {
        display.refreshAll();
    }

    private void handleDataFailure(Player player, Throwable failure) {
        if (!player.isOnline()) {
            storage.discard(player.getUniqueId());
            experience.discard(player.getUniqueId());
            profiles.remove(player.getUniqueId());
            return;
        }
        loadingGate.cancel(player);
        storage.discard(player.getUniqueId());
        experience.discard(player.getUniqueId());
        profiles.remove(player.getUniqueId());
        plugin.getLogger().log(
                Level.SEVERE,
                "Failed to load DreamRPG player data for " + player.getName() + ": "
                        + LibApi.rootCauseMessage(failure),
                failure
        );
        player.kickPlayer("DreamRPG 玩家数据加载失败, 请联系管理员。");
    }
}
