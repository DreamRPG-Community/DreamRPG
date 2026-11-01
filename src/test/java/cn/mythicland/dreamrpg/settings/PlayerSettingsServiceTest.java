package cn.mythicland.dreamrpg.settings;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.framework.database.SqlDatabase;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerSettingsServiceTest {
    @Test
    void cachesBothSettingsOnlyAfterSuccessfulSavesAndRejectsDepartedSessionCallbacks() throws Exception {
        var plugin = mock(JavaPlugin.class);
        var context = mock(DreamRPGContext.class);
        var lib = mock(DreamLibApi.class);
        var database = mock(SqlDatabase.class);
        when(context.lib()).thenReturn(lib);
        when(context.database()).thenReturn(database);
        when(database.query(any())).thenReturn(new PlayerSettingsRepository.Options(true, false));
        var queued = new ArrayDeque<Runnable>();
        when(lib.supplyAsync(any())).thenAnswer(call -> {
            Supplier<?> action = call.getArgument(0);
            CompletableFuture<Object> future = new CompletableFuture<>();
            queued.add(() -> {
                try { future.complete(action.get()); }
                catch (RuntimeException failure) { future.completeExceptionally(failure); }
            });
            return future;
        });
        when(lib.supplyOnMain(any())).thenAnswer(call -> {
            Supplier<?> action = call.getArgument(0);
            return CompletableFuture.completedFuture(action.get());
        });
        var service = new PlayerSettingsService(plugin, context);
        UUID id = UUID.randomUUID();
        var loaded = service.load(id);
        assertFalse(loaded.isDone());
        queued.remove().run();
        assertTrue(loaded.join());
        var saved = service.setAutoPickup(id, true);
        assertFalse(service.autoPicksUpRewards(id));
        queued.remove().run();
        assertTrue(saved.join());
        assertTrue(service.autoPicksUpRewards(id));
        service.setAllowsMobKnockback(id, false);
        queued.remove().run();
        assertFalse(service.load(id).join());
        when(database.transaction(any())).thenThrow(new SQLException("Test database unavailable"));
        var failed = service.setAutoPickup(id, false);
        queued.remove().run();
        assertTrue(failed.isCompletedExceptionally());
        assertTrue(service.autoPicksUpRewards(id));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        service.onQuit(new PlayerQuitEvent(player, "quit"));
        service.load(id);
        service.unload();
        queued.remove().run();
        assertTrue(service.allowsMobKnockback(id));
        assertFalse(service.autoPicksUpRewards(id));
    }
}
