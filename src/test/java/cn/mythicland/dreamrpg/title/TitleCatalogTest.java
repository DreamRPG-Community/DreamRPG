package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.api.TitleSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TitleCatalogTest {
    @Test
    void supportsEveryCombatAttributeAndPreservesRangesAndSigns() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("titles.hero.name", "&6[勇者]");
        for (CombatStat stat : CombatStat.values()) yaml.set("titles.hero.stats." + stat.configKey(), "2-4");
        var hero = TitleCatalog.parse(yaml).get("hero");
        assertEquals(CombatStat.values().length, hero.combatStats().values().size());
        for (CombatStat stat : CombatStat.values()) {
            var range = hero.bonuses().get(stat);
            assertEquals(2, range.minimum());
            assertEquals(4, range.maximum());
            assertEquals(stat.percentageAttribute(), range.percent());
        }
        yaml.set("titles.hero.stats.movement-speed", "-10%");
        assertEquals(-10, TitleCatalog.parse(yaml).get("hero").bonuses().get(CombatStat.MOVEMENT_SPEED).average());
        assertThrows(UnsupportedOperationException.class, () -> hero.bonuses().clear());
    }

    @Test
    void rejectsUnknownStatsInvalidUnitsAndMalformedDefinitions() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertTrue(TitleCatalog.parse(yaml).isEmpty());
        yaml.set("titles.hero.name", "Hero");
        yaml.set("titles.hero.stats.mana", 10);
        assertThrows(IllegalArgumentException.class, () -> TitleCatalog.parse(yaml));
        yaml.set("titles.hero.stats.mana", null);
        yaml.set("titles.hero.stats.health", "10%");
        assertThrows(IllegalArgumentException.class, () -> TitleCatalog.parse(yaml));
        yaml.set("titles.hero.stats.health", "NaN");
        assertThrows(IllegalArgumentException.class, () -> TitleCatalog.parse(yaml));
        assertThrows(IllegalArgumentException.class, () -> new TitleDefinition("../hero", "Hero", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new TitleDefinition("hero", " ", Map.of()));
    }

    @Test
    void expiresAtExactDeadlineAndKeepsPermanentOwnership() {
        var state = new TitleSnapshot(Map.of("hero", 1000L, "veteran", 0L), "hero");
        assertTrue(state.owns("hero", 999));
        assertFalse(state.owns("hero", 1000));
        assertTrue(state.owns("veteran", Long.MAX_VALUE));
        assertFalse(state.owns("missing", 1));
        assertThrows(UnsupportedOperationException.class, () -> state.expirations().clear());
        assertThrows(IllegalArgumentException.class, () -> new TitleSnapshot(Map.of(), "hero"));
        assertThrows(IllegalArgumentException.class, () -> new TitleSnapshot(Map.of("hero", -1L), ""));
    }
}
