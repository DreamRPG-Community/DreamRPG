package cn.mythicland.dreamrpg.compat;

import cn.mythicland.lib.bootstrap.LibPluginLifecycle;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.LifecycleComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Disables ViaRewind's legacy-client attack cooldown visualization without linking DreamRPG to
 * ViaVersion classes at compile time.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class ViaRewindCooldownDisabler implements LibPluginLifecycle, Listener {

    private static final String VIA_REWIND_PLUGIN = "ViaRewind";
    private static final String VIA_REWIND_CONFIG = "com.viaversion.viarewind.ViaRewind";
    private static final String COOLDOWN_STORAGE_TYPE =
            "com.viaversion.viarewind.protocol.v1_9to1_8.storage.CooldownStorage";
    private static final String COOLDOWN_VISUALIZATION_TYPE =
            "com.viaversion.viarewind.protocol.v1_9to1_8.cooldown.CooldownVisualization$Factory";

    private final PluginTaskScope tasks;
    private boolean active;

    /**
     * Creates the optional ViaRewind compatibility lifecycle.
     *
     * @param tasks plugin-owned scheduler scope
     */
    public ViaRewindCooldownDisabler(PluginTaskScope tasks) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    private static Object enumValue(Class<?> enumType, String name) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class) enumType, name);
        return value;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Applies the override once immediately and once after the first server tick, covering both
     * normal plugin startup and Via's delayed initialization path.
     */
    @Override
    public void enable() {
        active = true;
        disableIndicator();
        tasks.runLater(1L, this::disableIndicator);
    }

    /**
     * Reapplies the runtime override after a DreamRPG reload.
     */
    @Override
    public void reload() {
        if (active) disableIndicator();
    }

    /**
     * Reapplies the runtime override after Via has created a new player connection.
     *
     * @param event Bukkit join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.runLater(1L, () -> {
            if (player.isOnline()) disableIndicator();
        });
    }

    /**
     * No Via resources are owned by DreamRPG.
     */
    @Override
    public void disable() {
        active = false;
    }

    private void disableIndicator() {
        if (!active) return;
        Plugin viaRewind = Bukkit.getPluginManager().getPlugin(VIA_REWIND_PLUGIN);
        if (viaRewind == null || !viaRewind.isEnabled()) return;

        try {
            ClassLoader loader = viaRewind.getClass().getClassLoader();
            Class<?> viaRewindClass = load(loader, VIA_REWIND_CONFIG);
            Object config = viaRewindClass.getMethod("getConfig").invoke(null);
            if (config == null) return;

            Object disabled = enumValue(
                    load(loader, "com.viaversion.viarewind.api.ViaRewindConfig$CooldownIndicator"),
                    "DISABLED"
            );
            Field cooldownIndicator = findField(config.getClass(), "cooldownIndicator");
            if (cooldownIndicator != null && cooldownIndicator.trySetAccessible()) {
                cooldownIndicator.set(config, disabled);
            }

            Class<?> viaClass = load(loader, "com.viaversion.viaversion.api.Via");
            Object manager = viaClass.getMethod("getManager").invoke(null);
            Object connectionManager = manager.getClass().getMethod("getConnectionManager").invoke(manager);
            Collection<?> connections = (Collection<?>) connectionManager.getClass()
                    .getMethod("getConnections")
                    .invoke(connectionManager);

            Class<?> userConnectionType = load(loader, "com.viaversion.viaversion.api.connection.UserConnection");
            Class<?> cooldownStorageType = load(loader, COOLDOWN_STORAGE_TYPE);
            Class<?> factoryType = load(loader, COOLDOWN_VISUALIZATION_TYPE);
            Object disabledFactory = factoryType.getField("DISABLED").get(null);
            Method getStorage = userConnectionType.getMethod("get", Class.class);
            Method setFactory = cooldownStorageType.getMethod("setVisualizationFactory", factoryType);
            Method resetHit = cooldownStorageType.getMethod("setLastHit", long.class);
            Method tick = cooldownStorageType.getMethod("tick", userConnectionType);

            for (Object connection : connections) {
                Object storage = getStorage.invoke(connection, cooldownStorageType);
                if (storage == null) continue;
                setFactory.invoke(storage, disabledFactory);
                resetHit.invoke(storage, 0L);
                tick.invoke(storage, connection);
            }
        } catch (ReflectiveOperationException
                 | ClassCastException
                 | IllegalArgumentException
                 | SecurityException
                 | LinkageError exception) {
            Bukkit.getLogger().log(
                    Level.WARNING,
                    "DreamRPG could not disable ViaRewind's attack cooldown title: "
                            + exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }
}
