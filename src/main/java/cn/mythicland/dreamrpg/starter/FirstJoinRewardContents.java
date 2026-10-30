package cn.mythicland.dreamrpg.starter;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Immutable, fixed-size contents of the first-join reward chest.
 */
final class FirstJoinRewardContents {

    static final int SIZE = 54;

    private final ItemStack[] contents;

    FirstJoinRewardContents(ItemStack[] contents) {
        ItemStack[] value = Objects.requireNonNull(contents, "contents");
        if (value.length != SIZE) {
            throw new IllegalArgumentException("First-join reward contents must contain exactly 54 slots");
        }
        this.contents = copy(value);
    }

    static FirstJoinRewardContents empty() {
        return new FirstJoinRewardContents(new ItemStack[SIZE]);
    }

    ItemStack[] copyContents() {
        return copy(contents);
    }

    boolean hasItems() {
        for (ItemStack item : contents) {
            if (item != null) return true;
        }
        return false;
    }

    private static ItemStack[] copy(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            ItemStack item = source[index];
            copy[index] = item == null ? null : item.clone();
        }
        return copy;
    }
}
