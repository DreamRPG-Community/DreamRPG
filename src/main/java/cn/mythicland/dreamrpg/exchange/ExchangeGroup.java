package cn.mythicland.dreamrpg.exchange;

import java.util.List;
import java.util.UUID;

/**
 * An ordered NPC exchange menu outside the mainline directory.
 */
public record ExchangeGroup(
        String id,
        UUID npc,
        List<ExchangeOffer> offers
) {
    public ExchangeGroup {
        offers = List.copyOf(offers);
    }
}
