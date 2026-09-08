package com.pedidos360.users.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final Map<Long, Map<String, Object>> userStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public UserController() {
        seedUser("Diego Carrillo", "diegocarrillo.cw@gmail.com", "ADMIN", "ACTIVO");
        seedUser("Profesor Evaluador", "evaluador@duocuc.cl", "ADMIN", "ACTIVO");
        seedUser("Camila González", "c.gonzalez@pedidos360.com", "VENDEDOR", "ACTIVO");
        seedUser("Andrés Silva", "a.silva@cliente.cl", "CLIENTE", "ACTIVO");
        seedUser("Valentina Morales", "v.morales@cliente.cl", "CLIENTE", "INACTIVO");
    }

    private void seedUser(String name, String email, String role, String status) {
        long id = idGenerator.incrementAndGet();
        Map<String, Object> u = new HashMap<>();
        u.put("id", id);
        u.put("name", name);
        u.put("email", email);
        u.put("role", role);
        u.put("status", status);
        userStore.put(id, u);
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "users-service",
                "message", "Servicio de usuarios disponible públicamente"));
    }

    // READ ME (Profile from token)
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

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = new ArrayList<>(userStore.values());
        list.sort(Comparator.comparingLong(u -> (Long) u.get("id")));
        return ResponseEntity.ok(list);
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Map<String, Object> user = userStore.get(id);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(user);
    }

    // CREATE
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        long newId = idGenerator.incrementAndGet();
        Map<String, Object> newUser = new HashMap<>(body);
        newUser.put("id", newId);
        if (!newUser.containsKey("status") || newUser.get("status") == null) {
            newUser.put("status", "ACTIVO");
        }
        if (!newUser.containsKey("role") || newUser.get("role") == null) {
            newUser.put("role", "CLIENTE");
        }
        userStore.put(newId, newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!userStore.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        Map<String, Object> user = userStore.get(id);
        if (body.containsKey("name")) user.put("name", body.get("name"));
        if (body.containsKey("email")) user.put("email", body.get("email"));
        if (body.containsKey("role")) user.put("role", body.get("role"));
        if (body.containsKey("status")) user.put("status", body.get("status"));

        userStore.put(id, user);
        return ResponseEntity.ok(user);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Map<String, Object> removed = userStore.remove(id);
        if (removed == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(Map.of("message", "Usuario eliminado con éxito", "id", id));
    }
}
