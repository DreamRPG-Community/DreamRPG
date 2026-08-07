package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Saves the executing administrator's snapped location as the DreamRPG main spawn.
 */
@InjectComponent
@CommandComponent
public final class SetSpawnCommand implements BukkitCommandComponent, CommandExecutor, TabCompleter {

    private final DreamRpgContext context;

    /**
     * Creates the main-spawn configuration command.
     *
     * @param context initialized DreamRPG context
     */
    public SetSpawnCommand(DreamRpgContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public String commandName() {
        return "setspawn";
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
        if (arguments.length != 0) {
            sender.sendMessage(VanillaCommandMessages.usage("/setspawn"));
            return true;
        }
        Location saved = context.setMainSpawn(player.getLocation());
        sender.sendMessage(VanillaCommandMessages.green(formatSuccess(saved)));
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
}
