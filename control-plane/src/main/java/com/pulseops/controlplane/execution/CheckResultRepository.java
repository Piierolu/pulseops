package com.pulseops.controlplane.execution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

    boolean existsByExecutionId(UUID executionId);

    List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(UUID monitorId, Pageable pageable);
}
