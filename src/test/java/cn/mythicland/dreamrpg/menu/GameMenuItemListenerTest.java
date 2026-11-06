package cn.mythicland.dreamrpg.menu;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.framework.bukkit.item.ItemMarker;
import cn.mythicland.framework.bukkit.item.ItemMarkerCodec;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameMenuItemListenerTest {
    @Test
    void givesOneMenuInAnEmptySlotAndDoesNotDuplicateItOnTheNextCheck() {
        var fixture = new Fixture();
        var icon = mock(ItemStack.class);
        var marked = mock(ItemStack.class);
        when(marked.getType()).thenReturn(Material.WATCH);
        when(fixture.inventory.firstEmpty()).thenReturn(5);
        when(fixture.markers.write(eq(icon), any())).thenReturn(marked);
        try (var buttons = mockStatic(MenuItems.class)) {
            buttons.when(() -> MenuItems.button(eq(Material.WATCH), anyString(), anyList())).thenReturn(icon);
            fixture.listener.ensureMenu(fixture.player);
            verify(fixture.inventory).setItem(5, marked);
            when(fixture.inventory.getContents()).thenReturn(new ItemStack[]{marked});
            when(fixture.markers.read(marked, "DreamRPGMenu")).thenReturn(Optional.of(
                    new ItemMarker("DreamRPGMenu", 1, Map.of("kind", "game_menu"))));
            fixture.listener.ensureMenu(fixture.player);
            verify(fixture.inventory, times(1)).setItem(anyInt(), any());
        }
    }

    @Test
    void fullInventoryIsNotOverwrittenAndRetryWaitsForAFreeSlot() {
        var fixture = new Fixture();
        when(fixture.inventory.firstEmpty()).thenReturn(-1);
        fixture.listener.ensureMenu(fixture.player);
        fixture.listener.ensureMenu(fixture.player);
        verify(fixture.player, times(1)).sendMessage(anyString());
        verify(fixture.inventory, never()).setItem(anyInt(), any());
        // Retry is driven by real inventory changes; no item is dropped into the world.
        var drop = new PlayerDropItemEvent(fixture.player, mock(Item.class));
        fixture.listener.onDrop(drop);
        verify(fixture.tasks).runLater(eq(1L), any(Runnable.class));
        fixture.listener.onQuit(new PlayerQuitEvent(fixture.player, ""));
        fixture.listener.onDrop(drop);
        verify(fixture.tasks, times(1)).runLater(eq(1L), any(Runnable.class));
    }

    @Test
    void recognizesLegacyMenuButNotAnOrdinaryClock() {
        var fixture = new Fixture();
        var clock = mock(ItemStack.class);
        when(clock.getType()).thenReturn(Material.WATCH);
        assertFalse(fixture.listener.isMenu(clock));
        var meta = mock(ItemMeta.class);
        when(clock.hasItemMeta()).thenReturn(true);
        when(clock.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§e武林指南 §7(右键点击)");
        assertTrue(fixture.listener.isMenu(clock));
        when(fixture.inventory.getContents()).thenReturn(new ItemStack[]{clock});
        fixture.listener.ensureMenu(fixture.player);
        verify(fixture.inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void rightClickOpensTheGameMenuOnlyAfterStorageIsReady() {
        var fixture = new Fixture();
        var clock = mock(ItemStack.class);
        when(clock.getType()).thenReturn(Material.WATCH);
        when(fixture.markers.read(clock, "DreamRPGMenu")).thenReturn(Optional.of(
                new ItemMarker("DreamRPGMenu", 1, Map.of("kind", "game_menu"))));
        var event = new PlayerInteractEvent(fixture.player, Action.RIGHT_CLICK_AIR, clock, null, null);
        fixture.listener.onInteract(event);
        assertTrue(event.isCancelled());
        verify(fixture.player, never()).performCommand(anyString());
        when(fixture.storage.isReady(fixture.player.getUniqueId())).thenReturn(true);
        fixture.listener.onInteract(event);
        verify(fixture.player).performCommand("menu");
    }

    private static final class Fixture {
        final DreamLibApi lib = mock(DreamLibApi.class);
        final ItemMarkerCodec markers = mock(ItemMarkerCodec.class);
        final PluginTaskScope tasks = mock(PluginTaskScope.class);
        final PlayerStorageService storage = mock(PlayerStorageService.class);
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final GameMenuItemListener listener;

        Fixture() {
            when(lib.itemMarkerCodec()).thenReturn(markers);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getContents()).thenReturn(new ItemStack[41]);
            when(markers.read(any(), anyString())).thenReturn(Optional.empty());
            listener = new GameMenuItemListener(lib, tasks, storage);
        }
    }
}
