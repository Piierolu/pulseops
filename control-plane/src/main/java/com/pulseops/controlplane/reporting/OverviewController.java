package com.pulseops.controlplane.reporting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
class OverviewController {

    private final OverviewService service;

    OverviewController(OverviewService service) {
        this.service = service;
    }

    @GetMapping
    OverviewResponse getOverview() {
        return service.getOverview();
    }
}
