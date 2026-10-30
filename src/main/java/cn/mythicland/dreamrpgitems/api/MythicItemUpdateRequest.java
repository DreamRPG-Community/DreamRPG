package cn.mythicland.dreamrpgitems.api;

import cn.mythicland.dreamlib.storage.YamlTree;

import java.util.Map;

/**
 * Request to replace one MythicMobs item library configuration.
 */
public record MythicItemUpdateRequest(
        String internalName,
        String newInternalName,
        Map<String, Object> configuration,
        String expectedRevision,
        boolean confirmExternalMutation
) {
    /**
     * Compatibility constructor for callers compiled against the initial API shape.
     */
    @SuppressWarnings("unused")
    public MythicItemUpdateRequest(
            String internalName,
            Map<String, Object> configuration,
            String expectedRevision
    ) {
        this(internalName, internalName, configuration, expectedRevision, false);
    }

    public MythicItemUpdateRequest {
        internalName = MythicItemCreateRequest.requireName(internalName);
        newInternalName = MythicItemCreateRequest.requireName(
                newInternalName == null || newInternalName.isBlank() ? internalName : newInternalName
        );
        configuration = configuration == null ? Map.of() : YamlTree.immutableMap(configuration);
        expectedRevision = expectedRevision == null ? "" : expectedRevision.trim();
    }
}
