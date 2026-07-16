package com.jereplatform.kernel.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionFamilyRepository extends JpaRepository<SessionFamilyEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select family from SessionFamilyEntity family where family.id = :id")
    Optional<SessionFamilyEntity> findLockedById(@Param("id") UUID id);

    List<SessionFamilyEntity> findAllByIdentityIdAndStatus(
        UUID identityId,
        SessionFamilyStatus status
    );
}
