package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.campaign.CampaignMenus;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamrpgitems.menu.MythicItemsMenu;
import org.bukkit.entity.Player;


/**
 * Item operations registered under DreamRPG's single command router.
 */
abstract class ItemCommands {
    protected final DreamRPGPlugin plugin;
    protected final DreamLibApi lib;
    protected final CampaignMenus campaign;

    ItemCommands(DreamRPGPlugin plugin, DreamLibApi lib, CampaignMenus campaign) {
        this.plugin = plugin;
        this.lib = lib;
        this.campaign = campaign;
    }

    protected Player player(CommandContext context) {
        if (context.sender() instanceof Player player) return player;
        throw invalid(context, "此操作需要在游戏内执行");
    }

    @CommandHandler(value = "items list", permission = "dreamrpg.admin")
    void items(CommandContext context) {
        context.requireArguments(0);
        Player player = player(context);
        var layout = plugin.itemLibrary().layout().orElse(null);
        if (layout == null) {
            player.sendMessage(LegacyText.colorize("&c物品库配置正在加载, 请稍候。"));
            return;
        }
        new MythicItemsMenu(plugin.items(), layout).open(player, lib.menuService());
    }

    @CommandHandler(value = "items sync", permission = "dreamrpg.admin")
    void synchronize(CommandContext context) {
        context.requireArguments(0);
        var result = plugin.items().synchronizeAll();
        if (!result.success()) {
            throw new IllegalStateException("Cannot synchronize item catalog: " + result.message());
        }
        context.sender().sendMessage(LegacyText.colorize("&a已发起物品引用同步。"));
    }

    private RuntimeException invalid(CommandContext context, String message) {
        context.sender().sendMessage(LegacyText.colorize("&c" + message));
        return context.invalidUsage();
    }

}
