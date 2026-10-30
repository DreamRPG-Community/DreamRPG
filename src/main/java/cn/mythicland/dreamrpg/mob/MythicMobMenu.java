package cn.mythicland.dreamrpg.mob;

import cn.mythicland.dreamlib.menu.MenuItems;
import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.menu.MenuView;
import cn.mythicland.dreamlib.menu.PageWindow;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

import static cn.mythicland.dreamlib.menu.MenuPageLayout.BACK_SLOT;
import static cn.mythicland.dreamlib.menu.MenuPageLayout.CONTENT_SLOTS;
import static cn.mythicland.dreamlib.menu.MenuPageLayout.NEXT_SLOT;
import static cn.mythicland.dreamlib.menu.MenuPageLayout.PREVIOUS_SLOT;
import static cn.mythicland.dreamlib.menu.MenuPageLayout.SIZE;
import static cn.mythicland.dreamlib.menu.MenuPageLayout.forEachEntry;

/**
 * MythicMobs mob admin quick-action menu.
 *
 * The interaction has three phases: paged mob selection, operation selection (spawn, set spawn
 * point, kill nearby, spawn point list, info), and paged management of the selected mob's spawn
 * points (left click teleport, right click delete, shift+left click details). Every screen paints
 * the middle three slots on both side columns with blank-name black stained glass as a border.
 */
public final class MythicMobMenu implements MenuView {

    private static final Material MOB_ICON = Material.MONSTER_EGG;
    private static final short BLACK_GLASS = 15;
    private static final List<Integer> BORDER_SLOTS = List.of(18, 27, 36, 26, 35, 44);
    private static final int SELECTED_SLOT = 13;
    private static final int SPAWN_SLOT = 20;
    private static final int SET_SPAWN_SLOT = 21;
    private static final int KILL_SLOT = 22;
    private static final int SPAWNERS_SLOT = 23;
    private static final int INFO_SLOT = 24;

    private enum Phase {
        SELECT_MOB,
        SELECT_ACTION,
        MANAGE_SPAWNERS
    }

    private final MythicMobService service;
    private Phase phase = Phase.SELECT_MOB;
    private List<MobSummary> mobs = List.of();
    private List<SpawnPoint> spawnPoints = List.of();
    private String selectedMob = "";
    private int page;

    public MythicMobMenu(MythicMobService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Opens the menu and resets to the mob selection phase.
     */
    public void open(Player player, MenuService menus) {
        phase = Phase.SELECT_MOB;
        selectedMob = "";
        page = 0;
        menus.open(player, this);
    }

    @Override
    public String title(Player player) {
        return "MM 生物管理";
    }

    @Override
    public int size(Player player) {
        return SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        applyBorder(inventory);
        MobSummary selected = null;
        if (phase == Phase.SELECT_ACTION || phase == Phase.MANAGE_SPAWNERS) {
            selected = service.find(selectedMob).orElse(null);
            if (selected == null) {
                phase = Phase.SELECT_MOB;
                selectedMob = "";
                page = 0;
            }
        }
        switch (phase) {
            case SELECT_MOB -> renderMobSelection(inventory);
            case SELECT_ACTION -> renderActionSelection(inventory, selected);
            case MANAGE_SPAWNERS -> renderSpawnPoints(inventory);
        }
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menus) {
        if (!player.hasPermission("dreamrpg.admin")) {
            menus.close(player);
            return;
        }
        switch (phase) {
            case SELECT_MOB -> handleMobClick(player, event, menus);
            case SELECT_ACTION -> handleActionClick(player, event, menus);
            case MANAGE_SPAWNERS -> handleSpawnPointClick(player, event, menus);
        }
    }

    private void renderMobSelection(Inventory inventory) {
        mobs = service.listMobs();
        PageWindow window = forEachEntry(mobs, page, (slot, mob) ->
                inventory.setItem(slot, mobIcon(mob)));
        page = window.page();
        if (mobs.isEmpty()) {
            inventory.setItem(SELECTED_SLOT, MenuItems.button(Material.BARRIER, "&c没有已加载的 MM 生物",
                    List.of("&f请先确认 MythicMobs 已加载生物")));
        }
        if (window.hasPrevious()) {
            inventory.setItem(PREVIOUS_SLOT, MenuItems.button(Material.ARROW, "&a上一页",
                    List.of("&e点击翻到上一页!")));
        }
        if (window.hasNext()) {
            inventory.setItem(NEXT_SLOT, MenuItems.button(Material.ARROW, "&a下一页",
                    List.of("&e点击翻到下一页!")));
        }
        inventory.setItem(BACK_SLOT, MenuItems.button(Material.BARRIER, "&c关闭", List.of("&f点击关闭菜单")));
    }

    private void renderActionSelection(Inventory inventory, MobSummary selected) {
        inventory.setItem(SELECTED_SLOT, MenuItems.button(MOB_ICON, "&e" + selected.displayName(),
                List.of("&7" + selected.internalName(), "&f当前选中的生物")));
        inventory.setItem(SPAWN_SLOT, MenuItems.button(Material.EMERALD, "&a生成一只",
                List.of("&f在准星处生成 1 只 1 级生物")));
        inventory.setItem(SET_SPAWN_SLOT, MenuItems.button(Material.MOB_SPAWNER, "&a设为生成点",
                List.of("&f将玩家当前位置设为该生物生成点")));
        inventory.setItem(KILL_SLOT, MenuItems.button(Material.REDSTONE_BLOCK, "&c击杀附近",
                List.of("&f击杀 32 格内同类型生物")));
        inventory.setItem(SPAWNERS_SLOT, MenuItems.button(Material.COMPASS, "&e生成点列表",
                List.of("&f查看并管理该生物所有生成点")));
        inventory.setItem(INFO_SLOT, MenuItems.button(Material.BOOK, "&e查看信息",
                List.of("&f显示该生物的 ID 与显示名")));
        inventory.setItem(BACK_SLOT, MenuItems.button(Material.ARROW, "&a返回生物选择",
                List.of("&f回到生物选择页重新选择")));
    }

    private void renderSpawnPoints(Inventory inventory) {
        spawnPoints = service.listSpawnPoints(selectedMob);
        PageWindow window = forEachEntry(spawnPoints, page, (slot, point) ->
                inventory.setItem(slot, spawnPointIcon(point)));
        page = window.page();
        if (spawnPoints.isEmpty()) {
            inventory.setItem(SELECTED_SLOT, MenuItems.button(Material.BARRIER, "&c该生物没有生成点",
                    List.of("&f返回后点击“设为生成点”创建")));
        }
        if (window.hasPrevious()) {
            inventory.setItem(PREVIOUS_SLOT, MenuItems.button(Material.ARROW, "&a上一页",
                    List.of("&e点击翻到上一页!")));
        }
        if (window.hasNext()) {
            inventory.setItem(NEXT_SLOT, MenuItems.button(Material.ARROW, "&a下一页",
                    List.of("&e点击翻到下一页!")));
        }
        inventory.setItem(BACK_SLOT, MenuItems.button(Material.ARROW, "&a返回操作",
                List.of("&f回到生物操作页")));
    }

    private ItemStack mobIcon(MobSummary mob) {
        return MenuItems.button(MOB_ICON, "&e" + mob.displayName(),
                List.of("&7" + mob.internalName(), "&f左键选择此生物"));
    }

    private ItemStack spawnPointIcon(SpawnPoint point) {
        return MenuItems.button(Material.MOB_SPAWNER, "&e" + point.name(),
                List.of(
                        "&7" + point.internalName(),
                        "&7世界: " + point.worldName(),
                        "&7坐标: " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                        "&f左键传送 | 右键删除 | Shift+左键详情"
                ));
    }

    private void handleMobClick(Player player, InventoryClickEvent event, MenuService menus) {
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        PageWindow window = PageWindow.of(mobs.size(), CONTENT_SLOTS.size(), page);
        if (click == ClickType.LEFT && slot == PREVIOUS_SLOT && window.hasPrevious()) {
            page--;
            menus.refresh(player);
            return;
        }
        if (click == ClickType.LEFT && slot == NEXT_SLOT && window.hasNext()) {
            page++;
            menus.refresh(player);
            return;
        }
        if (click == ClickType.LEFT && slot == BACK_SLOT) {
            menus.close(player);
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) {
            return;
        }
        int offset = CONTENT_SLOTS.indexOf(slot);
        if (offset < 0) {
            return;
        }
        int index = window.startIndex() + offset;
        if (index >= window.endIndex()) {
            return;
        }
        selectedMob = mobs.get(index).internalName();
        phase = Phase.SELECT_ACTION;
        page = 0;
        menus.refresh(player);
    }

    private void handleActionClick(Player player, InventoryClickEvent event, MenuService menus) {
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            phase = Phase.SELECT_MOB;
            selectedMob = "";
            page = 0;
            menus.refresh(player);
            return;
        }
        switch (slot) {
            case SPAWN_SLOT -> spawn(player, menus);
            case SET_SPAWN_SLOT -> setSpawnPoint(player, menus);
            case KILL_SLOT -> killNearby(player, menus);
            case SPAWNERS_SLOT -> openSpawnPoints(player, menus);
            case INFO_SLOT -> info(player, menus);
            default -> {
            }
        }
    }

    private void handleSpawnPointClick(Player player, InventoryClickEvent event, MenuService menus) {
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        PageWindow window = PageWindow.of(spawnPoints.size(), CONTENT_SLOTS.size(), page);
        if (click == ClickType.LEFT && slot == PREVIOUS_SLOT && window.hasPrevious()) {
            page--;
            menus.refresh(player);
            return;
        }
        if (click == ClickType.LEFT && slot == NEXT_SLOT && window.hasNext()) {
            page++;
            menus.refresh(player);
            return;
        }
        if (click == ClickType.LEFT && slot == BACK_SLOT) {
            phase = Phase.SELECT_ACTION;
            page = 0;
            menus.refresh(player);
            return;
        }
        int offset = CONTENT_SLOTS.indexOf(slot);
        if (offset < 0) {
            return;
        }
        int index = window.startIndex() + offset;
        if (index >= window.endIndex()) {
            return;
        }
        SpawnPoint point = spawnPoints.get(index);
        switch (click) {
            case LEFT -> teleport(player, point);
            case RIGHT -> deleteSpawnPoint(player, point, menus);
            case SHIFT_LEFT -> showSpawnPointInfo(player, point);
            default -> {
            }
        }
    }

    private void spawn(Player player, MenuService menus) {
        boolean spawned = service.spawnAt(player, selectedMob);
        player.sendMessage(spawned
                ? ChatColor.GREEN + "已生成 MM 生物: " + selectedMob
                : ChatColor.RED + "生成失败: " + selectedMob);
        menus.close(player);
    }

    private void setSpawnPoint(Player player, MenuService menus) {
        boolean created = service.createSpawnPoint(player, selectedMob);
        player.sendMessage(created
                ? ChatColor.GREEN + "已在当前位置设置生成点: " + selectedMob
                : ChatColor.RED + "设置生成点失败: " + selectedMob);
        menus.close(player);
    }

    private void killNearby(Player player, MenuService menus) {
        int killed = service.killNearby(player, selectedMob);
        player.sendMessage(ChatColor.GREEN + "已击杀 " + killed + " 只 " + selectedMob);
        menus.close(player);
    }

    private void openSpawnPoints(Player player, MenuService menus) {
        phase = Phase.MANAGE_SPAWNERS;
        page = 0;
        menus.refresh(player);
    }

    private void teleport(Player player, SpawnPoint point) {
        Location location = service.spawnPointLocation(point.name()).orElse(null);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "无法传送: 生成点所在世界未加载或已不存在");
            return;
        }
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "已传送到生成点 " + point.name());
    }

    private void deleteSpawnPoint(Player player, SpawnPoint point, MenuService menus) {
        boolean removed = service.deleteSpawnPoint(point.name());
        player.sendMessage(removed
                ? ChatColor.GREEN + "已删除生成点 " + point.name()
                : ChatColor.RED + "删除失败: 生成点已不存在 " + point.name());
        menus.refresh(player);
    }

    private void showSpawnPointInfo(Player player, SpawnPoint point) {
        player.sendMessage(ChatColor.YELLOW + "生成点: " + point.name());
        player.sendMessage(ChatColor.YELLOW + "生物: " + point.internalName()
                + " | 世界: " + point.worldName()
                + " | 坐标: " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z());
    }

    private void info(Player player, MenuService menus) {
        MobSummary summary = service.find(selectedMob).orElse(null);
        if (summary == null) {
            player.sendMessage(ChatColor.RED + "MM 生物不存在: " + selectedMob);
        } else {
            player.sendMessage(ChatColor.YELLOW + "MM 生物: " + summary.displayName()
                    + " (" + summary.internalName() + ")");
        }
        menus.close(player);
    }

    private void applyBorder(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.STAINED_GLASS_PANE, 1, BLACK_GLASS);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("");
            glass.setItemMeta(meta);
        }
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, glass);
        }
    }
}
