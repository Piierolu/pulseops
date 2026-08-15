package com.pulseops.controlplane.execution;

import com.pulseops.controlplane.monitor.MonitorService;
import com.pulseops.controlplane.organization.ProjectAccessService;
import com.pulseops.controlplane.organization.TeamRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/projects/{projectId}/monitors/{monitorId}/results")
class CheckResultController {

    private final CheckResultService service;
    private final MonitorService monitors;
    private final ProjectAccessService access;

    CheckResultController(CheckResultService service, MonitorService monitors, ProjectAccessService access) {
        this.service = service;
        this.monitors = monitors;
        this.access = access;
    }

    @GetMapping
    List<CheckResultResponse> findRecent(
            @PathVariable UUID projectId,
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        access.requireProject(projectId, TeamRole.VIEWER);
        monitors.requireProjectHistory(projectId, monitorId);
        return service.findRecent(monitorId, limit);
    }
}
