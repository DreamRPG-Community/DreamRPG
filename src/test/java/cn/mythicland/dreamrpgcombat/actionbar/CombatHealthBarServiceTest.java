package cn.mythicland.dreamrpgcombat.actionbar;

import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.entity.EntityAbsorption;
import cn.mythicland.framework.bukkit.text.v1_12_R1.LegacyActionBar;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatHealthBarServiceTest {

    @Test
    void synchronizesCommittedDamageRegenerationAndMaximumHealthWithoutExtendingExpiry() {
        var now = new AtomicLong();
        var service = new CombatHealthBarService(now::get, mock(MythicMobsAdapter.class));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        var maximum = mock(AttributeInstance.class);
        when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(maximum);
        when(maximum.getValue()).thenReturn(215.0);
        when(player.getHealth()).thenReturn(215.0);
        try (var absorption = mockStatic(EntityAbsorption.class); var bars = mockStatic(LegacyActionBar.class)) {
            service.recordIncomingHit(player, 5, 0, CombatDamageType.PHYSICAL);
            assertEquals(210, service.currentStatus(player).remainingHealth());
            when(player.getHealth()).thenReturn(212.5);
            service.refresh(player);
            assertEquals(212.5, service.currentStatus(player).remainingHealth());
            assertEquals(2.5, service.currentStatus(player).healthDamage());
            when(player.getHealth()).thenReturn(215.0);
            service.refresh(player);
            assertEquals(215, service.currentStatus(player).remainingHealth());
            assertEquals(0, service.currentStatus(player).healthDamage());
            when(maximum.getValue()).thenReturn(220.0);
            service.refresh(player);
            assertEquals(220, service.currentStatus(player).maxHealth());
            now.set(2000);
            service.refresh(player);
            bars.verify(() -> LegacyActionBar.send(player, ""));
            assertNull(service.currentStatus(player), "Refreshing live health must not keep an expired bar alive");
        }
    }

    @Test
    void separatesAbsorbedDamageFromHealthDamageAndRetainsTheShieldScaleUntilExpiry() {
        var now = new AtomicLong();
        var service = new CombatHealthBarService(now::get, mock(MythicMobsAdapter.class));
        var attacker = mock(Player.class);
        var target = mock(LivingEntity.class);
        var maximum = mock(AttributeInstance.class);
        when(attacker.isOnline()).thenReturn(true);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.getName()).thenReturn("Boss");
        when(target.getHealth()).thenReturn(100.0);
        when(target.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(maximum);
        when(maximum.getValue()).thenReturn(100.0);
        try (var absorption = mockStatic(EntityAbsorption.class); var actionBar = mockStatic(LegacyActionBar.class)) {
            absorption.when(() -> EntityAbsorption.get(target)).thenReturn(100.0, 50.0, 50.0, 50.0);
            service.recordHit(attacker, target, 0, 50, CombatDamageType.MAGIC);
            actionBar.verify(() -> LegacyActionBar.send(attacker,
                    "Boss &7(&a100.00&7/&c100.00&7) &4" + "❤".repeat(10) + " &e" + "❤".repeat(5) + " &d+50.00"));
            var shieldOnly = service.currentStatus(attacker);
            assertEquals(100, shieldOnly.remainingHealth());
            assertEquals(50, shieldOnly.remainingAbsorption());
            assertEquals(50, shieldOnly.finalDamage());
            assertEquals(100, shieldOnly.maxAbsorption());
            assertEquals(CombatDamageType.MAGIC, shieldOnly.damageType());

            service.recordHit(attacker, target, 10, 50, CombatDamageType.TRUE);
            var shieldBreak = service.currentStatus(attacker);
            assertEquals(90, shieldBreak.remainingHealth());
            assertEquals(0, shieldBreak.remainingAbsorption());
            assertEquals(10, shieldBreak.healthDamage());
            assertEquals(60, shieldBreak.finalDamage());
            assertEquals(100, shieldBreak.maxAbsorption());

            now.set(2000);
            assertNull(service.currentStatus(attacker));
            service.recordHit(attacker, target, 1, 0, CombatDamageType.PHYSICAL);
            assertEquals(50, service.currentStatus(attacker).maxAbsorption());
            when(target.getUniqueId()).thenReturn(UUID.randomUUID());
            absorption.when(() -> EntityAbsorption.get(target)).thenReturn(4.0);
            service.recordHit(attacker, target, 1, 0, CombatDamageType.PHYSICAL);
            assertEquals(4, service.currentStatus(attacker).maxAbsorption());
            service.clear(attacker);
            assertNull(service.currentStatus(attacker));
        }
    }
}
