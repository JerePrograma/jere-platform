package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.OutboxEventDraft;
import com.jereplatform.kernel.reliability.internal.ReliabilityJson;
import com.jereplatform.kernel.reliability.internal.SafeMetadata;
import com.jereplatform.kernel.reliability.internal.persistence.OutboxStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxStore outboxStore;
    private final ReliabilityJson reliabilityJson;
    private final SafeMetadata safeMetadata;
    private final Clock clock;

    public OutboxService(
        OutboxStore outboxStore,
        ReliabilityJson reliabilityJson,
        SafeMetadata safeMetadata,
        Clock clock
    ) {
        this.outboxStore = outboxStore;
        this.reliabilityJson = reliabilityJson;
        this.safeMetadata = safeMetadata;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(TenantContext context, OutboxEventDraft draft) {
        return outboxStore.insert(
            context.tenantId().value(),
            draft.aggregateType(),
            draft.aggregateId(),
            draft.eventType(),
            reliabilityJson.write(draft.payload()),
            safeMetadata.sanitize(draft.headers()),
            draft.maxAttempts(),
            clock.instant()
        );
    }
}
