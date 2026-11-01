package cn.mythicland.dreamrpg.listener;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import cn.mythicland.dreamrpg.display.DreamRPGDisplayService;
import cn.mythicland.dreamrpg.experience.ExperienceService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.framework.bukkit.player.PlayerLoadingGate;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DreamRPGRespawnTest {
    @Test
    void respawnsNextTickInTheDeathWorldAndSkipsDisconnectedPlayers() {
        var tasks = mock(PluginTaskScope.class);
        var context = mock(DreamRPGContext.class);
        var settings = mock(DreamRPGSettings.class);
        when(context.settings()).thenReturn(settings);
        when(settings.spawn()).thenReturn(new DreamRPGSettings.SpawnSettings("main_city", true, true));
        var spawns = mock(SpawnService.class);
        when(context.spawnService()).thenReturn(spawns);
        var listener = new DreamRPGListener(mock(JavaPlugin.class), tasks, context,
                mock(PlayerProfileService.class), mock(DreamRPGDisplayService.class),
                mock(PlayerStorageService.class), mock(ExperienceService.class), mock(PlayerLoadingGate.class));
        var player = mock(Player.class);
        var world = mock(World.class);
        var spawn = new Location(world, 123, 80, 456);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(spawn);
        when(spawns.location(world)).thenReturn(spawn);
        var respawn = new PlayerRespawnEvent(player, new Location(world, 0, 70, 0), true);
        listener.onRespawn(respawn);
        assertEquals(spawn, respawn.getRespawnLocation());
        listener.onDeath(new PlayerDeathEvent(player, new ArrayList<>(), 0, "death"));
        var action = ArgumentCaptor.forClass(Runnable.class);
        verify(tasks).runLater(eq(1L), action.capture());
        var spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(true);
        action.getValue().run();
        verify(spigot).respawn();
        when(player.isOnline()).thenReturn(false);
        action.getValue().run();
        verify(spigot, times(1)).respawn();
    }
}
