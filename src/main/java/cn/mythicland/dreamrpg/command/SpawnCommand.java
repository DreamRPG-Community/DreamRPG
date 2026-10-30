package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.command.VanillaCommandMessages;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Handles the server-wide /spawn command owned by DreamRPG.
 */
@CommandComponent(value = "spawn", takeOverGlobalMapping = true)
public final class SpawnCommand {

    private final SpawnService spawnService;

    public SpawnCommand(DreamRPGContext context) {
        this.spawnService = Objects.requireNonNull(context, "context").spawnService();
    }

    @CommandHandler
    void teleport(CommandContext context) {
        context.requireArguments(0);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }
        spawnService.teleport(player);
        context.sender().sendMessage(VanillaCommandMessages.green("已返回主城出生点。"));
    }
}
