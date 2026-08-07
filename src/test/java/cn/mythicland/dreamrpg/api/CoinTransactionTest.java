package cn.mythicland.dreamrpg.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the immutable coin transaction business rules.
 */
class CoinTransactionTest {

    @Test
    void transactionStoresMoneyAtVaultCompatiblePrecision() {
        UUID uniqueId = UUID.randomUUID();

        CoinTransaction transaction = new CoinTransaction(
                uniqueId,
                CoinTransaction.Type.DEPOSIT,
                new BigDecimal("12.50"),
                new BigDecimal("99.90"),
                "QuickShop"
        );

        assertEquals(uniqueId, transaction.uniqueId());
        assertEquals(new BigDecimal("12.50"), transaction.amount());
        assertEquals(new BigDecimal("99.90"), transaction.balance());
    }

    @Test
    void transactionRejectsAmountsWithMoreThanTwoDecimalPlaces() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoinTransaction(
                        UUID.randomUUID(),
                        CoinTransaction.Type.DEPOSIT,
                        new BigDecimal("1.001"),
                        new BigDecimal("0.00"),
                        "test"
                )
        );
    }

    @Test
    void transactionRejectsNonPositiveAmountsAndNegativeBalances() {
        UUID uniqueId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CoinTransaction(
                        uniqueId,
                        CoinTransaction.Type.WITHDRAW,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "test"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CoinTransaction(
                        uniqueId,
                        CoinTransaction.Type.WITHDRAW,
                        BigDecimal.ONE,
                        new BigDecimal("-0.01"),
                        "test"
                )
        );
    }
}
