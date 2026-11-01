package cn.mythicland.dreamrpg.mob;

import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.pagination.FolderLayout;
import cn.mythicland.framework.pagination.PageWindow;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;

import static cn.mythicland.framework.pagination.MenuPageLayout.BACK_SLOT;
import static cn.mythicland.framework.pagination.MenuPageLayout.CONTENT_SLOTS;
import static cn.mythicland.framework.pagination.MenuPageLayout.NEXT_SLOT;
import static cn.mythicland.framework.pagination.MenuPageLayout.PREVIOUS_SLOT;
import static cn.mythicland.framework.pagination.MenuPageLayout.SIZE;
import static cn.mythicland.framework.pagination.MenuPageLayout.forEachEntry;

/** Paged mob folders, read-only attribute and equipment inspection, and native spawner actions. */
public final class MythicMobMenu extends InventoryMenu {

    private static final Material MOB_ICON = Material.MONSTER_EGG;
    private static final short BLACK_GLASS = 15;
    private static final List<Integer> BORDER_SLOTS = List.of(18, 27, 36, 26, 35, 44);
    private static final int SELECTED_SLOT = 13;
    private static final int SPAWN_SLOT = 20;
    private static final int SET_SPAWN_SLOT = 21;
    private static final int KILL_SLOT = 22;
    private static final int SPAWNERS_SLOT = 23;
    private static final int INFO_SLOT = 24;
    private static final int EQUIPMENT_SLOT = 16;

    private enum Phase {
        SELECT_MOB,
        SELECT_ACTION,
        MANAGE_SPAWNERS,
        ATTRIBUTES,
        EQUIPMENT
    }

    private final MythicMobService service;
    private Phase phase = Phase.SELECT_MOB;
    private List<MobSummary> mobs = List.of();
    private List<SpawnPoint> spawnPoints = List.of();
    private String selectedMob = "";
    private final Set<String> expanded = new HashSet<>();
    private List<FolderLayout.Row> rows = List.of();
    private int selectionPage;
    private int page;

    public MythicMobMenu(MythicMobService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /**
     * Opens the menu and resets to the mob selection phase.
     */
    public void open(Player player, MenuManager menus) {
        phase = Phase.SELECT_MOB;
        selectedMob = "";
        page = 0;
        menus.open(player, this);
    }

    @Override
    public String buildTitle(Player player) {
        return "DreamRPG 生物库";
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
        if (phase != Phase.SELECT_MOB) {
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
            case ATTRIBUTES -> renderDetails(inventory, false);
            case EQUIPMENT -> renderDetails(inventory, true);
        }
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (!player.hasPermission("dreamrpg.admin")) {
            menus.close(player);
            return;
        }
        switch (phase) {
            case SELECT_MOB -> handleMobClick(player, event, menus);
            case SELECT_ACTION -> handleActionClick(player, event, menus);
            case MANAGE_SPAWNERS -> handleSpawnPointClick(player, event, menus);
            case ATTRIBUTES, EQUIPMENT -> handleDetailsClick(player, event, menus);
        }
    }

    private void renderMobSelection(Inventory inventory) {
        mobs = service.listMobs();
        Map<String, MobSummary> byId = mobs.stream().collect(Collectors.toMap(MobSummary::internalName, mob -> mob));
        rows = MythicMobService.layout(mobs).rows(expanded, ignored -> true);
        PageWindow window = forEachEntry(rows, page, (slot, row) -> {
            MobSummary mob = byId.get(row.entryId());
            List<String> lore = new ArrayList<>(List.of(
                    "&7" + mob.internalName(), "&7类型: " + mob.entityType(),
                    "&7来源: " + mob.sourceFile(), "&f左键查看生物"));
            lore.add("&6文件夹: " + mob.folderName());
            if (expanded.contains(row.folderId())) lore.add("&e右键收起");
            else lore.add("&e右键展开 (" + row.count() + " 个生物)");
            ItemStack icon = MenuItems.button(MOB_ICON, mob.displayName(), lore);
            icon.setAmount(row.iconAmount());
            inventory.setItem(slot, icon);
        });
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
                List.of("&7" + selected.internalName(), "&7类型: " + selected.entityType(), "&7来源: " + selected.sourceFile())));
        inventory.setItem(EQUIPMENT_SLOT, MenuItems.button(Material.DIAMOND_CHESTPLATE, "&e穿着装备", List.of("&f查看装备部位、物品及原始配置")));
        inventory.setItem(SPAWN_SLOT, MenuItems.button(Material.EMERALD, "&a生成一只",
                List.of("&f在准星处生成 1 只 1 级生物")));
        inventory.setItem(SET_SPAWN_SLOT, MenuItems.button(Material.MOB_SPAWNER, "&a设为生成点",
                List.of("&f将玩家当前位置设为该生物生成点")));
        inventory.setItem(KILL_SLOT, MenuItems.button(Material.REDSTONE_BLOCK, "&c击杀附近",
                List.of("&f击杀 32 格内同类型生物")));
        inventory.setItem(SPAWNERS_SLOT, MenuItems.button(Material.COMPASS, "&e生成点列表",
                List.of("&f查看并管理该生物所有生成点")));
        inventory.setItem(INFO_SLOT, MenuItems.button(Material.BOOK, "&e全部属性",
                List.of("&f查看生命、伤害、选项、技能和掉落等配置")));
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

    private ItemStack spawnPointIcon(SpawnPoint point) {
        return MenuItems.button(Material.MOB_SPAWNER, "&e" + point.name(),
                List.of(
                        "&7" + point.internalName(),
                        "&7世界: " + point.worldName(),
                        "&7坐标: " + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                        "&f左键传送 | 右键删除 | Shift+左键详情"
                ));
    }

    private void handleMobClick(Player player, InventoryClickEvent event, MenuManager menus) {
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        PageWindow window = PageWindow.of(rows.size(), CONTENT_SLOTS.size(), page);
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
        int offset = CONTENT_SLOTS.indexOf(slot);
        if (offset < 0) {
            return;
        }
        int index = window.startIndex() + offset;
        if (index >= window.endIndex()) {
            return;
        }
        FolderLayout.Row row = rows.get(index);
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            if (!expanded.add(row.folderId())) expanded.remove(row.folderId());
            menus.refresh(player);
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        selectedMob = row.entryId();
        selectionPage = page;
        phase = Phase.SELECT_ACTION;
        page = 0;
        menus.refresh(player);
    }

    private void handleActionClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (event.getClick() != ClickType.LEFT) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            phase = Phase.SELECT_MOB;
            selectedMob = "";
            page = selectionPage;
            menus.refresh(player);
            return;
        }
        switch (slot) {
            case SPAWN_SLOT -> spawn(player, menus);
            case SET_SPAWN_SLOT -> setSpawnPoint(player, menus);
            case KILL_SLOT -> killNearby(player, menus);
            case SPAWNERS_SLOT -> openSpawnPoints(player, menus);
            case INFO_SLOT -> openDetails(player, menus, Phase.ATTRIBUTES);
            case EQUIPMENT_SLOT -> openDetails(player, menus, Phase.EQUIPMENT);
            default -> {
            }
        }
    }

    private void handleSpawnPointClick(Player player, InventoryClickEvent event, MenuManager menus) {
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

    private void spawn(Player player, MenuManager menus) {
        boolean spawned = service.spawnAt(player, selectedMob);
        player.sendMessage(spawned
                ? ChatColor.GREEN + "已生成 MM 生物: " + selectedMob
                : ChatColor.RED + "生成失败: " + selectedMob);
        menus.close(player);
    }

    private void setSpawnPoint(Player player, MenuManager menus) {
        boolean created = service.createSpawnPoint(player, selectedMob);
        player.sendMessage(created
                ? ChatColor.GREEN + "已在当前位置设置生成点: " + selectedMob
                : ChatColor.RED + "设置生成点失败: " + selectedMob);
        menus.close(player);
    }

    private void killNearby(Player player, MenuManager menus) {
        int killed = service.killNearby(player, selectedMob);
        player.sendMessage(ChatColor.GREEN + "已击杀 " + killed + " 只 " + selectedMob);
        menus.close(player);
    }

    private void openSpawnPoints(Player player, MenuManager menus) {
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

    private void deleteSpawnPoint(Player player, SpawnPoint point, MenuManager menus) {
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

    private void openDetails(Player player, MenuManager menus, Phase target) {
        phase = target;
        page = 0;
        menus.refresh(player);
    }

    private void renderDetails(Inventory inventory, boolean equipment) {
        MobDetails details = service.details(selectedMob).orElseThrow();
        PageWindow window;
        if (equipment) {
            window = forEachEntry(details.equipment(), page, (slot, entry) -> {
                ItemStack icon = MenuItems.icon(entry.preview());
                ItemMeta meta = icon.getItemMeta();
                List<String> lore = new ArrayList<>();
                if (meta.hasLore()) lore.addAll(meta.getLore());
                lore.add(ChatColor.GRAY + "部位: " + entry.slot());
                lore.addAll(valueLines(entry.declaration()));
                meta.setLore(lore);
                icon.setItemMeta(meta);
                inventory.setItem(slot, icon);
            });
        } else {
            window = forEachEntry(details.attributes(), page, (slot, attribute) -> inventory.setItem(slot,
                    MenuItems.button(Material.PAPER, "&e" + attribute.name(), valueLines(attribute.value()))));
        }
        page = window.page();
        if (window.totalEntryCount() == 0) inventory.setItem(SELECTED_SLOT, MenuItems.button(Material.BARRIER, "&7没有配置穿着装备", List.of()));
        if (window.hasPrevious()) inventory.setItem(PREVIOUS_SLOT, MenuItems.button(Material.ARROW, "&a上一页", List.of()));
        if (window.hasNext()) inventory.setItem(NEXT_SLOT, MenuItems.button(Material.ARROW, "&a下一页", List.of()));
        inventory.setItem(BACK_SLOT, MenuItems.button(Material.ARROW, "&a返回生物操作", List.of()));
    }

    private static List<String> valueLines(String value) {
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            for (int offset = 0; offset < line.length(); offset += 64)
                lines.add(ChatColor.WHITE + line.substring(offset, Math.min(offset + 64, line.length())));
        }
        return List.copyOf(lines);
    }

    private void handleDetailsClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (event.getClick() != ClickType.LEFT) return;
        if (event.getRawSlot() == BACK_SLOT) {
            phase = Phase.SELECT_ACTION;
            page = 0;
        } else if (event.getRawSlot() == PREVIOUS_SLOT && page > 0) page--;
        else if (event.getRawSlot() == NEXT_SLOT) page++;
        else return;
        menus.refresh(player);
    }

    private void applyBorder(Inventory inventory) {
        ItemStack glass = MenuItems.button(Material.STAINED_GLASS_PANE, BLACK_GLASS, "", List.of());
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, glass);
        }
    }
}
