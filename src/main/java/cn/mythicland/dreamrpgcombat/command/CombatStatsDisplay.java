package cn.mythicland.dreamrpgcombat.command;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.combat.CombatFormula;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.List;
import java.util.Locale;

/**
 * Renders the player-facing DreamRPG combat attribute panel.
 */
public final class CombatStatsDisplay {

    private CombatStatsDisplay() {
    }

    static List<String> render(CombatStatsSnapshot stats) {
        return render(stats, null);
    }

    public static List<String> render(CombatStatsSnapshot stats, CombatSettings settings) {
        return List.of(
                colorize("&2&lDream&aRPG &f| &7战斗属性"),
                colorize("  &f"),
                valueLine("&4", label(settings, CombatStat.LIFESTEAL, "嗜血"),
                        format(stats.value(CombatStat.LIFESTEAL)) + "%"
                ),
                valueLine("&a", label(settings, CombatStat.HEALTH, "生命值"), format(stats.totalHealth())),
                valueLine("&6", label(settings, CombatStat.DAMAGE, "物理攻击"), damageValue(stats)),
                valueLine("&d", label(settings, CombatStat.MAGIC_DAMAGE, "法术攻击"), magicDamageValue(stats)),
                valueLine("&6", label(settings, CombatStat.PHYSICAL_DEFENSE, "物理防御"),
                        defenseValue(stats, CombatStat.PHYSICAL_DEFENSE)
                ),
                valueLine("&d", label(settings, CombatStat.MAGIC_DEFENSE, "法术防御"),
                        defenseValue(stats, CombatStat.MAGIC_DEFENSE)
                ),
                penetrationLine(settings, stats, CombatDamageType.PHYSICAL),
                penetrationLine(settings, stats, CombatDamageType.MAGIC),
                valueLine("&c", label(settings, CombatStat.CRIT_CHANCE, "暴击几率"),
                        format(stats.critChancePercent()) + "%"
                ),
                valueLine("&4", label(settings, CombatStat.CRIT_DAMAGE, "暴击伤害"),
                        format(CombatFormula.criticalMultiplier(stats, true) * 100) + "%"
                ),
                valueLine("&a", label(settings, CombatStat.HEALTH_REGEN, "生命回复"),
                        format(stats.healthRegenPercent()) + "%"
                ),
                valueLine("&b", label(settings, CombatStat.MOVEMENT_SPEED, "移动速度"),
                        format(stats.value(CombatStat.MOVEMENT_SPEED)) + "%"
                ),
                valueLine("&b", label(settings, CombatStat.EXPERIENCE_BONUS, "经验加成"),
                        format(stats.experienceBonusPercent()) + "%"
                ),
                colorize("  &f")
        );
    }

    private static String penetrationLine(
            CombatSettings settings,
            CombatStatsSnapshot stats,
            CombatDamageType type
    ) {
        CombatStat flatPenetration = type.flatPenetration();
        CombatStat percentagePenetration = type.percentagePenetration();
        return valueLine("&f", label(settings, flatPenetration, flatPenetration.defaultDisplayName()),
                format(stats.value(flatPenetration)) + " / "
                        + format(stats.value(percentagePenetration)) + "%"
        );
    }

    private static String defenseValue(CombatStatsSnapshot stats, CombatStat stat) {
        if (stats.hasAttribute(stat)) return format(stats.value(stat));
        return stats.hasDefense() ? format(stats.defensePercent()) + "%" : "0";
    }

    private static String magicDamageValue(CombatStatsSnapshot stats) {
        var range = stats.attribute(CombatStat.MAGIC_DAMAGE);
        if (range.isFixed()) return format(range.minimum());
        return format(range.minimum()) + " - " + format(range.maximum());
    }

    private static String label(CombatSettings settings, CombatStat stat, String fallback) {
        if (settings == null) return fallback;
        String value = settings.label(stat);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String valueLine(String labelColor, String label, String value) {
        return colorize("  " + labelColor + "&l" + label + ": &2&l" + value);
    }

    private static String damageValue(CombatStatsSnapshot stats) {
        boolean noDamageContribution = Double.compare(stats.damageMinimum(), 0.0D) == 0
                && Double.compare(stats.damageMaximum(), 0.0D) == 0;
        if (!stats.hasDamage() && noDamageContribution) return "1";
        String value = Double.compare(stats.damageMinimum(), stats.damageMaximum()) == 0
                ? format(stats.damageMinimum())
                : format(stats.damageMinimum()) + " - " + format(stats.damageMaximum());
        return stats.hasDamage() ? value : value + " (基础伤害: 1)";
    }

    private static String colorize(String text) {
        return LegacyText.colorize(text);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
