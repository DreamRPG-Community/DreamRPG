package cn.mythicland.dreamrpg.chat;

import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.api.TitleApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.display.PlayerNameFormat;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import cn.mythicland.dreamrpg.member.MemberAccessService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.bukkit.text.TemplateRenderer;
import cn.mythicland.framework.component.InjectComponent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Applies the configured DreamRPG chat template and message color policy.
 */
@InjectComponent
@ListenerComponent
public final class DreamRPGChatListener implements Listener {

    private static final String MESSAGE_SLOT = "\u0002dreamrpg-message\u0003";

    private final DreamRPGContext context;
    private final PlayerProfileService profiles;
    private final MemberAccessService members;
    private final TemplateRenderer templates;
    private final TitleApi titles;

    /**
     * Creates the chat listener.
     *
     * @param context  initialized DreamRPG context
     * @param profiles profile service
     */
    public DreamRPGChatListener(
            DreamRPGContext context,
            PlayerProfileService profiles,
            MemberAccessService members,
            TitleApi titles
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.members = Objects.requireNonNull(members, "members");
        this.templates = context.templates();
        this.titles = Objects.requireNonNull(titles, "titles");
    }

    private static String escapeFormat(String renderedFormat) {
        return renderedFormat.replace("%", "%%").replace(MESSAGE_SLOT, "%2$s");
    }

    /**
     * Applies the chat format at the final normal plugin priority.
     *
     * @param event asynchronous chat event
     */
    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        PlayerProfile profile = profiles.getProfile(event.getPlayer().getUniqueId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prefix", profile.career().prefix());
        values.put("name", profile.career().nameColor() + event.getPlayer().getName());
        String permissionPrefix = members.permissionPrefix(event.getPlayer());
        values.put("lp_prefix", permissionPrefix);
        values.put("member_prefix", members.memberSuffix(event.getPlayer()));
        values.put("message", MESSAGE_SLOT);
        String title = titles.equipped(event.getPlayer().getUniqueId()).map(TitleDefinition::displayName).orElse("");
        values.put("title", title);

        DreamRPGSettings.ChatSettings settings = context.settings().chat();
        String format = PlayerNameFormat.ordered(settings.format(), !title.isEmpty(), !permissionPrefix.isEmpty());
        String renderedFormat = templates.render(
                format,
                event.getPlayer(),
                values
        );
        event.setFormat(escapeFormat(renderedFormat));
        event.setMessage(formatMessage(event));
    }

    private String formatMessage(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        DreamRPGSettings.ChatSettings settings = context.settings().chat();
        if (event.getPlayer().hasPermission(settings.colorPermission())) {
            return LegacyText.colorize(message);
        }
        String plainMessage = ChatColor.stripColor(LegacyText.colorize(message));
        return ChatColor.WHITE + plainMessage;
    }
}
