package cn.mythicland.dreamrpg.compat;

import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * Prevents the configured RPG chicken from producing vanilla eggs or offspring.
 */
@InjectComponent
@ListenerComponent
public final class MythicChickenCompatibility implements Listener {

    private static final String WHITE_CHICKEN_ID = "main_village_white_chicken";
    private static final double EGG_DROP_SEARCH_RADIUS = 1.0;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() != Material.EGG) return;
        if (hasWhiteChickenNear(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (isWhiteChicken(event.getMother()) || isWhiteChicken(event.getFather())) event.setCancelled(true);
    }

    private boolean hasWhiteChickenNear(Entity item) {
        return item.getNearbyEntities(
                        EGG_DROP_SEARCH_RADIUS,
                        EGG_DROP_SEARCH_RADIUS,
                        EGG_DROP_SEARCH_RADIUS
                ).stream()
                .filter(Chicken.class::isInstance)
                .anyMatch(this::isWhiteChicken);
    }

    private boolean isWhiteChicken(Entity entity) {
        ActiveMob activeMob = MythicMobs.inst().getAPIHelper().getMythicMobInstance(entity);
        return activeMob != null && WHITE_CHICKEN_ID.equals(activeMob.getType().getInternalName());
    }
}
