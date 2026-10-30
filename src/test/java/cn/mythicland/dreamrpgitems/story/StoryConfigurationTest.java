package cn.mythicland.dreamrpgitems.story;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StoryConfigurationTest {
    @Test
    void defaultsToNoPages() {
        StoryConfiguration story = StoryConfiguration.from(Map.of());
        assertTrue(story.pages().isEmpty());
    }

    @Test
    void readsStoryPagesEvenWhenLegacySwitchIsDisabled() {
        StoryConfiguration story = StoryConfiguration.from(Map.of(
                "Story", Map.of("enabled", false, "pages", List.of("&6第一章\n第二行", "第二页"))
        ));
        assertEquals(List.of("&6第一章\n第二行", "第二页"), story.pages());
        assertThrows(UnsupportedOperationException.class, () -> story.pages().add("x"));
    }

    @Test
    void rejectsInvalidTypesAndOversizedPages() {
        assertThrows(IllegalArgumentException.class, () -> StoryConfiguration.from(Map.of("Story", "wrong")));
        assertThrows(IllegalArgumentException.class, () -> StoryConfiguration.from(Map.of("Story", Map.of("pages", List.of(1)))));
        assertThrows(IllegalArgumentException.class, () -> StoryConfiguration.from(Map.of("Story", Map.of("pages", List.of("x".repeat(257))))));
        assertThrows(IllegalArgumentException.class, () -> new StoryConfiguration(java.util.Collections.nCopies(51, "x")));
    }
}
