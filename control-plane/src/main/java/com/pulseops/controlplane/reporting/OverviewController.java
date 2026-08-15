package com.pulseops.controlplane.reporting;

import com.pulseops.controlplane.organization.ProjectAccessService;
import com.pulseops.controlplane.organization.TeamRole;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/overview")
class OverviewController {

    private final OverviewService service;
    private final ProjectAccessService access;

    OverviewController(OverviewService service, ProjectAccessService access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    OverviewResponse getOverview(@PathVariable UUID projectId) {
        access.requireProject(projectId, TeamRole.VIEWER);
        return service.getOverview(projectId);
    }
}
