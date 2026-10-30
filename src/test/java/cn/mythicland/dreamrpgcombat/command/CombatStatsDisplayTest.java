package cn.mythicland.dreamrpgcombat.command;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatStatsDisplayTest {

    @Test
    void rendersTheConfiguredAttributePanel() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(100.0D)
                .damageMaximum(100.0D)
                .hasDamage(true)
                .defensePercent(100.0D)
                .hasDefense(true)
                .health(100.0D)
                .baseHealth(20.0D)
                .levelHealthBonus(50.0D)
                .healthRegenPercent(100.0D)
                .experienceBonusPercent(-50.0D)
                .build();

        assertEquals(List.of(
                "§8§lDreamRPG §f| §7战斗属性",
                "  §f",
                "  §4伤害: §f100",
                "  §b防御: §f100%",
                "  §a生命值: §f170",
                "  §2生命回复: §f100%",
                "  §c暴击几率: §f0%",
                "  §4暴击伤害: §f0%",
                "  §d经验加成: §f-50%",
                "  §f"
        ), CombatStatsDisplay.render(stats));
    }

    @Test
    void keepsPureNegativeDamageVisibleWithoutReplacingVanillaDamage() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(-20.0D)
                .damageMaximum(-10.0D)
                .build();

        assertEquals("  §4伤害: §f-20 - -10 (原版伤害)", CombatStatsDisplay.render(stats).get(2));
    }
}
