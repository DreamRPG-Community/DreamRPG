package cn.mythicland.dreamrpgcombat.integration.dreamrpg;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.combat.CombatFormula;
import cn.mythicland.dreamrpgcombat.listener.CombatListener;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.dreamrpg.api.*;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpg.event.RpgLevelUpEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Optional DreamRPG bridge for level-gated DreamRPG combat attributes and item experience bonuses.
 */
public final class DreamRPGExperienceIntegration implements Listener, AutoCloseable {

    private static final String MODIFIER_ID = "dreamrpg-combat-item-experience";

    private final DreamRPGPlugin plugin;
    private final ExperienceApi experience;
    private final HealthApi health;
    private Registration modifierRegistration;
    private CombatListener combatListener;

    private DreamRPGExperienceIntegration(
            DreamRPGPlugin plugin,
            ExperienceApi experience,
            HealthApi health
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.health = health;
    }

    /**
     * Finds DreamRPG's public service without making it a hard runtime dependency.
     *
     * @param plugin owning DreamRPG combat plugin
     * @return integration instance, or {@code null} when the service is unavailable
     */
    public static DreamRPGExperienceIntegration detect(DreamRPGPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        RegisteredServiceProvider<ExperienceApi> registration = plugin.getServer()
                .getServicesManager()
                .getRegistration(ExperienceApi.class);
        if (registration == null || registration.getProvider() == null) {
            plugin.getLogger().warning("DreamRPG is enabled but ExperienceApi is unavailable; "
                    + "level restrictions and item experience bonuses are disabled.");
            return null;
        }
        RegisteredServiceProvider<HealthApi> healthRegistration = plugin.getServer()
                .getServicesManager()
                .getRegistration(HealthApi.class);
        HealthApi health = healthRegistration == null ? null : healthRegistration.getProvider();
        if (health == null) {
            plugin.getLogger().warning("DreamRPG HealthApi is unavailable; DreamRPG health will not be shown in stats.");
        }
        return new DreamRPGExperienceIntegration(plugin, registration.getProvider(), health);
    }

    /**
     * Returns the ready DreamRPG level for one player.
     *
     * @param uniqueId player UUID
     * @return RPG level, or empty while DreamRPG data is not ready
     */
    public OptionalLong rpgLevel(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (!experience.isReady(uniqueId)) return OptionalLong.empty();
        ExperienceSnapshot snapshot = experience.snapshot(uniqueId);
        if (!snapshot.ready()) return OptionalLong.empty();
        return OptionalLong.of(snapshot.level());
    }

    /**
     * Returns the ready DreamRPG health progression for one player.
     *
     * @param uniqueId player UUID
     * @return health progression, or empty when the bridge is unavailable or not ready
     */
    public Optional<HealthSnapshot> rpgHealth(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        if (health == null) return Optional.empty();
        return health.snapshot(uniqueId).filter(HealthSnapshot::enabled);
    }

    /**
     * Registers the item modifier and DreamRPG lifecycle event listeners.
     *
     * @param listener DreamRPG combat listener to refresh after RPG state changes
     * @param stats    DreamRPG combat stats service used by the modifier
     */
    public void enable(CombatListener listener, CombatStatsService stats) {
        if (modifierRegistration != null) throw new IllegalStateException("DreamRPG integration is enabled");
        combatListener = Objects.requireNonNull(listener, "listener");
        CombatStatsService service = Objects.requireNonNull(stats, "stats");
        modifierRegistration = experience.registerModifier(new ItemExperienceModifier(service));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("DreamRPG experience integration enabled.");
    }

    @EventHandler
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        refresh(event.uniqueId());
    }

    @EventHandler
    public void onRpgLevelUp(RpgLevelUpEvent event) {
        refresh(event.uniqueId());
    }

    /**
     * Unregisters the external modifier and event listeners.
     */
    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        if (modifierRegistration != null) modifierRegistration.close();
        modifierRegistration = null;
        combatListener = null;
    }

    private void refresh(UUID uniqueId) {
        CombatListener listener = combatListener;
        if (listener == null) return;
        Player player = Bukkit.getPlayer(uniqueId);
        if (player != null && player.isOnline()) listener.refreshPlayer(player);
    }

    private record ItemExperienceModifier(CombatStatsService statsService) implements ExperienceModifier {

        @Override
        public String id() {
            return MODIFIER_ID;
        }

        @Override
        public BigDecimal multiplier(ExperienceModifierContext context) {
            Player player = Bukkit.getPlayer(context.uniqueId());
            if (player == null || !player.isOnline()) return BigDecimal.ONE;
            double bonus = statsService.getStats(player).experienceBonusPercent();
            return BigDecimal.valueOf(CombatFormula.experienceMultiplier(bonus));
        }
    }
}
