package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-NPC placement of task and exchange IDs; empty strings represent empty slots.
 */
final class OfferOrder {
    private OfferOrder() {
    }

    static List<ExchangeOffer> resolve(List<ExchangeOffer> offers, List<String> saved) {
        Map<String, ExchangeOffer> remaining = new HashMap<>();
        for (ExchangeOffer offer : offers) remaining.put(offer.id(), offer);
        Set<String> seen = new HashSet<>();
        List<ExchangeOffer> slots = new ArrayList<>();
        for (String id : saved) {
            if (!id.isEmpty() && !seen.add(id))
                throw new IllegalArgumentException("Duplicate offer ID in order: " + id);
            slots.add(id.isEmpty() ? null : remaining.remove(id));
        }
        for (ExchangeOffer offer : offers) {
            if (remaining.remove(offer.id()) != null) slots.add(offer);
        }
        while (!slots.isEmpty() && slots.getLast() == null) slots.removeLast();
        return Collections.unmodifiableList(slots);
    }

    static List<String> identifiers(List<ExchangeOffer> slots) {
        List<String> ids = new ArrayList<>();
        for (ExchangeOffer offer : slots) ids.add(offer == null ? "" : offer.id());
        while (!ids.isEmpty() && ids.getLast().isEmpty()) ids.removeLast();
        return List.copyOf(ids);
    }
}
