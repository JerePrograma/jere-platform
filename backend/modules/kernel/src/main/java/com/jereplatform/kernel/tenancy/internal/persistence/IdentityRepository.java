package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<IdentityEntity, UUID> {

    boolean existsByExternalSubjectIgnoreCase(String externalSubject);

    Optional<IdentityEntity> findByExternalSubjectIgnoreCaseAndStatus(
        String externalSubject,
        LifecycleStatus status
    );
}
