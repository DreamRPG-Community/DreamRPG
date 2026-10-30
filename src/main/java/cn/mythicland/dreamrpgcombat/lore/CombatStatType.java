package cn.mythicland.dreamrpgcombat.lore;

/**
 * Classifies how a Combat attribute takes effect.
 */
public enum CombatStatType {
    /**
     * An attribute that remains effective while its source is equipped.
     */
    PERMANENT,

    /**
     * An attribute that takes effect only when its combat or regeneration event triggers.
     */
    TRIGGERED
}
