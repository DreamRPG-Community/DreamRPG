package cn.mythicland.dreamrpg.health;

import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoodRegenerationListenerTest {
    @Test
    void preservesHealingSpeedAndHalfHeartStepsWhileDoublingSaturationCost() {
        var fixture = new Fixture();
        var plugins = mock(PluginManager.class);
        doAnswer(call -> {
            fixture.listener.onFoodRegeneration(call.getArgument(0));
            return null;
        }).when(plugins).callEvent(any(EntityRegainHealthEvent.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            for (boolean fast : new boolean[]{true, false}) {
                for (double maximumHealth : new double[]{10, 20, 200, 2048}) {
                    fixture.maximumHealth = maximumHealth;
                    fixture.health = maximumHealth / 2;
                    fixture.exhaustion = 0;
                    double before = fixture.health;
                    var event = new EntityRegainHealthEvent(fixture.player, 1,
                            EntityRegainHealthEvent.RegainReason.SATIATED, fast);
                    fixture.listener.onFoodRegeneration(event);
                    assertEquals(maximumHealth / 40, event.getAmount(), 1e-9);
                    fixture.health += event.getAmount();
                    assertEquals(fast ? 5L : 40L, fixture.delays.remove(0));
                    fixture.actions.remove(0).run();
                    assertEquals(maximumHealth / 20, fixture.health - before, 1e-9,
                            "Both food regeneration paths must retain their previous healing rate");
                    assertEquals(fast ? 6 : 0, fixture.exhaustion, 1e-6,
                            "Add exactly one native cost for the original fast event, never its deferred half");
                }
            }
            fixture.maximumHealth = 200;
            var small = new EntityRegainHealthEvent(fixture.player, 0.5,
                    EntityRegainHealthEvent.RegainReason.SATIATED);
            fixture.listener.onFoodRegeneration(small);
            assertEquals(5, small.getAmount());
            assertTrue(fixture.actions.isEmpty(), "A half-heart event already needs no splitting");
            var potion = new EntityRegainHealthEvent(fixture.player, 1,
                    EntityRegainHealthEvent.RegainReason.MAGIC_REGEN);
            fixture.listener.onFoodRegeneration(potion);
            assertEquals(1, potion.getAmount());
            verify(fixture.player, never()).setFoodLevel(anyInt());
            verify(fixture.player, never()).setSaturation(anyFloat());
        }
    }

    @Test
    void doublesPartialSaturationCostAndRespectsNativeExhaustionLimit() {
        var fixture = new Fixture();
        for (double nativeAmount : new double[]{1, 0.5, 1.0 / 6}) {
            fixture.exhaustion = 2;
            var event = new EntityRegainHealthEvent(fixture.player, nativeAmount,
                    EntityRegainHealthEvent.RegainReason.SATIATED, true);
            fixture.listener.onFoodRegeneration(event);
            assertEquals(2 + nativeAmount * 6, fixture.exhaustion, 1e-6);
        }
        fixture.exhaustion = 39;
        fixture.listener.onFoodRegeneration(new EntityRegainHealthEvent(fixture.player, 1,
                EntityRegainHealthEvent.RegainReason.SATIATED, true));
        assertEquals(40, fixture.exhaustion);
        verify(fixture.player, never()).setFoodLevel(anyInt());
        verify(fixture.player, never()).setSaturation(anyFloat());
    }

    @Test
    void cancelsDeferredHealingOnCancellationQuitAndUnload() {
        var fixture = new Fixture();
        var cancelled = fixture.foodEvent();
        fixture.listener.onFoodRegeneration(cancelled);
        cancelled.setCancelled(true);
        fixture.actions.remove(0).run();
        verify(fixture.player, never()).setHealth(anyDouble());
        fixture.listener.onFoodRegeneration(fixture.foodEvent());
        fixture.listener.onQuit(new PlayerQuitEvent(fixture.player, "quit"));
        fixture.actions.remove(0).run();
        verify(fixture.tasks).cancel(fixture.task);
        verify(fixture.player, never()).setHealth(anyDouble());
        fixture.listener.onFoodRegeneration(fixture.foodEvent());
        fixture.listener.unload();
        fixture.actions.remove(0).run();
        verify(fixture.tasks, times(2)).cancel(fixture.task);
        verify(fixture.player, never()).setHealth(anyDouble());
    }

    private static final class Fixture {
        private final Player player = mock(Player.class);
        private final PluginTaskScope tasks = mock(PluginTaskScope.class);
        private final BukkitTask task = mock(BukkitTask.class);
        private final List<Runnable> actions = new ArrayList<>();
        private final List<Long> delays = new ArrayList<>();
        private final FoodRegenerationListener listener = new FoodRegenerationListener(tasks);
        private double maximumHealth = 200;
        private double health = 100;
        private float exhaustion;

        private Fixture() {
            var maximum = mock(AttributeInstance.class);
            when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(maximum);
            when(maximum.getValue()).thenAnswer(ignored -> maximumHealth);
            when(player.isHealthScaled()).thenReturn(true);
            when(player.getHealthScale()).thenReturn(40.0);
            when(player.getHealth()).thenAnswer(ignored -> health);
            when(player.getExhaustion()).thenAnswer(ignored -> exhaustion);
            doAnswer(call -> { exhaustion = call.getArgument(0); return null; }).when(player).setExhaustion(anyFloat());
            when(player.getWorld()).thenReturn(mock(World.class));
            when(player.isOnline()).thenReturn(true);
            when(player.isValid()).thenReturn(true);
            doAnswer(call -> { health = call.getArgument(0); return null; }).when(player).setHealth(anyDouble());
            when(tasks.runLater(anyLong(), any())).thenAnswer(call -> {
                delays.add(call.getArgument(0));
                actions.add(call.getArgument(1));
                return task;
            });
        }

        private EntityRegainHealthEvent foodEvent() {
            return new EntityRegainHealthEvent(player, 1, EntityRegainHealthEvent.RegainReason.SATIATED, false);
        }
    }
}
