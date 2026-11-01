package cn.mythicland.dreamrpg.combat;

import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Suppresses native MM knockback when the player disables it. */
@InjectComponent
@ListenerComponent
public final class MobKnockbackListener implements Listener {

    private final MythicMobsAdapter mythicMobs;
    private final PlayerSettingsService settings;

    public MobKnockbackListener(PlayerSettingsService settings) {
        this.settings = settings;
        this.mythicMobs = MythicMobsAdapter.detect();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMythicMobKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (settings.allowsMobKnockback(player.getUniqueId())) return;
        LivingEntity attacker = findAttacker(event.getHitBy());
        if (attacker == null || !mythicMobs.isMythicMob(attacker)) return;
        event.setCancelled(true);
    }

    private static LivingEntity findAttacker(Entity damager) {
        if (damager instanceof LivingEntity attacker) return attacker;
        if (!(damager instanceof Projectile projectile)) return null;
        return projectile.getShooter() instanceof LivingEntity shooter ? shooter : null;
    }
}
