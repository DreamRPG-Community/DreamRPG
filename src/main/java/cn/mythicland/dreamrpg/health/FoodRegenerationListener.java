package cn.mythicland.dreamrpg.health;

import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Scales food healing, doubles saturation regeneration cost and displays half-heart steps. */
@InjectComponent
@ListenerComponent
@LifecycleComponent
public final class FoodRegenerationListener implements Listener, ManagedLifecycle {

    private static final double VANILLA_MAXIMUM_HEALTH = 20.0;
    private static final float SATURATION_EXHAUSTION_PER_HEALTH = 6.0F;

    private final PluginTaskScope tasks;
    private final Map<EntityRegainHealthEvent, BukkitTask> pending = new HashMap<>();
    private EntityRegainHealthEvent deferredEvent;

    public FoodRegenerationListener(PluginTaskScope tasks) {
        this.tasks = tasks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodRegeneration(EntityRegainHealthEvent event) {
        if (event == deferredEvent) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (!(event.getEntity() instanceof Player player)) return;
        double maximumHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double amount = event.getAmount() * maximumHealth / VANILLA_MAXIMUM_HEALTH;
        if (event.isFastRegen()) {
            // FoodMetaData adds the original cost after this event; add one more copy without slowing healing.
            float extraExhaustion = (float) event.getAmount() * SATURATION_EXHAUSTION_PER_HEALTH;
            player.setExhaustion(Math.min(40.0F, player.getExhaustion() + extraExhaustion));
        }
        double halfHeart = player.isHealthScaled() ? maximumHealth / player.getHealthScale() : 1.0;
        double firstHalf = Math.min(amount, halfHeart);
        event.setAmount(firstHalf);
        double remainder = amount - firstHalf;
        if (remainder <= 0) return;
        World world = player.getWorld();
        // Native food healing occurs every 10 ticks (fast) or 80 ticks (slow).
        long delay = event.isFastRegen() ? 5L : 40L;
        pending.put(event, tasks.runLater(delay, () -> {
            if (pending.remove(event) == null || event.isCancelled()) return;
            if (!player.isOnline() || !player.isValid() || player.isDead() || player.getWorld() != world) return;
            applyDeferredHealing(player, remainder, event.isFastRegen());
        }));
    }

    private void applyDeferredHealing(Player player, double amount, boolean fastRegen) {
        EntityRegainHealthEvent event = new EntityRegainHealthEvent(
                player, amount, EntityRegainHealthEvent.RegainReason.SATIATED, fastRegen
        );
        EntityRegainHealthEvent previousEvent = deferredEvent;
        deferredEvent = event;
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            deferredEvent = previousEvent;
        }
        if (event.isCancelled() || !player.isOnline() || player.isDead()) return;
        double maximumHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(maximumHealth, player.getHealth() + event.getAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cancelPending(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        cancelPending(event.getPlayer());
    }

    private void cancelPending(Player player) {
        Iterator<Map.Entry<EntityRegainHealthEvent, BukkitTask>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().getEntity() != player) continue;
            tasks.cancel(entry.getValue());
            iterator.remove();
        }
    }

    @Override
    public void unload() {
        pending.values().forEach(tasks::cancel);
        pending.clear();
    }
}
