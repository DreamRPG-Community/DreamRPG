package cn.mythicland.dreamrpgitems;

import cn.mythicland.dreamrpg.DreamRPGPlugin;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamrpgitems.api.MythicItemQuery;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.dreamrpgitems.web.DreamRPGItemWebServer;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Owns DreamRPG item service construction, web lifecycle, command binding, and MM reload integration.
 */
@LifecycleComponent
@ListenerComponent
public final class DreamRPGItemLifecycle implements DreamLibPluginLifecycle, Listener {

    private final DreamRPGPlugin plugin;
    private final DreamLibApi lib;
    private final PluginTaskScope tasks;
    private final MythicItemService itemService;
    private final DreamRPGItemConfiguration configuration;
    private MythicMobs mythicMobs;
    private DreamRPGItemWebServer webServer;
    private BukkitTask itemRefreshTask;

    /**
     * Creates the lifecycle module from Lib-provided dependencies.
     *
     * @param plugin      plugin entry point
     * @param lib         shared Lib service
     * @param tasks       plugin-owned task scope
     * @param itemService injected Mythic item service
     */
    public DreamRPGItemLifecycle(
            DreamRPGPlugin plugin,
            DreamLibApi lib,
            PluginTaskScope tasks,
            MythicItemService itemService,
            DreamRPGItemConfiguration configuration
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.itemService = Objects.requireNonNull(itemService, "itemService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Initializes DreamRPG item service and registers its public service and command.
     */
    @Override
    public void enable() {
        mythicMobs = (MythicMobs) Objects.requireNonNull(
                plugin.getServer().getPluginManager().getPlugin("MythicMobs"),
                "MythicMobs is not enabled"
        );
        itemService.initialize();

        webServer = new DreamRPGItemWebServer(plugin, lib);
        configureWeb();
        scheduleItemRefresh();
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
        configureWeb();
        var result = Objects.requireNonNull(itemService, "DreamRPG items item service is unavailable").reload();
        if (!result.success()) {
            throw new IllegalStateException("Cannot reload MythicMobs items: " + result.message());
        }
    }

    /**
     * Cancels refresh work and closes the web server.
     */
    @Override
    public void disable() {
        tasks.cancel(itemRefreshTask);
        itemRefreshTask = null;
        if (webServer != null) webServer.close();
        webServer = null;
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
        plugin.getLogger().info(
                DreamRPGPlugin.DISPLAY_NAME + " refreshed its MM item catalog after MythicMobs reload."
        );
    }

    private void configureWeb() {
        DreamRPGItemConfiguration.Snapshot current = configuration.snapshot();
        if (!current.enabled()) {
            Objects.requireNonNull(webServer, "DreamRPG items web server is unavailable").close();
            return;
        }
        String bindAddress = current.bindAddress();
        int port = current.port();
        String token = current.token();
        if (token == null || token.isBlank()) {
            token = generateToken();
            plugin.getConfig().set("web.token", token);
            plugin.saveConfig();
            plugin.getLogger().info(DreamRPGPlugin.DISPLAY_NAME + " generated a web token in config.yml.");
        }
        try {
            Objects.requireNonNull(webServer, "DreamRPG items web server is unavailable")
                    .start(bindAddress, port, token, itemService);
        } catch (IOException exception) {
            plugin.getLogger().warning(
                    DreamRPGPlugin.DISPLAY_NAME + " web console is disabled: " + exception.getMessage()
            );
        }
    }

    private void scheduleItemRefresh() {
        itemRefreshTask = tasks.runLater(40L, () -> {
            if (!plugin.isEnabled()) {
                itemRefreshTask = null;
                return;
            }
            try {
                itemService.refreshIfFilesChanged();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning(
                        DreamRPGPlugin.DISPLAY_NAME
                                + " failed to refresh changed MM files: " + exception.getMessage()
                );
            }
            if (plugin.isEnabled()) scheduleItemRefresh();
        });
    }
}
