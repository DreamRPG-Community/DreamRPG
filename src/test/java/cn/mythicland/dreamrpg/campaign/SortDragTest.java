package cn.mythicland.dreamrpg.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortDragTest {
    @Test
    void onlyCompleteSortItemsMayBeDraggedIntoAnEmptyContentSlot() {
        assertTrue(CampaignMenus.canPlaceSortDrag(10, 10, 10, true, true, true));
        assertFalse(CampaignMenus.canPlaceSortDrag(52, 10, 10, true, true, true));
        assertFalse(CampaignMenus.canPlaceSortDrag(10, 10, 1, false, true, true));
        assertFalse(CampaignMenus.canPlaceSortDrag(10, 10, 10, false, true, true));
        assertFalse(CampaignMenus.canPlaceSortDrag(10, 10, 10, true, false, true));
        assertFalse(CampaignMenus.canPlaceSortDrag(10, 10, 10, true, true, false));
        assertFalse(CampaignMenus.canPlaceSortDrag(10, 0, 0, true, true, true));
    }
}
