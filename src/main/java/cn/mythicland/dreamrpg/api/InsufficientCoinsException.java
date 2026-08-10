package cn.mythicland.dreamrpg.api;

import java.math.BigDecimal;
import java.io.Serial;
import java.util.UUID;

/**
 * Indicates that a player's balance cannot cover one requested withdrawal.
 */
public final class InsufficientCoinsException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates an insufficient-balance failure with the relevant business values.
     *
     * @param uniqueId player UUID
     * @param requested requested withdrawal
     * @param balance current balance
     */
    public InsufficientCoinsException(UUID uniqueId, BigDecimal requested, BigDecimal balance) {
        super("Insufficient DreamRPG coins for " + uniqueId
                + ": requested " + requested + ", balance " + balance);
    }
}
