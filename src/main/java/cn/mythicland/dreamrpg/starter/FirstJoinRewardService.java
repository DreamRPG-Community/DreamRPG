package cn.mythicland.dreamrpg.starter;

import cn.mythicland.dreamrpg.database.FirstJoinRewardClaimRepository;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpgitems.api.MythicItemRefreshMode;
import cn.mythicland.dreamrpgitems.api.MythicItemRefreshResult;
import cn.mythicland.dreamrpgitems.api.MythicItemRefreshStatus;
import cn.mythicland.dreamrpgitems.api.MythicItemsChangedEvent;
import cn.mythicland.dreamrpgitems.api.MythicItemsSyncRequestedEvent;
import cn.mythicland.dreamrpgitems.api.DreamRPGItemApi;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates first-join reward loading, one-time claim reservation, and the administrator editor.
 */
@InjectComponent
@ListenerComponent
public final class FirstJoinRewardService implements Listener, AutoCloseable {

    private final DreamLibApi lib;
    private final PluginTaskScope tasks;
    private final MenuService menus;
    private final FirstJoinRewardStore store;
    private final FirstJoinRewardClaimRepository claims;
    private final Logger logger;
    private final Map<UUID, FirstJoinRewardMenu> openMenus = new HashMap<>();
    private final Set<UUID> pendingClaims = new HashSet<>();
    private CompletableFuture<FirstJoinRewardContents> contentsLoad;
    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);
    private FirstJoinRewardContents contents;
    private boolean started;
    private boolean closed;
    private boolean syncRequested;

    /**
     * Creates the first-join reward service.
     *
     * @param lib    shared Lib facade
     * @param tasks  plugin task scope
     * @param menus  shared menu lifecycle
     * @param store  reward YAML store
     * @param claims one-time claim repository
     * @param logger plugin logger
     */
    public FirstJoinRewardService(
            DreamLibApi lib,
            PluginTaskScope tasks,
            MenuService menus,
            FirstJoinRewardStore store,
            FirstJoinRewardClaimRepository claims,
            Logger logger
    ) {
        this.lib = Objects.requireNonNull(lib, "lib");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.store = Objects.requireNonNull(store, "store");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Starts asynchronous loading of the configured reward inventory.
     */
    public void start() {
        if (started) return;
        started = true;
        contentsLoad = lib.supplyAsync(store::load);
        contentsLoad.whenComplete((loaded, error) -> lib.runOnMain(() -> {
            if (closed) return;
            if (error != null) {
                logger.log(Level.SEVERE, "Could not load DreamRPG first-join rewards", error);
                return;
            }
            contents = loaded;
            synchronizeContents();
        }));
    }

    /**
     * Opens the reward editor for an administrator.
     *
     * @param player administrator viewer
     */
    public void openEditor(Player player) {
        Objects.requireNonNull(player, "player");
        if (!Bukkit.isPrimaryThread()) {
            lib.runOnMain(() -> openEditor(player));
            return;
        }
        if (closed) {
            player.sendMessage(LegacyText.colorize("&c首次奖励服务不可用。"));
            return;
        }
        if (contents == null) {
            player.sendMessage(LegacyText.colorize("&c首次奖励正在加载, 请稍候。"));
            return;
        }
        if (menus.hasOpenMenu(player.getUniqueId())) menus.close(player);
        openMenus.remove(player.getUniqueId());
        FirstJoinRewardMenu menu = new FirstJoinRewardMenu(
                player.getUniqueId(),
                true,
                contents,
                nextContents -> saveContents(player.getUniqueId(), nextContents),
                () -> onMenuClosed(player.getUniqueId())
        );
        openMenus.put(player.getUniqueId(), menu);
        menus.open(player, menu);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsChanged(MythicItemsChangedEvent event) {
        syncRequested = true;
        synchronizeContents();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsSyncRequested(MythicItemsSyncRequestedEvent event) {
        syncRequested = true;
        synchronizeContents();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        UUID uniqueId = event.uniqueId();
        if (closed || !pendingClaims.add(uniqueId)) return;
        CompletableFuture<FirstJoinRewardContents> loaded = contentsLoad;
        if (loaded == null) {
            pendingClaims.remove(uniqueId);
            return;
        }
        loaded.thenCompose(loadedContents -> {
                    if (!loadedContents.hasItems()) {
                        return CompletableFuture.completedFuture(new RewardReservation(loadedContents, false));
                    }
                    return lib.supplyAsync(() -> claimIfUnclaimed(uniqueId))
                            .thenApply(claimed -> new RewardReservation(loadedContents, claimed));
                })
                .whenComplete((reservation, error) -> lib.runOnMain(() -> {
                    pendingClaims.remove(uniqueId);
                    if (closed || error != null || reservation == null || !reservation.claimed()) {
                        if (error != null) logger.log(
                                Level.WARNING,
                                "Could not reserve first-join reward for " + uniqueId,
                                error
                        );
                        return;
                    }
                    tasks.runLater(1L, () -> {
                        if (closed) return;
                        Player player = Bukkit.getPlayer(uniqueId);
                        if (player == null || !player.isOnline()) {
                            releaseClaim(uniqueId);
                            return;
                        }
                        try {
                            FirstJoinRewardContents current = contents;
                            if (current == null) current = reservation.contents();
                            openClaimMenu(player, current);
                        } catch (RuntimeException exception) {
                            logger.log(Level.SEVERE, "Could not open first-join reward for " + uniqueId, exception);
                            releaseClaim(uniqueId);
                        }
                    });
                }));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (UUID uniqueId : Set.copyOf(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null) menus.close(player);
        }
        openMenus.clear();
        pendingClaims.clear();
    }

    private void openClaimMenu(Player player, FirstJoinRewardContents rewardContents) {
        FirstJoinRewardMenu menu = new FirstJoinRewardMenu(
                player.getUniqueId(),
                false,
                rewardContents,
                ignored -> {
                },
                () -> onMenuClosed(player.getUniqueId())
        );
        openMenus.put(player.getUniqueId(), menu);
        menus.open(player, menu);
    }

    private void saveContents(UUID viewerUniqueId, FirstJoinRewardContents nextContents) {
        contents = Objects.requireNonNull(nextContents, "nextContents");
        synchronized (this) {
            saveChain = saveChain.handle((ignored, error) -> null)
                    .thenCompose(ignored -> lib.runAsync(() -> store.save(nextContents)));
        }
        saveChain.whenComplete((ignored, error) -> lib.runOnMain(() -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not save DreamRPG first-join rewards", error);
                return;
            }
            if (viewerUniqueId == null) return;
            Player player = Bukkit.getPlayer(viewerUniqueId);
            if (player != null && player.isOnline()) {
                player.sendMessage(LegacyText.colorize("&a首次进入奖励已保存。"));
            }
        }));
    }

    private void onMenuClosed(UUID uniqueId) {
        openMenus.remove(uniqueId);
        synchronizeContents();
    }

    private void synchronizeContents() {
        if (closed || !syncRequested || contents == null || !openMenus.isEmpty()) return;
        DreamRPGItemApi items = Bukkit.getServicesManager().load(DreamRPGItemApi.class);
        if (items == null) {
            logger.warning("Cannot synchronize first-join rewards: item service is unavailable");
            return;
        }
        ItemStack[] stacks = contents.copyContents();
        int updated = 0;
        int stale = 0;
        for (int index = 0; index < stacks.length; index++) {
            MythicItemRefreshResult result = items.refresh(stacks[index], MythicItemRefreshMode.EXISTING_INSTANCE);
            if (result.changed()) {
                stacks[index] = result.item();
                updated++;
            } else if (result.status() == MythicItemRefreshStatus.STALE) {
                stale++;
            }
        }
        syncRequested = false;
        if (updated > 0) saveContents(null, new FirstJoinRewardContents(stacks));
        if (stale > 0) logger.warning("First-join rewards contain " + stale + " stale item references");
    }

    private void releaseClaim(UUID uniqueId) {
        lib.runAsync(() -> {
            try {
                claims.releaseClaim(uniqueId);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Could not release first-join reward reservation for " + uniqueId, exception);
            }
        });
    }

    private boolean claimIfUnclaimed(UUID uniqueId) {
        try {
            return claims.claimIfUnclaimed(uniqueId);
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    private record RewardReservation(FirstJoinRewardContents contents, boolean claimed) {
    }
}
