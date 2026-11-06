package cn.mythicland.dreamrpgcombat.stats;

import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TitleCombatIntegrationTest {
    @Test
    void contributesEveryStatToPlayersAndRemovesBonusesWhenUnequipped() {
        var bonuses = new EnumMap<CombatStat, NumericRange>(CombatStat.class);
        for (CombatStat stat : CombatStat.values())
            bonuses.put(stat, new NumericRange(5, 5, stat.percentageAttribute()));
        var title = new TitleDefinition("hero", "Hero", bonuses);
        var active = new AtomicReference<>(title.combatStats());
        var service = new CombatStatsService(mock(JavaPlugin.class), new CombatSettings(false, Map.of()),
                null, null, id -> active.get());
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var stats = service.getStats(player);
        for (CombatStat stat : CombatStat.values()) {
            assertEquals(5, stats.value(stat), stat.configKey());
        }
        assertEquals(0, service.getStats(mock(LivingEntity.class)).damageMaximum());
        active.set(new CombatItemStats(Map.of(), List.of()));
        assertEquals(0, service.getStats(player).health());
        assertEquals(0, service.getStats(player).damageMaximum());
    }
}
