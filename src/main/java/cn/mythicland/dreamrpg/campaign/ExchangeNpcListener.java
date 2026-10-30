package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamnpc.api.DreamNPCInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@InjectComponent
@ListenerComponent
public final class ExchangeNpcListener implements Listener {
    private final CampaignMenus menus;

    public ExchangeNpcListener(CampaignMenus menus) {
        this.menus = menus;
    }

    static boolean canOpenEditor(boolean administrator, boolean sneaking) {
        return administrator && sneaking;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(DreamNPCInteractEvent event) {
        if (!canOpenEditor(event.getPlayer().hasPermission("dreamrpg.admin"), event.getPlayer().isSneaking())) return;
        event.setCancelled(true);
        menus.openNpcEditor(event.getPlayer(), event.getNpcId());
    }
}
