package cn.mythicland.dreamrpg.compat;

import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Disables vanilla zombie reinforcement behavior across the RPG server.
 */
@InjectComponent
@ListenerComponent
public final class ZombieReinforcementCompatibility implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Zombie zombie)) return;
        zombie.getAttribute(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS).setBaseValue(0);
    }
}
