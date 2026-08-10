package cn.mythicland.dreamrpg.config;

import cn.mythicland.dreamrpg.api.CareerDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * Immutable career catalog loaded from careers.yml.
 */
public final class CareerCatalog {

    public static final String DEFAULT_CAREER_ID = "unclassed";

    private final Map<String, CareerDefinition> careers;

    private CareerCatalog(Map<String, CareerDefinition> careers) {
        this.careers = Collections.unmodifiableMap(new LinkedHashMap<>(careers));
    }

    /**
     * Loads and validates the plugin-owned careers.yml file.
     *
     * @param plugin plugin owning the data file
     * @return immutable catalog
     */
    public static CareerCatalog load(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        File file = new File(plugin.getDataFolder(), "careers.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection("careers");
        if (section == null) throw new IllegalStateException("careers.yml is missing the careers section");

        Map<String, CareerDefinition> definitions = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection careerSection = section.getConfigurationSection(key);
            if (careerSection == null) throw new IllegalStateException("Career is not a section: " + key);
            CareerDefinition definition = new CareerDefinition(
                    key,
                    requiredString(careerSection, "display-name", key),
                    requiredString(careerSection, "prefix", key)
            );
            if (definitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate career id: " + definition.id());
            }
        }
        if (!definitions.containsKey(DEFAULT_CAREER_ID)) {
            throw new IllegalStateException("careers.yml must define career: " + DEFAULT_CAREER_ID);
        }
        return new CareerCatalog(definitions);
    }

    private static String requiredString(ConfigurationSection section, String path, String careerId) {
        Object rawValue = section.get(path);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Career " + careerId + " requires a non-empty string: " + path);
        }
        return value;
    }

    /**
     * Finds a career by ID.
     *
     * @param id career ID
     * @return career definition when present
     */
    public Optional<CareerDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(careers.get(id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns a required career definition.
     *
     * @param id career ID
     * @return career definition
     */
    public CareerDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalStateException("Career is not configured: " + id));
    }

    /**
     * Returns the default unclassed career.
     *
     * @return default career
     */
    public CareerDefinition defaultCareer() {
        return require(DEFAULT_CAREER_ID);
    }

    /**
     * Returns all definitions in configuration order.
     *
     * @return immutable definitions
     */
    public Collection<CareerDefinition> all() {
        return careers.values();
    }
}
