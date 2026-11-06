package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TitleMenuTest {
    private static final TitleDefinition HERO = new TitleDefinition("hero", "&6勇者", Map.of(
            CombatStat.DAMAGE, new NumericRange(10, 20, false)));
    private static final TitleDefinition VETERAN = new TitleDefinition("veteran", "&a老兵", Map.of());

    @Test
    void sizesTheMenuByCatalogCountAndKeepsFullPagesForOverflow() {
        try (var fixture = new Fixture()) {
            int[] counts = {0, 1, 5, 7, 8, 14, 15, 21, 22, 28, 29};
            int[] sizes = {27, 27, 27, 27, 36, 36, 45, 45, 54, 54, 54};
            for (int index = 0; index < counts.length; index++) {
                List<TitleDefinition> definitions = new ArrayList<>();
                for (int title = 0; title < counts[index]; title++)
                    definitions.add(new TitleDefinition("title_" + title, "Title", Map.of()));
                fixture.definitions = definitions;
                assertEquals(sizes[index], fixture.menu.size(fixture.player), "Catalog count: " + counts[index]);
            }
        }
    }

    @Test
    void displaysExactlyTwoAdjacentUnitsForRemainingTitleTime() {
        long now = 1_000_000;
        assertEquals("&7到期时间: &a7d 0h 后", TitleMenu.expiryLine(now + Duration.ofDays(7).plusMinutes(2).toMillis(), now));
        assertEquals("&7到期时间: &a1d 3h 后", TitleMenu.expiryLine(now + Duration.ofDays(1).plusHours(3).plusMinutes(4).toMillis(), now));
        assertEquals("&7到期时间: &a0m 59s 后", TitleMenu.expiryLine(now + 59_000, now));
        assertEquals("&7到期时间: &a1m 0s 后", TitleMenu.expiryLine(now + 60_000, now));
        assertEquals("&7到期时间: &a1m 59s 后", TitleMenu.expiryLine(now + 119_000, now));
        assertEquals("&7到期时间: &a59m 59s 后", TitleMenu.expiryLine(now + 3_599_000, now));
        assertEquals("&7到期时间: &a1h 0m 后", TitleMenu.expiryLine(now + 3_600_000, now));
        assertEquals("&7到期时间: &a0m 1s 后", TitleMenu.expiryLine(now + 1, now));
        assertEquals("&7到期时间: &a永久", TitleMenu.expiryLine(0, now));
    }

    @Test
    void showsLockedAttributesWithoutAnUnequipButtonOrEmptyPlaceholder() {
        try (var fixture = new Fixture()) {
            fixture.definitions = List.of(HERO);
            fixture.render();
            assertEquals(List.of(10), new ArrayList<>(fixture.buttons.keySet()));
            assertEquals(Material.NAME_TAG, fixture.buttons.get(10).getType());
            List<String> lore = fixture.buttons.get(10).getItemMeta().getLore();
            assertTrue(lore.contains("§c未解锁"));
            assertTrue(lore.contains("§6§l物理攻击 §2§l+10-20"));
            fixture.click(10, false);
            fixture.click(10, true);
            verify(fixture.titles, never()).select(any(), anyString());
            fixture.definitions = List.of();
            fixture.render();
            assertTrue(fixture.buttons.isEmpty(), "An empty catalog must render no icons");
        }
    }

    @Test
    void highlightsOnlyTheEffectiveSelectionAndUsesRightClickToUnequip() {
        try (var fixture = new Fixture()) {
            fixture.definitions = List.of(HERO, VETERAN);
            fixture.state = new TitleSnapshot(Map.of("hero", 0L, "veteran", 0L), "hero");
            fixture.render();
            var selected = fixture.buttons.get(10).getItemMeta();
            verify(selected).addEnchant(Enchantment.DURABILITY, 1, true);
            verify(selected).addItemFlags(ItemFlag.HIDE_ENCHANTS);
            assertTrue(selected.getLore().contains("§a已佩戴该称号"));
            verify(fixture.buttons.get(11).getItemMeta(), never()).addEnchant(any(), anyInt(), anyBoolean());
            fixture.click(11, true);
            verify(fixture.titles, never()).select(any(), anyString());
            fixture.click(10, true);
            verify(fixture.titles).select(fixture.playerId, "");
            fixture.click(11, false);
            verify(fixture.titles).select(fixture.playerId, "veteran");
        }
    }

    @Test
    void treatsExpiredGrantsAsLockedAndRechecksOwnershipAfterRendering() {
        try (var fixture = new Fixture()) {
            fixture.definitions = List.of(HERO);
            fixture.state = new TitleSnapshot(Map.of("hero", 1L), "hero");
            fixture.render();
            var expired = fixture.buttons.get(10).getItemMeta();
            assertTrue(expired.getLore().contains("§c未解锁"));
            verify(expired, never()).addEnchant(any(), anyInt(), anyBoolean());
            fixture.state = new TitleSnapshot(Map.of("hero", 0L), "");
            fixture.render();
            fixture.state = new TitleSnapshot(Map.of(), "");
            fixture.click(10, false);
            verify(fixture.titles, never()).select(any(), anyString());
            fixture.click(49, false);
            verify(fixture.titles, never()).select(any(), anyString());
        }
    }

    @Test
    void rendersEveryAttributeWithTheEquipmentPaletteAndPreservesNumericText() {
        try (var fixture = new Fixture()) {
            Map<CombatStat, String> colors = Map.ofEntries(
                    Map.entry(CombatStat.DAMAGE, "§6"), Map.entry(CombatStat.DEFENSE, "§6"),
                    Map.entry(CombatStat.PHYSICAL_DEFENSE, "§6"), Map.entry(CombatStat.MAGIC_DAMAGE, "§d"),
                    Map.entry(CombatStat.MAGIC_DEFENSE, "§d"), Map.entry(CombatStat.HEALTH, "§a"),
                    Map.entry(CombatStat.HEALTH_REGEN, "§a"), Map.entry(CombatStat.CRIT_CHANCE, "§c"),
                    Map.entry(CombatStat.CRIT_DAMAGE, "§4"), Map.entry(CombatStat.LIFESTEAL, "§4"),
                    Map.entry(CombatStat.PENETRATION, "§f"), Map.entry(CombatStat.PENETRATION_PERCENT, "§f"),
                    Map.entry(CombatStat.MOVEMENT_SPEED, "§b"), Map.entry(CombatStat.EXPERIENCE_BONUS, "§b")
            );
            Map<CombatStat, NumericRange> bonuses = new HashMap<>();
            for (CombatStat stat : CombatStat.values())
                bonuses.put(stat, new NumericRange(-10, 20, stat.percentageAttribute()));
            fixture.definitions = List.of(new TitleDefinition("all_stats", "All stats", bonuses));
            fixture.render();
            List<String> lore = fixture.buttons.get(10).getItemMeta().getLore();
            for (CombatStat stat : CombatStat.values()) {
                String expected = colors.get(stat) + "§l" + stat.defaultDisplayName() + " §2§l-10-20";
                if (stat.percentageAttribute()) expected += "%";
                assertTrue(lore.contains(expected), "Missing equipment-style attribute: " + stat.configKey());
            }
        }
    }

    @Test
    void paginatesTheWholeCatalogIncludingLockedTitles() {
        try (var fixture = new Fixture()) {
            List<TitleDefinition> definitions = new ArrayList<>();
            for (int index = 0; index < 30; index++) {
                definitions.add(new TitleDefinition("title_" + index, "Title " + index, Map.of()));
            }
            fixture.definitions = definitions;
            fixture.render();
            assertEquals(29, fixture.buttons.size(), "First page contains 28 titles and next-page navigation");
            fixture.click(53, false);
            fixture.render();
            assertEquals(3, fixture.buttons.size(), "Second page contains two titles and previous-page navigation");
            fixture.click(10, false);
            verify(fixture.titles, never()).select(any(), anyString());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UUID playerId = UUID.randomUUID();
        final Player player = mock(Player.class);
        final TitleService titles = mock(TitleService.class);
        final DreamLibApi lib = mock(DreamLibApi.class);
        final MenuManager menus = mock(MenuManager.class);
        final Inventory inventory = mock(Inventory.class);
        final Map<Integer, ItemStack> buttons = new HashMap<>();
        final MockedConstruction<ItemStack> items;
        final TitleMenu menu;
        TitleSnapshot state = new TitleSnapshot(Map.of(), "");
        List<TitleDefinition> definitions = List.of();

        Fixture() {
            items = mockConstruction(ItemStack.class, (item, construction) -> {
                var meta = mock(ItemMeta.class);
                var lore = new ArrayList<String>();
                when(item.getType()).thenReturn((Material) construction.arguments().get(0));
                when(item.getItemMeta()).thenReturn(meta);
                when(meta.getLore()).thenAnswer(ignored -> List.copyOf(lore));
                doAnswer(call -> {
                    lore.clear();
                    List<String> lines = call.getArgument(0);
                    lore.addAll(lines);
                    return null;
                }).when(meta).setLore(anyList());
            });
            when(player.getUniqueId()).thenReturn(playerId);
            when(player.isOnline()).thenReturn(true);
            when(titles.definitions()).thenAnswer(ignored -> definitions);
            when(titles.snapshot(playerId)).thenAnswer(ignored -> Optional.of(state));
            when(titles.select(eq(playerId), anyString())).thenAnswer(call -> {
                state = new TitleSnapshot(state.expirations(), call.getArgument(1));
                return CompletableFuture.completedFuture(state);
            });
            when(lib.runOnMain(any())).thenAnswer(call -> {
                ((Runnable) call.getArgument(0)).run();
                return CompletableFuture.completedFuture(null);
            });
            doAnswer(ignored -> {
                buttons.clear();
                return null;
            }).when(inventory).clear();
            doAnswer(call -> {
                buttons.put(call.getArgument(0), call.getArgument(1));
                return null;
            })
                    .when(inventory).setItem(anyInt(), any());
            when(inventory.getItem(anyInt())).thenAnswer(call -> buttons.get(call.getArgument(0)));
            var view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(inventory);
            when(player.getOpenInventory()).thenReturn(view);
            menu = new TitleMenu(titles, lib);
            when(inventory.getSize()).thenAnswer(ignored -> menu.size(player));
            when(menus.currentMenu(player)).thenReturn(menu);
        }

        void render() {
            menu.render(player, inventory);
        }

        void click(int slot, boolean right) {
            var event = mock(InventoryClickEvent.class);
            when(event.getRawSlot()).thenReturn(slot);
            when(event.isLeftClick()).thenReturn(!right);
            when(event.isRightClick()).thenReturn(right);
            menu.handleClick(player, event, menus);
            verify(event).setCancelled(true);
        }

        @Override
        public void close() {
            items.close();
        }
    }
}
