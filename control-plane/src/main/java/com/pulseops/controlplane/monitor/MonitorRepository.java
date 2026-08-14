package com.pulseops.controlplane.monitor;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MonitorRepository extends JpaRepository<Monitor, UUID> {

    List<Monitor> findAllByOrderByCreatedAtDesc();

    List<Monitor> findAllByEnabledTrue();
}
