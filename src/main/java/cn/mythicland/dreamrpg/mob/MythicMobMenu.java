package cn.mythicland.dreamrpg.mob;

import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.pagination.FolderLayout;
import cn.mythicland.framework.pagination.MenuPageLayout;
import cn.mythicland.framework.pagination.PageWindow;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Paged mob library with RPG attribute and equipment inspection plus native spawner actions.
 *
 * <p>The library uses the shared four-by-seven folder layout. The selected mob's actions are
 * arranged in one horizontal row. Destructive actions need a second click on the same button.</p>
 */
public final class MythicMobMenu extends InventoryMenu {

    private static final List<Integer> CONTENT_SLOTS = MenuPageLayout.CONTENT_SLOTS;
    private static final int PREVIOUS_PAGE_SLOT = MenuPageLayout.PREVIOUS_SLOT;
    private static final int NEXT_PAGE_SLOT = MenuPageLayout.NEXT_SLOT;
    private static final int ACTION_BACK_BUTTON_SLOT = 26;
    /**
     * Drawn only while a list has no entries, so it never covers a real entry.
     */
    private static final int EMPTY_CONTENT_SLOT = 22;
    private static final int ACTION_SELECTED_MOB_SLOT = 10;
    private static final int ACTION_ATTRIBUTES_SLOT = 11;
    private static final int ACTION_EQUIPMENT_SLOT = 12;
    private static final int ACTION_SPAWNERS_SLOT = 13;
    private static final int ACTION_SPAWN_SLOT = 14;
    private static final int ACTION_SET_SPAWNER_SLOT = 15;
    private static final int ACTION_KILL_SLOT = 16;
    /**
     * Arms the irreversible nearby kill on the same button instead of opening a second page.
     */
    private static final String KILL_ACTION = "kill-nearby";
    /**
     * Equipment-derived stats are listed in a stable, player-facing order.
     */
    private static final List<CombatStat> EQUIPMENT_ATTRIBUTE_ORDER = List.of(
            CombatStat.PHYSICAL_DEFENSE,
            CombatStat.MAGIC_DEFENSE,
            CombatStat.DEFENSE,
            CombatStat.MAGIC_DAMAGE,
            CombatStat.CRIT_CHANCE,
            CombatStat.CRIT_DAMAGE,
            CombatStat.LIFESTEAL,
            CombatStat.HEALTH_REGEN,
            CombatStat.MOVEMENT_SPEED,
            CombatStat.EXPERIENCE_BONUS,
            CombatStat.PHYSICAL_PENETRATION_PERCENT,
            CombatStat.MAGIC_PENETRATION_PERCENT
    );
    private final MythicMobService service;
    private final CombatApi combat;
    private final Set<String> expanded = new HashSet<>();
    private Phase phase = Phase.SELECT_MOB;
    private List<FolderLayout.Row> rows = List.of();
    private List<SpawnPoint> spawnPoints = List.of();
    private MobDetails details;
    private String selectedMob = "";
    private String armedDeletion = "";
    private int selectionPage;
    private int page;

    public MythicMobMenu(MythicMobService service, CombatApi combat) {
        this.service = Objects.requireNonNull(service, "service");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    private static ItemStack backButton(String description) {
        return MenuItems.button(Material.ARROW, "&a返回", List.of(description, " ", "&e点击返回"));
    }

    private static ItemStack mobIcon(MobSummary mob, List<String> lore) {
        return MenuItems.button(Material.MONSTER_EGG, spawnEggData(mob.entityType()), mob.displayName(), lore);
    }

    // 1.12 has no typed spawn eggs, so the egg's data value selects which mob silhouette is shown.
    @SuppressWarnings("deprecation")
    private static short spawnEggData(String entityType) {
        EntityType type = resolveEntityType(entityType);
        return type != null && type.isSpawnable() ? type.getTypeId() : 0;
    }

    // MM reports either Bukkit enum names (IRON_GOLEM) or legacy names (villager_golem), so try both.
    @SuppressWarnings("deprecation")
    private static EntityType resolveEntityType(String entityType) {
        String name = entityType.trim();
        for (EntityType candidate : EntityType.values()) {
            if (candidate.name().equalsIgnoreCase(name) || candidate.getName().equalsIgnoreCase(name)) return candidate;
        }
        return null;
    }

    private static String entityTypeName(MobSummary mob) {
        return mob.entityType().toUpperCase(Locale.ROOT);
    }

    private static List<String> valueLines(String value) {
        List<String> lines = new ArrayList<>();
        for (String line : value.split("\\R", -1)) {
            for (int offset = 0; offset < line.length(); offset += 64)
                lines.add(ChatColor.WHITE + line.substring(offset, Math.min(offset + 64, line.length())));
        }
        return List.copyOf(lines);
    }

    /**
     * Renders an attribute value as a highlighted number instead of a raw configuration string.
     */
    private static String formatRange(NumericRange range) {
        String value = range.isFixed()
                ? formatNumber(range.minimum())
                : formatNumber(range.minimum()) + " - " + formatNumber(range.maximum());
        return range.percent() ? value + "%" : value;
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }

    private static String attributeValue(MobDetails rendered, String name) {
        for (MobDetails.Attribute attribute : rendered.attributes()) {
            if (attribute.name().equals(name)) return attribute.value();
        }
        return null;
    }

    private static NumericRange sum(NumericRange first, NumericRange second) {
        return new NumericRange(
                first.minimum() + second.minimum(),
                first.maximum() + second.maximum(),
                first.percent() || second.percent()
        );
    }

    /**
     * Opens the menu and resets to the mob selection phase.
     */
    public void open(Player player, MenuManager menus) {
        phase = Phase.SELECT_MOB;
        selectedMob = "";
        armedDeletion = "";
        page = 0;
        menus.open(player, this);
    }

    @Override
    public String buildTitle(Player player) {
        return "DreamRPG 生物库";
    }

    @Override
    public int size(Player player) {
        currentMob();
        int count = switch (phase) {
            case SELECT_MOB -> CONTENT_SLOTS.size();
            case SELECT_ACTION -> 7;
            case MANAGE_SPAWNERS -> service.listSpawnPoints(selectedMob).size();
            case ATTRIBUTES -> rpgAttributes(details).size();
            case EQUIPMENT -> details.equipment().size();
        };
        return MenuPageLayout.compactSize(count);
    }

    @Override
    public void render(Player player, Inventory inventory) {
        inventory.clear();
        MobSummary selected = currentMob();
        switch (phase) {
            case SELECT_MOB -> renderMobSelection(inventory);
            case SELECT_ACTION -> renderActions(inventory, selected);
            case MANAGE_SPAWNERS -> renderSpawnPoints(inventory);
            case ATTRIBUTES -> renderAttributes(inventory);
            case EQUIPMENT -> renderEquipment(inventory);
        }
    }

    /**
     * @return the selected mob, or null once the library page has been restored after a reload
     */
    private MobSummary currentMob() {
        if (phase == Phase.SELECT_MOB) return null;
        MobSummary selected = service.find(selectedMob).orElse(null);
        boolean needsDetails = phase == Phase.ATTRIBUTES || phase == Phase.EQUIPMENT;
        if (selected != null && (!needsDetails || loadDetails(selected))) return selected;
        phase = Phase.SELECT_MOB;
        selectedMob = "";
        armedDeletion = "";
        details = null;
        page = 0;
        return null;
    }

    private boolean loadDetails(MobSummary selected) {
        details = service.details(selected.internalName()).orElse(null);
        return details != null;
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
        List<MobSummary> mobs = service.listMobs();
        Map<String, MobSummary> byId = mobs.stream()
                .collect(LinkedHashMap::new, (map, mob) -> map.put(mob.internalName(), mob), Map::putAll);
        rows = MythicMobService.layout(mobs).rows(expanded, ignored -> true);
        PageWindow window = PageWindow.of(rows.size(), CONTENT_SLOTS.size(), page);
        for (int index = window.startIndex(); index < window.endIndex(); index++) {
            int slot = CONTENT_SLOTS.get(index - window.startIndex());
            inventory.setItem(slot, libraryIcon(rows.get(index), byId));
        }
        page = window.page();
        if (mobs.isEmpty()) {
            inventory.setItem(EMPTY_CONTENT_SLOT, MenuItems.button(Material.BARRIER, "&c没有已加载的 MM 生物",
                            List.of("&7请确认 MythicMobs 已加载生物")
                    )
            );
        }
        renderArrows(inventory, window);
    }

    private ItemStack libraryIcon(FolderLayout.Row row, Map<String, MobSummary> byId) {
        MobSummary mob = byId.get(row.entryId());
        if (mob == null) return MenuItems.button(Material.BARRIER, "&c生物已卸载", List.of("&7请重新打开生物库"));
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + mob.internalName());
        lore.add("&7类型: &f" + entityTypeName(mob));
        lore.add("&7分组: &f" + mob.folderName());
        if (row.folderId() != null) {
            lore.add("&7生物数: &f" + row.count());
        }
        lore.add(" ");
        lore.add("&e点击查看详情!");
        ItemStack icon = mobIcon(mob, lore);
        icon.setAmount(row.iconAmount());
        return icon;
    }

    private void renderActions(Inventory inventory, MobSummary selected) {
        renderHeader(inventory, selected, null, ACTION_SELECTED_MOB_SLOT);
        inventory.setItem(ACTION_ATTRIBUTES_SLOT, MenuItems.button(Material.BOOK, "&a生物详情",
                        List.of("&7查看生命、攻击与防御属性", " ", "&e点击查看!")
                )
        );
        inventory.setItem(ACTION_EQUIPMENT_SLOT, MenuItems.button(Material.DIAMOND_CHESTPLATE, "&a穿着装备",
                        List.of("&7查看装备部位与物品", " ", "&e点击查看!")
                )
        );
        inventory.setItem(ACTION_SPAWNERS_SLOT, MenuItems.button(Material.COMPASS, "&a生成点列表",
                        List.of("&7查看并管理该生物的全部生成点", " ", "&e点击查看!")
                )
        );
        inventory.setItem(ACTION_SPAWN_SLOT, MenuItems.button(Material.MONSTER_EGG, "&a点击生成",
                        List.of("&7在准星处生成一只该生物", " ", "&e点击生成!")
                )
        );
        inventory.setItem(ACTION_SET_SPAWNER_SLOT, MenuItems.button(Material.MOB_SPAWNER, "&a设为生成点",
                        List.of("&7把当前位置设为该生物的生成点", " ", "&e点击设置!")
                )
        );
        boolean armed = armedDeletion.equals(KILL_ACTION);
        inventory.setItem(ACTION_KILL_SLOT, MenuItems.button(Material.DIAMOND_SWORD,
                        armed ? "&c确认击杀" : "&c击杀附近",
                        List.of("&7击杀附近的同种生物", " ", armed ? "&c再次点击以确认!" : "&e点击操作!")
                )
        );
        inventory.setItem(ACTION_BACK_BUTTON_SLOT, backButton("&7返回生物列表"));
    }

    private void renderSpawnPoints(Inventory inventory) {
        spawnPoints = service.listSpawnPoints(selectedMob);
        PageWindow window = PageWindow.of(spawnPoints.size(), CONTENT_SLOTS.size(), page);
        for (int index = window.startIndex(); index < window.endIndex(); index++) {
            int slot = CONTENT_SLOTS.get(index - window.startIndex());
            inventory.setItem(slot, spawnPointIcon(spawnPoints.get(index)));
        }
        page = window.page();
        renderArrows(inventory, window);
        inventory.setItem(inventory.getSize() - 1, backButton("&7返回生物操作"));
    }

    private ItemStack spawnPointIcon(SpawnPoint point) {
        boolean armed = armedDeletion.equals(point.name());
        return MenuItems.button(Material.MOB_SPAWNER, "&a" + point.name(), List.of(
                        "&7世界: &f" + point.worldName(),
                        "&7坐标: &f" + (int) point.x() + ", " + (int) point.y() + ", " + (int) point.z(),
                        "&7生物: &f" + point.internalName(),
                        " ",
                        "&e左键传送 &8| " + (armed ? "&c再次右键确认删除" : "&c右键删除")
                )
        );
    }

    private void renderAttributes(Inventory inventory) {
        MobDetails rendered = Objects.requireNonNull(details, "details");
        List<MobDetails.Attribute> attributes = rpgAttributes(rendered);
        PageWindow window = PageWindow.of(attributes.size(), CONTENT_SLOTS.size(), page);
        for (int index = window.startIndex(); index < window.endIndex(); index++) {
            int slot = CONTENT_SLOTS.get(index - window.startIndex());
            MobDetails.Attribute attribute = attributes.get(index);
            String color = "物理攻击".equals(attribute.name()) ? "&6" : "&a";
            inventory.setItem(slot, MenuItems.button(Material.PAPER, color + attribute.name(),
                            List.of("&7数值: &f" + attribute.value())
                    )
            );
        }
        page = window.page();
        renderArrows(inventory, window);
        inventory.setItem(inventory.getSize() - 1, backButton("&7返回生物操作"));
    }

    /**
     * Combines the configured base stats with the RPG attributes contributed by worn equipment.
     * Defence rows only appear when the mob actually wears equipment carrying that attribute.
     */
    private List<MobDetails.Attribute> rpgAttributes(MobDetails rendered) {
        List<MobDetails.Attribute> result = new ArrayList<>();
        String health = attributeValue(rendered, MythicMobService.HEALTH_ATTRIBUTE);
        if (health != null) result.add(new MobDetails.Attribute("生命值", health));
        String damage = attributeValue(rendered, MythicMobService.DAMAGE_ATTRIBUTE);
        if (damage != null) result.add(new MobDetails.Attribute("物理攻击", damage));
        Map<CombatStat, NumericRange> equipmentStats = equipmentAttributes(rendered);
        for (CombatStat stat : EQUIPMENT_ATTRIBUTE_ORDER) {
            NumericRange value = equipmentStats.get(stat);
            if (value != null) result.add(new MobDetails.Attribute(stat.defaultDisplayName(), formatRange(value)));
        }
        return List.copyOf(result);
    }

    private Map<CombatStat, NumericRange> equipmentAttributes(MobDetails rendered) {
        Map<CombatStat, NumericRange> totals = new EnumMap<>(CombatStat.class);
        for (MobDetails.Equipment entry : rendered.equipment()) {
            CombatItemStats parsed = combat.parseItem(entry.preview());
            parsed.values().forEach((stat, range) -> totals.merge(stat, range, MythicMobMenu::sum));
        }
        return totals;
    }

    private void renderEquipment(Inventory inventory) {
        MobDetails rendered = Objects.requireNonNull(details, "details");
        PageWindow window = PageWindow.of(rendered.equipment().size(), CONTENT_SLOTS.size(), page);
        for (int index = window.startIndex(); index < window.endIndex(); index++) {
            int slot = CONTENT_SLOTS.get(index - window.startIndex());
            inventory.setItem(slot, equipmentIcon(rendered.equipment().get(index)));
        }
        page = window.page();
        renderArrows(inventory, window);
        inventory.setItem(inventory.getSize() - 1, backButton("&7返回生物操作"));
    }

    private ItemStack equipmentIcon(MobDetails.Equipment entry) {
        ItemStack icon = MenuItems.icon(entry.preview());
        ItemMeta meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.getLore());
            lore.add(" ");
        }
        lore.add(ChatColor.GRAY + "部位: " + entry.slot());
        lore.addAll(valueLines(entry.declaration()));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private void renderHeader(Inventory inventory, MobSummary mob, String footer, int slot) {
        List<String> lore = new ArrayList<>();
        lore.add("&7ID: &f" + mob.internalName());
        lore.add("&7类型: &f" + entityTypeName(mob));
        lore.add("&7分组: &f" + mob.folderName());
        if (footer != null) {
            lore.add(" ");
            lore.add(footer);
        }
        inventory.setItem(slot, mobIcon(mob, lore));
    }

    private void renderArrows(Inventory inventory, PageWindow window) {
        if (window.hasPrevious()) {
            inventory.setItem(previousPageSlot(inventory.getSize()), MenuItems.button(Material.ARROW, "&a上一页",
                            List.of("&7查看上一页内容.", " ", "&e点击查看上一页!")
                    )
            );
        }
        if (window.hasNext()) {
            inventory.setItem(nextPageSlot(inventory.getSize()), MenuItems.button(Material.ARROW, "&a下一页",
                            List.of("&7查看下一页内容.", " ", "&e点击查看下一页!")
                    )
            );
        }
    }

    private void handleMobClick(Player player, InventoryClickEvent event, MenuManager menus) {
        PageWindow window = PageWindow.of(rows.size(), CONTENT_SLOTS.size(), page);
        if (tryTurnPage(player, event, menus, window)) return;
        int index = contentIndex(event.getRawSlot(), window);
        if (index < 0) return;
        ClickType click = event.getClick();
        FolderLayout.Row row = rows.get(index);
        if ((click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) && row.folderId() != null) {
            if (!expanded.add(row.folderId())) expanded.remove(row.folderId());
            menus.refresh(player);
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        selectedMob = row.entryId();
        selectionPage = page;
        phase = Phase.SELECT_ACTION;
        armedDeletion = "";
        page = 0;
        menus.refresh(player);
    }

    private void handleActionClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (event.getClick() != ClickType.LEFT) return;
        switch (event.getRawSlot()) {
            case ACTION_ATTRIBUTES_SLOT -> openPhase(player, menus, Phase.ATTRIBUTES);
            case ACTION_EQUIPMENT_SLOT -> openPhase(player, menus, Phase.EQUIPMENT);
            case ACTION_SPAWNERS_SLOT -> openPhase(player, menus, Phase.MANAGE_SPAWNERS);
            case ACTION_SPAWN_SLOT -> spawn(player, menus);
            case ACTION_SET_SPAWNER_SLOT -> setSpawnPoint(player, menus);
            case ACTION_KILL_SLOT -> killNearby(player, menus);
            case ACTION_BACK_BUTTON_SLOT -> returnToSelection(player, menus);
            default -> {
            }
        }
    }

    private void handleSpawnPointClick(Player player, InventoryClickEvent event, MenuManager menus) {
        PageWindow window = PageWindow.of(spawnPoints.size(), CONTENT_SLOTS.size(), page);
        if (tryTurnPage(player, event, menus, window)) return;
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        if (click == ClickType.LEFT && slot == size(player) - 1) {
            openPhase(player, menus, Phase.SELECT_ACTION);
            return;
        }
        int index = contentIndex(slot, window);
        if (index < 0) return;
        SpawnPoint point = spawnPoints.get(index);
        switch (click) {
            case LEFT -> teleport(player, point);
            case RIGHT -> deleteSpawnPoint(player, point, menus);
            default -> {
            }
        }
    }

    private void handleDetailsClick(Player player, InventoryClickEvent event, MenuManager menus) {
        if (event.getClick() != ClickType.LEFT) return;
        if (event.getRawSlot() == size(player) - 1) {
            openPhase(player, menus, Phase.SELECT_ACTION);
            return;
        }
        MobDetails rendered = Objects.requireNonNull(details, "details");
        int total = phase == Phase.EQUIPMENT ? rendered.equipment().size() : rpgAttributes(rendered).size();
        tryTurnPage(player, event, menus, PageWindow.of(total, CONTENT_SLOTS.size(), page));
    }

    private int contentIndex(int slot, PageWindow window) {
        int offset = CONTENT_SLOTS.indexOf(slot);
        if (offset < 0) return -1;
        int index = window.startIndex() + offset;
        return index < window.endIndex() ? index : -1;
    }

    /**
     * Applies a left-click on the previous or next arrow, keeping the page inside the window.
     *
     * @return whether the click was a page turn, so the caller stops handling the click
     */
    private boolean tryTurnPage(Player player, InventoryClickEvent event, MenuManager menus, PageWindow window) {
        if (event.getClick() != ClickType.LEFT) return false;
        int slot = event.getRawSlot();
        int menuSize = size(player);
        if (slot == previousPageSlot(menuSize) && window.hasPrevious()) page--;
        else if (slot == nextPageSlot(menuSize) && window.hasNext()) page++;
        else return false;
        menus.refresh(player);
        return true;
    }

    private int previousPageSlot(int menuSize) {
        return phase == Phase.SELECT_MOB ? PREVIOUS_PAGE_SLOT : menuSize - 9;
    }

    private int nextPageSlot(int menuSize) {
        return phase == Phase.SELECT_MOB ? NEXT_PAGE_SLOT : menuSize - 2;
    }

    private void returnToSelection(Player player, MenuManager menus) {
        phase = Phase.SELECT_MOB;
        selectedMob = "";
        armedDeletion = "";
        page = selectionPage;
        menus.refresh(player);
    }

    private void openPhase(Player player, MenuManager menus, Phase target) {
        phase = target;
        armedDeletion = "";
        page = 0;
        menus.refresh(player);
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

    /**
     * Killing nearby mobs is irreversible, so the button arms on the first click like every other
     * destructive action in this menu.
     */
    private void killNearby(Player player, MenuManager menus) {
        if (!armedDeletion.equals(KILL_ACTION)) {
            armedDeletion = KILL_ACTION;
            menus.refresh(player);
            return;
        }
        armedDeletion = "";
        int killed = service.killNearby(player, selectedMob);
        player.sendMessage(ChatColor.GREEN + "已击杀 " + killed + " 只 " + selectedMob);
        menus.refresh(player);
    }

    private void teleport(Player player, SpawnPoint point) {
        Location location = service.spawnPointLocation(point.name()).orElse(null);
        if (location == null) {
            player.sendMessage(ChatColor.RED + "生成点所在世界未加载, 无法传送");
            return;
        }
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "已传送到生成点 " + point.name());
    }

    /**
     * The first right-click only arms the confirmation, so a misclick never removes a spawner.
     */
    private void deleteSpawnPoint(Player player, SpawnPoint point, MenuManager menus) {
        if (!armedDeletion.equals(point.name())) {
            armedDeletion = point.name();
            menus.refresh(player);
            return;
        }
        armedDeletion = "";
        boolean removed = service.deleteSpawnPoint(point.name());
        player.sendMessage(removed
                ? ChatColor.GREEN + "已删除生成点 " + point.name()
                : ChatColor.RED + "删除失败, 生成点已不存在: " + point.name());
        menus.refresh(player);
    }

    private enum Phase {
        SELECT_MOB,
        SELECT_ACTION,
        MANAGE_SPAWNERS,
        ATTRIBUTES,
        EQUIPMENT
    }
}
