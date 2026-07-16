package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.ReliabilityCleanupPolicy;
import com.jereplatform.kernel.reliability.api.ReliabilityCleanupResult;
import com.jereplatform.kernel.reliability.internal.persistence.IdempotencyStore;
import com.jereplatform.kernel.reliability.internal.persistence.OutboxStore;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReliabilityCleanupService {

    private final IdempotencyStore idempotencyStore;
    private final OutboxStore outboxStore;
    private final Clock clock;

    public ReliabilityCleanupService(
        IdempotencyStore idempotencyStore,
        OutboxStore outboxStore,
        Clock clock
    ) {
        this.idempotencyStore = idempotencyStore;
        this.outboxStore = outboxStore;
        this.clock = clock;
    }

    @Transactional
    public ReliabilityCleanupResult cleanup(ReliabilityCleanupPolicy policy) {
        var now = clock.instant();
        return new ReliabilityCleanupResult(
            idempotencyStore.deleteExpiredCompleted(now, policy.batchSize()),
            outboxStore.deleteOldDispatched(
                now.minus(policy.dispatchedOutboxRetention()),
                policy.batchSize()
            )
        );
    }
}
