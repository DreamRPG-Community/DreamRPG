package cn.mythicland.dreamrpg.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeEditorEntryTest {
    @Test
    void onlySneakingAdministratorsEnterNpcEditor() {
        assertTrue(ExchangeNpcListener.canOpenEditor(true, true));
        assertFalse(ExchangeNpcListener.canOpenEditor(false, true));
        assertFalse(ExchangeNpcListener.canOpenEditor(true, false));
    }
}
