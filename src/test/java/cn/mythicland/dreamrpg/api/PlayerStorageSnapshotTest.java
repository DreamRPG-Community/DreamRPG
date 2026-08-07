package cn.mythicland.dreamrpg.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that the player storage contract keeps the 54-slot custom chest immutable.
 */
class PlayerStorageSnapshotTest {

    @Test
    void emptyStorageCreatesOneSixRowEnderChestPage() {
        PlayerStorageSnapshot snapshot = PlayerStorageSnapshot.empty(UUID.randomUUID());

        assertEquals(PlayerStorageSnapshot.INVENTORY_SIZE, snapshot.inventory().length);
        assertEquals(PlayerStorageSnapshot.ARMOR_SIZE, snapshot.armor().length);
        assertEquals(PlayerStorageSnapshot.ENDER_CHEST_SIZE, snapshot.enderChest().length);
        assertEquals(0, snapshot.enderChestPage());
        assertEquals(0L, snapshot.databaseVersion());
    }

    @Test
    void returnedItemsCannotMutateThePersistedSnapshot() {
        PlayerStorageSnapshot snapshot = PlayerStorageSnapshot.empty(UUID.randomUUID());
        ItemStack[] contents = snapshot.enderChest();
        contents[0] = new ItemStack(Material.STONE, 3);

        assertNull(snapshot.enderChest()[0]);

        PlayerStorageSnapshot updated = snapshot.withEnderChest(contents);
        contents[0].setAmount(64);

        assertEquals(3, updated.enderChest()[0].getAmount());
    }
}
