package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<BranchEntity, UUID> {

    boolean existsByTenantIdAndOrganizationIdAndCodeIgnoreCase(
        UUID tenantId,
        UUID organizationId,
        String code
    );

    Optional<BranchEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<BranchEntity> findAllByTenantIdAndIdInAndStatusOrderByDisplayNameAsc(
        UUID tenantId,
        Collection<UUID> ids,
        LifecycleStatus status
    );
}
