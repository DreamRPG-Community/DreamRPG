package cn.mythicland.dreamrpg.api;

import java.util.Objects;

/**
 * Immutable presentation snapshot for chat, TAB, and name tags.
 *
 * @param prefix    full career prefix including its configured spacing
 * @param nameColor color applied to the player name
 * @param playerName colored player name
 * @param tabName   complete TAB/name-tag presentation
 */
public record PlayerPresentation(
        String prefix,
        String nameColor,
        String playerName,
        String tabName
) {

    /** Eight spaces reserved after the player name in the TAB display name. */
    public static final String TAB_NAME_SUFFIX = "        ";

    /**
     * Validates presentation values.
     */
    public PlayerPresentation {
        prefix = Objects.requireNonNull(prefix, "prefix");
        nameColor = Objects.requireNonNull(nameColor, "nameColor");
        playerName = Objects.requireNonNull(playerName, "playerName");
        tabName = Objects.requireNonNull(tabName, "tabName");
    }
}
