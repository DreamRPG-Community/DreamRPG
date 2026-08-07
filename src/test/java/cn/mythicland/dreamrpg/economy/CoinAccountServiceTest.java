package cn.mythicland.dreamrpg.economy;

import cn.mythicland.dreamrpg.api.CoinTransaction;
import cn.mythicland.dreamrpg.api.InsufficientCoinsException;
import cn.mythicland.dreamrpg.database.CoinStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the DreamRPG coin service transaction semantics independently of JDBC.
 */
class CoinAccountServiceTest {

    @Test
    void newPlayerStartsAtZeroAndTransactionsReturnUpdatedBalances() {
        InMemoryCoinStore store = new InMemoryCoinStore();
        CoinAccountService service = new CoinAccountService(store);
        UUID uniqueId = UUID.randomUUID();

        assertEquals(new BigDecimal("0.00"), service.balance(uniqueId));

        CoinTransaction deposit = service.deposit(uniqueId, new BigDecimal("12.50"), "test");
        CoinTransaction withdrawal = service.withdraw(uniqueId, new BigDecimal("2.25"), "test");

        assertEquals(new BigDecimal("12.50"), deposit.balance());
        assertEquals(new BigDecimal("10.25"), withdrawal.balance());
        assertEquals(new BigDecimal("10.25"), service.balance(uniqueId));
    }

    @Test
    void insufficientWithdrawalLeavesThePersistedBalanceUnchanged() {
        InMemoryCoinStore store = new InMemoryCoinStore();
        CoinAccountService service = new CoinAccountService(store);
        UUID uniqueId = UUID.randomUUID();
        service.deposit(uniqueId, new BigDecimal("3.00"), "test");

        assertThrows(
                InsufficientCoinsException.class,
                () -> service.withdraw(uniqueId, new BigDecimal("3.01"), "test")
        );
        assertEquals(new BigDecimal("3.00"), service.balance(uniqueId));
    }

    private static final class InMemoryCoinStore implements CoinStore {

        private final Map<UUID, BigDecimal> balances = new HashMap<>();

        @Override
        public BigDecimal loadOrCreate(UUID uniqueId) {
            return balances.computeIfAbsent(uniqueId, ignored -> new BigDecimal("0.00"));
        }

        @Override
        public BigDecimal adjust(UUID uniqueId, BigDecimal delta) throws InsufficientCoinsException {
            BigDecimal current = loadOrCreate(uniqueId);
            BigDecimal next = current.add(delta).setScale(2);
            if (next.signum() < 0) {
                throw new InsufficientCoinsException(uniqueId, delta.negate(), current);
            }
            balances.put(uniqueId, next);
            return next;
        }
    }
}
