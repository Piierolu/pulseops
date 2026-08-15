package com.pulseops.controlplane.monitor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MonitorRepository extends JpaRepository<Monitor, UUID> {

    List<Monitor> findAllByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(UUID projectId);

    Optional<Monitor> findByIdAndProjectIdAndArchivedAtIsNull(UUID id, UUID projectId);

    Optional<Monitor> findByIdAndProjectId(UUID id, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Monitor m WHERE m.id = :id AND m.projectId = :projectId AND m.archivedAt IS NULL")
    Optional<Monitor> findActiveByIdAndProjectIdForUpdate(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Monitor m WHERE m.id = :id")
    Optional<Monitor> findByIdForUpdate(@Param("id") UUID id);

    List<Monitor> findAllByEnabledTrueAndArchivedAtIsNull();
}
