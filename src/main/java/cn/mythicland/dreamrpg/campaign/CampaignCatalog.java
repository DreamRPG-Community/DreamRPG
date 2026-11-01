package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import cn.mythicland.framework.bukkit.content.ContentDocument;
import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.exchange.ExchangeGroup;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.framework.bukkit.storage.AtomicYamlStore;
import cn.mythicland.framework.bukkit.storage.AtomicYamlTransaction;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Publishes chapters and NPC exchanges together, after both configuration files validate.
 */
@InjectComponent
public final class CampaignCatalog implements ManagedLifecycle {
    public static final String NPC_TASK_STAGE = "npc_managed";
    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private Snapshot snapshot = new Snapshot(List.of(), List.of());
    private Map<UUID, List<String>> orders = Map.of();
    private boolean saving;
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);
    private boolean enabled;

    public CampaignCatalog(JavaPlugin plugin, DreamLibApi lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    @Override
    public void load() {
        enabled = true;
        reload();
    }

    @Override
    public void unload() {
        enabled = false;
        snapshot = new Snapshot(List.of(), List.of());
        orders = Map.of();
    }

    @Override
    public void reload() {
        prepareContentReload().apply().run();
    }

    /** @return validated catalog update and an inverse preserving the current catalog */
    public ContentUpdate prepareContentReload() {
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
        Snapshot previous = snapshot;
        Map<UUID, List<String>> previousOrders = orders;
        Snapshot next = new Snapshot(stages, groups);
        return new ContentUpdate(() -> {
            snapshot = next;
            orders = loadedOrders;
        }, () -> {
            snapshot = previous;
            orders = previousOrders;
        });
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

    private ContentDocument document(String name) {
        String source = name.equals("stages.yml") ? "quests.yml" : name;
        if (lib.content().documents(source).isEmpty()) lib.content().resource(plugin, source, "common/" + source);
        return lib.content().document(source);
    }

    private YamlConfiguration load(String name) {
        ContentDocument document = document(name);
        if (name.equals("stages.yml")) {
            lib.content().resource(plugin, "stages.yml", "stages.yml");
            return orderedQuests(lib.content(), document.yaml());
        }
        return document.yaml();
    }

    /** Orders package-owned chapters through the global chapter index without copying tasks. */
    public static YamlConfiguration orderedQuests(ContentWorkspace content, YamlConfiguration quests) {
        YamlConfiguration index = ContentWorkspace.readYaml(content.resolve("DreamRPG/stages.yml", "stages.yml"));
        if (index.getInt("version") != 4 || !index.isList("stages"))
            throw new IllegalArgumentException("stages.yml requires version 4 and a chapter ID list");
        ConfigurationSection chapters = quests.getConfigurationSection("stages");
        if (chapters == null) throw new IllegalArgumentException("Active quests.yml files require stages");
        YamlConfiguration ordered = new YamlConfiguration();
        ordered.set("version", 3);
        ordered.createSection("stages");
        Set<String> seen = new HashSet<>();
        for (Object raw : index.getList("stages")) {
            if (!(raw instanceof String id) || !seen.add(id)) throw new IllegalArgumentException("Invalid or duplicate chapter ID: " + raw);
            if (!chapters.isConfigurationSection(id)) throw new IllegalArgumentException("Chapter references inactive or missing content: " + id);
            ordered.set("stages." + id, chapters.get(id));
        }
        for (String id : chapters.getKeys(false)) {
            if (seen.contains(id)) continue;
            if (!NPC_TASK_STAGE.equals(id)) throw new IllegalArgumentException("Chapter missing from stages.yml: " + id);
            ordered.set("stages." + id, chapters.get(id));
        }
        return ordered;
    }

    private void selectNpcDestination(ContentDocument document, UUID npc, String name) {
        for (String sourceName : List.of("quests.yml", "exchanges.yml")) {
            for (Path source : lib.content().documents(sourceName)) {
                boolean ownsNpc = ContentWorkspace.readYaml(source).getValues(true).entrySet().stream()
                        .anyMatch(entry -> entry.getKey().endsWith(".npc") && npc.toString().equals(entry.getValue()));
                if (ownsNpc) { document.destination(source.resolveSibling(name)); return; }
            }
        }
    }

    public List<CampaignStage> stages() {
        return snapshot.stages();
    }

    /**
     * Validates both editable campaign documents without publishing a new catalog.
     *
     * @param stages chapter and task definitions
     * @param exchanges NPC exchange definitions
     */
    public static void validateContent(Path stages, Path exchanges) {
        var tasks = parse(ContentWorkspace.readYaml(stages));
        var yaml = ContentWorkspace.readYaml(exchanges);
        var groups = parseGroups(yaml);
        Set<String> ids = new HashSet<>();
        Stream.concat(tasks.stream().flatMap(stage -> stage.tasks().stream()), groups.stream().flatMap(group -> group.offers().stream()))
                .forEach(offer -> {
                    if (!ids.add(offer.id())) throw new IllegalArgumentException("Duplicate task or exchange ID: " + offer.id());
                });
        parseOrders(yaml);
    }

    /** Validates all active package quests and exchanges as a single catalog. */
    public static void validateContent(ContentWorkspace content) {
        var stages = parse(orderedQuests(content, content.document("quests.yml").yaml()));
        var exchanges = content.document("exchanges.yml").yaml();
        var groups = parseGroups(exchanges);
        Set<String> ids = new HashSet<>();
        Stream.concat(stages.stream().flatMap(stage -> stage.tasks().stream()), groups.stream().flatMap(group -> group.offers().stream()))
                .forEach(offer -> { if (!ids.add(offer.id())) throw new IllegalArgumentException("Duplicate task or exchange ID: " + offer.id()); });
        parseOrders(exchanges);
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
        saving = true;
        CompletableFuture<Void> result = new CompletableFuture<>();
        saveTail = result;
        lib.supplyAsync(() -> {
            ContentDocument document = document("exchanges.yml");
            selectNpcDestination(document, npc, "exchanges.yml");
            YamlConfiguration yaml = document.yaml();
            Map<UUID, List<String>> saved = new HashMap<>(parseOrders(yaml));
            if (!Objects.equals(previous, saved.get(npc))) throw new IllegalStateException("NPC order changed externally");
            saved.put(npc, ids);
            yaml.set("offer-order." + npc, ids);
            document.save();
            return Map.copyOf(saved);
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
        CompletableFuture<Void> result = new CompletableFuture<>();
        saveTail = result;
        lib.supplyAsync(() -> {
            ContentDocument document = document(task ? "stages.yml" : "exchanges.yml");
            selectNpcDestination(document, replacement == null ? expected.npc() : replacement.npc(), task ? "quests.yml" : "exchanges.yml");
            YamlConfiguration yaml = document.yaml();
            changeYaml(yaml, expected, replacement, task, stageId);
            Object updated = task ? parse(orderedQuests(lib.content(), yaml)) : parseGroups(yaml);
            document.save();
            return updated;
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

    /**
     * Removes every task, exchange group and placement belonging to a deleted NPC.
     * Must be invoked on the main thread. Waits for an existing editor save before
     * writing both files in one rollback-capable transaction. A failed write leaves
     * the published catalog unchanged and completes the returned future exceptionally.
     *
     * @param npc stable identifier of the removed NPC
     * @return completion after persistence and main-thread catalog publication
     */
    public CompletableFuture<Void> removeNpc(UUID npc) {
        MainThread.require("NPC project removal requires the primary thread");
        if (!enabled) return CompletableFuture.failedFuture(new IllegalStateException("Catalog is disabled"));
        if (saving) {
            // Cleanup is independent of whether the preceding administrator edit succeeded.
            return saveTail.handle((ignored, failure) -> null).thenCompose(ignored -> removeNpc(npc));
        }
        if (tasks().stream().noneMatch(task -> task.npc().equals(npc))
                && groups().stream().noneMatch(group -> group.npc().equals(npc))
                && !orders.containsKey(npc)) return CompletableFuture.completedFuture(null);
        Snapshot expected = snapshot;
        Map<UUID, List<String>> expectedOrders = orders;
        saving = true;
        CompletableFuture<Void> result = new CompletableFuture<>();
        saveTail = result;
        lib.supplyAsync(() -> {
            try {
                ContentDocument stagesDocument = document("stages.yml");
                ContentDocument exchangesDocument = document("exchanges.yml");
                YamlConfiguration stages = stagesDocument.yaml();
                YamlConfiguration exchanges = exchangesDocument.yaml();
                if (!expected.equals(new Snapshot(parse(orderedQuests(lib.content(), stages)), parseGroups(exchanges)))
                        || !expectedOrders.equals(parseOrders(exchanges)))
                    throw new IllegalStateException("NPC projects changed externally before removal: " + npc);
                removeNpcYaml(stages, exchanges, npc);
                Snapshot updated = new Snapshot(parse(orderedQuests(lib.content(), stages)), parseGroups(exchanges));
                Map<UUID, List<String>> updatedOrders = parseOrders(exchanges);
                try (var transaction = new AtomicYamlTransaction()) {
                    stagesDocument.write(transaction);
                    exchangesDocument.write(transaction);
                    transaction.commit();
                }
                return new RemovalResult(updated, updatedOrders);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((updated, error) -> lib.runOnMain(() -> {
            saving = false;
            if (error != null) result.completeExceptionally(error);
            else {
                if (enabled) {
                    snapshot = updated.snapshot();
                    orders = updated.orders();
                }
                result.complete(null);
            }
        }));
        return result;
    }

    static void removeNpcYaml(YamlConfiguration stages, YamlConfiguration exchanges, UUID npc) {
        for (CampaignStage stage : parse(stages)) {
            for (ExchangeOffer task : stage.tasks()) {
                if (task.npc().equals(npc)) stages.set("stages." + stage.id() + ".tasks." + task.id(), null);
            }
        }
        for (ExchangeGroup group : parseGroups(exchanges)) {
            if (group.npc().equals(npc)) exchanges.set("exchanges." + group.id(), null);
        }
        exchanges.set("offer-order." + npc, null);
    }

    private record RemovalResult(Snapshot snapshot, Map<UUID, List<String>> orders) {
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
        if (!Integer.valueOf(3).equals(configuration.get("version")))
            throw new IllegalArgumentException("stages.yml version must be 3; remove retired chapter metadata first");
        ConfigurationSection root = configuration.getConfigurationSection("stages");
        if (root == null) throw new IllegalArgumentException("stages.yml requires a stages section");
        List<CampaignStage> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            if (!id.matches("[a-z][a-z0-9_-]*")) throw new IllegalArgumentException("Invalid stage ID: " + id);
            ConfigurationSection stage = root.getConfigurationSection(id);
            if (stage == null) throw new IllegalArgumentException("Invalid stage: " + id);
            for (String key : stage.getKeys(false)) {
                if (!key.equals("tasks")) throw new IllegalArgumentException("Unknown stage key: " + id + "." + key);
            }
            result.add(new CampaignStage(id, offers(stage, "tasks", null)));
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
