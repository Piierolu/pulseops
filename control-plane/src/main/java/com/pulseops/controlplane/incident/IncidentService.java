package com.pulseops.controlplane.incident;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IncidentService {

    private final IncidentRepository repository;

    IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    List<IncidentResponse> findAll(UUID projectId, IncidentStatus status) {
        List<Incident> incidents = status == null
                ? repository.findAllByProjectId(projectId)
                : repository.findAllByProjectIdAndStatus(projectId, status);
        return incidents.stream().map(Incident::toResponse).toList();
    }
}
