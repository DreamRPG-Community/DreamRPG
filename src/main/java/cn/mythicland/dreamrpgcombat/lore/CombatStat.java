package cn.mythicland.dreamrpgcombat.lore;

import java.util.List;

/**
 * Supported RPG attributes parsed by Combat.
 */
public enum CombatStat {
    DAMAGE(
            "damage",
            "伤害",
            List.of("伤害", "伤害值"),
            false,
            CombatStatType.PERMANENT
    ),
    DEFENSE(
            "defense",
            "防御",
            List.of("防御"),
            true,
            CombatStatType.PERMANENT
    ),
    HEALTH(
            "health",
            "生命值",
            List.of("生命", "生命值"),
            false,
            CombatStatType.PERMANENT
    ),
    HEALTH_REGEN(
            "health-regen",
            "生命回复",
            List.of("生命回复"),
            true,
            CombatStatType.TRIGGERED
    ),
    CRIT_CHANCE(
            "crit-chance",
            "暴击几率",
            List.of("暴击率", "暴击几率"),
            true,
            CombatStatType.TRIGGERED
    ),
    CRIT_DAMAGE(
            "crit-damage",
            "暴击伤害",
            List.of("暴击伤害"),
            true,
            CombatStatType.TRIGGERED
    ),
    EXPERIENCE_BONUS(
            "experience-bonus",
            "经验加成",
            List.of("经验加成", "经验倍率"),
            true,
            CombatStatType.PERMANENT
    );

    private final String configKey;
    private final String defaultDisplayName;
    private final List<String> aliases;
    private final boolean percentageAttribute;
    private final CombatStatType type;

    CombatStat(
            String configKey,
            String defaultDisplayName,
            List<String> aliases,
            boolean percentageAttribute,
            CombatStatType type
    ) {
        this.configKey = configKey;
        this.defaultDisplayName = defaultDisplayName;
        this.aliases = List.copyOf(aliases);
        this.percentageAttribute = percentageAttribute;
        this.type = type;
    }

    /**
     * Returns the configuration key under {@code labels}.
     *
     * @return the configuration key
     */
    public String configKey() {
        return configKey;
    }

    /**
     * Returns the default player-facing Chinese label.
     *
     * @return the default label
     */
    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    /**
     * Returns the built-in legacy aliases.
     *
     * @return immutable aliases
     */
    public List<String> aliases() {
        return aliases;
    }

    /**
     * Returns whether this attribute is interpreted as a percentage.
     *
     * @return true for percentage attributes
     */
    public boolean percentageAttribute() {
        return percentageAttribute;
    }

    /**
     * Returns the effect category of this attribute.
     *
     * @return the attribute category
     */
    public CombatStatType type() {
        return type;
    }
}
