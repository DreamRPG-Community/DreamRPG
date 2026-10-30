package cn.mythicland.dreamrpgcombat.command;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamlib.text.LegacyText;

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
                colorize("&8&lDreamRPG &f| &7战斗属性"),
                colorize("  &f"),
                valueLine("&4", label(settings, CombatStat.DAMAGE, "伤害"), damageValue(stats)),
                valueLine("&b", label(settings, CombatStat.DEFENSE, "防御"),
                        stats.hasDefense() ? format(stats.defensePercent()) + "%" : "原版护甲"),
                valueLine("&a", label(settings, CombatStat.HEALTH, "生命值"), format(stats.totalHealth())),
                valueLine("&2", label(settings, CombatStat.HEALTH_REGEN, "生命回复"),
                        format(stats.healthRegenPercent()) + "%"),
                valueLine("&c", label(settings, CombatStat.CRIT_CHANCE, "暴击几率"),
                        format(stats.critChancePercent()) + "%"),
                valueLine("&4", label(settings, CombatStat.CRIT_DAMAGE, "暴击伤害"),
                        format(stats.critDamagePercent()) + "%"),
                valueLine("&d", label(settings, CombatStat.EXPERIENCE_BONUS, "经验加成"),
                        format(stats.experienceBonusPercent()) + "%"),
                colorize("  &f")
        );
    }

    private static String label(CombatSettings settings, CombatStat stat, String fallback) {
        if (settings == null) return fallback;
        String value = settings.label(stat);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String valueLine(String labelColor, String label, String value) {
        return colorize("  " + labelColor + label + ": &f" + value);
    }

    private static String damageValue(CombatStatsSnapshot stats) {
        boolean noDamageContribution = Double.compare(stats.damageMinimum(), 0.0D) == 0
                && Double.compare(stats.damageMaximum(), 0.0D) == 0;
        if (!stats.hasDamage() && noDamageContribution) return "原版伤害";
        String value = Double.compare(stats.damageMinimum(), stats.damageMaximum()) == 0
                ? format(stats.damageMinimum())
                : format(stats.damageMinimum()) + " - " + format(stats.damageMaximum());
        return stats.hasDamage() ? value : value + " (原版伤害)";
    }

    private static String colorize(String text) {
        return LegacyText.colorize(text);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
