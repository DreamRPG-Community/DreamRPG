package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ItemAmount;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfferOrderTest {
    private static final UUID NPC = UUID.fromString("72a62f0f-a93a-3a40-a581-b05f345f94d1");

    private ExchangeOffer offer(String id) {
        return new ExchangeOffer(id, NPC, List.of(new ItemAmount("cost", 1)), new ItemAmount("reward", 1));
    }

    @Test
    void preservesGapsAndAddsNewItemsWithoutMovingExistingEntries() throws Exception {
        ExchangeOffer task = offer("task");
        ExchangeOffer exchange = offer("exchange");
        ExchangeOffer added = offer("added");
        List<ExchangeOffer> slots = Arrays.asList(exchange, null, task);
        List<String> ids = OfferOrder.identifiers(slots);
        var yaml = new YamlConfiguration();
        yaml.set(NPC.toString(), ids);
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        assertEquals(Arrays.asList(exchange, null, task, added),
                OfferOrder.resolve(List.of(task, exchange, added), CampaignCatalog.parseOrders(reloaded).get(NPC)));
        assertEquals(Arrays.asList(null, task), OfferOrder.resolve(List.of(task), List.of("removed", "task")));
    }

    @Test
    void rejectsDuplicateOrderEntriesAndTrimsUnusedTrailingSlots() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OfferOrder.resolve(List.of(offer("task")), List.of("task", "task")));
        assertEquals(List.of("", "task"), OfferOrder.identifiers(Arrays.asList(null, offer("task"), null)));
        var yaml = new YamlConfiguration();
        yaml.set(NPC.toString(), List.of("task", "task"));
        assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parseOrders(yaml));
    }
}
