package com.pedidos360.users.controller;

import com.pedidos360.users.model.UserEntity;
import com.pedidos360.users.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "users-service",
                "message", "Servicio de usuarios disponible públicamente"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "users-service",
                "timestamp", Instant.now().toString(),
                "publicEndpoint", "/api/users/public"));
    }

    // READ ME (Profile from token)
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("subject", jwt.getSubject());
        profile.put("name", jwt.getClaimAsString("name"));
        profile.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("issuer", jwt.getIssuer().toString());
        profile.put("issuedAt", jwt.getIssuedAt());
        profile.put("expiresAt", jwt.getExpiresAt());
        return ResponseEntity.ok(profile);
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = new ArrayList<>(
                userRepository.findAll().stream().map(this::toResponse).toList());
        list.sort(Comparator.comparingLong(user -> ((Number) user.get("id")).longValue()));
        return ResponseEntity.ok(list);
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        UserEntity user = userRepository.findById(Math.toIntExact(id)).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(toResponse(user));
    }

    // CREATE
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        String email = body.get("email") == null ? "" : body.get("email").toString();
        if (email.isBlank() || body.get("name") == null || body.get("name").toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre y correo son obligatorios"));
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El correo ya existe"));
        }
        UserEntity user = new UserEntity();
        user.setName(body.get("name").toString());
        user.setEmail(email);
        user.setRole(body.get("role") == null ? "CLIENTE" : body.get("role").toString());
        user.setStatus(body.get("status") == null ? "ACTIVO" : body.get("status").toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(userRepository.save(user)));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        UserEntity user = userRepository.findById(Math.toIntExact(id)).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        if (body.containsKey("name"))
            user.setName((String) body.get("name"));
        if (body.containsKey("email")) {
            String email = (String) body.get("email");
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "El correo ya existe"));
            }
            user.setEmail(email);
        }
        if (body.containsKey("role"))
            user.setRole((String) body.get("role"));
        if (body.containsKey("status"))
            user.setStatus((String) body.get("status"));
        return ResponseEntity.ok(toResponse(userRepository.save(user)));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        UserEntity user = userRepository.findById(Math.toIntExact(id)).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "Usuario eliminado con éxito", "id", id));
    }

    private Map<String, Object> toResponse(UserEntity user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("status", user.getStatus());
        return response;
    }
}
