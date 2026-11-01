package cn.mythicland.dreamrpg.compat;

import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;

/**
 * Clears random equipment from new and already-persisted MythicMobs entities.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class MythicMobEquipmentCompatibility implements Listener, ManagedLifecycle {

    private final PluginTaskScope tasks;

    /**
     * Creates the MythicMobs equipment compatibility lifecycle.
     *
     * @param tasks plugin-owned scheduler scope
     */
    public MythicMobEquipmentCompatibility(PluginTaskScope tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    @Override
    public void load() {
        tasks.runLater(1L, this::sanitizeLoadedEntities);
    }

    @Override
    public void reload() {
        tasks.runLater(1L, this::sanitizeLoadedEntities);
    }

    @Override
    public void unload() {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(MythicMobSpawnEvent event) {
        if (event.isCancelled()) return;
        if (!Boolean.TRUE.equals(event.getMobType().getPreventRandomEquipment())) return;
        if (event.getMobType().getEquipmentTable().hasDrops()) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        EntityEquipment equipment = target.getEquipment();
        if (equipment != null) equipment.clear();
        // Vanilla spawn finalization may equip the entity after the Mythic spawn event.
        tasks.runLater(1L, () -> sanitize(target));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        sanitizeChunk(chunk);
        tasks.runLater(1L, () -> sanitizeChunk(chunk));
    }

    private void sanitizeLoadedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) sanitize(entity);
        }
    }

    private void sanitizeChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        for (Entity entity : chunk.getEntities()) sanitize(entity);
    }

    private void sanitize(Entity entity) {
        if (!(entity instanceof LivingEntity target)) return;
        ActiveMob activeMob = MythicMobs.inst().getAPIHelper().getMythicMobInstance(target);
        if (activeMob == null) return;
        var mobType = activeMob.getType();
        if (!Boolean.TRUE.equals(mobType.getPreventRandomEquipment())) return;
        if (mobType.getEquipmentTable().hasDrops()) return;
        EntityEquipment equipment = target.getEquipment();
        if (equipment != null) equipment.clear();
    }
}
