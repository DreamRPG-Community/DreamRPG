package cn.mythicland.dreamrpg.settings;

import cn.mythicland.framework.database.SqlDatabase;
import cn.mythicland.framework.database.SqlWork;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSettingsRepositoryTest {
    @Test
    void migratesExistingKnockbackChoicesAndSavesBothOptionsIndependently() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            UUID existing = UUID.randomUUID();
            migrate(connection, "V7__create_player_settings.sql");
            try (var insert = connection.prepareStatement("INSERT INTO player_settings VALUES (?, FALSE)")) {
                insert.setString(1, existing.toString());
                insert.executeUpdate();
            }
            migrate(connection, "V8__add_auto_pickup.sql");
            SqlDatabase database = new SqlDatabase() {
                public <T> T query(SqlWork<T> work) throws SQLException { return work.execute(connection); }
                public <T> T transaction(SqlWork<T> work) throws SQLException { return work.execute(connection); }
                public void close() { }
            };
            var repository = new PlayerSettingsRepository(database);
            assertEquals(new PlayerSettingsRepository.Options(false, false), repository.load(existing));
            repository.setAutoPickup(existing, true);
            repository.setAllowsMobKnockback(existing, true);
            assertEquals(new PlayerSettingsRepository.Options(true, true), repository.load(existing));
            UUID fresh = UUID.randomUUID();
            assertEquals(new PlayerSettingsRepository.Options(true, false), repository.load(fresh));
            repository.setAutoPickup(fresh, true);
            repository.setAllowsMobKnockback(fresh, false);
            assertEquals(new PlayerSettingsRepository.Options(false, true), repository.load(fresh));
        }
    }

    private void migrate(Connection connection, String filename) throws Exception {
        try (var resource = getClass().getResourceAsStream("/db/sqlite/" + filename);
             var statement = connection.createStatement()) {
            assertNotNull(resource);
            statement.execute(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
