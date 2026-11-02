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
                        "  §4§l嗜血: §2§l0%",
                        "  §a§l生命值: §2§l170",
                        "  §6§l物理攻击: §2§l100",
                        "  §d§l法术攻击: §2§l0",
                        "  §6§l物理防御: §2§l100%",
                        "  §d§l法术防御: §2§l100%",
                        "  §f§l物理穿透: §2§l0 / 0%",
                        "  §f§l法术穿透: §2§l0 / 0%",
                        "  §c§l暴击几率: §2§l0%",
                        "  §4§l暴击伤害: §2§l150%",
                        "  §a§l生命回复: §2§l100%",
                        "  §b§l移动速度: §2§l0%",
                        "  §b§l经验加成: §2§l-50%",
                        "  §f"
                ), CombatStatsDisplay.render(stats)
        );
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
                        "  §4§l嗜血: §2§l4%",
                        "  §a§l生命值: §2§l0",
                        "  §6§l物理攻击: §2§l1",
                        "  §d§l法术攻击: §2§l0",
                        "  §6§l物理防御: §2§l0",
                        "  §d§l法术防御: §2§l0",
                        "  §f§l物理穿透: §2§l12 / 30%",
                        "  §f§l法术穿透: §2§l5 / 10%",
                        "  §c§l暴击几率: §2§l0%",
                        "  §4§l暴击伤害: §2§l150%",
                        "  §a§l生命回复: §2§l0%",
                        "  §b§l移动速度: §2§l7.5%",
                        "  §b§l经验加成: §2§l0%",
                        "  §f"
                ), CombatStatsDisplay.render(stats)
        );
    }

    @Test
    void keepsPureNegativeDamageVisibleWithoutChangingTheFixedBaseDamage() {
        CombatStatsSnapshot stats = CombatStatsSnapshot.builder()
                .damageMinimum(-20.0D)
                .damageMaximum(-10.0D)
                .build();

        assertEquals("  §6§l物理攻击: §2§l-20 - -10 (基础伤害: 1)", CombatStatsDisplay.render(stats).get(4));
    }
}
