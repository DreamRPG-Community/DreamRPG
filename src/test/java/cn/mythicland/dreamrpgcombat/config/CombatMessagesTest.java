package cn.mythicland.dreamrpgcombat.config;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamrpgcombat.lore.CombatStatType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatMessagesTest {

    @Test
    void rendersConfiguredTriggeredMessages() {
        CombatMessages messages = new CombatMessages(
                "&6暴击 &f{damage}",
                "&a回复 &f{amount}"
        );

        assertEquals("§6暴击 §f12.5", messages.criticalHit(12.5D));
        assertEquals("§a回复 §f2", messages.healthRegen(2.0D));
    }

    @Test
    void classifiesPermanentAndTriggeredAttributes() {
        assertEquals(CombatStatType.PERMANENT, CombatStat.DAMAGE.type());
        assertEquals(CombatStatType.PERMANENT, CombatStat.DEFENSE.type());
        assertEquals(CombatStatType.PERMANENT, CombatStat.HEALTH.type());
        assertEquals(CombatStatType.TRIGGERED, CombatStat.HEALTH_REGEN.type());
        assertEquals(CombatStatType.TRIGGERED, CombatStat.CRIT_CHANCE.type());
        assertEquals(CombatStatType.TRIGGERED, CombatStat.CRIT_DAMAGE.type());
    }
}
