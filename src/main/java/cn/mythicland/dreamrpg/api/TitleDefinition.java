package cn.mythicland.dreamrpg.api;

import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable title definition, safe to read on any thread while DreamRPG is enabled.
 *
 * @param id          stable lowercase identifier
 * @param displayName colored title text
 * @param bonuses     attributes contributed only while this title is equipped
 */
public record TitleDefinition(String id, String displayName, Map<CombatStat, NumericRange> bonuses) {
    public TitleDefinition {
        if (!Objects.requireNonNull(id, "id").matches("[a-z][a-z0-9_]{0,127}"))
            throw new IllegalArgumentException("Invalid title ID: " + id);
        if (Objects.requireNonNull(displayName, "displayName").isBlank())
            throw new IllegalArgumentException("Title display name cannot be blank: " + id);
        bonuses = Map.copyOf(bonuses);
        bonuses.forEach((stat, value) -> {
            if (value.percent() != stat.percentageAttribute())
                throw new IllegalArgumentException("Incorrect percentage unit for title " + id + ": " + stat.configKey());
        });
    }

    /**
     * @return unrestricted immutable attributes suitable for the combat aggregation pipeline
     */
    public CombatItemStats combatStats() {
        return new CombatItemStats(bonuses, List.of());
    }
}
