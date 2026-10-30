package cn.mythicland.dreamrpgcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatHealthFormulaTest {

    @Test
    void keepsMaximumHealthAtLeastOne() {
        assertEquals(20.0D, CombatHealthFormula.maximumHealth(20.0D, 0.0D));
        assertEquals(15.0D, CombatHealthFormula.maximumHealth(20.0D, -5.0D));
        assertEquals(1.0D, CombatHealthFormula.maximumHealth(20.0D, -100.0D));
    }

    @Test
    void preservesFullHealthButDoesNotHealDamagedPlayers() {
        assertEquals(25.0D, CombatHealthFormula.synchronizedCurrentHealth(20.0D, 20.0D, 25.0D));
        assertEquals(12.0D, CombatHealthFormula.synchronizedCurrentHealth(12.0D, 20.0D, 25.0D));
        assertEquals(20.0D, CombatHealthFormula.synchronizedCurrentHealth(25.0D, 25.0D, 20.0D));
    }
}
