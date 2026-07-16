package com.jereplatform.kernel.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

    List<RefreshTokenEntity> findAllByFamilyIdAndStatus(
        UUID familyId,
        RefreshTokenStatus status
    );

    @Modifying
    @Query("""
        update RefreshTokenEntity token
           set token.status = com.jereplatform.kernel.identity.internal.persistence.RefreshTokenStatus.REVOKED
         where token.familyId = :familyId
           and token.status = com.jereplatform.kernel.identity.internal.persistence.RefreshTokenStatus.ACTIVE
        """)
    int revokeAllActiveByFamilyId(@Param("familyId") UUID familyId);
}
