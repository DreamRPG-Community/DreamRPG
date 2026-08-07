package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.enderchest.EnderChestService;
import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

/**
 * Opens the executing player's custom ender chest.
 */
@InjectComponent
@CommandComponent
public final class EnderChestCommand implements BukkitCommandComponent, CommandExecutor, TabCompleter {

    private final EnderChestService enderChest;

    /**
     * Creates the ender-chest command.
     *
     * @param enderChest ender-chest service
     */
    public EnderChestCommand(EnderChestService enderChest) {
        this.enderChest = Objects.requireNonNull(enderChest, "enderChest");
    }

    @Override
    public String commandName() {
        return "enderchest";
    }

    @Override
    public CommandExecutor executor() {
        return this;
    }

    @Override
    public TabCompleter tabCompleter() {
        return this;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return true;
        }
        if (arguments.length != 0) {
            sender.sendMessage(VanillaCommandMessages.usage("/enderchest"));
            return true;
        }
        enderChest.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        return List.of();
    }
}
