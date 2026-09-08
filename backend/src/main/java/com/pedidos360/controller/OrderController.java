package com.pedidos360.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Endpoint público para prueba de flujo"));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt) {
        String userName = jwt.getClaimAsString("name");
        if (userName == null || userName.isBlank()) {
            userName = jwt.getSubject();
        }

        List<Map<String, Object>> sample = List.of(
                Map.of("id", 1, "item", "Camiseta", "qty", 2, "user", userName),
                Map.of("id", 2, "item", "Pantalon", "qty", 1, "user", userName));

        return ResponseEntity.ok(sample);
    }
}
