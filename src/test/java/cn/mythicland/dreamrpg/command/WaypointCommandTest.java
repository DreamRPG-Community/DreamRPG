package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamworld.api.DreamWorldApi;
import cn.mythicland.dreamworld.api.WorldSpawn;
import cn.mythicland.dreamworldregion.api.DreamWorldRegionApi;
import cn.mythicland.dreamworldregion.api.LandmarkDefinition;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.player.PlayerLoadingGate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WaypointCommandTest {
    @TempDir
    Path directory;

    @Test
    void aliasesUseLandmarksAndWorldSpawnsWhileDisabledLocationsStayClosed() throws Exception {
        Files.writeString(directory.resolve("waypoints.yml"), """
                waypoints:
                  gate:
                    name: Gate
                    landmark: old_gate
                    aliases: [g]
                  town:
                    name: Town
                    world: main_city
                  closed:
                    name: Closed
                    landmark: old_gate
                    enabled: false
                """);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.isEnabled()).thenReturn(true);
        DreamLibApi lib = mock(DreamLibApi.class);
        var content = new cn.mythicland.framework.bukkit.content.ContentWorkspace(directory);
        when(lib.content()).thenReturn(content);
        when(lib.playerLoadingGate()).thenReturn(mock(PlayerLoadingGate.class));
        when(lib.runOnMain(any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        DreamWorldApi worlds = mock(DreamWorldApi.class);
        DreamWorldRegionApi landmarks = mock(DreamWorldRegionApi.class);
        ServicesManager services = mock(ServicesManager.class);
        when(services.load(DreamWorldApi.class)).thenReturn(worlds);
        when(services.load(DreamWorldRegionApi.class)).thenReturn(landmarks);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        World world = mock(World.class);
        when(worlds.find("main_city")).thenReturn(Optional.of(world));
        when(worlds.findSpawn("main_city")).thenReturn(Optional.of(new WorldSpawn("main_city", 1, 2, 3, 0, 0)));
        when(player.teleport(any(org.bukkit.Location.class))).thenReturn(true);
        when(landmarks.findLandmark("old_gate")).thenReturn(Optional.of(
                new LandmarkDefinition("old_gate", "Gate", "main_city", 4, 5, 6, 90, 0)
        ));
        when(landmarks.teleport(player, "old_gate")).thenReturn(CompletableFuture.completedFuture(true));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            WaypointCommand command = new WaypointCommand(plugin, lib);
            command.load();
            command.teleport(context(player, "g"));
            verify(landmarks).teleport(player, "old_gate");
            verify(landmarks).findLandmark("old_gate");
            command.teleport(context(player, "town"));
            verify(player).teleport(any(org.bukkit.Location.class));
            command.teleport(context(player, "closed"));
            verifyNoMoreInteractions(landmarks);
        }
    }

    @Test
    void rejectsAmbiguousDestinationsAndRetainsPreviousConfigurationOnReload() throws Exception {
        Path file = directory.resolve("waypoints.yml");
        Files.writeString(file, "waypoints:\n  one: {name: One, world: main_city}\n");
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        DreamLibApi lib = mock(DreamLibApi.class);
        var content = new cn.mythicland.framework.bukkit.content.ContentWorkspace(directory);
        when(lib.content()).thenReturn(content);
        when(lib.playerLoadingGate()).thenReturn(mock(PlayerLoadingGate.class));
        DreamWorldApi worlds = mock(DreamWorldApi.class);
        DreamWorldRegionApi landmarks = mock(DreamWorldRegionApi.class);
        ServicesManager services = mock(ServicesManager.class);
        when(services.load(DreamWorldApi.class)).thenReturn(worlds);
        when(services.load(DreamWorldRegionApi.class)).thenReturn(landmarks);
        Player player = mock(Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            WaypointCommand command = new WaypointCommand(plugin, lib);
            command.load();
            Files.writeString(file, "waypoints:\n  one: {name: One, world: main_city, landmark: gate}\n");
            assertThrows(IllegalStateException.class, command::reload);
            command.teleport(context(player, "one"));
            verify(worlds).find("main_city");
        }
    }

    private static CommandContext context(Player player, String destination) {
        CommandContext context = mock(CommandContext.class);
        when(context.sender()).thenReturn(player);
        when(context.arguments()).thenReturn(List.of(destination));
        when(context.argument(0)).thenReturn(destination);
        return context;
    }
}
