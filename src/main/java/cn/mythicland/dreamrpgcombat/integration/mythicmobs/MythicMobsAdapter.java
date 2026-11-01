package cn.mythicland.dreamrpgcombat.integration.mythicmobs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Optional MythicMobs bridge. Reflection keeps DreamRPG combat loadable when MythicMobs is absent.
 */
public final class MythicMobsAdapter {

    private final Object apiHelper;
    private final Method isMythicMobMethod;
    private final Method getMythicMobInstanceMethod;

    private MythicMobsAdapter(
            Object apiHelper,
            Method isMythicMobMethod,
            Method getMythicMobInstanceMethod
    ) {
        this.apiHelper = apiHelper;
        this.isMythicMobMethod = isMythicMobMethod;
        this.getMythicMobInstanceMethod = getMythicMobInstanceMethod;
    }

    /**
     * Detects the legacy MythicMobs Bukkit API used by the server's 4.x plugin.
     *
     * @return a working adapter or a disabled adapter when MythicMobs is unavailable
     */
    public static MythicMobsAdapter detect() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (plugin == null || !plugin.isEnabled()) return disabled();

            Method getApiHelper = plugin.getClass().getMethod("getAPIHelper");
            Object apiHelper = getApiHelper.invoke(plugin);
            if (apiHelper == null) return disabled();

            Method isMythicMob = apiHelper.getClass().getMethod("isMythicMob", Entity.class);
            Method getMythicMobInstance = apiHelper.getClass().getMethod(
                    "getMythicMobInstance",
                    Entity.class
            );
            return new MythicMobsAdapter(apiHelper, isMythicMob, getMythicMobInstance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return disabled();
        }
    }

    private static MythicMobsAdapter disabled() {
        return new MythicMobsAdapter(null, null, null);
    }

    /**
     * Indicates whether the optional API was resolved successfully.
     *
     * @return true when MythicMobs integration is active
     */
    public boolean isAvailable() {
        return apiHelper != null;
    }

    /**
     * Returns whether the live MythicMobs registry owns this entity, on the server thread.
     */
    public boolean isMythicMob(Entity entity) {
        if (!isAvailable()) return false;
        try {
            return Boolean.TRUE.equals(isMythicMobMethod.invoke(apiHelper, entity));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot query the MythicMobs entity registry", exception);
        }
    }

    /**
     * Resolves a MythicMobs display name without making MythicMobs a compile-time dependency.
     *
     * @param target target entity
     * @return configured MythicMob display name when the entity is a MythicMob
     */
    public Optional<String> displayName(LivingEntity target) {
        if (!isAvailable() || target == null) return Optional.empty();
        try {
            Object result = isMythicMobMethod.invoke(apiHelper, target);
            if (!(result instanceof Boolean mythicMob) || !mythicMob) return Optional.empty();

            Object activeMob = getMythicMobInstanceMethod.invoke(apiHelper, target);
            if (activeMob == null) return Optional.empty();
            Object mythicType = activeMob.getClass().getMethod("getType").invoke(activeMob);
            if (mythicType == null) return Optional.empty();
            Object displayName = mythicType.getClass().getMethod("getDisplayName").invoke(mythicType);
            if (!(displayName instanceof String name) || name.isBlank()) return Optional.empty();
            return Optional.of(name);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Returns MM's configured, level-scaled attack, excluding weapon material modifiers.
     */
    public OptionalDouble attackDamage(LivingEntity source) {
        if (!isAvailable()) return OptionalDouble.empty();
        try {
            if (!Boolean.TRUE.equals(isMythicMobMethod.invoke(apiHelper, source))) return OptionalDouble.empty();
            Object active = getMythicMobInstanceMethod.invoke(apiHelper, source);
            double amount = ((Number) active.getClass().getMethod("getDamage").invoke(active)).doubleValue();
            if (!Double.isFinite(amount) || amount < 0) {
                throw new IllegalStateException("MythicMob attack damage must be finite and non-negative");
            }
            return OptionalDouble.of(amount);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read configured MythicMob attack damage", exception);
        }
    }
}
