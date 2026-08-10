package cn.mythicland.dreamrpg.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Published after DreamRPG's RPG level changes.
 *
 * <p>This event is the authoritative level-change hook for RPG attributes. Bukkit's native
 * {@code PlayerLevelChangeEvent} is only a presentation-side compatibility event and must not
 * be used for RPG attribute calculations.</p>
 */
public final class RpgLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uniqueId;
    private final long previousLevel;
    private final long currentLevel;
    private final String source;
    private final String sourceId;

    /**
     * Creates one RPG level-up event.
     *
     * @param uniqueId      player UUID
     * @param previousLevel previous RPG level
     * @param currentLevel  current RPG level
     * @param source        source namespace
     * @param sourceId      source identifier
     */
    public RpgLevelUpEvent(
            UUID uniqueId,
            long previousLevel,
            long currentLevel,
            String source,
            String sourceId
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (previousLevel < 0L || currentLevel < previousLevel) {
            throw new IllegalArgumentException("Invalid RPG level transition");
        }
        this.previousLevel = previousLevel;
        this.currentLevel = currentLevel;
        this.source = requireText(source, "source");
        this.sourceId = requireText(sourceId, "sourceId");
    }

    /**
     * Returns Bukkit's handler list.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static String requireText(String value, String fieldName) {
        String text = Objects.requireNonNull(value, fieldName).trim();
        if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
        return text;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public long previousLevel() {
        return previousLevel;
    }

    public long currentLevel() {
        return currentLevel;
    }

    public String source() {
        return source;
    }

    public String sourceId() {
        return sourceId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
