package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import cn.mythicland.framework.bukkit.item.NumericRange;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Owns editable title definitions; new entries go to common and edits retain their source.
 */
@InjectComponent
public final class TitleCatalog implements ManagedLifecycle {
    private final DreamLibApi lib;
    private volatile Map<String, TitleDefinition> definitions = Map.of();
    private boolean saving;
    private Object generation = new Object();

    public TitleCatalog(DreamLibApi lib) {
        this.lib = lib;
    }

    @Override
    public void load() {
        reload();
    }

    @Override
    public void reload() {
        prepareContentReload().apply().run();
    }

    @Override
    public void unload() {
        generation = new Object();
        definitions = Map.of();
    }

    public Map<String, TitleDefinition> definitions() {
        return definitions;
    }

    public TitleDefinition require(String id) {
        TitleDefinition title = definitions.get(id);
        if (title == null) throw new IllegalArgumentException("Unknown title: " + id);
        return title;
    }

    public ContentUpdate prepareContentReload() {
        if (saving) throw new IllegalStateException("Title save is in progress");
        Map<String, TitleDefinition> previous = definitions;
        Map<String, TitleDefinition> next = validateContent(lib.content());
        return new ContentUpdate(() -> definitions = next, () -> definitions = previous);
    }

    public static Map<String, TitleDefinition> validateContent(ContentWorkspace content) {
        return parse(content.document("titles.yml").yaml());
    }

    static Map<String, TitleDefinition> parse(YamlConfiguration yaml) {
        if (yaml.getKeys(false).isEmpty()) return Map.of();
        ConfigurationSection titles = yaml.getConfigurationSection("titles");
        if (titles == null) throw new IllegalArgumentException("titles.yml requires a titles section");
        Map<String, TitleDefinition> result = new LinkedHashMap<>();
        for (String id : titles.getKeys(false)) {
            ConfigurationSection section = titles.getConfigurationSection(id);
            if (section == null || !section.isString("name"))
                throw new IllegalArgumentException("Missing title name: " + id);
            Map<CombatStat, NumericRange> bonuses = new EnumMap<>(CombatStat.class);
            if (section.contains("stats")) {
                ConfigurationSection stats = section.getConfigurationSection("stats");
                if (stats == null) throw new IllegalArgumentException("Invalid title stats: " + id);
                for (String key : stats.getKeys(false)) {
                    CombatStat stat = stat(key);
                    Object raw = stats.get(key);
                    if (!(raw instanceof String) && !(raw instanceof Number))
                        throw new IllegalArgumentException("Invalid title stat value: " + id + "." + key);
                    NumericRange range = NumericRange.parse(raw.toString());
                    bonuses.put(stat, normalize(stat, range));
                }
            }
            result.put(id, new TitleDefinition(id, section.getString("name"), bonuses));
        }
        return Collections.unmodifiableMap(result);
    }

    static CombatStat stat(String key) {
        for (CombatStat stat : CombatStat.values()) if (stat.configKey().equals(key)) return stat;
        throw new IllegalArgumentException("Unknown combat stat: " + key);
    }

    static NumericRange normalize(CombatStat stat, NumericRange range) {
        if (range.percent() && !stat.percentageAttribute())
            throw new IllegalArgumentException("Stat does not accept percentages: " + stat.configKey());
        return new NumericRange(range.minimum(), range.maximum(), stat.percentageAttribute());
    }

    public CompletableFuture<Void> edit(Consumer<YamlConfiguration> mutation) {
        if (saving) throw new IllegalStateException("Title save is in progress");
        saving = true;
        Object expected = generation;
        return lib.supplyAsync(() -> {
            var document = lib.content().document("titles.yml");
            if (!document.yaml().contains("titles")) document.yaml().createSection("titles");
            mutation.accept(document.yaml());
            Map<String, TitleDefinition> next = parse(document.yaml());
            document.save();
            return next;
        }).handle((next, failure) -> lib.runOnMain(() -> {
            if (generation != expected) throw new IllegalStateException("Title catalog closed during save");
            saving = false;
            if (failure != null) throw new CompletionException(failure);
            definitions = next;
        })).thenCompose(result -> result);
    }
}
