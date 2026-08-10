package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.text.TemplateRenderer;
import cn.mythicland.lib.text.TextAnimation;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Immutable settings loaded from the standalone scoreboard.yml file.
 */
public record ScoreboardSettings(
        NormalSettings normal,
        LoadingSettings loading
) {

    /**
     * Validates scoreboard settings.
     */
    @SuppressWarnings("DataFlowIssue")
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
        if (configuration.getConfigurationSection("normal") == null) {
            throw new IllegalStateException("scoreboard.yml requires the normal section");
        }
        if (configuration.getConfigurationSection("loading") == null) {
            throw new IllegalStateException("scoreboard.yml requires the loading section");
        }
        return from(ConfigSupport.bind(configuration, RawSettings.class));
    }

    static ScoreboardSettings bind(FileConfiguration configuration, Consumer<String> warningConsumer) {
        Objects.requireNonNull(configuration, "configuration");
        if (configuration.getConfigurationSection("normal") == null) {
            throw new IllegalStateException("scoreboard.yml requires the normal section");
        }
        if (configuration.getConfigurationSection("loading") == null) {
            throw new IllegalStateException("scoreboard.yml requires the loading section");
        }
        return from(ConfigSupport.bind(configuration, RawSettings.class, warningConsumer));
    }

    private static ScoreboardSettings from(RawSettings raw) {
        if (raw.titleEntries().isEmpty()) {
            throw new IllegalStateException("scoreboard.yml requires normal.title-animation");
        }
        TextAnimation titleAnimation = TextAnimation.parse(raw.titleEntries());
        if (raw.lines().isEmpty()) throw new IllegalStateException("scoreboard.yml requires normal.lines");
        TextAnimation loadingTitleAnimation = TextAnimation.parse(raw.loadingTitleEntries());
        if (raw.loadingLines().isEmpty()) {
            throw new IllegalStateException("scoreboard.yml requires loading.lines");
        }
        ZoneId timeZone;
        try {
            timeZone = ZoneId.of(raw.timeZone());
        } catch (DateTimeException exception) {
            throw new IllegalStateException(
                    "Invalid scoreboard.normal.time-zone: " + raw.timeZone(),
                    exception
            );
        }
        return new ScoreboardSettings(
                new NormalSettings(
                        raw.enabled(),
                        titleAnimation,
                        raw.updateTicks(),
                        raw.titleUpdateTicks(),
                        raw.lines(),
                        timeZone
                ),
                new LoadingSettings(loadingTitleAnimation, raw.loadingLines())
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
                .anyMatch(frame -> frame.text().contains(token))
                || loading.lines().stream().anyMatch(line -> line.contains(token))
                || loading.titleAnimation().frames().stream()
                .anyMatch(frame -> frame.text().contains(token));
    }

    /**
     * Returns whether any configured text contains a PlaceholderAPI token.
     *
     * @return true when a percent-delimited token is configured
     */
    public boolean containsPlaceholderApiToken() {
        return normal.lines().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || normal.titleAnimation().frames().stream()
                .anyMatch(frame -> TemplateRenderer.containsPlaceholderApiToken(frame.text()))
                || loading.lines().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || loading.titleAnimation().frames().stream()
                .anyMatch(frame -> TemplateRenderer.containsPlaceholderApiToken(frame.text()));
    }

    /**
     * Immutable normal-state scoreboard settings.
     *
     * @param enabled          whether normal scoreboards are enabled
     * @param titleAnimation   normal title animation
     * @param updateTicks      sidebar refresh period
     * @param titleUpdateTicks title animation refresh period
     * @param lines            normal sidebar lines
     * @param timeZone         time zone used by the {@code {time}} placeholder
     */
    public record NormalSettings(
            boolean enabled,
            TextAnimation titleAnimation,
            long updateTicks,
            long titleUpdateTicks,
            List<String> lines,
            ZoneId timeZone
    ) {

        @SuppressWarnings("DataFlowIssue")
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
     * @param lines          loading sidebar lines
     */
    public record LoadingSettings(
            TextAnimation titleAnimation,
            List<String> lines
    ) {

        @SuppressWarnings("DataFlowIssue")
        public LoadingSettings {
            titleAnimation = Objects.requireNonNull(titleAnimation, "titleAnimation");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (lines.size() > 15) {
                throw new IllegalArgumentException("scoreboard.loading.lines cannot exceed 15 entries");
            }
        }
    }

    private record RawSettings(
            @ConfigValue(
                    path = "normal.enabled",
                    defaultValue = "true"
            )
            boolean enabled,
            @ConfigValue(
                    path = "normal.title-animation",
                    defaultValue = "",
                    trim = false
            )
            List<String> titleEntries,
            @ConfigValue(
                    path = "normal.update-ticks",
                    defaultValue = "20",
                    positive = true
            )
            long updateTicks,
            @ConfigValue(
                    path = "normal.title-update-ticks",
                    defaultValue = "1",
                    positive = true
            )
            long titleUpdateTicks,
            @ConfigValue(
                    path = "normal.lines",
                    defaultValue = "",
                    trim = false
            )
            List<String> lines,
            @ConfigValue(
                    path = "normal.time-zone",
                    defaultValue = "Asia/Shanghai",
                    nonBlank = true
            )
            String timeZone,
            @ConfigValue(
                    path = "loading.title-animation",
                    defaultValue = "",
                    trim = false
            )
            List<String> loadingTitleEntries,
            @ConfigValue(
                    path = "loading.lines",
                    defaultValue = "",
                    trim = false
            )
            List<String> loadingLines
    ) {
    }
}
