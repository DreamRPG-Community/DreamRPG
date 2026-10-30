package cn.mythicland.dreamrpg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies strict configuration parsing and plugin-local path containment.
 */
class DreamRPGSettingsTest {

    @Test
    void configuredSpawnAndDatabaseValuesBecomeImmutableSettings() {
        DreamRPGSettings settings = DreamRPGSettings.load(configuration());

        assertEquals(URI.create("https://repo1.maven.org/maven2/"), settings.libraryRepository());
        assertEquals(DreamRPGSettings.DatabaseMode.SQLITE, settings.database().mode());
        assertEquals("profiles.db", settings.database().sqlite().fileName());
        assertEquals(List.of(), settings.display().tab().header());
        assertEquals(List.of(), settings.display().tab().footer());
        assertEquals(
                "{prefix}{name}        ",
                settings.display().tab().playerNameFormat()
        );
        assertEquals("{prefix}{name}", settings.display().nameTagFormat());
        assertEquals(
                Path.of("C:/plugins/DreamRPG/profiles.db").toAbsolutePath().normalize(),
                settings.databasePath(Path.of("C:/plugins/DreamRPG"))
        );
    }

    @Test
    void databaseFileCannotEscapeThePluginDataDirectory() {
        YamlConfiguration configuration = configuration();
        configuration.set("database.sqlite.file", "../outside.db");

        assertThrows(
                IllegalArgumentException.class,
                () -> DreamRPGSettings.load(configuration)
        );
    }

    @Test
    void mysqlSettingsBuildAConnectorJUrlWithoutExposingCredentials() {
        DreamRPGSettings.MySqlSettings settings = new DreamRPGSettings.MySqlSettings(
                "db.example.test",
                3306,
                "dreamrpg",
                "rpg_user",
                "secret-value",
                false,
                "Asia/Shanghai"
        );

        assertEquals(
                "jdbc:mysql://db.example.test:3306/dreamrpg"
                        + "?useUnicode=true&characterEncoding=utf8"
                        + "&useSSL=false&serverTimezone=Asia/Shanghai",
                settings.jdbcUrl()
        );
    }

    @Test
    void databaseModeSelectsTheIndependentMysqlConfigurationSection() {
        YamlConfiguration configuration = configuration();
        configuration.set("database.mode", "mysql");
        configuration.set("database.mysql.host", "mysql.example.test");
        configuration.set("database.mysql.database", "dreamrpg_live");

        DreamRPGSettings settings = DreamRPGSettings.load(configuration);

        assertEquals(DreamRPGSettings.DatabaseMode.MYSQL, settings.database().mode());
        assertEquals("mysql.example.test", settings.database().mysql().host());
        assertEquals("dreamrpg_live", settings.database().mysql().database());
        assertEquals("profiles.db", settings.database().sqlite().fileName());
    }

    @Test
    void displayTemplatesPreserveConfiguredLinesAndTrailingSpaces() {
        YamlConfiguration configuration = configuration();
        configuration.set("display.tab.header", List.of("&aWelcome", "&f{name}"));
        configuration.set("display.tab.footer", List.of("&7{world}"));
        configuration.set("display.tab.player-name-format", "{prefix}{name} &8[{career_id}]");
        configuration.set("display.name-tag-format", "&e{prefix}{name}&7 [{health}]");

        DreamRPGSettings settings = DreamRPGSettings.load(configuration);

        assertEquals(List.of("&aWelcome", "&f{name}"), settings.display().tab().header());
        assertEquals(List.of("&7{world}"), settings.display().tab().footer());
        assertEquals(
                "{prefix}{name} &8[{career_id}]",
                settings.display().tab().playerNameFormat()
        );
        assertEquals("&e{prefix}{name}&7 [{health}]", settings.display().nameTagFormat());
    }

    @Test
    void nameTagFormatRequiresExactlyOneNamePlaceholder() {
        YamlConfiguration configuration = configuration();
        configuration.set("display.name-tag-format", "&7无名标签");

        assertThrows(IllegalArgumentException.class, () -> DreamRPGSettings.load(configuration));

        configuration.set("display.name-tag-format", "{name} {name}");

        assertThrows(IllegalArgumentException.class, () -> DreamRPGSettings.load(configuration));
    }

    @Test
    void chatAndDisplayTemplatesDetectPlaceholderApiTokens() {
        YamlConfiguration configuration = configuration();
        configuration.set("chat.format", "%luckperms_prefix%{prefix}{name}: {message}");
        configuration.set("display.tab.header", List.of("%player_name%"));
        configuration.set("display.tab.player-name-format", "%vault_eco_balance% {name}");
        configuration.set("display.name-tag-format", "{name} &7[%player_ping%]");

        DreamRPGSettings settings = DreamRPGSettings.load(configuration);

        assertTrue(settings.containsPlaceholderApiToken());
    }

    @Test
    void legacyStorageSettingIsNotConvertedIntoTheNewDatabaseMode() {
        YamlConfiguration configuration = configuration();
        configuration.set("database.mode", null);
        configuration.set("database.storage", "mysql");

        assertThrows(
                IllegalStateException.class,
                () -> DreamRPGSettings.load(configuration)
        );
    }

    private static YamlConfiguration configuration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("libraries.repository", "https://repo.example.test/maven/");
        configuration.set("database.mode", "sqlite");
        configuration.set("database.sqlite.file", "profiles.db");
        configuration.set("spawn.world", "world");
        configuration.set("spawn.x", 0.5D);
        configuration.set("spawn.y", 7.0D);
        configuration.set("spawn.z", 2.5D);
        configuration.set("spawn.yaw", 179.2716D);
        configuration.set("spawn.pitch", 5.3491D);
        configuration.set("spawn.teleport-on-join", true);
        configuration.set("spawn.teleport-on-respawn", true);
        configuration.set("chat.format", "{prefix}{name}&7: &f{message}");
        configuration.set("chat.color-permission", "dreamrpg.chat.color");
        return configuration;
    }
}
