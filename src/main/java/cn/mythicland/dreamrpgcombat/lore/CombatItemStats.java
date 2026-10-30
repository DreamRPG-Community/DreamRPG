package cn.mythicland.dreamrpgcombat.lore;

import cn.mythicland.dreamlib.item.NumericRange;

import java.util.*;

/**
 * Immutable attributes parsed from one item stack.
 *
 * @param values        supported attributes and their numeric values
 * @param invalidLines  supported attribute lines that were skipped
 * @param requiredLevel minimum RPG level, or {@code -1} when unrestricted
 */
public record CombatItemStats(
        Map<CombatStat, NumericRange> values,
        List<String> invalidLines,
        long requiredLevel
) {

    public CombatItemStats {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(invalidLines, "invalidLines");
        if (requiredLevel < -1L) {
            throw new IllegalArgumentException("requiredLevel must be -1 or greater");
        }
        EnumMap<CombatStat, NumericRange> copy = new EnumMap<>(CombatStat.class);
        copy.putAll(values);
        values = Collections.unmodifiableMap(copy);
        invalidLines = List.copyOf(invalidLines);
    }

    /**
     * Creates an unrestricted item-stat snapshot.
     *
     * @param values       supported attributes and their numeric values
     * @param invalidLines supported attribute lines that were skipped
     */
    public CombatItemStats(Map<CombatStat, NumericRange> values, List<String> invalidLines) {
        this(values, invalidLines, -1L);
    }

    /**
     * Returns one parsed attribute when present.
     *
     * @param stat the requested attribute
     * @return the parsed value
     */
    public Optional<NumericRange> value(CombatStat stat) {
        return Optional.ofNullable(values.get(Objects.requireNonNull(stat, "stat")));
    }

    /**
     * Returns whether this item contributes the requested attribute.
     *
     * @param stat the requested attribute
     * @return true when the attribute exists
     */
    public boolean has(CombatStat stat) {
        return values.containsKey(Objects.requireNonNull(stat, "stat"));
    }

    /**
     * Returns whether this item has a minimum RPG level.
     *
     * @return true when a level restriction was parsed
     */
    public boolean hasRequiredLevel() {
        return requiredLevel >= 0L;
    }
}
