package com.pedidos360.products.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.tenant-id}")
    private String tenantId;

    @Value("${app.security.jwt.audience}")
    private String audience;

    @Value("#{'${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,https://pedidos360-frontend.example.com}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public JwtDecoder jwtDecoder() {
        String issuerBase = issuerUri.endsWith("/") ? issuerUri : issuerUri + "/";
        String jwkSetUri = issuerBase + tenantId + "/discovery/v2.0/keys";
        String expectedIssuer = issuerBase + tenantId + "/v2.0";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(createTokenValidator(expectedIssuer, audience));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> createTokenValidator(String expectedIssuer, String expectedAudience) {
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token audience is not allowed", null));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(expectedIssuer), audienceValidator);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Todo usuario autenticado es Cliente; es Admin si trae el rol de Entra ID o su correo está en ADMIN_EMAILS.
    public static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains("Admin")) {
            return true;
        }
        String adminEmails = System.getenv("ADMIN_EMAILS");
        if (adminEmails == null || adminEmails.isBlank()) {
            return false;
        }
        Set<String> admins = new java.util.HashSet<>();
        for (String email : adminEmails.split(",")) {
            admins.add(email.trim().toLowerCase());
        }
        for (String claim : new String[] {"email", "preferred_username", "upn"}) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && admins.contains(value.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_Cliente"));
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
        if (isAdmin(jwt)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_Admin"));
        }
        return authorities;
    }
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractAuthorities);
        return converter;
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/products/public", "/api/v1/products/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").authenticated()
                        .anyRequest().hasRole("Admin"))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }
}
