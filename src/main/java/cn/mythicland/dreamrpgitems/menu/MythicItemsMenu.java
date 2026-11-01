package cn.mythicland.dreamrpgitems.menu;

import cn.mythicland.framework.pagination.FolderLayout;

import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.pagination.PageWindow;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import static cn.mythicland.framework.pagination.MenuPageLayout.*;

/**
 * Configured item library with expandable folders and a fixed content grid.
 */
public final class MythicItemsMenu extends InventoryMenu {
    private final MythicItemService service;
    private final FolderLayout layout;
    private final Set<String> expanded = new HashSet<>();
    private List<FolderLayout.Row> rendered = List.of();
    private int page;

    public MythicItemsMenu(MythicItemService service, FolderLayout layout) {
        this.service = Objects.requireNonNull(service, "service");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public void open(Player player, MenuManager menus) {
        page = 0;
        menus.open(player, this);
    }

    @Override
    public String buildTitle(Player player) {
        return "DreamRPG 物品库";
    }

    @Override
    public int size(Player player) {
        return SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        rendered = layout.rows(expanded, id -> service.configuration(id).isPresent());
        PageWindow window = forEachEntry(rendered, page, (slot, row) -> {
            ItemStack icon = MenuItems.icon(service.getItemStack(row.entryId(), 1));
            icon.setAmount(row.iconAmount());
            inventory.setItem(slot, icon);
        });
        page = window.page();
        if (window.hasPrevious()) {
            inventory.setItem(PREVIOUS_SLOT, MenuItems.button(Material.ARROW, "&a上一页", List.of("&e点击翻页!")));
        }
        if (window.hasNext()) {
            inventory.setItem(NEXT_SLOT, MenuItems.button(Material.ARROW, "&a下一页", List.of("&e点击翻页!")));
        }
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (!player.hasPermission("dreamrpg.admin")) {
            menus.close(player);
            return;
        }
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        PageWindow window = PageWindow.of(rendered.size(), CONTENT_SLOTS.size(), page);
        if (click == ClickType.LEFT && slot == PREVIOUS_SLOT && window.hasPrevious()) {
            page--;
            menus.refresh(player);
            return;
        }
        if (click == ClickType.LEFT && slot == NEXT_SLOT && window.hasNext()) {
            page++;
            menus.refresh(player);
            return;
        }
        int offset = CONTENT_SLOTS.indexOf(slot);
        if (offset < 0) return;
        int index = window.startIndex() + offset;
        if (index >= window.endIndex()) return;
        FolderLayout.Row row = rendered.get(index);
        if ((click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) && row.folderId() != null) {
            if (!expanded.add(row.folderId())) expanded.remove(row.folderId());
            menus.refresh(player);
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        if (!player.getInventory().addItem(service.getItemStack(row.entryId(), 1)).isEmpty()) {
            player.sendMessage(ChatColor.RED + "背包已满, 请先腾出一个位置。");
        }
    }
}
