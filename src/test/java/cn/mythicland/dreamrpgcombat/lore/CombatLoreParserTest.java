package cn.mythicland.dreamrpgcombat.lore;

import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatLoreParserTest {

    @Test
    void acceptsLegacySupportedAttributesAndSkipsUnsupportedAttributes() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of(
                "§4伤害: §2+400-450",
                "§b防御: §2+23%",
                "§a生命值: §2+1700",
                "§c暴击几率: §2+30%",
                "§4暴击伤害: §2+130%",
                "§b格挡: §2+50%"
        ));

        assertEquals(5, result.values().size());
        assertEquals(400.0D, result.value(CombatStat.DAMAGE).orElseThrow().minimum());
        assertEquals(450.0D, result.value(CombatStat.DAMAGE).orElseThrow().maximum());
        assertEquals(23.0D, result.value(CombatStat.DEFENSE).orElseThrow().minimum());
        assertFalse(result.has(CombatStat.HEALTH_REGEN));
        assertTrue(result.invalidLines().isEmpty());
    }

    @Test
    void acceptsSignedValuesAndKeepsPercentageValidation() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of(
                "§b防御: §2-23%",
                "§4伤害: §2+50%",
                "§2生命回复: §2-5%",
                "§d经验加成: §2-50%",
                "§a生命值: §2-100",
                "§c暴击几率: §2-20%",
                "§4暴击伤害: §2-50%"
        ));

        assertTrue(result.has(CombatStat.DEFENSE));
        assertFalse(result.has(CombatStat.DAMAGE));
        assertTrue(result.has(CombatStat.HEALTH_REGEN));
        assertEquals(-23.0D, result.value(CombatStat.DEFENSE).orElseThrow().minimum());
        assertEquals(-5.0D, result.value(CombatStat.HEALTH_REGEN).orElseThrow().minimum());
        assertEquals(-50.0D, result.value(CombatStat.EXPERIENCE_BONUS).orElseThrow().minimum());
        assertEquals(-100.0D, result.value(CombatStat.HEALTH).orElseThrow().minimum());
        assertEquals(-20.0D, result.value(CombatStat.CRIT_CHANCE).orElseThrow().minimum());
        assertEquals(-50.0D, result.value(CombatStat.CRIT_DAMAGE).orElseThrow().minimum());
        assertEquals(1, result.invalidLines().size());
    }

    @Test
    void acceptsLevelAliasesAndUsesTheHighestRequirement() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of(
                "等级限制: 200级",
                "装备等级: 250",
                "等级: 300",
                "需要等级: 350",
                "伤害: 0"
        ));

        assertEquals(350L, result.requiredLevel());
        assertTrue(result.hasRequiredLevel());
        assertEquals(0.0D, result.value(CombatStat.DAMAGE).orElseThrow().minimum());
        assertTrue(result.invalidLines().isEmpty());
    }

    @Test
    void rejectsInvalidLevelRequirements() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of(
                "等级限制: -1",
                "装备等级: 20.5",
                "等级: 30%"
        ));

        assertFalse(result.hasRequiredLevel());
        assertEquals(3, result.invalidLines().size());
    }

    @Test
    void acceptsCriticalDamageAsAnExtraBonusPercent() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of("§4暴击伤害: §2+49%"));

        assertEquals(49.0D, result.value(CombatStat.CRIT_DAMAGE).orElseThrow().minimum());
        assertTrue(result.invalidLines().isEmpty());
    }

    @Test
    void acceptsKnownLabelsWithAnyLoreColor() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of("§c伤害: §2+400"));

        assertTrue(result.has(CombatStat.DAMAGE));
        assertTrue(result.invalidLines().isEmpty());
    }

    @Test
    void matchesConfiguredLabelsWithoutUsingColor() {
        EnumMap<CombatStat, String> labels = defaultLabels();
        labels.put(CombatStat.DAMAGE, "攻击");

        CombatItemStats result = new CombatLoreParser(
                new CombatSettings(false, labels)
        ).parse(List.of("§c攻击: §2+400"));

        assertEquals(400.0D, result.value(CombatStat.DAMAGE).orElseThrow().minimum());
        assertTrue(result.invalidLines().isEmpty());
    }

    @Test
    void requiresAValidNumericValueAfterARecognizedLabel() {
        CombatLoreParser parser = new CombatLoreParser(defaultSettings());

        CombatItemStats result = parser.parse(List.of(
                "§4伤害",
                "§4伤害: §f不是数字",
                "§4伤害: §f+10"
        ));

        assertEquals(10.0D, result.value(CombatStat.DAMAGE).orElseThrow().minimum());
        assertEquals(1, result.invalidLines().size());
    }

    private static CombatSettings defaultSettings() {
        return new CombatSettings(false, defaultLabels());
    }

    private static EnumMap<CombatStat, String> defaultLabels() {
        EnumMap<CombatStat, String> labels = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) {
            labels.put(stat, stat.defaultDisplayName());
        }
        return labels;
    }
}
