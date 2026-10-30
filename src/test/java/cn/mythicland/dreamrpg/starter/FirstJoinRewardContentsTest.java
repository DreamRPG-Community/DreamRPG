package cn.mythicland.dreamrpg.starter;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirstJoinRewardContentsTest {

    @Test
    void usesExactlyFiftyFourSlotsAndDefensiveCopies() {
        ItemStack[] source = new ItemStack[FirstJoinRewardContents.SIZE];
        source[0] = new ItemStack(Material.STONE, 3);
        FirstJoinRewardContents contents = new FirstJoinRewardContents(source);

        source[0].setAmount(1);
        ItemStack[] firstCopy = contents.copyContents();
        ItemStack[] secondCopy = contents.copyContents();

        assertEquals(3, firstCopy[0].getAmount());
        assertNotSame(firstCopy, secondCopy);
        assertNotSame(firstCopy[0], secondCopy[0]);
    }

    @Test
    void rejectsUnexpectedSlotCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FirstJoinRewardContents(new ItemStack[9])
        );
    }
}
