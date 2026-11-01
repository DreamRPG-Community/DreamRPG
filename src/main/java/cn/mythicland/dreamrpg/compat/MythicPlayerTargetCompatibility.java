package cn.mythicland.dreamrpg.compat;

import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/** Clears disconnected MM player targets and rebinds stale references after player loading. */
@InjectComponent
@ListenerComponent
public final class MythicPlayerTargetCompatibility implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerReady(PlayerDataReadyEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player == null) return;
        for (var mob : List.copyOf(MythicMobs.inst().getMobManager().getActiveMobs())) {
            var entity = mob.getEntity();
            var pendingTarget = mob.getNewTarget();
            if (pendingTarget != null && pendingTarget.getUniqueId().equals(player.getUniqueId())
                    && pendingTarget.getBukkitEntity() != player) {
                // MM 4.x can retain newTarget after resetTarget has cleared the native target.
                mob.voidTargetChange();
            }
            var target = entity.getTarget();
            if (target == null) target = pendingTarget;
            if (target == null || !target.getUniqueId().equals(player.getUniqueId())) continue;
            if (target.getBukkitEntity() == player) continue;
            if (!(entity.getBukkitEntity() instanceof Creature creature)) continue;
            if (creature.getWorld() != player.getWorld()) continue;
            double range = creature.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).getValue();
            if (creature.getLocation().distanceSquared(player.getLocation()) > range * range) continue;
            mob.setTarget(BukkitAdapter.adapt(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (var mob : List.copyOf(MythicMobs.inst().getMobManager().getActiveMobs())) {
            var entity = mob.getEntity();
            var pendingTarget = mob.getNewTarget();
            if (pendingTarget != null && pendingTarget.getBukkitEntity() == player) mob.voidTargetChange();
            var target = entity.getTarget();
            if (target != null && target.getBukkitEntity() == player) {
                mob.resetTarget();
            }
            if (mob.hasThreatTable()) mob.getThreatTable().observeDeath(BukkitAdapter.adapt(player));
        }
    }
}
