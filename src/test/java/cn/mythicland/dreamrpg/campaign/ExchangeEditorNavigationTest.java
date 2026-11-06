package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ExchangeType;
import cn.mythicland.dreamrpgitems.api.MythicItemDetails;
import cn.mythicland.dreamrpgitems.api.MythicItemIdentity;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExchangeEditorNavigationTest {
    @Test
    void displayTypeControlSavesWithoutMovingTheOfferIntoMainlineTasks() {
        try (Fixture fixture = new Fixture()) {
            InventoryClickEvent typeClick = fixture.click(47);
            fixture.editor.onClick(typeClick);
            verify(typeClick).setCancelled(true);
            fixture.save();
            verify(fixture.catalog).saveOffer(isNull(), argThat(offer -> offer.type() == ExchangeType.UPGRADE), isNull());
        }
    }

    @Test
    void keepsEditorOpenWhileSavingAndReturnsDirectlyWhenSaved() {
        try (Fixture fixture = new Fixture()) {
            fixture.save();
            verify(fixture.player, never()).closeInventory();
            verify(fixture.back, never()).accept(any());

            fixture.saved.complete(null);

            verify(fixture.back).accept(fixture.player);
            verify(fixture.player, never()).closeInventory();
        }
    }

    @Test
    void doesNotReopenParentAfterPlayerClosesWhileSaving() {
        try (Fixture fixture = new Fixture()) {
            fixture.save();
            InventoryCloseEvent close = mock(InventoryCloseEvent.class);
            when(close.getPlayer()).thenReturn(fixture.player);
            when(close.getInventory()).thenReturn(fixture.inventory);
            fixture.editor.onClose(close);

            fixture.saved.complete(null);

            verify(fixture.back, never()).accept(any());
        }
    }

    @Test
    void failedSaveReturnsInputsAndAllowsAnotherAttempt() {
        try (Fixture fixture = new Fixture()) {
            fixture.save();
            fixture.saved.completeExceptionally(new IllegalStateException("Save failed"));
            verify(fixture.player.getInventory(), times(2)).addItem(any(ItemStack.class));
            fixture.saved = new CompletableFuture<>();

            fixture.save();
            fixture.saved.complete(null);

            verify(fixture.catalog, times(2)).saveOffer(isNull(), any(ExchangeOffer.class), isNull());
            verify(fixture.back).accept(fixture.player);
            verify(fixture.player, never()).closeInventory();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final DreamRPGPlugin plugin = mock(DreamRPGPlugin.class);
        private final DreamLibApi lib = mock(DreamLibApi.class);
        private final CampaignCatalog catalog = mock(CampaignCatalog.class);
        private final Player player = mock(Player.class);
        private final Inventory inventory = mock(Inventory.class);
        private final Map<Integer, ItemStack> slots = new HashMap<>();
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @SuppressWarnings("unchecked")
        private final Consumer<Player> back = mock(Consumer.class);
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<MenuItems> menuItems = mockStatic(MenuItems.class);
        private final ExchangeEditor editor = new ExchangeEditor(plugin, catalog, lib);
        private CompletableFuture<Void> saved = new CompletableFuture<>();

        private Fixture() {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission("dreamrpg.admin")).thenReturn(true);
            InventoryView view = mock(InventoryView.class);
            when(player.getOpenInventory()).thenReturn(view);
            when(view.getTopInventory()).thenReturn(inventory);
            PlayerInventory playerInventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(playerInventory);
            when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            doAnswer(call -> {
                tasks.add(call.getArgument(1));
                return null;
            }).when(lib).runLater(anyLong(), any(Runnable.class));
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), anyString()))
                    .thenAnswer(call -> {
                        when(inventory.getHolder()).thenReturn(call.getArgument(0));
                        return inventory;
                    });
            when(inventory.getItem(anyInt())).thenAnswer(call -> slots.get(call.getArgument(0)));
            when(inventory.getContents()).thenAnswer(call -> {
                ItemStack[] contents = new ItemStack[54];
                slots.forEach((slot, item) -> contents[slot] = item);
                return contents;
            });
            doAnswer(call -> {
                slots.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            MythicItemService items = mock(MythicItemService.class);
            when(plugin.items()).thenReturn(items);
            when(items.identify(any())).thenReturn(Optional.of(new MythicItemIdentity("test_item", "revision")));
            when(items.find("test_item")).thenReturn(Optional.of(mock(MythicItemDetails.class)));
            when(catalog.saveOffer(isNull(), any(ExchangeOffer.class), isNull())).thenAnswer(call -> saved);
            editor.create(player, UUID.randomUUID(), null, back);
            drainTasks();
        }

        private void save() {
            slots.put(0, new ItemStack(Material.DIAMOND));
            InventoryClickEvent change = click(49);
            editor.onClick(change);
            slots.put(49, new ItemStack(Material.EMERALD));
            drainTasks();
            editor.onClick(click(53));
            drainTasks();
        }

        private void drainTasks() {
            while (!tasks.isEmpty()) tasks.remove().run();
        }

        private InventoryClickEvent click(int slot) {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getWhoClicked()).thenReturn(player);
            doReturn(player.getOpenInventory()).when(event).getView();
            when(event.getClick()).thenReturn(ClickType.LEFT);
            when(event.getRawSlot()).thenReturn(slot);
            return event;
        }

        @Override
        public void close() {
            menuItems.close();
            bukkit.close();
        }
    }
}
