package cn.mythicland.dreamrpg.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the runtime dependency manifest is embedded and immutable.
 */
class RuntimeLibraryManifestTest {

    @Test
    void embeddedManifestPinsVerifiedSqliteDependency() {
        RuntimeLibraryManifest manifest = RuntimeLibraryManifest.embedded();

        assertEquals("org.xerial:sqlite-jdbc:3.46.1.3", manifest.sqlite().coordinate());
        assertEquals("sqlite-jdbc-3.46.1.3.jar", manifest.sqlite().fileName());
        assertEquals(
                "4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa",
                manifest.sqlite().sha256()
        );
        assertEquals("org.sqlite.JDBC", manifest.driverClassName());
        assertEquals("com.mysql:mysql-connector-j:8.0.33", manifest.mysql().coordinate());
        assertEquals("mysql-connector-j-8.0.33.jar", manifest.mysql().fileName());
        assertEquals(
                "e2a3b2fc726a1ac64e998585db86b30fa8bf3f706195b78bb77c5f99bf877bd9",
                manifest.mysql().sha256()
        );
        assertEquals("com.mysql.cj.jdbc.Driver", manifest.mysqlDriverClassName());
    }
}
