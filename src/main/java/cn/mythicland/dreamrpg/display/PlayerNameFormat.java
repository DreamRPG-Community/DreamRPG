package cn.mythicland.dreamrpg.display;

/**
 * Keeps RPG titles and careers before the name and memberships after it, preserving LP rank text.
 */
public final class PlayerNameFormat {
    private PlayerNameFormat() {
    }

    /**
     * Normalizes name placeholders without changing surrounding chat or TAB content.
     *
     * @param template            configured presentation template
     * @param hasTitle            whether a title is equipped
     * @param hasPermissionPrefix whether an LP staff prefix is available
     * @return template with the required name-part order
     */
    public static String ordered(String template, boolean hasTitle, boolean hasPermissionPrefix) {
        String format = template.replace("%luckperms_prefix%", "{lp_prefix}")
                .replaceAll(" ?\\{member_prefix}", "")
                .replaceAll("\\{(?:title|prefix|lp_prefix)} ?", "");
        String name = "{prefix} {name} {member_prefix}";
        if (hasTitle) name = "{title} " + name;
        if (hasPermissionPrefix) name = "{lp_prefix} " + name;
        return format.stripLeading().replace("{name}", name);
    }
}
