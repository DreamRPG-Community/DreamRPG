package cn.mythicland.dreamrpg.command;

import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamworld.api.DreamWorldApi;
import cn.mythicland.dreamworldregion.api.DreamWorldRegionApi;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configurable fixed-map travel, including aliases used by migrated NPC interactions.
 */
@CommandBinding("warp")
public final class WaypointCommand implements ManagedLifecycle {
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    private final DreamLibApi lib;
    private final JavaPlugin plugin;
    private final DreamWorldApi worlds;
    private final DreamWorldRegionApi landmarks;

    public WaypointCommand(JavaPlugin plugin, DreamLibApi lib) {
        this.lib = lib;
        this.plugin = plugin;
        this.worlds = Objects.requireNonNull(Bukkit.getServicesManager().load(DreamWorldApi.class),
                "DreamWorld service is required for fixed-map travel");
        this.landmarks = Objects.requireNonNull(Bukkit.getServicesManager().load(DreamWorldRegionApi.class),
                "DreamWorld landmark service is required for fixed-map travel");
    }

    @Override
    public void load() {
        reload();
    }

    @Override
    public void unload() {
        waypoints.clear();
    }

    @Override
    public void reload() {
        prepareContentReload().apply().run();
    }

    /** @return validated waypoint aliases and an inverse retaining the current destinations */
    public ContentUpdate prepareContentReload() {
        Map<String, Waypoint> next = loadWaypoints();
        Map<String, Waypoint> previous = new LinkedHashMap<>(waypoints);
        return new ContentUpdate(() -> {
            waypoints.clear();
            waypoints.putAll(next);
        }, () -> {
            waypoints.clear();
            waypoints.putAll(previous);
        });
    }

    private Map<String, Waypoint> loadWaypoints() {
        Map<String, Waypoint> next = new LinkedHashMap<>();
        if (lib.content().documents("waypoints.yml").isEmpty()) lib.content().resource(plugin, "waypoints.yml", "common/waypoints.yml");
        var configuration = lib.content().document("waypoints.yml").yaml();
        var entries = configuration.getConfigurationSection("waypoints");
        if (entries == null) {
            throw new IllegalStateException("waypoints.yml requires a waypoints section");
        }
        for (String id : entries.getKeys(false)) {
            var entry = entries.getConfigurationSection(id);
            if (entry == null || !entry.isString("name")) {
                throw new IllegalStateException("Invalid waypoint: " + id);
            }
            boolean useWorld = entry.isString("world");
            boolean useLandmark = entry.isString("landmark");
            if (useWorld == useLandmark || List.of("x", "y", "z", "yaw", "pitch").stream().anyMatch(entry::contains)) {
                throw new IllegalStateException(
                        "Waypoint requires either a world spawn or a DreamWorld landmark: " + id);
            }
            if ((useWorld && entry.getString("world").isBlank())
                    || (useLandmark && entry.getString("landmark").isBlank())) {
                throw new IllegalStateException("Waypoint destination cannot be blank: " + id);
            }
            var waypoint = new Waypoint(id, entry.getString("name"), entry.getString("world"),
                    entry.getString("landmark"), entry.getBoolean("enabled", true),
                    entry.getBoolean("show-in-menu", true));
            put(next, id, waypoint);
            for (String alias : entry.getStringList("aliases")) {
                put(next, alias, waypoint);
            }
        }
        return next;
    }

    private void put(Map<String, Waypoint> next, String alias, Waypoint waypoint) {
        if (next.putIfAbsent(alias, waypoint) != null) {
            throw new IllegalStateException("Duplicate waypoint alias: " + alias);
        }
    }

    @CommandRoute(path = {"warp"}, maxArguments = Integer.MAX_VALUE, usages = "/warp <地点>")
    void teleport(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.sender().sendMessage("Only players can travel.");
            return;
        }
        if (lib.playerLoadingGate().isLoading(player)) {
            player.sendMessage(LegacyText.colorize("&e玩家数据正在加载，请稍候。"));
            return;
        }
        if (context.arguments().isEmpty()) {
            openMenu(player);
            return;
        }
        context.requireArguments(1);
        Waypoint point = waypoints.get(context.argument(0));
        if (point == null) {
            player.sendMessage(LegacyText.colorize("&c没有这个地点。使用 /warp 查看地点列表。"));
            return;
        }
        travel(player, point);
    }

    private void travel(Player player, Waypoint point) {
        if (lib.playerLoadingGate().isLoading(player)) {
            return;
        }
        if (!point.enabled()) {
            player.sendMessage(LegacyText.colorize("&e该地点尚未开放，正在等待地图恢复。"));
            return;
        }
        if (point.landmark() != null) {
            var landmark = landmarks.findLandmark(point.landmark())
                    .orElseThrow(() -> new IllegalStateException("Missing DreamWorld landmark: " + point.landmark()));
            if (worlds.find(landmark.worldName()).isEmpty()) {
                player.sendMessage(LegacyText.colorize("&e该地点尚未开放，正在等待地图恢复。"));
                return;
            }
            landmarks.teleport(player, point.landmark()).whenComplete((success, error) -> lib.runOnMain(() -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                if (error != null) {
                    plugin.getLogger()
                            .log(java.util.logging.Level.WARNING, "Waypoint teleport failed: " + point.id(), error);
                    player.sendMessage(LegacyText.colorize("&c传送失败，请稍后再试。"));
                } else if (success) player.sendMessage(LegacyText.colorize("&a已抵达 " + point.name()));
            }));
            return;
        }
        var world = worlds.find(point.world()).orElse(null);
        if (world == null) {
            player.sendMessage(LegacyText.colorize("&e该地点尚未开放，正在等待地图恢复。"));
            return;
        }
        Location destination = worlds.findSpawn(point.world())
                .orElseThrow(() -> new IllegalStateException("Missing world spawn: " + point.world()))
                .toLocation(world);
        if (player.teleport(destination)) {
            player.sendMessage(LegacyText.colorize("&a已抵达 " + point.name()));
        }
    }

    private void openMenu(Player player) {
        List<Waypoint> destinations = waypoints.values().stream().distinct().filter(Waypoint::showInMenu).toList();
        lib.menuService().open(player, new InventoryMenu() {
            private int page;

            @Override
            public String buildTitle(Player viewer) {
                return LegacyText.colorize("&8旅途 · 地图传送");
            }

            @Override
            public int size(Player viewer) {
                return 54;
            }

            @Override
            public void render(Player viewer, Inventory inventory) {
                inventory.clear();
                for (int slot = 0; slot < 45 && page * 45 + slot < destinations.size(); slot++) {
                    Waypoint point = destinations.get(page * 45 + slot);
                    inventory.setItem(slot, MenuItems.button(point.enabled() ? Material.ENDER_PEARL : Material.BARRIER,
                            "&a" + point.name(), List.of(point.enabled() ? "&7点击前往" : "&7等待原地图补充")));
                }
                inventory.setItem(45, MenuItems.button(Material.ARROW, "&a上一页", List.of()));
                inventory.setItem(49, MenuItems.button(Material.MAP, "&e第 " + (page + 1) + " 页", List.of()));
                inventory.setItem(53, MenuItems.button(Material.ARROW, "&a下一页", List.of()));
            }

            @Override
            public void handleClick(Player viewer, InventoryClickEvent event, MenuManager menus) {
                if (event.getClick() != ClickType.LEFT) {
                    return;
                }
                int slot = event.getRawSlot();
                if (slot == 45 && page > 0) {
                    page--;
                    menus.refresh(viewer);
                } else if (slot == 53 && (page + 1) * 45 < destinations.size()) {
                    page++;
                    menus.refresh(viewer);
                } else if (slot >= 0 && slot < 45 && page * 45 + slot < destinations.size()) {
                    menus.close(viewer);
                    travel(viewer, destinations.get(page * 45 + slot));
                }
            }
        });
    }

    private record Waypoint(
            String id,
            String name,
            String world,
            String landmark,
            boolean enabled,
            boolean showInMenu
    ) {
    }
}
