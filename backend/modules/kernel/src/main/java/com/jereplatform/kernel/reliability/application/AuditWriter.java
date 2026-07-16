package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.ReliableCommand;
import com.jereplatform.kernel.reliability.internal.SafeMetadata;
import com.jereplatform.kernel.reliability.internal.persistence.AuditStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditWriter {

    private final AuditStore auditStore;
    private final SafeMetadata safeMetadata;
    private final Clock clock;

    public AuditWriter(AuditStore auditStore, SafeMetadata safeMetadata, Clock clock) {
        this.auditStore = auditStore;
        this.safeMetadata = safeMetadata;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void success(TenantContext context, ReliableCommand command) {
        write(context, command, "SUCCESS", null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replay(TenantContext context, ReliableCommand command) {
        write(context, command, "REPLAY", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(
        TenantContext context,
        ReliableCommand command,
        Throwable failure
    ) {
        write(context, command, "FAILURE", safeMetadata.failureCode(failure));
    }

    private void write(
        TenantContext context,
        ReliableCommand command,
        String result,
        String failureCode
    ) {
        auditStore.insert(
            context,
            command.actionCode(),
            command.targetType(),
            command.targetId(),
            result,
            failureCode,
            safeMetadata.sanitize(command.auditMetadata()),
            clock.instant()
        );
    }
}
