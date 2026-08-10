package cn.mythicland.dreamrpg.command;

import cn.mythicland.lib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles the administrator shorthand for changing a player's own game mode.
 */
@CommandComponent(
        value = "gm",
        permission = "dreamrpg.admin",
        takeOverGlobalMapping = true
)
public final class GameModeCommand {

    private static final String USAGE = "/gm <游戏模式ID>";
    private static final List<String> GAME_MODE_VALUES = List.of("0", "1", "2", "3");

    private static GameMode parseGameMode(String value) {
        return switch (value) {
            case "0" -> GameMode.SURVIVAL;
            case "1" -> GameMode.CREATIVE;
            case "2" -> GameMode.ADVENTURE;
            case "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @CommandHandler(usage = USAGE)
    void change(CommandContext context) {
        context.requireArguments(1);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }

        GameMode gameMode = parseGameMode(context.argument(0));
        if (gameMode == null) throw context.invalidUsage();
        player.setGameMode(gameMode);
        context.sender().sendMessage(ChatColor.WHITE + "您的游戏模式已更新");
    }

    @CommandCompleter
    List<String> complete(CommandContext context) {
        if (context.arguments().size() != 1) return List.of();
        String prefix = context.argument(0);
        return GAME_MODE_VALUES.stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}
