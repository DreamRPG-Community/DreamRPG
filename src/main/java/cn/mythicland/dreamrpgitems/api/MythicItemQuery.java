package cn.mythicland.dreamrpgitems.api;

/**
 * Immutable query for the MythicMobs item catalog.
 *
 * @param searchText case-insensitive text matched against internal name, display name, and
 *                   material
 * @param source     source filter
 * @param status     status filter, or null for all statuses
 * @param page       zero-based page
 * @param pageSize   page size
 */
public record MythicItemQuery(
        String searchText,
        MythicItemSource source,
        MythicItemStatus status,
        int page,
        int pageSize
) {

    public MythicItemQuery {
        searchText = searchText == null ? "" : searchText.trim();
        source = source == null ? MythicItemSource.ALL : source;
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("pageSize must be between 1 and 200");
    }

    /**
     * Creates a first-page query with defaults.
     *
     * @return default query
     */
    public static MythicItemQuery defaults() {
        return new MythicItemQuery(
                "",
                MythicItemSource.ALL,
                null,
                0,
                50
        );
    }
}
