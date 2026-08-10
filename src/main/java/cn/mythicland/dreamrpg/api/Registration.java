package cn.mythicland.dreamrpg.api;

/**
 * Handle for unregistering a DreamRPG extension.
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    /**
     * Unregisters the extension. Repeated calls are harmless.
     */
    @Override
    void close();
}
