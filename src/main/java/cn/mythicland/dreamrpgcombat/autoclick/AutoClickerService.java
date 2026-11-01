package cn.mythicland.dreamrpgcombat.autoclick;

import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.entity.v1_12_R1.LegacyMeleeAttack;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Owns the double-sneak toggle and MM-only automatic normal attacks.
 */
public final class AutoClickerService implements Listener, AutoCloseable {

    private static final long DOUBLE_SNEAK_NANOS = 500_000_000L;
    private static final int CLICKS_PER_SECOND = 16;
    private static final int TICKS_PER_SECOND = 20;
    private static final double REACH = 3.0D;
    private final MythicMobsAdapter mythicMobs;
    private final LongSupplier nanoTime;
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Set<UUID> attacking = new HashSet<>();

    public AutoClickerService(MythicMobsAdapter mythicMobs) {
        this(mythicMobs, System::nanoTime);
    }

    AutoClickerService(MythicMobsAdapter mythicMobs, LongSupplier nanoTime) {
        this.mythicMobs = mythicMobs;
        this.nanoTime = nanoTime;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!active(player) || !mythicMobs.isAvailable()) {
            clear(id);
            return;
        }
        long now = nanoTime.getAsLong();
        Long previous = lastSneak.put(id, now);
        if (previous == null || now - previous > DOUBLE_SNEAK_NANOS) return;
        lastSneak.remove(id);
        if (progress.remove(id) != null) {
            player.sendMessage("§c自动点击已关闭。");
        } else {
            progress.put(id, 0);
            player.sendMessage("§a自动点击已开启。");
        }
    }

    /**
     * Advances the enabled players once per server tick, on the server thread.
     */
    public void tick() {
        // Native attack callbacks can clear sessions while this tick is in progress.
        for (UUID id : progress.keySet().toArray(UUID[]::new)) {
            Integer previous = progress.get(id);
            if (previous == null) continue;
            Player player = Bukkit.getPlayer(id);
            if (!active(player)) {
                clear(id);
                continue;
            }
            if (!weapon(player.getInventory().getItemInMainHand())) {
                progress.put(id, 0);
                continue;
            }
            int accumulated = previous + CLICKS_PER_SECOND;
            progress.put(id, accumulated % TICKS_PER_SECOND);
            if (accumulated < TICKS_PER_SECOND) continue;
            LegacyMeleeAttack.swing(player);
            LivingEntity target = LegacyMeleeAttack.target(player, REACH, this::allowed);
            if (target == null || !allowed(target)) continue;
            attacking.add(id);
            try {
                LegacyMeleeAttack.attack(player, target);
            } finally {
                attacking.remove(id);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAutomaticDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !attacking.contains(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity target) || !allowed(target)) event.setCancelled(true);
    }

    private boolean allowed(LivingEntity target) {
        return !(target instanceof Player) && target.isValid() && !target.isDead() && mythicMobs.isMythicMob(target);
    }

    private static boolean active(Player player) {
        return player != null && player.isOnline() && player.isValid() && !player.isDead()
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static boolean weapon(ItemStack item) {
        if (item == null) return false;
        String material = item.getType().name();
        return material.endsWith("_SWORD") || material.endsWith("_AXE") || material.endsWith("_SPADE");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getEntity().getUniqueId());
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.SPECTATOR) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean wasEnabled = progress.containsKey(id);
        clear(id);
        attacking.remove(id);
        if (wasEnabled) player.sendMessage("§c自动点击已关闭。");
    }

    private void clear(UUID id) {
        progress.remove(id);
        lastSneak.remove(id);
    }

    @Override
    public void close() {
        progress.clear();
        lastSneak.clear();
        attacking.clear();
    }
}
