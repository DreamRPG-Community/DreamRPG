package cn.mythicland.dreamrpg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies standalone scoreboard parsing and native integration detection.
 */
class ScoreboardSettingsTest {

    @Test
    void animationAndNativePlaceholdersBecomeImmutableSettings() {
        ScoreboardSettings settings = ScoreboardSettings.load(configuration());

        assertEquals(2, settings.normal().titleAnimation().frames().size());
        assertEquals(40, settings.normal().titleAnimation().frames().get(0).holdTicks());
        assertEquals(2, settings.normal().titleAnimation().frames().get(1).holdTicks());
        assertEquals(ZoneId.of("Asia/Shanghai"), settings.normal().timeZone());
        assertEquals(1L, settings.normal().titleUpdateTicks());
        assertEquals("&c加载中", settings.loading().titleAnimation().frameAt(0).text());
        assertTrue(settings.containsNativePlaceholder("coins"));
        assertTrue(settings.containsNativePlaceholder("location"));
        assertTrue(settings.containsPlaceholderApiToken());
        assertThrows(
                UnsupportedOperationException.class,
                () -> settings.normal().lines().add("mutated")
        );
    }

    @Test
    void missingNormalSectionFailsExplicitly() {
        YamlConfiguration configuration = configuration();
        configuration.set("normal", null);

        assertThrows(IllegalStateException.class, () -> ScoreboardSettings.load(configuration));
    }

    @Test
    void legacyTopLevelScoreboardValuesAreRejectedWithoutMigration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("enabled", true);
        configuration.set("title", "&a旧标题");
        configuration.set("lines", List.of("旧配置"));
        configuration.set("update-ticks", 20L);

        assertThrows(IllegalStateException.class, () -> ScoreboardSettings.load(configuration));
        assertFalse(configuration.contains("normal"));
        assertTrue(configuration.contains("title"));
    }

    private static YamlConfiguration configuration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("normal.enabled", true);
        configuration.set(
                "normal.title-animation",
                List.of("&2DreamRPG [x40]", "&6DreamRPG [x2]")
        );
        configuration.set("normal.title-update-ticks", 1L);
        configuration.set("normal.update-ticks", 20L);
        configuration.set("normal.time-zone", "Asia/Shanghai");
        configuration.set(
                "normal.lines",
                List.of("{time}", "{coins}", "{location}", "%player_points_points%")
        );
        configuration.set("loading.title-animation", List.of("&c加载中 [x1]"));
        configuration.set("loading.lines", List.of("&7请稍候"));
        return configuration;
    }
}
