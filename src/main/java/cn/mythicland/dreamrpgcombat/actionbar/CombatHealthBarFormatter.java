package cn.mythicland.dreamrpgcombat.actionbar;

import java.util.Locale;

/**
 * Renders health and absorption independently, with at most ten hearts per pool.
 */
final class CombatHealthBarFormatter {

    private static final int DISPLAY_HEARTS = 10;
    private static final String HEART = "❤";

    private CombatHealthBarFormatter() {
    }

    static String render(CombatHealthBarStatus status) {
        StringBuilder builder = new StringBuilder(status.targetDisplayName().stripTrailing());
        builder.append(" &7(")
                .append(healthColor(status))
                .append(formatTwoDecimals(status.remainingHealth()))
                .append("&7/&c")
                .append(formatTwoDecimals(status.maxHealth()))
                .append("&7) ");
        int capacity = DISPLAY_HEARTS;
        int remaining = Math.min(capacity, healthHearts(status.remainingHealth(), status.maxHealth()));
        int damage = Math.min(capacity - remaining, hearts(status.healthDamage(), status.maxHealth()));
        appendHearts(builder, "&4", remaining);
        appendHearts(builder, "&c", damage);
        appendHearts(builder, "&7", capacity - remaining - damage);
        int absorption = hearts(status.remainingAbsorption(), status.maxAbsorption());
        if (absorption > 0) {
            builder.append(" ");
            appendHearts(builder, "&e", absorption);
        }
        String damageColor = switch (status.damageType()) {
            case PHYSICAL -> "&6";
            case MAGIC -> "&d";
            case TRUE -> "&f";
        };
        builder.append(" ").append(damageColor).append("+").append(formatTwoDecimals(status.finalDamage()));
        return builder.toString();
    }

    private static void appendHearts(StringBuilder builder, String color, int hearts) {
        if (hearts <= 0) return;
        builder.append(color).repeat(HEART, hearts);
    }

    private static String healthColor(CombatHealthBarStatus status) {
        if (status.remainingHealth() <= status.maxHealth() / 3) return "&c";
        if (status.remainingHealth() <= status.maxHealth() * 2 / 3) return "&e";
        return "&a";
    }

    private static int healthHearts(double value, double maximum) {
        if (value <= 0) return 0;
        if (formatTwoDecimals(value).equals(formatTwoDecimals(maximum))) return DISPLAY_HEARTS;
        return Math.max(1, (int) Math.floor(Math.clamp(value / maximum, 0, 1) * DISPLAY_HEARTS));
    }

    private static int hearts(double value, double maximum) {
        if (maximum <= 0) return 0;
        return (int) Math.ceil(Math.clamp(value / maximum, 0, 1) * DISPLAY_HEARTS);
    }

    private static String formatTwoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
