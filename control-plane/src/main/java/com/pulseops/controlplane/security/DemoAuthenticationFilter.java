package com.pulseops.controlplane.security;

import com.pulseops.controlplane.identity.DemoPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class DemoAuthenticationFilter extends OncePerRequestFilter {

    static final String ISSUER = "urn:pulseops:demo";
    static final String SUBJECT = "demo-user";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        DemoPrincipal principal = new DemoPrincipal(ISSUER, SUBJECT);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())
        );
        filterChain.doFilter(request, response);
    }
}
