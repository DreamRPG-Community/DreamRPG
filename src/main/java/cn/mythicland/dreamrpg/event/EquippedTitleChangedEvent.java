package cn.mythicland.dreamrpg.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the primary thread when an online player's effective title changes, including expiry.
 */
public final class EquippedTitleChangedEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * @param player player whose effective title is now available from TitleApi
     */
    public EquippedTitleChangedEvent(Player player) {
        super(player);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return Bukkit handler registry
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
