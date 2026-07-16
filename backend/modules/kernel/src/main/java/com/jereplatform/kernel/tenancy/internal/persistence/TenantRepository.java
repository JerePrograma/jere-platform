package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<TenantEntity> findByCodeIgnoreCaseAndStatus(String code, LifecycleStatus status);
}
