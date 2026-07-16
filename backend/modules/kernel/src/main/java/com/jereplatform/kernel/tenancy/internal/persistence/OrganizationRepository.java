package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    Optional<OrganizationEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
