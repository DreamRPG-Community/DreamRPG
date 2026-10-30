package cn.mythicland.dreamrpg;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpgcombat.DreamRPGCombatLifecycle;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamlib.bootstrap.PluginBootstrap;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.commands.ReloadCommand;

import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for DreamRPG's Lib component graph.
 */
public final class DreamRPGPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.dreamrpg";

    public static final String DISPLAY_NAME = "DreamRPG";

    private PluginBootstrap bootstrap;
    private PluginBootstrap combat;
    private PluginBootstrap content;

    /**
     * Starts the Lib-managed DreamRPG component graph.
     */
    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            DreamLibApi lib = DreamLibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
            combat = lib.createPluginBootstrap(this, "cn.mythicland.dreamrpgcombat");
            combat.enable();
            content = lib.createPluginBootstrap(this, "cn.mythicland.dreamrpgitems");
            content.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "DreamRPG failed to enable: " + DreamLibApi.rootCauseMessage(exception),
                    exception
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Closes the Lib-managed component graph.
     */
    @Override
    public void onDisable() {
        if (content != null) content.disable();
        content = null;
        if (combat != null) combat.disable();
        combat = null;
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads mutable DreamRPG components without reloading runtime dependency JARs.
     */
    public void reloadDreamRPG() {
        if (bootstrap == null) throw new IllegalStateException("DreamRPG bootstrap is unavailable");
        bootstrap.reload();
        combat.reload();
        content.reload();
        reloadMobs();
    }

    public CombatSettings combatSettings() {
        return combat.resolve(DreamRPGCombatLifecycle.class).settings();
    }

    public void reloadCombatConfig() {
        combat.reload();
    }

    public void reloadDreamRPGItems() {
        content.reload();
    }

    public MythicItemService items() {
        return content.resolve(MythicItemService.class);
    }

    public DreamRPGCombatLifecycle combatModule() {
        return combat.resolve(DreamRPGCombatLifecycle.class);
    }

    public void reloadMobs() {
        var mythic = MythicMobs.inst();
        var reload = new ReloadCommand(mythic.getBaseCommand());
        if (!reload.onCommand(getServer().getConsoleSender(), new String[0])) {
            throw new IllegalStateException("MythicMobs rejected its reload command");
        }
    }
}
