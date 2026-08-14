package com.pulseops.controlplane.agent;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
class AgentController {

    private final AgentService service;

    AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    List<AgentResponse> findAll() {
        return service.findAll();
    }
}
