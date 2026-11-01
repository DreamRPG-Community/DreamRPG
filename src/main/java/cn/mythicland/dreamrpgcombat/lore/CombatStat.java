package cn.mythicland.dreamrpgcombat.lore;

import java.util.List;

/**
 * Supported RPG attributes parsed by Combat.
 */
public enum CombatStat {
    DAMAGE(
            "damage",
            "物理攻击",
            List.of("伤害", "伤害值", "物理攻击", "物理伤害"),
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
    ),
    MAGIC_DAMAGE(
            "magic-damage",
            "法术攻击",
            List.of("法术攻击", "法术伤害", "魔法伤害"),
            false,
            CombatStatType.PERMANENT
    ),
    PHYSICAL_DEFENSE(
            "physical-defense",
            "物理防御",
            List.of("物理防御"),
            false,
            CombatStatType.PERMANENT
    ),
    MAGIC_DEFENSE(
            "magic-defense",
            "法术防御",
            List.of("法术防御", "魔法防御"),
            false,
            CombatStatType.PERMANENT
    ),
    PHYSICAL_PENETRATION(
            "physical-penetration",
            "物理穿透",
            List.of("物理穿透"),
            false,
            CombatStatType.PERMANENT
    ),
    PHYSICAL_PENETRATION_PERCENT(
            "physical-penetration-percent",
            "物理穿透",
            List.of("物理穿透"),
            true,
            CombatStatType.PERMANENT
    ),
    MAGIC_PENETRATION(
            "magic-penetration",
            "法术穿透",
            List.of("法术穿透"),
            false,
            CombatStatType.PERMANENT
    ),
    MAGIC_PENETRATION_PERCENT(
            "magic-penetration-percent",
            "法术穿透",
            List.of("法术穿透"),
            true,
            CombatStatType.PERMANENT
    ),
    MOVEMENT_SPEED(
            "movement-speed",
            "移动速度",
            List.of("移动速度", "速度"),
            true,
            CombatStatType.PERMANENT
    ),
    LIFESTEAL(
            "lifesteal",
            "嗜血",
            List.of("嗜血", "吸血"),
            true,
            CombatStatType.PERMANENT
    );

    /**
     * Returns whether this is a typed attribute added after the legacy combat contract.
     */
    public boolean extended() {
        return switch (this) {
            case DAMAGE, DEFENSE, HEALTH, HEALTH_REGEN, CRIT_CHANCE, CRIT_DAMAGE, EXPERIENCE_BONUS -> false;
            default -> true;
        };
    }

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
