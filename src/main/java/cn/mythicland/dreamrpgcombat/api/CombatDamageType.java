package cn.mythicland.dreamrpgcombat.api;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;

/**
 * The damage channel, independent of whether a hit originates from a weapon or a skill.
 */
public enum CombatDamageType {
    PHYSICAL(CombatStat.PHYSICAL_DEFENSE, CombatStat.PHYSICAL_PENETRATION, CombatStat.PHYSICAL_PENETRATION_PERCENT),
    MAGIC(CombatStat.MAGIC_DEFENSE, CombatStat.MAGIC_PENETRATION, CombatStat.MAGIC_PENETRATION_PERCENT),
    TRUE(null, null, null);

    private final CombatStat defense;
    private final CombatStat flatPenetration;
    private final CombatStat percentagePenetration;

    CombatDamageType(CombatStat defense, CombatStat flatPenetration, CombatStat percentagePenetration) {
        this.defense = defense;
        this.flatPenetration = flatPenetration;
        this.percentagePenetration = percentagePenetration;
    }

    /**
     * Returns the defense attribute used by this channel, or null for true damage.
     */
    public CombatStat defense() {
        return defense;
    }

    /**
     * Returns the fixed penetration attribute used by this channel, or null for true damage.
     */
    public CombatStat flatPenetration() {
        return flatPenetration;
    }

    /**
     * Returns the percentage penetration attribute used by this channel, or null for true damage.
     */
    public CombatStat percentagePenetration() {
        return percentagePenetration;
    }
}
