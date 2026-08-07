package cn.mythicland.dreamrpg.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the public career prefix contract.
 */
class CareerDefinitionTest {

    @Test
    void unclassedPrefixProvidesGrayNameColorAndFitsTheTeamLimit() {
        CareerDefinition career = new CareerDefinition("unclassed", "无职业", "&7[无职业] ");

        assertEquals("§7[无职业] ", career.prefix());
        assertEquals("§7", career.nameColor());
    }

    @Test
    void careerPrefixLongerThanSixteenLegacyCharactersIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CareerDefinition("warrior", "战士", "&7[这是一个极其漫长的职业前缀名称] ")
        );
    }

    @Test
    void profileCannotPairAnIdentifierWithAnotherCareerDefinition() {
        CareerDefinition unclassed = new CareerDefinition("unclassed", "无职业", "&7[无职业] ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerProfile(
                        java.util.UUID.randomUUID(),
                        "warrior",
                        unclassed
                )
        );
    }
}
