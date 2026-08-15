package com.pulseops.controlplane.incident;

import com.pulseops.controlplane.organization.ProjectAccessService;
import com.pulseops.controlplane.organization.TeamRole;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/incidents")
class IncidentController {

    private final IncidentService service;
    private final ProjectAccessService access;

    IncidentController(IncidentService service, ProjectAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    List<IncidentResponse> findAll(
            @PathVariable UUID projectId,
            @RequestParam(required = false) IncidentStatus status
    ) {
        access.requireProject(projectId, TeamRole.VIEWER);
        return service.findAll(projectId, status);
    }
}
