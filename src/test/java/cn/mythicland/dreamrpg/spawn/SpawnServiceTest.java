package cn.mythicland.dreamrpg.spawn;

import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import cn.mythicland.dreamworld.api.DreamWorldApi;
import cn.mythicland.dreamworld.api.WorldSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnServiceTest {
    @Test
    void prefersEachWorldsCustomSpawnAndUsesNativeSpawnOnlyWhenUnconfigured() {
        var services = mock(ServicesManager.class);
        var worlds = mock(DreamWorldApi.class);
        when(services.load(DreamWorldApi.class)).thenReturn(worlds);
        var city = mock(World.class);
        var dungeon = mock(World.class);
        when(worlds.find("main_city")).thenReturn(Optional.of(city));
        when(worlds.findLogicalName(dungeon)).thenReturn(Optional.of("dungeon"));
        var nativeSpawn = new Location(dungeon, 10, 75, 20);
        when(dungeon.getSpawnLocation()).thenReturn(nativeSpawn);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            var spawns = new SpawnService(new DreamRPGSettings.SpawnSettings("main_city", true, true));
            assertEquals(nativeSpawn, spawns.location(dungeon));
            var custom = new WorldSpawn("dungeon", 50.5, 80, 60.5, 90, 0);
            when(worlds.findSpawn("dungeon")).thenReturn(Optional.of(custom));
            var actual = spawns.location(dungeon);
            assertEquals(custom.toLocation(dungeon), actual);
            actual.add(20, 0, 0);
            assertEquals(custom.toLocation(dungeon), spawns.location(dungeon));
        }
    }
}
