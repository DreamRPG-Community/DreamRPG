package cn.mythicland.dreamrpg.event;

import cn.mythicland.dreamrpg.api.ExperienceSnapshot;
import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Published after a player's profile and storage have been applied on the main thread.
 */
public final class PlayerDataReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uniqueId;
    private final PlayerStorageSnapshot storage;
    private final ExperienceSnapshot experience;

    /**
     * Creates a ready event.
     *
     * @param uniqueId   player UUID
     * @param storage    applied immutable storage snapshot
     * @param experience applied immutable experience snapshot
     */
    public PlayerDataReadyEvent(
            UUID uniqueId,
            PlayerStorageSnapshot storage,
            ExperienceSnapshot experience
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.experience = Objects.requireNonNull(experience, "experience");
        if (!experience.uniqueId().equals(uniqueId)) {
            throw new IllegalArgumentException("Experience snapshot belongs to another player");
        }
    }

    /**
     * Creates a legacy ready event without an experience payload.
     *
     * @param uniqueId player UUID
     * @param storage  applied immutable storage snapshot
     * @deprecated use the constructor that includes {@link ExperienceSnapshot}
     */
    @Deprecated
    public PlayerDataReadyEvent(UUID uniqueId, PlayerStorageSnapshot storage) {
        this(uniqueId, storage, ExperienceSnapshot.notReady(uniqueId));
    }

    /**
     * Returns this event's Bukkit handler list.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Returns the player UUID.
     *
     * @return player UUID
     */
    public UUID uniqueId() {
        return uniqueId;
    }

    /**
     * Returns the applied storage snapshot.
     *
     * @return storage snapshot
     */
    public PlayerStorageSnapshot storage() {
        return storage;
    }

    /**
     * Returns the applied experience snapshot.
     *
     * @return experience snapshot
     */
    public ExperienceSnapshot experience() {
        return experience;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
