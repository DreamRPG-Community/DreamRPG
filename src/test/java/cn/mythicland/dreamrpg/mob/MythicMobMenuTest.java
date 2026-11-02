package cn.mythicland.dreamrpg.mob;

import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
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
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MythicMobMenuTest {
    private static final int HEADER_SLOT = 10;
    private static final int ATTRIBUTE_ACTION_SLOT = 11;
    private static final int EQUIPMENT_ACTION_SLOT = 12;
    private static final int KILL_ACTION_SLOT = 16;
    private static final int PREVIOUS_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int BACK_SLOT = 26;
    /**
     * Four rows of seven slots, matching the item library.
     */
    private static final List<Integer> CONTENT_SLOTS = cn.mythicland.framework.pagination.MenuPageLayout.CONTENT_SLOTS;
    /**
     * How many mobs the fixture loads into a single folder.
     */
    private static final int MEMBER_COUNT = 70;
    /**
     * An expanded folder renders only its members.
     */
    private static final int EXPANDED_ROW_COUNT = MEMBER_COUNT;

    @Test
    void sharesTheItemLibraryFolderLayout() {
        var fixture = new Fixture();
        fixture.openLibrary();

        assertEquals(1, fixture.renderedCount(), "A collapsed folder renders a single row");
        assertNoDecorativeSlots(fixture.slots);

        fixture.click(fixture.contentSlot(0), ClickType.RIGHT);
        assertEquals(CONTENT_SLOTS.size(), fixture.renderedCount(),
                "An expanded folder fills the first page with members"
        );

        fixture.click(NEXT_PAGE_SLOT, ClickType.LEFT);
        fixture.click(NEXT_PAGE_SLOT, ClickType.LEFT);
        assertEquals(EXPANDED_ROW_COUNT - CONTENT_SLOTS.size() * 2, fixture.renderedCount(),
                "The final page shows only the members that did not fit on the first page"
        );
    }

    @Test
    void keepsInspectionReadOnly() {
        var fixture = new Fixture();
        fixture.openLibrary();
        fixture.click(fixture.contentSlot(0), ClickType.RIGHT);
        fixture.click(fixture.contentSlot(0), ClickType.LEFT);

        fixture.click(ATTRIBUTE_ACTION_SLOT, ClickType.LEFT);
        assertFalse(fixture.slots.containsKey(4), "Details do not add a mob egg above the content grid");
        assertTrue(fixture.slots.containsKey(BACK_SLOT), "The back arrow occupies the last slot");
        assertEquals(3, fixture.renderedCount(),
                "Health, physical attack and the equipped physical defence are listed"
        );

        fixture.click(BACK_SLOT, ClickType.LEFT);
        fixture.click(EQUIPMENT_ACTION_SLOT, ClickType.LEFT);
        assertTrue(fixture.slots.containsKey(BACK_SLOT), "The equipment page keeps the back arrow last too");
        fixture.click(BACK_SLOT, ClickType.LEFT);

        verify(fixture.player, never()).getInventory();
        verify(fixture.service, never()).spawnAt(any(), any());
        verify(fixture.service, never()).createSpawnPoint(any(), any());
        verify(fixture.service, never()).killNearby(any(), any());
        verify(fixture.service, never()).deleteSpawnPoint(any());
    }

    @Test
    void confirmsDestructiveActionsBeforeApplyingThem() {
        var fixture = new Fixture();
        fixture.openLibrary();
        fixture.click(fixture.contentSlot(0), ClickType.RIGHT);
        fixture.click(fixture.contentSlot(0), ClickType.LEFT);

        fixture.click(KILL_ACTION_SLOT, ClickType.LEFT);
        verify(fixture.service, never()).killNearby(any(), any());

        fixture.click(KILL_ACTION_SLOT, ClickType.LEFT);
        verify(fixture.service).killNearby(fixture.player, "mob_00");
    }

    @Test
    void hidesDefenceRowsWhenTheMobWearsNoEquipment() {
        var fixture = new Fixture();
        fixture.wearingNothing = true;
        fixture.openLibrary();
        fixture.click(fixture.contentSlot(0), ClickType.RIGHT);
        fixture.click(fixture.contentSlot(0), ClickType.LEFT);
        fixture.click(ATTRIBUTE_ACTION_SLOT, ClickType.LEFT);

        assertEquals(2, fixture.renderedCount(),
                "Without equipment only the configured health and physical attack rows are shown"
        );
    }

    @Test
    void showsDefenceRowsContributedByWornEquipment() {
        var fixture = new Fixture();
        fixture.openLibrary();
        fixture.click(fixture.contentSlot(0), ClickType.RIGHT);
        fixture.click(fixture.contentSlot(0), ClickType.LEFT);
        fixture.click(ATTRIBUTE_ACTION_SLOT, ClickType.LEFT);

        assertTrue(fixture.renderedCount() >= 3,
                "Worn equipment adds its physical defence attribute to the RPG attribute page"
        );
    }

    @Test
    void leavesEmptyEquipmentPageBlankExceptForBack() {
        var fixture = new Fixture();
        fixture.wearingNothing = true;
        fixture.openLibrary();
        fixture.click(10, ClickType.LEFT);
        fixture.click(EQUIPMENT_ACTION_SLOT, ClickType.LEFT);

        assertEquals(27, fixture.menuSize());
        assertEquals(java.util.Set.of(26), fixture.slots.keySet());
        fixture.click(26, ClickType.LEFT);
        assertEquals(27, fixture.menuSize());
        assertTrue(fixture.slots.containsKey(HEADER_SLOT));
    }

    @Test
    void growsEquipmentPagesAfterEachSevenEntriesAndKeepsBackSeparateFromNext() {
        int[] counts = {1, 7, 8, 14, 15, 21, 22, 28, 29};
        int[] sizes = {27, 27, 36, 36, 45, 45, 54, 54, 54};
        for (int index = 0; index < counts.length; index++) {
            var fixture = new Fixture();
            fixture.equipmentCount = counts[index];
            fixture.openLibrary();
            fixture.click(10, ClickType.LEFT);
            fixture.click(EQUIPMENT_ACTION_SLOT, ClickType.LEFT);
            assertEquals(sizes[index], fixture.menuSize(), "Equipment count: " + counts[index]);
            assertEquals(Math.min(counts[index], 28), fixture.renderedCount());
            assertTrue(fixture.slots.containsKey(sizes[index] - 1));
            assertFalse(fixture.slots.containsKey(4));
            if (counts[index] > 28) {
                fixture.click(52, ClickType.LEFT);
                assertEquals(1, fixture.renderedCount());
                fixture.click(45, ClickType.LEFT);
                assertEquals(28, fixture.renderedCount());
            }
            fixture.click(sizes[index] - 1, ClickType.LEFT);
            assertEquals(27, fixture.menuSize());
        }
    }

    @Test
    void sizesSpawnerManagementFromItsPointsWithoutAMobEgg() {
        var fixture = new Fixture();
        when(fixture.service.listSpawnPoints(anyString())).thenReturn(IntStream.range(0, 8)
                .mapToObj(index -> new SpawnPoint("point_" + index, "mob_00", "world", 1, 2, 3)).toList());
        fixture.openLibrary();
        fixture.click(10, ClickType.LEFT);
        fixture.click(13, ClickType.LEFT);
        assertEquals(36, fixture.menuSize());
        assertEquals(8, fixture.renderedCount());
        assertFalse(fixture.slots.containsKey(4));
        assertTrue(fixture.slots.containsKey(35));
        fixture.click(10, ClickType.SHIFT_LEFT);
        verify(fixture.player, never()).sendMessage(anyString());
        verify(fixture.service, never()).spawnPointLocation(anyString());
        verify(fixture.service, never()).deleteSpawnPoint(anyString());
        fixture.click(35, ClickType.LEFT);
        assertEquals(27, fixture.menuSize());
    }

    private void assertNoDecorativeSlots(Map<Integer, ItemStack> slots) {
        assertTrue(slots.keySet().stream().allMatch(slot -> CONTENT_SLOTS.contains(slot)
                        || slot == PREVIOUS_PAGE_SLOT
                        || slot == NEXT_PAGE_SLOT
                        || slot == BACK_SLOT
                        || slot == HEADER_SLOT),
                "The library page only renders entries, arrows and the header, without a decorative border"
        );
    }

    /**
     * Shared menu harness so each test states only the behaviour it exercises.
     */
    private static final class Fixture {
        private final MythicMobService service = mock(MythicMobService.class);
        private final CombatApi combat = mock(CombatApi.class);
        private final Player player = mock(Player.class);
        private final MenuManager menus = mock(MenuManager.class);
        private final Inventory inventory = mock(Inventory.class);
        private final Map<Integer, ItemStack> slots = new HashMap<>();
        private final MythicMobMenu menu;
        private boolean wearingNothing;
        private int equipmentCount = 1;

        private Fixture() {
            var mobs = IntStream.range(0, MEMBER_COUNT)
                    .mapToObj(index -> new MobSummary(String.format("mob_%02d", index), "Mob " + index,
                            "ZOMBIE", "dungeons/village/mobs/forest.yml"
                    ))
                    .toList();
            when(service.listMobs()).thenReturn(mobs);
            when(service.find(anyString())).thenAnswer(call -> mobs.stream()
                    .filter(mob -> mob.internalName().equals(call.getArgument(0)))
                    .findFirst());
            var gear = mock(ItemStack.class);
            when(gear.clone()).thenReturn(gear);
            when(gear.getItemMeta()).thenReturn(mock(ItemMeta.class));
            when(service.details(anyString())).thenAnswer(call -> Optional.of(new MobDetails(mobs.getFirst(),
                    List.of(new MobDetails.Attribute(MythicMobService.HEALTH_ATTRIBUTE, "20"),
                            new MobDetails.Attribute(MythicMobService.DAMAGE_ATTRIBUTE, "5")
                    ),
                    wearingNothing ? List.of() : IntStream.range(0, equipmentCount)
                            .mapToObj(index -> new MobDetails.Equipment("头盔", "helmet:4", gear)).toList()
            )));
            when(combat.parseItem(any())).thenReturn(new CombatItemStats(
                    Map.of(CombatStat.PHYSICAL_DEFENSE, new NumericRange(12, 12, false)), List.of()));
            when(player.hasPermission("dreamrpg.admin")).thenReturn(true);
            doAnswer(call -> {
                slots.clear();
                return null;
            }).when(inventory).clear();
            doAnswer(call -> {
                int slot = call.getArgument(0);
                assertTrue(slot >= 0 && slot < menuSize(), "Rendered buttons must fit inside the current inventory");
                slots.put(slot, call.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            menu = new MythicMobMenu(service, combat);
            when(inventory.getSize()).thenAnswer(call -> menuSize());
            doAnswer(call -> {
                menu.render(player, inventory);
                return null;
            }).when(menus).refresh(player);
            doAnswer(call -> {
                menu.render(player, inventory);
                return null;
            }).when(menus).open(player, menu);
        }

        /**
         * Reopens the library so every test starts from the collapsed first page.
         */
        private void openLibrary() {
            try (MockedStatic<MenuItems> buttons = mockStatic(MenuItems.class)) {
                stubButtons(buttons);
                menu.open(player, menus);
            }
        }

        private int menuSize() {
            return menu.size(player);
        }

        private int renderedCount() {
            return (int) slots.keySet().stream()
                    .filter(CONTENT_SLOTS::contains)
                    .count();
        }

        private int contentSlot(int index) {
            return CONTENT_SLOTS.get(index);
        }

        private void click(int slot, ClickType click) {
            try (MockedStatic<MenuItems> buttons = mockStatic(MenuItems.class)) {
                stubButtons(buttons);
                var event = mock(InventoryClickEvent.class);
                when(event.getRawSlot()).thenReturn(slot);
                when(event.getClick()).thenReturn(click);
                menu.handleClick(player, event, menus);
            }
        }

        private void stubButtons(MockedStatic<MenuItems> buttons) {
            buttons.when(() -> MenuItems.button(any(Material.class), anyString(), anyList()))
                    .thenAnswer(call -> mock(ItemStack.class));
            buttons.when(() -> MenuItems.button(any(Material.class), anyShort(), anyString(), anyList()))
                    .thenAnswer(call -> mock(ItemStack.class));
            buttons.when(() -> MenuItems.icon(any())).thenAnswer(call -> call.getArgument(0));
        }
    }
}
