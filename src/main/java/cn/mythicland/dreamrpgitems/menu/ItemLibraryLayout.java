package cn.mythicland.dreamrpgitems.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable configured item-library order and its expandable folders.
 */
public record ItemLibraryLayout(List<Entry> entries) {

    public ItemLibraryLayout {
        Objects.requireNonNull(entries, "entries");
        Set<String> ids = new HashSet<>();
        Set<String> items = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate library entry: " + entry.id());
            for (String item : entry.items()) {
                if (!items.add(item)) throw new IllegalArgumentException("Duplicate library item: " + item);
            }
        }
        entries = entries.stream()
                .sorted(Comparator.comparingInt(Entry::order).thenComparing(Entry::id))
                .toList();
    }

    public List<Row> rows(Set<String> expanded, Predicate<String> available) {
        Objects.requireNonNull(expanded, "expanded");
        Objects.requireNonNull(available, "available");
        List<Row> result = new ArrayList<>();
        for (Entry entry : entries) {
            List<String> items = entry.items().stream().filter(available).toList();
            if (items.isEmpty()) continue;
            if (!entry.folder()) {
                result.add(new Row(items.getFirst(), null, 1));
            } else if (expanded.contains(entry.id())) {
                for (String item : items) result.add(new Row(item, entry.id(), 1));
            } else {
                result.add(new Row(items.getFirst(), entry.id(), items.size()));
            }
        }
        return List.copyOf(result);
    }

    public record Entry(String id, int order, boolean folder, List<String> items) {
        public Entry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Library entry ID is required");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (items.isEmpty() || items.size() > 64 || (!folder && items.size() != 1)) {
                throw new IllegalArgumentException("Invalid library item count for " + id);
            }
            if (items.stream().anyMatch(item -> item == null || item.isBlank())) {
                throw new IllegalArgumentException("Blank item ID in " + id);
            }
        }
    }

    public record Row(String itemId, String folderId, int iconAmount) {
    }
}
