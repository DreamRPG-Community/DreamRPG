package cn.mythicland.dreamrpg.title;

import cn.mythicland.framework.database.SqlDatabase;
import cn.mythicland.framework.database.SqlWork;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TitleRepositoryTest {
    @Test
    void persistsSelectionReplacesTimedExpiryAndRevokesAtomically() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (var resource = getClass().getResourceAsStream("/db/sqlite/V12__create_player_titles.sql")) {
                assertNotNull(resource);
                for (String sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!sql.isBlank()) try (var statement = connection.createStatement()) {
                        statement.execute(sql);
                    }
                }
            }
            SqlDatabase database = new SqlDatabase() {
                public <T> T query(SqlWork<T> work) throws SQLException {
                    return work.execute(connection);
                }

                public <T> T transaction(SqlWork<T> work) throws SQLException {
                    connection.setAutoCommit(false);
                    try {
                        T result = work.execute(connection);
                        connection.commit();
                        return result;
                    } catch (SQLException | RuntimeException failure) {
                        connection.rollback();
                        throw failure;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                }

                public void close() {
                }
            };
            var repository = new TitleRepository(database);
            UUID player = UUID.randomUUID();
            assertTrue(repository.load(player).expirations().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> repository.select(player, "hero", 1000));
            assertEquals(1500L, repository.grant(player, "hero", 500, 1000).expirations().get("hero"));
            assertEquals(1500L, repository.grant(player, "hero", 500, 1000).expirations().get("hero"),
                    "Giving the same duration twice must not stack time");
            assertEquals(1750L, repository.grant(player, "hero", 500, 1250).expirations().get("hero"),
                    "A later grant restarts the duration from that grant's timestamp");
            assertEquals("hero", repository.select(player, "hero", 1749).selectedId());
            assertThrows(IllegalArgumentException.class, () -> repository.select(player, "hero", 1750));
            assertEquals("hero", repository.load(player).selectedId(), "Failed selection must preserve persisted state");
            assertEquals("hero", repository.expire(player, 1749).selectedId());
            assertEquals("", repository.expire(player, 1750).selectedId());
            assertEquals("", repository.load(player).selectedId(), "Expiry must durably unequip the title");
            assertEquals(3500L, repository.grant(player, "hero", 500, 3000).expirations().get("hero"));
            assertEquals("", repository.load(player).selectedId(), "Renewing an expired title must not equip it again");
            repository.select(player, "hero", 3100);
            assertEquals("hero", repository.expire(player, 3499).selectedId());
            assertEquals("", repository.grant(player, "hero", 500, 3500).selectedId(),
                    "A grant at the old deadline must clear the expired selection before renewing ownership");
            assertEquals(0L, repository.grant(player, "hero", 0, 3000).expirations().get("hero"));
            assertEquals(0L, repository.grant(player, "hero", 500, 3000).expirations().get("hero"));
            assertEquals("", repository.select(player, "", 3000).selectedId());
            repository.select(player, "hero", 3000);
            var removed = repository.revoke(player, "hero");
            assertEquals("", removed.selectedId());
            assertFalse(removed.expirations().containsKey("hero"));
            assertEquals(removed, new TitleRepository(database).load(player));
            assertThrows(ArithmeticException.class, () -> repository.grant(player, "hero", Long.MAX_VALUE, 3000));
            assertTrue(repository.load(player).expirations().isEmpty());
        }
    }
}
