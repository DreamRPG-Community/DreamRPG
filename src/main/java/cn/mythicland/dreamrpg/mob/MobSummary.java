package cn.mythicland.dreamrpg.mob;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Immutable summary of one loaded MythicMobs mob.
 *
 * @param internalName MythicMobs internal ID
 * @param displayName  display name, falling back to the internal ID when missing
 * @param entityType loaded MM entity type
 * @param sourceFile content-relative source file
 */
public record MobSummary(
        String internalName,
        String displayName,
        String entityType,
        String sourceFile
) {

    public MobSummary {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(sourceFile, "sourceFile");
    }

    public String folder() {
        int separator = sourceFile.lastIndexOf('/');
        return separator < 0 ? "mobs" : sourceFile.substring(0, separator);
    }

    public String folderName() {
        var parts = new ArrayList<>(Arrays.asList(folder().split("/")));
        if (parts.getFirst().equals("dungeons")) parts.removeFirst();
        parts.remove("mobs");
        return parts.isEmpty() ? "mobs" : String.join("/", parts);
    }
}
