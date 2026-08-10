package cn.mythicland.dreamrpg.economy;

import cn.mythicland.dreamrpg.api.CoinService;
import cn.mythicland.dreamrpg.api.CoinTransaction;
import cn.mythicland.dreamrpg.database.CoinStore;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cached facade over the persistent DreamRPG coin ledger.
 */
@InjectComponent
@ServiceComponent(CoinService.class)
public final class CoinAccountService implements CoinService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CoinStore store;
    private final ConcurrentHashMap<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();

    /**
     * Creates the coin service.
     *
     * @param store persistent coin store
     */
    public CoinAccountService(CoinStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    private static CoinTransaction validateRequest(
            UUID uniqueId,
            CoinTransaction.Type type,
            BigDecimal amount,
            String source
    ) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        return new CoinTransaction(uniqueId, type, amount, ZERO, source);
    }

    private static BigDecimal validateAmount(UUID uniqueId, BigDecimal amount) {
        return validateRequest(uniqueId, CoinTransaction.Type.WITHDRAW, amount, "has").amount();
    }

    @Override
    public BigDecimal balance(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        synchronized (lockFor(uniqueId)) {
            return balances.computeIfAbsent(uniqueId, this::loadBalance);
        }
    }

    @Override
    public boolean has(UUID uniqueId, BigDecimal amount) {
        BigDecimal normalizedAmount = validateAmount(uniqueId, amount);
        return balance(uniqueId).compareTo(normalizedAmount) >= 0;
    }

    @Override
    public CoinTransaction deposit(UUID uniqueId, BigDecimal amount, String source) {
        CoinTransaction request = validateRequest(uniqueId, CoinTransaction.Type.DEPOSIT, amount, source);
        synchronized (lockFor(uniqueId)) {
            BigDecimal next = adjust(uniqueId, request.amount());
            balances.put(uniqueId, next);
            return new CoinTransaction(uniqueId, request.type(), request.amount(), next, request.source());
        }
    }

    @Override
    public CoinTransaction withdraw(UUID uniqueId, BigDecimal amount, String source) {
        CoinTransaction request = validateRequest(uniqueId, CoinTransaction.Type.WITHDRAW, amount, source);
        synchronized (lockFor(uniqueId)) {
            BigDecimal next = adjust(uniqueId, request.amount().negate());
            balances.put(uniqueId, next);
            return new CoinTransaction(uniqueId, request.type(), request.amount(), next, request.source());
        }
    }

    private BigDecimal adjust(UUID uniqueId, BigDecimal delta) {
        try {
            return store.adjust(uniqueId, delta);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist DreamRPG coin balance: " + uniqueId, exception);
        }
    }

    private BigDecimal loadBalance(UUID uniqueId) {
        try {
            return store.loadOrCreate(uniqueId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load DreamRPG coin balance: " + uniqueId, exception);
        }
    }

    private Object lockFor(UUID uniqueId) {
        return locks.computeIfAbsent(uniqueId, ignored -> new Object());
    }
}
