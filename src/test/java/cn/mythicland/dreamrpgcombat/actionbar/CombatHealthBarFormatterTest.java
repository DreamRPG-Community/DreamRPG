package cn.mythicland.dreamrpgcombat.actionbar;

import cn.mythicland.dreamrpgcombat.api.CombatDamageType;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatHealthBarFormatterTest {

    @Test
    void neverRoundsAnInjuredPlayerUpToAFullHealthBar() {
        String rendered = CombatHealthBarFormatter.render(status(215, 210, 5).finalDamage(5).build());
        assertTrue(rendered.contains("&4" + "❤".repeat(9) + "&c❤"), rendered);
        String roundedFull = CombatHealthBarFormatter.render(status(215, 214.999999, 0).build());
        assertTrue(roundedFull.contains("215.00&7/&c215.00"), roundedFull);
        assertTrue(roundedFull.contains("&4" + "❤".repeat(10)), roundedFull);
    }

    @Test
    void scalesSmallHealthPoolsToTenHearts() {
        var status = status(10, 6, 2).finalDamage(2).build();
        assertEquals("目标 &7(&e6.00&7/&c10.00&7) &4❤❤❤❤❤❤&c❤❤&7❤❤ &6+2.00", CombatHealthBarFormatter.render(status));
    }

    @Test
    void displaysHealthAndAbsorptionAsIndependentPools() {
        var status = status(20, 6, 8).remainingAbsorption(4).finalDamage(8).build();
        assertEquals("目标 &7(&c6.00&7/&c20.00&7) &4❤❤❤&c❤❤❤❤&7❤❤❤ &e" + "❤".repeat(10) + " &6+8.00", CombatHealthBarFormatter.render(status));
    }

    @Test
    void proportionallyScalesLargePoolsToTenHeartsEach() {
        var status = status(2_000_000, 1_000_000, 100_000)
                .remainingAbsorption(500).maxAbsorption(1000).finalDamage(100_000).build();
        assertEquals("目标 &7(&e1000000.00&7/&c2000000.00&7) &4" + "❤".repeat(5) + "&c❤&7" + "❤".repeat(4)
                + " &e" + "❤".repeat(5) + " &6+100000.00", CombatHealthBarFormatter.render(status));
    }

    @Test
    void retainsTheOriginalShieldScaleWhenOnlyAFewHealthPointsRemain() {
        var status = status(1000, 1000, 0).remainingAbsorption(8).maxAbsorption(100).finalDamage(2).build();
        assertEquals("目标 &7(&a1000.00&7/&c1000.00&7) &4" + "❤".repeat(10) + " &e❤ &6+2.00", CombatHealthBarFormatter.render(status));
    }

    @Test
    void keepsFullRedAndGoldPoolsVisibleWithoutSharingCapacity() {
        var status = status(20, 20, 0).remainingAbsorption(20).finalDamage(1).build();
        assertEquals("目标 &7(&a20.00&7/&c20.00&7) &4" + "❤".repeat(10) + " &e" + "❤".repeat(10) + " &6+1.00",
                CombatHealthBarFormatter.render(status));
    }

    @Test
    void showsTheLastFractionOfAHeartAndNoGoldHeartForZeroAbsorption() {
        var status = status(20, 0.1, 19.9).finalDamage(19.9).build();
        assertEquals("目标 &7(&c0.10&7/&c20.00&7) &4❤&c" + "❤".repeat(9) + " &6+19.90", CombatHealthBarFormatter.render(status));
    }

    @Test
    void rendersExactDamageWithTheResolvedChannelColor() {
        assertEquals("目标 &7(&a2.00&7/&c2.00&7) &4" + "❤".repeat(10) + " &6+10.00", CombatHealthBarFormatter.render(
                status(2, 2, 0).damageType(CombatDamageType.PHYSICAL).finalDamage(10).build()));
        assertEquals("目标 &7(&a2.00&7/&c2.00&7) &4" + "❤".repeat(10) + " &d+10.25", CombatHealthBarFormatter.render(
                status(2, 2, 0).damageType(CombatDamageType.MAGIC).finalDamage(10.25).build()));
        assertEquals("目标 &7(&a2.00&7/&c2.00&7) &4" + "❤".repeat(10) + " &f+10.26", CombatHealthBarFormatter.render(
                status(2, 2, 0).damageType(CombatDamageType.TRUE).finalDamage(10.256).build()));
    }

    @Test
    void showsAnEmptyHealthPoolAfterALethalHitWithoutExpandingIt() {
        assertEquals("目标 &7(&c0.00&7/&c6.00&7) &c" + "❤".repeat(10) + " &f+100.00", CombatHealthBarFormatter.render(
                status(6, 0, 6).damageType(CombatDamageType.TRUE).finalDamage(100).build()));
    }

    @Test
    void colorsRemainingHealthAtTheThirdBoundaries() {
        Map<Double, String> expected = Map.of(
                0.0, "&c0.00", 100.0, "&c100.00", 100.01, "&e100.01",
                200.0, "&e200.00", 200.01, "&a200.01", 300.0, "&a300.00"
        );
        for (var entry : expected.entrySet()) {
            String rendered = CombatHealthBarFormatter.render(status(300, entry.getKey(), 0).build());
            assertTrue(rendered.startsWith("目标 &7(" + entry.getValue() + "&7/&c300.00&7) "), rendered);
        }
    }

    @Test
    void displaysTheRequestedFullHealthNumbersAfterTheName() {
        String rendered = CombatHealthBarFormatter.render(status(5000, 5000, 0).build());
        assertTrue(rendered.startsWith("目标 &7(&a5000.00&7/&c5000.00&7) "), rendered);
    }

    private static CombatHealthBarStatus.Builder status(double maximum, double remaining, double damage) {
        return CombatHealthBarStatus.builder()
                .targetUniqueId(UUID.randomUUID())
                .targetDisplayName("目标 ")
                .maxHealth(maximum)
                .remainingHealth(remaining)
                .healthDamage(damage)
                .expiresAtMillis(1);
    }
}
