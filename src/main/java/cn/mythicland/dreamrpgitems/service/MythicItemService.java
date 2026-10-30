package cn.mythicland.dreamrpgitems.service;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.dreamlib.item.ItemMarker;
import cn.mythicland.dreamlib.item.ItemMarkerCodec;
import cn.mythicland.dreamlib.item.ItemStackCodec;
import cn.mythicland.dreamlib.menu.PageWindow;
import cn.mythicland.dreamlib.path.ManagedPathResolver;
import cn.mythicland.dreamlib.storage.AtomicYamlTransaction;
import cn.mythicland.dreamlib.storage.YamlTree;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgitems.api.*;
import cn.mythicland.dreamrpgitems.story.StoryConfiguration;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.items.ItemManager;
import io.lumine.xikage.mythicmobs.items.MythicItem;
import io.lumine.xikage.mythicmobs.utils.config.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * MM item domain service. MythicMobs ItemManager and the original Items YAML files remain the
 * source of truth for all RPG items.
 */
@ServiceComponent(DreamRPGItemApi.class)
public final class MythicItemService implements DreamRPGItemApi {

    private static final String IDENTITY_NAMESPACE = "DreamRPG";
    private static final int IDENTITY_SCHEMA = 1;

    private static final Pattern ITEM_NAME = Pattern.compile("[A-Za-z0-9_\\-\\u4e00-\\u9fff]{1,64}");
    private static final List<String> REFERENCE_DIRECTORIES = List.of("Mobs", "DropTables", "Skills");
    private static final int MAX_IMPORT_FILES = 50;
    private static final int MAX_IMPORT_FILE_BYTES = 4 * 1024 * 1024;
    private static final Set<String> ITEM_SECTION_KEYS = Set.of(
            "id", "itemstack", "legacyitemstack", "material", "type", "data", "display", "lore", "amount",
            "durability", "enchantments", "enchants", "hide", "itemflags", "attributes",
            "options", "nbt", "unbreakable", "repair-cost", "repaircost", "custommodeldata",
            "modeldata"
    );
    private static final Set<String> ITEM_WRAPPER_KEYS = Set.of("item", "items", "definitions");

    private final JavaPlugin plugin;
    private final DreamLibApi lib;
    private final MythicMobs mythicMobs;
    private final ItemManager itemManager;
    private final ManagedPathResolver itemsPath;
    private final Path managedFile;
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, CatalogEntry> catalog = new LinkedHashMap<>();
    private final ItemMarkerCodec identityCodec;
    private String itemFingerprint = "";
    private String pendingFingerprint = "";

    public MythicItemService(DreamRPGPlugin plugin, DreamLibApi lib) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.mythicMobs = requireMythicMobs(plugin);
        this.itemManager = mythicMobs.getItemManager();
        Path itemsRoot = mythicMobs.getDataFolder().toPath().resolve("Items");
        this.itemsPath = lib.pathService().managed(itemsRoot);
        this.managedFile = itemsPath.resolve("DreamRPG/items.yml");
        this.identityCodec = lib.itemMarkerCodec();
    }

    private static MythicMobs requireMythicMobs(DreamRPGPlugin plugin) {
        Plugin dependency = Objects.requireNonNull(plugin, "plugin")
                .getServer()
                .getPluginManager()
                .getPlugin("MythicMobs");
        if (!(dependency instanceof MythicMobs mythicMobs)) {
            throw new IllegalStateException("MythicMobs is not enabled");
        }
        return mythicMobs;
    }

    public Path managedFile() {
        return managedFile;
    }

    /**
     * Returns the current item definition for addon-owned interactions.
     */
    public Optional<Map<String, Object>> configuration(String internalName) {
        requirePrimaryThread();
        CatalogEntry entry = catalog.get(internalName);
        return entry == null ? Optional.empty() : Optional.of(entry.configuration());
    }

    public void initialize() {
        requirePrimaryThread();
        try {
            itemsPath.ensureRootDirectory();
            ensureManagedFile();
            itemManager.loadItems();
            refreshCatalog();
            itemFingerprint = currentFingerprint();
            pendingFingerprint = itemFingerprint;
        } catch (IOException | RuntimeException exception) {
            throw new MythicItemException("INITIALIZE_FAILED", "无法初始化 MM 物品文件", exception);
        }
    }

    @Override
    public MythicItemPage search(MythicItemQuery query) {
        requirePrimaryThread();
        Objects.requireNonNull(query, "query");
        String search = query.searchText().toLowerCase(Locale.ROOT);
        Predicate<CatalogEntry> filter = entry -> matches(query, search, entry);

        List<CatalogEntry> matching = catalog.values().stream()
                .filter(filter)
                .sorted(Comparator.comparing((CatalogEntry entry) -> entry.summary().materialName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.summary().internalName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        PageWindow window = PageWindow.of(matching.size(), query.pageSize(), query.page());
        List<MythicItemSummary> page = matching.subList(window.startIndex(), window.endIndex()).stream()
                .map(CatalogEntry::summary)
                .toList();
        return new MythicItemPage(page, window.page(), window.pageSize(), matching.size(), generation.get());
    }

    private boolean matches(MythicItemQuery query, String search, CatalogEntry entry) {
        MythicItemSummary summary = entry.summary();
        if (query.source() == MythicItemSource.MANAGED && !summary.managed()) return false;
        if (query.source() == MythicItemSource.EXTERNAL && summary.managed()) return false;
        if (query.status() != null && query.status() != summary.status()) return false;
        if (search.isBlank()) return true;
        return summary.internalName().toLowerCase(Locale.ROOT).contains(search)
                || summary.displayName().toLowerCase(Locale.ROOT).contains(search)
                || summary.materialName().toLowerCase(Locale.ROOT).contains(search);
    }

    @Override
    public Optional<MythicItemDetails> find(String internalName) {
        requirePrimaryThread();
        if (internalName == null || internalName.isBlank()) return Optional.empty();
        CatalogEntry entry = catalog.get(internalName);
        return entry == null ? Optional.empty() : Optional.of(details(entry));
    }

    @Override
    public ItemStack getItemStack(String internalName, int amount) {
        requirePrimaryThread();
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("amount must be between 1 and 64");
        MythicItem item = itemManager.getItem(Objects.requireNonNull(internalName, "internalName"))
                .orElseThrow(() -> new MythicItemException("ITEM_NOT_FOUND", "MM 物品不存在: " + internalName));
        ItemStack stack = itemManager.getItemStack(item.getInternalName());
        if (stack == null) throw new MythicItemException("ITEM_STACK_FAILED", "MM 物品无法生成: " + internalName);
        ItemStack copy = stack.clone();
        copy.setAmount(Math.clamp(amount, 1, Math.max(1, copy.getMaxStackSize())));
        CatalogEntry entry = catalog.get(item.getInternalName());
        if (entry == null) throw new MythicItemException("ITEM_NOT_FOUND", "MM 物品目录未注册: " + internalName);
        return markGenerated(item.getInternalName(), copy);
    }

    /**
     * Marks an MM-generated stack without generating another item or firing another event.
     */
    public ItemStack markGenerated(String internalName, ItemStack copy) {
        requirePrimaryThread();
        CatalogEntry entry = catalog.get(internalName);
        if (entry == null) {
            return copy;
        }
        Map<?, ?> serialized = mapValue(entry.configuration(), "LegacyItemStack", "ItemStack");
        if (serialized != null) {
            ItemStack original = ItemStackCodec.deserialize(YamlTree.mutableMap(serialized));
            var metadata = original.getItemMeta();
            var display = copy.getItemMeta();
            metadata.setDisplayName(display.getDisplayName());
            metadata.setLore(display.getLore());
            for (var enchantment : metadata.getEnchants().keySet()) {
                metadata.removeEnchant(enchantment);
            }
            display.getEnchants().forEach((enchantment, level) -> metadata.addEnchant(enchantment, level, true));
            metadata.addItemFlags(display.getItemFlags().toArray(ItemFlag[]::new));
            copy.setItemMeta(metadata);
        }
        return identityCodec.write(
                copy,
                new ItemMarker(
                        IDENTITY_NAMESPACE,
                        IDENTITY_SCHEMA,
                        Map.of(
                                "item-id", internalName,
                                "revision", entry.summary().revision()
                        )
                )
        );
    }

    @Override
    public ItemStack materialize(String internalName) {
        requirePrimaryThread();
        CatalogEntry entry = catalog.get(Objects.requireNonNull(internalName, "internalName"));
        if (entry == null) throw new MythicItemException("ITEM_NOT_FOUND", "MM 物品不存在: " + internalName);
        return getItemStack(entry.item().getInternalName(), Math.clamp(entry.summary().amount(), 1, 64));
    }

    @Override
    public Optional<MythicItemIdentity> identify(ItemStack item) {
        requirePrimaryThread();
        return identityCodec.read(item, IDENTITY_NAMESPACE)
                .filter(marker -> marker.schema() == IDENTITY_SCHEMA)
                .flatMap(this::identity);
    }

    private Optional<MythicItemIdentity> identity(ItemMarker marker) {
        String internalName = marker.values().get("item-id");
        String revision = marker.values().get("revision");
        if (internalName == null || internalName.isBlank() || revision == null || revision.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MythicItemIdentity(internalName, revision));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Override
    public MythicItemRefreshResult refresh(ItemStack item, MythicItemRefreshMode mode) {
        requirePrimaryThread();
        Objects.requireNonNull(mode, "mode");
        Optional<MythicItemIdentity> identity = identify(item);
        if (identity.isEmpty()) {
            return new MythicItemRefreshResult(MythicItemRefreshStatus.UNMANAGED, item, item, null);
        }
        MythicItemIdentity currentIdentity = identity.orElseThrow();
        CatalogEntry entry = catalog.get(currentIdentity.internalName());
        if (entry == null) {
            return new MythicItemRefreshResult(MythicItemRefreshStatus.STALE, item, item, currentIdentity);
        }
        MythicItemIdentity latest = new MythicItemIdentity(
                entry.item().getInternalName(), entry.summary().revision()
        );
        if (latest.revision().equals(currentIdentity.revision())) {
            return new MythicItemRefreshResult(MythicItemRefreshStatus.CURRENT, item, item, currentIdentity);
        }
        ItemStack replacement = getItemStack(
                entry.item().getInternalName(),
                Math.clamp(entry.summary().amount(), 1, 64)
        );
        if (mode == MythicItemRefreshMode.EXISTING_INSTANCE && item != null) {
            replacement.setAmount(Math.clamp(item.getAmount(), 1, Math.max(1, replacement.getMaxStackSize())));
        }
        return new MythicItemRefreshResult(
                MythicItemRefreshStatus.UPDATED, item, replacement, latest
        );
    }

    @Override
    public MythicItemWriteResult create(MythicItemCreateRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        validateItemName(request.internalName());
        if (catalog.containsKey(request.internalName())) {
            return result(MythicItemMutationStatus.CONFLICT, request.internalName(), "MM 物品 ID 已存在", null, "");
        }
        return writeItem(
                "",
                request.internalName(),
                request.configuration(),
                managedFile,
                MythicItemMutationStatus.CREATED,
                false,
                false
        );
    }

    @Override
    public MythicItemWriteResult update(MythicItemUpdateRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        validateItemName(request.internalName());
        validateItemName(request.newInternalName());
        Optional<CatalogEntry> current = Optional.ofNullable(catalog.get(request.internalName()));
        if (current.isEmpty()) return invalidItemResult(request.internalName());
        CatalogEntry currentEntry = current.orElseThrow();
        boolean renamed = !request.internalName().equals(request.newInternalName());
        if (!currentEntry.summary().editable()) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "该 MM 物品来源文件不可写",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        if (!currentEntry.summary().managed() && !request.confirmExternalMutation()) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "编辑外部 MM 文件需要确认",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        if (!request.expectedRevision().isBlank()
                && !request.expectedRevision().equals(currentEntry.summary().revision())) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "物品已被其他操作修改",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        if (renamed && catalog.containsKey(request.newInternalName())) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "新的 MM 物品 ID 已存在",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        Map<String, Object> merged = mergeConfiguration(currentEntry.configuration(), request.configuration());
        return writeItem(
                request.internalName(),
                request.newInternalName(),
                merged,
                currentEntry.sourceFile(),
                !renamed
                        ? MythicItemMutationStatus.UPDATED
                        : MythicItemMutationStatus.RENAMED,
                renamed,
                !currentEntry.summary().managed()
        );
    }

    @Override
    public MythicItemWriteResult delete(MythicItemDeleteRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        validateItemName(request.internalName());
        Optional<CatalogEntry> current = Optional.ofNullable(catalog.get(request.internalName()));
        if (current.isEmpty()) return invalidItemResult(request.internalName());
        CatalogEntry currentEntry = current.orElseThrow();
        if (!currentEntry.summary().editable()) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "该 MM 物品来源文件不可写",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        if (!currentEntry.summary().managed() && !request.confirmExternalMutation()) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "删除外部 MM 文件物品需要确认",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        if (!request.expectedRevision().isBlank()
                && !request.expectedRevision().equals(currentEntry.summary().revision())) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "物品已被其他操作修改",
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        List<ReferenceHit> references = referenceHits(request.internalName());
        if (!references.isEmpty()) {
            return result(
                    MythicItemMutationStatus.CONFLICT,
                    request.internalName(),
                    "物品仍被 MM 配置引用: " + references.getFirst().relativeFile(),
                    currentEntry.details(),
                    currentEntry.summary().revision()
            );
        }
        return writeItem(
                request.internalName(),
                "",
                Map.of(),
                currentEntry.sourceFile(),
                MythicItemMutationStatus.DELETED,
                false,
                !currentEntry.summary().managed()
        );
    }

    @Override
    public MythicItemsReloadResult reload() {
        return reload(true);
    }

    /**
     * Refreshes the item catalog and requests reconciliation of all managed references.
     *
     * @return catalog refresh result
     * @throws IllegalStateException if called off the primary thread
     */
    public MythicItemsReloadResult synchronizeAll() {
        MythicItemsReloadResult result = reload(false);
        if (result.success()) Bukkit.getPluginManager().callEvent(new MythicItemsSyncRequestedEvent());
        return result;
    }

    private MythicItemsReloadResult reload(boolean publishChanges) {
        requirePrimaryThread();
        try {
            Map<String, String> previous = revisionSnapshot();
            itemManager.loadItems();
            refreshCatalog();
            itemFingerprint = currentFingerprint();
            pendingFingerprint = itemFingerprint;
            if (publishChanges) publishChanges(previous);
            return new MythicItemsReloadResult(true, catalog.size(), generation.get(), "MythicMobs 物品已重新加载");
        } catch (RuntimeException exception) {
            return new MythicItemsReloadResult(false, catalog.size(), generation.get(), exception.getMessage());
        }
    }

    @Override
    public MythicItemImportResult previewImport(MythicItemImportRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        ImportAnalysis analysis = analyzeImport(request);
        return importResult(
                analysis,
                analysis.status(),
                analysis.status() == MythicItemImportStatus.PREVIEW
                        ? "已识别 MM 物品，确认后将写入 MythicMobs 物品目录"
                        : "导入预览存在问题，未写入任何文件"
        );
    }

    @Override
    public MythicItemImportResult importItems(MythicItemImportRequest request) {
        requirePrimaryThread();
        Objects.requireNonNull(request, "request");
        ImportAnalysis analysis = analyzeImport(request);
        if (analysis.status() != MythicItemImportStatus.PREVIEW) {
            return importResult(analysis, analysis.status(), "导入未执行，未写入任何文件");
        }

        try (AtomicYamlTransaction transaction = lib.storageService().transaction()) {
            YamlConfiguration configuration = loadYaml(managedFile);
            Map<String, String> previous = revisionSnapshot();
            for (ParsedImportItem item : analysis.items()) {
                setItem(configuration, item.internalName(), item.configuration());
            }
            transaction.write(managedFile, configuration);

            itemManager.loadItems();
            refreshCatalog();
            verifyImportedItems(analysis.items());
            transaction.commit();
            itemFingerprint = currentFingerprint();
            pendingFingerprint = itemFingerprint;
            publishChanges(previous);
            return importResult(
                    analysis,
                    MythicItemImportStatus.IMPORTED,
                    "已导入 " + analysis.items().size() + " 个 MM 物品"
            );
        } catch (IOException | RuntimeException exception) {
            try {
                itemManager.loadItems();
                refreshCatalog();
            } catch (RuntimeException rollbackException) {
                plugin.getLogger().severe("DreamRPG items import rollback refresh failed: "
                        + safeMessage(rollbackException));
            }
            return importResult(
                    analysis,
                    MythicItemImportStatus.FAILED,
                    "MM 批量导入失败: " + safeMessage(exception)
            );
        }
    }

    @Override
    public MythicItemEditorCatalog editorCatalog() {
        requirePrimaryThread();
        return new MythicItemEditorCatalog(
                lib.materialCatalog().entries(),
                lib.enchantmentCatalog().entries(),
                Arrays.stream(ItemFlag.values()).map(Enum::name).toList()
        );
    }

    /**
     * Refreshes the MM registry after a MythicMobs reload event.
     */
    public void refreshAfterMythicReload() {
        requirePrimaryThread();
        Map<String, String> previous = revisionSnapshot();
        refreshCatalog();
        itemFingerprint = currentFingerprint();
        pendingFingerprint = itemFingerprint;
        publishChanges(previous);
    }

    /**
     * Detects manual changes below MythicMobs/Items and refreshes the registry on the main thread.
     */
    public void refreshIfFilesChanged() {
        requirePrimaryThread();
        String currentFingerprint = currentFingerprint();
        if (currentFingerprint.equals(itemFingerprint)) return;
        if (!currentFingerprint.equals(pendingFingerprint)) {
            pendingFingerprint = currentFingerprint;
            return;
        }
        Map<String, String> previous = revisionSnapshot();
        itemManager.loadItems();
        refreshCatalog();
        itemFingerprint = currentFingerprint;
        pendingFingerprint = itemFingerprint;
        publishChanges(previous);
    }

    private MythicItemWriteResult writeItem(
            String oldName,
            String newName,
            Map<String, Object> values,
            Path sourceFile,
            MythicItemMutationStatus mutationStatus,
            boolean migrateReferences,
            boolean externalMutation
    ) {
        requirePrimaryThread();
        ensureWritableYaml(sourceFile);
        Map<Path, String> rewrittenReferences = new LinkedHashMap<>();
        try (AtomicYamlTransaction transaction = lib.storageService().transaction()) {
            YamlConfiguration sourceConfiguration = loadYaml(sourceFile);
            Map<String, String> previous = revisionSnapshot();
            if (mutationStatus == MythicItemMutationStatus.DELETED) {
                sourceConfiguration.set(oldName, null);
            } else if (oldName.isBlank()) {
                setItem(sourceConfiguration, newName, values);
            } else if (!oldName.equals(newName)) {
                sourceConfiguration.set(oldName, null);
                setItem(sourceConfiguration, newName, values);
                if (migrateReferences) rewrittenReferences.putAll(rewriteReferences(oldName, newName));
            } else {
                setItem(sourceConfiguration, newName, values);
            }

            transaction.write(sourceFile, sourceConfiguration);
            for (Map.Entry<Path, String> entry : rewrittenReferences.entrySet()) {
                transaction.writeText(entry.getKey(), entry.getValue());
            }

            itemManager.loadItems();
            refreshCatalog();
            verifyMutation(oldName, newName, mutationStatus, sourceFile);
            transaction.commit();
            itemFingerprint = currentFingerprint();
            pendingFingerprint = itemFingerprint;
            publishChanges(previous);

            CatalogEntry entry = mutationStatus == MythicItemMutationStatus.DELETED ? null : catalog.get(newName);
            String revision = entry == null ? "" : entry.summary().revision();
            MythicItemDetails details = entry == null ? null : entry.details();
            String message = mutationStatus == MythicItemMutationStatus.RENAMED
                    ? "MM 物品已改名并迁移引用"
                    : mutationStatus == MythicItemMutationStatus.DELETED ? "MM 物品已删除" : "MM 物品已保存";
            return result(
                    mutationStatus,
                    newName.isBlank() ? oldName : newName,
                    message,
                    details,
                    revision,
                    mutationStatus == MythicItemMutationStatus.RENAMED ? oldName : "",
                    rewrittenReferences.keySet().stream().map(this::relativeFile).toList()
            );
        } catch (IOException | RuntimeException exception) {
            try {
                itemManager.loadItems();
                refreshCatalog();
            } catch (RuntimeException rollbackException) {
                plugin.getLogger().severe("DreamRPG items rollback refresh failed: " + rollbackException.getMessage());
            }
            String prefix = externalMutation ? "外部 MM 文件写入失败: " : "MM 物品写入失败: ";
            return result(MythicItemMutationStatus.FAILED, newName.isBlank() ? oldName : newName,
                    prefix + safeMessage(exception), null, "");
        }
    }

    private void verifyMutation(
            String oldName,
            String newName,
            MythicItemMutationStatus mutationStatus,
            Path sourceFile
    ) throws IOException {
        if (mutationStatus == MythicItemMutationStatus.DELETED) {
            if (catalog.containsKey(oldName)) throw new IOException("MM reload still contains deleted item " + oldName);
            return;
        }
        CatalogEntry entry = catalog.get(newName);
        if (entry == null) throw new IOException("MM reload did not register " + newName);
        if (!sourceFile.equals(entry.sourceFile())) {
            throw new IOException("MM registered " + newName + " from an unexpected source file");
        }
        if (mutationStatus == MythicItemMutationStatus.RENAMED && catalog.containsKey(oldName)) {
            throw new IOException("MM reload still contains old item ID " + oldName);
        }
    }

    @SuppressWarnings("VulnerableCodeUsages")
    private ImportAnalysis analyzeImport(MythicItemImportRequest request) {
        List<MythicItemImportCandidate> candidates = new ArrayList<>();
        List<ParsedImportItem> items = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> namesInBatch = new HashSet<>();

        if (request.files().isEmpty()) {
            errors.add("至少选择一个 YAML 文件");
        }
        if (request.files().size() > MAX_IMPORT_FILES) {
            errors.add("单次最多导入 " + MAX_IMPORT_FILES + " 个 YAML 文件");
        }

        for (MythicItemImportFile file : request.files().stream().limit(MAX_IMPORT_FILES).toList()) {
            if (file == null) {
                errors.add("导入文件不能为空");
                continue;
            }
            String fileName = displayFileName(file.fileName());
            if (!isYamlFileName(fileName)) {
                errors.add(fileName + "：只支持 .yml 或 .yaml 文件");
                continue;
            }
            byte[] content = file.content();
            if (content.length > MAX_IMPORT_FILE_BYTES) {
                errors.add(fileName + "：文件不能超过 " + (MAX_IMPORT_FILE_BYTES / 1024 / 1024) + " MB");
                continue;
            }

            Object parsedYaml;
            try {
                // Legacy MM files contain Bukkit serialization aliases such as ItemMeta. Keep them
                // as plain YAML maps so import does not depend on server-side alias registration.
                parsedYaml = new Yaml(new SafeConstructor())
                        .load(stripUtf8Bom(new String(content, StandardCharsets.UTF_8)));
            } catch (RuntimeException exception) {
                errors.add(fileName + "：YAML 解析失败");
                continue;
            }

            if (!(parsedYaml instanceof Map<?, ?> root)) {
                errors.add(fileName + "：YAML 顶层必须是对象");
                continue;
            }
            List<ParsedImportItem> parsed = extractImportItems(root, fileName);
            if (parsed.isEmpty()) {
                warnings.add(fileName + "：未识别到 MM 物品节点");
                continue;
            }
            for (ParsedImportItem item : parsed) {
                candidates.add(new MythicItemImportCandidate(
                        item.internalName(),
                        item.fileName(),
                        item.format()
                ));
                try {
                    validateItemName(item.internalName());
                } catch (MythicItemException exception) {
                    errors.add(fileName + " / " + item.internalName() + "：MM ID 无效");
                    continue;
                }
                String normalizedName = item.internalName().toLowerCase(Locale.ROOT);
                if (!namesInBatch.add(normalizedName)) {
                    conflicts.add(fileName + " / " + item.internalName() + "：批量文件中重复定义");
                    continue;
                }
                String existingName = existingItemName(item.internalName());
                if (existingName != null) {
                    conflicts.add(fileName + " / " + item.internalName() + "：已存在于 MM 物品库（" + existingName + "）");
                    continue;
                }
                items.add(item);
            }
        }

        if (candidates.isEmpty() && errors.isEmpty() && conflicts.isEmpty()) {
            errors.add("没有可导入的 MM 物品");
        }
        MythicItemImportStatus status = !errors.isEmpty()
                ? MythicItemImportStatus.INVALID
                : !conflicts.isEmpty() ? MythicItemImportStatus.CONFLICT : MythicItemImportStatus.PREVIEW;
        return new ImportAnalysis(request.files().size(), candidates, items, conflicts, warnings, errors, status);
    }

    private List<ParsedImportItem> extractImportItems(Map<?, ?> root, String fileName) {
        List<ParsedImportItem> result = new ArrayList<>();
        for (Map.Entry<?, ?> rootEntry : root.entrySet()) {
            String key = String.valueOf(rootEntry.getKey());
            if (!(rootEntry.getValue() instanceof Map<?, ?> section)) continue;
            if (isItemSection(section)) {
                result.add(parsedImportItem(key, fileName, section));
                continue;
            }
            if (!ITEM_WRAPPER_KEYS.contains(key.toLowerCase(Locale.ROOT))) continue;
            for (Map.Entry<?, ?> childEntry : section.entrySet()) {
                String childKey = String.valueOf(childEntry.getKey());
                if (childEntry.getValue() instanceof Map<?, ?> child && isItemSection(child)) {
                    result.add(parsedImportItem(childKey, fileName, child));
                }
            }
        }
        return result;
    }

    private ParsedImportItem parsedImportItem(
            String internalName,
            String fileName,
            Map<?, ?> section
    ) {
        Map<String, Object> values = YamlTree.immutableMap(section);
        String format = values.keySet().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .anyMatch(key -> key.equals("itemstack"))
                ? "LEGACY_ITEMSTACK" : "MM_ITEM";
        values = YamlTree.immutableMap(LegacyItemStackNormalizer.normalize(values));
        return new ParsedImportItem(internalName, fileName, format, values);
    }

    private boolean isItemSection(Map<?, ?> section) {
        return section.keySet().stream()
                .map(String::valueOf)
                .map(key -> key.toLowerCase(Locale.ROOT))
                .anyMatch(ITEM_SECTION_KEYS::contains);
    }

    private void verifyImportedItems(List<ParsedImportItem> items) throws IOException {
        Path expectedFile = managedFile.toAbsolutePath().normalize();
        for (ParsedImportItem item : items) {
            String actualName = existingItemName(item.internalName());
            CatalogEntry entry = actualName == null ? null : catalog.get(actualName);
            if (entry == null) throw new IOException("MM reload did not register imported item " + item.internalName());
            if (!expectedFile.equals(entry.sourceFile().toAbsolutePath().normalize())) {
                throw new IOException("MM imported item came from an unexpected source file " + item.internalName());
            }
        }
    }

    private String existingItemName(String internalName) {
        return catalog.keySet().stream()
                .filter(existing -> existing.equalsIgnoreCase(internalName))
                .findFirst()
                .orElse(null);
    }

    private MythicItemImportResult importResult(
            ImportAnalysis analysis,
            MythicItemImportStatus status,
            String message
    ) {
        return new MythicItemImportResult(
                status,
                message,
                analysis.fileCount(),
                analysis.candidates(),
                analysis.conflicts(),
                analysis.warnings(),
                analysis.errors()
        );
    }

    private String displayFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return (separator < 0 ? normalized : normalized.substring(separator + 1)).trim();
    }

    private boolean isYamlFileName(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
    }

    private String stripUtf8Bom(String value) {
        return value.startsWith("\ufeff") ? value.substring(1) : value;
    }

    private Map<Path, String> rewriteReferences(String oldName, String newName) {
        Map<Path, String> rewritten = new LinkedHashMap<>();
        for (ReferenceHit hit : referenceHits(oldName)) {
            try {
                String content = Files.readString(hit.path(), StandardCharsets.UTF_8);
                String replacement = replaceTokenOnReferenceLines(content, oldName, newName);
                if (!content.equals(replacement)) rewritten.put(hit.path(), replacement);
            } catch (IOException exception) {
                throw new MythicItemException(
                        "REFERENCE_CHECK_FAILED",
                        "无法读取 MM 引用文件: " + hit.relativeFile(),
                        exception
                );
            }
        }
        return rewritten;
    }

    private List<ReferenceHit> referenceHits(String itemName) {
        List<ReferenceHit> hits = new ArrayList<>();
        Path dataFolder = mythicMobs.getDataFolder().toPath();
        for (String directory : REFERENCE_DIRECTORIES) {
            Path root = dataFolder.resolve(directory);
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(this::isYaml)
                        .forEach(path -> collectReferenceHits(path, root, itemName, hits));
            } catch (IOException exception) {
                throw new MythicItemException("REFERENCE_CHECK_FAILED", "无法检查 MM 物品引用", exception);
            }
        }
        return List.copyOf(hits);
    }

    private void collectReferenceHits(
            Path path,
            Path root,
            String itemName,
            List<ReferenceHit> hits
    ) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            for (int line : MythicItemReferenceScanner.findReferenceLines(content, itemName)) {
                hits.add(new ReferenceHit(path, relativeFile(path), line));
            }
        } catch (IOException exception) {
            throw new MythicItemException("REFERENCE_CHECK_FAILED", "无法读取 MM 配置: " + root.relativize(path),
                    exception);
        }
    }

    private String replaceTokenOnReferenceLines(String content, String oldName, String newName) {
        return MythicItemReferenceScanner.replaceReferences(content, oldName, newName);
    }

    private void ensureManagedFile() throws IOException {
        if (Files.exists(managedFile, LinkOption.NOFOLLOW_LINKS)) return;
        lib.storageService().writeUtf8(managedFile, "# MythicMobs items managed by DreamRPG items.\n");
    }

    private YamlConfiguration loadYaml(Path sourceFile) {
        if (!Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new MythicItemException("SOURCE_NOT_WRITABLE", "MM 物品来源文件不存在: " + relativeFile(sourceFile));
        }
        return YamlConfiguration.loadConfiguration(sourceFile.toFile());
    }

    private void ensureWritableYaml(Path sourceFile) {
        if (sourceFile == null) {
            throw new MythicItemException("SOURCE_NOT_WRITABLE", "MM 物品来源文件不能为空");
        }
        Path normalized = sourceFile.toAbsolutePath().normalize();
        if (!normalized.startsWith(itemsPath.root())) {
            throw new MythicItemException("SOURCE_NOT_WRITABLE", "MM 物品来源文件不在 Items 目录内");
        }
        if (!isYaml(normalized)) {
            throw new MythicItemException("SOURCE_NOT_WRITABLE", "只允许修改 YAML 物品文件");
        }
        Path current = itemsPath.root();
        for (Path segment : itemsPath.root().relativize(normalized)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new MythicItemException("SOURCE_NOT_WRITABLE", "MM 物品来源路径不能经过符号链接");
            }
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new MythicItemException("SOURCE_NOT_WRITABLE", "MM 物品来源文件不存在或不是普通文件");
        }
    }

    private void setItem(YamlConfiguration configuration, String internalName, Map<String, Object> values) {
        StoryConfiguration.from(values);
        configuration.set(internalName, null);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            configuration.set(internalName + "." + entry.getKey(), YamlTree.mutable(entry.getValue()));
        }
    }

    private Map<String, Object> mergeConfiguration(Map<String, Object> current, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>();
        current.forEach((key, value) -> merged.put(key, YamlTree.mutable(value)));
        incoming.forEach((key, value) -> {
            Object existing = merged.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> incomingMap) {
                merged.put(key, mergeConfiguration(toStringKeyedMap(existingMap), toStringKeyedMap(incomingMap)));
            } else {
                merged.put(key, YamlTree.mutable(value));
            }
        });
        return merged;
    }

    private Map<String, Object> toStringKeyedMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private void validateItemName(String internalName) {
        if (internalName == null || !ITEM_NAME.matcher(internalName).matches()) {
            throw new MythicItemException("INVALID_ITEM_ID", "MM ID 只能包含字母、数字、下划线、连字符或中文");
        }
    }

    private void refreshCatalog() {
        requirePrimaryThread();
        Map<String, CatalogEntry> next = new LinkedHashMap<>();
        Map<Path, YamlConfiguration> sources = new HashMap<>();
        Collection<MythicItem> items = itemManager.getItems();
        for (MythicItem item : items) {
            if (item == null || item.getInternalName() == null) continue;
            next.put(item.getInternalName(), toEntry(item, sources));
        }
        catalog.clear();
        catalog.putAll(next);
        generation.incrementAndGet();
    }

    private Map<String, String> revisionSnapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        catalog.forEach((id, entry) -> snapshot.put(id, entry.summary().revision()));
        return snapshot;
    }

    private void publishChanges(Map<String, String> previous) {
        Map<String, String> current = revisionSnapshot();
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            if (!Objects.equals(previous.get(entry.getKey()), entry.getValue())) changed.add(entry.getKey());
        }
        Set<String> removed = new LinkedHashSet<>(previous.keySet());
        removed.removeAll(current.keySet());
        if (changed.isEmpty() && removed.isEmpty()) return;
        Bukkit.getPluginManager().callEvent(new MythicItemsChangedEvent(
                changed, removed, previous, current, generation.get()
        ));
    }

    @SuppressWarnings("deprecation")
    private CatalogEntry toEntry(MythicItem item, Map<Path, YamlConfiguration> sources) {
        Path sourceFile = sourcePath(item);
        Map<String, Object> configuration = copyItemSection(item, sourceFile, sources);
        String rawYaml = renderItemYaml(item.getInternalName(), configuration);
        LegacyItemView legacy = legacyItemView(configuration);
        String configuredDisplayName = text(configuration, "Display", "display", "Name", "name");
        String displayName = configuredDisplayName.isBlank()
                ? legacy.displayName().orElse(item.getDisplayName())
                : configuredDisplayName;
        String configuredMaterialName = text(configuration, "Id", "Material", "Type");
        String materialName = configuredMaterialName.isBlank()
                ? legacy.materialName().orElse(item.getMaterialName())
                : configuredMaterialName;
        if (materialName.isBlank() || materialName.equalsIgnoreCase("UNKNOWN")) {
            materialName = legacy.materialName().orElse(materialName);
        }
        int data = number(configuration.get("Data"), number(configuration.get("Durability"),
                legacy.data().orElse(item.getMaterialData())));
        int amount = number(configuration.get("Amount"), legacy.amount().orElse(item.getAmount()));
        boolean editable = sourceFile.startsWith(itemsPath.root()) && isYaml(sourceFile)
                && !Files.isSymbolicLink(sourceFile);
        List<String> warnings = editable ? List.of() : List.of("来源文件不可由网页写入");
        MythicItemSummary summary = new MythicItemSummary(
                item.getInternalName(),
                relativeFile(sourceFile),
                sourceFile.startsWith(managedFile.getParent()),
                MythicItemStatus.LOADED,
                displayName,
                materialName,
                Math.max(1, amount),
                warnings,
                revision(rawYaml),
                editable,
                lib.materialIconCatalog().iconUrls(materialName, data)
        );
        return new CatalogEntry(summary, configuration, rawYaml, item, sourceFile);
    }

    private Map<String, Object> copyItemSection(MythicItem item, Path sourceFile,
            Map<Path, YamlConfiguration> sources) {
        if (Files.isRegularFile(sourceFile, LinkOption.NOFOLLOW_LINKS)) {
            YamlConfiguration configuration = sources.computeIfAbsent(sourceFile,
                    path -> YamlConfiguration.loadConfiguration(path.toFile()));
            org.bukkit.configuration.ConfigurationSection section = configuration
                    .getConfigurationSection(item.getInternalName());
            if (section != null) return copyBukkitSection(section);
        }
        return copyMythicSection(item);
    }

    private Map<String, Object> copyMythicSection(MythicItem item) {
        ConfigurationSection section = item.getConfig().getFileConfiguration()
                .getConfigurationSection(item.getInternalName());
        if (section == null) return Map.of();
        return copyMythicMap(section.getValues(false));
    }

    private Map<String, Object> copyBukkitSection(org.bukkit.configuration.ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) result.put(key, copyBukkitValue(section.get(key)));
        return result;
    }

    private Object copyBukkitValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) return copyBukkitSection(section);
        if (value instanceof Map<?, ?> map) return copyBukkitMap(map);
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(entry -> result.add(copyBukkitValue(entry)));
            return result;
        }
        return YamlTree.immutable(value);
    }

    private Map<String, Object> copyBukkitMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), copyBukkitValue(value)));
        return result;
    }

    private Map<String, Object> copyMythicMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), copyMythicValue(value)));
        return result;
    }

    private Object copyMythicValue(Object value) {
        if (value instanceof ConfigurationSection section) return copyMythicMap(section.getValues(false));
        if (value instanceof Map<?, ?> map) return copyMythicMap(map);
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(entry -> result.add(copyMythicValue(entry)));
            return result;
        }
        return YamlTree.immutable(value);
    }

    private LegacyItemView legacyItemView(Map<String, Object> configuration) {
        Map<?, ?> stack = mapValue(configuration, "LegacyItemStack", "ItemStack");
        if (stack == null) return LegacyItemView.empty();
        Map<?, ?> meta = mapValue(stack, "meta", "Meta");
        String displayName = meta == null ? "" : text(meta, "display-name", "displayName", "name");
        String materialName = text(stack, "type", "material", "id");
        Optional<Integer> amount = integer(stack, "amount", "Amount");
        Optional<Integer> data = integer(stack, "damage", "durability", "data", "Data");
        return new LegacyItemView(
                displayName.isBlank() ? Optional.empty() : Optional.of(displayName),
                materialName.isBlank() ? Optional.empty() : Optional.of(materialName),
                amount,
                data
        );
    }

    private Map<?, ?> mapValue(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        return value instanceof Map<?, ?> map ? map : null;
    }

    private Object value(Map<?, ?> source, String... keys) {
        if (source == null) return null;
        Set<String> names = new HashSet<>();
        for (String key : keys) names.add(key.toLowerCase(Locale.ROOT));
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null && names.contains(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String text(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        if (value == null) return "";
        String text = String.valueOf(value);
        return text.isBlank() ? "" : text;
    }

    private Optional<Integer> integer(Map<?, ?> source, String... keys) {
        Object value = value(source, keys);
        if (value instanceof Number number) return Optional.of(number.intValue());
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(String.valueOf(value).trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private MythicItemDetails details(CatalogEntry entry) {
        ItemStack preview;
        try {
            preview = getItemStack(entry.item().getInternalName(), Math.max(1, entry.item().getAmount()));
        } catch (RuntimeException exception) {
            preview = null;
        }
        return new MythicItemDetails(entry.summary(), entry.configuration(), entry.rawYaml(), preview,
                entry.summary().revision());
    }

    private Path sourcePath(MythicItem item) {
        File file = item.getConfig() == null ? null : item.getConfig().getFile();
        if (file == null) return Path.of("").toAbsolutePath().normalize();
        return file.toPath().toAbsolutePath().normalize();
    }

    private String relativeFile(Path source) {
        Path normalized = source.toAbsolutePath().normalize();
        if (normalized.startsWith(itemsPath.root())) {
            return itemsPath.root().relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    private String renderItemYaml(String internalName, Map<String, Object> values) {
        YamlConfiguration configuration = new YamlConfiguration();
        setItem(configuration, internalName, values);
        return configuration.saveToString();
    }

    private String revision(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean isYaml(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private String currentFingerprint() {
        try {
            return lib.storageService().fingerprint(itemsPath.root());
        } catch (IOException exception) {
            throw new MythicItemException("ITEM_SCAN_FAILED", "无法检查 MM Items 目录变化", exception);
        }
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private MythicItemWriteResult invalidItemResult(String internalName) {
        return result(MythicItemMutationStatus.INVALID, internalName, "MM 物品不存在", null, "");
    }

    private MythicItemWriteResult result(
            MythicItemMutationStatus status,
            String internalName,
            String message,
            MythicItemDetails item,
            String revision
    ) {
        return result(status, internalName, message, item, revision, "", List.of());
    }

    private MythicItemWriteResult result(
            MythicItemMutationStatus status,
            String internalName,
            String message,
            MythicItemDetails item,
            String revision,
            String previousInternalName,
            List<String> affectedFiles
    ) {
        return new MythicItemWriteResult(
                status,
                internalName,
                message,
                item,
                revision,
                previousInternalName,
                affectedFiles
        );
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DreamRPG items API must run on Bukkit's primary thread");
        }
    }

    private record CatalogEntry(
            MythicItemSummary summary,
            Map<String, Object> configuration,
            String rawYaml,
            MythicItem item,
            Path sourceFile
    ) {
        private CatalogEntry {
            configuration = YamlTree.immutableMap(configuration);
        }

        private MythicItemDetails details() {
            return new MythicItemDetails(summary, configuration, rawYaml, null, summary.revision());
        }
    }

    private record LegacyItemView(
            Optional<String> displayName,
            Optional<String> materialName,
            Optional<Integer> amount,
            Optional<Integer> data
    ) {
        private static LegacyItemView empty() {
            return new LegacyItemView(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    private record ReferenceHit(Path path, String relativeFile, int line) {
    }

    private record ParsedImportItem(
            String internalName,
            String fileName,
            String format,
            Map<String, Object> configuration
    ) {
        private ParsedImportItem {
            configuration = YamlTree.immutableMap(configuration);
        }
    }

    private record ImportAnalysis(
            int fileCount,
            List<MythicItemImportCandidate> candidates,
            List<ParsedImportItem> items,
            List<String> conflicts,
            List<String> warnings,
            List<String> errors,
            MythicItemImportStatus status
    ) {
        private ImportAnalysis {
            candidates = List.copyOf(candidates);
            items = List.copyOf(items);
            conflicts = List.copyOf(conflicts);
            warnings = List.copyOf(warnings);
            errors = List.copyOf(errors);
        }
    }
}
