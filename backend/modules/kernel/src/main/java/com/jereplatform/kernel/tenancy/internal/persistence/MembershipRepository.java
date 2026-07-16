package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {

    boolean existsByTenantIdAndIdentityId(UUID tenantId, UUID identityId);

    Optional<MembershipEntity> findByTenantIdAndIdentityIdAndStatus(
        UUID tenantId,
        UUID identityId,
        LifecycleStatus status
    );

    Optional<MembershipEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
