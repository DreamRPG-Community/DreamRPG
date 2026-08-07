package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;

/**
 * Shared coin precision constants kept package-private so callers use CoinService's domain rules.
 */
final class CoinValues {

    static final BigDecimal ZERO = new BigDecimal("0.00");
    static final BigDecimal MAXIMUM = new BigDecimal("99999999999999999.99");

    private CoinValues() {
    }
}
