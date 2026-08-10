package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;

/**
 * External experience multiplier participant.
 */
public interface ExperienceModifier {

    /**
     * Returns a stable identifier used for diagnostics and deterministic ordering.
     *
     * @return modifier identifier
     */
    String id();

    /**
     * Returns the ordering priority. Lower values are evaluated first.
     *
     * @return priority
     */
    default int priority() {
        return 0;
    }

    /**
     * Returns this modifier's multiplicative factor.
     *
     * @param context current grant context
     * @return non-negative finite multiplier
     */
    BigDecimal multiplier(ExperienceModifierContext context);
}
