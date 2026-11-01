package cn.mythicland.dreamrpg.mob;

import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static cn.mythicland.framework.pagination.MenuPageLayout.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MythicMobMenuTest {
    @Test
    void expandsPagesAndKeepsInspectionReadOnlyAndSelectionPosition() {
        var service = mock(MythicMobService.class);
        var mobs = IntStream.range(0, 70).mapToObj(index -> new MobSummary(String.format("mob_%02d", index),
                "Mob " + index, "ZOMBIE", "dungeons/village/mobs/forest.yml")).toList();
        when(service.listMobs()).thenReturn(mobs);
        when(service.find(anyString())).thenAnswer(call -> mobs.stream().filter(mob -> mob.internalName().equals(call.getArgument(0))).findFirst());
        var gear = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        when(gear.clone()).thenReturn(gear);
        when(gear.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("Original equipment lore"));
        when(service.details(anyString())).thenAnswer(call -> Optional.of(new MobDetails(mobs.get(28),
                List.of(new MobDetails.Attribute("Health", "30")), List.of(new MobDetails.Equipment("头盔", "helmet:4", gear)))));
        var player = mock(Player.class);
        when(player.hasPermission("dreamrpg.admin")).thenReturn(true);
        var inventory = mock(Inventory.class);
        Map<Integer, ItemStack> slots = new HashMap<>();
        doAnswer(call -> { slots.clear(); return null; }).when(inventory).clear();
        doAnswer(call -> { slots.put(call.getArgument(0), call.getArgument(1)); return null; }).when(inventory).setItem(anyInt(), any());
        var menu = new MythicMobMenu(service);
        var menus = mock(MenuManager.class);
        doAnswer(call -> { menu.render(player, inventory); return null; }).when(menus).refresh(player);
        try (var buttons = mockStatic(MenuItems.class)) {
            buttons.when(() -> MenuItems.button(any(Material.class), anyString(), anyList())).thenAnswer(call -> mock(ItemStack.class));
            buttons.when(() -> MenuItems.button(any(Material.class), anyShort(), anyString(), anyList())).thenAnswer(call -> mock(ItemStack.class));
            buttons.when(() -> MenuItems.icon(any())).thenAnswer(call -> call.getArgument(0));
            menu.render(player, inventory);
            assertEquals(1, CONTENT_SLOTS.stream().filter(slots::containsKey).count());
            click(menu, player, menus, CONTENT_SLOTS.getFirst(), ClickType.RIGHT);
            assertEquals(28, CONTENT_SLOTS.stream().filter(slots::containsKey).count());
            click(menu, player, menus, NEXT_SLOT, ClickType.LEFT);
            click(menu, player, menus, CONTENT_SLOTS.getFirst(), ClickType.LEFT);
            click(menu, player, menus, 24, ClickType.LEFT);
            verify(service).details("mob_28");
            click(menu, player, menus, BACK_SLOT, ClickType.LEFT);
            click(menu, player, menus, 16, ClickType.LEFT);
            verify(meta).setLore(argThat(lore -> lore.contains("Original equipment lore") && lore.stream().anyMatch(line -> line.contains("头盔"))));
            click(menu, player, menus, CONTENT_SLOTS.getFirst(), ClickType.LEFT);
            verify(player, never()).getInventory();
            verify(service, never()).spawnAt(any(), any());
            click(menu, player, menus, BACK_SLOT, ClickType.LEFT);
            click(menu, player, menus, BACK_SLOT, ClickType.LEFT);
            click(menu, player, menus, CONTENT_SLOTS.getFirst(), ClickType.LEFT);
            verify(service, atLeastOnce()).find("mob_28");
            when(service.find("mob_28")).thenReturn(Optional.empty());
            menu.render(player, inventory);
            assertTrue(slots.containsKey(NEXT_SLOT), "Removing the selected mob during reload returns to the library");
            when(player.hasPermission("dreamrpg.admin")).thenReturn(false);
            click(menu, player, menus, CONTENT_SLOTS.getFirst(), ClickType.LEFT);
            verify(menus).close(player);
        }
    }

    private void click(MythicMobMenu menu, Player player, MenuManager menus, int slot, ClickType click) {
        var event = mock(InventoryClickEvent.class);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(click);
        menu.handleClick(player, event, menus);
    }
}
