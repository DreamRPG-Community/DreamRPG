package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.menu.MenuItems;
import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.menu.MenuView;
import cn.mythicland.dreamlib.menu.StatefulMenuView;
import cn.mythicland.dreamlib.item.ItemMarker;
import cn.mythicland.dreamlib.menu.PageWindow;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamnpc.api.DreamNPCApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ItemExchangeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static cn.mythicland.dreamlib.menu.MenuPageLayout.*;

/**
 * NPC offer and administration menus.
 */
@InjectComponent
public final class CampaignMenus {
    private final DreamRPGPlugin plugin;
    private final DreamLibApi lib;
    private final CampaignCatalog catalog;
    private final ItemExchangeService exchanges;
    private final MenuService menus;
    private final ExchangeEditor editor;

    public CampaignMenus(
            DreamRPGPlugin plugin,
            CampaignCatalog catalog,
            ItemExchangeService exchanges,
            DreamLibApi lib,
            ExchangeEditor editor
    ) {
        this.plugin = plugin;
        this.lib = lib;
        this.catalog = catalog;
        this.exchanges = exchanges;
        this.menus = lib.menuService();
        this.editor = editor;
    }

    static boolean canPlaceSortDrag(
            int slot, int oldAmount, int newAmount, boolean cursorEmpty, boolean targetEmpty, boolean sameOffer
    ) {
        return CONTENT_SLOTS.contains(slot) && oldAmount > 0 && oldAmount == newAmount
                && cursorEmpty && targetEmpty && sameOffer;
    }

    public void openNpc(Player player, UUID npcId) {
        DreamNPCApi npcs = Bukkit.getServicesManager().load(DreamNPCApi.class);
        var npc = npcs.find(npcId).orElse(null);
        if (npc == null) {
            player.sendMessage("§c该NPC当前不可用, 请重新选择。");
            return;
        }
        menus.open(player, new OfferMenu(npc.name(), () -> catalog.placementsForNpc(npcId)));
    }

    public void openNpcEditor(Player player, UUID npcId) {
        if (!player.hasPermission("dreamrpg.admin")) return;
        DreamNPCApi npcs = Bukkit.getServicesManager().load(DreamNPCApi.class);
        var npc = npcs.find(npcId).orElse(null);
        if (npc == null) {
            player.sendMessage("§c该NPC当前不可用。");
            return;
        }
        menus.open(player, new NpcEditor(npcId, npc.name()));
    }

    private final class NpcEditor implements StatefulMenuView {
        private static final String SORT_MARKER = "DreamRPGOfferSort";
        private final UUID npc;
        private final String name;
        private boolean newTask;
        private int page;
        private List<ExchangeOffer> rendered = List.of();
        private List<ExchangeOffer> original;
        private List<ExchangeOffer> sortingSlots;
        private String sortSession;
        private boolean sortSaving;

        private NpcEditor(UUID npc, String name) {
            this.npc = npc;
            this.name = name;
            newTask = false;
        }

        private List<ExchangeOffer> offers() {
            return catalog.placementsForNpc(npc);
        }

        @Override
        public String title(Player player) {
            return LegacyText.colorize(name + " &8· 编辑");
        }

        @Override
        public int size(Player player) {
            return 54;
        }

        @Override
        public void render(Player player, Inventory inventory) {
            inventory.clear();
            rendered = new ArrayList<>(sortingSlots == null ? offers() : sortingSlots);
            PageWindow window = PageWindow.of(rendered.size(), CONTENT_SLOTS.size(), page);
            page = window.page();
            forEachEntry(rendered, page, (slot, offer) -> {
                if (offer == null) return;
                ItemStack icon = exchanges.icon(offer);
                if (sortingSlots != null) {
                    icon = lib.itemMarkerCodec().write(icon, new ItemMarker(SORT_MARKER, 1,
                            Map.of("session", sortSession, "offer", offer.id())));
                }
                inventory.setItem(slot, icon);
            });
            if (window.hasPrevious()) inventory.setItem(45, MenuItems.button(Material.ARROW, "&a上一页", List.of()));
            if (window.hasNext()) inventory.setItem(50, MenuItems.button(Material.ARROW, "&a下一页", List.of()));
            if (sortingSlots == null) {
                inventory.setItem(52, MenuItems.button(Material.SLIME_BALL, "&a调整顺序", List.of(
                        "&7调整当前NPC的项目位置", " ", "&e点击编辑!"
                )));
            } else {
                inventory.setItem(52, MenuItems.button(Material.MAGMA_CREAM, "&a确认顺序", List.of(
                        "&7点击项目后放到新的位置", " ", "&e点击确认!"
                )));
            }
            inventory.setItem(53, MenuItems.button(Material.BOOK, "&a新增项目", List.of(
                    "&7选择要新增的项目类型", " ",
                    newTask ? "&a▶ 主线任务" : "&7  主线任务",
                    newTask ? "&7  普通兑换" : "&a▶ 普通兑换",
                    " ", "&e左键切换, 右键新增!"
            )));
        }

        @Override
        public void handleClick(Player player, InventoryClickEvent event, MenuService service) {
            if (!player.hasPermission("dreamrpg.admin")) {
                menus.close(player);
                return;
            }
            int slot = event.getRawSlot();
            if (sortSaving) return;
            if (slot == 52 && event.getClick() == ClickType.LEFT) {
                if (sortingSlots == null) beginSort(player);
                else confirmSort(player, event);
                return;
            }
            if (slot == 53 && sortingSlots == null) {
                if (event.getClick() == ClickType.LEFT) {
                    newTask = !newTask;
                    menus.refresh(player);
                }
                if (event.getClick() == ClickType.RIGHT) {
                    editor.create(player, npc, newTask ? CampaignCatalog.NPC_TASK_STAGE : null, this::reopen);
                }
                return;
            }
            if (event.getClick() == ClickType.LEFT && slot == 45 && page > 0) {
                if (sortingSlots != null && !capturePage(player, event.getView().getTopInventory())) return;
                page--;
                menus.refresh(player);
                return;
            }
            if (event.getClick() == ClickType.LEFT && slot == 50
                    && (page + 1) * CONTENT_SLOTS.size() < rendered.size()) {
                if (sortingSlots != null && !capturePage(player, event.getView().getTopInventory())) return;
                page++;
                menus.refresh(player);
                return;
            }
            int index = CONTENT_SLOTS.indexOf(slot);
            if (index < 0) return;
            int position = page * CONTENT_SLOTS.size() + index;
            if (sortingSlots != null) {
                if (event.getClick() == ClickType.LEFT && canMove(event)) event.setCancelled(false);
                return;
            }
            if (position >= rendered.size()) return;
            ExchangeOffer offer = rendered.get(position);
            if (offer == null) return;
            if (event.getClick() == ClickType.LEFT) editor.open(player, offer, this::reopen);
            if (event.getClick() == ClickType.RIGHT)
                menus.open(player, new DeleteConfirmation(this, offer, catalog.isTask(offer.id())));
        }

        private void beginSort(Player player) {
            if (player.getItemOnCursor() != null && player.getItemOnCursor().getType() != Material.AIR) {
                player.sendMessage("§c请先放下鼠标上的物品。");
                return;
            }
            original = new ArrayList<>(offers());
            sortingSlots = new ArrayList<>(original);
            sortSession = UUID.randomUUID().toString();
            menus.refresh(player);
        }

        private boolean canMove(InventoryClickEvent event) {
            ItemStack cursor = event.getCursor();
            ItemStack target = event.getCurrentItem();
            if (!empty(cursor) && sortOffer(cursor) == null) return false;
            if (!empty(target) && sortOffer(target) == null) return false;
            return switch (event.getAction()) {
                case PICKUP_ALL, PLACE_ALL, SWAP_WITH_CURSOR, NOTHING -> true;
                default -> false;
            };
        }

        private boolean capturePage(Player player, Inventory inventory) {
            if (!empty(player.getItemOnCursor())) {
                player.sendMessage("§c请先放下手中的项目。");
                return false;
            }
            int start = page * CONTENT_SLOTS.size();
            for (int index = 0; index < CONTENT_SLOTS.size(); index++) {
                ItemStack item = inventory.getItem(CONTENT_SLOTS.get(index));
                ExchangeOffer offer = empty(item) ? null : sortOffer(item);
                if (!empty(item) && offer == null) {
                    player.sendMessage("§c排序物品已发生变化, 请重新打开后重试。");
                    return false;
                }
                int position = start + index;
                if (position < sortingSlots.size()) sortingSlots.set(position, offer);
                else if (offer != null) {
                    while (sortingSlots.size() <= position) sortingSlots.add(null);
                    sortingSlots.set(position, offer);
                }
            }
            while (!sortingSlots.isEmpty() && sortingSlots.getLast() == null) sortingSlots.removeLast();
            return true;
        }

        private ExchangeOffer sortOffer(ItemStack item) {
            var marker = lib.itemMarkerCodec().read(item, SORT_MARKER).orElse(null);
            if (marker == null || sortSession == null || !sortSession.equals(marker.values().get("session")))
                return null;
            String id = marker.values().get("offer");
            return original.stream().filter(offer -> offer != null && offer.id().equals(id)).findFirst().orElse(null);
        }

        private boolean empty(ItemStack item) {
            return item == null || item.getType() == Material.AIR;
        }

        private void confirmSort(Player player, InventoryClickEvent event) {
            if (!capturePage(player, event.getView().getTopInventory())) return;
            sortSaving = true;
            var holder = event.getView().getTopInventory().getHolder();
            catalog.saveOrder(npc, original, sortingSlots).whenComplete((ignored, error) -> {
                sortSaving = false;
                if (error != null) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save NPC order " + npc, error);
                    if (player.isOnline()) player.sendMessage("§c保存顺序失败, 请重新打开后重试。");
                } else {
                    sortingSlots = null;
                    original = null;
                    if (player.isOnline()) player.sendMessage("§a项目顺序已保存。");
                }
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                    menus.refresh(player);
                }
            });
        }

        private void clearSortCursor(Player player) {
            if (sortingSlots != null && sortOffer(player.getItemOnCursor()) != null) player.setItemOnCursor(null);
            sortingSlots = null;
            original = null;
        }

        @Override
        public void handleDrag(Player player, InventoryDragEvent event, MenuService service) {
            if (sortingSlots == null || sortSaving || !player.hasPermission("dreamrpg.admin")) return;
            if (event.getNewItems().size() != 1) return;
            ItemStack oldCursor = event.getOldCursor();
            ExchangeOffer oldOffer = sortOffer(oldCursor);
            if (oldOffer == null) return;
            var entry = event.getNewItems().entrySet().iterator().next();
            ItemStack placed = entry.getValue();
            if (canPlaceSortDrag(entry.getKey(), oldCursor.getAmount(), placed.getAmount(),
                    empty(event.getCursor()), empty(event.getView().getItem(entry.getKey())),
                    oldOffer.equals(sortOffer(placed)))) {
                event.setCancelled(false);
            }
        }

        @Override
        public void onClose(Player player, Inventory inventory) {
            clearSortCursor(player);
        }

        @Override
        public void onQuit(Player player, Inventory inventory) {
            clearSortCursor(player);
        }

        private void reopen(Player player) {
            menus.open(player, this);
        }
    }

    private final class DeleteConfirmation implements MenuView {
        private final NpcEditor parent;
        private final ExchangeOffer offer;
        private final boolean task;
        private boolean deleting;

        private DeleteConfirmation(NpcEditor parent, ExchangeOffer offer, boolean task) {
            this.parent = parent;
            this.offer = offer;
            this.task = task;
        }

        @Override
        public String title(Player player) {
            return "确认?";
        }

        @Override
        public int size(Player player) {
            return 45;
        }

        @Override
        public void render(Player player, Inventory inventory) {
            inventory.clear();
            inventory.setItem(20, exchanges.icon(offer));
            inventory.setItem(24, MenuItems.button(Material.STAINED_CLAY, (short) 5, "&a确认", List.of(
                    "&7一旦确认, 该操作无法撤销!", " ", "&e点击确认!"
            )));
            inventory.setItem(44, MenuItems.button(Material.ARROW, "&a取消并返回", List.of(
                    "&7放弃删除并返回项目列表", " ", "&e点击返回!"
            )));
        }

        @Override
        public void handleClick(Player player, InventoryClickEvent event, MenuService service) {
            if (!player.hasPermission("dreamrpg.admin")) {
                menus.close(player);
                return;
            }
            if (deleting) return;
            if (event.getRawSlot() == 44 && event.getClick() == ClickType.LEFT) {
                parent.reopen(player);
                return;
            }
            if (event.getRawSlot() != 24 ||
                    (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.SHIFT_LEFT))
                return;
            deleting = true;
            var holder = event.getView().getTopInventory().getHolder();
            catalog.deleteOffer(offer, task).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    plugin.getLogger()
                            .log(java.util.logging.Level.SEVERE, "Failed to delete offer " + offer.id(), failure);
                    if (player.isOnline()) player.sendMessage("§c删除失败, 请查看服务器日志。");
                } else if (player.isOnline()) player.sendMessage("§a已删除。");
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder) {
                    parent.reopen(player);
                }
            });
        }
    }

    private final class OfferMenu implements MenuView {
        private final String name;
        private final Supplier<List<ExchangeOffer>> offers;
        private int page;
        private List<ExchangeOffer> rendered = List.of();

        OfferMenu(String name, Supplier<List<ExchangeOffer>> offers) {
            this.name = name;
            this.offers = offers;
        }

        @Override
        public int size(Player player) {
            return compactSize(offers.get().size());
        }

        @Override
        public String title(Player player) {
            return LegacyText.colorize(name);
        }

        @Override
        public void render(Player player, Inventory inventory) {
            inventory.clear();
            rendered = new ArrayList<>(offers.get());
            PageWindow window = PageWindow.of(rendered.size(), CONTENT_SLOTS.size(), page);
            page = window.page();
            int size = inventory.getSize();
            if (window.hasPrevious())
                inventory.setItem(size - 9, MenuItems.button(Material.ARROW, "&a上一页", List.of("&e点击翻页!")));
            if (window.hasNext())
                inventory.setItem(size - 1, MenuItems.button(Material.ARROW, "&a下一页", List.of("&e点击翻页!")));
            forEachEntry(rendered, page, (slot, offer) -> {
                if (offer != null) inventory.setItem(slot, exchanges.icon(offer));
            });
        }

        @Override
        public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
            int slot = event.getRawSlot();
            int size = event.getView().getTopInventory().getSize();
            if (event.getClick() == ClickType.LEFT && slot == size - 9 && page > 0) {
                page--;
                menus.refresh(player);
                return;
            }
            if (event.getClick() == ClickType.LEFT && slot == size - 1
                    && (page + 1) * CONTENT_SLOTS.size() < rendered.size()) {
                page++;
                menus.refresh(player);
                return;
            }
            int offset = CONTENT_SLOTS.indexOf(slot);
            int index = offset < 0 ? -1 : page * CONTENT_SLOTS.size() + offset;
            if (index < 0 || index >= rendered.size() || rendered.get(index) == null) return;
            if (event.getClick() == ClickType.LEFT) exchanges.submit(player, rendered.get(index), false);
            if (event.getClick() == ClickType.SHIFT_LEFT) exchanges.submit(player, rendered.get(index), true);
        }
    }

}
