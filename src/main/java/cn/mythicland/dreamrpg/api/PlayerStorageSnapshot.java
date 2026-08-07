package cn.mythicland.dreamrpg.api;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of the player inventory and custom ender chest.
 *
 * @param uniqueId        player UUID
 * @param inventory       36 native inventory slots
 * @param armor           four armor slots
 * @param offHand         off-hand item
 * @param heldSlot        selected hotbar slot
 * @param enderChest      54 custom ender-chest slots
 * @param enderChestPage  current future page index
 * @param formatVersion   storage format version
 * @param databaseVersion optimistic persistence version
 */
public record PlayerStorageSnapshot(
        UUID uniqueId,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack offHand,
        int heldSlot,
        ItemStack[] enderChest,
        int enderChestPage,
        int formatVersion,
        long databaseVersion
) {

    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final int INVENTORY_SIZE = 36;
    public static final int ARMOR_SIZE = 4;
    public static final int ENDER_CHEST_SIZE = 54;

    /**
     * Validates and detaches all mutable Bukkit item values.
     */
    public PlayerStorageSnapshot {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        inventory = copyItems(inventory, INVENTORY_SIZE, "inventory");
        armor = copyItems(armor, ARMOR_SIZE, "armor");
        offHand = copyItem(offHand);
        if (heldSlot < 0 || heldSlot > 8) throw new IllegalArgumentException("heldSlot must be between 0 and 8");
        enderChest = copyItems(enderChest, ENDER_CHEST_SIZE, "enderChest");
        if (enderChestPage < 0) throw new IllegalArgumentException("enderChestPage cannot be negative");
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported player storage format: " + formatVersion);
        }
        if (databaseVersion < 0L) throw new IllegalArgumentException("databaseVersion cannot be negative");
    }

    /**
     * Creates an empty first-login snapshot.
     *
     * @param uniqueId player UUID
     * @return empty storage
     */
    public static PlayerStorageSnapshot empty(UUID uniqueId) {
        return new PlayerStorageSnapshot(
                uniqueId,
                new ItemStack[INVENTORY_SIZE],
                new ItemStack[ARMOR_SIZE],
                null,
                0,
                new ItemStack[ENDER_CHEST_SIZE],
                0,
                CURRENT_FORMAT_VERSION,
                0L
        );
    }

    @Override
    public ItemStack[] inventory() {
        return copyItems(inventory, INVENTORY_SIZE, "inventory");
    }

    @Override
    public ItemStack[] armor() {
        return copyItems(armor, ARMOR_SIZE, "armor");
    }

    @Override
    public ItemStack offHand() {
        return copyItem(offHand);
    }

    @Override
    public ItemStack[] enderChest() {
        return copyItems(enderChest, ENDER_CHEST_SIZE, "enderChest");
    }

    /**
     * Returns a snapshot with a new native inventory state.
     *
     * @param nextInventory next 36-slot contents
     * @param nextArmor     next armor contents
     * @param nextOffHand   next off-hand item
     * @param nextHeldSlot  next selected slot
     * @return updated snapshot
     */
    public PlayerStorageSnapshot withPlayerInventory(
            ItemStack[] nextInventory,
            ItemStack[] nextArmor,
            ItemStack nextOffHand,
            int nextHeldSlot
    ) {
        return new PlayerStorageSnapshot(
                uniqueId,
                nextInventory,
                nextArmor,
                nextOffHand,
                nextHeldSlot,
                enderChest,
                enderChestPage,
                formatVersion,
                databaseVersion
        );
    }

    /**
     * Returns a snapshot with new custom ender-chest contents.
     *
     * @param nextEnderChest next 54-slot contents
     * @return updated snapshot
     */
    public PlayerStorageSnapshot withEnderChest(ItemStack[] nextEnderChest) {
        return new PlayerStorageSnapshot(
                uniqueId,
                inventory,
                armor,
                offHand,
                heldSlot,
                nextEnderChest,
                enderChestPage,
                formatVersion,
                databaseVersion
        );
    }

    private static ItemStack[] copyItems(ItemStack[] source, int expectedLength, String fieldName) {
        Objects.requireNonNull(source, fieldName);
        if (source.length != expectedLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain exactly " + expectedLength + " slots"
            );
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) copy[index] = copyItem(source[index]);
        return copy;
    }

    private static ItemStack copyItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
