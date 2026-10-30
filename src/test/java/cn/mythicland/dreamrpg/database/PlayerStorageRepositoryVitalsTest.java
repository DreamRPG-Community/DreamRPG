package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.api.PlayerVitals;
import cn.mythicland.dreamlib.database.SqlDatabase;
import cn.mythicland.dreamlib.database.SqlWork;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStorageRepositoryVitalsTest {
    @Test
    void savesAndLoadsVitalsAtomicallyWithInventoryVersion() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            for (String resource : new String[]{"/db/sqlite/V3__create_player_storage.sql", "/db/sqlite/V6__create_player_vitals.sql"}) {
                try (var stream = getClass().getResourceAsStream(resource)) {
                    assertNotNull(stream);
                    connection.createStatement().execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            SqlDatabase database = new SqlDatabase() {
                @Override public <T> T query(SqlWork<T> work) throws SQLException { return work.execute(connection); }
                @Override public <T> T transaction(SqlWork<T> work) throws SQLException {
                    connection.setAutoCommit(false);
                    try {
                        T value = work.execute(connection);
                        connection.commit();
                        return value;
                    } catch (SQLException | RuntimeException exception) {
                        connection.rollback();
                        throw exception;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                }
                @Override public void close() { }
            };
            Constructor<PlayerStorageRepository> constructor = PlayerStorageRepository.class.getDeclaredConstructor(SqlDatabase.class);
            constructor.setAccessible(true);
            PlayerStorageRepository repository = constructor.newInstance(database);
            UUID id = UUID.randomUUID();
            PlayerStorageSnapshot initial = repository.loadOrCreate(id);
            assertNull(initial.vitals());
            PlayerVitals vitals = new PlayerVitals(9.5, 13, 2.5f, 1.25f);
            assertEquals(1, repository.save(initial.withVitals(vitals), 0));
            assertEquals(vitals, repository.loadOrCreate(id).vitals());
            assertThrows(SQLException.class, () -> repository.save(initial.withVitals(new PlayerVitals(1, 1, 0, 0)), 0));
            assertEquals(vitals, repository.loadOrCreate(id).vitals());
        }
    }
}
