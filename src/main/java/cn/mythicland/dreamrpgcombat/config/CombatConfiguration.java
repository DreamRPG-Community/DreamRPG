package cn.mythicland.dreamrpgcombat.config;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.config.ConfigView;
import cn.mythicland.framework.bukkit.config.ConfigurableComponent;
import cn.mythicland.framework.bukkit.config.annotation.ConfigComponent;
import cn.mythicland.framework.bukkit.config.annotation.ConfigProperty;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Binds DreamRPG combat's flat configuration values and publishes its immutable domain settings.
 */
@ConfigComponent
public final class CombatConfiguration implements ConfigurableComponent {

    private volatile CombatSettings snapshot;

    private static String visibleLabel(String value, CombatStat stat) {
        return visibleLabel(value, stat.defaultDisplayName());
    }

    private static String visibleLabel(String value, String fallback) {
        String visible = LegacyText.stripColor(value).trim();
        return visible.isBlank() ? fallback : visible;
    }

    /**
     * Binds the current main configuration and publishes a complete settings snapshot.
     *
     * @param configuration Lib-owned configuration view
     */
    @Override
    public void reload(ConfigView configuration) {
        RawSettings raw = Objects.requireNonNull(configuration, "configuration")
                .bind(RawSettings.class);
        EnumMap<CombatStat, String> labels = new EnumMap<>(CombatStat.class);
        labels.put(CombatStat.DAMAGE, visibleLabel(raw.damageLabel(), CombatStat.DAMAGE));
        labels.put(CombatStat.DEFENSE, visibleLabel(raw.defenseLabel(), CombatStat.DEFENSE));
        labels.put(CombatStat.HEALTH, visibleLabel(raw.healthLabel(), CombatStat.HEALTH));
        labels.put(CombatStat.HEALTH_REGEN, visibleLabel(raw.healthRegenLabel(), CombatStat.HEALTH_REGEN));
        labels.put(CombatStat.CRIT_CHANCE, visibleLabel(raw.critChanceLabel(), CombatStat.CRIT_CHANCE));
        labels.put(CombatStat.CRIT_DAMAGE, visibleLabel(raw.critDamageLabel(), CombatStat.CRIT_DAMAGE));
        labels.put(
                CombatStat.EXPERIENCE_BONUS,
                visibleLabel(raw.experienceBonusLabel(), CombatStat.EXPERIENCE_BONUS)
        );
        labels.put(CombatStat.MAGIC_DAMAGE, visibleLabel(raw.magicDamageLabel(), CombatStat.MAGIC_DAMAGE));
        labels.put(CombatStat.PHYSICAL_DEFENSE, visibleLabel(raw.physicalDefenseLabel(), CombatStat.PHYSICAL_DEFENSE));
        labels.put(CombatStat.MAGIC_DEFENSE, visibleLabel(raw.magicDefenseLabel(), CombatStat.MAGIC_DEFENSE));
        labels.put(CombatStat.PENETRATION, visibleLabel(raw.penetrationLabel(), CombatStat.PENETRATION));
        labels.put(CombatStat.PENETRATION_PERCENT, visibleLabel(raw.penetrationRateLabel(), CombatStat.PENETRATION_PERCENT));
        labels.put(CombatStat.MOVEMENT_SPEED, visibleLabel(raw.movementSpeedLabel(), CombatStat.MOVEMENT_SPEED));
        labels.put(CombatStat.LIFESTEAL, visibleLabel(raw.lifestealLabel(), CombatStat.LIFESTEAL));
        snapshot = new CombatSettings(
                raw.feedbackEnabled(),
                labels,
                visibleLabel(raw.levelRequirementLabel(), "需要等级"),
                new CombatMessages(raw.criticalHitMessage(), raw.healthRegenMessage()),
                raw.defenseConstant()
        );
    }

    /**
     * Returns the most recently bound settings.
     *
     * @return immutable DreamRPG combat settings
     */
    public CombatSettings snapshot() {
        CombatSettings value = snapshot;
        if (value == null) throw new IllegalStateException("DreamRPG combat settings are not loaded");
        return value;
    }

    private record RawSettings(
            @ConfigProperty(
                    path = "combat-feedback",
                    defaultValue = "false"
            )
            boolean feedbackEnabled,
            @ConfigProperty(
                    path = "messages.critical-hit",
                    defaultValue = "&c&l暴击! &f你造成了&c{damage}&f点伤害!",
                    nonBlank = true
            )
            String criticalHitMessage,
            @ConfigProperty(
                    path = "messages.health-regen",
                    defaultValue = "&a&l生命回复! &f你恢复了&c{amount}&f点生命值!",
                    nonBlank = true
            )
            String healthRegenMessage,
            @ConfigProperty(
                    path = "labels.damage",
                    defaultValue = "物理攻击",
                    nonBlank = true
            )
            String damageLabel,
            @ConfigProperty(
                    path = "labels.defense",
                    defaultValue = "防御",
                    nonBlank = true
            )
            String defenseLabel,
            @ConfigProperty(
                    path = "labels.health",
                    defaultValue = "生命值",
                    nonBlank = true
            )
            String healthLabel,
            @ConfigProperty(
                    path = "labels.health-regen",
                    defaultValue = "生命回复",
                    nonBlank = true
            )
            String healthRegenLabel,
            @ConfigProperty(
                    path = "labels.crit-chance",
                    defaultValue = "暴击几率",
                    nonBlank = true
            )
            String critChanceLabel,
            @ConfigProperty(
                    path = "labels.crit-damage",
                    defaultValue = "暴击伤害",
                    nonBlank = true
            )
            String critDamageLabel,
            @ConfigProperty(
                    path = "labels.experience-bonus",
                    defaultValue = "经验加成",
                    nonBlank = true
            )
            String experienceBonusLabel,
            @ConfigProperty(
                    path = "labels.level-requirement",
                    defaultValue = "需要等级",
                    nonBlank = true
            )
            String levelRequirementLabel,
            @ConfigProperty(
                    path = "labels.magic-damage",
                    defaultValue = "法术攻击",
                    nonBlank = true
            )
            String magicDamageLabel,
            @ConfigProperty(
                    path = "labels.physical-defense",
                    defaultValue = "物理防御",
                    nonBlank = true
            )
            String physicalDefenseLabel,
            @ConfigProperty(
                    path = "labels.magic-defense",
                    defaultValue = "法术防御",
                    nonBlank = true
            )
            String magicDefenseLabel,
            @ConfigProperty(
                    path = "labels.penetration",
                    defaultValue = "穿透值",
                    nonBlank = true
            )
            String penetrationLabel,
            @ConfigProperty(
                    path = "labels.penetration-percent",
                    defaultValue = "穿透率",
                    nonBlank = true
            )
            String penetrationRateLabel,
            @ConfigProperty(
                    path = "labels.movement-speed",
                    defaultValue = "移动速度",
                    nonBlank = true
            )
            String movementSpeedLabel,
            @ConfigProperty(
                    path = "labels.lifesteal",
                    defaultValue = "嗜血",
                    nonBlank = true
            )
            String lifestealLabel,
            @ConfigProperty(
                    path = "combat.defense-constant",
                    defaultValue = "600",
                    positive = true,
                    finite = true
            )
            double defenseConstant
    ) {
    }
}
