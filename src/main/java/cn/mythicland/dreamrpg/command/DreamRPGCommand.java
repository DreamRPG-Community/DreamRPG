package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.command.CombatStatsDisplay;
import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.dreamrpg.exchange.ItemExchangeService;
import cn.mythicland.dreamrpg.campaign.CampaignMenus;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.starter.FirstJoinRewardService;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import cn.mythicland.dreamlib.bootstrap.annotation.CommandCompleter;
import cn.mythicland.dreamlib.command.CommandContext;
import cn.mythicland.dreamlib.command.VanillaCommandMessages;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

/**
 * Handles DreamRPG administrative commands.
 */
@CommandComponent(
        value = "dreamrpg",
        treeHelp = true
)
public final class DreamRPGCommand extends ItemCommands {

    private final FirstJoinRewardService firstJoinRewards;
    private final CampaignCatalog catalog;
    private final ItemExchangeService exchanges;

    public DreamRPGCommand(
            DreamRPGPlugin plugin,
            FirstJoinRewardService firstJoinRewards,
            DreamLibApi lib,
            CampaignCatalog catalog,
            ItemExchangeService exchanges,
            CampaignMenus campaign
    ) {
        super(plugin, lib, campaign);
        this.catalog = catalog;
        this.exchanges = exchanges;
        this.firstJoinRewards = Objects.requireNonNull(firstJoinRewards, "firstJoinRewards");
    }

    void npc(CommandContext context) {
        context.requireArguments(1);
        UUID id;
        try {
            id = UUID.fromString(context.argument(0));
        } catch (IllegalArgumentException exception) {
            throw context.invalidUsage();
        }
        campaign.openNpc(player(context), id);
    }

    List<String> completeNpc(CommandContext context) {
        if (context.arguments().size() != 1) return List.of();
        return catalog.offers().stream().map(offer -> offer.npc().toString()).distinct().sorted().toList();
    }

    void check(CommandContext context) {
        context.requireArguments(0);
        var problems = exchanges.problems();
        if (problems.isEmpty())
            context.sender().sendMessage(VanillaCommandMessages.green("已检查 " + catalog.offers().size()
                    + " 个NPC项目, 所有引用均已就绪。"));
        else {
            context.sender().sendMessage(VanillaCommandMessages.red("发现 " + problems.size() + " 个未就绪引用:"));
            problems.forEach(problem -> context.sender().sendMessage(problem));
        }
    }

    void stats(CommandContext context) {
        context.requireArguments(0);
        Player player = player(context);
        var combat = plugin.combatModule();
        CombatStatsDisplay.render(combat.statsService().getStats(player), combat.settings())
                .forEach(player::sendMessage);
        combat.statsService().levelRequirementDisplay(player).forEach(player::sendMessage);
    }

    @CommandHandler(value = "npc open", permission = "dreamrpg.quests", usage = "/dreamrpg npc open <NPC UUID>")
    void npcOpen(CommandContext context) {
        npc(context);
    }

    @CommandCompleter("npc open")
    List<String> completeNpcOpen(CommandContext context) {
        return completeNpc(context);
    }

    @CommandHandler(value = "admin check", permission = "dreamrpg.admin")
    void adminCheck(CommandContext context) {
        check(context);
    }

    @CommandHandler(value = "admin first-rewards", permission = "dreamrpg.admin")
    void adminFirstRewards(CommandContext context) {
        firstRewards(context);
    }

    @CommandHandler(
            value = "combat stats",
            permission = "dreamrpg.stats"
    )
    void combatStats(CommandContext context) {
        stats(context);
    }

    @CommandHandler(
            value = "combat debug",
            permission = "dreamrpg.admin"
    )
    void debug(CommandContext context) {
        context.requireArguments(0);
        plugin.combatModule().statsService().debug(player(context)).forEach(context.sender()::sendMessage);
    }

    @CommandHandler(
            value = "reload",
            permission = "dreamrpg.admin"
    )
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadDreamRPG();
        context.sender().sendMessage(VanillaCommandMessages.green("主线、成长、战斗与MM内容已重载, 物品同步已执行。"));
    }

    void firstRewards(CommandContext context) {
        context.requireArguments(0);
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("该命令只能由玩家执行。"));
            return;
        }
        firstJoinRewards.openEditor(player);
    }
}
