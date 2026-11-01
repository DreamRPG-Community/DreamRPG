package cn.mythicland.dreamrpgitems.api;

import cn.mythicland.framework.bukkit.storage.YamlTree;

import java.util.Map;

/**
 * Request to create one item in the MythicMobs item library.
 */
public record MythicItemCreateRequest(
        String internalName,
        Map<String, Object> configuration,
        String contentPackage
) {

    public MythicItemCreateRequest(String internalName, Map<String, Object> configuration) {
        this(internalName, configuration, "common");
    }

    public MythicItemCreateRequest {
        contentPackage = contentPackage == null || contentPackage.isBlank() ? "common" : contentPackage;
        internalName = requireName(internalName);
        configuration = configuration == null ? Map.of() : YamlTree.immutableMap(configuration);
    }

    static String requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("internalName is required");
        return value.trim();
    }
}
