package cn.mythicland.dreamrpgcombat.listener;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgcombat.actionbar.CombatHealthBarService;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.api.CombatDamageSpec;
import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import org.bukkit.Bukkit;
import cn.mythicland.dreamrpgcombat.combat.CombatDamageService;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;

import java.util.Map;
import java.util.EnumMap;

import com.google.common.base.Function;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class CombatListenerTest {

    @Test
    void restoresExplicitSkillAmountAfterAnEarlierListenerRewritesTheBase() {
        var source = player();
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        when(stats.getStats((LivingEntity) source)).thenReturn(attack(100));
        when(stats.getStats(target)).thenReturn(CombatStatsSnapshot.builder()
                .attribute(CombatStat.PHYSICAL_DEFENSE, new NumericRange(600, 600, false)).build());
        var service = new CombatDamageService(stats);
        var listener = new CombatListener(plugin(), mock(PluginTaskScope.class), stats,
                mock(CombatHealthBarService.class), service, mock(MythicMobsAdapter.class));
        doAnswer(call -> {
            var event = new EntityDamageByEntityEvent(source, target,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK, 2);
            listener.onEntityDamage(event);
            assertEquals(500, event.getFinalDamage(), 1e-9);
            return null;
        }).when(target).damage(anyDouble(), eq(source));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            service.damage(source, target,
                    CombatDamageSpec.fixed(
                            CombatDamageType.PHYSICAL, 1000));
        }
        listener.close();
    }

    @Test
    void forwardsDamageTypesAndShieldOnlyHitsToTheHealthBar() {
        var source = player();
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        when(stats.getStats(any(LivingEntity.class))).thenReturn(CombatStatsSnapshot.empty());
        var bars = mock(CombatHealthBarService.class);
        var service = new CombatDamageService(stats);
        var listener = new CombatListener(plugin(), mock(PluginTaskScope.class), stats,
                bars, service, mock(MythicMobsAdapter.class));
        for (CombatDamageType type : CombatDamageType.values()) {
            Map<EntityDamageEvent.DamageModifier, Double> values = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
            values.put(EntityDamageEvent.DamageModifier.BASE, 10.0);
            values.put(EntityDamageEvent.DamageModifier.ARMOR, 0.0);
            values.put(EntityDamageEvent.DamageModifier.RESISTANCE, -5.0);
            values.put(EntityDamageEvent.DamageModifier.ABSORPTION, -5.0);
            Map<EntityDamageEvent.DamageModifier, Function<Double, Double>> functions =
                    new EnumMap<>(EntityDamageEvent.DamageModifier.class);
            functions.put(EntityDamageEvent.DamageModifier.BASE, incoming -> 0.0);
            functions.put(EntityDamageEvent.DamageModifier.ARMOR, incoming -> 0.0);
            functions.put(EntityDamageEvent.DamageModifier.RESISTANCE, incoming -> -incoming * 0.5);
            functions.put(EntityDamageEvent.DamageModifier.ABSORPTION, incoming -> -Math.min(20, incoming));
            doAnswer(call -> {
                var event = new EntityDamageByEntityEvent(source, target,
                        EntityDamageEvent.DamageCause.ENTITY_ATTACK, values, functions);
                listener.onEntityDamage(event);
                listener.onFinalDamage(event);
                verify(bars).recordHit(source, target, 0, type == CombatDamageType.TRUE ? 10 : 5, type);
                event.setCancelled(true);
                listener.onFinalDamage(event);
                return null;
            }).when(target).damage(anyDouble(), eq(source));
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                service.damage(source, target, CombatDamageSpec.fixed(type, 10));
            }
        }
        verifyNoMoreInteractions(bars);
        listener.close();
    }

    @Test
    void ignoresMaterialDamageArmorAndProtectionEnchantments() {
        var source = player();
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        when(stats.getStats((LivingEntity) source)).thenReturn(attack(1));
        when(stats.getStats(target)).thenReturn(CombatStatsSnapshot.empty());
        var listener = new CombatListener(plugin(), mock(PluginTaskScope.class), stats,
                mock(CombatHealthBarService.class), new CombatDamageService(stats), mock(MythicMobsAdapter.class));
        var base = EntityDamageEvent.DamageModifier.BASE;
        var armor = EntityDamageEvent.DamageModifier.ARMOR;
        var protection = EntityDamageEvent.DamageModifier.MAGIC;
        Map<EntityDamageEvent.DamageModifier, Double> values = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        values.put(base, 10.0);
        values.put(armor, -8.0);
        values.put(protection, -1.0);
        Map<EntityDamageEvent.DamageModifier, Function<Double, Double>> functions =
                new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(base, incoming -> 0.0);
        functions.put(armor, incoming -> -incoming * 0.8);
        functions.put(protection, incoming -> -incoming * 0.5);
        var event = new EntityDamageByEntityEvent(source, target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, values, functions);
        listener.onEntityDamage(event);
        assertEquals(1, event.getFinalDamage(), 1e-9);
        assertEquals(0, event.getDamage(armor), 1e-9);
        assertEquals(0, event.getDamage(protection), 1e-9);
    }

    @Test
    void projectileRetainsLaunchAttributesAfterTheShooterChangesWeapons() {
        var source = player();
        var target = mock(LivingEntity.class);
        var arrow = mock(Arrow.class);
        var stats = mock(CombatStatsService.class);
        when(source.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(source);
        when(stats.getStats((LivingEntity) source)).thenReturn(attack(10), attack(1000));
        when(stats.getStats(target)).thenReturn(CombatStatsSnapshot.empty());
        var listener = new CombatListener(plugin(), mock(PluginTaskScope.class), stats,
                mock(CombatHealthBarService.class), new CombatDamageService(stats), mock(MythicMobsAdapter.class));
        listener.onProjectileLaunch(new ProjectileLaunchEvent(arrow));
        var event = new EntityDamageByEntityEvent(arrow, target, EntityDamageEvent.DamageCause.PROJECTILE, 2);
        listener.onEntityDamage(event);
        assertEquals(10, event.getFinalDamage());
    }

    @Test
    void lifestealExcludesOverkillAndHonorsLateCancellation() {
        var source = player();
        var target = mock(LivingEntity.class);
        var stats = mock(CombatStatsService.class);
        var tasks = mock(PluginTaskScope.class);
        var maximum = mock(AttributeInstance.class);
        when(stats.getStats((LivingEntity) source)).thenReturn(CombatStatsSnapshot.builder()
                .hasDamage(true).damageMinimum(100).damageMaximum(100)
                .attribute(CombatStat.LIFESTEAL, new NumericRange(10, 10, true)).build());
        when(stats.getStats(target)).thenReturn(CombatStatsSnapshot.empty());
        when(source.isValid()).thenReturn(true);
        when(source.getHealth()).thenReturn(50.0);
        when(source.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(maximum);
        when(maximum.getValue()).thenReturn(100.0);
        when(target.getHealth()).thenReturn(20.0);
        AtomicReference<Runnable> callback = new AtomicReference<>();
        doAnswer(call -> {
            callback.set(call.getArgument(1));
            return null;
        }).when(tasks).runLater(eq(1L), any(Runnable.class));
        var listener = new CombatListener(plugin(), tasks, stats,
                mock(CombatHealthBarService.class), new CombatDamageService(stats), mock(MythicMobsAdapter.class));
        var accepted = new EntityDamageByEntityEvent(source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
        listener.onEntityDamage(accepted);
        listener.onFinalDamage(accepted);
        callback.get().run();
        verify(source).setHealth(52);

        var cancelled = new EntityDamageByEntityEvent(source, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1);
        listener.onEntityDamage(cancelled);
        listener.onFinalDamage(cancelled);
        cancelled.setCancelled(true);
        callback.get().run();
        verify(source, times(1)).setHealth(anyDouble());
    }

    private static DreamRPGPlugin plugin() {
        var plugin = mock(DreamRPGPlugin.class);
        when(plugin.combatSettings()).thenReturn(new CombatSettings(false, Map.of()));
        return plugin;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }

    private static CombatStatsSnapshot attack(double damage) {
        return CombatStatsSnapshot.builder().hasDamage(true).damageMinimum(damage).damageMaximum(damage).build();
    }
}
