package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.text.TextAnimation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable settings loaded from the standalone scoreboard.yml file.
 */
public record ScoreboardSettings(
        NormalSettings normal,
        LoadingSettings loading
) {

    private static final Pattern PLACEHOLDER_API_TOKEN = Pattern.compile("%[A-Za-z0-9_:-]+%");

    /**
     * Validates scoreboard settings.
     */
    public ScoreboardSettings {
        normal = Objects.requireNonNull(normal, "normal");
        loading = Objects.requireNonNull(loading, "loading");
    }

    /**
     * Loads the standalone scoreboard configuration.
     *
     * @param configuration scoreboard.yml configuration
     * @return validated settings
     */
    public static ScoreboardSettings load(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        ConfigurationSection normal = requiredSection(configuration, "normal");
        ConfigurationSection loading = requiredSection(configuration, "loading");
        List<String> titleEntries = stringList(normal, "title-animation");
        if (titleEntries.isEmpty()) {
            throw new IllegalStateException("scoreboard.yml requires normal.title-animation");
        }
        TextAnimation titleAnimation = TextAnimation.parse(titleEntries);
        List<String> lines = stringList(normal, "lines");
        if (lines.isEmpty()) throw new IllegalStateException("scoreboard.yml requires normal.lines");
        TextAnimation loadingTitleAnimation = TextAnimation.parse(stringList(loading, "title-animation"));
        List<String> loadingLines = stringList(loading, "lines");
        if (loadingLines.isEmpty()) throw new IllegalStateException("scoreboard.yml requires loading.lines");
        long updateTicks = positiveLong(normal, "update-ticks");
        long titleUpdateTicks = positiveLongOrDefault(normal, "title-update-ticks", 1L);
        boolean enabled = booleanValue(normal, "enabled");
        String zoneName = stringValue(normal, "time-zone");
        ZoneId timeZone;
        try {
            timeZone = ZoneId.of(zoneName);
        } catch (DateTimeException exception) {
            throw new IllegalStateException("Invalid scoreboard.normal.time-zone: " + zoneName, exception);
        }
        return new ScoreboardSettings(
                new NormalSettings(
                        enabled,
                        titleAnimation,
                        updateTicks,
                        titleUpdateTicks,
                        lines,
                        timeZone
                ),
                new LoadingSettings(loadingTitleAnimation, loadingLines)
        );
    }

    /**
     * Returns whether the scoreboard uses one native placeholder.
     *
     * @param placeholderName placeholder name without braces
     * @return true when configured text contains the placeholder
     */
    public boolean containsNativePlaceholder(String placeholderName) {
        String token = "{" + Objects.requireNonNull(placeholderName, "placeholderName") + "}";
        return normal.lines().stream().anyMatch(line -> line.contains(token))
                || normal.titleAnimation().frames().stream()
                .anyMatch(frame -> frame.text().contains(token));
    }

    /**
     * Returns whether any configured text contains a PlaceholderAPI token.
     *
     * @return true when a percent-delimited token is configured
     */
    public boolean containsPlaceholderApiToken() {
        return normal.lines().stream().anyMatch(line -> PLACEHOLDER_API_TOKEN.matcher(line).find())
                || normal.titleAnimation().frames().stream().anyMatch(
                frame -> PLACEHOLDER_API_TOKEN.matcher(frame.text()).find()
        );
    }

    private static ConfigurationSection requiredSection(
            ConfigurationSection configuration,
            String path
    ) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) throw new IllegalStateException("scoreboard.yml requires the " + path + " section");
        return section;
    }

    private static List<String> stringList(ConfigurationSection configuration, String path) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return List.of();
        if (!(rawValue instanceof List<?> values)) {
            throw new IllegalStateException("Configuration requires a string list: " + path);
        }
        for (Object value : values) {
            if (!(value instanceof String)) {
                throw new IllegalStateException("Configuration requires string list entries: " + path);
            }
        }
        return values.stream().map(String.class::cast).toList();
    }

    private static boolean booleanValue(ConfigurationSection configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof Boolean value)) {
            throw new IllegalStateException("Configuration requires a boolean: " + path);
        }
        return value;
    }

    private static String stringValue(ConfigurationSection configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Configuration requires a non-empty string: " + path);
        }
        return value.trim();
    }

    private static long positiveLong(ConfigurationSection configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof Number value) || value.longValue() < 1L) {
            throw new IllegalStateException("Configuration requires a positive number: " + path);
        }
        return value.longValue();
    }

    private static long positiveLongOrDefault(
            ConfigurationSection configuration,
            String path,
            long defaultValue
    ) {
        if (!configuration.contains(path)) return defaultValue;
        return positiveLong(configuration, path);
    }

    /**
     * Immutable normal-state scoreboard settings.
     *
     * @param enabled whether normal scoreboards are enabled
     * @param titleAnimation normal title animation
     * @param updateTicks sidebar refresh period
     * @param titleUpdateTicks title animation refresh period
     * @param lines normal sidebar lines
     * @param timeZone time zone used by the {@code {time}} placeholder
     */
    public record NormalSettings(
            boolean enabled,
            TextAnimation titleAnimation,
            long updateTicks,
            long titleUpdateTicks,
            List<String> lines,
            ZoneId timeZone
    ) {

        public NormalSettings {
            titleAnimation = Objects.requireNonNull(titleAnimation, "titleAnimation");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            timeZone = Objects.requireNonNull(timeZone, "timeZone");
            if (updateTicks < 1L) {
                throw new IllegalArgumentException("scoreboard.normal.update-ticks must be positive");
            }
            if (titleUpdateTicks < 1L) {
                throw new IllegalArgumentException(
                        "scoreboard.normal.title-update-ticks must be positive"
                );
            }
            if (lines.size() > 15) {
                throw new IllegalArgumentException("scoreboard.normal.lines cannot exceed 15 entries");
            }
        }
    }

    /**
     * Immutable loading-state scoreboard settings.
     *
     * @param titleAnimation loading title animation
     * @param lines loading sidebar lines
     */
    public record LoadingSettings(
            TextAnimation titleAnimation,
            List<String> lines
    ) {

        public LoadingSettings {
            titleAnimation = Objects.requireNonNull(titleAnimation, "titleAnimation");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (lines.size() > 15) {
                throw new IllegalArgumentException("scoreboard.loading.lines cannot exceed 15 entries");
            }
        }
    }
}
