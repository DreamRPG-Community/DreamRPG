package cn.mythicland.dreamrpg.mob;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.pagination.FolderLayout;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.adapters.bukkit.BukkitAdapter;
import io.lumine.xikage.mythicmobs.drops.EquipSlot;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Main-thread access to the loaded MM registry and its native spawner persistence.
 */
@InjectComponent
public final class MythicMobService {
    /**
     * Base attribute labels shared with the RPG attribute page.
     */
    static final String HEALTH_ATTRIBUTE = "基础生命值";
    static final String DAMAGE_ATTRIBUTE = "基础伤害";
    private final DreamRPGPlugin plugin;
    private final ContentWorkspace content;

    public MythicMobService(DreamRPGPlugin plugin, ContentWorkspace content) {
        this.plugin = plugin;
        this.content = content;
    }

    public List<MobSummary> listMobs() {
        MainThread.require("Mob library requires the primary thread");
        return MythicMobs.inst().getMobManager().getMobNames().stream()
                .sorted()
                .map(id -> summary(MythicMobs.inst().getMobManager().getMythicMob(id)))
                .toList();
    }

    public Optional<MobSummary> find(String id) {
        MainThread.require("Mob library requires the primary thread");
        return Optional.ofNullable(MythicMobs.inst().getMobManager().getMythicMob(id)).map(this::summary);
    }

    static FolderLayout layout(List<MobSummary> mobs) {
        Map<String, List<String>> folders = new LinkedHashMap<>();
        mobs.stream()
                .sorted(Comparator.comparing(MobSummary::sourceFile).thenComparing(MobSummary::internalName))
                .forEach(mob -> folders.computeIfAbsent(mob.folder(), ignored -> new ArrayList<>()).add(
                        mob.internalName()));
        List<FolderLayout.Entry> entries = new ArrayList<>();
        folders.forEach((folder, ids) -> entries.add(new FolderLayout.Entry(folder, entries.size(), true, ids)));
        return new FolderLayout(entries);
    }

    private MobSummary summary(MythicMob mob) {
        Path source = mob.getConfig().getFile().toPath().toAbsolutePath().normalize();
        if (!source.startsWith(content.root()))
            throw new IllegalStateException("Mob source is outside the content workspace: " + source);
        String name = mob.getDisplayName().get();
        if (name == null || name.isBlank()) name = mob.getInternalName();
        String relativeFile = content.root().relativize(source).toString().replace('\\', '/');
        return new MobSummary(mob.getInternalName(), name, mob.getEntityType(), relativeFile);
    }

    public Optional<MobDetails> details(String id) {
        MainThread.require("Mob details require the primary thread");
        MythicMob mob = MythicMobs.inst().getMobManager().getMythicMob(id);
        if (mob == null) return Optional.empty();
        List<MobDetails.Attribute> attributes = new ArrayList<>();
        attributes.add(new MobDetails.Attribute(HEALTH_ATTRIBUTE, statExpression(mob.getHealth().toString())));
        attributes.add(new MobDetails.Attribute(DAMAGE_ATTRIBUTE, statExpression(mob.getDamage().toString())));
        attributes.add(new MobDetails.Attribute("基础护甲", statExpression(mob.getArmor().toString())));
        attributes.add(new MobDetails.Attribute("每级生命值", String.valueOf(mob.getPerLevelHealth())));
        attributes.add(new MobDetails.Attribute("每级伤害", String.valueOf(mob.getPerLevelDamage())));
        attributes.add(new MobDetails.Attribute("每级护甲", String.valueOf(mob.getPerLevelArmor())));
        ConfigurationSection configuration = mob.getConfig().getFileConfiguration().getConfigurationSection(id);
        configuration.getValues(true).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Object value = entry.getValue();
                    if (value instanceof ConfigurationSection) return;
                    if (value instanceof List<?> values) {
                        for (int index = 0; index < values.size(); index++)
                            attributes.add(new MobDetails.Attribute(entry.getKey() + "[" + index + "]",
                                    String.valueOf(values.get(index))
                            ));
                    } else attributes.add(new MobDetails.Attribute(entry.getKey(), String.valueOf(value)));
                });
        List<MobDetails.Equipment> equipment = mob.getConfig().getStringList("Equipment").stream()
                .map(this::equipment)
                .toList();
        return Optional.of(new MobDetails(summary(mob), attributes, equipment));
    }

    private static String statExpression(String expression) {
        // MM 4.12 wraps even fixed numeric stats in RandomDouble; never sample it for a preview.
        if (!expression.startsWith("RandomDouble{") || !expression.endsWith("}")) return expression;
        String[] range = expression.substring("RandomDouble{".length(), expression.length() - 1).split(" to ", 2);
        String minimum = new BigDecimal(range[0]).stripTrailingZeros().toPlainString();
        String maximum = new BigDecimal(range[1]).stripTrailingZeros().toPlainString();
        return minimum.equals(maximum) ? minimum : minimum + " - " + maximum;
    }

    // MM 4.12 accepts numeric legacy material IDs and durability values.
    @SuppressWarnings("deprecation")
    private MobDetails.Equipment equipment(String declaration) {
        EquipmentReference reference = equipmentReference(declaration);
        ItemStack preview;
        if (plugin.items().configuration(reference.item()).isPresent())
            preview = plugin.items().getItemStack(reference.item(), 1);
        else {
            String[] parts = reference.item().split(":", 2);
            Material material;
            if (parts[0].matches("[0-9]+")) material = Material.getMaterial(Integer.parseInt(parts[0]));
            else material = Material.matchMaterial(parts[0]);
            if (material == null || material == Material.AIR) preview = new ItemStack(Material.BOOK);
            else {
                short data = 0;
                if (parts.length == 2 && parts[1].matches("[0-9]+")) data = Short.parseShort(parts[1]);
                preview = new ItemStack(material, 1, data);
            }
        }
        return new MobDetails.Equipment(reference.slot(), declaration, preview);
    }

    record EquipmentReference(String item, String slot) {
    }

    static EquipmentReference equipmentReference(String declaration) {
        String[] words = declaration.trim().split("\\s+");
        String item = words[0];
        String slot = "装备表 / 动态装备";
        int separator = item.lastIndexOf(':');
        String token = words.length > 1 ? words[1] : "";
        boolean namedSlot = false;
        for (EquipSlot candidate : EquipSlot.values())
            if (candidate != EquipSlot.NONE && candidate.matches(token)) namedSlot = true;
        if (!namedSlot && separator >= 0) token = item.substring(separator + 1);
        for (EquipSlot candidate : EquipSlot.values()) {
            if (candidate == EquipSlot.NONE || !candidate.matches(token)) continue;
            if (!namedSlot && separator >= 0) item = item.substring(0, separator);
            slot = switch (candidate) {
                case HEAD -> "头盔";
                case CHEST -> "胸甲";
                case LEGS -> "护腿";
                case FEET -> "靴子";
                case HAND -> "主手";
                case OFFHAND -> "副手";
                default -> throw new IllegalStateException("Unsupported equipment slot: " + candidate);
            };
            break;
        }
        return new EquipmentReference(item, slot);
    }

    public boolean spawnAt(Player player, String id) {
        MainThread.require("Mob spawning requires the primary thread");
        MythicMob mob = MythicMobs.inst().getMobManager().getMythicMob(id);
        if (mob == null) return false;
        Location target = player.getTargetBlock(null, 32).getLocation().add(0.5, 1, 0.5);
        return mob.spawn(BukkitAdapter.adapt(target), 1) != null;
    }

    public boolean createSpawnPoint(Player player, String id) {
        MainThread.require("Spawner creation requires the primary thread");
        if (find(id).isEmpty()) return false;
        var manager = MythicMobs.inst().getSpawnerManager();
        String name = id + "_" + UUID.randomUUID().toString().replace("-", "");
        var spawner = manager.createSpawner(name, player.getLocation(), id);
        if (spawner == null) return false;
        manager.saveSpawners();
        return true;
    }

    public int killNearby(Player player, String id) {
        MainThread.require("Mob removal requires the primary thread");
        int killed = 0;
        for (var mob : List.copyOf(MythicMobs.inst().getMobManager().getActiveMobs())) {
            if (!mob.getType().getInternalName().equals(id)) continue;
            var entity = BukkitAdapter.adapt(mob.getEntity());
            if (!(entity instanceof LivingEntity living) || living.isDead() || !living.getWorld().equals(
                    player.getWorld())) continue;
            if (living.getLocation().distanceSquared(player.getLocation()) > 32 * 32) continue;
            living.setHealth(0);
            killed++;
        }
        return killed;
    }

    public List<SpawnPoint> listSpawnPoints(String id) {
        MainThread.require("Spawner inspection requires the primary thread");
        return MythicMobs.inst().getSpawnerManager().getSpawners().stream()
                .filter(point -> Arrays.stream(point.getTypeName().split("[, ]+"))
                        .anyMatch(name -> name.split(":", 2)[0].equals(id)))
                .map(point -> new SpawnPoint(point.getName(), point.getTypeName(), point.getWorldName(),
                        point.getBlockX(), point.getBlockY(), point.getBlockZ()
                ))
                .sorted(Comparator.comparing(SpawnPoint::name))
                .toList();
    }

    public Optional<Location> spawnPointLocation(String name) {
        MainThread.require("Spawner teleport requires the primary thread");
        var point = MythicMobs.inst().getSpawnerManager().getSpawnerByName(name);
        if (point == null) return Optional.empty();
        var world = Bukkit.getWorld(point.getWorldName());
        if (world == null) return Optional.empty();
        return Optional.of(new Location(world, point.getBlockX() + 0.5, point.getBlockY(), point.getBlockZ() + 0.5));
    }

    public boolean deleteSpawnPoint(String name) {
        MainThread.require("Spawner removal requires the primary thread");
        var manager = MythicMobs.inst().getSpawnerManager();
        var point = manager.getSpawnerByName(name);
        return point != null && manager.removeSpawner(point);
    }
}
