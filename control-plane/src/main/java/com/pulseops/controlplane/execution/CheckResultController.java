package com.pulseops.controlplane.execution;

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
@RequestMapping("/api/monitors/{monitorId}/results")
class CheckResultController {

    private final CheckResultService service;

    CheckResultController(CheckResultService service) {
        this.service = service;
    }

    @GetMapping
    List<CheckResultResponse> findRecent(
            @PathVariable UUID monitorId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit
    ) {
        return service.findRecent(monitorId, limit);
    }
}
