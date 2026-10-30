package cn.mythicland.dreamrpg.exchange;

/**
 * A quantity of one item from the authoritative RPG library.
 */
public record ItemAmount(String item, int amount) {
    public ItemAmount {
        if (item == null || item.isBlank()) throw new IllegalArgumentException("Exchange item key is required");
        item = item.trim();
        if (amount < 1 || amount > 2304)
            throw new IllegalArgumentException("Exchange amount must be between 1 and 2304: " + item);
    }
}
