package cn.mythicland.dreamrpg.profile;

import cn.mythicland.dreamrpg.event.PlayerDataReadyEvent;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Unlocks a first career from a genuine marked trade certificate, retaining it for equipment exchange.
 */
@InjectComponent
@ListenerComponent
public final class CareerTokenService implements Listener, AutoCloseable {
    private final PlayerProfileService profiles;
    private final DreamLibApi lib;
    private final PluginTaskScope tasks;
    private final JavaPlugin plugin;
    private final Map<String, String> tokens = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final String namespace;
    private boolean closed;

    public CareerTokenService(JavaPlugin plugin, PlayerProfileService profiles, DreamLibApi lib, PluginTaskScope tasks) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.lib = lib;
        this.tasks = tasks;
        var path = plugin.getDataFolder().toPath().resolve("career-tokens.yml");
        if (!java.nio.file.Files.exists(path)) {
            plugin.saveResource("career-tokens.yml", false);
        }
        var config = YamlConfiguration.loadConfiguration(path.toFile());
        namespace = config.getString("marker-namespace");
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Career token marker namespace is missing");
        }
        var entries = config.getConfigurationSection("tokens");
        if (entries == null) {
            throw new IllegalStateException("career-tokens.yml requires tokens");
        }
        for (String item : entries.getKeys(false)) {
            String career = entries.getString(item);
            if (career == null || profiles.findCareer(career).isEmpty()) {
                throw new IllegalStateException("Unknown career for token: " + item);
            }
            tokens.put(item, career);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            schedule(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            schedule(player);
        }
    }

    @EventHandler
    public void ready(PlayerDataReadyEvent event) {
        Player player = Bukkit.getPlayer(event.uniqueId());
        if (player != null) {
            schedule(player);
        }
    }

    private void schedule(Player player) {
        if (closed || tokens.isEmpty() || !pending.add(player.getUniqueId())) {
            return;
        }
        UUID id = player.getUniqueId();
        tasks.runLater(1, () -> {
            if (!player.isOnline() || lib.playerLoadingGate().isLoading(player)
                    || !profiles.getProfile(id).careerId().equals("unclassed")) {
                pending.remove(id);
                return;
            }
            for (var item : player.getInventory().getContents()) {
                var marker = lib.itemMarkerCodec().read(item, namespace);
                if (marker.isEmpty()) {
                    continue;
                }
                String career = tokens.get(marker.orElseThrow().values().get("item-id"));
                if (career == null) {
                    continue;
                }
                profiles.changeCareer(id, career).whenComplete((profile, failure) -> lib.runOnMain(() -> {
                    pending.remove(id);
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "Cannot unlock career for " + id, failure);
                        return;
                    }
                    if (closed || !plugin.isEnabled()) {
                        return;
                    }
                    if (player.isOnline()) {
                        player.sendMessage(LegacyText.colorize("&a你已转职为 " + profile.career().displayName() + "。"));
                    }
                }));
                return;
            }
            pending.remove(id);
        });
    }

    @Override
    public void close() {
        closed = true;
        pending.clear();
    }
}
