package cn.mythicland.dreamrpg.member;

import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.framework.bukkit.luckperms.LuckPermsNameStyles;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.text.PlayerNameStyle;
import cn.mythicland.memberlib.MemberNameStyle;
import cn.mythicland.memberlib.MemberKey;
import cn.mythicland.memberlib.MemberScope;
import cn.mythicland.memberlib.MemberService;
import cn.mythicland.memberlib.MemberView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.luckperms.api.LuckPermsProvider;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Resolves ThePit-compatible player name styles and DreamRPG membership access.
 */
@InjectComponent
public final class MemberAccessService {

    private static final String STAFF_GROUP = "staff";
    private static final Set<MemberScope> DISPLAY_SCOPES = Set.of(MemberScope.NETWORK);
    private static final MemberNameStyle DEFAULT_NAME_STYLE = new MemberNameStyle("&7[玩家]");
    private static final Set<MemberKey> VIP_PLUS_RANKS = Set.of(
            MythicMemberRuntime.VIP_PLUS,
            MythicMemberRuntime.MVP_PLUS,
            MythicMemberRuntime.MVP_PLUS_PLUS
    );

    private final MemberService memberships;
    private final Function<Player, PlayerNameStyle> permissionStyleResolver;

    public MemberAccessService(DreamRPGContext context) {
        this.memberships = context.membership().service();
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            this.permissionStyleResolver = player -> new PlayerNameStyle("");
            return;
        }
        LuckPermsNameStyles luckPerms = new LuckPermsNameStyles(LuckPermsProvider.get());
        this.permissionStyleResolver = player -> luckPerms.resolve(
                player,
                STAFF_GROUP,
                () -> new PlayerNameStyle("")
        );
    }

    public String prefix(Player player) {
        String permissionPrefix = permissionPrefix(player);
        return permissionPrefix.isEmpty() ? memberSuffix(player) : permissionPrefix;
    }

    public String permissionPrefix(Player player) {
        return permissionStyleResolver.apply(player).prefix();
    }

    public String memberSuffix(Player player) {
        return memberNameStyle(player).prefix();
    }

    public boolean hasVipPlus(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.memberships.isLoaded(playerId)) return false;
        return this.memberships.view(playerId).primary(DISPLAY_SCOPES)
                .map(active -> VIP_PLUS_RANKS.contains(active.definition().key()))
                .orElse(false);
    }

    public CompletableFuture<MemberView> load(UUID playerId) {
        if (this.memberships.isLoaded(playerId)) {
            return CompletableFuture.completedFuture(this.memberships.view(playerId));
        }
        return this.memberships.load(playerId);
    }

    private PlayerNameStyle memberNameStyle(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.memberships.isLoaded(playerId)) return DEFAULT_NAME_STYLE.playerNameStyle();
        return this.memberships.view(playerId)
                .nameStyle(DISPLAY_SCOPES, DEFAULT_NAME_STYLE)
                .playerNameStyle();
    }
}
