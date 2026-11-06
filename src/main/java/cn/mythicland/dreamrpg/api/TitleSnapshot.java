package cn.mythicland.dreamrpg.api;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable player title ownership, safe to read on any thread.
 *
 * @param expirations title IDs mapped to epoch milliseconds, with zero meaning permanent
 * @param selectedId  selected title ID, or an empty string when unequipped
 */
public record TitleSnapshot(Map<String, Long> expirations, String selectedId) {
    public TitleSnapshot {
        expirations = Map.copyOf(expirations);
        Objects.requireNonNull(selectedId, "selectedId");
        expirations.forEach((id, expiry) -> {
            if (expiry < 0) throw new IllegalArgumentException("Negative title expiration: " + id);
        });
        if (!selectedId.isEmpty() && !expirations.containsKey(selectedId))
            throw new IllegalArgumentException("Selected title is not owned: " + selectedId);
    }

    /**
     * Checks ownership at the supplied time; the exact expiry instant is already expired.
     *
     * @param id  title ID
     * @param now epoch milliseconds
     * @return whether the grant is permanent or has not yet expired
     */
    public boolean owns(String id, long now) {
        Long expiry = expirations.get(id);
        return expiry != null && (expiry == 0 || expiry > now);
    }
}
