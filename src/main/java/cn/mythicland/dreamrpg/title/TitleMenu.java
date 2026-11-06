package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.pagination.MenuPageLayout;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only selection menu; all changes go through durable ownership checks.
 */
public final class TitleMenu extends InventoryMenu {
    private final TitleService titles;
    private final DreamLibApi lib;
    private final Map<Integer, String> entries = new HashMap<>();
    private int page;
    private boolean saving;

    public TitleMenu(TitleService titles, DreamLibApi lib) {
        this.titles = titles;
        this.lib = lib;
    }

    static String format(NumericRange range) {
        String value = number(range.minimum());
        if (!range.isFixed()) value += "-" + number(range.maximum());
        return range.percent() ? value + "%" : value;
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String expiryLine(long expiresAtMillis, long nowMillis) {
        if (expiresAtMillis == 0) return "&7到期时间: &a永久";
        long seconds = Math.max(1, (expiresAtMillis - nowMillis) / 1000);
        Duration remaining = Duration.ofSeconds(seconds);
        String value;
        if (remaining.toDays() > 0) {
            value = remaining.toDays() + "d " + remaining.toHoursPart() + "h";
        } else if (remaining.toHours() > 0) {
            value = remaining.toHours() + "h " + remaining.toMinutesPart() + "m";
        } else {
            value = remaining.toMinutes() + "m " + remaining.toSecondsPart() + "s";
        }
        return "&7到期时间: &a" + value + " 后";
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.colorize(name));
        meta.setLore(lore.stream().map(LegacyText::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String buildTitle(Player player) {
        return "称号选择";
    }

    @Override
    public int size(Player player) {
        return MenuPageLayout.compactSize(titles.definitions().size());
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        entries.clear();
        TitleSnapshot state = titles.snapshot(player.getUniqueId()).orElseThrow();
        long now = System.currentTimeMillis();
        var window = MenuPageLayout.forEachEntry(titles.definitions(), page, (slot, title) -> {
            entries.put(slot, title.id());
            boolean unlocked = state.owns(title.id(), now);
            boolean selected = unlocked && state.selectedId().equals(title.id());
            List<String> lore = new ArrayList<>();
            if (unlocked) {
                long expiry = state.expirations().get(title.id());
                lore.add(expiryLine(expiry, now));
            } else {
                lore.add("&c未解锁");
            }
            lore.add("");
            for (CombatStat stat : CombatStat.values()) {
                NumericRange bonus = title.bonuses().get(stat);
                if (bonus != null) {
                    String value = format(bonus);
                    if (bonus.minimum() >= 0) value = "+" + value;
                    lore.add(stat.displayColor() + "&l" + stat.defaultDisplayName() + " &2&l" + value);
                }
            }
            lore.add("");
            if (selected) {
                lore.add("&a已佩戴该称号");
            } else if (unlocked) {
                lore.add("&e左键佩戴!");
            } else {
                lore.add("&c解锁后可佩戴");
            }
            ItemStack button = item(Material.NAME_TAG, title.displayName(), lore);
            if (selected) {
                var meta = button.getItemMeta();
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                button.setItemMeta(meta);
            }
            inventory.setItem(slot, button);
        });
        page = window.page();
        if (window.hasPrevious()) inventory.setItem(45, item(Material.ARROW, "&e上一页", List.of()));
        if (window.hasNext()) inventory.setItem(53, item(Material.ARROW, "&e下一页", List.of()));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuManager menus) {
        event.setCancelled(true);
        if (!event.isLeftClick() && !event.isRightClick()) return;
        if (saving) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= player.getOpenInventory().getTopInventory().getSize()) return;
        if ((slot == 45 || slot == 53) && player.getOpenInventory().getTopInventory().getItem(slot) != null) {
            page += slot == 45 ? -1 : 1;
            menus.refresh(player);
            return;
        }
        String id = entries.get(slot);
        if (id == null) return;
        TitleSnapshot current = titles.snapshot(player.getUniqueId()).orElseThrow();
        if (!current.owns(id, System.currentTimeMillis())) {
            player.sendMessage("§c你尚未解锁该称号!");
            menus.refresh(player);
            return;
        }
        boolean selected = current.selectedId().equals(id);
        if (event.isRightClick()) {
            if (!selected) return;
            id = "";
        } else if (selected) return;
        String selection = id;
        CompletableFuture<TitleSnapshot> change;
        try {
            change = titles.select(player.getUniqueId(), selection);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c称号已失效, 请重新选择。");
            menus.refresh(player);
            return;
        }
        saving = true;
        change.whenComplete((state, failure) -> lib.runOnMain(() -> {
            saving = false;
            if (!player.isOnline()) return;
            if (failure != null) player.sendMessage("§c称号切换失败: " + DreamLibApi.rootCauseMessage(failure));
            else player.sendMessage(selection.isEmpty() ? "§a已卸下称号。" : "§a已佩戴称号。");
            if (menus.currentMenu(player) == this) menus.refresh(player);
        }));
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuManager menus) {
        event.setCancelled(true);
    }
}
