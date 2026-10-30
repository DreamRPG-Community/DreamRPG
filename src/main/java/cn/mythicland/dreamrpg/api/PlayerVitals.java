package cn.mythicland.dreamrpg.api;

/**
 * Persistent health and hunger values captured on the server thread.
 *
 * @param health     current health in Bukkit health units
 * @param foodLevel  current food level between zero and twenty
 * @param saturation current food saturation
 * @param exhaustion current food exhaustion
 */
public record PlayerVitals(double health, int foodLevel, float saturation, float exhaustion) {
    public PlayerVitals {
        if (!Double.isFinite(health) || health < 0) throw new IllegalArgumentException("Invalid health");
        if (foodLevel < 0 || foodLevel > 20) throw new IllegalArgumentException("Invalid food level");
        if (!Float.isFinite(saturation) || saturation < 0)
            throw new IllegalArgumentException("Invalid saturation");
        if (!Float.isFinite(exhaustion) || exhaustion < 0) throw new IllegalArgumentException("Invalid exhaustion");
    }
}
