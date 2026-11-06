package cn.mythicland.dreamrpg.display;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.api.TitleApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.event.EquippedTitleChangedEvent;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import cn.mythicland.dreamrpg.config.ScoreboardSettings;
import cn.mythicland.dreamrpg.member.MemberAccessService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.integration.PlayerPointsService;
import cn.mythicland.framework.bukkit.player.PlayerLoadingGate;
import cn.mythicland.framework.bukkit.scoreboard.ScoreboardSession;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.bukkit.text.TemplateRenderer;
import cn.mythicland.framework.component.InjectComponent;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

/**
 * Owns DreamRPG player presentation, TAB/head teams, and the standalone sidebar scoreboard.
 */
@InjectComponent
@ListenerComponent
public final class DreamRPGDisplayService implements AutoCloseable, Listener {

    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final double HEALTH_DISPLAY_SCALE = 40.0D;
    private static final String NAME_TAG_MARKER = "\u0002dreamrpg-name-tag\u0003";
    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final PlayerProfileService profiles;
    private final MemberAccessService members;
    private final TemplateRenderer templates;
    private final CoinService coins;
    private final PlayerPointsService points;
    private final WorldLocationService locations;
    private final PlayerLoadingGate loadingGate;
    private final TitleApi titles;
    private final Map<UUID, ScoreboardSession> sessions = new HashMap<>();
    private final Map<UUID, TabHeaderFooter> tabHeaderFooters = new HashMap<>();
    private final Map<UUID, String> memberPrefixes = new HashMap<>();
    private final Map<UUID, String> permissionPrefixes = new HashMap<>();
    private final Set<String> nameTagOverflowWarnings = new HashSet<>();
    private ScoreboardSettings settings;
    private DreamRPGSettings.DisplaySettings displaySettings;
    private long animationTick;

    /**
     * Creates the display service.
     *
     * @param plugin      owning plugin
     * @param context     initialized DreamRPG context
     * @param profiles    profile service
     * @param coins       DreamRPG authoritative coin service
     * @param locations   world location service
     * @param loadingGate profile-loading state service
     */
    public DreamRPGDisplayService(
            JavaPlugin plugin,
            DreamRPGContext context,
            PlayerProfileService profiles,
            MemberAccessService members,
            CoinService coins,
            WorldLocationService locations,
            PlayerLoadingGate loadingGate,
            TitleApi titles
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        DreamRPGContext initializedContext = Objects.requireNonNull(context, "context");
        this.lib = initializedContext.lib();
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.members = Objects.requireNonNull(members, "members");
        this.templates = initializedContext.templates();
        this.coins = Objects.requireNonNull(coins, "coins");
        this.points = this.lib.playerPointsService();
        this.locations = Objects.requireNonNull(locations, "locations");
        this.loadingGate = Objects.requireNonNull(loadingGate, "loadingGate");
        this.titles = Objects.requireNonNull(titles, "titles");
        this.settings = initializedContext.scoreboard();
        this.displaySettings = initializedContext.settings().display();
        this.animationTick = 0L;
    }

    private static Map<UUID, String> buildTeamNames(List<Player> onlinePlayers) {
        Map<UUID, String> teamNames = new LinkedHashMap<>();
        for (int index = 0; index < onlinePlayers.size(); index++) {
            Player player = onlinePlayers.get(index);
            String teamName = "drpg" + Integer.toHexString(index);
            teamNames.put(player.getUniqueId(), teamName);
        }
        return teamNames;
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

    private static String normalizeTabText(String text) {
        return LegacyText.stripColor(text).isBlank() ? "" : text;
    }

    private static BaseComponent[] toComponents(String text) {
        return text.isEmpty() ? new BaseComponent[0] : TextComponent.fromLegacyText(text);
    }

    private static void applyHealthDisplayScale(Player player) {
        player.setHealthScale(HEALTH_DISPLAY_SCALE);
        player.setHealthScaled(true);
    }

    private static void ensureMainThread() {
        MainThread.require("Display update must run on the main thread");
    }

    /**
     * Refreshes all online viewers, sidebar lines, and player teams.
     */
    @SuppressWarnings("resource")
    public void refreshAll() {
        ensureMainThread();
        List<Player> onlinePlayers = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        Map<UUID, PlayerProfile> onlineProfiles = new LinkedHashMap<>();
        for (Player player : onlinePlayers) {
            PlayerProfile profile = profiles.getProfile(player.getUniqueId());
            onlineProfiles.put(player.getUniqueId(), profile);
            memberPrefixes.put(player.getUniqueId(), members.memberSuffix(player));
            permissionPrefixes.put(player.getUniqueId(), members.permissionPrefix(player));
            applyHealthDisplayScale(player);
            applyPlayerName(player, profile);
        }
        if (!settings.normal().enabled()) {
            closeScoreboardSessions();
            return;
        }
        Map<UUID, String> teamNames = buildTeamNames(onlinePlayers);
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
            for (Player target : onlinePlayers) {
                String teamName = teamNames.get(target.getUniqueId());
                PlayerProfile profile = onlineProfiles.get(target.getUniqueId());
                NameTagParts nameTag = renderNameTag(target, profile);
                session.replaceTeam(
                        teamName,
                        nameTag.prefix(),
                        nameTag.suffix(),
                        List.of(target.getName())
                );
                session.setTeamNameTagVisibility(teamName, Team.OptionStatus.ALWAYS);
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
        reload(displaySettings, refreshedSettings);
    }

    /**
     * Refreshes scoreboard and player presentation settings.
     *
     * @param refreshedDisplaySettings    new TAB and name-tag settings
     * @param refreshedScoreboardSettings new scoreboard settings
     */
    public void reload(
            DreamRPGSettings.DisplaySettings refreshedDisplaySettings,
            ScoreboardSettings refreshedScoreboardSettings
    ) {
        displaySettings = Objects.requireNonNull(refreshedDisplaySettings, "refreshedDisplaySettings");
        settings = Objects.requireNonNull(refreshedScoreboardSettings, "refreshedScoreboardSettings");
        nameTagOverflowWarnings.clear();
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
        memberPrefixes.remove(player.getUniqueId());
        permissionPrefixes.remove(player.getUniqueId());
        clearPlayerPresentation(player);
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
            clearPlayerPresentation(player);
        }
        tabHeaderFooters.clear();
        memberPrefixes.clear();
        permissionPrefixes.clear();
        closeScoreboardSessions();
    }

    private String renderTitle(Player viewer) {
        String title = settings.normal().titleAnimation().frameAt(animationTick).text();
        return templates.render(title, viewer, renderValues(viewer, title));
    }

    @EventHandler
    public void onTitleChanged(EquippedTitleChangedEvent event) {
        refreshAll();
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
        return renderValues(
                viewer,
                profiles.getProfile(viewer.getUniqueId()),
                template,
                viewer.getName()
        );
    }

    private Map<String, Object> renderValues(
            Player viewer,
            PlayerProfile profile,
            String template,
            String nameValue
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", viewer.getName());
        values.put("name", nameValue);
        values.put("health", formatNumber(viewer.getHealth()));
        AttributeInstance maxHealth = viewer.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) throw new IllegalStateException("Player has no generic max-health attribute");
        values.put("max_health", formatNumber(maxHealth.getValue()));
        values.put("career", profile.career().displayName());
        values.put("career_id", profile.careerId());
        values.put("prefix", profile.career().prefix());
        values.put("title", titles.equipped(viewer.getUniqueId()).map(TitleDefinition::displayName).orElse(""));
        values.put("member_prefix", memberPrefixes.getOrDefault(viewer.getUniqueId(), "&7[玩家]"));
        values.put("lp_prefix", permissionPrefixes.getOrDefault(viewer.getUniqueId(), ""));
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

    private String renderPlayerListName(Player player, PlayerProfile profile) {
        String template = nameFormat(displaySettings.tab().playerNameFormat(), player);
        String coloredName = profile.career().nameColor() + player.getName();
        return templates.render(
                template,
                player,
                renderValues(player, profile, template, coloredName)
        );
    }

    private NameTagParts renderNameTag(Player player, PlayerProfile profile) {
        String template = displaySettings.nameTagFormat().replace("%luckperms_prefix%", "{lp_prefix}");
        String markerName = profile.career().nameColor() + NAME_TAG_MARKER;
        String rendered = templates.render(
                template,
                player,
                renderValues(player, profile, template, markerName)
        );
        int markerIndex = rendered.indexOf(NAME_TAG_MARKER);
        if (markerIndex < 0 || rendered.indexOf(NAME_TAG_MARKER, markerIndex + NAME_TAG_MARKER.length()) >= 0) {
            throw new IllegalStateException(
                    "DreamRPG name-tag format must render exactly one {name} placeholder"
            );
        }
        return new NameTagParts(
                normalizeNameTagPart(player, "prefix", rendered.substring(0, markerIndex)),
                normalizeNameTagPart(
                        player,
                        "suffix",
                        rendered.substring(markerIndex + NAME_TAG_MARKER.length())
                )
        );
    }

    private String normalizeNameTagPart(Player player, String partName, String value) {
        if (value.length() <= 16) return value;
        String warningKey = player.getUniqueId() + ":" + partName + ":" + displaySettings.nameTagFormat();
        if (nameTagOverflowWarnings.add(warningKey)) {
            plugin.getLogger().warning(
                    "DreamRPG name-tag " + partName + " for " + player.getName()
                            + " exceeds Paper 1.12.2's 16-character scoreboard limit; "
                            + "the value will be truncated. Shorten display.name-tag-format "
                            + "or the membership prefix."
            );
        }
        String truncated = value.substring(0, 16);
        if (truncated.endsWith("§")) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated;
    }

    private void applyTabHeaderFooter(Player player, PlayerProfile profile) {
        DreamRPGSettings.TabSettings tab = displaySettings.tab();
        String header = normalizeTabText(renderTabLines(player, profile, tab.header()));
        String footer = normalizeTabText(renderTabLines(player, profile, tab.footer()));
        TabHeaderFooter next = new TabHeaderFooter(header, footer);
        if (next.equals(tabHeaderFooters.get(player.getUniqueId()))) return;
        if (!next.hasText() && !tabHeaderFooters.containsKey(player.getUniqueId())) {
            tabHeaderFooters.put(player.getUniqueId(), next);
            return;
        }
        player.setPlayerListHeaderFooter(
                toComponents(header),
                toComponents(footer)
        );
        tabHeaderFooters.put(player.getUniqueId(), next);
    }

    private String renderTabLines(Player player, PlayerProfile profile, List<String> lines) {
        return String.join(
                "\n",
                lines.stream()
                        .map(line -> templates.render(
                                line,
                                player,
                                renderValues(
                                        player,
                                        profile,
                                        line,
                                        profile.career().nameColor() + player.getName()
                                )
                        ))
                        .toList()
        );
    }

    private Map<String, Object> renderLoadingValues(Player viewer, String template) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player", viewer.getName());
        values.put("name", viewer.getName());
        values.put("member_prefix", memberPrefixes.getOrDefault(viewer.getUniqueId(), "&7[玩家]"));
        values.put("lp_prefix", permissionPrefixes.getOrDefault(viewer.getUniqueId(), ""));
        values.put("title", titles.equipped(viewer.getUniqueId()).map(TitleDefinition::displayName).orElse(""));
        if (template.contains("{time}")) {
            values.put(
                    "time",
                    ZonedDateTime.now(settings.normal().timeZone()).format(CLOCK_FORMATTER)
            );
        }
        if (template.contains("{location}")) {
            values.put("location", locations.resolve(viewer));
        }
        if (template.contains("{world}")) {
            values.put("world", locations.worldName(viewer.getWorld()));
        }
        return values;
    }

    private void applyPlayerName(Player player, PlayerProfile profile) {
        String displayName = profile.career().nameColor() + player.getName();
        if (!displayName.equals(player.getDisplayName())) player.setDisplayName(displayName);
        String playerListName = renderPlayerListName(player, profile);
        if (!playerListName.equals(player.getPlayerListName())) player.setPlayerListName(playerListName);
        applyTabHeaderFooter(player, profile);
    }

    private String nameFormat(String template, Player player) {
        return PlayerNameFormat.ordered(template, titles.equipped(player.getUniqueId()).isPresent(),
                !permissionPrefixes.getOrDefault(player.getUniqueId(), "").isEmpty());
    }

    private void clearPlayerPresentation(Player player) {
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());
        TabHeaderFooter applied = tabHeaderFooters.remove(player.getUniqueId());
        if (applied != null && applied.hasText()) {
            player.setPlayerListHeaderFooter(toComponents(""), toComponents(""));
        }
    }

    private void closeScoreboardSessions() {
        sessions.values().forEach(ScoreboardSession::close);
        sessions.clear();
    }

    private record NameTagParts(String prefix, String suffix) {
    }

    private record TabHeaderFooter(String header, String footer) {

        private boolean hasText() {
            return !header.isEmpty() || !footer.isEmpty();
        }
    }
}
