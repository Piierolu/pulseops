package com.pulseops.controlplane.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class IdentityController {

    private final IdentityService identities;

    IdentityController(IdentityService identities) {
        this.identities = identities;
    }

    @GetMapping
    CurrentUser me() {
        return identities.currentUser();
    }
}
