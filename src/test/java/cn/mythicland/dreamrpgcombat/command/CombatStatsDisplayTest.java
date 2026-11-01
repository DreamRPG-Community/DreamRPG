package cn.mythicland.dreamrpgcombat.command;

import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
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
                "§2§lDream§aRPG §f| §7战斗属性",
                "  §f",
                "  §4嗜血: §f0%",
                "  §a生命值: §f170",
                "  §6物理攻击: §f100",
                "  §d法术攻击: §f0",
                "  §6物理防御: §f100%",
                "  §d法术防御: §f100%",
                "  §f物理穿透: §f0 / 0%",
                "  §f法术穿透: §f0 / 0%",
                "  §c暴击几率: §f0%",
                "  §4暴击伤害: §f150%",
                "  §a生命回复: §f100%",
                "  §b移动速度: §f0%",
                "  §b经验加成: §f-50%",
                "  §f"
        ), CombatStatsDisplay.render(stats));
    }

    @Test
    void displaysPenetrationValuesAndPercentageOnlyHealthRegen() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .attribute(CombatStat.PHYSICAL_PENETRATION, new NumericRange(12, 12, false))
                .attribute(CombatStat.PHYSICAL_PENETRATION_PERCENT, new NumericRange(30, 30, true))
                .attribute(CombatStat.MAGIC_PENETRATION, new NumericRange(5, 5, false))
                .attribute(CombatStat.MAGIC_PENETRATION_PERCENT, new NumericRange(10, 10, true))
                .attribute(CombatStat.LIFESTEAL, new NumericRange(4, 4, true))
                .attribute(CombatStat.MOVEMENT_SPEED, new NumericRange(7.5, 7.5, true))
                .build();

        assertEquals(List.of(
                "§2§lDream§aRPG §f| §7战斗属性",
                "  §f",
                "  §4嗜血: §f4%",
                "  §a生命值: §f0",
                "  §6物理攻击: §f1",
                "  §d法术攻击: §f0",
                "  §6物理防御: §f0",
                "  §d法术防御: §f0",
                "  §f物理穿透: §f12 / 30%",
                "  §f法术穿透: §f5 / 10%",
                "  §c暴击几率: §f0%",
                "  §4暴击伤害: §f150%",
                "  §a生命回复: §f0%",
                "  §b移动速度: §f7.5%",
                "  §b经验加成: §f0%",
                "  §f"
        ), CombatStatsDisplay.render(stats));
    }

    @Test
    void keepsPureNegativeDamageVisibleWithoutChangingTheFixedBaseDamage() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(-20.0D)
                .damageMaximum(-10.0D)
                .build();

        assertEquals("  §6物理攻击: §f-20 - -10 (基础伤害: 1)", CombatStatsDisplay.render(stats).get(4));
    }
}
