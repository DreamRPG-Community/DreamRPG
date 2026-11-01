package cn.mythicland.dreamrpg.compat;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.BukkitAPIHelper;
import io.lumine.xikage.mythicmobs.mobs.ActiveMob;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MythicChickenCompatibilityTest {

    @Test
    void cancelsVanillaEggsSpawnedBesideTheConfiguredRpgChicken() {
        MythicMobs mythicMobs = mock(MythicMobs.class);
        BukkitAPIHelper apiHelper = mock(BukkitAPIHelper.class);
        ActiveMob activeMob = activeWhiteChicken();
        Chicken chicken = mock(Chicken.class);
        Item egg = mock(Item.class);
        ItemSpawnEvent event = mock(ItemSpawnEvent.class);
        when(mythicMobs.getAPIHelper()).thenReturn(apiHelper);
        when(apiHelper.getMythicMobInstance(chicken)).thenReturn(activeMob);
        when(event.getEntity()).thenReturn(egg);
        when(egg.getItemStack()).thenReturn(new ItemStack(Material.EGG));
        when(egg.getNearbyEntities(1, 1, 1)).thenReturn(List.of(chicken));

        try (MockedStatic<MythicMobs> mythicMobsInstance = mockStatic(MythicMobs.class)) {
            mythicMobsInstance.when(MythicMobs::inst).thenReturn(mythicMobs);

            new MythicChickenCompatibility().onItemSpawn(event);

            verify(event).setCancelled(true);
        }
    }

    @Test
    void cancelsBreedingWhenEitherParentIsTheConfiguredRpgChicken() {
        MythicMobs mythicMobs = mock(MythicMobs.class);
        BukkitAPIHelper apiHelper = mock(BukkitAPIHelper.class);
        ActiveMob activeMob = activeWhiteChicken();
        Chicken mother = mock(Chicken.class);
        LivingEntity father = mock(LivingEntity.class);
        EntityBreedEvent event = mock(EntityBreedEvent.class);
        when(mythicMobs.getAPIHelper()).thenReturn(apiHelper);
        when(apiHelper.getMythicMobInstance(mother)).thenReturn(activeMob);
        when(apiHelper.getMythicMobInstance(father)).thenReturn(null);
        when(event.getMother()).thenReturn(mother);
        when(event.getFather()).thenReturn(father);

        try (MockedStatic<MythicMobs> mythicMobsInstance = mockStatic(MythicMobs.class)) {
            mythicMobsInstance.when(MythicMobs::inst).thenReturn(mythicMobs);

            new MythicChickenCompatibility().onBreed(event);

            verify(event).setCancelled(true);
        }
    }

    private static ActiveMob activeWhiteChicken() {
        ActiveMob activeMob = mock(ActiveMob.class);
        MythicMob mobType = mock(MythicMob.class);
        when(activeMob.getType()).thenReturn(mobType);
        when(mobType.getInternalName()).thenReturn("main_village_white_chicken");
        return activeMob;
    }
}
