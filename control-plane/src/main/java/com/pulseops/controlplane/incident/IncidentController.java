package com.pulseops.controlplane.incident;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
class IncidentController {

    private final IncidentService service;

    IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    List<IncidentResponse> findAll(@RequestParam(required = false) IncidentStatus status) {
        return service.findAll(status);
    }
}
