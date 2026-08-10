package cn.mythicland.dreamrpg.event;

import cn.mythicland.dreamrpg.api.PlayerProfile;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/**
 * Bukkit event published after a player's persisted career changes.
 */
public final class CareerChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlayerProfile previousProfile;
    private final PlayerProfile currentProfile;

    /**
     * Creates a career change event.
     *
     * @param previousProfile profile before the change
     * @param currentProfile  profile after the change
     */
    public CareerChangedEvent(PlayerProfile previousProfile, PlayerProfile currentProfile) {
        this.previousProfile = Objects.requireNonNull(previousProfile, "previousProfile");
        this.currentProfile = Objects.requireNonNull(currentProfile, "currentProfile");
        if (!previousProfile.uniqueId().equals(currentProfile.uniqueId())) {
            throw new IllegalArgumentException("Career change profiles must belong to the same player");
        }
        if (previousProfile.careerId().equals(currentProfile.careerId())) {
            throw new IllegalArgumentException("Career change must change the career identifier");
        }
    }

    /**
     * Returns the static Bukkit handler list.
     *
     * @return event handlers
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Returns the profile before the change.
     *
     * @return previous profile
     */
    public PlayerProfile previousProfile() {
        return previousProfile;
    }

    /**
     * Returns the profile after the change.
     *
     * @return current profile
     */
    public PlayerProfile currentProfile() {
        return currentProfile;
    }

    /**
     * Returns the Bukkit handler list.
     *
     * @return event handlers
     */
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
