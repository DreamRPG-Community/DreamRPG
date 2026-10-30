package cn.mythicland.dreamrpgitems.sync;

import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicMobItemGenerateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Preserves legacy metadata for every MM generation path, including mob drops.
 */
@InjectComponent
@ListenerComponent
public final class MythicItemGenerationListener implements Listener {
    private final MythicItemService items;

    public MythicItemGenerationListener(MythicItemService items) {
        this.items = items;
    }

    @EventHandler
    public void onGenerate(MythicMobItemGenerateEvent event) {
        ItemStack generated = event.getItemStack();
        generated.setItemMeta(items.markGenerated(event.getItem().getInternalName(), generated).getItemMeta());
    }
}
