package cn.mythicland.dreamrpg;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginBootstrap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Minimal Bukkit entry point for DreamRPG's Lib component graph.
 */
public final class DreamRpgPlugin extends JavaPlugin {

    private static final String COMPONENT_PACKAGE = "cn.mythicland.dreamrpg";

    private PluginBootstrap bootstrap;

    /**
     * Starts the Lib-managed DreamRPG component graph.
     */
    @Override
    @SuppressWarnings("resource")
    public void onEnable() {
        try {
            LibApi lib = LibApi.require(this);
            bootstrap = lib.createPluginBootstrap(this, COMPONENT_PACKAGE);
            bootstrap.enable();
        } catch (RuntimeException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "DreamRPG failed to enable: " + LibApi.rootCauseMessage(exception),
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
        if (bootstrap != null) bootstrap.disable();
        bootstrap = null;
    }

    /**
     * Reloads mutable DreamRPG components without reloading runtime dependency JARs.
     */
    public void reloadDreamRpg() {
        if (bootstrap == null) throw new IllegalStateException("DreamRPG bootstrap is unavailable");
        bootstrap.reload();
    }
}
