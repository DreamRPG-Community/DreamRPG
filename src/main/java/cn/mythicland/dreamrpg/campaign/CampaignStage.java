package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeOffer;

import java.util.List;

/**
 * An ordered chapter containing only its item-submission tasks.
 */
public record CampaignStage(
        String id,
        List<ExchangeOffer> tasks
) {
    public CampaignStage {
        tasks = List.copyOf(tasks);
    }
}
