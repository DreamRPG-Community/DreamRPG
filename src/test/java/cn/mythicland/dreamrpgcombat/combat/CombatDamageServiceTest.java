package cn.mythicland.dreamrpgcombat.combat;

import cn.mythicland.dreamrpgcombat.api.CombatDamageSpec;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.framework.bukkit.item.NumericRange;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class CombatDamageServiceTest {

    @Test
    void combinesExplicitSkillDamageAndAttackCoefficientsExactlyOnce() {
        var source = mock(LivingEntity.class);
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        when(stats.getStats(source)).thenReturn(CombatStatsSnapshot.builder()
                .hasDamage(true).damageMinimum(10).damageMaximum(10)
                .attribute(CombatStat.MAGIC_DAMAGE, new NumericRange(20, 20, false)).build());
        var service = new CombatDamageService(stats);
        var spec = new CombatDamageSpec(CombatDamageType.MAGIC, 5, 2, 3, false, true);
        doAnswer(call -> {
            double amount = call.getArgument(0);
            assertEquals(85, amount);
            var event = new EntityDamageByEntityEvent(source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, amount);
            assertEquals(spec, service.claim(event).specification());
            assertNull(service.claim(event));
            return null;
        }).when(target).damage(anyDouble(), eq(source));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            service.damage(source, target, spec);
        }
        verify(target).damage(85, source);
    }

    @Test
    void clearsSkillContextWhenTheServerRejectsDamage() {
        var source = mock(LivingEntity.class);
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        when(stats.getStats(source)).thenReturn(CombatStatsSnapshot.empty());
        var service = new CombatDamageService(stats);
        doThrow(new IllegalStateException("rejected")).when(target).damage(anyDouble(), eq(source));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> service.damage(source, target,
                    new CombatDamageSpec(CombatDamageType.PHYSICAL, 10, 0, 0, false, false)));
        }
        assertNull(service.claim(new EntityDamageByEntityEvent(source, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 10)));
    }

    @Test
    void rejectsAsyncAndInvalidSkillRequests() {
        var service = new CombatDamageService(mock(CombatStatsService.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> service.damage(mock(LivingEntity.class),
                    mock(LivingEntity.class), new CombatDamageSpec(CombatDamageType.MAGIC, 1, 0, 0, false, false)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDamageSpec(CombatDamageType.MAGIC, Double.NaN, 0, 0, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatDamageSpec(CombatDamageType.PHYSICAL, 1, -1, 0, false, false));
    }
}
