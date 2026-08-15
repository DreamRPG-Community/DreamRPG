package cn.mythicland.dreamrpg.compat;

import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;

/**
 * Restores the server-side parts of the 1.8.9 combat and inventory interaction rules.
 *
 * <p>ViaRewind's legacy cooldown display is handled separately by
 * {@link ViaRewindCooldownDisabler}. This listener deliberately has no Via classes in its
 * compile-time dependency set, so DreamRPG remains usable when the Via plugins are absent.</p>
 */
@InjectComponent
@ListenerComponent
public final class LegacyCombatCompatibility implements Listener {

    private static final double LEGACY_ATTACK_SPEED = 40.0D;
    private static final double VANILLA_ATTACK_SPEED = 4.0D;
    private static final double EPSILON = 1.0E-8D;
    private static final int PLAYER_OFF_HAND_SLOT = 40;

    private final PluginTaskScope tasks;

    /**
     * Creates the legacy combat listener.
     *
     * @param tasks plugin-owned scheduler scope
     */
    public LegacyCombatCompatibility(PluginTaskScope tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    private static boolean isOffHandSwap(InventoryClickEvent event) {
        return event.getAction() == InventoryAction.HOTBAR_SWAP
                && event.getHotbarButton() == PLAYER_OFF_HAND_SLOT;
    }

    private static boolean isPlayerOffHandSlot(Inventory inventory, int slot) {
        return inventory != null
                && inventory.getType() == InventoryType.PLAYER
                && slot == PLAYER_OFF_HAND_SLOT;
    }

    private static boolean affectsPlayerOffHand(InventoryDragEvent event, Player player) {
        if (event.getView().getBottomInventory() != player.getInventory()) return false;
        int topSize = event.getView().getTopInventory().getSize();
        return event.getRawSlots().stream().anyMatch(rawSlot ->
                rawSlot >= topSize && event.getView().convertSlot(rawSlot) == PLAYER_OFF_HAND_SLOT
        );
    }

    private static void applyLegacyAttackSpeed(Player player) {
        setAttackSpeed(player, LEGACY_ATTACK_SPEED);
    }

    private static void setAttackSpeed(Player player, double baseValue) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attribute != null && attribute.getBaseValue() != baseValue) {
            attribute.setBaseValue(baseValue);
        }
    }

    private static void clearOffHand(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand == null || offHand.getType() == Material.AIR) return;

        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        if (!inventory.addItem(offHand).isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), offHand);
        }
    }

    /**
     * Applies the 1.8.9 order of operations: the critical multiplier affects the attack
     * attribute damage, while the creature-specific enchantment bonus is added afterwards.
     *
     * @param baseDamage        base attack damage including the weapon correction
     * @param enchantmentDamage creature-specific enchantment damage
     * @param critical          whether the hit is critical
     * @return legacy pre-mitigation damage
     */
    static double calculateLegacyDamage(double baseDamage, double enchantmentDamage, boolean critical) {
        if (critical && baseDamage > 0.0D) {
            return baseDamage * 1.5D + enchantmentDamage;
        }
        return baseDamage + enchantmentDamage;
    }

    private static double damageScale(double originalDamage, double modifiedDamage) {
        if (Math.abs(originalDamage) < EPSILON) return 1.0D;
        if (!Double.isFinite(modifiedDamage)) return 1.0D;
        return Math.max(0.0D, modifiedDamage / originalDamage);
    }

    /**
     * 1.8.9 gave swords one more attack-damage point than the 1.12 item attributes. Axes used a
     * different legacy constructor, so their correction is material-specific. Adding the delta
     * to the live player attribute preserves strength, custom attribute modifiers and other
     * plugins' attack-damage changes instead of replacing them with a constant.
     */
    private static double legacyWeaponDamageDelta(ItemStack weapon) {
        if (weapon == null) return 0.0D;

        switch (weapon.getType()) {
            case WOOD_SWORD:
            case STONE_SWORD:
            case IRON_SWORD:
            case GOLD_SWORD:
            case DIAMOND_SWORD:
                return 1.0D;
            case WOOD_AXE:
            case GOLD_AXE:
                return -3.0D;
            case STONE_AXE:
                return -4.0D;
            case IRON_AXE:
                return -3.0D;
            case DIAMOND_AXE:
                return -2.0D;
            default:
                return 0.0D;
        }
    }

    private static double legacyEnchantmentDamage(ItemStack weapon, Entity target) {
        if (weapon == null || !(target instanceof LivingEntity)) return 0.0D;

        int sharpness = weapon.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        if (sharpness > 0) return sharpness * 1.25D;

        EntityType targetType = target.getType();
        if (isUndead(targetType)) {
            int smite = weapon.getEnchantmentLevel(Enchantment.DAMAGE_UNDEAD);
            if (smite > 0) return smite * 2.5D;
        }
        if (isArthropod(targetType)) {
            int bane = weapon.getEnchantmentLevel(Enchantment.DAMAGE_ARTHROPODS);
            if (bane > 0) return bane * 2.5D;
        }
        return 0.0D;
    }

    private static boolean isUndead(EntityType type) {
        switch (type) {
            case ZOMBIE:
            case ZOMBIE_VILLAGER:
            case PIG_ZOMBIE:
            case SKELETON:
            case WITHER_SKELETON:
            case STRAY:
            case WITHER:
            case HUSK:
                return true;
            default:
                return false;
        }
    }

    private static boolean isArthropod(EntityType type) {
        switch (type) {
            case SPIDER:
            case CAVE_SPIDER:
            case SILVERFISH:
            case ENDERMITE:
                return true;
            default:
                return false;
        }
    }

    private static boolean isLegacyCritical(Player player, Entity target) {
        return target instanceof LivingEntity
                && player.getFallDistance() > 0.0F
                && !player.isOnGround()
                && !isOnLadder(player)
                && !isInWater(player)
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                && player.getVehicle() == null;
    }

    private static boolean isOnLadder(Player player) {
        Location feet = player.getLocation();
        Location eyes = player.getEyeLocation();
        return isLadder(feet.getBlock().getType())
                || isLadder(eyes.getBlock().getType())
                || isLadder(feet.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType());
    }

    private static boolean isLadder(Material material) {
        return material == Material.LADDER || material == Material.VINE;
    }

    private static boolean isInWater(Player player) {
        return isWater(player.getLocation().getBlock().getType())
                || isWater(player.getEyeLocation().getBlock().getType());
    }

    private static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER;
    }

    /**
     * Applies the no-cooldown attribute before player data loading starts.
     *
     * @param event join event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        applyLegacyAttackSpeed(event.getPlayer());
    }

    /**
     * Reapplies the combat attribute after DreamRPG has applied the stored inventory.
     *
     * @param event DreamRPG data-ready event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDataReady(PlayerDataReadyEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player == null) return;
        applyLegacyAttackSpeed(player);
        clearOffHand(player);
    }

    /**
     * Reapplies the combat attribute after a world change.
     *
     * @param event world-change event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        applyLegacyAttackSpeed(event.getPlayer());
        clearOffHand(event.getPlayer());
    }

    /**
     * Reapplies the combat attribute after the native respawn reset.
     *
     * @param event respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        tasks.runLater(1L, () -> {
            if (!event.getPlayer().isOnline()) return;
            applyLegacyAttackSpeed(event.getPlayer());
            clearOffHand(event.getPlayer());
        });
    }

    /**
     * Restores the vanilla base value after the player leaves the server.
     *
     * @param event quit event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        clearOffHand(event.getPlayer());
        setAttackSpeed(event.getPlayer(), VANILLA_ATTACK_SPEED);
    }

    /**
     * Cancels the 1.9+ sweep damage event.
     *
     * @param event entity damage event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSweepAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
        }
    }

    /**
     * Replaces the 1.12 attack result with the calculated 1.8.9 result. The replacement is made
     * at the BASE modifier so armor, resistance, blocking, absorption and other damage modifiers
     * are still recalculated by Bukkit. It runs before normal-priority damage plugins, allowing
     * those plugins to amplify the calculated legacy damage normally. Entity velocity is left to
     * Paper's native attack implementation so sprint and enchantment knockback is not applied twice.
     *
     * @param event player melee damage event
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        Entity target = event.getEntity();
        ItemStack weapon = player.getInventory().getItemInMainHand();
        double attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) == null
                ? 1.0D
                : player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
        double legacyBaseDamage = attackDamage + legacyWeaponDamageDelta(weapon);
        double enchantmentDamage = legacyEnchantmentDamage(weapon, target);
        boolean critical = isLegacyCritical(player, target);
        double legacyDamage = calculateLegacyDamage(legacyBaseDamage, enchantmentDamage, critical);

        double originalDamage = event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE);
        double modifiedDamage = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
        double externalScale = damageScale(originalDamage, modifiedDamage);
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, Math.max(0.0D, legacyDamage * externalScale));
    }

    /**
     * Cancels the native attack cooldown by keeping the player's base attack speed high enough
     * that a normal weapon reaches full charge immediately.
     *
     * @param event hotbar-change event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        applyLegacyAttackSpeed(event.getPlayer());
    }

    /**
     * Prevents swapping any item into the off-hand.
     *
     * @param event off-hand swap event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
        clearOffHand(event.getPlayer());
    }

    /**
     * Prevents inventory clicks and number-key actions targeting the off-hand slot.
     *
     * @param event inventory click event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isOffHandSwap(event) || isPlayerOffHandSlot(event.getClickedInventory(), event.getSlot())) {
            event.setCancelled(true);
            clearOffHand(player);
        }
    }

    /**
     * Prevents dragging an item into the off-hand slot.
     *
     * @param event inventory drag event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (affectsPlayerOffHand(event, player)) {
            event.setCancelled(true);
            clearOffHand(player);
        }
    }

    /**
     * Prevents using an off-hand item, including off-hand block interaction.
     *
     * @param event interaction event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) event.setCancelled(true);
    }

    /**
     * Prevents using an off-hand item on an entity.
     *
     * @param event entity interaction event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) event.setCancelled(true);
    }

    /**
     * Cancels reeling an entity in and removes the bobber so the server cannot apply the pull.
     *
     * @param event fishing event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCaughtEntity(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        event.setCancelled(true);
        if (event.getHook() != null) event.getHook().remove();
    }
}
