package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamnpc.api.DreamNPCInteractEvent;
import cn.mythicland.dreamnpc.api.DreamNPCRemovedEvent;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

@InjectComponent
@ListenerComponent
public final class ExchangeNpcListener implements Listener {
    private final CampaignMenus menus;
    private final CampaignCatalog catalog;
    private final JavaPlugin plugin;

    public ExchangeNpcListener(CampaignMenus menus, CampaignCatalog catalog, JavaPlugin plugin) {
        this.menus = menus;
        this.catalog = catalog;
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcRemoved(DreamNPCRemovedEvent event) {
        catalog.removeNpc(event.getNpcId()).whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.SEVERE,
                    "Could not remove RPG projects for deleted NPC " + event.getNpcId(), failure);
        });
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
