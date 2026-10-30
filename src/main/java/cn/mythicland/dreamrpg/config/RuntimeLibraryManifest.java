package cn.mythicland.dreamrpg.config;

import cn.mythicland.dreamlib.database.DatabaseDrivers;
import cn.mythicland.dreamlib.library.LibrarySpec;

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

    private static final String MYSQL_COORDINATE = "com.mysql:mysql-connector-j:8.0.33";
    private static final String MYSQL_FILE_NAME = "mysql-connector-j-8.0.33.jar";
    private static final String MYSQL_SHA256 = "e2a3b2fc726a1ac64e998585db86b30fa8bf3f706195b78bb77c5f99bf877bd9";
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    /**
     * Validates the SQLite manifest entry.
     */
    @SuppressWarnings("DataFlowIssue")
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
                DatabaseDrivers.SQLITE,
                DatabaseDrivers.SQLITE_CLASS,
                new LibrarySpec(MYSQL_COORDINATE, MYSQL_FILE_NAME, MYSQL_SHA256),
                MYSQL_DRIVER_CLASS
        );
    }
}
