package cn.mythicland.dreamrpgcombat.integration.mythicmobs;

import cn.mythicland.dreamrpgcombat.api.CombatDamageSpec;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.combat.CombatDamageService;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.skills.damage.DamageMetadata;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.MetadataValue;

/**
 * Routes legacy MM's native and direct-health damage branches through one RPG hit pipeline.
 */
public final class MythicMobsDamageBridge implements Listener {

    private final CombatDamageService damageService;

    public MythicMobsDamageBridge(CombatDamageService damageService) {
        this.damageService = damageService;
    }

    @EventHandler(
            priority = EventPriority.LOWEST,
            ignoreCancelled = true
    )
    public void onSkillDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event instanceof EntityDamageByEntityEvent byEntity && damageService.handles(byEntity)) return;
        for (MetadataValue value : target.getMetadata("skill-damage")) {
            if (!value.getOwningPlugin().getName().equals("MythicMobs")) continue;
            if (!(value.value() instanceof DamageMetadata metadata)) continue;
            if (!metadata.getDamager().isUsingDamageSkill()) continue;
            if (!(BukkitAdapter.adapt(metadata.getDamager().getEntity()) instanceof LivingEntity source)) return;
            String element = metadata.getElement();
            CombatDamageType type = "magic".equalsIgnoreCase(element) || "magical".equalsIgnoreCase(element)
                    ? CombatDamageType.MAGIC : CombatDamageType.PHYSICAL;
            if ("true".equalsIgnoreCase(element)) type = CombatDamageType.TRUE;
            var velocity = target.getVelocity();
            event.setCancelled(true);
            if (metadata.getPreventsImmunity()) target.setNoDamageTicks(0);
            damageService.damage(source, target,
                    CombatDamageSpec.fixed(type, metadata.getAmount()));
            if (metadata.getPreventsKnockback()) target.setVelocity(velocity);
            if (metadata.getPreventsImmunity()) target.setNoDamageTicks(0);
            return;
        }
    }
}
