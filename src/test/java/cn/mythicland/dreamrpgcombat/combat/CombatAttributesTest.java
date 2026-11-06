package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.lore.CombatLoreParser;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatAttributesTest {

    @Test
    void parsesSharedPenetrationValueAndRate() {
        EnumMap<CombatStat, String> labels = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) labels.put(stat, stat.defaultDisplayName());
        var item = new CombatLoreParser(new CombatSettings(false, labels)).parse(List.of(
                "穿透值: +100", "穿透率: +30%",
                "移动速度: +12%", "嗜血: +3%", "法术攻击: +10-20"
        ));
        assertTrue(item.invalidLines().isEmpty());
        assertEquals(100, item.value(CombatStat.PENETRATION).orElseThrow().average());
        assertEquals(30, item.value(CombatStat.PENETRATION_PERCENT).orElseThrow().average());
        assertEquals(12, item.value(CombatStat.MOVEMENT_SPEED).orElseThrow().average());
        assertEquals(3, item.value(CombatStat.LIFESTEAL).orElseThrow().average());
    }

    @Test
    void ignoresRemovedPhysicalAndMagicPenetrationLabels() {
        var parser = new CombatLoreParser(new CombatSettings(false, new EnumMap<>(CombatStat.class)));
        var item = parser.parse(List.of(
                "物理穿透: +100", "物理穿透: +30%", "法术穿透: +20", "法术穿透: +15%"
        ));
        assertTrue(item.invalidLines().isEmpty());
        assertTrue(item.values().isEmpty());
        assertEquals(1, parser.parse(List.of("穿透值: +30%")).invalidLines().size());
    }

    @Test
    void appliesSharedRateThenValueToEachMatchingDefense() {
        var attacker = CombatStatsSnapshot.builder()
                .attribute(CombatStat.PENETRATION, new NumericRange(100, 100, false))
                .attribute(CombatStat.PENETRATION_PERCENT, new NumericRange(30, 30, true))
                .build();
        var defender = CombatStatsSnapshot.builder()
                .attribute(CombatStat.PHYSICAL_DEFENSE, new NumericRange(500, 500, false))
                .attribute(CombatStat.MAGIC_DEFENSE, new NumericRange(600, 600, false))
                .build();
        assertEquals(1000.0 * 600 / 850,
                CombatFormula.applyDefense(1000, attacker, defender, CombatDamageType.PHYSICAL, 600), 1e-9);
        assertEquals(1000.0 * 600 / 920,
                CombatFormula.applyDefense(1000, attacker, defender, CombatDamageType.MAGIC, 600), 1e-9);
    }

    @Test
    void preservesLegacyPercentageDefenseAndClampsExcessPenetrationAtZeroDefense() {
        var attacker = CombatStatsSnapshot.builder()
                .attribute(CombatStat.PENETRATION, new NumericRange(1000, 1000, false)).build();
        var numeric = CombatStatsSnapshot.builder()
                .attribute(CombatStat.PHYSICAL_DEFENSE, new NumericRange(100, 100, false)).build();
        var legacy = CombatStatsSnapshot.builder().hasDefense(true).defensePercent(20).build();
        assertEquals(100, CombatFormula.applyDefense(100, attacker, numeric, CombatDamageType.PHYSICAL, 600));
        assertEquals(80, CombatFormula.applyDefense(100, attacker, legacy, CombatDamageType.PHYSICAL, 600));
    }

    @Test
    void trueDamageBypassesTypedAndLegacyDefense() {
        var defender = CombatStatsSnapshot.builder()
                .hasDefense(true).defensePercent(100)
                .attribute(CombatStat.PHYSICAL_DEFENSE, new NumericRange(10000, 10000, false))
                .attribute(CombatStat.MAGIC_DEFENSE, new NumericRange(10000, 10000, false))
                .build();
        assertEquals(10, CombatFormula.applyDefense(10, CombatStatsSnapshot.empty(), defender,
                CombatDamageType.TRUE, 600));
    }

    @Test
    void lifestealUsesActualLifeDamageAndNeverExceedsMissingHealth() {
        assertEquals(3, CombatFormula.lifesteal(100, 200, 50, 100, 3));
        assertEquals(1.5, CombatFormula.lifesteal(100, 50, 50, 100, 3));
        assertEquals(1, CombatFormula.lifesteal(100, 200, 99, 100, 3));
        assertEquals(0, CombatFormula.lifesteal(0, 200, 50, 100, 3));
        assertEquals(0, CombatFormula.lifesteal(100, 200, 50, 100, -10));
    }
}
