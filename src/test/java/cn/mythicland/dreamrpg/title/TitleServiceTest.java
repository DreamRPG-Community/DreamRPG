package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.database.SqlDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TitleServiceTest {
    @Test
    void publishesOnlyDurableCurrentSessionsAndDropsCallbacksAfterUnload() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var lib = mock(DreamLibApi.class);
        var context = mock(DreamRPGContext.class);
        var database = mock(SqlDatabase.class);
        var menus = mock(MenuManager.class);
        when(lib.menuService()).thenReturn(menus);
        when(context.lib()).thenReturn(lib);
        when(context.database()).thenReturn(database);
        var catalog = mock(TitleCatalog.class);
        var hero = new TitleDefinition("hero", "Hero", Map.of());
        when(catalog.definitions()).thenReturn(Map.of("hero", hero));
        when(catalog.require("hero")).thenReturn(hero);
        var queued = new ArrayDeque<Runnable>();
        when(lib.supplyAsync(any())).thenAnswer(call -> {
            Supplier<?> action = call.getArgument(0);
            var future = new CompletableFuture<Object>();
            queued.add(() -> {
                try {
                    future.complete(action.get());
                } catch (RuntimeException failure) {
                    future.completeExceptionally(failure);
                }
            });
            return future;
        });
        when(lib.supplyOnMain(any())).thenAnswer(call -> {
            try {
                return CompletableFuture.completedFuture(((Supplier<?>) call.getArgument(0)).get());
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        });
        when(lib.runOnMain(any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var services = mock(ServicesManager.class);
        var combat = mock(CombatApi.class);
        when(services.load(CombatApi.class)).thenReturn(combat);
        var granted = new TitleSnapshot(Map.of("hero", 0L), "hero");
        when(database.transaction(any())).thenReturn(granted);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var tasks = mock(PluginTaskScope.class);
            var timer = mock(BukkitTask.class);
            when(tasks.runTimer(anyLong(), anyLong(), any())).thenReturn(timer);
            var service = new TitleService(plugin, context, catalog, tasks);
            service.load();
            var refresh = ArgumentCaptor.forClass(Runnable.class);
            verify(tasks).runTimer(eq(20L), eq(20L), refresh.capture());
            service.onJoin(new PlayerJoinEvent(player, ""));
            assertTrue(service.snapshot(id).isEmpty());
            queued.remove().run();
            assertEquals(hero, service.equipped(id).orElseThrow());
            verify(combat).refresh(player);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(menus.currentMenu(player)).thenReturn(mock(TitleMenu.class));
            refresh.getValue().run();
            refresh.getValue().run();
            verify(menus, times(2)).refresh(player);
            when(database.transaction(any())).thenThrow(new SQLException("Test storage unavailable"));
            var failed = service.select(id, "hero");
            queued.remove().run();
            assertTrue(failed.isCompletedExceptionally());
            assertEquals(granted, service.snapshot(id).orElseThrow());
            doReturn(new TitleSnapshot(Map.of("hero", 1L), "hero")).when(database).transaction(any());
            var expired = service.load(id);
            queued.remove().run();
            assertTrue(expired.isDone());
            assertTrue(service.equipped(id).isEmpty());
            doReturn(new TitleSnapshot(Map.of("hero", 1L), "")).when(database).transaction(any());
            refresh.getValue().run();
            refresh.getValue().run();
            assertEquals(1, queued.size(), "Only one expiry write may be queued for the selected title");
            queued.remove().run();
            assertEquals("", service.snapshot(id).orElseThrow().selectedId());
            verify(menus, times(4)).refresh(player);
            when(menus.currentMenu(player)).thenReturn(null);
            clearInvocations(menus);
            refresh.getValue().run();
            verify(menus, never()).refresh(any());
            service.onQuit(new PlayerQuitEvent(player, ""));
            service.onJoin(new PlayerJoinEvent(player, ""));
            service.onQuit(new PlayerQuitEvent(player, ""));
            queued.remove().run();
            assertTrue(service.snapshot(id).isEmpty());
            service.onJoin(new PlayerJoinEvent(player, ""));
            service.unload();
            verify(tasks).cancel(timer);
            queued.remove().run();
            assertTrue(service.snapshot(id).isEmpty());
            assertThrows(IllegalStateException.class, () -> service.load(id));
        }
    }
}
