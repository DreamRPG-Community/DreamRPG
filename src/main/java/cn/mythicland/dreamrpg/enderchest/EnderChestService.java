package cn.mythicland.dreamrpg.enderchest;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.container.ContainerAnimationHandle;
import cn.mythicland.lib.container.ContainerAnimationService;
import cn.mythicland.lib.container.ContainerAnimationSpec;
import cn.mythicland.lib.loading.PlayerLoadingGate;
import cn.mythicland.lib.menu.MenuService;
import cn.mythicland.lib.text.LegacyText;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns DreamRPG's custom ender-chest domain interaction and delegates animation lifecycle to Lib.
 */
@InjectComponent
@ListenerComponent
public final class EnderChestService implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final MenuService menus;
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
            MenuService menus,
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
        open(player, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;
        event.setCancelled(true);
        open(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (openMenus.containsKey(event.getPlayer().getUniqueId())) menus.close(event.getPlayer());
    }

    @Override
    public void close() {
        for (UUID uniqueId : Map.copyOf(openMenus).keySet()) {
            Player player = plugin.getServer().getPlayer(uniqueId);
            if (player != null) menus.close(player);
        }
        openMenus.clear();
    }

    private void open(Player player, Block sourceBlock) {
        Objects.requireNonNull(player, "player");
        if (loadingGate.isLoading(player)) {
            player.sendMessage(LegacyText.colorize("&c玩家数据正在加载, 请稍候。"));
            return;
        }
        if (!storage.isLoaded(player.getUniqueId())) {
            player.sendMessage(LegacyText.colorize("&c玩家数据尚未加载完成。"));
            return;
        }
        PlayerStorageSnapshot snapshot = storage.findLoaded(player.getUniqueId()).orElseThrow(
                () -> new IllegalStateException("Loaded player storage disappeared: " + player.getUniqueId())
        );
        ContainerAnimationSpec animationSpecification = ContainerAnimationSpec.enderChest();
        boolean localSound = sourceBlock == null;
        EnderChestMenu menu = new EnderChestMenu(
                player.getUniqueId(),
                storage,
                snapshot,
                null,
                localSound,
                () -> openMenus.remove(player.getUniqueId())
        );
        try {
            menus.open(player, menu);
            openMenus.put(player.getUniqueId(), menu);
            if (sourceBlock != null) {
                menu.attachAnimation(animations.open(sourceBlock, player, animationSpecification));
            } else {
                player.playSound(
                        player.getLocation(),
                        animationSpecification.openSound(),
                        animationSpecification.openVolume(),
                        animationSpecification.openPitch()
                );
            }
        } catch (RuntimeException exception) {
            menus.close(player);
            logger.log(
                    Level.SEVERE,
                    "Failed to open DreamRPG ender chest for " + player.getName(),
                    exception
            );
            player.sendMessage(LegacyText.colorize("&c末影箱打开失败, 请联系管理员。"));
        }
    }
}
