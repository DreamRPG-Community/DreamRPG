package cn.mythicland.dreamrpg.compat;

import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Removes vanilla creature loot before MythicMobs adds configured drops and experience.
 */
@InjectComponent
@ListenerComponent
public final class VanillaMobDropCompatibility implements Listener {

    private final Set<UUID> mythicDeaths = new HashSet<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        if (MythicMobs.inst().getAPIHelper().isMythicMob(event.getEntity())) {
            mythicDeaths.add(event.getEntity().getUniqueId());
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathComplete(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        // MM may unregister its entity during death processing; retain the original ownership.
        if (mythicDeaths.remove(event.getEntity().getUniqueId())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}
