package cn.mythicland.dreamrpg.combat;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MobLootSharesTest {
    @Test
    void dividesWholeStacksExactlyAndSingleItemsByDamageProbability() {
        UUID major = UUID.randomUUID();
        UUID minor = UUID.randomUUID();
        Map<UUID, Double> damage = new LinkedHashMap<>();
        damage.put(major, 75.0);
        damage.put(minor, 25.0);
        assertEquals(Map.of(major, 48, minor, 16), MobLootShares.divide(64, damage, 0.7));
        int majorWins = 0;
        for (int sample = 0; sample < 10000; sample++) {
            var single = MobLootShares.divide(1, damage, (sample + 0.5) / 10000);
            assertEquals(1, single.values().stream().mapToInt(Integer::intValue).sum());
            majorWins += single.getOrDefault(major, 0);
        }
        assertEquals(7500, majorWins, "Single rewards must follow the 75% damage contribution");
        damage.put(UUID.randomUUID(), 12.5);
        for (int amount = 1; amount < 100; amount++) {
            var shares = MobLootShares.divide(amount, damage, 0.999999);
            assertEquals(amount, shares.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(shares.values().stream().allMatch(count -> count > 0));
        }
    }
}
