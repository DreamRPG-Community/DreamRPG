package cn.mythicland.dreamrpg;

import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.dreamrpg.command.WaypointCommand;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGLifecycle;
import cn.mythicland.dreamworld.DreamWorldPlugin;
import cn.mythicland.dreamchest.DreamChestPlugin;
import cn.mythicland.dreamnpc.DreamNPCPlugin;
import cn.mythicland.dreamrpg.content.ContentController;
import cn.mythicland.dreamrpg.title.TitleCatalog;
import cn.mythicland.dreamlib.bootstrap.DreamLibManagedPlugin;
import cn.mythicland.dreamrpgcombat.DreamRPGCombatLifecycle;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.dreamrpgitems.menu.ItemLibraryConfiguration;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.commands.ReloadCommand;


/**
 * Minimal Bukkit entry point for DreamRPG's Lib component graph.
 */
public final class DreamRPGPlugin extends DreamLibManagedPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.dreamrpg";
    private static final String COMBAT_PACKAGE = "cn.mythicland.dreamrpgcombat";
    private static final String ITEMS_PACKAGE = "cn.mythicland.dreamrpgitems";

    public static final String DISPLAY_NAME = "DreamRPG";

    public DreamRPGPlugin() {
        super(COMPONENT_PACKAGE, COMBAT_PACKAGE, ITEMS_PACKAGE);
    }

    /**
     * Reloads mutable DreamRPG components without reloading runtime dependency JARs.
     */
    public void reloadDreamRPG() {
        bootstrap(COMPONENT_PACKAGE).reload();
        bootstrap(COMBAT_PACKAGE).reload();
        bootstrap(ITEMS_PACKAGE).reload();
        reloadMobs();
    }

    public CombatSettings combatSettings() {
        return bootstrap(COMBAT_PACKAGE).resolve(DreamRPGCombatLifecycle.class).settings();
    }

    public void reloadCombatConfig() {
        bootstrap(COMBAT_PACKAGE).reload();
    }

    public void reloadDreamRPGItems() {
        bootstrap(ITEMS_PACKAGE).reload();
    }

    public MythicItemService items() {
        return bootstrap(ITEMS_PACKAGE).resolve(MythicItemService.class);
    }

    public ItemLibraryConfiguration itemLibrary() {
        return bootstrap(ITEMS_PACKAGE).resolve(ItemLibraryConfiguration.class);
    }

    public DreamRPGCombatLifecycle combatModule() {
        return bootstrap(COMBAT_PACKAGE).resolve(DreamRPGCombatLifecycle.class);
    }

    /** Applies editable content on the main thread without rebinding system configuration. */
    public void applyContent() {
        prepareContent().forEach(update -> update.apply().run());
    }

    /** @return prepared snapshots; none are applied until every consumer validates successfully */
    public java.util.List<ContentUpdate> prepareContent() {
        var updates = new java.util.ArrayList<ContentUpdate>();
        updates.add(bootstrap(COMPONENT_PACKAGE).resolve(CampaignCatalog.class).prepareContentReload());
        updates.add(bootstrap(COMPONENT_PACKAGE).resolve(TitleCatalog.class).prepareContentReload());
        updates.add(bootstrap(COMPONENT_PACKAGE).resolve(WaypointCommand.class).prepareContentReload());
        updates.add(bootstrap(COMPONENT_PACKAGE).resolve(DreamRPGLifecycle.class).prepareContentReload());
        updates.addAll(((DreamWorldPlugin) getServer().getPluginManager().getPlugin("DreamWorld")).prepareContentReload());
        updates.add(((DreamChestPlugin) getServer().getPluginManager().getPlugin("DreamChest")).prepareContentReload());
        updates.add(itemLibrary().prepareContentReload());
        return java.util.List.copyOf(updates);
    }

    /** Requests reconciliation of loaded item references after content is accepted. */
    public void synchronizeContentItems() {
        var refreshed = items().synchronizeAll();
        if (!refreshed.success()) throw new IllegalStateException("Item synchronization failed: " + refreshed.message());
    }

    /** @return asynchronous NPC content reload completion */
    public java.util.concurrent.CompletableFuture<Void> reloadNpcContent() {
        return ((DreamNPCPlugin) getServer().getPluginManager().getPlugin("DreamNPC")).reloadContent();
    }

    /**
     * Starts an independent content operation.
     *
     * @param sender administrator receiving the result
     * @param reload whether to reload validated source content
     */
    public void content(org.bukkit.command.CommandSender sender, boolean reload) {
        bootstrap(COMPONENT_PACKAGE).resolve(ContentController.class).execute(sender, reload);
    }

    public void reloadMobs() {
        var mythic = MythicMobs.inst();
        var reload = new ReloadCommand(mythic.getBaseCommand());
        if (!reload.onCommand(getServer().getConsoleSender(), new String[0])) {
            throw new IllegalStateException("MythicMobs rejected its reload command");
        }
    }
}
