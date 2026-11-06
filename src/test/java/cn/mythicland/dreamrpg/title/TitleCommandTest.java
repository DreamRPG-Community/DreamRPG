package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.framework.bukkit.command.BukkitCommandBridge;
import cn.mythicland.framework.bukkit.command.CommandRouteCompiler;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TitleCommandTest {
    @Test
    void bareAliasSendsUsageAndOpenSubcommandOpensTheMenu() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        var context = mock(DreamRPGContext.class);
        var lib = mock(DreamLibApi.class);
        var menus = mock(MenuManager.class);
        when(context.lib()).thenReturn(lib);
        when(lib.menuService()).thenReturn(menus);
        when(lib.runOnMain(any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        var titles = mock(TitleService.class);
        UUID id = UUID.randomUUID();
        when(titles.load(id)).thenReturn(CompletableFuture.completedFuture(new TitleSnapshot(Map.of(), "")));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var handlers = new TitleCommand(plugin, context, mock(TitleCatalog.class), titles);
        var router = new BukkitCommandBridge(plugin, new CommandRouteCompiler(null).compileBound(handlers));
        var command = mock(Command.class);
        assertTrue(router.onCommand(player, command, "ch", new String[0]));
        verify(player).sendMessage(contains("/ch open"));
        verifyNoInteractions(titles, menus);
        assertTrue(router.onCommand(player, command, "ch", new String[]{"open"}));
        verify(titles).load(id);
        verify(menus).open(eq(player), isA(TitleMenu.class));
    }
}
