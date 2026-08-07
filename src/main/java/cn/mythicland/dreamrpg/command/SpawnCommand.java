package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.spawn.SpawnService;
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
 * Handles the server-wide /spawn command owned by DreamRPG.
 */
@InjectComponent
@CommandComponent
public final class SpawnCommand implements BukkitCommandComponent, CommandExecutor, TabCompleter {

    private final SpawnService spawnService;

    /**
     * Creates the spawn command.
     *
     * @param context initialized DreamRPG context
     */
    public SpawnCommand(DreamRpgContext context) {
        this.spawnService = Objects.requireNonNull(context, "context").spawnService();
    }

    /**
     * Returns the command declared in plugin.yml.
     *
     * @return command name
     */
    @Override
    public String commandName() {
        return "spawn";
    }

    /**
     * Returns this command's executor.
     *
     * @return command executor
     */
    @Override
    public CommandExecutor executor() {
        return this;
    }

    /**
     * Returns this command's tab completer.
     *
     * @return tab completer
     */
    @Override
    public TabCompleter tabCompleter() {
        return this;
    }

    /**
     * Requests Lib to replace an existing global /spawn mapping.
     *
     * @return true
     */
    @Override
    public boolean takeOverGlobalMapping() {
        return true;
    }

    /**
     * Executes /spawn for a player.
     *
     * @param sender command sender
     * @param command Bukkit command
     * @param label command label
     * @param arguments command arguments
     * @return always true
     */
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
            sender.sendMessage(VanillaCommandMessages.usage("/spawn"));
            return true;
        }
        spawnService.teleport(player);
        sender.sendMessage(VanillaCommandMessages.green("已返回主城出生点。"));
        return true;
    }

    /**
     * Supplies no arguments for the fixed spawn command.
     *
     * @param sender command sender
     * @param command Bukkit command
     * @param alias command alias
     * @param arguments partial arguments
     * @return empty completions
     */
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
