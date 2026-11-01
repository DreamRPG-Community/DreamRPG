package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatDamageSpec;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns synchronous, single-use skill contexts, including nested damage calls.
 */
public final class CombatDamageService {

    private final CombatStatsService statsService;
    private final Deque<PendingHit> pending = new ArrayDeque<>();

    public CombatDamageService(CombatStatsService statsService) {
        this.statsService = Objects.requireNonNull(statsService, "statsService");
    }

    public void damage(LivingEntity source, LivingEntity target, CombatDamageSpec specification) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Combat damage requires the server thread");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(specification, "specification");
        CombatStatsSnapshot stats = statsService.getStats(source);
        double amount = specification.baseDamage()
                + CombatFormula.damage(stats, CombatDamageType.PHYSICAL, ThreadLocalRandom.current().nextDouble())
                * specification.physicalScaling()
                + CombatFormula.damage(stats, CombatDamageType.MAGIC, ThreadLocalRandom.current().nextDouble())
                * specification.magicScaling();
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("Computed skill damage must be finite");
        if (amount <= 0) return;
        pending.push(new PendingHit(source, target, new SkillHit(stats, specification, amount)));
        try {
            target.damage(amount, source);
        } finally {
            pending.pop();
        }
    }

    /**
     * Returns whether this event belongs to an explicit skill currently being applied.
     */
    public boolean handles(EntityDamageByEntityEvent event) {
        PendingHit hit = pending.peek();
        return hit != null && hit.source.equals(event.getDamager()) && hit.target.equals(event.getEntity());
    }

    /**
     * Claims the matching context once; unrelated or nested secondary events cannot reuse it.
     */
    public SkillHit claim(EntityDamageByEntityEvent event) {
        PendingHit hit = pending.peek();
        if (hit == null || hit.claimed || !hit.source.equals(event.getDamager())
                || !hit.target.equals(event.getEntity())) return null;
        hit.claimed = true;
        return hit.value;
    }

    /**
     * Immutable information consumed by the Bukkit hit listener.
     */
    public record SkillHit(CombatStatsSnapshot stats, CombatDamageSpec specification, double amount) {
    }

    private static final class PendingHit {
        private final LivingEntity source;
        private final LivingEntity target;
        private final SkillHit value;
        private boolean claimed;

        private PendingHit(LivingEntity source, LivingEntity target, SkillHit value) {
            this.source = source;
            this.target = target;
            this.value = value;
        }
    }
}
