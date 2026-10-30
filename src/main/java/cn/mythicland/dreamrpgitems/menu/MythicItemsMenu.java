package cn.mythicland.dreamrpgitems.menu;

import cn.mythicland.dreamlib.menu.*;
import cn.mythicland.dreamrpgitems.api.*;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

import static cn.mythicland.dreamlib.menu.MenuPageLayout.*;

/**
 * Unfiltered item library with a fixed content grid and navigation footer.
 */
public final class MythicItemsMenu implements MenuView {
    private final MythicItemService service;
    private int page;

    public MythicItemsMenu(MythicItemService service) {
        this.service = service;
    }

    public void open(Player player, MenuService menus) {
        page = 0;
        menus.open(player, this);
    }

    @Override
    public String title(Player player) {
        return "DreamRPG 物品库";
    }

    @Override
    public int size(Player player) {
        return SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        MythicItemPage result = page();
        page = result.page();
        for (int index = 0; index < result.items().size(); index++) {
            String id = result.items().get(index).internalName();
            inventory.setItem(CONTENT_SLOTS.get(index), MenuItems.icon(service.getItemStack(id, 1)));
        }
        inventory.setItem(PREVIOUS_SLOT, MenuItems.button(Material.ARROW, "&a上一页", List.of("&e点击翻页!")));
        inventory.setItem(NEXT_SLOT, MenuItems.button(Material.ARROW, "&a下一页", List.of("&e点击翻页!")));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menus) {
        if (!player.hasPermission("dreamrpg.admin")) {
            menus.close(player);
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        int slot = event.getRawSlot();
        MythicItemPage result = page();
        if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) {
            if (click != ClickType.LEFT) return;
            PageWindow window = PageWindow.of(result.total(), result.pageSize(), result.page());
            if (slot == PREVIOUS_SLOT && window.hasPrevious()) page = result.page() - 1;
            if (slot == NEXT_SLOT && window.hasNext()) page = result.page() + 1;
            menus.refresh(player);
            return;
        }
        int index = CONTENT_SLOTS.indexOf(slot);
        if (index < 0 || index >= result.items().size()) return;
        String id = result.items().get(index).internalName();
        if (click != ClickType.SHIFT_LEFT) return;
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "背包已满, 请先腾出一个位置。");
            return;
        }
        player.getInventory().addItem(service.getItemStack(id, 1));
    }

    private MythicItemPage page() {
        return service.search(new MythicItemQuery("", MythicItemSource.ALL, null, page, CONTENT_SLOTS.size()));
    }

}
