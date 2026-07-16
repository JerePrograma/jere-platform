package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.OutboxFailureView;
import com.jereplatform.kernel.reliability.api.ReliabilitySummary;
import com.jereplatform.kernel.reliability.internal.persistence.AuditStore;
import com.jereplatform.kernel.reliability.internal.persistence.IdempotencyStore;
import com.jereplatform.kernel.reliability.internal.persistence.OutboxStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReliabilityAdministrationService {

    private final OutboxStore outboxStore;
    private final IdempotencyStore idempotencyStore;
    private final AuditStore auditStore;
    private final Clock clock;

    public ReliabilityAdministrationService(
        OutboxStore outboxStore,
        IdempotencyStore idempotencyStore,
        AuditStore auditStore,
        Clock clock
    ) {
        this.outboxStore = outboxStore;
        this.idempotencyStore = idempotencyStore;
        this.auditStore = auditStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReliabilitySummary summary(TenantContext context) {
        return new ReliabilitySummary(
            outboxStore.countByStatus(context.tenantId().value()),
            idempotencyStore.countInProgress(context.tenantId().value()),
            auditStore.countFailures(context.tenantId().value()),
            outboxStore.oldestDeliverable(context.tenantId().value()).orElse(null)
        );
    }

    @Transactional(readOnly = true)
    public List<OutboxFailureView> deadOutbox(TenantContext context, int requestedLimit) {
        return outboxStore.findDead(
            context.tenantId().value(),
            Math.max(1, Math.min(requestedLimit, 200))
        );
    }

    @Transactional
    public boolean requeueDead(TenantContext context, UUID eventId) {
        return outboxStore.requeueDead(
            context.tenantId().value(),
            eventId,
            clock.instant()
        );
    }
}
