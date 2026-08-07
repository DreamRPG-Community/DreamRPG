package cn.mythicland.dreamrpg.event;

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

    /**
     * Creates a ready event.
     *
     * @param uniqueId player UUID
     * @param storage  applied immutable storage snapshot
     */
    public PlayerDataReadyEvent(UUID uniqueId, PlayerStorageSnapshot storage) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.storage = Objects.requireNonNull(storage, "storage");
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns this event's Bukkit handler list.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
