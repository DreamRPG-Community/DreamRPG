package cn.mythicland.dreamrpg.combat;

import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobKnockbackListenerTest {
    @Test
    void removesDownwardPushWhileSuppressingNativeMeleeAndProjectileKnockback() {
        var settings = mock(PlayerSettingsService.class);
        var adapter = mock(MythicMobsAdapter.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var mob = mock(LivingEntity.class);
        when(adapter.isMythicMob(mob)).thenReturn(true);
        try (var detection = mockStatic(MythicMobsAdapter.class)) {
            detection.when(MythicMobsAdapter::detect).thenReturn(adapter);
            var listener = new MobKnockbackListener(settings);
            Vector acceleration = new Vector(0.3, 0.4, 0.2);
            var melee = new EntityKnockbackByEntityEvent(player, mob, 0.4f, acceleration.clone());
            listener.onMythicMobKnockback(melee);
            assertTrue(melee.isCancelled());
            assertEquals(acceleration, melee.getAcceleration(), "No downward acceleration may be injected");
            var arrow = mock(Projectile.class);
            when(arrow.getShooter()).thenReturn(mob);
            var ranged = new EntityKnockbackByEntityEvent(player, arrow, 0.4f, acceleration.clone());
            listener.onMythicMobKnockback(ranged);
            assertTrue(ranged.isCancelled());
            verify(player, never()).getVelocity();
            verify(player, never()).setVelocity(any());
            when(settings.allowsMobKnockback(id)).thenReturn(true);
            var allowed = new EntityKnockbackByEntityEvent(player, mob, 0.4f, acceleration.clone());
            listener.onMythicMobKnockback(allowed);
            assertFalse(allowed.isCancelled());
            assertEquals(acceleration, allowed.getAcceleration());
        }
    }
}
