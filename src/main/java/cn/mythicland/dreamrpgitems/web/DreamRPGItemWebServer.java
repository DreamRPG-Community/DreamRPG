package cn.mythicland.dreamrpgitems.web;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.web.EmbeddedHttpServer;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

/**
 * Lifecycle wrapper for the DreamRPG item service web service.
 */
public final class DreamRPGItemWebServer implements AutoCloseable {

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private EmbeddedHttpServer server;
    private String bindAddress;
    private int port;

    public DreamRPGItemWebServer(JavaPlugin plugin, DreamLibApi lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    public void start(String bindAddress, int port, String token, MythicItemService service)
            throws IOException {
        close();
        this.bindAddress = bindAddress;
        this.port = port;
        server = lib.webService().start(bindAddress, port,
                new DreamRPGItemWebHandler(plugin, lib, service, token));
    }

    public boolean isRunning() {
        return server != null;
    }

    public String displayAddress() {
        return "http://" + bindAddress + ":" + (server == null ? port : server.port());
    }

    @Override
    public void close() {
        if (server != null) server.close();
        server = null;
    }
}
