package com.jereplatform.kernel.reliability.application;

import com.jereplatform.kernel.reliability.api.AuditEventView;
import com.jereplatform.kernel.reliability.internal.persistence.AuditStore;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditStore auditStore;

    public AuditQueryService(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public List<AuditEventView> latest(TenantContext context, int requestedLimit) {
        var limit = Math.max(1, Math.min(requestedLimit, 200));
        return auditStore.findLatest(context.tenantId().value(), limit);
    }
}
