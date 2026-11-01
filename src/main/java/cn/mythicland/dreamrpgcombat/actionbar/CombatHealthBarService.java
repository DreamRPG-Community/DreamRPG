package cn.mythicland.dreamrpgcombat.actionbar;

import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.framework.bukkit.entity.EntityAbsorption;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.text.v1_12_R1.LegacyActionBar;

import java.util.*;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Owns temporary target health bars shown after player attacks.
 */
public final class CombatHealthBarService {

    private static final long DISPLAY_MILLIS = 2_000L;
    private final LongSupplier currentTimeMillis;
    private final MythicMobsAdapter mythicMobsAdapter;
    private final Map<UUID, TrackedBar> statusByAttacker = new HashMap<>();

    public CombatHealthBarService() {
        this(System::currentTimeMillis, MythicMobsAdapter.detect());
    }

    public CombatHealthBarService(MythicMobsAdapter mythicMobsAdapter) {
        this(System::currentTimeMillis, mythicMobsAdapter);
    }

    CombatHealthBarService(
            LongSupplier currentTimeMillis,
            MythicMobsAdapter mythicMobsAdapter
    ) {
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
        this.mythicMobsAdapter = Objects.requireNonNull(mythicMobsAdapter, "mythicMobsAdapter");
    }

    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity target) {
        AttributeInstance attribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) return Math.max(1.0D, attribute.getValue());
        return Math.max(1.0D, target.getMaxHealth());
    }

    private static double nonNegativeFinite(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    /**
     * Records the post-formula damage snapshot and immediately displays it to the attacker.
     *
     * @param attacker player who caused the damage
     * @param target   non-player living target
     * @param damage   final event damage after DreamRPG combat's adjustments
     */
    public void recordHit(Player attacker, LivingEntity target, double damage) {
        recordHit(attacker, target, damage, 0, CombatDamageType.PHYSICAL);
    }

    /**
     * Records one hit before Bukkit applies it, on the server thread.
     *
     * @param attacker       player who caused the hit
     * @param target         non-player living target
     * @param damage         final event damage to health, after absorption
     * @param absorbedDamage damage consumed by absorption in the same event
     * @param type           resolved RPG damage channel
     */
    public void recordHit(
            Player attacker,
            LivingEntity target,
            double damage,
            double absorbedDamage,
            CombatDamageType type
    ) {
        if (attacker == null || !attacker.isOnline()) return;
        if (target == null || target instanceof Player) return;

        this.recordHealthStatus(
                attacker,
                target,
                this.targetDisplayName(target),
                damage,
                absorbedDamage,
                type
        );
    }

    /**
     * Records a MythicMob hit for the damaged player's own action bar.
     *
     * @param victim         player who received the hit
     * @param damage         final event damage to health, after absorption
     * @param absorbedDamage damage consumed by absorption in the same event
     * @param type           resolved RPG damage channel
     */
    public void recordIncomingHit(
            Player victim,
            double damage,
            double absorbedDamage,
            CombatDamageType type
    ) {
        if (victim == null || !victim.isOnline()) return;
        this.recordHealthStatus(victim, victim, "你", damage, absorbedDamage, type);
    }

    private void recordHealthStatus(
            Player viewer,
            LivingEntity target,
            String displayName,
            double damage,
            double absorbedDamage,
            CombatDamageType type
    ) {

        double maxHealth = maxHealth(target);
        double currentHealth = Math.clamp(target.getHealth(), 0.0D, maxHealth);
        double absorption = nonNegativeFinite(EntityAbsorption.get(target));
        double healthDamage = Math.clamp(nonNegativeFinite(damage), 0.0D, currentHealth);
        double absorbed = Math.min(absorption, nonNegativeFinite(absorbedDamage));
        CombatHealthBarStatus previous = currentStatus(viewer);
        double maxAbsorption = absorption;
        if (previous != null && previous.targetUniqueId().equals(target.getUniqueId())) {
            maxAbsorption = Math.max(maxAbsorption, previous.maxAbsorption());
        }
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(target.getUniqueId())
                .targetDisplayName(displayName)
                .remainingHealth(Math.max(0.0D, currentHealth - healthDamage))
                .remainingAbsorption(absorption - absorbed)
                .maxAbsorption(maxAbsorption)
                .finalDamage(nonNegativeFinite(damage) + absorbed)
                .damageType(type)
                .maxHealth(maxHealth)
                .healthDamage(healthDamage)
                .expiresAtMillis(this.currentTimeMillis.getAsLong() + DISPLAY_MILLIS)
                .build();
        statusByAttacker.put(viewer.getUniqueId(), new TrackedBar(target, status));
        render(viewer);
    }

    /**
     * Refreshes all active bars. The plugin schedules this every ten server ticks.
     */
    public void refreshOnlinePlayers() {
        Set<UUID> attackers = new HashSet<>(statusByAttacker.keySet());
        for (UUID attackerUniqueId : attackers) {
            Player attacker = Bukkit.getPlayer(attackerUniqueId);
            if (attacker == null || !attacker.isOnline()) {
                statusByAttacker.remove(attackerUniqueId);
                continue;
            }
            refresh(attacker);
        }
    }

    /**
     * Clears one attacker's status and action bar.
     *
     * @param attacker player whose status should be cleared
     */
    public void clear(Player attacker) {
        if (attacker == null) return;
        statusByAttacker.remove(attacker.getUniqueId());
        LegacyActionBar.send(attacker, "");
    }

    /**
     * Clears all statuses during plugin shutdown.
     */
    public void clearAll() {
        Set<UUID> attackers = new HashSet<>(statusByAttacker.keySet());
        statusByAttacker.clear();
        for (UUID attackerUniqueId : attackers) {
            Player attacker = Bukkit.getPlayer(attackerUniqueId);
            LegacyActionBar.send(attacker, "");
        }
    }

    CombatHealthBarStatus currentStatus(Player attacker) {
        if (attacker == null) return null;
        TrackedBar tracked = statusByAttacker.get(attacker.getUniqueId());
        if (tracked == null) return null;
        CombatHealthBarStatus status = tracked.status();
        if (status.expired(currentTimeMillis.getAsLong())) {
            statusByAttacker.remove(attacker.getUniqueId());
            return null;
        }
        return status;
    }

    /**
     * Refreshes a visible bar from its target's committed health, including later regeneration.
     * Call on the server thread while the combat component is active.
     *
     * @param attacker online viewer whose temporary bar should be synchronized
     */
    public void refresh(Player attacker) {
        CombatHealthBarStatus status = currentStatus(attacker);
        if (status == null) {
            render(attacker);
            return;
        }
        LivingEntity target = statusByAttacker.get(attacker.getUniqueId()).target();
        if (!target.isValid() && !target.isDead()) {
            clear(attacker);
            return;
        }
        double maximum = maxHealth(target);
        double health = Math.clamp(target.getHealth(), 0.0D, maximum);
        double absorption = nonNegativeFinite(EntityAbsorption.get(target));
        statusByAttacker.put(attacker.getUniqueId(), new TrackedBar(target,
                status.withVitals(health, maximum, absorption)));
        render(attacker);
    }

    private void render(Player attacker) {
        CombatHealthBarStatus status = currentStatus(attacker);
        if (status == null) {
            LegacyActionBar.send(attacker, "");
            return;
        }
        LegacyActionBar.send(attacker, CombatHealthBarFormatter.render(status));
    }

    private record TrackedBar(LivingEntity target, CombatHealthBarStatus status) {
    }

    private String targetDisplayName(LivingEntity target) {
        String mythicName = mythicMobsAdapter.displayName(target).orElse(null);
        if (mythicName != null) return mythicName;
        String customName = target.getCustomName();
        if (customName != null && !customName.isBlank()) return customName;
        String name = target.getName();
        if (name != null && !name.isBlank()) return name;
        return target.getType().name();
    }
}
