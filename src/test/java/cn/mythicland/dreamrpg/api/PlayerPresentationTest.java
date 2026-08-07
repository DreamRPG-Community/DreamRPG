package cn.mythicland.dreamrpg.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the shared TAB presentation spacing contract.
 */
class PlayerPresentationTest {

    @Test
    void tabNameSuffixContainsEightSpaces() {
        assertEquals("        ", PlayerPresentation.TAB_NAME_SUFFIX);
    }
}
