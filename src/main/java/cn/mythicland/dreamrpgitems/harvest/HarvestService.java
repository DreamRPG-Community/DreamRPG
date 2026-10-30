package cn.mythicland.dreamrpgitems.harvest;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.PluginTaskScope;
import cn.mythicland.dreamlib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed melon resource points with identity-marked rewards and no disguise dependency.
 */
@LifecycleComponent(dependsOn = cn.mythicland.dreamrpgitems.DreamRPGItemLifecycle.class)
@ListenerComponent
public final class HarvestService implements DreamLibPluginLifecycle, Listener {
    private final List<Point> points = new ArrayList<>();
    private final MythicItemService items;
    private final DreamLibApi lib;
    private final PluginTaskScope tasks;
    private BukkitTask refresh;

    public HarvestService(JavaPlugin plugin, MythicItemService items, DreamLibApi lib, PluginTaskScope tasks) {
        this.items = items;
        this.lib = lib;
        this.tasks = tasks;
        var path = plugin.getDataFolder().toPath().resolve("harvest.yml");
        if (!java.nio.file.Files.exists(path)) {
            plugin.saveResource("harvest.yml", false);
        }
        var entries = YamlConfiguration.loadConfiguration(path.toFile()).getConfigurationSection("points");
        if (entries == null) {
            throw new IllegalStateException("harvest.yml requires a points section");
        }
        for (String id : entries.getKeys(false)) {
            var entry = entries.getConfigurationSection(id);
            if (entry == null || !entry.isString("world") || !entry.isString("item") || !entry.isInt("cooldown-seconds")) {
                throw new IllegalStateException("Invalid harvest point: " + id);
            }
            for (String coordinate : List.of("x", "y", "z")) {
                if (!entry.isInt(coordinate)) {
                    throw new IllegalStateException("Missing harvest coordinate: " + id + "." + coordinate);
                }
            }
            String item = entry.getString("item");
            if (entry.getInt("cooldown-seconds") < 0) {
                throw new IllegalStateException("Invalid harvest reward or cooldown: " + id);
            }
            points.add(new Point(entry.getString("world"), entry.getInt("x"), entry.getInt("y"),
                    entry.getInt("z"), item, entry.getInt("cooldown-seconds")));
        }
    }

    @Override
    public void enable() {
        for (Point point : points) {
            if (items.find(point.item).isEmpty()) {
                throw new IllegalStateException("Unknown harvest reward: " + point.item);
            }
        }
        refresh = tasks.runTimer(1, 20, () -> {
            for (Point point : points) {
                var world = Bukkit.getWorld(point.world);
                if (world == null || !world.isChunkLoaded(point.x >> 4, point.z >> 4)) {
                    continue;
                }
                Block block = world.getBlockAt(point.x, point.y, point.z);
                if (block.getType() == Material.AIR || block.getType() == Material.MELON_BLOCK) {
                    Material desired = System.nanoTime() >= point.availableAt ? Material.MELON_BLOCK : Material.AIR;
                    if (block.getType() != desired) {
                        block.setType(desired, false);
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || lib.playerLoadingGate().isLoading(event.getPlayer())) {
            return;
        }
        Block block = event.getClickedBlock();
        for (Point point : points) {
            if (!block.getWorld().getName().equals(point.world) || block.getX() != point.x || block.getY() != point.y || block.getZ() != point.z) {
                continue;
            }
            event.setCancelled(true);
            if (System.nanoTime() < point.availableAt || block.getType() != Material.MELON_BLOCK) {
                return;
            }
            var reward = items.getItemStack(point.item, 1);
            if (!event.getPlayer().getInventory().addItem(reward).isEmpty()) {
                event.getPlayer().sendMessage(LegacyText.colorize("&e背包已满，请先整理背包。"));
                return;
            }
            point.availableAt = System.nanoTime() + point.cooldownSeconds * 1_000_000_000L;
            block.setType(Material.AIR, false);
            event.getPlayer().sendMessage(LegacyText.colorize("&a你采集到了西瓜片 x1。"));
            return;
        }
    }

    @Override
    public void disable() {
        tasks.cancel(refresh);
        refresh = null;
    }

    @Override
    public void reload() {
        for (Point point : points) {
            if (items.find(point.item).isEmpty()) {
                throw new IllegalStateException("Harvest reward no longer exists: " + point.item);
            }
        }
        disable();
        enable();
    }

    private static final class Point {
        private final String world;
        private final int x, y, z;
        private final String item;
        private final int cooldownSeconds;
        private long availableAt;

        private Point(String world, int x, int y, int z, String item, int cooldownSeconds) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.item = item;
            this.cooldownSeconds = cooldownSeconds;
        }
    }
}
