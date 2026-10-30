package cn.mythicland.dreamrpgcombat.actionbar;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatHealthBarFormatterTest {

    @Test
    void rendersRemainingDamageAndEmptyHeartsLikeReferenceProject() {
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("僵尸")
                .remainingHealth(10.0D)
                .remainingAbsorption(0.0D)
                .maxHealth(20.0D)
                .healthDamage(4.0D)
                .expiresAtMillis(1L)
                .build();

        assertEquals("僵尸 &4❤❤❤❤❤&c❤❤&7❤❤❤", CombatHealthBarFormatter.render(status));
    }

    @Test
    void absorptionIsDisplayedBeforeDamageAndEmptySlots() {
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("Boss")
                .remainingHealth(6.0D)
                .remainingAbsorption(4.0D)
                .maxHealth(20.0D)
                .healthDamage(8.0D)
                .expiresAtMillis(1L)
                .build();

        assertEquals("Boss &4❤❤❤&e❤❤&c❤❤❤❤&7❤", CombatHealthBarFormatter.render(status));
    }

    @Test
    void compressesVeryLargeMythicMobHealthIntoAClientSafeBar() {
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("大型怪物")
                .remainingHealth(1_000_000.0D)
                .remainingAbsorption(0.0D)
                .maxHealth(2_000_000.0D)
                .healthDamage(100_000.0D)
                .expiresAtMillis(1L)
                .build();

        String expected = "大型怪物 &4" + "❤".repeat(5)
                + "&c❤"
                + "&7" + "❤".repeat(4);
        assertEquals(expected, CombatHealthBarFormatter.render(status));
    }

    @Test
    void alwaysUsesTenHeartsAndScalesAbsorptionToTheSameCapacity() {
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("超大怪物")
                .remainingHealth(60.0D)
                .remainingAbsorption(20.0D)
                .maxHealth(100.0D)
                .healthDamage(10.0D)
                .expiresAtMillis(1L)
                .build();

        assertEquals("超大怪物 &4" + "❤".repeat(6)
                + "&e" + "❤".repeat(2)
                + "&c❤"
                + "&7❤", CombatHealthBarFormatter.render(status));
    }

    @Test
    void capsAbsorptionAtTenHeartsWithoutExpandingTheBar() {
        CombatHealthBarStatus status = CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("护盾怪物")
                .remainingHealth(0.0D)
                .remainingAbsorption(500.0D)
                .maxHealth(100.0D)
                .healthDamage(0.0D)
                .expiresAtMillis(1L)
                .build();

        assertEquals("护盾怪物 &e" + "❤".repeat(10), CombatHealthBarFormatter.render(status));
    }
}
