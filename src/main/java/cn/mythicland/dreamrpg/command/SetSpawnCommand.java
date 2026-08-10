package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/**
 * Saves the executing administrator's snapped location as the DreamRPG main spawn.
 */
@CommandComponent(
        value = "setspawn",
        permission = "dreamrpg.admin",
        takeOverGlobalMapping = true
)
public final class SetSpawnCommand {

    private final DreamRpgContext context;

    public SetSpawnCommand(DreamRpgContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    private static String formatSuccess(Location location) {
        return String.format(
                Locale.ROOT,
                "已设置主城出生点: %s (%.2f, %.2f, %.2f, yaw %.0f, pitch %.0f)",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    @CommandHandler
    void setSpawn(CommandContext commandContext) {
        commandContext.requireArguments(0);
        if (!(commandContext.sender() instanceof Player player)) {
            commandContext.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }
        Location saved = context.setMainSpawn(player.getLocation());
        commandContext.sender().sendMessage(VanillaCommandMessages.green(formatSuccess(saved)));
    }
}
