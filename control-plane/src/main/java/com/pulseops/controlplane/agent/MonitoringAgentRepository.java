package com.pulseops.controlplane.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MonitoringAgentRepository extends JpaRepository<MonitoringAgent, String> {

    List<MonitoringAgent> findAllByOrderByLastSeenAtDesc();
}
