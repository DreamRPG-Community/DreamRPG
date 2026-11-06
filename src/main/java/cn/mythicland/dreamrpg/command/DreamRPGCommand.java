package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.dreamrpg.api.DreamRPGApi;
import cn.mythicland.dreamrpg.api.ExperienceApi;
import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.dreamrpg.campaign.CampaignMenus;
import cn.mythicland.dreamrpg.exchange.ItemExchangeService;
import cn.mythicland.dreamrpg.mob.MythicMobMenu;
import cn.mythicland.dreamrpg.mob.MythicMobService;
import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.dreamrpg.starter.FirstJoinRewardService;
import cn.mythicland.dreamrpg.trade.PlayerTradeService;
import cn.mythicland.dreamrpgcombat.command.CombatStatsDisplay;
import cn.mythicland.framework.bukkit.command.CommandArgument;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandCompleter;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.SuggestionProvider;
import cn.mythicland.framework.bukkit.command.SuggestionSource;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;
import cn.mythicland.framework.component.InjectComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles DreamRPG administrative commands.
 */
@CommandBinding(
        value = "dreamrpg",
        treeHelp = true
)
@InjectComponent
public final class DreamRPGCommand extends ItemCommands implements SuggestionProvider {

    private final FirstJoinRewardService firstJoinRewards;
    private final CampaignCatalog catalog;
    private final ItemExchangeService exchanges;
    private final DreamRPGApi profiles;
    private final ExperienceApi experience;
    private final CoinService coins;
    private final PlayerSettingsService playerSettings;
    private final MythicMobService mobService;
    private final PlayerTradeService trades;

    public DreamRPGCommand(
            DreamRPGPlugin plugin,
            FirstJoinRewardService firstJoinRewards,
            DreamLibApi lib,
            CampaignCatalog catalog,
            ItemExchangeService exchanges,
            DreamRPGApi profiles,
            ExperienceApi experience,
            CoinService coins,
            PlayerSettingsService playerSettings,
            CampaignMenus campaign,
            MythicMobService mobService,
            PlayerTradeService trades
    ) {
        super(plugin, lib, campaign);
        this.mobService = Objects.requireNonNull(mobService, "mobService");
        this.catalog = catalog;
        this.exchanges = exchanges;
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.coins = Objects.requireNonNull(coins, "coins");
        this.playerSettings = Objects.requireNonNull(playerSettings, "playerSettings");
        this.firstJoinRewards = Objects.requireNonNull(firstJoinRewards, "firstJoinRewards");
        this.trades = Objects.requireNonNull(trades, "trades");
    }

    @Override
    public List<String> suggestions(CommandSender sender, CommandContext context) {
        if (context.argumentCount() != 2) return List.of();
        return switch (context.argument(1).toLowerCase(Locale.ROOT)) {
            case "career" -> profiles.careers().stream()
                    .map(career -> career.id())
                    .toList();
            case "knockback" -> List.of("true", "false");
            default -> List.of();
        };
    }

    void change(CommandContext context) {
        context.requireArguments(3);
        Player target = findOnlinePlayer(context.argument(0));
        if (target == null) {
            context.sender().sendMessage(VanillaCommandMessages.red("目标玩家必须在线。"));
            return;
        }
        String field = context.argument(1).toLowerCase(Locale.ROOT);
        String value = context.argument(2);
        try {
            switch (field) {
                case "level" -> changeLevel(context.sender(), target, value);
                case "experience" -> changeExperience(context.sender(), target, value);
                case "coins" -> changeCoins(context.sender(), target, value);
                case "career" -> changeCareer(context.sender(), target, value);
                case "knockback" -> changeKnockback(context.sender(), target, value);
                default -> throw context.invalidUsage();
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            context.sender().sendMessage(VanillaCommandMessages.red(exception.getMessage()));
        }
    }

    private static Player findOnlinePlayer(String input) {
        Player byName = Bukkit.getPlayerExact(input);
        if (byName != null) return byName;
        try {
            return Bukkit.getPlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void changeLevel(CommandSender sender, Player target, String value) {
        long level = Long.parseLong(value);
        if (!experience.isReady(target.getUniqueId())) {
            throw new IllegalStateException("该玩家的经验数据尚未加载完成。");
        }
        experience.setLevel(target.getUniqueId(), level);
        sender.sendMessage(VanillaCommandMessages.green(
                "已将玩家 " + target.getName() + " 的等级设为 " + level + "。"
        ));
    }

    private void changeExperience(CommandSender sender, Player target, String value) {
        BigDecimal amount = new BigDecimal(value);
        if (!experience.isReady(target.getUniqueId())) {
            throw new IllegalStateException("该玩家的经验数据尚未加载完成。");
        }
        var snapshot = experience.setExperience(target.getUniqueId(), amount);
        sender.sendMessage(VanillaCommandMessages.green(
                "已将玩家 " + target.getName() + " 的本级经验设为 " + snapshot.currentExperience()
                        + "（等级 " + snapshot.level() + "）。"
        ));
    }

    private void changeCoins(CommandSender sender, Player target, String value) {
        BigDecimal balance = coins.setBalance(target.getUniqueId(), new BigDecimal(value));
        sender.sendMessage(VanillaCommandMessages.green(
                "已将玩家 " + target.getName() + " 的硬币余额设为 " + balance + "。"
        ));
    }

    private void changeCareer(CommandSender sender, Player target, String careerId) {
        profiles.changeCareer(target.getUniqueId(), careerId).whenComplete((profile, failure) ->
                lib.runOnMain(() -> {
                    if (failure != null) {
                        sender.sendMessage(VanillaCommandMessages.red(
                                "职业修改失败：" + DreamLibApi.rootCauseMessage(failure)
                        ));
                        return;
                    }
                    sender.sendMessage(VanillaCommandMessages.green(
                            "已将玩家 " + target.getName() + " 的职业设为 " + profile.careerId() + "。"
                    ));
                })
        );
    }

    private void changeKnockback(CommandSender sender, Player target, String value) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("击退值只能是 true 或 false。");
        }
        boolean allowed = Boolean.parseBoolean(value);
        playerSettings.setAllowsMobKnockback(target.getUniqueId(), allowed).whenComplete((saved, failure) ->
                lib.runOnMain(() -> {
                    if (failure != null) {
                        sender.sendMessage(VanillaCommandMessages.red(
                                "击退设置修改失败：" + DreamLibApi.rootCauseMessage(failure)
                        ));
                        return;
                    }
                    sender.sendMessage(VanillaCommandMessages.green(
                            "已将玩家 " + target.getName() + " 的怪物击退设为 "
                                    + (saved ? "允许" : "不允许") + "。"
                    ));
                })
        );
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
        return catalog.offers().stream()
                .map(offer -> offer.npc().toString())
                .distinct()
                .sorted()
                .toList();
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

    @CommandRoute(
            path = {"dreamrpg"},
            minArguments = 1,
            maxArguments = 1,
            usages = "/trade <在线玩家>"
    )
    void trade(CommandContext context) {
        if (!context.label().equalsIgnoreCase("trade")) {
            context.sender().sendMessage(VanillaCommandMessages.usage("/dreamrpg <子命令>"));
            return;
        }
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage(VanillaCommandMessages.red("只有玩家可以发起交易。"));
            return;
        }
        Player target = findOnlinePlayer(context.argument(0));
        if (target == null) {
            player.sendMessage(VanillaCommandMessages.red("该玩家不在线。"));
            return;
        }
        trades.request(player, target);
    }

    @CommandRoute(
            path = {"dreamrpg", "npc", "open"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.quests",
            usages = "/dreamrpg npc open <NPC UUID>"
    )
    void npcOpen(CommandContext context) {
        npc(context);
    }

    @CommandCompleter("npc open")
    List<String> completeNpcOpen(CommandContext context) {
        return completeNpc(context);
    }

    @CommandRoute(
            path = {"dreamrpg", "change"},
            maxArguments = 3,
            minArguments = 3,
            permission = "dreamrpg.admin",
            usages = "/dreamrpg change <在线玩家> <level|experience|coins|career|knockback> <值>",
            arguments = {
                    @CommandArgument(index = 0, source = SuggestionSource.ONLINE_PLAYERS),
                    @CommandArgument(index = 1, source = SuggestionSource.STATIC_VALUES, values = {
                            "level", "experience", "coins", "career", "knockback"
                    }),
                    @CommandArgument(index = 2, source = SuggestionSource.COMPONENT, provider = DreamRPGCommand.class)
            }
    )
    void changePlayer(CommandContext context) {
        change(context);
    }

    @CommandRoute(
            path = {"dreamrpg", "admin", "check"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.admin"
    )
    void adminCheck(CommandContext context) {
        check(context);
    }

    @CommandRoute(
            path = {"dreamrpg", "admin", "first-rewards"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.admin"
    )
    void adminFirstRewards(CommandContext context) {
        firstRewards(context);
    }

    // Like /drpg items list, the library is only reachable through its list subcommand.
    @CommandRoute(path = {"dreamrpg", "mobs", "list"}, maxArguments = 0, permission = "dreamrpg.admin")
    void mobsList(CommandContext context) {
        openMobLibrary(context);
    }

    private void openMobLibrary(CommandContext context) {
        new MythicMobMenu(mobService, plugin.combatModule()).open(player(context), lib.menuService());
    }

    @CommandRoute(
            path = {"dreamrpg", "combat", "stats"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.stats"
    )
    void combatStats(CommandContext context) {
        stats(context);
    }

    @CommandRoute(
            path = {"dreamrpg", "combat", "debug"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.admin"
    )
    void debug(CommandContext context) {
        context.requireArguments(0);
        plugin.combatModule().statsService().debug(player(context)).forEach(context.sender()::sendMessage);
    }

    @CommandRoute(
            path = {"dreamrpg", "reload"},
            maxArguments = Integer.MAX_VALUE,
            permission = "dreamrpg.admin"
    )
    void reload(CommandContext context) {
        context.requireArguments(0);
        plugin.reloadDreamRPG();
        context.sender().sendMessage(VanillaCommandMessages.green("主线、成长、战斗与MM内容已重载, 物品同步已执行。"));
    }

    @CommandRoute(path = {"dreamrpg", "content", "check"}, maxArguments = 0, permission = "dreamrpg.admin")
    void checkContent(CommandContext context) {
        plugin.content(context.sender(), false);
    }

    @CommandRoute(path = {"dreamrpg", "content", "reload"}, maxArguments = 0, permission = "dreamrpg.admin")
    void reloadContent(CommandContext context) {
        plugin.content(context.sender(), true);
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
