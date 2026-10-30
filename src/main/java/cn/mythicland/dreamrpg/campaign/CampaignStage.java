package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import java.util.List;

/**
 * An ordered chapter containing only its item-submission tasks.
 */
public record CampaignStage(
        String id,
        String name,
        List<String> description,
        List<ExchangeOffer> tasks
) {
    public CampaignStage {
        description = List.copyOf(description);
        tasks = List.copyOf(tasks);
    }
}
