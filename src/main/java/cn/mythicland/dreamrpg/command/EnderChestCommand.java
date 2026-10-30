package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.enderchest.EnderChestService;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.command.VanillaCommandMessages;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Opens the executing player's custom ender chest.
 */
@CommandComponent("enderchest")
public final class EnderChestCommand {

    private final EnderChestService enderChest;

    public EnderChestCommand(EnderChestService enderChest) {
        this.enderChest = Objects.requireNonNull(enderChest, "enderChest");
    }

    @CommandHandler
    void open(CommandContext context) {
        context.requireArguments(0);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }
        enderChest.open(player);
    }
}
