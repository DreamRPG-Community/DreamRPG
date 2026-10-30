package cn.mythicland.dreamrpgitems.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validated optional story pages attached to an MM item definition.
 */
public record StoryConfiguration(List<String> pages) {
    public StoryConfiguration {
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        if (pages.size() > 50) {
            throw new IllegalArgumentException("Story exceeds 50 pages");
        }
        for (String page : pages) {
            if (page.length() > 256) {
                throw new IllegalArgumentException("Story page exceeds 256 characters");
            }
        }
    }

    public static StoryConfiguration from(Map<String, Object> definition) {
        Object raw = Objects.requireNonNull(definition, "definition").get("Story");
        if (raw == null) {
            return new StoryConfiguration(List.of());
        }
        if (!(raw instanceof Map<?, ?> section)) {
            throw new IllegalArgumentException("Story must be a mapping");
        }
        Object pagesValue = section.get("pages");
        if (pagesValue == null) {
            return new StoryConfiguration(List.of());
        }
        if (!(pagesValue instanceof List<?> values)) {
            throw new IllegalArgumentException("Story.pages must be a list");
        }
        List<String> pages = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Story page must be text");
            }
            pages.add(text);
        }
        return new StoryConfiguration(pages);
    }
}
