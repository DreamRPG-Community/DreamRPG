package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.text.TemplateRenderer;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        URI libraryRepository = URI.create(requiredString(configuration, "libraries.repository"));
        DatabaseSettings database = DatabaseSettings.load(configuration);
        SpawnSettings spawn = new SpawnSettings(
                requiredString(configuration, "spawn.world"),
                requiredDouble(configuration, "spawn.x"),
                requiredDouble(configuration, "spawn.y"),
                requiredDouble(configuration, "spawn.z"),
                (float) requiredDouble(configuration, "spawn.yaw"),
                (float) requiredDouble(configuration, "spawn.pitch"),
                requiredBoolean(configuration, "spawn.teleport-on-join"),
                requiredBoolean(configuration, "spawn.teleport-on-respawn")
        );
        ChatSettings chat = new ChatSettings(
                requiredString(configuration, "chat.format"),
                requiredString(configuration, "chat.color-permission")
        );
        DisplaySettings display = DisplaySettings.load(configuration);
        return new DreamRpgSettings(libraryRepository, database, spawn, chat, display);
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

    /** Database storage settings. */
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

        private static DatabaseSettings load(FileConfiguration configuration) {
            DatabaseMode mode = DatabaseMode.parse(requiredString(configuration, "database.mode"));
            SqliteSettings sqlite = new SqliteSettings(
                    optionalString(configuration, "database.sqlite.file", "profiles.db")
            );
            MySqlSettings mysql = new MySqlSettings(
                    optionalString(configuration, "database.mysql.host", "127.0.0.1"),
                    optionalInt(configuration, "database.mysql.port", 3306),
                    optionalString(configuration, "database.mysql.database", "dreamrpg"),
                    optionalString(configuration, "database.mysql.username", "root"),
                    optionalPassword(configuration, "database.mysql.password"),
                    optionalBoolean(configuration, "database.mysql.use-ssl", false),
                    optionalString(configuration, "database.mysql.server-timezone", "Asia/Shanghai")
            );
            return new DatabaseSettings(mode, sqlite, mysql);
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

    /** SQLite file settings. */
    public record SqliteSettings(String fileName) {

        /**
         * Validates the SQLite file name.
         */
        public SqliteSettings {
            fileName = requireFileName(fileName, "database.sqlite.file");
        }
    }

    /** Database provider selected by database.mode. */
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

        private static DatabaseMode parse(String rawValue) {
            String normalized = Objects.requireNonNull(rawValue, "rawValue").trim().toLowerCase(Locale.ROOT);
            for (DatabaseMode value : values()) {
                if (value.configValue.equals(normalized)) return value;
            }
            throw new IllegalArgumentException("Unsupported database.mode: " + rawValue);
        }
    }

    /** MySQL 5.7-compatible connection settings. */
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
    }

    /** Main-city spawn settings. */
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

    /** Chat presentation settings. */
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

    /** Player-facing TAB and name-tag presentation settings. */
    public record DisplaySettings(TabSettings tab, String nameTagFormat) {

        private static final String DEFAULT_PLAYER_NAME_FORMAT = "{prefix}{name}        ";
        private static final String DEFAULT_NAME_TAG_FORMAT = "{prefix}{name}";

        /**
         * Validates display settings.
         */
        @SuppressWarnings("DataFlowIssue")
        public DisplaySettings {
            tab = Objects.requireNonNull(tab, "tab");
            nameTagFormat = requireTemplate(nameTagFormat, "display.name-tag-format");
            requireSingleNameSlot(nameTagFormat, "display.name-tag-format");
        }

        private static DisplaySettings load(FileConfiguration configuration) {
            TabSettings tab = new TabSettings(
                    optionalStringList(configuration, "display.tab.header", List.of()),
                    optionalStringList(configuration, "display.tab.footer", List.of()),
                    optionalTemplate(
                            configuration,
                            "display.tab.player-name-format",
                            DEFAULT_PLAYER_NAME_FORMAT
                    )
            );
            String configuredFormat = optionalTemplate(
                    configuration,
                    "display.name-tag-format",
                    DEFAULT_NAME_TAG_FORMAT
            );
            return new DisplaySettings(tab, configuredFormat);
        }
    }

    /** TAB header, footer, and individual player-name settings. */
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

    private static String requiredString(FileConfiguration configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Configuration requires a non-empty string: " + path);
        }
        return value.trim();
    }

    private static String optionalString(FileConfiguration configuration, String path, String defaultValue) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Configuration requires a non-empty string: " + path);
        }
        return value.trim();
    }

    private static String optionalTemplate(FileConfiguration configuration, String path, String defaultValue) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof String value)) {
            throw new IllegalStateException("Configuration requires a string: " + path);
        }
        return requireTemplate(value, path);
    }

    private static List<String> optionalStringList(
            FileConfiguration configuration,
            String path,
            List<String> defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return List.copyOf(defaultValue);
        if (!(rawValue instanceof List<?> values)) {
            throw new IllegalStateException("Configuration requires a string list: " + path);
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof String line)) {
                throw new IllegalStateException("Configuration requires string list entries: " + path);
            }
            result.add(line);
        }
        return List.copyOf(result);
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

    @SuppressWarnings("SameParameterValue")
    private static String optionalPassword(FileConfiguration configuration, String path) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return "";
        if (!(rawValue instanceof String value)) {
            throw new IllegalStateException("Configuration requires a string: " + path);
        }
        return value;
    }

    private static boolean requiredBoolean(FileConfiguration configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof Boolean value)) throw new IllegalStateException("Configuration requires a boolean: " + path);
        return value;
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean optionalBoolean(FileConfiguration configuration, String path, boolean defaultValue) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof Boolean value)) throw new IllegalStateException("Configuration requires a boolean: " + path);
        return value;
    }

    @SuppressWarnings("SameParameterValue")
    private static int optionalInt(FileConfiguration configuration, String path, int defaultValue) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof Number value)) throw new IllegalStateException("Configuration requires a number: " + path);
        return value.intValue();
    }

    private static double optionalDouble(
            FileConfiguration configuration,
            String path,
            double defaultValue
    ) {
        Object rawValue = configuration.get(path);
        if (rawValue == null) return defaultValue;
        if (!(rawValue instanceof Number value)) {
            throw new IllegalStateException("Configuration requires a number: " + path);
        }
        return value.doubleValue();
    }

    private static double requiredDouble(FileConfiguration configuration, String path) {
        Object rawValue = configuration.get(path);
        if (!(rawValue instanceof Number value)) throw new IllegalStateException("Configuration requires a number: " + path);
        return value.doubleValue();
    }

    @SuppressWarnings("SameParameterValue")
    private static String requireFileName(String value, String fieldName) {
        String fileName = Objects.requireNonNull(value, fieldName).trim();
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException(fieldName + " must be a single file name");
        }
        return fileName;
    }
}
