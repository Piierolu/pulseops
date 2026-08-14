package com.pulseops.controlplane.incident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findFirstByMonitorIdAndStatus(UUID monitorId, IncidentStatus status);

    List<Incident> findAllByOrderByOpenedAtDesc();

    List<Incident> findAllByStatusOrderByOpenedAtDesc(IncidentStatus status);
}
