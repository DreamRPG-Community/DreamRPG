package cn.mythicland.dreamrpg.combat;

import cn.mythicland.dreamrpg.settings.PlayerSettingsService;
import cn.mythicland.dreamrpgcombat.integration.mythicmobs.MythicMobsAdapter;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobRewardListenerTest {
    @Test
    void capsOverkillAndDistributesConfiguredDropsOnceWithOwnedOverflow() {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        var settings = mock(PlayerSettingsService.class);
        var adapter = mock(MythicMobsAdapter.class);
        var world = mock(World.class);
        var location = new Location(world, 5, 70, 5);
        var mob = mock(LivingEntity.class);
        when(mob.getUniqueId()).thenReturn(UUID.randomUUID());
        when(mob.getLocation()).thenReturn(location);
        when(adapter.isMythicMob(mob)).thenReturn(true);
        Player major = player(server, location);
        Player minor = player(server, location);
        when(settings.autoPicksUpRewards(major.getUniqueId())).thenReturn(true);
        when(major.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>(Map.of(0, new ItemStack(Material.DIAMOND, 8))));
        var groundItem = mock(Item.class);
        when(world.dropItem(eq(location), any(ItemStack.class))).thenReturn(groundItem);
        try (var detection = mockStatic(MythicMobsAdapter.class)) {
            detection.when(MythicMobsAdapter::detect).thenReturn(adapter);
            var listener = new MobRewardListener(plugin, settings, mock(PluginTaskScope.class));
            when(mob.getHealth()).thenReturn(100.0);
            listener.onDamage(hit(major, mob, 75));
            when(mob.getHealth()).thenReturn(25.0);
            listener.onDamage(hit(minor, mob, 10000));
            var drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 64)));
            var death = new EntityDeathEvent(mob, drops);
            listener.onDeath(death);
            assertTrue(drops.isEmpty());
            var inventoryReward = ArgumentCaptor.forClass(ItemStack.class);
            verify(major.getInventory()).addItem(inventoryReward.capture());
            assertEquals(48, inventoryReward.getValue().getAmount(), "Overkill must not outweigh actual health damage");
            var groundRewards = ArgumentCaptor.forClass(ItemStack.class);
            verify(world, times(2)).dropItem(eq(location), groundRewards.capture());
            assertEquals(List.of(8, 16), groundRewards.getAllValues().stream().map(ItemStack::getAmount).toList());
            var owners = ArgumentCaptor.forClass(MetadataValue.class);
            verify(groundItem, times(2)).setMetadata(anyString(), owners.capture());
            when(groundItem.getMetadata(anyString())).thenReturn(List.of(owners.getAllValues().get(0)));
            var theft = new EntityPickupItemEvent(minor, groundItem, 0);
            listener.onPickup(theft);
            assertTrue(theft.isCancelled());
            var ownPickup = new EntityPickupItemEvent(major, groundItem, 0);
            listener.onPickup(ownPickup);
            assertFalse(ownPickup.isCancelled());
            listener.onDeath(death);
            verify(major.getInventory(), times(1)).addItem(any(ItemStack.class));
            listener.unload();
        }
    }

    private static Player player(Server server, Location location) {
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(location);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(server.getPlayer(id)).thenReturn(player);
        return player;
    }

    private static EntityDamageByEntityEvent hit(Player player, LivingEntity mob, double damage) {
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(mob);
        when(event.getDamager()).thenReturn(player);
        when(event.getFinalDamage()).thenReturn(damage);
        return event;
    }
}
