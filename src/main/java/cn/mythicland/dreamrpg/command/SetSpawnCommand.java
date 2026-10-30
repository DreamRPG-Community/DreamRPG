package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.command.VanillaCommandMessages;
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

    private final DreamRPGContext context;
    private final DreamLibApi lib;

    public SetSpawnCommand(DreamRPGContext context, DreamLibApi lib) {
        this.lib = lib;
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
        context.setMainSpawn(player.getLocation()).whenComplete((saved, error) -> lib.runOnMain(() -> {
            if (error != null)
                player.sendMessage(VanillaCommandMessages.red("出生点保存失败: " + DreamLibApi.rootCauseMessage(error)));
            else player.sendMessage(VanillaCommandMessages.green(formatSuccess(saved)));
        }));
    }
}
