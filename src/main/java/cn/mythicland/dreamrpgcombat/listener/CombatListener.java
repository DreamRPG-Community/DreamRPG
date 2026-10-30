package cn.mythicland.dreamrpgcombat.listener;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.actionbar.CombatHealthBarService;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.combat.CombatFormula;
import cn.mythicland.dreamrpgcombat.combat.CombatHealthFormula;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("deprecation")
public final class CombatListener implements Listener {

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("5d3a95aa-0bd7-4cc6-9c7c-24e39e0f6d68");
    private static final String HEALTH_MODIFIER_NAME = "Combat Lore Health";

    private final DreamRPGPlugin plugin;
    private final PluginTaskScope tasks;
    private final CombatStatsService statsService;
    private final CombatHealthBarService healthBarService;
    private final Set<UUID> queuedRefreshes = new HashSet<>();

    public CombatListener(
            DreamRPGPlugin plugin,
            PluginTaskScope tasks,
            CombatStatsService statsService,
            CombatHealthBarService healthBarService
    ) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.statsService = statsService;
        this.healthBarService = healthBarService;
    }

    private static void applyDefense(EntityDamageByEntityEvent event, double defensePercent) {
        try {
            if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
                double vanillaArmorModifier = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
                double damageWithoutArmor = Math.max(0.0D, event.getFinalDamage() - vanillaArmorModifier);
                double customFinalDamage = CombatFormula.applyDefense(damageWithoutArmor, defensePercent);
                event.setDamage(
                        EntityDamageEvent.DamageModifier.ARMOR,
                        customFinalDamage - damageWithoutArmor
                );
                return;
            }
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            // Some damage causes do not expose an armor modifier; the base fallback below is enough.
        }
        event.setDamage(CombatFormula.applyDefense(event.getFinalDamage(), defensePercent));
    }

    private static void playCriticalEffects(LivingEntity target) {
        Location location = target.getLocation().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        target.getWorld().spawnParticle(Particle.CRIT, location, 10, 0.3D, 0.4D, 0.3D, 0.1D);
        target.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
    }

    private static Player findPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (!(damager instanceof Projectile projectile)) return null;
        return projectile.getShooter() instanceof Player player ? player : null;
    }

    private static AttributeModifier findHealthModifier(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (HEALTH_MODIFIER_ID.equals(modifier.getUniqueId())) return modifier;
        }
        return null;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        Player attacker = findPlayerAttacker(event.getDamager());
        boolean critical = false;
        if (attacker != null) {
            CombatStatsSnapshot attackerStats = statsService.getStats(attacker);
            double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            if (attackerStats.hasDamage()) {
                baseDamage = CombatFormula.damage(attackerStats, ThreadLocalRandom.current().nextDouble());
            }
            critical = CombatFormula.critical(
                    attackerStats,
                    ThreadLocalRandom.current().nextDouble(0.0D, 100.0D)
            );
            if (attackerStats.hasDamage() || critical) {
                double multiplier = CombatFormula.criticalMultiplier(attackerStats, critical);
                event.setDamage(EntityDamageEvent.DamageModifier.BASE, baseDamage * multiplier);
            }
        }

        CombatStatsSnapshot targetStats = statsService.getStats(target);
        if (targetStats.hasDefense()) applyDefense(event, targetStats.defensePercent());
        if (attacker != null) healthBarService.recordHit(attacker, target, event.getFinalDamage());
        if (critical) playCriticalEffects(target);
        if (critical && plugin.combatSettings().feedbackEnabled()) {
            String message = plugin.combatSettings().criticalHitMessage(event.getFinalDamage());
            if (!message.isBlank()) attacker.sendMessage(message);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshPlayer(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshPlayer(player);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        refreshPlayer(event.getPlayer());
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) refreshPlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        queuedRefreshes.remove(event.getPlayer().getUniqueId());
        healthBarService.clear(event.getPlayer());
    }

    public void refreshOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) refreshPlayer(player);
    }

    public void regenerateOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            CombatStatsSnapshot stats = statsService.getStats(player);
            if (stats.healthRegenPercent() <= 0.0D) continue;
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attribute == null) continue;
            double maximumHealth = attribute.getValue();
            double amount = maximumHealth * stats.healthRegenPercent() / 100.0D;
            double currentHealth = player.getHealth();
            if (amount <= 0.0D || currentHealth >= maximumHealth) continue;
            double restored = Math.min(amount, maximumHealth - currentHealth);
            if (restored <= 0.0D) continue;
            player.setHealth(currentHealth + restored);
            if (plugin.combatSettings().feedbackEnabled()) {
                String message = plugin.combatSettings().healthRegenMessage(restored);
                if (!message.isBlank()) player.sendMessage(message);
            }
        }
    }

    public void removeHealthModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) return;
        AttributeModifier modifier = findHealthModifier(attribute);
        if (modifier != null) attribute.removeModifier(modifier);
        if (player.getHealth() > attribute.getValue()) player.setHealth(attribute.getValue());
    }

    /**
     * Recalculates the player's active DreamRPG combat health and level-gated attributes on the next
     * server tick.
     *
     * @param player player to refresh
     */
    public void refreshPlayer(Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!queuedRefreshes.add(uniqueId)) return;
        tasks.runLater(1L, () -> {
            queuedRefreshes.remove(uniqueId);
            if (!player.isOnline()) return;
            applyHealthModifier(player);
        });
    }

    private void applyHealthModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) return;
        double previousMaximumHealth = attribute.getValue();
        double currentHealth = player.getHealth();
        AttributeModifier previous = findHealthModifier(attribute);
        if (previous != null) attribute.removeModifier(previous);

        CombatStatsSnapshot stats = statsService.getStats(player);
        double baseMaximumHealth = attribute.getValue();
        double desiredMaximumHealth = CombatHealthFormula.maximumHealth(baseMaximumHealth, stats.health());
        double modifierAmount = desiredMaximumHealth - baseMaximumHealth;
        if (Math.abs(modifierAmount) > 1.0E-9D) {
            attribute.addModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID,
                    HEALTH_MODIFIER_NAME,
                    modifierAmount,
                    AttributeModifier.Operation.ADD_NUMBER
            ));
        }
        double newMaximumHealth = attribute.getValue();
        double synchronizedHealth = CombatHealthFormula.synchronizedCurrentHealth(
                currentHealth,
                previousMaximumHealth,
                newMaximumHealth
        );
        if (Double.compare(currentHealth, synchronizedHealth) != 0) {
            player.setHealth(synchronizedHealth);
        }
    }

}
