package cn.mythicland.dreamrpg;

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

    public void reloadMobs() {
        var mythic = MythicMobs.inst();
        var reload = new ReloadCommand(mythic.getBaseCommand());
        if (!reload.onCommand(getServer().getConsoleSender(), new String[0])) {
            throw new IllegalStateException("MythicMobs rejected its reload command");
        }
    }
}
