package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.spawn.SpawnService;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;

import java.util.Objects;

import org.bukkit.entity.Player;

/**
 * Handles the server-wide /spawn command owned by DreamRPG.
 */
@CommandBinding(value = "spawn", takeOverGlobalMapping = true)
public final class SpawnCommand {

    private final SpawnService spawnService;

    public SpawnCommand(DreamRPGContext context) {
        this.spawnService = Objects.requireNonNull(context, "context").spawnService();
    }

    @CommandRoute(path = {"spawn"}, maxArguments = Integer.MAX_VALUE)
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
