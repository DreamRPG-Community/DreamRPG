package cn.mythicland.dreamrpg.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Proportional integer allocation with unbiased rounding and no lost remainder. */
final class MobLootShares {

    private MobLootShares() {
    }

    static Map<UUID, Integer> divide(int amount, Map<UUID, Double> damage, double roll) {
        double totalDamage = damage.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Integer> shares = new LinkedHashMap<>();
        double cumulativeDamage = 0;
        int assigned = 0;
        int index = 0;
        for (var entry : damage.entrySet()) {
            cumulativeDamage += entry.getValue();
            index++;
            int cumulativeItems;
            if (index == damage.size()) cumulativeItems = amount;
            else cumulativeItems = (int) Math.floor(amount * (cumulativeDamage / totalDamage) + roll);
            int count = cumulativeItems - assigned;
            if (count > 0) shares.put(entry.getKey(), count);
            assigned = cumulativeItems;
        }
        return shares;
    }
}
