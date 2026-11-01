package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.settings.PlayerSettingsMenu;
import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Opens the player's DreamRPG options menu.
 */
@InjectComponent
@CommandBinding("settings")
public final class PlayerSettingsCommand {

    private final JavaPlugin plugin;
    private final DreamRPGContext context;
    private final PlayerSettingsService settings;
    private final MenuManager menus;

    public PlayerSettingsCommand(
            JavaPlugin plugin,
            DreamRPGContext context,
            PlayerSettingsService settings,
            MenuManager menus
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.context = Objects.requireNonNull(context, "context");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    @CommandRoute(path = {"settings"}, maxArguments = Integer.MAX_VALUE, usages = "/settings")
    void open(CommandContext command) {
        command.requireArguments(0);
        if (!(command.sender() instanceof Player player)) {
            command.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以使用此命令。"));
            return;
        }

        this.settings.load(player.getUniqueId())
                .thenCompose(ignored -> this.context.lib().supplyOnMain(() -> {
                    if (player.isOnline()) {
                        this.menus.open(player, new PlayerSettingsMenu(
                                this.plugin,
                                this.context.lib(),
                                this.settings
                        ));
                    }
                    return null;
                }))
                .whenComplete((ignored, failure) -> {
                    if (failure == null) return;
                    this.context.lib().runOnMain(() -> {
                        this.plugin.getLogger().severe(
                                "Failed to open player settings for " + player.getUniqueId() + ": "
                                        + failure.getMessage()
                        );
                        if (player.isOnline()) player.sendMessage("§c读取游戏选项失败，请稍后重试。");
                    });
                });
    }
}
