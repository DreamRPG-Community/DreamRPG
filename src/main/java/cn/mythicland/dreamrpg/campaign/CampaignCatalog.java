package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeGroup;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.storage.AtomicYamlStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * Publishes chapters and NPC exchanges together, after both configuration files validate.
 */
@InjectComponent
public final class CampaignCatalog implements DreamLibPluginLifecycle {
    public static final String NPC_TASK_STAGE = "npc_managed";
    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private Snapshot snapshot = new Snapshot(List.of(), List.of());
    private Map<UUID, List<String>> orders = Map.of();
    private boolean saving;
    private boolean enabled;

    public CampaignCatalog(JavaPlugin plugin, DreamLibApi lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    @Override
    public void enable() {
        enabled = true;
        reload();
    }

    @Override
    public void disable() {
        enabled = false;
        snapshot = new Snapshot(List.of(), List.of());
        orders = Map.of();
    }

    @Override
    public void reload() {
        if (saving) throw new IllegalStateException("Exchange save is in progress");
        List<CampaignStage> stages = parse(load("stages.yml"));
        YamlConfiguration exchanges = load("exchanges.yml");
        List<ExchangeGroup> groups = parseGroups(exchanges);
        Set<String> identifiers = new HashSet<>();
        Stream.concat(stages.stream().flatMap(stage -> stage.tasks().stream()),
                        groups.stream().flatMap(group -> group.offers().stream()))
                .forEach(offer -> {
                    if (!identifiers.add(offer.id()))
                        throw new IllegalArgumentException("Duplicate task or exchange ID: " + offer.id());
                });
        Map<UUID, List<String>> loadedOrders = parseOrders(exchanges);
        snapshot = new Snapshot(stages, groups);
        orders = loadedOrders;
    }

    static Map<UUID, List<String>> parseOrders(YamlConfiguration yaml) {
        Map<UUID, List<String>> result = new HashMap<>();
        if (!yaml.contains("offer-order")) return Map.of();
        ConfigurationSection section = yaml.getConfigurationSection("offer-order");
        if (section == null) throw new IllegalArgumentException("offer-order must be a section");
        for (String key : section.getKeys(false)) {
            UUID npc = UUID.fromString(key);
            Object raw = section.get(key);
            if (!(raw instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof String))) {
                throw new IllegalArgumentException("Invalid offer order for NPC: " + npc);
            }
            List<String> ids = values.stream().map(String.class::cast).toList();
            Set<String> seen = new HashSet<>();
            for (String id : ids) {
                if (!id.isEmpty() && !seen.add(id))
                    throw new IllegalArgumentException("Duplicate offer ID in order: " + id);
            }
            result.put(npc, ids);
        }
        return Map.copyOf(result);
    }

    private YamlConfiguration load(String name) {
        Path file = plugin.getDataFolder().toPath().resolve(name);
        if (!Files.exists(file)) plugin.saveResource(name, false);
        try {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(Files.readString(file));
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Cannot load DreamRPG/" + name, exception);
        }
    }

    public List<CampaignStage> stages() {
        return snapshot.stages();
    }

    public List<ExchangeGroup> groups() {
        return snapshot.groups();
    }

    public List<ExchangeOffer> tasks() {
        return stages().stream().flatMap(stage -> stage.tasks().stream()).toList();
    }

    public List<ExchangeOffer> exchangesForNpc(UUID npcId) {
        return groups().stream().flatMap(group -> group.offers().stream())
                .filter(offer -> offer.npc().equals(npcId)).toList();
    }

    public List<ExchangeOffer> offersForNpc(UUID npcId) {
        return offers().stream().filter(offer -> offer.npc().equals(npcId)).toList();
    }

    public List<ExchangeOffer> placementsForNpc(UUID npcId) {
        return OfferOrder.resolve(offersForNpc(npcId), orders.getOrDefault(npcId, List.of()));
    }

    public CompletableFuture<Void> saveOrder(UUID npc, List<ExchangeOffer> expected, List<ExchangeOffer> placement) {
        if (!enabled) return CompletableFuture.failedFuture(new IllegalStateException("Catalog is disabled"));
        if (saving) return CompletableFuture.failedFuture(new IllegalStateException("Catalog save is in progress"));
        if (!placementsForNpc(npc).equals(expected)) {
            return CompletableFuture.failedFuture(new IllegalStateException("NPC offers changed while sorting"));
        }
        List<ExchangeOffer> actual = placement.stream().filter(Objects::nonNull).toList();
        List<ExchangeOffer> current = offersForNpc(npc);
        if (actual.size() != current.size() || !new HashSet<>(actual).equals(new HashSet<>(current))) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid NPC offer placement"));
        }
        List<String> ids = OfferOrder.identifiers(placement);
        List<String> previous = orders.get(npc);
        Path file = plugin.getDataFolder().toPath().resolve("exchanges.yml");
        saving = true;
        CompletableFuture<Void> result = new CompletableFuture<>();
        lib.supplyAsync(() -> {
            try {
                String original = Files.readString(file);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(original);
                Map<UUID, List<String>> saved = new HashMap<>(parseOrders(yaml));
                if (!Objects.equals(previous, saved.get(npc)))
                    throw new IllegalStateException("NPC order changed externally");
                saved.put(npc, ids);
                yaml.set("offer-order." + npc, ids);
                String latest = Files.readString(file);
                if (!Objects.equals(original, latest)) throw new IllegalStateException("Offer order file changed");
                AtomicYamlStore.write(file, yaml);
                return Map.copyOf(saved);
            } catch (IOException | InvalidConfigurationException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((saved, error) -> lib.runOnMain(() -> {
            saving = false;
            if (error != null) result.completeExceptionally(error);
            else {
                if (enabled) orders = saved;
                result.complete(null);
            }
        }));
        return result;
    }

    public CompletableFuture<Void> saveOffer(
            ExchangeOffer expected, ExchangeOffer replacement, String stageId
    ) {
        boolean task = stageId != null;
        if (expected != null &&
                (!expected.id().equals(replacement.id()) || !expected.npc().equals(replacement.npc()))) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Offer identity changed"));
        }
        return changeOffer(expected, replacement, task, stageId);
    }

    public CompletableFuture<Void> deleteOffer(ExchangeOffer expected, boolean task) {
        return changeOffer(expected, null, task, null);
    }

    private CompletableFuture<Void> changeOffer(
            ExchangeOffer expected, ExchangeOffer replacement, boolean task, String stageId
    ) {
        if (!enabled) return CompletableFuture.failedFuture(new IllegalStateException("Catalog is disabled"));
        if (saving) return CompletableFuture.failedFuture(new IllegalStateException("Catalog save is in progress"));
        if (expected == null && (replacement == null || find(replacement.id()).isPresent())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Offer already exists"));
        }
        if (expected != null &&
                (find(expected.id()).filter(expected::equals).isEmpty() || isTask(expected.id()) != task)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Offer has changed"));
        }
        saving = true;
        Path file = plugin.getDataFolder().toPath().resolve(task ? "stages.yml" : "exchanges.yml");
        CompletableFuture<Void> result = new CompletableFuture<>();
        lib.supplyAsync(() -> {
            try {
                String original = Files.readString(file);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(original);
                changeYaml(yaml, expected, replacement, task, stageId);
                Object updated = task ? parse(yaml) : parseGroups(yaml);
                if (!Files.readString(file).equals(original)) throw new IllegalStateException("Catalog file changed");
                AtomicYamlStore.write(file, yaml);
                return updated;
            } catch (IOException | InvalidConfigurationException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((updated, error) -> lib.runOnMain(() -> {
            saving = false;
            if (error != null) result.completeExceptionally(error);
            else {
                if (enabled) {
                    if (task) {
                        @SuppressWarnings("unchecked") List<CampaignStage> stages = (List<CampaignStage>) updated;
                        snapshot = new Snapshot(stages, snapshot.groups());
                    } else {
                        @SuppressWarnings("unchecked") List<ExchangeGroup> groups = (List<ExchangeGroup>) updated;
                        snapshot = new Snapshot(snapshot.stages(), groups);
                    }
                }
                result.complete(null);
            }
        }));
        return result;
    }

    static void changeYaml(
            YamlConfiguration yaml, ExchangeOffer expected, ExchangeOffer replacement,
            boolean task, String stageId
    ) {
        String base;
        if (task) {
            List<CampaignStage> stages = parse(yaml);
            CampaignStage owner;
            if (expected == null) {
                owner = stages.stream().filter(stage -> stage.id().equals(stageId)).findFirst().orElse(null);
                if (owner == null && NPC_TASK_STAGE.equals(stageId)) {
                    yaml.set("stages." + NPC_TASK_STAGE + ".name", "NPC任务");
                    yaml.createSection("stages." + NPC_TASK_STAGE + ".tasks");
                    owner = parse(yaml).stream().filter(stage -> stage.id().equals(NPC_TASK_STAGE)).findFirst()
                            .orElseThrow();
                }
                if (owner == null) throw new IllegalStateException("Stage no longer exists");
            } else {
                owner = stages.stream().filter(stage -> stage.tasks().contains(expected)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Task changed since editing began"));
            }
            String id = expected == null ? replacement.id() : expected.id();
            base = "stages." + owner.id() + ".tasks." + id;
        } else {
            List<ExchangeGroup> groups = parseGroups(yaml);
            ExchangeGroup owner;
            if (expected == null) {
                owner = groups.stream().filter(group -> group.npc().equals(replacement.npc())).findFirst().orElse(null);
                if (owner == null) {
                    String id = "npc_" + replacement.npc().toString().replace("-", "");
                    String path = "exchanges." + id;
                    if (yaml.contains(path)) throw new IllegalStateException("Exchange group ID already exists");
                    yaml.set(path + ".npc", replacement.npc().toString());
                    yaml.createSection(path + ".offers");
                    base = path + ".offers." + replacement.id();
                } else base = "exchanges." + owner.id() + ".offers." + replacement.id();
            } else {
                owner = groups.stream().filter(group -> group.offers().contains(expected)).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Exchange changed since editing began"));
                base = "exchanges." + owner.id() + ".offers." + expected.id();
            }
        }
        if (expected == null && yaml.contains(base)) throw new IllegalStateException("Offer ID already exists");
        if (replacement == null) {
            yaml.set(base, null);
            return;
        }
        if (task) yaml.set(base + ".npc", replacement.npc().toString());
        yaml.set(base + ".requires", replacement.requires().stream()
                .map(item -> Map.of("item", item.item(), "amount", item.amount())).toList());
        yaml.set(base + ".reward.item", replacement.reward().item());
        yaml.set(base + ".reward.amount", replacement.reward().amount());
    }

    static void updateExchange(YamlConfiguration yaml, ExchangeOffer expected, ExchangeOffer replacement) {
        ExchangeGroup owner = parseGroups(yaml).stream()
                .filter(group -> group.offers().stream().anyMatch(expected::equals)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange file changed since editing began"));
        String base = "exchanges." + owner.id() + ".offers." + expected.id();
        yaml.set(base + ".requires", replacement.requires().stream()
                .map(item -> Map.of("item", item.item(), "amount", item.amount())).toList());
        yaml.set(base + ".reward.item", replacement.reward().item());
        yaml.set(base + ".reward.amount", replacement.reward().amount());
    }

    public List<ExchangeOffer> offers() {
        return Stream.concat(tasks().stream(), groups().stream().flatMap(group -> group.offers().stream())).toList();
    }

    public Optional<ExchangeOffer> find(String id) {
        return offers().stream().filter(offer -> offer.id().equals(id)).findFirst();
    }

    public boolean isTask(String id) {
        return tasks().stream().anyMatch(offer -> offer.id().equals(id));
    }

    static List<CampaignStage> parse(ConfigurationSection configuration) {
        if (!Integer.valueOf(2).equals(configuration.get("version")))
            throw new IllegalArgumentException("stages.yml version must be 2; migrate the legacy directory first");
        ConfigurationSection root = configuration.getConfigurationSection("stages");
        if (root == null) throw new IllegalArgumentException("stages.yml requires a stages section");
        List<CampaignStage> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection stage = entry(root, id, Set.of("name", "description", "tasks"));
            Object description = stage.get("description", List.of());
            if (!(description instanceof List<?> lines) || lines.stream().anyMatch(line -> !(line instanceof String))) {
                throw new IllegalArgumentException("Expected stage description lines: " + id);
            }
            result.add(new CampaignStage(id, stage.getString("name"), stage.getStringList("description"),
                    offers(stage, "tasks", null)));
        }
        return List.copyOf(result);
    }

    static List<ExchangeGroup> parseGroups(ConfigurationSection configuration) {
        if (!Integer.valueOf(1).equals(configuration.get("version")))
            throw new IllegalArgumentException("exchanges.yml version must be 1");
        ConfigurationSection root = configuration.getConfigurationSection("exchanges");
        if (root == null) throw new IllegalArgumentException("exchanges.yml requires an exchanges section");
        List<ExchangeGroup> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            if (!id.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid directory ID: " + id);
            ConfigurationSection group = root.getConfigurationSection(id);
            if (group == null) throw new IllegalArgumentException("Invalid exchange group: " + id);
            for (String key : group.getKeys(false)) {
                if (!Set.of("name", "npc", "offers").contains(key)) {
                    throw new IllegalArgumentException("Unknown directory key: " + id + "." + key);
                }
            }
            UUID npc = UUID.fromString(group.getString("npc", ""));
            result.add(new ExchangeGroup(id, npc, offers(group, "offers", npc)));
        }
        return List.copyOf(result);
    }

    private static ConfigurationSection entry(
            ConfigurationSection root,
            String id,
            Set<String> keys
    ) {
        if (!id.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid directory ID: " + id);
        ConfigurationSection entry = root.getConfigurationSection(id);
        if (entry == null || !entry.isString("name") || entry.getString("name").isBlank())
            throw new IllegalArgumentException("Directory entry requires a name: " + id);
        for (String key : entry.getKeys(false)) {
            if (!keys.contains(key)) throw new IllegalArgumentException("Unknown directory key: " + id + "." + key);
        }
        return entry;
    }

    private static List<ExchangeOffer> offers(
            ConfigurationSection owner,
            String path,
            UUID npc
    ) {
        ConfigurationSection entries = owner.getConfigurationSection(path);
        if (entries == null)
            throw new IllegalArgumentException("Expected exchange definitions: " + owner.getCurrentPath() + "." + path);
        List<ExchangeOffer> result = new ArrayList<>();
        for (String id : entries.getKeys(false))
            result.add(ExchangeOffer.parse(id, entries.getConfigurationSection(id), npc));
        return List.copyOf(result);
    }

    private record Snapshot(List<CampaignStage> stages, List<ExchangeGroup> groups) {
    }
}
