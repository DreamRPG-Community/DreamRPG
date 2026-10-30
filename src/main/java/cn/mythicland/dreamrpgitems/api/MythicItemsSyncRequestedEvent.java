package cn.mythicland.dreamrpgitems.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Requests a full refresh of managed item references on the Bukkit main thread.
 */
public final class MythicItemsSyncRequestedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return Bukkit's event handler registry
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
