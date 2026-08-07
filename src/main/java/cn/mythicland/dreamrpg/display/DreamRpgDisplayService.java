package cn.mythicland.dreamrpg.display;

import cn.mythicland.dreamrpg.api.CareerDefinition;
import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.api.PlayerPresentation;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.dreamrpg.config.ScoreboardSettings;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.integration.PlayerPointsService;
import cn.mythicland.lib.loading.PlayerLoadingGate;
import cn.mythicland.lib.scoreboard.ScoreboardSession;
import cn.mythicland.lib.text.TemplateRenderer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns DreamRPG player presentation, TAB/head teams, and the standalone sidebar scoreboard.
 */
@InjectComponent
public final class DreamRpgDisplayService implements AutoCloseable {

    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final double HEALTH_DISPLAY_SCALE = 40.0D;

    private final JavaPlugin plugin;
    private final LibApi lib;
    private final PlayerProfileService profiles;
    private final TemplateRenderer templates;
    private final CoinService coins;
    private final PlayerPointsService points;
    private final WorldLocationService locations;
    private final PlayerLoadingGate loadingGate;
    private final Map<UUID, ScoreboardSession> sessions = new HashMap<>();
    private ScoreboardSettings settings;
    private long animationTick;

    /**
     * Creates the display service.
     *
     * @param plugin owning plugin
     * @param context initialized DreamRPG context
     * @param profiles profile service
     * @param coins DreamRPG authoritative coin service
     * @param locations world location service
     */
    public DreamRpgDisplayService(
            JavaPlugin plugin,
            DreamRpgContext context,
            PlayerProfileService profiles,
            CoinService coins,
            WorldLocationService locations,
            PlayerLoadingGate loadingGate
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        DreamRpgContext initializedContext = Objects.requireNonNull(context, "context");
        this.lib = initializedContext.lib();
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.templates = initializedContext.templates();
        this.coins = Objects.requireNonNull(coins, "coins");
        this.points = this.lib.playerPointsService();
        this.locations = Objects.requireNonNull(locations, "locations");
        this.loadingGate = Objects.requireNonNull(loadingGate, "loadingGate");
        this.settings = initializedContext.scoreboard();
        this.animationTick = 0L;
    }

    /**
     * Refreshes all online viewers, sidebar lines, and player teams.
     */
    public void refreshAll() {
        ensureMainThread();
        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        for (Player player : onlinePlayers) {
            applyHealthDisplayScale(player);
            applyPlayerName(player, profiles.getProfile(player.getUniqueId()));
        }
        if (!settings.normal().enabled()) {
            closeScoreboardSessions();
            return;
        }
        Map<String, String> teamNames = buildTeamNames();
        Map<String, List<String>> teamEntries = buildTeamEntries(onlinePlayers, teamNames);
        for (Player viewer : onlinePlayers) {
            ScoreboardSession session = sessions.computeIfAbsent(
                    viewer.getUniqueId(),
                    ignored -> lib.scoreboardService().createSession(
                            "dreamrpg",
                            renderTitle(viewer)
                    )
            );
            if (loadingGate.isLoading(viewer)) {
                session.setTitle(renderLoadingTitle(viewer));
                session.setLines(renderLoadingLines(viewer));
            } else {
                session.setTitle(renderTitle(viewer));
                session.setLines(renderLines(viewer));
            }
            session.setBelowName("dummy", "&c❤");
            session.setBelowNameScores(buildHealthScores(onlinePlayers));
            session.retainTeams(teamNames.values());
            for (Map.Entry<String, List<String>> entry : teamEntries.entrySet()) {
                String careerId = findCareerId(entry.getKey(), teamNames);
                CareerDefinition career = profiles.findCareer(careerId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Scoreboard career is missing: " + careerId
                        ));
                session.replaceTeam(
                        entry.getKey(),
                        career.prefix(),
                        entry.getValue()
                );
            }
            session.show(viewer);
        }
    }

    /**
     * Advances the title animation and updates only the title of existing sessions.
     */
    public void advanceTitle() {
        ensureMainThread();
        animationTick = animationTick == Long.MAX_VALUE ? 0L : animationTick + 1L;
        for (Map.Entry<UUID, ScoreboardSession> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            entry.getValue().setTitle(
                    loadingGate.isLoading(player) ? renderLoadingTitle(player) : renderTitle(player)
            );
        }
    }

    /**
     * Refreshes display settings without recreating database or dependency resources.
     *
     * @param refreshedSettings new scoreboard settings
     */
    public void reload(ScoreboardSettings refreshedSettings) {
        settings = Objects.requireNonNull(refreshedSettings, "refreshedSettings");
        animationTick = 0L;
        refreshAll();
    }

    /**
     * Removes one player from the display lifecycle.
     *
     * @param player leaving player
     */
    public void remove(Player player) {
        ensureMainThread();
        Objects.requireNonNull(player, "player");
        ScoreboardSession session = sessions.remove(player.getUniqueId());
        if (session != null) session.close();
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        refreshAll();
    }

    /**
     * Restores all viewers and closes owned scoreboard sessions.
     */
    @Override
    public void close() {
        ensureMainThread();
        for (Player player : new ArrayList<>(plugin.getServer().getOnlinePlayers())) {
            player.setHealthScaled(false);
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
        }
        closeScoreboardSessions();
    }

    private Map<String, String> buildTeamNames() {
        Map<String, String> teamNames = new LinkedHashMap<>();
        int index = 0;
        for (CareerDefinition career : profiles.careers()) {
            String teamName = "drpg" + Integer.toHexString(index++);
            if (teamName.length() > 16) {
                throw new IllegalStateException("Too many configured careers for scoreboard teams");
            }
            teamNames.put(career.id(), teamName);
        }
        return teamNames;
    }

    private Map<String, List<String>> buildTeamEntries(
            List<Player> onlinePlayers,
            Map<String, String> teamNames
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Player player : onlinePlayers) {
            PlayerProfile profile = profiles.getProfile(player.getUniqueId());
            String teamName = teamNames.get(profile.careerId());
            if (teamName == null) {
                throw new IllegalStateException("Career has no scoreboard team: " + profile.careerId());
            }
            result.computeIfAbsent(teamName, ignored -> new ArrayList<>()).add(player.getName());
        }
        return result;
    }

    private static String findCareerId(String teamName, Map<String, String> teamNames) {
        for (Map.Entry<String, String> entry : teamNames.entrySet()) {
            if (entry.getValue().equals(teamName)) return entry.getKey();
        }
        throw new IllegalStateException("Cannot resolve DreamRPG scoreboard career: " + teamName);
    }

    private static Map<String, Integer> buildHealthScores(List<Player> onlinePlayers) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Player player : onlinePlayers) {
            double health = player.getHealth();
            if (!Double.isFinite(health) || health < 0.0D) {
                throw new IllegalStateException("Player has an invalid health value: " + player.getName());
            }
            double roundedHealth = Math.floor(health);
            if (roundedHealth > Integer.MAX_VALUE) {
                throw new IllegalStateException("Player health exceeds scoreboard range: " + player.getName());
            }
            scores.put(player.getName(), (int) roundedHealth);
        }
        return scores;
    }

    private String renderTitle(Player viewer) {
        String title = settings.normal().titleAnimation().frameAt(animationTick).text();
        return templates.render(title, viewer, renderValues(viewer, title));
    }

    private List<String> renderLines(Player viewer) {
        return settings.normal().lines().stream()
                .map(line -> templates.render(line, viewer, renderValues(viewer, line)))
                .toList();
    }

    private String renderLoadingTitle(Player viewer) {
        String title = settings.loading().titleAnimation().frameAt(animationTick).text();
        return templates.render(title, viewer, renderLoadingValues(viewer, title));
    }

    private List<String> renderLoadingLines(Player viewer) {
        return settings.loading().lines().stream()
                .map(line -> templates.render(line, viewer, renderLoadingValues(viewer, line)))
                .toList();
    }

    private Map<String, Object> renderValues(Player viewer, String template) {
        PlayerProfile profile = profiles.getProfile(viewer.getUniqueId());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", viewer.getName());
        values.put("name", viewer.getName());
        values.put("health", formatNumber(viewer.getHealth()));
        AttributeInstance maxHealth = viewer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) throw new IllegalStateException("Player has no generic max-health attribute");
        values.put("max_health", formatNumber(maxHealth.getValue()));
        values.put("career", profile.career().displayName());
        values.put("career_id", profile.careerId());
        values.put("prefix", profile.career().prefix());
        if (template.contains("{time}")) {
            values.put(
                    "time",
                    ZonedDateTime.now(settings.normal().timeZone()).format(CLOCK_FORMATTER)
            );
        }
        if (template.contains("{coins}")) {
            values.put("coins", formatCoins(coins.balance(viewer.getUniqueId())));
        }
        if (template.contains("{points}")) {
            values.put("points", points.formattedPoints(viewer));
        }
        if (template.contains("{location}")) {
            values.put("location", locations.resolve(viewer));
        }
        if (template.contains("{world}")) {
            values.put("world", locations.worldName(viewer.getWorld()));
        }
        return values;
    }

    private Map<String, Object> renderLoadingValues(Player viewer, String template) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", viewer.getName());
        values.put("name", viewer.getName());
        if (template.contains("{time}")) {
            values.put(
                    "time",
                    ZonedDateTime.now(settings.normal().timeZone()).format(CLOCK_FORMATTER)
            );
        }
        return values;
    }

    private static String formatCoins(BigDecimal value) {
        DecimalFormat format = new DecimalFormat(
                "#,##0.##",
                DecimalFormatSymbols.getInstance(Locale.ROOT)
        );
        return format.format(Objects.requireNonNull(value, "value"));
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void applyPlayerName(Player player, PlayerProfile profile) {
        String displayName = profile.career().nameColor() + player.getName();
        if (!displayName.equals(player.getDisplayName())) player.setDisplayName(displayName);
        String playerListName =
                profile.career().prefix()
                        + profile.career().nameColor()
                        + player.getName()
                        + PlayerPresentation.TAB_NAME_SUFFIX;
        if (!playerListName.equals(player.getPlayerListName())) player.setPlayerListName(playerListName);
    }

    private static void applyHealthDisplayScale(Player player) {
        player.setHealthScale(HEALTH_DISPLAY_SCALE);
        player.setHealthScaled(true);
    }

    private static void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Display update must run on the main thread");
    }

    private void closeScoreboardSessions() {
        sessions.values().forEach(ScoreboardSession::close);
        sessions.clear();
    }
}
