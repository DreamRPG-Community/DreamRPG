package cn.mythicland.dreamrpg.menu;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.item.ItemMarker;
import cn.mythicland.framework.bukkit.item.ItemMarkerCodec;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Restores the game-menu item only after the player's saved inventory has been applied.
 */
@InjectComponent
@ListenerComponent
public final class GameMenuItemListener implements Listener {
    private static final String NAMESPACE = "DreamRPGMenu";
    private static final ItemMarker MARKER = new ItemMarker(NAMESPACE, 1, Map.of("kind", "game_menu"));
    private final ItemMarkerCodec markers;
    private final PluginTaskScope tasks;
    private final PlayerStorageService storage;
    private final Set<UUID> awaitingSpace = new HashSet<>();

    public GameMenuItemListener(DreamLibApi lib, PluginTaskScope tasks, PlayerStorageService storage) {
        this.markers = lib.itemMarkerCodec();
        this.tasks = tasks;
        this.storage = storage;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataReady(PlayerDataReadyEvent event) {
        tasks.runLater(1L, () -> {
            Player player = Bukkit.getPlayer(event.uniqueId());
            if (player != null && player.isOnline() && storage.isReady(event.uniqueId())) ensureMenu(player);
        });
    }

    void ensureMenu(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isMenu(item)) continue;
            awaitingSpace.remove(player.getUniqueId());
            return;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            if (awaitingSpace.add(player.getUniqueId())) {
                player.sendMessage("§e背包已满, 腾出一格后将自动补发游戏菜单。");
            }
            return;
        }
        ItemStack item = MenuItems.button(Material.WATCH, "&e游戏菜单 &7(右键打开)", List.of("&7右键打开游戏菜单"));
        player.getInventory().setItem(slot, markers.write(item, MARKER));
        awaitingSpace.remove(player.getUniqueId());
    }

    boolean isMenu(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (markers.read(item, NAMESPACE).filter(MARKER::equals).isPresent()) return true;
        // Recognize the actual legacy CustomJoinItems clock without renaming player-owned items.
        return item.getType() == Material.WATCH && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && "武林指南 (右键点击)".equals(ChatColor.stripColor(item.getItemMeta().getDisplayName()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenu(event.getItem())) return;
        event.setCancelled(true);
        if (!storage.isReady(event.getPlayer().getUniqueId())) return;
        event.getPlayer().performCommand("menu");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) retryAfterInventoryChange(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) retryAfterInventoryChange(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        retryAfterInventoryChange(event.getPlayer());
    }

    private void retryAfterInventoryChange(Player player) {
        if (!awaitingSpace.contains(player.getUniqueId())) return;
        tasks.runLater(1L, () -> {
            if (player.isOnline() && storage.isReady(player.getUniqueId())) ensureMenu(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        awaitingSpace.remove(event.getPlayer().getUniqueId());
    }
}
