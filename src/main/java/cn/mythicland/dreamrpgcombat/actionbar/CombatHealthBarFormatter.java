package cn.mythicland.dreamrpgcombat.actionbar;

/**
 * Renders the temporary target health bar in legacy text format.
 * Every target uses a fixed ten-heart capacity; health, absorption and damage
 * are scaled against the target's maximum health before rendering.
 */
final class CombatHealthBarFormatter {

    private static final int DISPLAY_HEARTS = 10;
    private static final String HEART = "❤";

    private CombatHealthBarFormatter() {
    }

    static String render(CombatHealthBarStatus status) {
        StringBuilder builder = new StringBuilder(trimTrailingWhitespace(status.targetDisplayName()));
        builder.append(" ");

        int remainingHearts = scaledHearts(status.remainingHealth(), status.maxHealth(), false);
        int absorptionHearts = scaledHearts(status.remainingAbsorption(), status.maxHealth(), true);
        int damageHearts = scaledHearts(status.healthDamage(), status.maxHealth(), true);
        int visibleAbsorptionHearts = Math.clamp(
                absorptionHearts,
                0,
                DISPLAY_HEARTS - remainingHearts
        );
        int visibleDamageHearts = Math.clamp(
                damageHearts,
                0,
                DISPLAY_HEARTS - remainingHearts - visibleAbsorptionHearts
        );
        int emptyHearts = Math.max(
                0,
                DISPLAY_HEARTS - remainingHearts - visibleAbsorptionHearts - visibleDamageHearts
        );

        appendHearts(builder, "&4", remainingHearts);
        appendHearts(builder, "&e", visibleAbsorptionHearts);
        appendHearts(builder, "&c", visibleDamageHearts);
        appendHearts(builder, "&7", emptyHearts);
        return builder.toString();
    }

    private static void appendHearts(StringBuilder builder, String color, int hearts) {
        if (hearts <= 0) return;
        builder.append(color).repeat(HEART, hearts);
    }

    private static int scaledHearts(double value, double maxValue, boolean roundUp) {
        double ratio = Math.clamp(value / maxValue, 0.0D, 1.0D);
        double scaled = ratio * DISPLAY_HEARTS;
        return (int) (roundUp ? Math.ceil(scaled) : Math.floor(scaled));
    }

    private static String trimTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
}
