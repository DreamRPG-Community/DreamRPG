package cn.mythicland.dreamrpg.compat;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZombieReinforcementCompatibilityTest {

    @Test
    void cancelsReinforcementZombieSpawns() {
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);

        new ZombieReinforcementCompatibility().onCreatureSpawn(event);

        verify(event).setCancelled(true);
    }

    @Test
    void clearsLeaderReinforcementChanceFromEveryZombieSpawn() {
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
        Zombie zombie = mock(Zombie.class);
        AttributeInstance reinforcementChance = mock(AttributeInstance.class);
        when(event.getEntity()).thenReturn(zombie);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
        when(zombie.getAttribute(Attribute.ZOMBIE_SPAWN_REINFORCEMENTS)).thenReturn(reinforcementChance);

        new ZombieReinforcementCompatibility().onCreatureSpawn(event);

        verify(reinforcementChance).setBaseValue(0);
    }
}
