package com.jereplatform.kernel.identity.internal.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BootstrapMarkerRepository extends JpaRepository<BootstrapMarkerEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select marker from BootstrapMarkerEntity marker where marker.id = 1")
    Optional<BootstrapMarkerEntity> lockSingleton();
}
