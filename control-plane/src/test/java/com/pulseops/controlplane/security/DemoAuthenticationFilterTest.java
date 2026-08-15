package com.pulseops.controlplane.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulseops.controlplane.identity.DemoPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class DemoAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void alwaysAuthenticatesTheFixedDemoIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.addHeader("X-User", "attacker");

        new DemoAuthenticationFilter().doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> {
                    DemoPrincipal principal = (DemoPrincipal) SecurityContextHolder.getContext()
                            .getAuthentication().getPrincipal();
                    assertThat(principal.issuer()).isEqualTo("urn:pulseops:demo");
                    assertThat(principal.subject()).isEqualTo("demo-user");
                }
        );
    }
}
