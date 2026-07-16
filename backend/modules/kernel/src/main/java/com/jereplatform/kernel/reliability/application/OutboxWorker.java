package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.OutboxDispatcher;
import com.jereplatform.kernel.reliability.api.OutboxMessage;
import com.jereplatform.kernel.reliability.api.OutboxRunResult;
import com.jereplatform.kernel.reliability.internal.SafeMetadata;
import com.jereplatform.kernel.reliability.internal.persistence.OutboxStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutboxWorker {

    private static final Duration MAXIMUM_BACKOFF = Duration.ofHours(1);

    private final OutboxStore outboxStore;
    private final SafeMetadata safeMetadata;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Counter dispatchedCounter;
    private final Counter retriedCounter;
    private final Counter deadCounter;

    public OutboxWorker(
        OutboxStore outboxStore,
        SafeMetadata safeMetadata,
        Clock clock,
        PlatformTransactionManager transactionManager,
        MeterRegistry meterRegistry
    ) {
        this.outboxStore = outboxStore;
        this.safeMetadata = safeMetadata;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.dispatchedCounter = Counter.builder("jere.outbox.dispatched")
            .description("Successfully dispatched outbox events")
            .register(meterRegistry);
        this.retriedCounter = Counter.builder("jere.outbox.retried")
            .description("Outbox events scheduled for another attempt")
            .register(meterRegistry);
        this.deadCounter = Counter.builder("jere.outbox.dead")
            .description("Outbox events moved to terminal failure")
            .register(meterRegistry);
    }

    public OutboxRunResult runOnce(
        int requestedLimit,
        Duration lease,
        OutboxDispatcher dispatcher
    ) {
        if (requestedLimit < 1 || requestedLimit > 500) {
            throw new IllegalArgumentException("requestedLimit must be between 1 and 500");
        }
        if (lease == null || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }

        var now = clock.instant();
        var recoveredDead = required(transactionTemplate.execute(status ->
            outboxStore.markExhaustedExpiredClaimsDead(now)));
        List<OutboxMessage> claimed = required(transactionTemplate.execute(status ->
            outboxStore.claimBatch(requestedLimit, lease, now)));

        int dispatched = 0;
        int retried = 0;
        int dead = recoveredDead;

        for (var message : claimed) {
            try {
                dispatcher.dispatch(message);
                var marked = required(transactionTemplate.execute(status ->
                    outboxStore.markDispatched(message, clock.instant())));
                if (marked) {
                    dispatched++;
                    dispatchedCounter.increment();
                }
            } catch (Exception failure) {
                var terminal = message.attempt() >= message.maxAttempts();
                var nextAvailable = clock.instant().plus(backoffFor(message.attempt()));
                var marked = required(transactionTemplate.execute(status ->
                    outboxStore.markFailed(
                        message,
                        safeMetadata.failureCode(failure),
                        nextAvailable
                    )));
                if (marked && terminal) {
                    dead++;
                    deadCounter.increment();
                } else if (marked) {
                    retried++;
                    retriedCounter.increment();
                }
            }
        }

        if (recoveredDead > 0) {
            deadCounter.increment(recoveredDead);
        }
        return new OutboxRunResult(claimed.size(), dispatched, retried, dead);
    }

    private static Duration backoffFor(int attempt) {
        var exponent = Math.max(0, Math.min(attempt - 1, 16));
        var seconds = Math.min(MAXIMUM_BACKOFF.toSeconds(), 30L * (1L << exponent));
        return Duration.ofSeconds(seconds);
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw new IllegalStateException("Transaction returned no result");
        }
        return value;
    }
}
