package cn.mythicland.dreamrpg.exchange;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable costs and reward; no item definition or player progress is duplicated here.
 */
public record ExchangeOffer(
        String id,
        UUID npc,
        List<ItemAmount> requires,
        ItemAmount reward,
        ExchangeType type
) {
    public ExchangeOffer(String id, UUID npc, List<ItemAmount> requires, ItemAmount reward) {
        this(id, npc, requires, reward, ExchangeType.EXCHANGE);
    }

    public ExchangeOffer {
        if (id == null || !id.matches("[a-z][a-z0-9_-]*"))
            throw new IllegalArgumentException("Invalid exchange ID: " + id);
        if (npc == null) throw new IllegalArgumentException("Exchange requires an NPC: " + id);
        requires = List.copyOf(requires);
        if (requires.isEmpty()) throw new IllegalArgumentException("Exchange requires at least one cost: " + id);
        Objects.requireNonNull(reward, "reward");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Parses one strict offer; the owning group supplies its NPC when the offer does not.
     */
    public static ExchangeOffer parse(
            String id,
            ConfigurationSection section,
            UUID groupNpc
    ) {
        if (section == null) throw new IllegalArgumentException("Exchange must be an object: " + id);
        for (String key : section.getKeys(false)) {
            if (!Set.of("npc", "requires", "reward", "type").contains(key))
                throw new IllegalArgumentException("Unknown exchange key: " + id + "." + key);
        }
        UUID npc = groupNpc;
        if (section.contains("npc")) npc = UUID.fromString(section.getString("npc", ""));
        Object raw = section.get("requires");
        if (!(raw instanceof List<?> costs)) throw new IllegalArgumentException("Exchange requires a cost list: " + id);
        List<ItemAmount> requires = new ArrayList<>();
        for (Object cost : costs) {
            if (!(cost instanceof Map<?, ?> values))
                throw new IllegalArgumentException("Exchange cost must contain item and amount: " + id);
            requires.add(amount(values, id + ".requires"));
        }
        ConfigurationSection result = section.getConfigurationSection("reward");
        if (result == null) throw new IllegalArgumentException("Exchange requires a reward: " + id);
        ExchangeType type = groupNpc == null ? ExchangeType.TASK : ExchangeType.EXCHANGE;
        if (section.contains("type")) {
            Object configured = section.get("type");
            if (!(configured instanceof String name))
                throw new IllegalArgumentException("Expected exchange type at " + id + ".type");
            try {
                type = ExchangeType.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown exchange type at " + id + ".type: " + name, exception);
            }
        }
        return new ExchangeOffer(id, npc, requires, amount(result.getValues(false), id + ".reward"), type);
    }

    private static ItemAmount amount(Map<?, ?> values, String path) {
        if (!values.keySet().stream().allMatch(key -> key.equals("item") || key.equals("amount"))) {
            throw new IllegalArgumentException("Unknown item reference field: " + path);
        }
        Object item = values.get("item");
        Object amount = values.containsKey("amount") ? values.get("amount") : 1;
        if (!(item instanceof String key) || !(amount instanceof Number number)
                || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("Expected item key and integer amount: " + path);
        }
        return new ItemAmount(key, number.intValue());
    }
}
