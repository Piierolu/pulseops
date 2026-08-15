package com.pulseops.controlplane.incident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findFirstByMonitorIdAndStatus(UUID monitorId, IncidentStatus status);

    @Query("""
            SELECT i FROM Incident i, Monitor m
            WHERE i.monitorId = m.id AND m.projectId = :projectId AND m.archivedAt IS NULL
            ORDER BY i.openedAt DESC
            """)
    List<Incident> findAllByProjectId(@Param("projectId") UUID projectId);

    @Query("""
            SELECT i FROM Incident i, Monitor m
            WHERE i.monitorId = m.id AND m.projectId = :projectId
                AND m.archivedAt IS NULL AND i.status = :status
            ORDER BY i.openedAt DESC
            """)
    List<Incident> findAllByProjectIdAndStatus(
            @Param("projectId") UUID projectId,
            @Param("status") IncidentStatus status
    );
}
