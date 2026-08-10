package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Public DreamRPG experience service exposed through Bukkit's ServicesManager.
 */
public interface ExperienceApi {

    /**
     * Returns the cached experience snapshot for a player.
     *
     * @param uniqueId player UUID
     * @return immutable snapshot; it is marked not ready until player data is applied
     */
    ExperienceSnapshot snapshot(UUID uniqueId);

    /**
     * Returns whether the player's DreamRPG data has been applied and is ready for mutations.
     *
     * @param uniqueId player UUID
     * @return true when the player is ready
     */
    boolean isReady(UUID uniqueId);

    /**
     * Grants experience through the single DreamRPG calculation pipeline.
     *
     * <p>The call must run on Bukkit's primary thread. The base amount is an integer source
     * amount; registered modifiers may produce a fractional effective amount.</p>
     *
     * @param request grant request
     * @return immutable result
     */
    ExperienceGrantResult grant(ExperienceGrantRequest request);

    /**
     * Registers an external multiplier.
     *
     * @param modifier multiplier implementation
     * @return handle that unregisters the modifier when closed
     */
    Registration registerModifier(ExperienceModifier modifier);

    /**
     * Sets the server-wide timed multiplier.
     *
     * @param multiplier non-negative multiplier
     * @param expiresAt  absolute expiration instant
     */
    void setServerMultiplier(BigDecimal multiplier, Instant expiresAt);

    /**
     * Sets a player's timed multiplier.
     *
     * @param uniqueId   player UUID
     * @param multiplier non-negative multiplier
     * @param expiresAt  absolute expiration instant
     */
    void setPlayerMultiplier(UUID uniqueId, BigDecimal multiplier, Instant expiresAt);

    /**
     * Clears the server-wide timed multiplier.
     */
    void clearServerMultiplier();

    /**
     * Clears a player's timed multiplier.
     *
     * @param uniqueId player UUID
     */
    void clearPlayerMultiplier(UUID uniqueId);
}
