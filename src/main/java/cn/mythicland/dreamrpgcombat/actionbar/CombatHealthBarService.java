package cn.mythicland.dreamrpgcombat.actionbar;

import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.dreamlib.text.LegacyActionBar;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * Owns temporary target health bars shown after player attacks.
 */
public final class CombatHealthBarService {

    private static final long DISPLAY_MILLIS = 2_000L;
    private final LongSupplier currentTimeMillis;
    private final MythicMobsAdapter mythicMobsAdapter;
    private final Map<UUID, CombatHealthBarStatus> statusByAttacker = new HashMap<>();

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
        if (attacker == null || !attacker.isOnline()) return;
        if (target == null || target instanceof Player) return;

        double maxHealth = maxHealth(target);
        double currentHealth = Math.clamp(target.getHealth(), 0.0D, maxHealth);
        // Paper 1.12.2 exposes absorption only on Player, while this bar intentionally targets mobs.
        double absorption = 0.0D;
        double totalDamage = nonNegativeFinite(damage);
        double healthDamage = Math.clamp(totalDamage - absorption, 0.0D, currentHealth);
        double remainingAbsorption = Math.max(0.0D, absorption - totalDamage);
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(target.getUniqueId())
                .targetDisplayName(targetDisplayName(target))
                .remainingHealth(Math.max(0.0D, currentHealth - healthDamage))
                .remainingAbsorption(remainingAbsorption)
                .maxHealth(maxHealth)
                .healthDamage(healthDamage)
                .expiresAtMillis(this.currentTimeMillis.getAsLong() + DISPLAY_MILLIS)
                .build();
        statusByAttacker.put(attacker.getUniqueId(), status);
        refresh(attacker);
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
        CombatHealthBarStatus status = statusByAttacker.get(attacker.getUniqueId());
        if (status == null) return null;
        if (status.expired(currentTimeMillis.getAsLong())) {
            statusByAttacker.remove(attacker.getUniqueId());
            return null;
        }
        return status;
    }

    private void refresh(Player attacker) {
        CombatHealthBarStatus status = currentStatus(attacker);
        if (status == null) {
            LegacyActionBar.send(attacker, "");
            return;
        }
        LegacyActionBar.send(attacker, CombatHealthBarFormatter.render(status));
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
