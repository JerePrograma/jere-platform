package com.jereplatform.kernel.tenancy.application;

import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.TenantAccessDeniedException;
import com.jereplatform.kernel.tenancy.api.TenantContext;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.internal.persistence.BranchRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipBranchRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.TenantRepository;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantAccessService {

    private static final String ACCESS_DENIED =
        "Authenticated identity is not authorized for the selected tenant";

    private final IdentityRepository identityRepository;
    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipBranchRepository membershipBranchRepository;
    private final BranchRepository branchRepository;

    public TenantAccessService(
        IdentityRepository identityRepository,
        TenantRepository tenantRepository,
        MembershipRepository membershipRepository,
        MembershipBranchRepository membershipBranchRepository,
        BranchRepository branchRepository
    ) {
        this.identityRepository = identityRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.membershipBranchRepository = membershipBranchRepository;
        this.branchRepository = branchRepository;
    }

    public TenantContext activate(
        String authenticatedSubject,
        String requestedTenantCode,
        UUID correlationId
    ) {
        var subject = normalize(authenticatedSubject);
        var tenantCode = normalize(requestedTenantCode);
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        var identity = identityRepository
            .findByExternalSubjectIgnoreCaseAndStatus(subject, LifecycleStatus.ACTIVE)
            .orElseThrow(TenantAccessService::accessDenied);

        var tenant = tenantRepository
            .findByCodeIgnoreCaseAndStatus(tenantCode, LifecycleStatus.ACTIVE)
            .orElseThrow(TenantAccessService::accessDenied);

        var membership = membershipRepository
            .findByTenantIdAndIdentityIdAndStatus(
                tenant.getId(),
                identity.getId(),
                LifecycleStatus.ACTIVE
            )
            .orElseThrow(TenantAccessService::accessDenied);

        var grantedBranchIds = membershipBranchRepository
            .findAllForMembership(tenant.getId(), membership.getId())
            .stream()
            .map(grant -> grant.getId().getBranchId())
            .collect(Collectors.toSet());

        Set<BranchId> activeBranches;
        if (grantedBranchIds.isEmpty()) {
            activeBranches = Set.of();
        } else {
            activeBranches = branchRepository
                .findAllByTenantIdAndIdInAndStatusOrderByDisplayNameAsc(
                    tenant.getId(),
                    grantedBranchIds,
                    LifecycleStatus.ACTIVE
                )
                .stream()
                .map(branch -> new BranchId(branch.getId()))
                .collect(Collectors.toUnmodifiableSet());
        }

        return new TenantContext(
            new TenantId(tenant.getId()),
            new IdentityId(identity.getId()),
            new MembershipId(membership.getId()),
            activeBranches,
            correlationId
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw accessDenied();
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static TenantAccessDeniedException accessDenied() {
        return new TenantAccessDeniedException(ACCESS_DENIED);
    }
}
