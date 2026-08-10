package cn.mythicland.dreamrpg.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable request to add experience to one RPG player.
 *
 * @param uniqueId   player UUID
 * @param baseAmount source amount before multipliers
 * @param source     source namespace, for example {@code mob} or {@code bottle}
 * @param sourceId   source identifier within the namespace
 * @param metadata   optional immutable source metadata
 */
public record ExperienceGrantRequest(
        UUID uniqueId,
        long baseAmount,
        String source,
        String sourceId,
        Map<String, String> metadata
) {

    /**
     * Validates and freezes a grant request.
     */
    public ExperienceGrantRequest {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (baseAmount < 0L) throw new IllegalArgumentException("baseAmount cannot be negative");
        source = requireText(source, "source");
        sourceId = requireText(sourceId, "sourceId");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    /**
     * Creates a request without metadata.
     *
     * @param uniqueId   player UUID
     * @param baseAmount source amount before multipliers
     * @param source     source namespace
     * @param sourceId   source identifier
     */
    public ExperienceGrantRequest(UUID uniqueId, long baseAmount, String source, String sourceId) {
        this(uniqueId, baseAmount, source, sourceId, Map.of());
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }
}
