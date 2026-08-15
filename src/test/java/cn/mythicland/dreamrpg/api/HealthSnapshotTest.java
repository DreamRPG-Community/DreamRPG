package cn.mythicland.dreamrpg.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthSnapshotTest {

    @Test
    void storesBaseAndLevelHealthSeparately() {
        HealthSnapshot snapshot = new HealthSnapshot(
                UUID.randomUUID(),
                10L,
                20.0D,
                50.0D,
                70.0D,
                true
        );

        assertEquals(20.0D, snapshot.baseHealth());
        assertEquals(50.0D, snapshot.levelHealthBonus());
        assertEquals(70.0D, snapshot.maximumHealth());
    }
}
