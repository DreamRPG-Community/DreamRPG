package cn.mythicland.dreamrpg.chat;

import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.config.DreamRpgSettings;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.lib.text.LegacyText;
import cn.mythicland.lib.text.TemplateRenderer;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Applies the configured DreamRPG chat template and message color policy.
 */
@InjectComponent
@ListenerComponent
public final class DreamRpgChatListener implements Listener {

    private static final String MESSAGE_SLOT = "\u0002dreamrpg-message\u0003";

    private final DreamRpgContext context;
    private final PlayerProfileService profiles;
    private final TemplateRenderer templates;

    /**
     * Creates the chat listener.
     *
     * @param context  initialized DreamRPG context
     * @param profiles profile service
     */
    public DreamRpgChatListener(
            DreamRpgContext context,
            PlayerProfileService profiles
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.templates = context.templates();
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
        values.put("message", MESSAGE_SLOT);

        DreamRpgSettings.ChatSettings settings = context.settings().chat();
        String renderedFormat = templates.render(settings.format(), event.getPlayer(), values);
        event.setFormat(escapeFormat(renderedFormat));
        event.setMessage(formatMessage(event));
    }

    private String formatMessage(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        DreamRpgSettings.ChatSettings settings = context.settings().chat();
        if (event.getPlayer().hasPermission(settings.colorPermission())) {
            return LegacyText.colorize(message);
        }
        String plainMessage = ChatColor.stripColor(LegacyText.colorize(message));
        return ChatColor.WHITE + plainMessage;
    }
}
