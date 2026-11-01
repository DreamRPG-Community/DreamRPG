package cn.mythicland.dreamrpgitems;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgitems.api.MythicItemQuery;
import cn.mythicland.dreamrpgitems.menu.ItemLibraryConfiguration;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Owns DreamRPG item service construction, library lifecycle, and MM reload integration.
 */
@LifecycleComponent
@InjectComponent
@ListenerComponent
public final class DreamRPGItemLifecycle implements ManagedLifecycle, Listener {

    private final DreamRPGPlugin plugin;
    private final MythicItemService itemService;
    private final ItemLibraryConfiguration libraryConfiguration;
    private MythicMobs mythicMobs;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin      plugin entry point
     * @param itemService injected Mythic item service
     */
    public DreamRPGItemLifecycle(
            DreamRPGPlugin plugin,
            MythicItemService itemService,
            ItemLibraryConfiguration libraryConfiguration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemService = Objects.requireNonNull(itemService, "itemService");
        this.libraryConfiguration = Objects.requireNonNull(libraryConfiguration, "libraryConfiguration");
    }

    /**
     * Initializes DreamRPG item service and registers its public service and command.
     */
    @Override
    public void load() {
        mythicMobs = (MythicMobs) Objects.requireNonNull(
                plugin.getServer().getPluginManager().getPlugin("MythicMobs"),
                "MythicMobs is not enabled"
        );
        itemService.initialize();
        libraryConfiguration.reload();

        plugin.getLogger().info(
                DreamRPGPlugin.DISPLAY_NAME + " enabled; MM items loaded: "
                        + itemService.search(MythicItemQuery.defaults()).total()
        );
    }

    /**
     * Refreshes the catalog when a generic Lib bootstrap reload is requested.
     */
    @Override
    public void reload() {
        var result = Objects.requireNonNull(itemService, "DreamRPG items item service is unavailable").reload();
        if (!result.success()) {
            throw new IllegalStateException("Cannot reload MythicMobs items: " + result.message());
        }
        libraryConfiguration.reload();
    }

    /**
     * Closes the item library lifecycle.
     */
    @Override
    public void unload() {
        libraryConfiguration.close();
        mythicMobs = null;
    }

    /**
     * Refreshes the catalog after MythicMobs emits its reload event.
     *
     * @param event MythicMobs reload event
     */
    @EventHandler
    public void handleMythicReload(MythicReloadedEvent event) {
        if (event.getInstance() != mythicMobs) return;
        itemService.refreshAfterMythicReload();
        libraryConfiguration.warnMissingItems();
        plugin.getLogger().info(
                DreamRPGPlugin.DISPLAY_NAME + " refreshed its MM item catalog after MythicMobs reload."
        );
    }

}
