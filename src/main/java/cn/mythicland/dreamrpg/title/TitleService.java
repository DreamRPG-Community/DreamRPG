package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.event.EquippedTitleChangedEvent;
import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.component.ServiceComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Owns online title sessions and publishes immutable snapshots only after durable SQL writes.
 */
@InjectComponent
@ListenerComponent
@ServiceComponent(TitleApi.class)
public final class TitleService implements TitleApi, Listener, ManagedLifecycle {
    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final TitleCatalog catalog;
    private final TitleRepository repository;
    private final PluginTaskScope tasks;
    private final Map<UUID, TitleSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> operations = new HashMap<>();
    private final Map<UUID, TitleDefinition> applied = new HashMap<>();
    private final Set<UUID> expirationRequested = new HashSet<>();
    private Object generation = new Object();
    private boolean enabled;
    private BukkitTask expiryTask;

    public TitleService(JavaPlugin plugin, DreamRPGContext context, TitleCatalog catalog, PluginTaskScope tasks) {
        this.plugin = plugin;
        this.lib = context.lib();
        this.catalog = catalog;
        this.repository = new TitleRepository(context.database());
        this.tasks = tasks;
    }

    @Override
    public void load() {
        enabled = true;
        for (Player player : Bukkit.getOnlinePlayers()) join(player.getUniqueId());
        expiryTask = tasks.runTimer(20, 20, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) tick(player);
        });
    }

    @Override
    public void unload() {
        enabled = false;
        generation = new Object();
        tasks.cancel(expiryTask);
        expiryTask = null;
        sessions.clear();
        snapshots.clear();
        applied.clear();
        expirationRequested.clear();
        operations.clear();
    }

    @Override
    public List<TitleDefinition> definitions() {
        return List.copyOf(catalog.definitions().values());
    }

    @Override
    public Optional<TitleSnapshot> snapshot(UUID playerId) {
        return Optional.ofNullable(snapshots.get(playerId));
    }

    @Override
    public Optional<TitleDefinition> equipped(UUID playerId) {
        TitleSnapshot state = snapshots.get(playerId);
        if (state == null || !state.owns(state.selectedId(), System.currentTimeMillis())) return Optional.empty();
        return Optional.ofNullable(catalog.definitions().get(state.selectedId()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        join(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        snapshots.remove(id);
        applied.remove(id);
        expirationRequested.remove(id);
    }

    private void join(UUID playerId) {
        sessions.put(playerId, new Object());
        load(playerId).whenComplete((state, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.SEVERE,
                    "Failed to load player titles: " + playerId, failure);
        });
    }

    public CompletableFuture<TitleSnapshot> load(UUID playerId) {
        return execute(playerId, () -> repository.expire(playerId, System.currentTimeMillis()));
    }

    public CompletableFuture<TitleSnapshot> grant(UUID playerId, String id, long durationMillis) {
        catalog.require(id);
        return execute(playerId, () -> repository.grant(playerId, id, durationMillis, System.currentTimeMillis()));
    }

    public CompletableFuture<TitleSnapshot> revoke(UUID playerId, String id) {
        return execute(playerId, () -> repository.revoke(playerId, id));
    }

    public CompletableFuture<TitleSnapshot> select(UUID playerId, String id) {
        if (!id.isEmpty()) catalog.require(id);
        return execute(playerId, () -> {
            if (!id.isEmpty()) catalog.require(id);
            return repository.select(playerId, id, System.currentTimeMillis());
        });
    }

    private CompletableFuture<TitleSnapshot> execute(UUID playerId, SqlAction action) {
        MainThread.require("Title operations require the primary thread");
        if (!enabled) throw new IllegalStateException("Title service is closed");
        Object expectedGeneration = generation;
        Object expectedSession = sessions.get(playerId);
        var previous = operations.getOrDefault(playerId, CompletableFuture.completedFuture(null));
        var result = previous.thenCompose(ignored -> lib.supplyAsync(() -> {
            try {
                return action.run();
            } catch (SQLException exception) {
                throw new IllegalStateException("Title storage operation failed: " + playerId, exception);
            }
        })).thenCompose(state -> lib.supplyOnMain(() -> {
            if (generation != expectedGeneration)
                throw new IllegalStateException("Title service closed during operation");
            if (expectedSession != null && sessions.get(playerId) == expectedSession) {
                snapshots.put(playerId, state);
                expirationRequested.remove(playerId);
                refresh(playerId);
            }
            return state;
        }));
        // Each caller observes its own failure; later explicit operations may retry independently.
        var tail = result.handle((state, failure) -> (Void) null);
        operations.put(playerId, tail);
        tail.thenRun(() -> lib.runOnMain(() -> operations.remove(playerId, tail)));
        return result;
    }

    private void refresh(UUID playerId) {
        TitleDefinition next = equipped(playerId).orElse(null);
        if (Objects.equals(applied.get(playerId), next)) return;
        CombatApi combat = Bukkit.getServicesManager().load(CombatApi.class);
        if (combat == null) return; // Combat is registered after the core component graph starts.
        if (next == null) applied.remove(playerId);
        else applied.put(playerId, next);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            combat.refresh(player);
            Bukkit.getPluginManager().callEvent(new EquippedTitleChangedEvent(player));
        }
    }

    private void tick(Player player) {
        UUID playerId = player.getUniqueId();
        refresh(playerId);
        TitleSnapshot state = snapshots.get(playerId);
        if (state == null) return;
        var menus = lib.menuService();
        if (menus.currentMenu(player) instanceof TitleMenu) menus.refresh(player);
        if (state.selectedId().isEmpty() || state.owns(state.selectedId(), System.currentTimeMillis())) return;
        if (!expirationRequested.add(playerId)) return;
        execute(playerId, () -> repository.expire(playerId, System.currentTimeMillis()))
                .whenComplete((updated, failure) -> {
                    // A failed cleanup is reported once; an explicit player operation can reconcile it later.
                    if (failure != null) plugin.getLogger().log(Level.SEVERE,
                            "Failed to expire selected player title: " + playerId, failure);
                });
    }

    @FunctionalInterface
    private interface SqlAction {
        TitleSnapshot run() throws SQLException;
    }
}
