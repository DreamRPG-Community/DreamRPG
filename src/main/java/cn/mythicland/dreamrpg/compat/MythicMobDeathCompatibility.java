package cn.mythicland.dreamrpg.compat;

import cn.mythicland.dreamrpg.api.ExperienceApi;
import cn.mythicland.dreamrpg.api.ExperienceGrantRequest;
import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDespawnEvent;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Suppresses MythicMobs death particles and grants configured experience directly to killers. */
@LifecycleComponent
public final class MythicMobDeathCompatibility implements Listener, ManagedLifecycle {

    private static final long CORPSE_VISIBLE_TICKS = 12L;

    private final JavaPlugin plugin;
    private final ExperienceApi experience;
    private final PluginTaskScope tasks;
    private final Map<Integer, String> mythicEntityIds = new HashMap<>();
    private final Map<UUID, Long> pendingExperience = new HashMap<>();
    private MythicMobsAdapter mythicMobs;
    private ProtocolManager protocolManager;

    public MythicMobDeathCompatibility(JavaPlugin plugin, ExperienceApi experience, PluginTaskScope tasks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    @Override
    public void load() {
        mythicMobs = MythicMobsAdapter.detect();
        if (!mythicMobs.isAvailable()) throw new IllegalStateException("MythicMobs API is unavailable");
        protocolManager = ProtocolLibrary.getProtocolManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            world.getEntities().stream()
                    .filter(mythicMobs::isMythicMob)
                    .forEach(this::track);
        }
        plugin.getLogger().info("MythicMobs death effects and direct experience enabled.");
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(this);
        mythicEntityIds.clear();
        pendingExperience.clear();
        mythicMobs = null;
        protocolManager = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        mythicEntityIds.put(event.getEntity().getEntityId(), event.getMobType().getInternalName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDespawn(MythicMobDespawnEvent event) {
        mythicEntityIds.remove(event.getEntity().getEntityId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (mythicMobs.isMythicMob(entity)) track(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!mythicEntityIds.containsKey(entity.getEntityId()) && !mythicMobs.isMythicMob(entity)) return;
        String mobId = mythicEntityIds.getOrDefault(entity.getEntityId(), "mythicmobs");

        int droppedExperience = event.getDroppedExp();
        event.setDroppedExp(0);
        // Let every death listener settle loot before removing only the client-side corpse.
        tasks.runLater(CORPSE_VISIBLE_TICKS, () -> hideDeadEntity(entity));
        Player killer = entity.getKiller();
        if (killer == null || droppedExperience <= 0) return;
        if (experience.isReady(killer.getUniqueId())) {
            grant(killer.getUniqueId(), droppedExperience, mobId);
        } else {
            pendingExperience.merge(killer.getUniqueId(), (long) droppedExperience, Long::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        Long pending = pendingExperience.remove(event.uniqueId());
        if (pending == null || pending <= 0) return;
        grant(event.uniqueId(), pending, "pending_mythicmobs");
    }

    private void grant(UUID playerId, long amount, String mobId) {
        experience.grant(new ExperienceGrantRequest(playerId, amount, "mob", mobId));
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1F, 1.0F);
        }
    }

    private void track(Entity entity) {
        mythicEntityIds.put(entity.getEntityId(), "mythicmobs");
    }

    private void hideDeadEntity(LivingEntity entity) {
        mythicEntityIds.remove(entity.getEntityId());
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntegerArrays().write(0, new int[]{entity.getEntityId()});
        for (Player viewer : entity.getWorld().getPlayers()) {
            protocolManager.sendServerPacket(viewer, destroy, false);
        }
    }
}
