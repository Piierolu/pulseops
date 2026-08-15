package com.pulseops.controlplane.monitor;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.pulseops.controlplane.organization.ProjectAccessService;
import com.pulseops.controlplane.organization.TeamRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/monitors")
public class MonitorController {

    private final MonitorService service;
    private final ProjectAccessService access;

    public MonitorController(MonitorService service, ProjectAccessService access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<MonitorResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMonitorRequest request
    ) {
        access.requireProject(projectId, TeamRole.EDITOR);
        MonitorResponse created = service.create(projectId, request);
        return ResponseEntity.created(URI.create(
                "/api/projects/" + projectId + "/monitors/" + created.id()
        )).body(created);
    }

    @GetMapping
    public List<MonitorResponse> findAll(@PathVariable UUID projectId) {
        access.requireProject(projectId, TeamRole.VIEWER);
        return service.findAll(projectId);
    }

    @GetMapping("/{id}")
    public MonitorResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        access.requireProject(projectId, TeamRole.VIEWER);
        return service.findById(projectId, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
        access.requireProject(projectId, TeamRole.EDITOR);
        service.archive(projectId, id);
        return ResponseEntity.noContent().build();
    }
}
