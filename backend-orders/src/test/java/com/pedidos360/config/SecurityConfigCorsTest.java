package com.pedidos360.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void shouldAllowOnlyExplicitFrontendOrigins() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins",
                List.of("http://localhost:5173", "https://pedidos360.app"));

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration cors = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173", "https://pedidos360.app");
        assertThat(cors.getAllowedOrigins()).doesNotContain("*");
    }
}
