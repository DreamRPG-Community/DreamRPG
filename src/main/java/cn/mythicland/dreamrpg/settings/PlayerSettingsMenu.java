package cn.mythicland.dreamrpg.settings;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.text.LegacyText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * DreamRPG player options menu, following MythicThePit toggle descriptions and states.
 */
public final class PlayerSettingsMenu extends InventoryMenu {

    private static final int MENU_SIZE = 36;
    private static final int KNOCKBACK_SLOT = 10;
    private static final int AUTO_PICKUP_SLOT = 11;

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final PlayerSettingsService settings;
    private boolean saving;

    public PlayerSettingsMenu(
            JavaPlugin plugin,
            DreamLibApi lib,
            PlayerSettingsService settings
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    private static String optionLine(boolean enabled, boolean optionValue, String label) {
        String color = optionValue ? "&a" : "&c";
        return enabled == optionValue ? color + "▶ " + label : "&7  &7" + label;
    }

    private static boolean isToggleClick(ClickType clickType) {
        return clickType == ClickType.LEFT
                || clickType == ClickType.RIGHT
                || clickType == ClickType.SHIFT_LEFT
                || clickType == ClickType.SHIFT_RIGHT;
    }

    @Override
    public String buildTitle(Player player) {
        return "游戏选项";
    }

    @Override
    public int size(Player player) {
        return MENU_SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        inventory.setItem(KNOCKBACK_SLOT, this.knockbackButton(player));
        inventory.setItem(AUTO_PICKUP_SLOT, this.autoPickupButton(player));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuManager menus) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot != KNOCKBACK_SLOT && slot != AUTO_PICKUP_SLOT) return;
        if (this.saving) return;
        if (!isToggleClick(event.getClick())) return;

        this.saving = true;
        CompletableFuture<Boolean> change;
        if (slot == KNOCKBACK_SLOT) {
            change = settings.setAllowsMobKnockback(player.getUniqueId(), !settings.allowsMobKnockback(player.getUniqueId()));
        } else {
            change = settings.setAutoPickup(player.getUniqueId(), !settings.autoPicksUpRewards(player.getUniqueId()));
        }
        change.whenComplete((saved, failure) -> this.lib.runOnMain(() -> {
            this.saving = false;
            if (failure != null) {
                this.plugin.getLogger().severe(
                        "Failed to save player settings for " + player.getUniqueId() + ": "
                                + failure.getMessage()
                );
                if (player.isOnline()) player.sendMessage("§c保存游戏选项失败, 请稍后重试。");
            }
            if (player.isOnline()) menus.refresh(player);
        }));
    }

    @Override
    public void handleDrag(
            Player player,
            InventoryDragEvent event,
            MenuManager menus
    ) {
        event.setCancelled(true);
    }

    private ItemStack knockbackButton(Player player) {
        boolean allowed = this.settings.allowsMobKnockback(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        lore.add("&8普通玩家权益");
        lore.add(" ");
        lore.add("&7是否允许怪物攻击时对你造成击退。");
        lore.add("&7关闭后，抑制怪物攻击造成的击退。");
        lore.add(" ");
        lore.add(optionLine(allowed, true, "允许"));
        lore.add(optionLine(allowed, false, "不允许"));
        lore.add(" ");
        lore.add("&e点击切换此选项!");

        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.colorize("&a游戏选项: 怪物击退"));
        meta.setLore(lore.stream().map(LegacyText::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack autoPickupButton(Player player) {
        boolean enabled = settings.autoPicksUpRewards(player.getUniqueId());
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(LegacyText.colorize("&a游戏选项: 自动拾取击杀奖励"));
        meta.setLore(Stream.of(
                "&8普通玩家权益",
                " ",
                "&7开启后, 将怪物掉落物直接放入背包。",
                "&7多人攻击时, 按实际伤害比例分配掉落物。",
                "",
                optionLine(enabled, true, "开启"),
                optionLine(enabled, false, "关闭"),
                " ",
                "&e点击切换此选项!"
        ).map(LegacyText::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

}
