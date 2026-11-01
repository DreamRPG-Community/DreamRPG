package cn.mythicland.dreamrpgcombat.config;

import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.Locale;
import java.util.Objects;

/**
 * Configured messages for triggered Combat effects.
 */
record CombatMessages(String criticalHitTemplate, String healthRegenTemplate) {

    private static final String DEFAULT_CRITICAL_HIT = "&6暴击! &f造成 &c{damage} &f点伤害";
    private static final String DEFAULT_HEALTH_REGEN = "&a生命回复 &f+{amount}";

    CombatMessages {
        Objects.requireNonNull(criticalHitTemplate, "criticalHitTemplate");
        Objects.requireNonNull(healthRegenTemplate, "healthRegenTemplate");
    }

    static CombatMessages defaults() {
        return new CombatMessages(DEFAULT_CRITICAL_HIT, DEFAULT_HEALTH_REGEN);
    }

    private static String render(String template, String placeholder, double value) {
        String formattedValue = format(value);
        return LegacyText.colorize(template.replace("{" + placeholder + "}", formattedValue));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    String criticalHit(double damage) {
        return render(criticalHitTemplate, "damage", damage);
    }

    String healthRegen(double amount) {
        return render(healthRegenTemplate, "amount", amount);
    }
}
