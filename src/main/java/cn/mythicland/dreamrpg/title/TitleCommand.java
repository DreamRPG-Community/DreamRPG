package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandCompleter;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.memberlib.MemberDurationParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Console administration and player selection share the framework command router.
 */
@InjectComponent
@CommandBinding(value = "titles", treeHelp = true)
public final class TitleCommand {
    private static final String ADMIN = "dreamrpg.admin";
    private final JavaPlugin plugin;
    private final DreamRPGContext context;
    private final DreamLibApi lib;
    private final TitleCatalog catalog;
    private final TitleService titles;

    public TitleCommand(JavaPlugin plugin, DreamRPGContext context, TitleCatalog catalog, TitleService titles) {
        this.plugin = plugin;
        this.context = context;
        this.lib = context.lib();
        this.catalog = catalog;
        this.titles = titles;
    }

    private static Player player(CommandContext command) {
        if (command.sender() instanceof Player player) return player;
        throw new IllegalArgumentException("此操作需要在游戏内执行。");
    }

    private static void requireDefinition(YamlConfiguration yaml, String id) {
        if (!yaml.isConfigurationSection("titles." + id)) throw new IllegalArgumentException("Unknown title: " + id);
    }

    @CommandRoute(path = {"titles"}, maxArguments = 0, usages = "/ch")
    void usage(CommandContext command) {
        command.sender().sendMessage("§e使用 /" + command.label() + " open 打开称号界面，/" + command.label() + " help 查看帮助。");
    }

    @CommandRoute(path = {"titles", "open"}, maxArguments = 0, usages = "/ch open")
    void open(CommandContext command) {
        Player player = player(command);
        complete(command.sender(), titles.load(player.getUniqueId()).thenCompose(state -> lib.runOnMain(() -> {
            if (plugin.isEnabled() && player.isOnline()) lib.menuService().open(player, new TitleMenu(titles, lib));
        })), null);
    }

    @CommandRoute(path = {"titles", "select"}, minArguments = 1, maxArguments = 1, usages = "/titles select <ID>")
    void select(CommandContext command) {
        complete(command.sender(), titles.select(player(command).getUniqueId(), command.argument(0)), "已佩戴称号。");
    }

    @CommandRoute(path = {"titles", "clear"}, maxArguments = 0)
    void clear(CommandContext command) {
        complete(command.sender(), titles.select(player(command).getUniqueId(), ""), "已卸下称号。");
    }

    @CommandRoute(path = {"titles", "list"}, maxArguments = 0, permission = ADMIN)
    void list(CommandContext command) {
        command.sender().sendMessage("§a已配置称号: " + catalog.definitions().size());
        catalog.definitions().values().forEach(title -> {
            command.sender().sendMessage(LegacyText.colorize("&e" + title.id() + " &f" + title.displayName()));
            for (CombatStat stat : CombatStat.values()) {
                var value = title.bonuses().get(stat);
                if (value != null)
                    command.sender().sendMessage("  §7" + stat.configKey() + ": " + TitleMenu.format(value));
            }
        });
    }

    @CommandRoute(
            path = {"titles", "create"}, minArguments = 2, maxArguments = Integer.MAX_VALUE,
            permission = ADMIN, usages = "/titles create <ID> <显示名称>"
    )
    void create(CommandContext command) {
        String id = command.argument(0);
        String name = String.join(" ", command.arguments().subList(1, command.argumentCount()));
        new TitleDefinition(id, name, Map.of());
        complete(command.sender(), catalog.edit(yaml -> {
            if (yaml.contains("titles." + id)) throw new IllegalArgumentException("Title already exists: " + id);
            yaml.set("titles." + id + ".name", name);
        }), "已创建称号 " + id + "。");
    }

    @CommandRoute(
            path = {"titles", "rename"}, minArguments = 2, maxArguments = Integer.MAX_VALUE,
            permission = ADMIN, usages = "/titles rename <ID> <显示名称>"
    )
    void rename(CommandContext command) {
        String id = catalog.require(command.argument(0)).id();
        String name = String.join(" ", command.arguments().subList(1, command.argumentCount()));
        complete(command.sender(), catalog.edit(yaml -> {
            requireDefinition(yaml, id);
            yaml.set("titles." + id + ".name", name);
        }), "已修改称号名称。");
    }

    @CommandRoute(
            path = {"titles", "stat"}, minArguments = 3, maxArguments = 3,
            permission = ADMIN, usages = "/titles stat <ID> <属性键> <数值|范围|remove>"
    )
    void stat(CommandContext command) {
        String id = catalog.require(command.argument(0)).id();
        CombatStat stat = TitleCatalog.stat(command.argument(1));
        String input = command.argument(2);
        String value = input.equalsIgnoreCase("remove") ? null : TitleMenu.format(TitleCatalog.normalize(
                stat, NumericRange.parse(input)));
        complete(command.sender(), catalog.edit(yaml -> {
            requireDefinition(yaml, id);
            yaml.set("titles." + id + ".stats." + stat.configKey(), value);
        }), "已修改称号属性。");
    }

    @CommandRoute(
            path = {"titles", "delete"}, minArguments = 1, maxArguments = 1,
            permission = ADMIN, usages = "/titles delete <ID>"
    )
    void delete(CommandContext command) {
        String id = catalog.require(command.argument(0)).id();
        complete(command.sender(), catalog.edit(yaml -> {
            requireDefinition(yaml, id);
            yaml.set("titles." + id, null);
        }), "已删除称号定义。");
    }

    @CommandRoute(
            path = {"titles", "grant"}, minArguments = 2, maxArguments = 3,
            permission = ADMIN, usages = "/titles grant <玩家|UUID> <ID> [0|7d|12h|30m|60s]"
    )
    void grant(CommandContext command) {
        String id = catalog.require(command.argument(1)).id();
        String duration = command.argumentCount() == 3 ? command.argument(2) : "0";
        long millis = duration.equals("0") ? 0 : MemberDurationParser.parse(duration).toMillis();
        withPlayer(command, playerId -> titles.grant(playerId, id, millis), "已发放称号。");
    }

    @CommandRoute(
            path = {"titles", "revoke"}, minArguments = 2, maxArguments = 2,
            permission = ADMIN, usages = "/titles revoke <玩家|UUID> <ID>"
    )
    void revoke(CommandContext command) {
        withPlayer(command, playerId -> titles.revoke(playerId, command.argument(1)), "已撤销称号。");
    }

    @CommandRoute(
            path = {"titles", "info"}, minArguments = 1, maxArguments = 1,
            permission = ADMIN, usages = "/titles info <玩家|UUID>"
    )
    void info(CommandContext command) {
        withPlayer(command, playerId -> titles.load(playerId).thenCompose(state -> lib.runOnMain(() -> {
            command.sender().sendMessage("§a玩家称号: " + playerId + "，已选: " + state.selectedId());
            state.expirations().forEach((id, expiry) -> command.sender().sendMessage(
                    "§e" + id + " §7" + (expiry == 0 ? "永久" : Instant.ofEpochMilli(expiry).toString())
                            + (state.owns(id, System.currentTimeMillis()) ? "" : " (已过期)")));
        })), null);
    }

    @CommandRoute(path = {"titles", "reload"}, maxArguments = 0, permission = ADMIN)
    void reload(CommandContext command) {
        complete(command.sender(), lib.supplyAsync(() -> TitleCatalog.validateContent(lib.content()))
                .thenCompose(ignored -> lib.runOnMain(catalog::reload)), "称号配置已重载。");
    }

    @CommandCompleter("stat")
    List<String> completeStat(CommandContext command) {
        if (command.argumentCount() == 1) return ids();
        if (command.argumentCount() == 2) return Arrays.stream(CombatStat.values()).map(CombatStat::configKey).toList();
        return List.of("remove", "0", "10", "10-20");
    }

    @CommandCompleter("select")
    List<String> completeSelect(CommandContext command) {
        if (!(command.sender() instanceof Player player)) return List.of();
        return titles.snapshot(player.getUniqueId()).stream()
                .flatMap(state -> ids().stream().filter(id -> state.owns(id, System.currentTimeMillis()))).toList();
    }

    @CommandCompleter("grant")
    List<String> completeGrant(CommandContext command) {
        if (command.argumentCount() == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (command.argumentCount() == 2) return ids();
        return List.of("0", "7d", "12h", "30m", "60s");
    }

    @CommandCompleter("revoke")
    List<String> completeRevoke(CommandContext command) {
        return completeGrant(command);
    }

    @CommandCompleter("info")
    List<String> completeInfo(CommandContext command) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    @CommandCompleter("rename")
    List<String> completeRename(CommandContext command) {
        return command.argumentCount() == 1 ? ids() : List.of();
    }

    @CommandCompleter("delete")
    List<String> completeDelete(CommandContext command) {
        return ids();
    }

    private List<String> ids() {
        return List.copyOf(catalog.definitions().keySet());
    }

    private void withPlayer(CommandContext command, Function<UUID, CompletableFuture<?>> action, String success) {
        var work = context.membership().service().findPlayer(command.argument(0)).thenCompose(found ->
                lib.supplyOnMain(() -> action.apply(found.orElseThrow(
                        () -> new IllegalArgumentException("未找到玩家: " + command.argument(0))))).thenCompose(result -> result));
        complete(command.sender(), work, success);
    }

    private void complete(CommandSender sender, CompletableFuture<?> work, String success) {
        work.whenComplete((result, failure) -> lib.runOnMain(() -> {
            if (!plugin.isEnabled()) return;
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Title operation failed", failure);
                sender.sendMessage("§c称号操作失败: " + DreamLibApi.rootCauseMessage(failure));
            } else if (success != null) sender.sendMessage("§a" + success);
        }));
    }
}
