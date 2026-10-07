package com.pedidos360.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigJwtValidationTest {

    private static final String EXPECTED_ISSUER = "https://login.microsoftonline.com/tenant-id/v2.0";
    private static final String EXPECTED_AUDIENCE = "orders-api-client-id";

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.createTokenValidator(EXPECTED_ISSUER,
            EXPECTED_AUDIENCE);

    @Test
    void acceptsTokenWithExpectedIssuerAndAudience() {
        assertThat(validator.validate(token(EXPECTED_ISSUER, EXPECTED_AUDIENCE)).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenWithUnexpectedIssuer() {
        assertThat(validator.validate(token("https://attacker.example/issuer", EXPECTED_AUDIENCE)).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsTokenWithUnexpectedAudience() {
        assertThat(validator.validate(token(EXPECTED_ISSUER, "another-api-client-id")).hasErrors()).isTrue();
    }

    private Jwt token(String issuer, String audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(issuer)
                .subject("test-user")
                .audience(List.of(audience))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}