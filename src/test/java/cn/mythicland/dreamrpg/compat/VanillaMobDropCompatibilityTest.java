package cn.mythicland.dreamrpg.compat;

import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.BukkitAPIHelper;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VanillaMobDropCompatibilityTest {

    @Test
    void clearsLootAddedLaterForAnUnmanagedCreature() {
        verifyDeathDrops(false);
    }

    @Test
    void keepsConfiguredLootAfterMythicMobsUnregistersTheDyingEntity() {
        verifyDeathDrops(true);
    }

    private void verifyDeathDrops(boolean ownedByMythicMobs) {
        Chicken chicken = mock(Chicken.class);
        when(chicken.getUniqueId()).thenReturn(UUID.randomUUID());
        MythicMobs mythic = mock(MythicMobs.class);
        BukkitAPIHelper helper = mock(BukkitAPIHelper.class);
        when(mythic.getAPIHelper()).thenReturn(helper);
        when(helper.isMythicMob(chicken)).thenReturn(ownedByMythicMobs);
        List<ItemStack> drops = new ArrayList<>();
        EntityDeathEvent event = new EntityDeathEvent(chicken, drops, 5);
        VanillaMobDropCompatibility listener = new VanillaMobDropCompatibility();
        try (var instance = mockStatic(MythicMobs.class)) {
            instance.when(MythicMobs::inst).thenReturn(mythic);
            listener.onEntityDeath(event);
            drops.add(new ItemStack(Material.DIAMOND));
            event.setDroppedExp(10);
            when(helper.isMythicMob(chicken)).thenReturn(false);
            listener.onDeathComplete(event);
            assertEquals(ownedByMythicMobs ? 1 : 0, drops.size());
            assertEquals(ownedByMythicMobs ? 10 : 0, event.getDroppedExp());
        }
    }
}
