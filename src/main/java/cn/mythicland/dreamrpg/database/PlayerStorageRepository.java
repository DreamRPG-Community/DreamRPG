package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.api.PlayerVitals;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.database.SqlDatabase;
import cn.mythicland.dreamlib.item.ItemStackArrayCodec;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC implementation of the DreamRPG player storage store.
 */
@InjectComponent
public final class PlayerStorageRepository implements PlayerStorageStore {

    private final SqlDatabase database;

    /**
     * Creates a storage repository from DreamRPG's initialized database.
     *
     * @param context DreamRPG infrastructure context
     */
    public PlayerStorageRepository(DreamRPGContext context) {
        this.database = Objects.requireNonNull(context, "context").database();
    }

    private PlayerStorageRepository(SqlDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    private static PlayerStorageSnapshot select(Connection connection, UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT s.format_version, s.inventory_base64, s.armor_base64, s.extra_base64, s.held_slot, "
                        + "s.ender_chest_base64, s.ender_chest_page, s.version, "
                        + "v.health, v.food_level, v.saturation, v.exhaustion FROM player_storage s "
                        + "LEFT JOIN player_vitals v ON v.uuid = s.uuid WHERE s.uuid = ?"
        )) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                int formatVersion = resultSet.getInt("format_version");
                if (formatVersion != PlayerStorageSnapshot.CURRENT_FORMAT_VERSION) {
                    throw new SQLException("Unsupported player storage format: " + formatVersion);
                }
                ItemStack[] inventory = ItemStackArrayCodec.deserialize(
                        resultSet.getString("inventory_base64"),
                        PlayerStorageSnapshot.INVENTORY_SIZE
                );
                ItemStack[] armor = ItemStackArrayCodec.deserialize(
                        resultSet.getString("armor_base64"),
                        PlayerStorageSnapshot.ARMOR_SIZE
                );
                ItemStack[] extra = ItemStackArrayCodec.deserialize(
                        resultSet.getString("extra_base64"),
                        1
                );
                ItemStack[] enderChest = ItemStackArrayCodec.deserialize(
                        resultSet.getString("ender_chest_base64"),
                        PlayerStorageSnapshot.ENDER_CHEST_SIZE
                );
                return new PlayerStorageSnapshot(
                        uniqueId,
                        inventory,
                        armor,
                        extra[0],
                        resultSet.getInt("held_slot"),
                        enderChest,
                        resultSet.getInt("ender_chest_page"),
                        formatVersion,
                        resultSet.getLong("version"),
                        resultSet.getObject("health") == null ? null : new PlayerVitals(
                                resultSet.getDouble("health"), resultSet.getInt("food_level"),
                                resultSet.getFloat("saturation"), resultSet.getFloat("exhaustion")
                        )
                );
            }
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Corrupted player storage data: " + uniqueId, exception);
        }
    }

    private static void insert(Connection connection, PlayerStorageSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_storage (uuid, format_version, inventory_base64, armor_base64, "
                        + "extra_base64, held_slot, ender_chest_base64, ender_chest_page, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            statement.setString(1, snapshot.uniqueId().toString());
            bindSnapshot(statement, snapshot, snapshot.databaseVersion(), 2);
            long now = System.currentTimeMillis();
            statement.setLong(10, now);
            statement.setLong(11, now);
            statement.executeUpdate();
        }
    }

    private static void bindSnapshot(
            PreparedStatement statement,
            PlayerStorageSnapshot snapshot,
            long version,
            int offset
    ) throws SQLException {
        statement.setInt(offset, snapshot.formatVersion());
        statement.setString(offset + 1, ItemStackArrayCodec.serialize(snapshot.inventory()));
        statement.setString(offset + 2, ItemStackArrayCodec.serialize(snapshot.armor()));
        statement.setString(offset + 3, ItemStackArrayCodec.serialize(new ItemStack[]{snapshot.offHand()}));
        statement.setInt(offset + 4, snapshot.heldSlot());
        statement.setString(offset + 5, ItemStackArrayCodec.serialize(snapshot.enderChest()));
        statement.setInt(offset + 6, snapshot.enderChestPage());
        statement.setLong(offset + 7, version);
        statement.setLong(offset + 8, System.currentTimeMillis());
    }

    @Override
    public PlayerStorageSnapshot loadOrCreate(UUID uniqueId) throws SQLException {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return database.transaction(connection -> {
            PlayerStorageSnapshot existing = select(connection, uniqueId);
            if (existing != null) return existing;
            PlayerStorageSnapshot empty = PlayerStorageSnapshot.empty(uniqueId);
            insert(connection, empty);
            return empty;
        });
    }

    @Override
    public long save(PlayerStorageSnapshot snapshot, long expectedVersion) throws SQLException {
        PlayerStorageSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        if (expectedVersion < 0L) throw new IllegalArgumentException("expectedVersion cannot be negative");
        long nextVersion = expectedVersion + 1L;
        int changedRows = database.transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_storage SET format_version = ?, inventory_base64 = ?, "
                            + "armor_base64 = ?, extra_base64 = ?, held_slot = ?, "
                            + "ender_chest_base64 = ?, ender_chest_page = ?, version = ?, updated_at = ? "
                            + "WHERE uuid = ? AND version = ?"
            )) {
                bindSnapshot(statement, value, nextVersion, 1);
                statement.setString(10, value.uniqueId().toString());
                statement.setLong(11, expectedVersion);
                int updated = statement.executeUpdate();
                if (updated != 1 || value.vitals() == null) return updated;
                PlayerVitals vitals = value.vitals();
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM player_vitals WHERE uuid = ?")) {
                    delete.setString(1, value.uniqueId().toString());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO player_vitals (uuid, health, food_level, saturation, exhaustion) VALUES (?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, value.uniqueId().toString());
                    insert.setDouble(2, vitals.health());
                    insert.setInt(3, vitals.foodLevel());
                    insert.setFloat(4, vitals.saturation());
                    insert.setFloat(5, vitals.exhaustion());
                    insert.executeUpdate();
                }
                return updated;
            }
        });
        if (changedRows != 1) {
            throw new SQLException("Player storage version conflict: " + value.uniqueId());
        }
        return nextVersion;
    }
}
