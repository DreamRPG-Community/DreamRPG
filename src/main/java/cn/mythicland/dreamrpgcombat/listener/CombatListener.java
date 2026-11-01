package cn.mythicland.dreamrpgcombat.listener;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.actionbar.CombatHealthBarService;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.combat.CombatFormula;
import cn.mythicland.dreamrpgcombat.combat.CombatDamageService;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.dreamrpgcombat.combat.CombatHealthFormula;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.framework.bukkit.entity.DamageModifierSupport;
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
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("deprecation")
public final class CombatListener implements Listener {

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("5d3a95aa-0bd7-4cc6-9c7c-24e39e0f6d68");
    private static final String HEALTH_MODIFIER_NAME = "Combat Lore Health";

    private static final UUID MOVEMENT_MODIFIER_ID = UUID.fromString("5d3a95aa-0bd7-4cc6-9c7c-24e39e0f6d69");
    private final Map<UUID, ShotStats> shots = new HashMap<>();
    private final Map<EntityDamageByEntityEvent, HitOutcome> outcomes = new IdentityHashMap<>();
    private final CombatDamageService damageService;
    private final MythicMobsAdapter mythicMobs;
    private final DreamRPGPlugin plugin;
    private final PluginTaskScope tasks;
    private final CombatStatsService statsService;
    private final CombatHealthBarService healthBarService;
    private final Set<UUID> queuedRefreshes = new HashSet<>();
    private final Map<UUID, Long> levelWarnings = new HashMap<>();

    public CombatListener(
            DreamRPGPlugin plugin,
            PluginTaskScope tasks,
            CombatStatsService statsService,
            CombatHealthBarService healthBarService,
            CombatDamageService damageService,
            MythicMobsAdapter mythicMobs
    ) {
        this.damageService = damageService;
        this.mythicMobs = mythicMobs;
        this.plugin = plugin;
        this.tasks = tasks;
        this.statsService = statsService;
        this.healthBarService = healthBarService;
    }

    private void applyDefense(
            EntityDamageByEntityEvent event,
            CombatStatsSnapshot attacker,
            CombatStatsSnapshot target,
            CombatDamageType type
    ) {
        double constant = plugin.combatSettings().defenseConstant();
        Map<EntityDamageEvent.DamageModifier, DoubleUnaryOperator> reductions = equipmentReductions(event);
        if (type == CombatDamageType.TRUE) {
            if (event.isApplicable(EntityDamageEvent.DamageModifier.RESISTANCE)) {
                reductions.put(EntityDamageEvent.DamageModifier.RESISTANCE, incoming -> incoming);
            }
            DamageModifierSupport.replaceReductions(event, reductions);
            return;
        }
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) {
            reductions.put(EntityDamageEvent.DamageModifier.ARMOR,
                    incoming -> CombatFormula.applyDefense(incoming, attacker, target, type, constant));
            DamageModifierSupport.replaceReductions(event, reductions);
        } else {
            event.setDamage(CombatFormula.applyDefense(event.getFinalDamage(), attacker, target, type, constant));
        }
    }

    private static Map<EntityDamageEvent.DamageModifier, DoubleUnaryOperator> equipmentReductions(EntityDamageEvent event) {
        Map<EntityDamageEvent.DamageModifier, DoubleUnaryOperator> reductions =
                new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        for (EntityDamageEvent.DamageModifier modifier : new EntityDamageEvent.DamageModifier[]{
                EntityDamageEvent.DamageModifier.ARMOR, EntityDamageEvent.DamageModifier.MAGIC,
                EntityDamageEvent.DamageModifier.BLOCKING, EntityDamageEvent.DamageModifier.HARD_HAT}) {
            if (event.isApplicable(modifier)) reductions.put(modifier, incoming -> incoming);
        }
        return reductions;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof LivingEntity)) return;
        Map<EntityDamageEvent.DamageModifier, DoubleUnaryOperator> reductions = equipmentReductions(event);
        if (!reductions.isEmpty()) DamageModifierSupport.replaceReductions(event, reductions);
    }

    private static void playCriticalEffects(LivingEntity target) {
        Location location = target.getLocation().add(0.0D, target.getHeight() * 0.5D, 0.0D);
        target.getWorld().spawnParticle(Particle.CRIT, location, 10, 0.3D, 0.4D, 0.3D, 0.1D);
        target.getWorld().playSound(location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.0F);
    }

    private static LivingEntity findAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (!(damager instanceof Projectile projectile)) return null;
        return projectile.getShooter() instanceof LivingEntity living ? living : null;
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
        LivingEntity attacker = findAttacker(event.getDamager());
        CombatDamageService.SkillHit skill = damageService.claim(event);
        if (skill == null && event.getDamager() instanceof Player player) {
            warnInsufficientLevel(player, player.getInventory().getItemInMainHand());
        }
        CombatStatsSnapshot attackStats;
        if (skill != null) {
            attackStats = skill.stats();
        } else if (event.getDamager() instanceof Projectile projectile) {
            ShotStats shot = shots.get(projectile.getUniqueId());
            // Projectiles already in flight at reload have no trustworthy weapon snapshot.
            attackStats = shot != null && attacker != null && shot.source().equals(attacker.getUniqueId())
                    ? shot.stats() : CombatStatsSnapshot.empty();
        } else {
            attackStats = attacker == null ? CombatStatsSnapshot.empty() : statsService.getStats(attacker);
        }
        boolean weaponHit = skill == null && switch (event.getCause()) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> true;
            default -> false;
        };
        CombatDamageType type = CombatDamageType.PHYSICAL;
        if (skill != null) {
            type = skill.specification().type();
        } else if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC
                || event.getCause() == EntityDamageEvent.DamageCause.DRAGON_BREATH) {
            type = CombatDamageType.MAGIC;
        }
        if (weaponHit && !attackStats.hasDamage() && attackStats.hasAttribute(CombatStat.MAGIC_DAMAGE)) {
            type = CombatDamageType.MAGIC;
        }
        double baseDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        if (skill != null) baseDamage = skill.amount();
        if (weaponHit) {
            baseDamage = 1.0D;
            if (attacker != null && !(attacker instanceof Player)) {
                baseDamage = mythicMobs.attackDamage(attacker).orElse(1.0D);
            }
            if (attackStats.hasDamage() || type == CombatDamageType.MAGIC) {
                baseDamage = CombatFormula.damage(attackStats, type, ThreadLocalRandom.current().nextDouble());
            }
        }
        boolean allowCritical = skill != null ? skill.specification().critical()
                : weaponHit && type == CombatDamageType.PHYSICAL;
        boolean critical = allowCritical && CombatFormula.critical(attackStats,
                ThreadLocalRandom.current().nextDouble(100));
        if (weaponHit || skill != null) {
            DamageModifierSupport.setBaseDamage(event,
                    baseDamage * CombatFormula.criticalMultiplier(attackStats, critical));
        }
        CombatStatsSnapshot targetStats = statsService.getStats(target);
        applyDefense(event, attackStats, targetStats, type);
        if (attacker != null) {
            boolean leech = skill != null ? skill.specification().lifesteal() : weaponHit;
            outcomes.put(event, new HitOutcome(attacker, attackStats, type, critical, leech));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFinalDamage(EntityDamageByEntityEvent event) {
        HitOutcome hit = outcomes.remove(event);
        if (hit == null || event.isCancelled()) return;
        double absorbed = 0;
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) {
            absorbed = Math.max(0, -event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION));
        }
        if (event.getFinalDamage() <= 0 && absorbed <= 0) return;
        LivingEntity target = (LivingEntity) event.getEntity();
        if (hit.attacker() instanceof Player player) {
            healthBarService.recordHit(player, target, Math.max(0, event.getFinalDamage()), absorbed, hit.type());
            if (!(target instanceof Player)) refreshHealthBarAfterHit(player, event);
            if (hit.critical() && plugin.combatSettings().feedbackEnabled()) {
                String message = plugin.combatSettings().criticalHitMessage(event.getFinalDamage());
                if (!message.isBlank()) player.sendMessage(message);
            }
        } else if (target instanceof Player victim && mythicMobs.isMythicMob(hit.attacker())) {
            healthBarService.recordIncomingHit(victim, Math.max(0, event.getFinalDamage()), absorbed, hit.type());
            refreshHealthBarAfterHit(victim, event);
        }
        if (hit.critical()) playCriticalEffects(target);
        double percentage = hit.stats().value(CombatStat.LIFESTEAL);
        if (!hit.lifesteal() || percentage <= 0) return;
        double targetHealth = target.getHealth();
        tasks.runLater(1L, () -> {
            LivingEntity attacker = hit.attacker();
            if (event.isCancelled() || attacker.isDead() || !attacker.isValid()) return;
            AttributeInstance maximum = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maximum == null) return;
            double healed = CombatFormula.lifesteal(event.getFinalDamage(), targetHealth,
                    attacker.getHealth(), maximum.getValue(), percentage);
            if (healed > 0) attacker.setHealth(attacker.getHealth() + healed);
        });
    }

    private void refreshHealthBarAfterHit(Player viewer, EntityDamageByEntityEvent event) {
        tasks.runLater(1L, () -> {
            if (!viewer.isOnline()) return;
            if (event.isCancelled()) healthBarService.clear(viewer);
            else healthBarService.refresh(viewer);
        });
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity source) {
            if (source instanceof Player player) warnInsufficientLevel(player, player.getInventory().getItemInMainHand());
            shots.put(event.getEntity().getUniqueId(),
                    new ShotStats(source.getUniqueId(), statsService.getStats(source), System.nanoTime()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        tasks.runLater(1L, () -> shots.remove(projectileId));
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
        if (event.getWhoClicked() instanceof Player player) {
            if (!event.isCancelled()) warnNewArmor(player);
            refreshPlayer(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (!event.isCancelled()) warnNewArmor(player);
            refreshPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        warnInsufficientLevel(event.getPlayer(), event.getItem());
    }

    private void warnNewArmor(Player player) {
        ItemStack[] previous = player.getInventory().getArmorContents();
        tasks.runLater(1L, () -> {
            if (!player.isOnline()) return;
            ItemStack[] current = player.getInventory().getArmorContents();
            for (int slot = 0; slot < current.length; slot++) {
                if (!Objects.equals(previous[slot], current[slot])
                        && statsService.hasInsufficientLevel(player, current[slot])) {
                    sendLevelWarning(player);
                    return;
                }
            }
        });
    }

    private void warnInsufficientLevel(Player player, ItemStack item) {
        if (statsService.hasInsufficientLevel(player, item)) sendLevelWarning(player);
    }

    private void sendLevelWarning(Player player) {
        long now = System.nanoTime();
        Long last = levelWarnings.get(player.getUniqueId());
        if (last != null && now - last < 1_000_000_000L) return;
        levelWarnings.put(player.getUniqueId(), now);
        player.sendMessage("§c你的等级不足以使用这件物品!");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
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
        removeMovementModifier(event.getPlayer());
        queuedRefreshes.remove(event.getPlayer().getUniqueId());
        levelWarnings.remove(event.getPlayer().getUniqueId());
        healthBarService.clear(event.getPlayer());
    }

    public void refreshOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) refreshPlayer(player);
    }

    public void regenerateOnlinePlayers() {
        long now = System.nanoTime();
        shots.values().removeIf(shot -> now - shot.createdNanos() > 60_000_000_000L);
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
        applyMovementModifier(player, stats);
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

    /**
     * Removes the owned speed modifier without changing sprint, potion or other plugin modifiers.
     */
    public void removeMovementModifier(Player player) {
        AttributeInstance movement = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movement == null) return;
        for (AttributeModifier modifier : movement.getModifiers()) {
            if (MOVEMENT_MODIFIER_ID.equals(modifier.getUniqueId())) {
                movement.removeModifier(modifier);
                return;
            }
        }
    }

    private void applyMovementModifier(Player player, CombatStatsSnapshot stats) {
        removeMovementModifier(player);
        double percentage = Math.clamp(stats.value(CombatStat.MOVEMENT_SPEED), -100, 100);
        if (percentage == 0) return;
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID, "DreamRPG Equipment Movement", percentage / 100,
                AttributeModifier.Operation.ADD_SCALAR
        ));
    }

    /**
     * Discards all short-lived attack and refresh state during unload.
     */
    public void close() {
        shots.clear();
        outcomes.clear();
        queuedRefreshes.clear();
    }

    private record ShotStats(UUID source, CombatStatsSnapshot stats, long createdNanos) {
    }

    private record HitOutcome(
            LivingEntity attacker,
            CombatStatsSnapshot stats,
            CombatDamageType type,
            boolean critical,
            boolean lifesteal
    ) {
    }

}
