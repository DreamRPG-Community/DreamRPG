package cn.mythicland.dreamrpgcombat.config;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable DreamRPG combat configuration used by the runtime modules.
 */
public final class CombatSettings {

    private final boolean feedbackEnabled;
    private final double defenseConstant;
    private final Map<CombatStat, String> labels;
    private final String levelRequirementLabel;
    private final CombatMessages messages;

    /**
     * Creates settings with the default triggered-effect messages.
     *
     * @param feedbackEnabled whether triggered-effect messages are enabled
     * @param labels          configured visible labels without color codes
     */
    public CombatSettings(boolean feedbackEnabled, Map<CombatStat, String> labels) {
        this(feedbackEnabled, labels, "需要等级", CombatMessages.defaults());
    }

    CombatSettings(
            boolean feedbackEnabled,
            Map<CombatStat, String> labels,
            String levelRequirementLabel,
            CombatMessages messages
    ) {
        this(feedbackEnabled, labels, levelRequirementLabel, messages, 600.0D);
    }

    CombatSettings(
            boolean feedbackEnabled,
            Map<CombatStat, String> labels,
            String levelRequirementLabel,
            CombatMessages messages,
            double defenseConstant
    ) {
        if (!Double.isFinite(defenseConstant) || defenseConstant <= 0) {
            throw new IllegalArgumentException("defenseConstant must be finite and positive");
        }
        this.defenseConstant = defenseConstant;
        this.feedbackEnabled = feedbackEnabled;
        Objects.requireNonNull(labels, "labels");
        EnumMap<CombatStat, String> copy = new EnumMap<>(CombatStat.class);
        copy.putAll(labels);
        for (CombatStat stat : CombatStat.values()) copy.putIfAbsent(stat, stat.defaultDisplayName());
        this.labels = Collections.unmodifiableMap(copy);
        this.levelRequirementLabel = Objects.requireNonNull(levelRequirementLabel, "levelRequirementLabel");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Returns the positive scale used by typed defense reduction.
     *
     * @return the positive defense scale
     */
    public double defenseConstant() {
        return defenseConstant;
    }

    /**
     * Returns whether triggered-effect messages are enabled.
     */
    public boolean feedbackEnabled() {
        return feedbackEnabled;
    }

    /**
     * Returns the configured visible label.
     *
     * @param stat the requested stat
     * @return the visible configured label
     */
    public String label(CombatStat stat) {
        return labels.get(Objects.requireNonNull(stat, "stat"));
    }

    /**
     * Returns the visible configured label.
     *
     * @param stat the requested stat
     * @return the visible label
     */
    public String labelText(CombatStat stat) {
        return label(stat);
    }

    /**
     * Returns the configured item level-requirement label.
     *
     * @return visible level-requirement label without color codes
     */
    public String levelRequirementLabel() {
        return levelRequirementLabel;
    }

    /**
     * Renders the configured critical-hit feedback message.
     *
     * @param damage the final critical-hit damage
     * @return a colorized message
     */
    public String criticalHitMessage(double damage) {
        return messages.criticalHit(damage);
    }

    /**
     * Renders the configured health-regeneration feedback message.
     *
     * @param amount the restored health amount
     * @return a colorized message
     */
    public String healthRegenMessage(double amount) {
        return messages.healthRegen(amount);
    }

}
