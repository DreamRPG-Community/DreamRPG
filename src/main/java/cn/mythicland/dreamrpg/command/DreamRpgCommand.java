package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.DreamRpgPlugin;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.BukkitCommandComponent;
import cn.mythicland.lib.bootstrap.annotation.CommandComponent;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.command.CommandRouter;
import cn.mythicland.lib.command.CommandUsageException;
import cn.mythicland.lib.command.Subcommand;
import cn.mythicland.lib.command.VanillaCommandMessages;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;

/**
 * Registers the DreamRPG administrative command tree.
 */
@InjectComponent
@CommandComponent
public final class DreamRpgCommand implements BukkitCommandComponent {

    private final CommandRouter router;

    /**
     * Creates the /dreamrpg command router from injected infrastructure.
     *
     * @param plugin owning plugin
     * @param lib shared Lib service
     * @param context initialized DreamRPG context
     */
    public DreamRpgCommand(
            DreamRpgPlugin plugin,
            LibApi lib,
            DreamRpgContext context
    ) {
        Objects.requireNonNull(context, "context");
        this.router = Objects.requireNonNull(lib, "lib").createCommandRouter(plugin, "dreamrpg");
        router.register(new ReloadSubcommand(plugin));
    }

    /**
     * Returns the command declared in plugin.yml.
     *
     * @return command name
     */
    @Override
    public String commandName() {
        return "dreamrpg";
    }

    /**
     * Returns the shared command router.
     *
     * @return command executor
     */
    @Override
    public CommandRouter executor() {
        return router;
    }

    /**
     * Returns the shared command router as tab completer.
     *
     * @return tab completer
     */
    @Override
    public CommandRouter tabCompleter() {
        return router;
    }

    private static final class ReloadSubcommand implements Subcommand {

        private final DreamRpgPlugin plugin;

        private ReloadSubcommand(DreamRpgPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "reload";
        }

        @Override
        public String usage() {
            return "/dreamrpg reload";
        }

        @Override
        public String permission() {
            return "dreamrpg.admin";
        }

        @Override
        public void execute(CommandSender sender, List<String> arguments) {
            if (!arguments.isEmpty()) throw new CommandUsageException(usage());
            plugin.reloadDreamRpg();
            sender.sendMessage(VanillaCommandMessages.green(
                    "DreamRPG 配置已重载。"
            ));
        }
    }
}
