package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.reliability.api.IdempotencyInProgressException;
import com.jereplatform.kernel.reliability.api.ReliableCommand;
import com.jereplatform.kernel.reliability.api.ReliableExecutionResult;
import com.jereplatform.kernel.reliability.internal.ReliabilityHashing;
import com.jereplatform.kernel.reliability.internal.ReliabilityJson;
import com.jereplatform.kernel.reliability.internal.persistence.IdempotencyStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReliableCommandExecutor {

    private static final Duration OWNERSHIP_LEASE = Duration.ofMinutes(5);

    private final IdempotencyStore idempotencyStore;
    private final AuditWriter auditWriter;
    private final ReliabilityHashing hashing;
    private final ReliabilityJson reliabilityJson;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Counter executedCounter;
    private final Counter replayedCounter;
    private final Counter conflictedCounter;

    public ReliableCommandExecutor(
        IdempotencyStore idempotencyStore,
        AuditWriter auditWriter,
        ReliabilityHashing hashing,
        ReliabilityJson reliabilityJson,
        Clock clock,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry
    ) {
        this.idempotencyStore = idempotencyStore;
        this.auditWriter = auditWriter;
        this.hashing = hashing;
        this.reliabilityJson = reliabilityJson;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.executedCounter = Counter.builder("jere.idempotency.executed")
            .description("Idempotent commands that executed their business effect")
            .register(meterRegistry);
        this.replayedCounter = Counter.builder("jere.idempotency.replayed")
            .description("Completed idempotent responses replayed")
            .register(meterRegistry);
        this.conflictedCounter = Counter.builder("jere.idempotency.conflicted")
            .description("Idempotency key reuse with different request content")
            .register(meterRegistry);
    }

    public <T> ReliableExecutionResult<T> execute(
        TenantContext context,
        ReliableCommand command,
        Class<T> responseType,
        Supplier<T> businessEffect
    ) {
        if (context == null || command == null || responseType == null || businessEffect == null) {
            throw new IllegalArgumentException("Execution arguments must not be null");
        }

        try {
            var result = transactionTemplate.execute(status -> executeInsideTransaction(
                context,
                command,
                responseType,
                businessEffect
            ));
            if (result == null) {
                throw new IllegalStateException("Reliable command transaction returned no result");
            }
            return result;
        } catch (RuntimeException failure) {
            try {
                auditWriter.failure(context, command, failure);
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
    }

    private <T> ReliableExecutionResult<T> executeInsideTransaction(
        TenantContext context,
        ReliableCommand command,
        Class<T> responseType,
        Supplier<T> businessEffect
    ) {
        var now = clock.instant();
        var ownerToken = UUID.randomUUID();
        var keyHash = hashing.hashText(command.idempotencyKey());
        var requestHash = hashing.hashBytes(command.requestBytes());

        idempotencyStore.ensureRecord(
            context.tenantId().value(),
            command.operationCode(),
            keyHash,
            requestHash,
            ownerToken,
            now.plus(OWNERSHIP_LEASE),
            now,
            now.plus(command.idempotencyRetention())
        );

        var record = idempotencyStore.lockRecord(
            context.tenantId().value(),
            command.operationCode(),
            keyHash
        ).orElseThrow(() -> new IllegalStateException("Idempotency record disappeared"));

        if (!record.requestHash().equals(requestHash)) {
            conflictedCounter.increment();
            throw new IdempotencyConflictException();
        }

        if ("COMPLETED".equals(record.status())) {
            if (record.responseType() == null || !record.responseType().equals(responseType.getName())) {
                throw new IdempotencyConflictException();
            }
            var replay = reliabilityJson.read(record.responseJson(), responseType);
            auditWriter.replay(context, command);
            replayedCounter.increment();
            return new ReliableExecutionResult<>(replay, true);
        }

        if (!record.ownerToken().equals(ownerToken)) {
            if (record.lockedUntil().isAfter(now)) {
                throw new IdempotencyInProgressException();
            }
            idempotencyStore.takeOwnership(
                context.tenantId().value(),
                command.operationCode(),
                keyHash,
                ownerToken,
                now.plus(OWNERSHIP_LEASE),
                now
            );
        }

        var value = businessEffect.get();
        var responseJson = reliabilityJson.write(value);
        auditWriter.success(context, command);
        idempotencyStore.complete(
            context.tenantId().value(),
            command.operationCode(),
            keyHash,
            ownerToken,
            responseType.getName(),
            responseJson,
            clock.instant()
        );
        executedCounter.increment();
        return new ReliableExecutionResult<>(value, false);
    }
}
