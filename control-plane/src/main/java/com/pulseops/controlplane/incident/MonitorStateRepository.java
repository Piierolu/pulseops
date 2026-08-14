package com.pulseops.controlplane.incident;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MonitorStateRepository extends JpaRepository<MonitorState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from MonitorState state where state.monitorId = :monitorId")
    Optional<MonitorState> findForUpdate(@Param("monitorId") UUID monitorId);
}
