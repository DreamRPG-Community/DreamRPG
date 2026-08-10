package cn.mythicland.dreamrpg.database;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.bootstrap.DreamRpgContext;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.database.SqlDatabase;
import cn.mythicland.lib.item.ItemStackArrayCodec;
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
    public PlayerStorageRepository(DreamRpgContext context) {
        this.database = Objects.requireNonNull(context, "context").database();
    }

    private static PlayerStorageSnapshot select(Connection connection, UUID uniqueId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT format_version, inventory_base64, armor_base64, extra_base64, held_slot, "
                        + "ender_chest_base64, ender_chest_page, version FROM player_storage WHERE uuid = ?"
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
                        resultSet.getLong("version")
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
                return statement.executeUpdate();
            }
        });
        if (changedRows != 1) {
            throw new SQLException("Player storage version conflict: " + value.uniqueId());
        }
        return nextVersion;
    }
}
