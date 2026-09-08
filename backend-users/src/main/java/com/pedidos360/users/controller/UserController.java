package com.pedidos360.users.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "users-service",
                "message", "Servicio de usuarios disponible públicamente"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("subject", jwt.getSubject());
        profile.put("name", jwt.getClaimAsString("name"));
        profile.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("issuer", jwt.getIssuer().toString());
        profile.put("issuedAt", jwt.getIssuedAt());
        profile.put("expiresAt", jwt.getExpiresAt());

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        String requester = jwt.getClaimAsString("name");
        if (requester == null || requester.isBlank()) {
            requester = jwt.getSubject();
        }

        Map<String, Object> user = Map.of(
                "id", id,
                "name", "Usuario Demo #" + id,
                "email", "usuario" + id + "@pedidos360.com",
                "role", "CLIENTE",
                "requestedBy", requester
        );
        return ResponseEntity.ok(user);
    }
}
