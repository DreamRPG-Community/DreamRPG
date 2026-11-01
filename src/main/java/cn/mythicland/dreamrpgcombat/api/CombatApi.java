package cn.mythicland.dreamrpgcombat.api;

import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Main-thread combat attributes and typed skill damage exposed by DreamRPG.
 */
public interface CombatApi {

    /**
     * Returns the current effective attributes of a player.
     *
     * @param player the player to inspect
     * @return an immutable snapshot
     */
    CombatStatsSnapshot getStats(Player player);

    /**
     * Returns the current effective attributes of any living entity's supported equipment.
     *
     * @param entity the entity to inspect
     * @return an immutable snapshot
     */
    CombatStatsSnapshot getStats(LivingEntity entity);

    /**
     * Parses supported attributes from one ItemStack.
     *
     * @param itemStack the item to inspect
     * @return immutable parsed values and skipped supported lines
     */
    CombatItemStats parseItem(ItemStack itemStack);

    /**
     * Schedules health and movement refresh after a plugin changes equipment programmatically.
     *
     * @param player online player; call from the server thread
     */
    void refresh(Player player);

    /**
     * Inflicts one typed skill hit through Bukkit events, preserving cancellation and attribution.
     * The skill amount is not replaced with weapon damage. Must be called on the server thread.
     *
     * @param source        living caster
     * @param target        living victim
     * @param specification immutable damage and scaling rules
     * @throws IllegalStateException if called outside the server thread
     */
    void damage(LivingEntity source, LivingEntity target, CombatDamageSpec specification);
}
