package com.example.spamer.user.service;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ConflictException;
import com.example.spamer.user.dto.BalanceResponse;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Balance steps of the /spam/send saga. A caller may not know whether a timed-out
 * debit was applied, so both operations are idempotent by operationId and a debit
 * can always be followed by a refund:
 * - a repeated debit or refund changes nothing;
 * - a refund for an unknown operation records a zero-amount REFUNDED row, so a
 *   debit that arrives late is ignored too and no money moves.
 *
 * Why transactions: the journal row and the balance must change together,
 * otherwise an operation can be recorded without the money moving, or the reverse.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final String CHARGED = "CHARGED";
    private static final String REFUNDED = "REFUNDED";

    private final DatabaseClient db;
    private final UserService users;

    @Transactional
    public Mono<BalanceResponse> debit(UUID userId, UUID operationId, BigDecimal amount) {
        return users.find(userId)
                .then(recordOperation(operationId, userId, amount, CHARGED))
                .flatMap(isNew -> isNew ? charge(userId, amount) : checkRepeatedDebit(operationId))
                .then(balanceOf(userId));
    }

    @Transactional
    public Mono<BalanceResponse> refund(UUID userId, UUID operationId) {
        return users.find(userId)
                .then(recordOperation(operationId, userId, BigDecimal.ZERO, REFUNDED))
                .flatMap(isNew -> isNew ? Mono.<Void>empty() : returnMoney(userId, operationId))
                .then(balanceOf(userId));
    }

    private Mono<Boolean> recordOperation(
            UUID operationId, UUID userId, BigDecimal amount, String status) {
        return db.sql("""
                        INSERT INTO balance_operations (id, user_id, amount, status, created_at)
                        VALUES (:id, :userId, :amount, :status, now())
                        ON CONFLICT (id) DO NOTHING
                        """)
                .bind("id", operationId)
                .bind("userId", userId)
                .bind("amount", amount)
                .bind("status", status)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    // The WHERE clause makes the check and the write atomic under concurrent sends.
    private Mono<Void> charge(UUID userId, BigDecimal amount) {
        return db.sql("""
                        UPDATE users SET balance = balance - :amount, updated_at = now()
                        WHERE id = :userId AND balance >= :amount
                        """)
                .bind("amount", amount)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(rows -> rows > 0
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new BusinessException(
                                "Insufficient balance: need " + amount)));
    }

    private Mono<Void> checkRepeatedDebit(UUID operationId) {
        return db.sql("SELECT status FROM balance_operations WHERE id = :id")
                .bind("id", operationId)
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .flatMap(status -> CHARGED.equals(status)
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new ConflictException(
                                "Operation already refunded: " + operationId)));
    }

    private Mono<Void> returnMoney(UUID userId, UUID operationId) {
        return db.sql("""
                        UPDATE balance_operations SET status = 'REFUNDED'
                        WHERE id = :id AND user_id = :userId AND status = 'CHARGED'
                        """)
                .bind("id", operationId)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .flatMap(rows -> rows > 0 ? addOperationAmount(userId, operationId) : Mono.<Void>empty());
    }

    private Mono<Void> addOperationAmount(UUID userId, UUID operationId) {
        return db.sql("""
                        UPDATE users
                        SET balance = balance + (SELECT amount FROM balance_operations WHERE id = :id),
                            updated_at = now()
                        WHERE id = :userId
                        """)
                .bind("id", operationId)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .then();
    }

    private Mono<BalanceResponse> balanceOf(UUID userId) {
        return users.find(userId).map(u -> new BalanceResponse(u.getBalance()));
    }
}
