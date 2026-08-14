package com.pulseops.controlplane.incident;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IncidentService {

    private final IncidentRepository repository;

    IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    List<IncidentResponse> findAll(IncidentStatus status) {
        List<Incident> incidents = status == null
                ? repository.findAllByOrderByOpenedAtDesc()
                : repository.findAllByStatusOrderByOpenedAtDesc(status);
        return incidents.stream().map(Incident::toResponse).toList();
    }
}
