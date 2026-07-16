package com.jereplatform.kernel.tenancy.application;

import com.jereplatform.kernel.tenancy.api.BranchSummary;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import com.jereplatform.kernel.tenancy.internal.persistence.BranchRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BranchDirectory {

    private final BranchRepository branchRepository;

    public BranchDirectory(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<BranchSummary> listAccessible(TenantContext context) {
        if (context.branchIds().isEmpty()) {
            return List.of();
        }

        var ids = context.branchIds().stream().map(id -> id.value()).toList();
        return branchRepository
            .findAllByTenantIdAndIdInAndStatusOrderByDisplayNameAsc(
                context.tenantId().value(),
                ids,
                LifecycleStatus.ACTIVE
            )
            .stream()
            .map(branch -> new BranchSummary(
                new com.jereplatform.kernel.tenancy.api.BranchId(branch.getId()),
                new OrganizationId(branch.getOrganizationId()),
                branch.getCode(),
                branch.getDisplayName()
            ))
            .toList();
    }
}
