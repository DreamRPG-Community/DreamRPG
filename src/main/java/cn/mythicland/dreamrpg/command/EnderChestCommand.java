package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.enderchest.EnderChestService;
import cn.mythicland.framework.bukkit.command.CommandArgument;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.SuggestionSource;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Opens a player's custom ender chest for themselves or administrator editing.
 */
@CommandBinding("enderchest")
public final class EnderChestCommand {

    private final EnderChestService enderChest;

    public EnderChestCommand(EnderChestService enderChest) {
        this.enderChest = Objects.requireNonNull(enderChest, "enderChest");
    }

    @CommandRoute(
            path = {"enderchest"},
            usages = "/enderchest [在线玩家]",
            minArguments = 0,
            maxArguments = 1,
            arguments = @CommandArgument(index = 0, source = SuggestionSource.ONLINE_PLAYERS)
    )
    void open(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }
        if (context.argumentCount() == 0) {
            enderChest.open(player);
            return;
        }
        if (!context.sender().hasPermission("dreamrpg.admin")) {
            context.sender().sendMessage(VanillaCommandMessages.red("你没有权限编辑其他玩家的末影箱。"));
            return;
        }
        Player target = Bukkit.getPlayerExact(context.argument(0));
        if (target == null) {
            context.sender().sendMessage(VanillaCommandMessages.red("目标玩家必须在线。"));
            return;
        }
        enderChest.open(player, target);
    }
}
