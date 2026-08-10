package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.text.TemplateRenderer;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable DreamRPG settings loaded from config.yml.
 */
public record DreamRpgSettings(
        URI libraryRepository,
        DatabaseSettings database,
        SpawnSettings spawn,
        ChatSettings chat,
        DisplaySettings display
) {

    /**
     * Validates grouped settings.
     */
    @SuppressWarnings("DataFlowIssue")
    public DreamRpgSettings {
        libraryRepository = Objects.requireNonNull(libraryRepository, "libraryRepository");
        database = Objects.requireNonNull(database, "database");
        spawn = Objects.requireNonNull(spawn, "spawn");
        chat = Objects.requireNonNull(chat, "chat");
        display = Objects.requireNonNull(display, "display");
    }

    /**
     * Loads strict settings from config.yml.
     *
     * @param configuration configuration with defaults materialized
     * @return validated settings
     */
    public static DreamRpgSettings load(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (!configuration.contains("database.mode")) {
            throw new IllegalStateException("Configuration requires a non-empty string: database.mode");
        }
        return from(ConfigSupport.bind(configuration, RawSettings.class));
    }

    static DreamRpgSettings bind(ConfigView configuration) {
        return from(Objects.requireNonNull(configuration, "configuration").bind(RawSettings.class));
    }

    private static DreamRpgSettings from(RawSettings raw) {
        URI libraryRepository = URI.create(raw.libraryRepository());
        DatabaseSettings database = new DatabaseSettings(
                raw.databaseMode(),
                new SqliteSettings(raw.sqliteFile()),
                new MySqlSettings(
                        raw.mysqlHost(),
                        raw.mysqlPort(),
                        raw.mysqlDatabase(),
                        raw.mysqlUsername(),
                        raw.mysqlPassword(),
                        raw.mysqlUseSsl(),
                        raw.mysqlServerTimezone()
                )
        );
        SpawnSettings spawn = new SpawnSettings(
                raw.spawnWorld(),
                raw.spawnX(),
                raw.spawnY(),
                raw.spawnZ(),
                (float) raw.spawnYaw(),
                (float) raw.spawnPitch(),
                raw.teleportOnJoin(),
                raw.teleportOnRespawn()
        );
        ChatSettings chat = new ChatSettings(raw.chatFormat(), raw.colorPermission());
        DisplaySettings display = new DisplaySettings(
                new TabSettings(raw.tabHeader(), raw.tabFooter(), raw.playerNameFormat()),
                raw.nameTagFormat()
        );
        return new DreamRpgSettings(libraryRepository, database, spawn, chat, display);
    }

    private static String requireTemplate(String value, String path) {
        String template = Objects.requireNonNull(value, path);
        if (template.isBlank()) throw new IllegalArgumentException(path + " cannot be blank");
        return template;
    }

    @SuppressWarnings("SameParameterValue")
    private static void requireSingleNameSlot(String template, String path) {
        String slot = "{name}";
        int first = template.indexOf(slot);
        if (first < 0 || first != template.lastIndexOf(slot)) {
            throw new IllegalArgumentException(path + " must contain exactly one {name} placeholder");
        }
    }

    private static String requireFileName(String value, String fieldName) {
        String fileName = Objects.requireNonNull(value, fieldName).trim();
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException(fieldName + " must be a single file name");
        }
        return fileName;
    }

    /**
     * Returns whether chat or player display configuration contains a PlaceholderAPI token.
     *
     * @return true when a configured chat, TAB, or name-tag template uses PlaceholderAPI
     */
    public boolean containsPlaceholderApiToken() {
        return TemplateRenderer.containsPlaceholderApiToken(chat.format())
                || display.tab().header().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || display.tab().footer().stream().anyMatch(TemplateRenderer::containsPlaceholderApiToken)
                || TemplateRenderer.containsPlaceholderApiToken(display.tab().playerNameFormat())
                || TemplateRenderer.containsPlaceholderApiToken(display.nameTagFormat());
    }

    /**
     * Returns the SQLite database path below a plugin data directory.
     *
     * @param pluginDataDirectory plugin data directory
     * @return normalized database path
     */
    public Path databasePath(Path pluginDataDirectory) {
        return database.databasePath(pluginDataDirectory);
    }

    /**
     * Database provider selected by database.mode.
     */
    public enum DatabaseMode {
        SQLITE("sqlite"),
        MYSQL("mysql");

        private final String configValue;

        DatabaseMode(String configValue) {
            this.configValue = configValue;
        }

        /**
         * Returns the configuration value.
         *
         * @return lower-case value
         */
        public String configValue() {
            return configValue;
        }

    }

    /**
     * Database storage settings.
     */
    public record DatabaseSettings(
            DatabaseMode mode,
            SqliteSettings sqlite,
            MySqlSettings mysql
    ) {

        /**
         * Validates database settings.
         */
        @SuppressWarnings("DataFlowIssue")
        public DatabaseSettings {
            mode = Objects.requireNonNull(mode, "mode");
            sqlite = Objects.requireNonNull(sqlite, "sqlite");
            mysql = Objects.requireNonNull(mysql, "mysql");
        }

        /**
         * Returns the SQLite path below a plugin directory.
         *
         * @param pluginDataDirectory plugin data directory
         * @return normalized SQLite path
         */
        public Path databasePath(Path pluginDataDirectory) {
            Path root = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory")
                    .toAbsolutePath()
                    .normalize();
            Path path = root.resolve(sqlite.fileName()).normalize();
            if (!path.startsWith(root) || path.equals(root)) {
                throw new IllegalStateException("Database path must stay below the plugin data directory");
            }
            return path;
        }
    }

    /**
     * SQLite file settings.
     */
    public record SqliteSettings(String fileName) {

        /**
         * Validates the SQLite file name.
         */
        public SqliteSettings {
            fileName = requireFileName(fileName, "database.sqlite.file");
        }
    }

    /**
     * MySQL 5.7-compatible connection settings.
     */
    public record MySqlSettings(
            String host,
            int port,
            String database,
            String username,
            String password,
            boolean useSsl,
            String serverTimezone
    ) {

        /**
         * Validates MySQL settings.
         */
        public MySqlSettings {
            host = requireText(host, "database.mysql.host");
            if (containsPathOrUrlDelimiter(host)) {
                throw new IllegalArgumentException("database.mysql.host contains unsupported characters");
            }
            if (port < 1 || port > 65535) throw new IllegalArgumentException("database.mysql.port is invalid");
            database = requireText(database, "database.mysql.database");
            if (containsPathOrUrlDelimiter(database)) {
                throw new IllegalArgumentException("database.mysql.database contains unsupported characters");
            }
            username = requireText(username, "database.mysql.username");
            password = Objects.requireNonNull(password, "password");
            serverTimezone = requireText(serverTimezone, "database.mysql.server-timezone");
            if (containsUrlDelimiter(serverTimezone)) {
                throw new IllegalArgumentException(
                        "database.mysql.server-timezone contains unsupported characters"
                );
            }
        }

        private static String requireText(String value, String fieldName) {
            String text = Objects.requireNonNull(value, fieldName).trim();
            if (text.isBlank()) throw new IllegalArgumentException(fieldName + " cannot be blank");
            return text;
        }

        private static boolean containsUrlDelimiter(String value) {
            return value.indexOf('?') >= 0
                    || value.indexOf('#') >= 0
                    || value.indexOf('&') >= 0
                    || value.chars().anyMatch(Character::isWhitespace);
        }

        private static boolean containsPathOrUrlDelimiter(String value) {
            return value.indexOf('/') >= 0 || containsUrlDelimiter(value);
        }

        /**
         * Builds the JDBC URL without credentials.
         *
         * @return MySQL JDBC URL
         */
        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&useSSL=" + useSsl
                    + "&serverTimezone=" + serverTimezone;
        }
    }

    /**
     * Main-city spawn settings.
     */
    public record SpawnSettings(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            boolean teleportOnJoin,
            boolean teleportOnRespawn
    ) {

        /**
         * Validates spawn settings.
         */
        public SpawnSettings {
            worldName = Objects.requireNonNull(worldName, "worldName").trim();
            if (worldName.isBlank()) throw new IllegalArgumentException("spawn.world cannot be blank");
        }
    }

    /**
     * Chat presentation settings.
     */
    public record ChatSettings(String format, String colorPermission) {

        /**
         * Validates chat settings.
         */
        public ChatSettings {
            format = Objects.requireNonNull(format, "format");
            colorPermission = Objects.requireNonNull(colorPermission, "colorPermission").trim();
            if (format.isBlank()) throw new IllegalArgumentException("chat.format cannot be blank");
            if (colorPermission.isBlank()) throw new IllegalArgumentException("chat.color-permission cannot be blank");
        }
    }

    /**
     * Player-facing TAB and name-tag presentation settings.
     */
    public record DisplaySettings(TabSettings tab, String nameTagFormat) {

        /**
         * Validates display settings.
         */
        @SuppressWarnings("DataFlowIssue")
        public DisplaySettings {
            tab = Objects.requireNonNull(tab, "tab");
            nameTagFormat = requireTemplate(nameTagFormat, "display.name-tag-format");
            requireSingleNameSlot(nameTagFormat, "display.name-tag-format");
        }

    }

    /**
     * TAB header, footer, and individual player-name settings.
     */
    public record TabSettings(
            List<String> header,
            List<String> footer,
            String playerNameFormat
    ) {

        /**
         * Validates and freezes TAB settings.
         */
        public TabSettings {
            header = List.copyOf(Objects.requireNonNull(header, "header"));
            footer = List.copyOf(Objects.requireNonNull(footer, "footer"));
            playerNameFormat = requireTemplate(playerNameFormat, "display.tab.player-name-format");
        }
    }

    private record RawSettings(
            @ConfigValue(
                    path = "libraries.repository",
                    defaultValue = "https://repo1.maven.org/maven2/",
                    nonBlank = true
            )
            String libraryRepository,
            @ConfigValue(
                    path = "database.mode",
                    defaultValue = "SQLITE"
            )
            DatabaseMode databaseMode,
            @ConfigValue(
                    path = "database.sqlite.file",
                    defaultValue = "profiles.db",
                    nonBlank = true
            )
            String sqliteFile,
            @ConfigValue(
                    path = "database.mysql.host",
                    defaultValue = "127.0.0.1",
                    nonBlank = true
            )
            String mysqlHost,
            @ConfigValue(
                    path = "database.mysql.port",
                    defaultValue = "3306",
                    positive = true
            )
            int mysqlPort,
            @ConfigValue(
                    path = "database.mysql.database",
                    defaultValue = "dreamrpg",
                    nonBlank = true
            )
            String mysqlDatabase,
            @ConfigValue(
                    path = "database.mysql.username",
                    defaultValue = "root",
                    nonBlank = true
            )
            String mysqlUsername,
            @ConfigValue(
                    path = "database.mysql.password",
                    defaultValue = "",
                    trim = false
            )
            String mysqlPassword,
            @ConfigValue(
                    path = "database.mysql.use-ssl",
                    defaultValue = "false"
            )
            boolean mysqlUseSsl,
            @ConfigValue(
                    path = "database.mysql.server-timezone",
                    defaultValue = "Asia/Shanghai",
                    nonBlank = true
            )
            String mysqlServerTimezone,
            @ConfigValue(
                    path = "spawn.world",
                    defaultValue = "world",
                    nonBlank = true
            )
            String spawnWorld,
            @ConfigValue(
                    path = "spawn.x",
                    defaultValue = "0.5"
            )
            double spawnX,
            @ConfigValue(
                    path = "spawn.y",
                    defaultValue = "7.0"
            )
            double spawnY,
            @ConfigValue(
                    path = "spawn.z",
                    defaultValue = "2.5"
            )
            double spawnZ,
            @ConfigValue(
                    path = "spawn.yaw",
                    defaultValue = "180"
            )
            double spawnYaw,
            @ConfigValue(
                    path = "spawn.pitch",
                    defaultValue = "0"
            )
            double spawnPitch,
            @ConfigValue(
                    path = "spawn.teleport-on-join",
                    defaultValue = "true"
            )
            boolean teleportOnJoin,
            @ConfigValue(
                    path = "spawn.teleport-on-respawn",
                    defaultValue = "true"
            )
            boolean teleportOnRespawn,
            @ConfigValue(
                    path = "chat.format",
                    defaultValue = "%luckperms_prefix% {prefix} {name}&7: &f{message}",
                    nonBlank = true,
                    trim = false
            )
            String chatFormat,
            @ConfigValue(
                    path = "chat.color-permission",
                    defaultValue = "dreamrpg.chat.color",
                    nonBlank = true
            )
            String colorPermission,
            @ConfigValue(
                    path = "display.tab.header",
                    defaultValue = "",
                    trim = false
            )
            List<String> tabHeader,
            @ConfigValue(
                    path = "display.tab.footer",
                    defaultValue = "",
                    trim = false
            )
            List<String> tabFooter,
            @ConfigValue(
                    path = "display.tab.player-name-format",
                    defaultValue = "{prefix}{name}        ",
                    nonBlank = true,
                    trim = false
            )
            String playerNameFormat,
            @ConfigValue(
                    path = "display.name-tag-format",
                    defaultValue = "{prefix}{name}",
                    nonBlank = true,
                    trim = false
            )
            String nameTagFormat
    ) {
    }
}
