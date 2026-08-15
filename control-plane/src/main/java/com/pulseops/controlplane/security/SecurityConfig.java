package com.pulseops.controlplane.security;

import com.pulseops.controlplane.identity.CurrentUser;
import com.pulseops.controlplane.identity.IdentityService;
import com.pulseops.controlplane.organization.OrganizationService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    ApplicationRunner validateSecurityMode(@Value("${pulseops.security.mode:oidc}") String mode) {
        return args -> {
            if (!mode.equals("oidc") && !mode.equals("demo")) {
                throw new IllegalStateException("pulseops.security.mode must be 'oidc' or 'demo'");
            }
        };
    }

    private static void common(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());
    }

    @Configuration
    @ConditionalOnProperty(name = "pulseops.security.mode", havingValue = "oidc", matchIfMissing = true)
    static class OidcSecurity {

        @Bean
        SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
            common(http);
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
            return http.build();
        }

        @Bean
        JwtDecoder jwtDecoder(
                @Value("${pulseops.security.oidc.issuer-uri}") String issuer,
                @Value("${pulseops.security.oidc.audience}") String audience
        ) {
            if (issuer.isBlank() || audience.isBlank()) {
                throw new IllegalStateException("OIDC_ISSUER_URI and OIDC_AUDIENCE are required in oidc mode");
            }
            JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
            if (!(decoder instanceof NimbusJwtDecoder nimbus)) {
                throw new IllegalStateException("OIDC issuer did not create a configurable JWT decoder");
            }
            nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)
            ));
            return nimbus;
        }

        @Bean
        ApplicationRunner bootstrapLegacyOwner(
                IdentityService identities,
                OrganizationService organizations,
                @Value("${pulseops.security.oidc.issuer-uri}") String oidcIssuer,
                @Value("${pulseops.security.bootstrap.issuer:}") String issuer,
                @Value("${pulseops.security.bootstrap.subject:}") String subject
        ) {
            return args -> {
                if (issuer.isBlank() || subject.isBlank()) {
                    throw new IllegalStateException("BOOTSTRAP_ISSUER and BOOTSTRAP_SUBJECT are required in oidc mode");
                }
                if (!issuer.equals(oidcIssuer)) {
                    throw new IllegalStateException("BOOTSTRAP_ISSUER must match OIDC_ISSUER_URI");
                }
                organizations.grantLegacyOwner(identities.upsert(issuer, subject, null, null));
            };
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "pulseops.security.mode", havingValue = "demo")
    static class DemoSecurity {

        @Bean
        SecurityFilterChain demoSecurityFilterChain(HttpSecurity http) throws Exception {
            common(http);
            http.addFilterBefore(new DemoAuthenticationFilter(), BearerTokenAuthenticationFilter.class);
            return http.build();
        }

        @Bean
        ApplicationRunner demoIdentityInitializer(IdentityService identities, OrganizationService organizations) {
            return args -> {
                CurrentUser demo = identities.upsert(
                        DemoAuthenticationFilter.ISSUER, DemoAuthenticationFilter.SUBJECT, null, "Demo User"
                );
                organizations.grantLegacyOwner(demo);
            };
        }
    }
}
