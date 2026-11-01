package cn.mythicland.dreamrpg.compat;

import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobSpawnEvent;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import io.lumine.xikage.mythicmobs.drops.DropTable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MythicMobEquipmentCompatibilityTest {

    @Test
    void clearsVanillaEquipmentImmediatelyWhenMythicMobsRequestsSuppression() {
        var event = mock(MythicMobSpawnEvent.class);
        var type = mock(MythicMob.class);
        var target = mock(LivingEntity.class);
        var equipment = mock(EntityEquipment.class);
        when(event.getMobType()).thenReturn(type);
        when(type.getPreventRandomEquipment()).thenReturn(true);
        when(type.getEquipmentTable()).thenReturn(mock(DropTable.class));
        when(event.getEntity()).thenReturn(target);
        when(target.getEquipment()).thenReturn(equipment);

        new MythicMobEquipmentCompatibility(mock(PluginTaskScope.class)).onSpawn(event);

        verify(equipment).clear();
    }

    @Test
    void leavesEquipmentToMythicMobsWhenSuppressionIsDisabled() {
        var event = mock(MythicMobSpawnEvent.class);
        var type = mock(MythicMob.class);
        when(event.getMobType()).thenReturn(type);
        when(type.getPreventRandomEquipment()).thenReturn(false);

        new MythicMobEquipmentCompatibility(mock(PluginTaskScope.class)).onSpawn(event);

        verify(event, never()).getEntity();
    }

    @Test
    void ignoresCancelledSpawns() {
        var event = mock(MythicMobSpawnEvent.class);
        when(event.isCancelled()).thenReturn(true);

        new MythicMobEquipmentCompatibility(mock(PluginTaskScope.class)).onSpawn(event);

        verify(event, never()).getMobType();
        verify(event, never()).getEntity();
    }
}
