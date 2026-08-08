package cn.mythicland.dreamrpg.command;

import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles the administrator shorthand for changing a player's own game mode.
 */
@InjectComponent
@CommandComponent
public final class GameModeCommand implements BukkitCommandComponent, CommandExecutor, TabCompleter {

    private static final String USAGE = "/gm <0|1|2|3>";
    private static final List<String> GAME_MODE_VALUES = List.of("0", "1", "2", "3");

    @Override
    public String commandName() {
        return "gm";
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
    public boolean takeOverGlobalMapping() {
        return true;
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
        if (arguments.length != 1) {
            sender.sendMessage(VanillaCommandMessages.usage(USAGE));
            return true;
        }

        GameMode gameMode = parseGameMode(arguments[0]);
        if (gameMode == null) {
            sender.sendMessage(VanillaCommandMessages.usage(USAGE));
            return true;
        }
        player.setGameMode(gameMode);
        sender.sendMessage(ChatColor.WHITE + "您的游戏模式已更新");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length != 1) return List.of();
        String prefix = arguments[0];
        return GAME_MODE_VALUES.stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private static GameMode parseGameMode(String value) {
        return switch (value) {
            case "0" -> GameMode.SURVIVAL;
            case "1" -> GameMode.CREATIVE;
            case "2" -> GameMode.ADVENTURE;
            case "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }
}
