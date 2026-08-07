package cn.mythicland.dreamrpg.config;

import cn.mythicland.lib.library.LibrarySpec;

import java.util.Objects;

/**
 * Defines the immutable runtime library manifest bundled with DreamRPG.
 */
public record RuntimeLibraryManifest(
        LibrarySpec sqlite,
        String driverClassName,
        LibrarySpec mysql,
        String mysqlDriverClassName
) {

    private static final String SQLITE_COORDINATE = "org.xerial:sqlite-jdbc:3.46.1.3";
    private static final String SQLITE_FILE_NAME = "sqlite-jdbc-3.46.1.3.jar";
    private static final String SQLITE_SHA256 = "4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa";
    private static final String SQLITE_DRIVER_CLASS = "org.sqlite.JDBC";
    private static final String MYSQL_COORDINATE = "com.mysql:mysql-connector-j:8.0.33";
    private static final String MYSQL_FILE_NAME = "mysql-connector-j-8.0.33.jar";
    private static final String MYSQL_SHA256 = "e2a3b2fc726a1ac64e998585db86b30fa8bf3f706195b78bb77c5f99bf877bd9";
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    /**
     * Validates the SQLite manifest entry.
     */
    public RuntimeLibraryManifest {
        sqlite = Objects.requireNonNull(sqlite, "sqlite");
        driverClassName = Objects.requireNonNull(driverClassName, "driverClassName").trim();
        if (driverClassName.isBlank()) throw new IllegalArgumentException("SQLite driver class cannot be blank");
        mysql = Objects.requireNonNull(mysql, "mysql");
        mysqlDriverClassName = Objects.requireNonNull(mysqlDriverClassName, "mysqlDriverClassName").trim();
        if (mysqlDriverClassName.isBlank()) throw new IllegalArgumentException("MySQL driver class cannot be blank");
    }

    /**
     * Returns the fixed SQLite manifest compiled into DreamRPG.
     *
     * @return embedded SQLite runtime library manifest
     */
    public static RuntimeLibraryManifest embedded() {
        return new RuntimeLibraryManifest(
                new LibrarySpec(SQLITE_COORDINATE, SQLITE_FILE_NAME, SQLITE_SHA256),
                SQLITE_DRIVER_CLASS,
                new LibrarySpec(MYSQL_COORDINATE, MYSQL_FILE_NAME, MYSQL_SHA256),
                MYSQL_DRIVER_CLASS
        );
    }
}
