package cn.mythicland.dreamrpg.enderchest;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.dreamrpgitems.api.DreamRPGItemApi;
import cn.mythicland.dreamrpgitems.api.MythicItemRefreshMode;
import cn.mythicland.dreamrpgitems.api.MythicItemRefreshStatus;
import cn.mythicland.dreamrpgitems.api.MythicItemsChangedEvent;
import cn.mythicland.dreamrpgitems.api.MythicItemsSyncRequestedEvent;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.container.ContainerAnimationService;
import cn.mythicland.framework.bukkit.container.ContainerAnimationSpec;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.player.PlayerLoadingGate;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.InjectComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns DreamRPG's custom ender-chest domain interaction and delegates animation lifecycle to Lib.
 */
@InjectComponent
@ListenerComponent
public final class EnderChestService implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final MenuManager menus;
    private final ContainerAnimationService animations;
    private final PlayerStorageService storage;
    private final PlayerLoadingGate loadingGate;
    private final Logger logger;
    private final Map<UUID, EnderChestMenu> openMenus = new HashMap<>();

    /**
     * Creates the ender-chest service.
     *
     * @param plugin      owning DreamRPG plugin
     * @param menus       Lib menu lifecycle
     * @param animations  Lib packet animation service
     * @param storage     loaded player storage
     * @param loadingGate shared loading restriction
     * @param logger      interaction failure logger
     */
    public EnderChestService(
            JavaPlugin plugin,
            MenuManager menus,
            ContainerAnimationService animations,
            PlayerStorageService storage,
            PlayerLoadingGate loadingGate,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.loadingGate = Objects.requireNonNull(loadingGate, "loadingGate");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Opens the player's custom ender chest from a command.
     *
     * @param player viewer and storage owner
     */
    public void open(Player player) {
        open(player, player, null);
    }

    /**
     * Opens another loaded player's custom ender chest for editing.
     *
     * @param viewer administrator viewing the chest
     * @param owner  player whose chest is being edited
     */
    public void open(Player viewer, Player owner) {
        open(viewer, owner, null);
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        event.setCancelled(true);
        open(event.getPlayer(), event.getPlayer(), block);
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent event) {
        if (openMenus.containsKey(event.getPlayer().getUniqueId())) menus.close(event.getPlayer());
    }

    /**
     * Closes remote editors before the owner's storage session is saved and released.
     *
     * @param event owner quit event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnerQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        List<UUID> viewers = openMenus.entrySet().stream()
                .filter(entry -> entry.getValue().ownerId().equals(ownerId))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) menus.close(viewer);
            else openMenus.remove(viewerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player != null) synchronizeItems(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsChanged(MythicItemsChangedEvent event) {
        synchronizeLoadedPlayers();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSyncRequested(MythicItemsSyncRequestedEvent event) {
        synchronizeLoadedPlayers();
    }

    private void synchronizeLoadedPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!storage.isReady(player.getUniqueId()) || loadingGate.isRestricted(player)) continue;
            synchronizeItems(player);
        }
    }

    private void synchronizeItems(Player player) {
        UUID ownerId = player.getUniqueId();
        if (!storage.isLoaded(ownerId)) return;
        DreamRPGItemApi items = Bukkit.getServicesManager().load(DreamRPGItemApi.class);
        if (items == null) {
            logger.warning("DreamRPG item service is unavailable; custom ender-chest synchronization skipped.");
            return;
        }
        Map.Entry<UUID, EnderChestMenu> openMenu = openMenus.entrySet().stream()
                .filter(entry -> entry.getValue().ownerId().equals(ownerId))
                .findFirst()
                .orElse(null);
        Player viewer = openMenu == null ? null : Bukkit.getPlayer(openMenu.getKey());
        Inventory inventory = viewer == null ? null : viewer.getOpenInventory().getTopInventory();
        boolean open = inventory != null && inventory.getSize() == EnderChestMenu.SIZE;
        ItemStack[] contents =
                open ? inventory.getContents() : storage.findLoaded(ownerId).orElseThrow().enderChest();
        boolean changed = false;
        int stale = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            var result = items.refresh(contents[slot], MythicItemRefreshMode.EXISTING_INSTANCE);
            if (result.status() == MythicItemRefreshStatus.UPDATED) {
                contents[slot] = result.item();
                changed = true;
            } else if (result.status() == MythicItemRefreshStatus.STALE) stale++;
        }
        if (changed) {
            storage.updateEnderChest(ownerId, contents);
            if (open) {
                inventory.setContents(contents);
                viewer.updateInventory();
            }
        }
        if (stale > 0)
            logger.warning("Kept " + stale + " stale items in " + player.getName() + "'s custom ender chest");
    }

    @Override
    public void close() {
        for (UUID uniqueId : Map.copyOf(openMenus).keySet()) {
            Player player = plugin.getServer().getPlayer(uniqueId);
            if (player != null) menus.close(player);
        }
        openMenus.clear();
    }

    private void open(Player viewer, Player owner, Block sourceBlock) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(owner, "owner");
        if (loadingGate.isLoading(viewer)) {
            viewer.sendMessage(LegacyText.colorize("&c玩家数据正在加载, 请稍候。"));
            return;
        }
        UUID ownerId = owner.getUniqueId();
        if (!storage.isReady(ownerId)) {
            viewer.sendMessage(LegacyText.colorize("&c目标玩家数据尚未加载完成。"));
            return;
        }
        UUID viewerId = viewer.getUniqueId();
        boolean openedByAnotherViewer = openMenus.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(viewerId) && entry.getValue().ownerId().equals(ownerId)
        );
        if (openedByAnotherViewer) {
            viewer.sendMessage(LegacyText.colorize("&c该玩家的末影箱正在被编辑。"));
            return;
        }
        PlayerStorageSnapshot snapshot = storage.findLoaded(ownerId).orElseThrow(
                () -> new IllegalStateException("Loaded player storage disappeared: " + ownerId)
        );
        ContainerAnimationSpec animationSpecification = ContainerAnimationSpec.enderChest();
        boolean localSound = sourceBlock == null;
        EnderChestMenu menu = new EnderChestMenu(
                ownerId,
                storage,
                snapshot,
                localSound,
                () -> openMenus.remove(viewerId)
        );
        try {
            menus.open(viewer, menu);
            openMenus.put(viewerId, menu);
            if (sourceBlock != null) {
                menu.attachAnimation(animations.open(sourceBlock, viewer, animationSpecification));
            } else {
                viewer.playSound(
                        viewer.getLocation(),
                        animationSpecification.openSound(),
                        animationSpecification.openVolume(),
                        animationSpecification.openPitch()
                );
            }
        } catch (RuntimeException exception) {
            menus.close(viewer);
            logger.log(
                    Level.SEVERE,
                    "Failed to open DreamRPG ender chest for " + viewer.getName(),
                    exception
            );
            viewer.sendMessage(LegacyText.colorize("&c末影箱打开失败, 请联系管理员。"));
        }
    }
}
