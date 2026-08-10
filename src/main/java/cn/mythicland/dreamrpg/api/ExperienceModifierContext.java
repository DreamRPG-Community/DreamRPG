package cn.mythicland.dreamrpg.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-only context supplied to an experience modifier.
 *
 * @param uniqueId player UUID
 * @param source   grant source namespace
 * @param sourceId grant source identifier
 * @param level    current RPG level before the grant
 * @param metadata immutable grant metadata
 */
public record ExperienceModifierContext(
        UUID uniqueId,
        String source,
        String sourceId,
        long level,
        Map<String, String> metadata
) {

    /**
     * Validates and freezes the modifier context.
     */
    public ExperienceModifierContext {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        source = requireText(source, "source");
        sourceId = requireText(sourceId, "sourceId");
        if (level < 0L) throw new IllegalArgumentException("level cannot be negative");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
