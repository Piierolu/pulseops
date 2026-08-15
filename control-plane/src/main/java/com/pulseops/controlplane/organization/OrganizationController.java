package com.pulseops.controlplane.organization;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class OrganizationController {

    private final OrganizationService organizations;

    OrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @GetMapping("/projects")
    List<ProjectResponse> findProjects() {
        return organizations.findAccessibleProjects();
    }

    @GetMapping("/teams")
    List<TeamResponse> findTeams() {
        return organizations.findTeams();
    }

    @PostMapping("/teams")
    ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateOrganizationRequest request) {
        TeamResponse created = organizations.createTeam(request);
        return ResponseEntity.created(URI.create("/api/teams/" + created.id())).body(created);
    }

    @GetMapping("/teams/{teamId}/projects")
    List<ProjectResponse> findTeamProjects(@PathVariable UUID teamId) {
        return organizations.findTeamProjects(teamId);
    }

    @PostMapping("/teams/{teamId}/projects")
    ResponseEntity<ProjectResponse> createProject(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        ProjectResponse created = organizations.createProject(teamId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + created.id())).body(created);
    }
}
