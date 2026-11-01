package cn.mythicland.dreamrpg.member;

import cn.mythicland.framework.database.MigrationRunner;
import cn.mythicland.framework.database.MigrationSpec;
import cn.mythicland.framework.database.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MembershipMigrationTest {
    @TempDir
    Path directory;

    private List<MigrationSpec> migrations() {
        return List.of(
                new MigrationSpec(9, "/db/sqlite/V9__create_memberships.sql"),
                new MigrationSpec(10, "/db/sqlite/V10__create_member_names.sql"),
                new MigrationSpec(11, "/db/sqlite/V11__index_member_names.sql")
        );
    }

    @Test
    void createsFullMembershipSchemaAndPreservesRecordsOnRepeatedStartup() throws Exception {
        try (var database = SqliteDatabase.open(Logger.getAnonymousLogger(), directory.resolve("game.db"),
                "org.sqlite.JDBC", getClass().getClassLoader())) {
            var runner = new MigrationRunner("dreamrpg_schema_history");
            runner.migrate(database, getClass()::getResourceAsStream, migrations());
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO mythic_membership VALUES "
                            + "('player', 'mythicmembership:vip_plus', 'network', NULL, 'mythicmembership:mvp_plus', 60000)");
                    statement.executeUpdate("INSERT INTO mythic_membership_player_name VALUES ('tester', 'Tester', 'player', 123)");
                }
                return null;
            });
            runner.migrate(database, getClass()::getResourceAsStream, migrations());
            database.query(connection -> {
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT m.expires_at_millis, m.paused_remaining_millis, n.player_name "
                             + "FROM mythic_membership m JOIN mythic_membership_player_name n USING (player_uuid)")) {
                    assertTrue(rows.next());
                    assertNull(rows.getObject(1), "Permanent membership must survive migration");
                    assertEquals(60000, rows.getLong(2), "Paused membership balance must be retained");
                    assertEquals("Tester", rows.getString(3));
                    assertFalse(rows.next());
                }
                return null;
            });
        }
    }

    @Test
    void failedMigrationRollsBackAllNewTablesAndHistory() throws Exception {
        try (var database = SqliteDatabase.open(Logger.getAnonymousLogger(), directory.resolve("failure.db"),
                "org.sqlite.JDBC", getClass().getClassLoader())) {
            var specs = new ArrayList<>(migrations());
            specs.add(new MigrationSpec(12, "broken"));
            var runner = new MigrationRunner("dreamrpg_schema_history");
            assertThrows(SQLException.class, () -> runner.migrate(database, path -> {
                if (path.equals("broken")) return new ByteArrayInputStream("INVALID SQL".getBytes(StandardCharsets.UTF_8));
                return getClass().getResourceAsStream(path);
            }, specs));
            database.query(connection -> {
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table'")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1), "Failed initialization must leave no partial schema");
                }
                return null;
            });
            runner.migrate(database, getClass()::getResourceAsStream, migrations());
        }
    }
}
