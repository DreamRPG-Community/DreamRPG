package cn.mythicland.dreamrpg.event;

import cn.mythicland.dreamrpg.api.CareerDefinition;
import cn.mythicland.dreamrpg.api.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the career event's before-and-after identity contract.
 */
class CareerChangedEventTest {

    @Test
    void eventKeepsTheSamePlayerAcrossDistinctCareerSnapshots() {
        UUID uniqueId = UUID.randomUUID();
        CareerDefinition previousCareer = new CareerDefinition("unclassed", "无职业", "&7[无职业] ");
        CareerDefinition currentCareer = new CareerDefinition("warrior", "战士", "&c[战士] ");
        CareerChangedEvent event = new CareerChangedEvent(
                new PlayerProfile(uniqueId, "unclassed", previousCareer),
                new PlayerProfile(uniqueId, "warrior", currentCareer)
        );

        assertEquals(uniqueId, event.currentProfile().uniqueId());
        assertEquals("unclassed", event.previousProfile().careerId());
    }

    @Test
    void eventRejectsAProfilePairForDifferentPlayers() {
        CareerDefinition career = new CareerDefinition("unclassed", "无职业", "&7[无职业] ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new CareerChangedEvent(
                        new PlayerProfile(UUID.randomUUID(), "unclassed", career),
                        new PlayerProfile(UUID.randomUUID(), "unclassed", career)
                )
        );
    }
}
