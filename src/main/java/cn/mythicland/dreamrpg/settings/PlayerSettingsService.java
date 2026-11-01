package cn.mythicland.dreamrpg.settings;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.settings.PlayerSettingsRepository.Options;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Owns main-thread player sessions and serializes their asynchronous setting writes. */
@InjectComponent
@ListenerComponent
@LifecycleComponent
public final class PlayerSettingsService implements Listener, ManagedLifecycle {

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final PlayerSettingsRepository repository;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PlayerSettingsService(JavaPlugin plugin, DreamRPGContext context) {
        this.plugin = plugin;
        this.lib = context.lib();
        this.repository = new PlayerSettingsRepository(context.database());
    }

    @Override
    public void load() {
        for (Player player : Bukkit.getOnlinePlayers()) loadLogged(player.getUniqueId());
    }

    @Override
    public void reload() {
    }

    @Override
    public void unload() {
        sessions.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadLogged(event.getPlayer().getUniqueId());
    }

    private void loadLogged(UUID playerId) {
        load(playerId).whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().severe(
                    "Failed to load player settings for " + playerId + ": " + failure.getMessage());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public boolean allowsMobKnockback(UUID playerId) {
        Session session = sessions.get(playerId);
        return session == null || session.options == null || session.options.allowMobKnockback();
    }

    public boolean autoPicksUpRewards(UUID playerId) {
        Session session = sessions.get(playerId);
        return session != null && session.options != null && session.options.autoPickup();
    }

    public CompletableFuture<Boolean> load(UUID playerId) {
        Session session = session(playerId);
        return session.loading.thenApply(loaded -> {
            if (session.options != null) return session.options.allowMobKnockback();
            return loaded.allowMobKnockback();
        });
    }

    private Session session(UUID playerId) {
        Session current = sessions.get(playerId);
        if (current != null) return current;
        Session created = new Session();
        sessions.put(playerId, created);
        created.loading = lib.supplyAsync(() -> {
            try {
                return repository.load(playerId);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load player settings: " + playerId, exception);
            }
        }).thenCompose(options -> lib.supplyOnMain(() -> {
            if (sessions.get(playerId) == created) created.options = options;
            return options;
        }));
        created.writes = created.loading.thenApply(ignored -> null);
        return created;
    }

    public CompletableFuture<Boolean> setAllowsMobKnockback(UUID playerId, boolean allowed) {
        return save(playerId, allowed, false);
    }

    public CompletableFuture<Boolean> setAutoPickup(UUID playerId, boolean enabled) {
        return save(playerId, enabled, true);
    }

    private CompletableFuture<Boolean> save(UUID playerId, boolean enabled, boolean autoPickup) {
        Session session = session(playerId);
        CompletableFuture<Boolean> saved = session.writes.thenCompose(ignored -> lib.supplyAsync(() -> {
            try {
                if (autoPickup) repository.setAutoPickup(playerId, enabled);
                else repository.setAllowsMobKnockback(playerId, enabled);
                return enabled;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to save player settings: " + playerId, exception);
            }
        })).thenCompose(value -> lib.supplyOnMain(() -> {
            if (sessions.get(playerId) == session) {
                Options previous = session.options;
                if (autoPickup) session.options = new Options(previous.allowMobKnockback(), value);
                else session.options = new Options(value, previous.autoPickup());
            }
            return value;
        }));
        // A failed save is reported to its caller; subsequent user changes can retry independently.
        session.writes = saved.handle((ignored, failure) -> null)
                .thenCompose(ignored -> session.loading.thenApply(options -> null));
        return saved;
    }

    private static final class Session {
        private Options options;
        private CompletableFuture<Options> loading;
        private CompletableFuture<Void> writes;
    }
}
