package cn.mythicland.dreamrpgcombat.stats;

import cn.mythicland.dreamrpgcombat.api.CombatApi;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamrpgcombat.lore.CombatItemStats;
import cn.mythicland.dreamrpgcombat.lore.CombatLoreParser;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.dreamrpg.api.HealthSnapshot;
import cn.mythicland.dreamlib.item.ItemLoreReader;
import cn.mythicland.dreamlib.item.NumericRange;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Function;

public final class CombatStatsService implements CombatApi {

    private static final long INVALID_WARNING_COOLDOWN_MILLIS = 60_000L;

    private final JavaPlugin plugin;
    private final Function<UUID, OptionalLong> rpgLevelResolver;
    private final DreamRPGHealthResolver rpgHealthResolver;
    private final Map<String, Long> warningTimes = new HashMap<>();
    private volatile CombatLoreParser parser;
    private volatile CombatSettings settings;

    public CombatStatsService(JavaPlugin plugin, CombatSettings settings) {
        this(plugin, settings, null, null);
    }

    /**
     * Creates the combat attribute service.
     *
     * @param plugin           owning plugin
     * @param settings         immutable combat settings
     * @param rpgLevelResolver optional RPG level resolver; an empty result keeps restricted items
     *                         inactive until the player's DreamRPG data is ready
     */
    public CombatStatsService(
            JavaPlugin plugin,
            CombatSettings settings,
            Function<UUID, OptionalLong> rpgLevelResolver
    ) {
        this(plugin, settings, rpgLevelResolver, null);
    }

    /**
     * Creates the combat attribute service with optional DreamRPG level and health bridges.
     *
     * @param plugin            owning plugin
     * @param settings          immutable combat settings
     * @param rpgLevelResolver  optional RPG level resolver
     * @param rpgHealthResolver optional DreamRPG health resolver
     */
    public CombatStatsService(
            JavaPlugin plugin,
            CombatSettings settings,
            Function<UUID, OptionalLong> rpgLevelResolver,
            DreamRPGHealthResolver rpgHealthResolver
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.rpgLevelResolver = rpgLevelResolver;
        this.rpgHealthResolver = rpgHealthResolver;
        this.parser = new CombatLoreParser(settings);
    }

    static CombatStatsSnapshot aggregate(List<CombatItemStats> itemStats) {
        return aggregate(itemStats, null);
    }

    static CombatStatsSnapshot aggregate(List<CombatItemStats> itemStats, HealthSnapshot rpgHealth) {
        Objects.requireNonNull(itemStats, "itemStats");
        double damageMinimum = 0.0D;
        double damageMaximum = 0.0D;
        double defense = 0.0D;
        double health = 0.0D;
        double healthRegen = 0.0D;
        double critChance = 0.0D;
        double critDamage = 0.0D;
        double experienceBonus = 0.0D;
        boolean hasDefense = false;

        for (CombatItemStats item : itemStats) {
            NumericRange damage = item.value(CombatStat.DAMAGE).orElse(null);
            if (damage != null) {
                damageMinimum += damage.minimum();
                damageMaximum += damage.maximum();
            }
            NumericRange defenseValue = item.value(CombatStat.DEFENSE).orElse(null);
            if (defenseValue != null) {
                hasDefense = true;
                defense += defenseValue.average();
            }
            health += average(item, CombatStat.HEALTH);
            healthRegen += average(item, CombatStat.HEALTH_REGEN);
            critChance += average(item, CombatStat.CRIT_CHANCE);
            critDamage += average(item, CombatStat.CRIT_DAMAGE);
            experienceBonus += average(item, CombatStat.EXPERIENCE_BONUS);
        }

        CombatStatsSnapshot.Builder builder = CombatStatsSnapshot.builder()
                .damageMinimum(damageMinimum)
                .damageMaximum(damageMaximum)
                .hasDamage(damageMaximum > 0.0D)
                .defensePercent(Math.clamp(defense, -100.0D, 100.0D))
                .hasDefense(hasDefense)
                .health(health)
                .healthRegenPercent(Math.clamp(healthRegen, 0.0D, 100.0D))
                .critChancePercent(Math.clamp(critChance, 0.0D, 100.0D))
                .critDamagePercent(critDamage)
                .experienceBonusPercent(experienceBonus);
        if (rpgHealth != null && rpgHealth.enabled()) {
            builder.baseHealth(rpgHealth.baseHealth())
                    .levelHealthBonus(rpgHealth.levelHealthBonus());
        }
        return builder.build();
    }

    private static List<ItemStack> supportedEquipment(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return List.of();
        List<ItemStack> items = new ArrayList<>(5);
        items.add(equipment.getItemInMainHand());
        ItemStack[] armorContents = equipment.getArmorContents();
        if (armorContents != null) {
            Collections.addAll(items, armorContents);
        }
        return items;
    }

    private static double average(CombatItemStats item, CombatStat stat) {
        NumericRange value = item.value(stat).orElse(null);
        return value == null ? 0.0D : value.average();
    }

    private static String describe(NumericRange value) {
        String range = value.isFixed()
                ? format(value.minimum())
                : format(value.minimum()) + "-" + format(value.maximum());
        return value.percent() ? range + "%" : range;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value)
                .replaceAll("\\.?0+$", "");
    }

    static boolean usableAtLevel(CombatItemStats stats, long rpgLevel) {
        Objects.requireNonNull(stats, "stats");
        if (rpgLevel < 0L) throw new IllegalArgumentException("rpgLevel cannot be negative");
        return !stats.hasRequiredLevel() || rpgLevel >= stats.requiredLevel();
    }

    private static boolean usableAtLevel(CombatItemStats stats, OptionalLong rpgLevel) {
        return !stats.hasRequiredLevel()
                || rpgLevel.isPresent() && usableAtLevel(stats, rpgLevel.getAsLong());
    }

    public void reload(CombatSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        parser = new CombatLoreParser(settings);
        warningTimes.clear();
    }

    @Override
    public CombatStatsSnapshot getStats(Player player) {
        return getStats((LivingEntity) Objects.requireNonNull(player, "player"));
    }

    @Override
    public CombatStatsSnapshot getStats(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        boolean levelGateEnabled = rpgLevelResolver != null && entity instanceof Player;
        OptionalLong rpgLevel = levelGateEnabled && entity instanceof Player player
                ? resolveRpgLevel(player)
                : OptionalLong.empty();
        List<CombatItemStats> itemStats = new ArrayList<>();
        for (ItemStack item : supportedEquipment(entity)) {
            CombatItemStats stats = parseItem(item);
            if (levelGateEnabled && !usableAtLevel(stats, rpgLevel)) continue;
            itemStats.add(stats);
        }
        HealthSnapshot rpgHealth = levelGateEnabled && entity instanceof Player player
                ? resolveRpgHealth(player).orElse(null)
                : null;
        return aggregate(itemStats, rpgHealth);
    }

    @Override
    public CombatItemStats parseItem(ItemStack itemStack) {
        CombatItemStats stats = parser.parse(ItemLoreReader.read(itemStack));
        for (String invalidLine : stats.invalidLines()) warnInvalidLine(invalidLine);
        return stats;
    }

    public List<String> debug(Player player) {
        List<String> lines = new ArrayList<>();
        EntityEquipment equipment = Objects.requireNonNull(player, "player").getEquipment();
        List<EquipmentEntry> entries = List.of(
                new EquipmentEntry("主手", equipment.getItemInMainHand()),
                new EquipmentEntry("头盔", equipment.getHelmet()),
                new EquipmentEntry("胸甲", equipment.getChestplate()),
                new EquipmentEntry("护腿", equipment.getLeggings()),
                new EquipmentEntry("靴子", equipment.getBoots())
        );
        for (EquipmentEntry entry : entries) {
            CombatItemStats stats = parseItem(entry.item());
            if (stats.values().isEmpty() && stats.invalidLines().isEmpty() && !stats.hasRequiredLevel()) {
                lines.add(entry.slot() + ": 无支持属性");
                continue;
            }
            lines.add(entry.slot() + ":");
            if (stats.hasRequiredLevel()) {
                lines.add(
                        "  " + settings.levelRequirementLabel() + " = " + stats.requiredLevel()
                                + " (" + requirementStatus(player, stats) + ")"
                );
            }
            for (CombatStat stat : CombatStat.values()) {
                stats.value(stat).ifPresent(value -> lines.add("  " + settings.label(stat) + " = " + describe(value)));
            }
            for (String invalidLine : stats.invalidLines()) lines.add("  跳过: " + invalidLine);
        }
        return lines;
    }

    /**
     * Returns the level restrictions on currently supported equipment for the player-facing stats
     * command.
     *
     * @param player player whose equipment is inspected
     * @return immutable requirement and status lines
     */
    public List<String> levelRequirementDisplay(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return List.of();
        List<EquipmentEntry> entries = List.of(
                new EquipmentEntry("主手", equipment.getItemInMainHand()),
                new EquipmentEntry("头盔", equipment.getHelmet()),
                new EquipmentEntry("胸甲", equipment.getChestplate()),
                new EquipmentEntry("护腿", equipment.getLeggings()),
                new EquipmentEntry("靴子", equipment.getBoots())
        );
        List<String> lines = new ArrayList<>();
        for (EquipmentEntry entry : entries) {
            CombatItemStats stats = parseItem(entry.item());
            if (!stats.hasRequiredLevel()) continue;
            lines.add(
                    "  " + entry.slot() + " - " + settings.levelRequirementLabel() + " = "
                            + stats.requiredLevel() + " (" + requirementStatus(target, stats) + ")"
            );
        }
        return List.copyOf(lines);
    }

    private OptionalLong resolveRpgLevel(Player player) {
        if (rpgLevelResolver == null) return OptionalLong.empty();
        return Objects.requireNonNull(
                rpgLevelResolver.apply(player.getUniqueId()),
                "rpgLevelResolver result"
        );
    }

    private Optional<HealthSnapshot> resolveRpgHealth(Player player) {
        if (rpgHealthResolver == null) return Optional.empty();
        return Objects.requireNonNull(
                rpgHealthResolver.resolve(player.getUniqueId()),
                "rpgHealthResolver result"
        );
    }

    private String requirementStatus(Player player, CombatItemStats stats) {
        if (rpgLevelResolver == null) return "DreamRPG未启用";
        OptionalLong level = resolveRpgLevel(player);
        if (level.isEmpty()) return "数据未就绪";
        return usableAtLevel(stats, level.getAsLong()) ? "已达标" : "未达标";
    }

    private void warnInvalidLine(String invalidLine) {
        long now = System.currentTimeMillis();
        Long previous = warningTimes.get(invalidLine);
        if (previous != null && now - previous < INVALID_WARNING_COOLDOWN_MILLIS) return;
        warningTimes.put(invalidLine, now);
        plugin.getLogger().warning("Ignored invalid Combat lore: " + invalidLine);
    }

    private record EquipmentEntry(String slot, ItemStack item) {
    }
}
