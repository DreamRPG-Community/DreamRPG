package cn.mythicland.dreamrpgcombat.autoclick;

import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.entity.v1_12_R1.LegacyMeleeAttack;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class AutoClickerServiceTest {

    @Test
    void doublePressTogglesAndTwentyTicksProduceSixteenClicks() {
        var fixture = fixture();
        try (var bukkit = mockStatic(Bukkit.class); var attacks = mockStatic(LegacyMeleeAttack.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.player().getUniqueId())).thenReturn(fixture.player());
            fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));
            fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), false));
            fixture.service().tick();
            attacks.verifyNoInteractions();
            fixture.time().set(600_000_000L);
            fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));
            fixture.service().tick();
            attacks.verifyNoInteractions();
            fixture.time().set(700_000_000L);
            fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));
            for (int tick = 0; tick < 20; tick++) fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.swing(fixture.player()), times(16));
            verify(fixture.player()).sendMessage("§a自动点击已开启。");
            pressTwice(fixture);
            for (int tick = 0; tick < 20; tick++) fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.swing(fixture.player()), times(16));
            verify(fixture.player()).sendMessage("§c自动点击已关闭。");
        }
    }

    @Test
    void rejectsPlayersAndOrdinaryMobsAndCancelsCollateralOnlyDuringAutomaticAttacks() {
        var fixture = fixture();
        var mm = mock(LivingEntity.class);
        var ordinary = mock(LivingEntity.class);
        var otherPlayer = mock(Player.class);
        when(mm.isValid()).thenReturn(true);
        when(ordinary.isValid()).thenReturn(true);
        when(fixture.mythicMobs().isMythicMob(mm)).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class); var attacks = mockStatic(LegacyMeleeAttack.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.player().getUniqueId())).thenReturn(fixture.player());
            attacks.when(() -> LegacyMeleeAttack.target(eq(fixture.player()), eq(3.0), any()))
                    .thenAnswer(call -> {
                        Predicate<LivingEntity> allowed = call.getArgument(2);
                        assertFalse(allowed.test(otherPlayer));
                        assertFalse(allowed.test(ordinary));
                        assertTrue(allowed.test(mm));
                        return mm;
                    });
            attacks.when(() -> LegacyMeleeAttack.attack(fixture.player(), mm)).thenAnswer(call -> {
                assertTrue(damage(fixture, otherPlayer).isCancelled());
                assertTrue(damage(fixture, ordinary).isCancelled());
                assertFalse(damage(fixture, mm).isCancelled());
                fixture.service().onQuit(new PlayerQuitEvent(fixture.player(), ""));
                return null;
            });
            pressTwice(fixture);
            fixture.service().tick();
            fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.attack(fixture.player(), mm));
            assertFalse(damage(fixture, otherPlayer).isCancelled());
            fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.attack(fixture.player(), mm));
        }
    }

    @Test
    void pausesUnsupportedItemsAndClearsStateForSpectatorsAndUnload() {
        var fixture = fixture();
        try (var bukkit = mockStatic(Bukkit.class); var attacks = mockStatic(LegacyMeleeAttack.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.player().getUniqueId())).thenReturn(fixture.player());
            pressTwice(fixture);
            when(fixture.player().getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.BOW));
            for (int tick = 0; tick < 20; tick++) fixture.service().tick();
            attacks.verifyNoInteractions();
            when(fixture.player().getInventory().getItemInMainHand()).thenReturn(new ItemStack(Material.WOOD_SWORD));
            fixture.service().tick();
            fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.swing(fixture.player()));
            fixture.service().onGameMode(new PlayerGameModeChangeEvent(fixture.player(), GameMode.SPECTATOR));
            for (int tick = 0; tick < 20; tick++) fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.swing(fixture.player()));
            pressTwice(fixture);
            fixture.service().close();
            for (int tick = 0; tick < 20; tick++) fixture.service().tick();
            attacks.verify(() -> LegacyMeleeAttack.swing(fixture.player()));
        }
    }

    @Test
    void cannotEnableWhenMythicMobsIsUnavailable() {
        var fixture = fixture();
        when(fixture.mythicMobs().isAvailable()).thenReturn(false);
        pressTwice(fixture);
        verify(fixture.player(), never()).sendMessage(anyString());
    }

    private static EntityDamageByEntityEvent damage(Fixture fixture, LivingEntity target) {
        var event = new EntityDamageByEntityEvent(fixture.player(), target,
                EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, 1);
        fixture.service().onAutomaticDamage(event);
        return event;
    }

    private static void pressTwice(Fixture fixture) {
        fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));
        fixture.time().addAndGet(100_000_000L);
        fixture.service().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));
    }

    private static Fixture fixture() {
        var player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.WOOD_SWORD));
        var mythicMobs = mock(MythicMobsAdapter.class);
        when(mythicMobs.isAvailable()).thenReturn(true);
        var time = new AtomicLong();
        return new Fixture(player, mythicMobs, new AutoClickerService(mythicMobs, time::get), time);
    }

    private record Fixture(
            Player player,
            MythicMobsAdapter mythicMobs,
            AutoClickerService service,
            AtomicLong time
    ) {
    }
}
