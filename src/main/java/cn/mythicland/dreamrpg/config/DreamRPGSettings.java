package cn.mythicland.dreamrpg.config;

import cn.mythicland.framework.bukkit.config.ConfigSupport;
import cn.mythicland.framework.bukkit.config.ConfigView;
import cn.mythicland.framework.bukkit.config.annotation.ConfigProperty;
import cn.mythicland.framework.bukkit.text.TemplateRenderer;

import java.util.List;
import java.util.Objects;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable DreamRPG settings loaded from config.yml.
 */
public record DreamRPGSettings(
        SpawnSettings spawn,
        ChatSettings chat,
        DisplaySettings display
) {

    /**
     * Validates grouped settings.
     */
    @SuppressWarnings("DataFlowIssue")
    public DreamRPGSettings {
        spawn = Objects.requireNonNull(spawn, "spawn");
        chat = Objects.requireNonNull(chat, "chat");
        display = Objects.requireNonNull(display, "display");
    }

    /**
     * Loads strict settings from config.yml.
     *
     * @param configuration configuration with defaults materialized
     * @return validated settings
     */
    public static DreamRPGSettings load(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return from(ConfigSupport.bind(configuration, RawSettings.class));
    }

    static DreamRPGSettings bind(ConfigView configuration) {
        return from(Objects.requireNonNull(configuration, "configuration").bind(RawSettings.class));
    }

    private static DreamRPGSettings from(RawSettings raw) {
        SpawnSettings spawn = new SpawnSettings(
                raw.spawnWorld(),
                raw.teleportOnJoin(),
                raw.teleportOnRespawn()
        );
        ChatSettings chat = new ChatSettings(raw.chatFormat(), raw.colorPermission());
        DisplaySettings display = new DisplaySettings(
                new TabSettings(raw.tabHeader(), raw.tabFooter(), raw.playerNameFormat()),
                raw.nameTagFormat()
        );
        return new DreamRPGSettings(spawn, chat, display);
    }

    private static String requireTemplate(String value, String path) {
        String template = Objects.requireNonNull(value, path);
        if (template.isBlank()) throw new IllegalArgumentException(path + " cannot be blank");
        return template;
    }

    @SuppressWarnings("SameParameterValue")
    private static void requireSingleNameSlot(String template, String path) {
        String slot = "{name}";
        int first = template.indexOf(slot);
        if (first < 0 || first != template.lastIndexOf(slot)) {
            throw new IllegalArgumentException(path + " must contain exactly one {name} placeholder");
        }
    }

    /**
     * Returns whether chat or player display configuration contains a PlaceholderAPI token.
     *
     * @return true when a configured chat, TAB, or name-tag template uses PlaceholderAPI
     */
    public boolean containsPlaceholderApiToken() {
        return TemplateRenderer.containsPlaceholderApiToken(
                        chat.format().replace("%luckperms_prefix%", "")
                )
                || display.tab().header().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || display.tab().footer().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || TemplateRenderer.containsPlaceholderApiToken(
                        display.tab().playerNameFormat().replace("%luckperms_prefix%", "")
                )
                || TemplateRenderer.containsPlaceholderApiToken(
                        display.nameTagFormat().replace("%luckperms_prefix%", "")
                );
    }

    /**
     * Main-city spawn settings.
     */
    public record SpawnSettings(
            String worldName,
            boolean teleportOnJoin,
            boolean teleportOnRespawn
    ) {

        /**
         * Validates spawn settings.
         */
        public SpawnSettings {
            worldName = Objects.requireNonNull(worldName, "worldName").trim();
            if (worldName.isBlank()) throw new IllegalArgumentException("spawn.world cannot be blank");
        }
    }

    /**
     * Chat presentation settings.
     */
    public record ChatSettings(String format, String colorPermission) {

        /**
         * Validates chat settings.
         */
        public ChatSettings {
            format = Objects.requireNonNull(format, "format");
            colorPermission = Objects.requireNonNull(colorPermission, "colorPermission").trim();
            if (format.isBlank()) throw new IllegalArgumentException("chat.format cannot be blank");
            if (colorPermission.isBlank()) throw new IllegalArgumentException("chat.color-permission cannot be blank");
        }
    }

    /**
     * Player-facing TAB and name-tag presentation settings.
     */
    public record DisplaySettings(TabSettings tab, String nameTagFormat) {

        /**
         * Validates display settings.
         */
        @SuppressWarnings("DataFlowIssue")
        public DisplaySettings {
            tab = Objects.requireNonNull(tab, "tab");
            nameTagFormat = requireTemplate(nameTagFormat, "display.name-tag-format");
            requireSingleNameSlot(nameTagFormat, "display.name-tag-format");
        }

    }

    /**
     * TAB header, footer, and individual player-name settings.
     */
    public record TabSettings(
            List<String> header,
            List<String> footer,
            String playerNameFormat
    ) {

        /**
         * Validates and freezes TAB settings.
         */
        public TabSettings {
            header = List.copyOf(Objects.requireNonNull(header, "header"));
            footer = List.copyOf(Objects.requireNonNull(footer, "footer"));
            playerNameFormat = requireTemplate(playerNameFormat, "display.tab.player-name-format");
        }
    }

    private record RawSettings(
            @ConfigProperty(
                    path = "spawn.world",
                    defaultValue = "world",
                    nonBlank = true
            )
            String spawnWorld,
            @ConfigProperty(
                    path = "spawn.teleport-on-join",
                    defaultValue = "true"
            )
            boolean teleportOnJoin,
            @ConfigProperty(
                    path = "spawn.teleport-on-respawn",
                    defaultValue = "true"
            )
            boolean teleportOnRespawn,
            @ConfigProperty(
                    path = "chat.format",
                    defaultValue = "{lp_prefix} {title} {prefix} {name} {member_prefix}&7: &f{message}",
                    nonBlank = true,
                    trim = false
            )
            String chatFormat,
            @ConfigProperty(
                    path = "chat.color-permission",
                    defaultValue = "dreamrpg.chat.color",
                    nonBlank = true
            )
            String colorPermission,
            @ConfigProperty(
                    path = "display.tab.header",
                    defaultValue = "",
                    trim = false
            )
            List<String> tabHeader,
            @ConfigProperty(
                    path = "display.tab.footer",
                    defaultValue = "",
                    trim = false
            )
            List<String> tabFooter,
            @ConfigProperty(
                    path = "display.tab.player-name-format",
                    defaultValue = "{lp_prefix} {title} {prefix} {name} {member_prefix}        ",
                    nonBlank = true,
                    trim = false
            )
            String playerNameFormat,
            @ConfigProperty(
                    path = "display.name-tag-format",
                    defaultValue = "{lp_prefix} {title} {prefix} {name} {member_prefix}",
                    nonBlank = true,
                    trim = false
            )
            String nameTagFormat
    ) {
    }
}
