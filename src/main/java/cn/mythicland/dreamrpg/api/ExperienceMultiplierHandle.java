package cn.mythicland.dreamrpg.api;

/**
 * Handle returned when an external experience modifier is registered.
 */
@FunctionalInterface
public interface ExperienceMultiplierHandle extends Registration {

    /**
     * Unregisters the associated modifier. Repeated calls are harmless.
     */
    @Override
    void close();
}
