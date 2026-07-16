package com.jereplatform.kernel.tenancy.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipBranchRepository
    extends JpaRepository<MembershipBranchEntity, MembershipBranchId> {

    @Query("""
        select grant
        from MembershipBranchEntity grant
        where grant.id.tenantId = :tenantId
          and grant.id.membershipId = :membershipId
        """)
    List<MembershipBranchEntity> findAllForMembership(
        @Param("tenantId") UUID tenantId,
        @Param("membershipId") UUID membershipId
    );
}
