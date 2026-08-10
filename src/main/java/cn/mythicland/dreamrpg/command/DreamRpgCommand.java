package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.DreamRpgPlugin;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandHandler;
import cn.mythicland.lib.command.CommandContext;
import cn.mythicland.lib.command.VanillaCommandMessages;

import java.util.Objects;

/**
 * Handles DreamRPG administrative commands.
 */
@CommandComponent("dreamrpg")
public final class DreamRpgCommand {

    private final DreamRpgPlugin plugin;

    public DreamRpgCommand(DreamRpgPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @CommandHandler(value = "reload", permission = "dreamrpg.admin")
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadDreamRpg();
        context.sender().sendMessage(VanillaCommandMessages.green("DreamRPG 配置已重载。"));
    }
}
