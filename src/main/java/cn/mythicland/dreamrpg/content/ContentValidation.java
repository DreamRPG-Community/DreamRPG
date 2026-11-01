package cn.mythicland.dreamrpg.content;

import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Validates editable definitions and their statically resolvable cross-plugin references. */
final class ContentValidation {
    private static final Pattern SKILL = Pattern.compile("(?i)\\bskill\\{[^}]*?\\b(?:skill|s)\\s*=\\s*([^;,}]+)");
    private static final Pattern SUMMON = Pattern.compile("(?i)(?:summon|spawn)\\{[^}]*?(?:type|t|mob|m)=([^;,}]+)");
    private static final Set<String> BUILTIN_DROPS = Set.of("exp", "experience", "money", "command", "nothing", "mcmmo-exp");

    private ContentValidation() {
    }

    record Result(String summary, Map<String, Set<String>> ids) {
    }

    static Result validate(ContentWorkspace content, Map<String, List<Path>> definitionsByKind) {
        Map<String, Map<String, Path>> ids = new LinkedHashMap<>();
        for (String kind : List.of("items", "mobs", "skills", "droptables", "spawners")) ids.put(kind, new LinkedHashMap<>());
        Map<Path, YamlConfiguration> definitions = new LinkedHashMap<>();
        Map<Path, String> kinds = new HashMap<>();
        definitionsByKind.forEach((kind, paths) -> paths.forEach(path -> kinds.put(path, kind)));
        for (Path source : definitionsByKind.values().stream().flatMap(Collection::stream).toList()) {
            String kind = kinds.get(source);
            YamlConfiguration yaml = ContentWorkspace.readYaml(source);
            definitions.put(source, yaml);
            for (String id : yaml.getKeys(false)) {
                if (kind.equals("spawners") && !source.getFileName().toString().equals(id + ".yml"))
                    throw new IllegalArgumentException("Spawner definitions require one <id>.yml per source: " + source);
                if (!id.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Non-standard " + kind + " ID '" + id + "' in " + source);
                if (!ids.containsKey(kind)) throw new IllegalArgumentException("Unsupported content kind: " + kind);
                ids.get(kind).put(id, source);
                if (kind.equals("items") && !yaml.isConfigurationSection(id)) throw new IllegalArgumentException("Invalid item: " + source + " / " + id);
                if (kind.equals("items")) {
                    Object material = yaml.get(id + ".Id");
                    if (material == null && !yaml.isConfigurationSection(id + ".ItemStack"))
                        throw new IllegalArgumentException("Missing item Id or ItemStack: " + source + " / " + id);
                    if (material != null && !vanilla(String.valueOf(material)))
                        throw new IllegalArgumentException("Invalid item material: " + source + " / " + id);
                }
                if (kind.equals("mobs") && !yaml.isString(id + ".Type")) throw new IllegalArgumentException("Missing mob Type: " + source + " / " + id);
                if (kind.equals("mobs") && io.lumine.xikage.mythicmobs.mobs.entities.MythicEntityType.get(yaml.getString(id + ".Type")) == null)
                    throw new IllegalArgumentException("Unknown mob Type: " + source + " / " + id);
                if (kind.equals("skills") && !yaml.isList(id + ".Skills")) throw new IllegalArgumentException("Missing skill list: " + source + " / " + id);
            }
        }
        for (var definition : definitions.entrySet()) {
            String kind = kinds.get(definition.getKey());
            for (var entry : definition.getValue().getValues(true).entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.endsWith(".MobName") && value instanceof String mob) require(ids.get("mobs"), mob, definition.getKey(), key);
                if (value instanceof List<?> lines) {
                    if (key.endsWith(".Drops") || (kind.equals("droptables") && key.endsWith(".Drops"))) {
                        for (Object line : lines) drop(ids, String.valueOf(line), definition.getKey());
                    }
                    if (key.endsWith(".Equipment")) {
                        for (Object line : lines) {
                            if (line == null) continue;
                            String item = line.toString().split(":", 2)[0];
                            if (!vanilla(item)) require(ids.get("items"), item, definition.getKey(), key);
                        }
                    }
                    if (key.endsWith(".Skills")) {
                        for (Object line : lines) {
                            var summon = SUMMON.matcher(String.valueOf(line));
                            while (summon.find()) require(ids.get("mobs"), summon.group(1).trim(), definition.getKey(), key);
                            if (String.valueOf(line).toLowerCase(Locale.ROOT).matches(".*\\bskill\\{.*")) {
                                var skill = SKILL.matcher(String.valueOf(line));
                                while (skill.find()) require(ids.get("skills"), skill.group(1).trim(), definition.getKey(), key);
                            }
                        }
                    }
                }
            }
        }
        Path plugins = content.root().getParent();
        CampaignCatalog.validateContent(content);
        cn.mythicland.dreamworldregion.WorldRegionDataStore.validateContent(content);
        var npcFile = plugins.resolve("DreamNPC/npcs.yml");
        var npcSection = ContentWorkspace.readYaml(npcFile).getConfigurationSection("npcs");
        if (npcSection == null) throw new IllegalArgumentException("Missing npcs section: " + npcFile);
        Set<String> npcs = npcSection.getKeys(false);
        cn.mythicland.dreamnpc.storage.NpcRepository.validateContent(ContentWorkspace.readYaml(npcFile));
        for (String id : npcs) {
            UUID.fromString(id);
            ConfigurationSection npc = npcSection.getConfigurationSection(id);
            if (npc == null || !npc.isString("world") || npc.getString("world").isBlank())
                throw new IllegalArgumentException("Invalid NPC location: " + id);
            for (String coordinate : List.of("x", "y", "z", "yaw", "pitch")) {
                if ((coordinate.equals("yaw") || coordinate.equals("pitch")) && !npc.contains(coordinate)) continue;
                if (!(npc.get(coordinate) instanceof Number value) || !Double.isFinite(value.doubleValue()))
                    throw new IllegalArgumentException("Invalid NPC coordinate " + coordinate + ": " + id);
            }
        }
        for (Path file : java.util.stream.Stream.concat(content.documents("quests.yml").stream(), content.documents("exchanges.yml").stream()).toList()) {
            for (var entry : ContentWorkspace.readYaml(file).getValues(true).entrySet()) {
                if (entry.getKey().endsWith(".npc") && !npcs.contains(String.valueOf(entry.getValue())))
                    throw new IllegalArgumentException("Unknown NPC " + entry.getValue() + " in " + file + " / " + entry.getKey());
                validateItemValues(entry.getValue(), ids.get("items"), file, entry.getKey());
            }
        }
        Path chests = plugins.resolve("DreamChest/chests.yml");
        for (var entry : ContentWorkspace.readYaml(chests).getValues(true).entrySet())
            validateItemValues(entry.getValue(), ids.get("items"), chests, entry.getKey());
        var libraryFiles = cn.mythicland.dreamrpgitems.menu.ItemLibraryConfiguration.contentFiles(content);
        cn.mythicland.dreamrpgitems.menu.ItemLibraryConfiguration.validateContent(libraryFiles);
        for (Path file : libraryFiles) {
            for (var entry : ContentWorkspace.readYaml(file).getValues(true).entrySet()) {
                validateItemValues(entry.getValue(), ids.get("items"), file, entry.getKey());
                if (entry.getKey().endsWith(".items") && entry.getValue() instanceof List<?> values)
                    values.forEach(id -> require(ids.get("items"), String.valueOf(id), file, entry.getKey()));
            }
        }
        ConfigurationSection landmarkIds = content.document("landmarks.yml").yaml().getConfigurationSection("landmarks");
        ConfigurationSection points = content.document("waypoints.yml").yaml().getConfigurationSection("waypoints");
        if (points == null || landmarkIds == null) throw new IllegalArgumentException("Missing waypoint or landmark definitions");
        Set<String> aliases = new HashSet<>();
        for (String id : points.getKeys(false)) {
            ConfigurationSection point = points.getConfigurationSection(id);
            if (point == null || !point.isString("name") || point.isString("world") == point.isString("landmark"))
                throw new IllegalArgumentException("Invalid waypoint: " + id);
            if (point.isString("landmark") && !landmarkIds.isConfigurationSection(point.getString("landmark")))
                throw new IllegalArgumentException("Unknown landmark for waypoint: " + id);
            List<String> names = new ArrayList<>(point.getStringList("aliases"));
            names.add(id);
            for (String name : names) if (!aliases.add(name)) throw new IllegalArgumentException("Duplicate waypoint alias: " + name);
        }
        String summary = "物品 " + ids.get("items").size() + ", 怪物 " + ids.get("mobs").size()
                + ", 技能 " + ids.get("skills").size() + ", 掉落表 " + ids.get("droptables").size()
                + ", 刷怪点 " + ids.get("spawners").size();
        Map<String, Set<String>> accepted = new LinkedHashMap<>();
        ids.forEach((kind, entries) -> accepted.put(kind, Set.copyOf(entries.keySet())));
        return new Result(summary, Map.copyOf(accepted));
    }

    private static void validateItemValues(Object value, Map<String, Path> items, Path file, String key) {
        if (key.endsWith(".item") && value instanceof String id) require(items, id, file, key);
        if (value instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> map && map.get("item") != null) require(items, String.valueOf(map.get("item")), file, key);
            }
        }
    }

    private static void drop(Map<String, Map<String, Path>> ids, String line, Path file) {
        String id = line.trim().split("\\s+", 2)[0];
        if (id.isBlank() || BUILTIN_DROPS.contains(id.toLowerCase(Locale.ROOT)) || vanilla(id)) return;
        if (!ids.get("items").containsKey(id) && !ids.get("droptables").containsKey(id)) throw new IllegalArgumentException("Unknown drop '" + id + "' in " + file);
    }

    private static boolean vanilla(String id) {
        return id.matches("[0-9]+(?::[0-9]+)?") || org.bukkit.Material.matchMaterial(id) != null;
    }

    private static void require(Map<String, Path> ids, String id, Path file, String key) {
        if (!ids.containsKey(id)) throw new IllegalArgumentException("Unknown reference '" + id + "' in " + file + " / " + key);
    }

}
