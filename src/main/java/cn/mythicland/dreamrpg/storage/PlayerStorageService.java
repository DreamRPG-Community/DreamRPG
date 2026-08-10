package cn.mythicland.dreamrpg.storage;

import cn.mythicland.dreamrpg.api.PlayerStorageApi;
import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.database.PlayerStorageStore;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.lib.storage.VersionedPlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates asynchronous player storage loads, main-thread application, and versioned saves.
 */
@InjectComponent
@ServiceComponent(PlayerStorageApi.class)
public final class PlayerStorageService implements PlayerStorageApi {

    private static final long AUTOSAVE_PERIOD_TICKS = 20L;

    private final LibApi lib;
    private final PluginTaskScope tasks;
    private final PlayerStorageStore store;
    private final Logger logger;
    private final Map<UUID, VersionedPlayerSession<PlayerStorageSnapshot>> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerStorageSnapshot>> loads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();
    private BukkitTask autosaveTask;
    private boolean closed;

    /**
     * Creates the player storage coordinator.
     *
     * @param lib    shared Lib service
     * @param tasks  plugin-owned task scope
     * @param store  database storage adapter
     * @param logger persistence logger
     */
    public PlayerStorageService(
            LibApi lib,
            PluginTaskScope tasks,
            PlayerStorageStore store,
            Logger logger
    ) {
        this.lib = Objects.requireNonNull(lib, "lib");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.store = Objects.requireNonNull(store, "store");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Starts the main-thread autosave loop.
     */
    public void start() {
        ensurePrimaryThread();
        if (closed) throw new IllegalStateException("Player storage service is closed");
        if (autosaveTask != null) return;
        autosaveTask = tasks.runTimer(
                AUTOSAVE_PERIOD_TICKS,
                AUTOSAVE_PERIOD_TICKS,
                this::captureOnlinePlayersAndFlush
        );
    }

    /**
     * Loads a player's storage asynchronously.
     *
     * @param uniqueId player UUID
     * @return loaded snapshot future
     */
    public CompletableFuture<PlayerStorageSnapshot> load(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Player storage service is closed"));
        VersionedPlayerSession<PlayerStorageSnapshot> session = sessions.get(uniqueId);
        if (session != null) return CompletableFuture.completedFuture(session.snapshot());
        CompletableFuture<PlayerStorageSnapshot> existing = loads.get(uniqueId);
        if (existing != null) return existing;
        CompletableFuture<PlayerStorageSnapshot> created = new CompletableFuture<>();
        CompletableFuture<PlayerStorageSnapshot> selected = loads.putIfAbsent(uniqueId, created);
        if (selected != null) return selected;
        lib.supplyAsync(() -> loadFromStore(uniqueId)).whenComplete((snapshot, failure) -> {
            if (failure != null) created.completeExceptionally(failure);
            else created.complete(snapshot);
            loads.remove(uniqueId, created);
        });
        return created;
    }

    /**
     * Applies loaded data to Bukkit's player inventory on the primary thread.
     *
     * @param player online player
     */
    public void apply(Player player) {
        ensurePrimaryThread();
        Player target = Objects.requireNonNull(player, "player");
        PlayerStorageSnapshot snapshot = session(target.getUniqueId()).snapshot();
        PlayerInventory inventory = target.getInventory();
        inventory.clear();
        inventory.setStorageContents(snapshot.inventory());
        inventory.setArmorContents(snapshot.armor());
        inventory.setItemInOffHand(snapshot.offHand());
        inventory.setHeldItemSlot(snapshot.heldSlot());
        target.getEnderChest().clear();
    }

    /**
     * Captures the native player inventory into the loaded session.
     *
     * @param player online player
     */
    public void capture(Player player) {
        ensurePrimaryThread();
        Player target = Objects.requireNonNull(player, "player");
        VersionedPlayerSession<PlayerStorageSnapshot> session = session(target.getUniqueId());
        PlayerInventory inventory = target.getInventory();
        session.replace(session.snapshot().withPlayerInventory(
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getItemInOffHand(),
                inventory.getHeldItemSlot()
        ));
    }

    /**
     * Replaces a player's custom ender-chest contents.
     *
     * @param uniqueId player UUID
     * @param contents exactly 54 menu slots
     */
    public void updateEnderChest(UUID uniqueId, ItemStack[] contents) {
        ensurePrimaryThread();
        VersionedPlayerSession<PlayerStorageSnapshot> session = session(uniqueId);
        session.replace(session.snapshot().withEnderChest(contents));
    }

    /**
     * Discards an unfinished load without saving partial data.
     *
     * @param uniqueId player UUID
     */
    public void discard(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        sessions.remove(uniqueId);
        loads.remove(uniqueId);
    }

    /**
     * Releases a completed session after its quit save finished.
     *
     * @param uniqueId player UUID
     */
    public void release(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        sessions.remove(uniqueId);
    }

    @Override
    public boolean isLoaded(UUID uniqueId) {
        return uniqueId != null && sessions.containsKey(uniqueId);
    }

    @Override
    public boolean isLoading(UUID uniqueId) {
        return uniqueId != null && loads.containsKey(uniqueId);
    }

    @Override
    public Optional<PlayerStorageSnapshot> findLoaded(UUID uniqueId) {
        if (uniqueId == null) return Optional.empty();
        VersionedPlayerSession<PlayerStorageSnapshot> session = sessions.get(uniqueId);
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    @Override
    public CompletableFuture<Void> flush(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        VersionedPlayerSession<PlayerStorageSnapshot> session = sessions.get(uniqueId);
        if (session == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> next;
        synchronized (saveChains) {
            CompletableFuture<Void> previous = saveChains.get(uniqueId);
            CompletableFuture<Void> ready = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous;
            next = ready.thenCompose(ignored -> lib.runAsync(() -> saveUntilClean(session)));
            saveChains.put(uniqueId, next);
        }
        next.whenComplete((ignored, failure) -> removeSaveChain(uniqueId, next));
        return next;
    }

    /**
     * Flushes all loaded player sessions.
     *
     * @return future completed after every save
     */
    public CompletableFuture<Void> flushAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uniqueId : sessions.keySet()) futures.add(flush(uniqueId));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Captures online data, flushes it, and reports failures through the logger.
     */
    public void captureOnlinePlayersAndFlush() {
        ensurePrimaryThread();
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (isLoaded(player.getUniqueId())) capture(player);
        }
        flushAll().whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(
                        Level.SEVERE,
                        "DreamRPG player storage autosave failed: " + LibApi.rootCauseMessage(failure),
                        failure
                );
            }
        });
    }

    /**
     * Flushes and closes all loaded sessions before the database is closed.
     */
    public void close() {
        ensurePrimaryThread();
        if (closed) return;
        tasks.cancel(autosaveTask);
        autosaveTask = null;
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (isLoaded(player.getUniqueId())) capture(player);
        }
        try {
            flushAll().join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("Failed to flush DreamRPG player storage", exception.getCause());
        } finally {
            closed = true;
            sessions.clear();
            loads.clear();
            saveChains.clear();
        }
    }

    private PlayerStorageSnapshot loadFromStore(UUID uniqueId) {
        try {
            PlayerStorageSnapshot snapshot = store.loadOrCreate(uniqueId);
            sessions.putIfAbsent(
                    uniqueId,
                    new VersionedPlayerSession<>(uniqueId, snapshot, snapshot.databaseVersion())
            );
            return sessions.get(uniqueId).snapshot();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load DreamRPG player storage: " + uniqueId, exception);
        }
    }

    private void saveUntilClean(VersionedPlayerSession<PlayerStorageSnapshot> session) {
        while (true) {
            VersionedPlayerSession.SaveCandidate<PlayerStorageSnapshot> candidate = session.saveCandidate();
            if (!candidate.dirty()) return;
            try {
                long nextVersion = store.save(candidate.snapshot(), candidate.databaseVersion());
                session.completeSave(candidate, nextVersion);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Failed to save DreamRPG player storage: " + candidate.uniqueId(),
                        exception
                );
            }
        }
    }

    private VersionedPlayerSession<PlayerStorageSnapshot> session(UUID uniqueId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(uniqueId, "uniqueId")))
                .orElseThrow(() -> new IllegalStateException("Player storage is not loaded: " + uniqueId));
    }

    private void removeSaveChain(UUID uniqueId, CompletableFuture<Void> chain) {
        synchronized (saveChains) {
            saveChains.remove(uniqueId, chain);
        }
    }

    private static void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Player storage requires the main thread");
    }
}
