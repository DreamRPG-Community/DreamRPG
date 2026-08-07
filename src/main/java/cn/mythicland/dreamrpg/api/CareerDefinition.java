package cn.mythicland.dreamrpg.api;

import cn.mythicland.lib.text.LegacyText;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable public definition of one DreamRPG career.
 *
 * @param id          stable database identifier
 * @param displayName configured player-facing name
 * @param prefix      colored prefix used by chat and scoreboard teams
 */
public record CareerDefinition(
        String id,
        String displayName,
        String prefix
) {

    /**
     * Validates and normalizes a career definition.
     */
    public CareerDefinition {
        id = normalizeId(id);
        displayName = requireText(displayName, "displayName").trim();
        prefix = LegacyText.colorize(requireText(prefix, "prefix"));
        if (prefix.length() > 16) {
            throw new IllegalArgumentException("Career prefix cannot exceed 16 characters: " + id);
        }
    }

    /**
     * Returns the first Minecraft color applied by the prefix.
     *
     * @return a section-sign color code, or white when the prefix has no color
     */
    public String nameColor() {
        String color = LegacyText.firstColorCode(prefix);
        return color.isEmpty() ? "§f" : color;
    }

    private static String normalizeId(String value) {
        String normalized = requireText(value, "id").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Career id contains unsupported characters: " + value);
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName);
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
