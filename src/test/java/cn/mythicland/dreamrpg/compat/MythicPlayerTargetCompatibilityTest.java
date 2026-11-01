package cn.mythicland.dreamrpg.compat;

import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.adapters.AbstractPlayer;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import io.lumine.xikage.mythicmobs.mobs.MobManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class MythicPlayerTargetCompatibilityTest {
    @Test
    void rebindsOnlyTheDisconnectedSessionAndRemovesQuitTargets() {
        UUID id = UUID.randomUUID();
        var player = mock(Player.class);
        var oldPlayer = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        var staleTarget = mock(AbstractEntity.class);
        when(staleTarget.getUniqueId()).thenReturn(id);
        when(staleTarget.getBukkitEntity()).thenReturn(oldPlayer);
        var currentTarget = mock(AbstractPlayer.class);
        when(currentTarget.getUniqueId()).thenReturn(id);
        when(currentTarget.getBukkitEntity()).thenReturn(player);
        var mob = mock(ActiveMob.class);
        var entity = mock(AbstractEntity.class);
        when(mob.getEntity()).thenReturn(entity);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        var creature = mock(Creature.class);
        var world = mock(World.class);
        var location = new Location(world, 0, 70, 0);
        when(entity.getBukkitEntity()).thenReturn(creature);
        when(creature.getWorld()).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(creature.getLocation()).thenReturn(location);
        when(player.getLocation()).thenReturn(location);
        var range = mock(AttributeInstance.class);
        when(creature.getAttribute(Attribute.GENERIC_FOLLOW_RANGE)).thenReturn(range);
        when(range.getValue()).thenReturn(16.0);
        when(entity.getTarget()).thenReturn(staleTarget);
        var mythic = mock(MythicMobs.class);
        var mobs = mock(MobManager.class);
        when(mythic.getMobManager()).thenReturn(mobs);
        when(mobs.getActiveMobs()).thenReturn(List.of(mob));
        var listener = new MythicPlayerTargetCompatibility();
        var event = mock(PlayerDataReadyEvent.class);
        when(event.uniqueId()).thenReturn(id);
        try (var bukkit = mockStatic(Bukkit.class); var api = mockStatic(MythicMobs.class);
             var adapter = mockStatic(BukkitAdapter.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            api.when(MythicMobs::inst).thenReturn(mythic);
            adapter.when(() -> BukkitAdapter.adapt(player)).thenReturn(currentTarget);
            when(mob.getNewTarget()).thenReturn(staleTarget);
            listener.onPlayerReady(event);
            verify(mob).voidTargetChange();
            when(mob.getNewTarget()).thenReturn(null);
            verify(mob).setTarget(currentTarget);
            when(entity.getTarget()).thenReturn(currentTarget);
            listener.onPlayerReady(event);
            verify(mob, times(1)).setTarget(any());
            var table = mock(ActiveMob.ThreatTable.class);
            when(mob.hasThreatTable()).thenReturn(true);
            when(mob.getThreatTable()).thenReturn(table);
            when(mob.getNewTarget()).thenReturn(currentTarget);
            listener.onQuit(new PlayerQuitEvent(player, "quit"));
            verify(mob, times(2)).voidTargetChange();
            verify(table).observeDeath(currentTarget);
            verify(mob).resetTarget();
            when(entity.getTarget()).thenReturn(currentTarget);
            when(currentTarget.getUniqueId()).thenReturn(UUID.randomUUID());
            listener.onPlayerReady(event);
            verify(mob, times(1)).setTarget(any());
        }
    }
}
