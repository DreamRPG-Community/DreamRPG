package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.framework.bukkit.command.CommandArgument;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.SuggestionSource;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens online players' live inventories for administrator editing.
 */
@InjectComponent
@ListenerComponent
@CommandBinding("inv")
public final class InventoryCommand implements Listener {

    private final JavaPlugin plugin;
    private final PlayerStorageService storage;
    private final Map<UUID, EditSession> editors = new HashMap<>();

    public InventoryCommand(JavaPlugin plugin, PlayerStorageService storage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @CommandRoute(
            path = {"inv"},
            permission = "dreamrpg.admin",
            usages = "/inv <在线玩家>",
            minArguments = 1,
            maxArguments = 1,
            arguments = @CommandArgument(index = 0, source = SuggestionSource.ONLINE_PLAYERS)
    )
    void open(CommandContext context) {
        if (!(context.sender() instanceof Player viewer)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以打开编辑界面。"));
            return;
        }
        Player target = Bukkit.getPlayerExact(context.argument(0));
        if (target == null) {
            context.sender().sendMessage(VanillaCommandMessages.red("目标玩家必须在线。"));
            return;
        }
        if (!storage.isReady(target.getUniqueId())) {
            context.sender().sendMessage(VanillaCommandMessages.red("目标玩家数据尚未加载完成。"));
            return;
        }
        Inventory inventory = target.getInventory();
        viewer.openInventory(inventory);
        editors.put(viewer.getUniqueId(), new EditSession(target.getUniqueId(), inventory));
        context.sender().sendMessage(VanillaCommandMessages.green("已打开玩家 " + target.getName() + " 的背包。"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        EditSession edit = editors.get(viewerId);
        if (edit == null || edit.inventory() != event.getInventory()) return;
        editors.remove(viewerId, edit);
        captureAndFlush(edit.targetId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        EditSession viewed = editors.remove(playerId);
        if (viewed != null) captureAndFlush(viewed.targetId());
        for (Map.Entry<UUID, EditSession> entry : new ArrayList<>(editors.entrySet())) {
            if (!entry.getValue().targetId().equals(playerId)) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null) editors.remove(entry.getKey(), entry.getValue());
            else viewer.closeInventory();
        }
    }

    private void captureAndFlush(UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !storage.isReady(targetId)) return;
        storage.capture(target);
        storage.flush(targetId).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed to save an administrator-edited inventory for " + targetId,
                        failure
                );
            }
        });
    }

    private record EditSession(UUID targetId, Inventory inventory) {
    }
}
