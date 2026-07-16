package com.jereplatform.kernel.tenancy.application;

import com.jereplatform.kernel.tenancy.api.BranchId;
import com.jereplatform.kernel.tenancy.api.IdentityId;
import com.jereplatform.kernel.tenancy.api.MembershipId;
import com.jereplatform.kernel.tenancy.api.OrganizationId;
import com.jereplatform.kernel.tenancy.api.TenantConflictException;
import com.jereplatform.kernel.tenancy.api.TenantId;
import com.jereplatform.kernel.tenancy.internal.persistence.BranchEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.BranchRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.IdentityRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.LifecycleStatus;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipBranchEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipBranchId;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipBranchRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.MembershipRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.OrganizationEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.OrganizationRepository;
import com.jereplatform.kernel.tenancy.internal.persistence.TenantEntity;
import com.jereplatform.kernel.tenancy.internal.persistence.TenantRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantProvisioningService {

    private static final Pattern CODE_PATTERN =
        Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?$");

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final IdentityRepository identityRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipBranchRepository membershipBranchRepository;

    public TenantProvisioningService(
        TenantRepository tenantRepository,
        OrganizationRepository organizationRepository,
        BranchRepository branchRepository,
        IdentityRepository identityRepository,
        MembershipRepository membershipRepository,
        MembershipBranchRepository membershipBranchRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
        this.branchRepository = branchRepository;
        this.identityRepository = identityRepository;
        this.membershipRepository = membershipRepository;
        this.membershipBranchRepository = membershipBranchRepository;
    }

    public TenantId createTenant(String code, String displayName) {
        var normalizedCode = normalizeCode(code);
        if (tenantRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new TenantConflictException("Tenant code already exists");
        }

        var id = TenantId.random();
        tenantRepository.save(new TenantEntity(
            id.value(),
            normalizedCode,
            requireDisplayName(displayName),
            Instant.now()
        ));
        return id;
    }

    public IdentityId registerIdentity(String externalSubject) {
        var normalizedSubject = normalizeSubject(externalSubject);
        if (identityRepository.existsByExternalSubjectIgnoreCase(normalizedSubject)) {
            throw new TenantConflictException("Identity subject already exists");
        }

        var id = IdentityId.random();
        identityRepository.save(new IdentityEntity(id.value(), normalizedSubject, Instant.now()));
        return id;
    }

    public OrganizationId createOrganization(TenantId tenantId, String code, String displayName) {
        requireActiveTenant(tenantId);
        var normalizedCode = normalizeCode(code);
        if (organizationRepository.existsByTenantIdAndCodeIgnoreCase(tenantId.value(), normalizedCode)) {
            throw new TenantConflictException("Organization code already exists in tenant");
        }

        var id = OrganizationId.random();
        organizationRepository.save(new OrganizationEntity(
            id.value(),
            tenantId.value(),
            normalizedCode,
            requireDisplayName(displayName),
            Instant.now()
        ));
        return id;
    }

    public BranchId createBranch(
        TenantId tenantId,
        OrganizationId organizationId,
        String code,
        String displayName
    ) {
        requireOrganization(tenantId, organizationId);
        var normalizedCode = normalizeCode(code);
        if (branchRepository.existsByTenantIdAndOrganizationIdAndCodeIgnoreCase(
            tenantId.value(),
            organizationId.value(),
            normalizedCode
        )) {
            throw new TenantConflictException("Branch code already exists in organization");
        }

        var id = BranchId.random();
        branchRepository.save(new BranchEntity(
            id.value(),
            tenantId.value(),
            organizationId.value(),
            normalizedCode,
            requireDisplayName(displayName),
            Instant.now()
        ));
        return id;
    }

    public MembershipId createMembership(
        TenantId tenantId,
        IdentityId identityId,
        OrganizationId defaultOrganizationId
    ) {
        requireActiveTenant(tenantId);
        requireActiveIdentity(identityId);
        requireOrganization(tenantId, defaultOrganizationId);

        if (membershipRepository.existsByTenantIdAndIdentityId(tenantId.value(), identityId.value())) {
            throw new TenantConflictException("Membership already exists in tenant");
        }

        var id = MembershipId.random();
        membershipRepository.save(new MembershipEntity(
            id.value(),
            tenantId.value(),
            identityId.value(),
            defaultOrganizationId.value(),
            Instant.now()
        ));
        return id;
    }

    public void grantBranch(TenantId tenantId, MembershipId membershipId, BranchId branchId) {
        requireMembership(tenantId, membershipId);
        requireBranch(tenantId, branchId);

        var id = new MembershipBranchId(tenantId.value(), membershipId.value(), branchId.value());
        if (!membershipBranchRepository.existsById(id)) {
            membershipBranchRepository.save(new MembershipBranchEntity(id, Instant.now()));
        }
    }

    public void revokeMembership(TenantId tenantId, MembershipId membershipId) {
        var membership = requireMembership(tenantId, membershipId);
        membership.revoke(Instant.now());
    }

    private TenantEntity requireActiveTenant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return tenantRepository.findById(tenantId.value())
            .filter(tenant -> tenant.getStatus() == LifecycleStatus.ACTIVE)
            .orElseThrow(() -> new TenantConflictException("Active tenant not found"));
    }

    private IdentityEntity requireActiveIdentity(IdentityId identityId) {
        Objects.requireNonNull(identityId, "identityId must not be null");
        return identityRepository.findById(identityId.value())
            .filter(identity -> identity.getStatus() == LifecycleStatus.ACTIVE)
            .orElseThrow(() -> new TenantConflictException("Active identity not found"));
    }

    private OrganizationEntity requireOrganization(TenantId tenantId, OrganizationId organizationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        return organizationRepository.findByIdAndTenantId(organizationId.value(), tenantId.value())
            .orElseThrow(() -> new TenantConflictException("Organization does not belong to tenant"));
    }

    private BranchEntity requireBranch(TenantId tenantId, BranchId branchId) {
        Objects.requireNonNull(branchId, "branchId must not be null");
        return branchRepository.findByIdAndTenantId(branchId.value(), tenantId.value())
            .orElseThrow(() -> new TenantConflictException("Branch does not belong to tenant"));
    }

    private MembershipEntity requireMembership(TenantId tenantId, MembershipId membershipId) {
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        return membershipRepository.findByIdAndTenantId(membershipId.value(), tenantId.value())
            .orElseThrow(() -> new TenantConflictException("Membership does not belong to tenant"));
    }

    private static String normalizeCode(String value) {
        var normalized = requireText(value, "code").toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "code must contain lowercase letters, numbers or internal hyphens and be at most 80 characters"
            );
        }
        return normalized;
    }

    private static String normalizeSubject(String value) {
        var normalized = requireText(value, "externalSubject").toLowerCase(Locale.ROOT);
        if (normalized.length() > 190) {
            throw new IllegalArgumentException("externalSubject must be at most 190 characters");
        }
        return normalized;
    }

    private static String requireDisplayName(String value) {
        var normalized = requireText(value, "displayName");
        if (normalized.length() > 160) {
            throw new IllegalArgumentException("displayName must be at most 160 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
