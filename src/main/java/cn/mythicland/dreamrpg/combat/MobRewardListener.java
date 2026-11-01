package cn.mythicland.dreamrpg.combat;

import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobDespawnEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Tracks actual health damage and distributes configured MM item drops once per death. */
@InjectComponent
@ListenerComponent
@LifecycleComponent
public final class MobRewardListener implements Listener, ManagedLifecycle {

    private static final String REWARD_OWNER = "dreamrpg-reward-owner";

    private final JavaPlugin plugin;
    private final PlayerSettingsService settings;
    private final PluginTaskScope tasks;
    private final MythicMobsAdapter mythicMobs;
    private final Map<UUID, Contribution> contributions = new HashMap<>();
    private BukkitTask cleanup;

    public MobRewardListener(JavaPlugin plugin, PlayerSettingsService settings, PluginTaskScope tasks) {
        this.plugin = plugin;
        this.settings = settings;
        this.tasks = tasks;
        this.mythicMobs = MythicMobsAdapter.detect();
    }

    @Override
    public void load() {
        cleanup = tasks.runTimer(200L, 200L,
                () -> contributions.values().removeIf(entry -> !entry.mob.isValid() || entry.mob.isDead()));
    }

    @Override
    public void unload() {
        tasks.cancel(cleanup);
        cleanup = null;
        contributions.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || mob instanceof Player) return;
        Player attacker = playerAttacker(event.getDamager());
        if (attacker == null || !mythicMobs.isMythicMob(mob)) return;
        double damage = Math.min(mob.getHealth(), event.getFinalDamage());
        if (!Double.isFinite(damage) || damage <= 0) return;
        contributions.computeIfAbsent(mob.getUniqueId(), ignored -> new Contribution(mob))
                .damage.merge(attacker.getUniqueId(), damage, Double::sum);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        Contribution contribution = contributions.remove(event.getEntity().getUniqueId());
        if (contribution == null || event.getDrops().isEmpty()) return;
        for (ItemStack drop : new ArrayList<>(event.getDrops())) {
            if (drop == null || drop.getType() == Material.AIR || drop.getAmount() <= 0) continue;
            Map<UUID, Integer> shares = MobLootShares.divide(drop.getAmount(), contribution.damage,
                    ThreadLocalRandom.current().nextDouble());
            for (var share : shares.entrySet()) {
                ItemStack reward = drop.clone();
                reward.setAmount(share.getValue());
                deliver(share.getKey(), reward, event.getEntity().getLocation());
            }
        }
        event.getDrops().clear();
    }

    private void deliver(UUID playerId, ItemStack reward, Location deathLocation) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            dropOwned(deathLocation, reward, playerId);
            return;
        }
        if (!settings.autoPicksUpRewards(playerId)) {
            dropOwned(player.getLocation(), reward, playerId);
            return;
        }
        for (ItemStack leftover : player.getInventory().addItem(reward).values()) {
            dropOwned(player.getLocation(), leftover, playerId);
        }
    }

    private void dropOwned(Location location, ItemStack reward, UUID playerId) {
        Item item = location.getWorld().dropItem(location, reward);
        item.setMetadata(REWARD_OWNER, new FixedMetadataValue(plugin, playerId));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        UUID owner = owner(event.getItem());
        if (owner != null && !owner.equals(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (owner(event.getItem()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (!Objects.equals(owner(event.getEntity()), owner(event.getTarget()))) event.setCancelled(true);
    }

    private UUID owner(Item item) {
        for (MetadataValue value : item.getMetadata(REWARD_OWNER)) {
            if (value.getOwningPlugin() == plugin) return (UUID) value.value();
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDespawn(MythicMobDespawnEvent event) {
        contributions.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) contributions.remove(entity.getUniqueId());
    }

    private static Player playerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (!(damager instanceof Projectile projectile)) return null;
        return projectile.getShooter() instanceof Player player ? player : null;
    }

    private static final class Contribution {
        private final LivingEntity mob;
        private final Map<UUID, Double> damage = new LinkedHashMap<>();

        private Contribution(LivingEntity mob) {
            this.mob = mob;
        }
    }
}
