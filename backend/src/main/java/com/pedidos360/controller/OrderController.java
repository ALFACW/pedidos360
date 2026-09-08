package com.pedidos360.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Map<Long, Map<String, Object>> orderStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1000);

    public OrderController() {
        // Pedidos iniciales de demostración
        seedOrder("Diego Carrillo", List.of(
                Map.of("name", "Laptop Gamer Asus ROG Strix", "price", 1299.99, "qty", 1)
        ), 1299.99, "ENTREGADO");

        seedOrder("Profesor Evaluador", List.of(
                Map.of("name", "Monitor LG UltraWide 34'' Curvo", "price", 449.99, "qty", 1),
                Map.of("name", "Teclado Mecánico Keychron K2", "price", 89.99, "qty", 1)
        ), 539.98, "CONFIRMADO");
    }

    private void seedOrder(String user, List<Map<String, Object>> items, double total, String status) {
        long id = idGenerator.incrementAndGet();
        Map<String, Object> o = new HashMap<>();
        o.put("id", id);
        o.put("orderNumber", "PED-2026-" + id);
        o.put("user", user);
        o.put("items", items);
        o.put("total", total);
        o.put("status", status);
        o.put("createdAt", LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        orderStore.put(id, o);
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "orders-service",
                "message", "Endpoint público para prueba de flujo"));
    }

    // LIST ORDERS
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = new ArrayList<>(orderStore.values());
        list.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id"))); // más recientes primero
        return ResponseEntity.ok(list);
    }

    // CREATE ORDER (Checkout de Carrito)
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        String userName = jwt.getClaimAsString("name");
        if (userName == null || userName.isBlank()) {
            userName = jwt.getSubject();
        }

        long newId = idGenerator.incrementAndGet();
        Map<String, Object> order = new HashMap<>(body);
        order.put("id", newId);
        order.put("orderNumber", "PED-2026-" + newId);
        order.put("user", userName);
        order.put("status", "CONFIRMADO");
        order.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        orderStore.put(newId, order);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Map<String, Object> order = orderStore.get(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        return ResponseEntity.ok(order);
    }
}
