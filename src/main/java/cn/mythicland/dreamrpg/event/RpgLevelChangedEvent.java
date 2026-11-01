package cn.mythicland.dreamrpg.event;

import cn.mythicland.framework.validation.TextValidation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Published after an administrative operation changes a player's RPG level.
 */
public final class RpgLevelChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uniqueId;
    private final long previousLevel;
    private final long currentLevel;
    private final String source;
    private final String sourceId;

    /**
     * Creates an RPG level-change event.
     *
     * @param uniqueId      player UUID
     * @param previousLevel previous RPG level
     * @param currentLevel  current RPG level
     * @param source        source namespace
     * @param sourceId      source identifier
     */
    public RpgLevelChangedEvent(
            UUID uniqueId,
            long previousLevel,
            long currentLevel,
            String source,
            String sourceId
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (previousLevel < 0L || currentLevel < 0L || previousLevel == currentLevel) {
            throw new IllegalArgumentException("Invalid RPG level change");
        }
        this.previousLevel = previousLevel;
        this.currentLevel = currentLevel;
        this.source = TextValidation.requireText(source, "source");
        this.sourceId = TextValidation.requireText(sourceId, "sourceId");
    }

    /**
     * Returns Bukkit's static handler list.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Returns the affected player UUID.
     *
     * @return player UUID
     */
    public UUID uniqueId() {
        return uniqueId;
    }

    /**
     * Returns the level before the change.
     *
     * @return previous level
     */
    public long previousLevel() {
        return previousLevel;
    }

    /**
     * Returns the level after the change.
     *
     * @return current level
     */
    public long currentLevel() {
        return currentLevel;
    }

    /**
     * Returns the source namespace.
     *
     * @return source namespace
     */
    public String source() {
        return source;
    }

    /**
     * Returns the source identifier.
     *
     * @return source identifier
     */
    public String sourceId() {
        return sourceId;
    }

    /**
     * Returns Bukkit's static handler list.
     *
     * @return handler list
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
