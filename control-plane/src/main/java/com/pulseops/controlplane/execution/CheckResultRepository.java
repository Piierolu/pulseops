package com.pulseops.controlplane.execution;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface CheckResultRepository extends JpaRepository<CheckResult, CheckResultId> {

    List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(java.util.UUID monitorId, Pageable pageable);
}
