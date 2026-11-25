package com.pedidos360.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.pedidos360.config.SecurityConfig;
import com.pedidos360.service.OrderStore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderStore orderStore;

    public OrderController(OrderStore orderStore) {
        this.orderStore = orderStore;
        if (orderStore.isEmpty()) {
            seedOrder("Diego Carrillo", List.of(
                    Map.of("name", "Laptop Gamer Asus ROG Strix", "price", 1299.99, "qty", 1)), 1299.99, "ENTREGADO");

            seedOrder("Profesor Evaluador", List.of(
                    Map.of("name", "Monitor LG UltraWide 34'' Curvo", "price", 449.99, "qty", 1),
                    Map.of("name", "Teclado Mecánico Keychron K2", "price", 89.99, "qty", 1)), 539.98, "CONFIRMADO");
        }
    }

    private void seedOrder(String user, List<Map<String, Object>> items, double total, String status) {
        Map<String, Object> o = new HashMap<>();
        o.put("user", user);
        o.put("items", items);
        o.put("total", total);
        o.put("status", status);
        o.put("createdAt", LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        orderStore.save(o);
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "orders-service",
                "message", "Endpoint público para prueba de flujo"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "orders-service",
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "publicEndpoint", "/api/v1/orders/public"));
    }

    // LIST ORDERS
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = SecurityConfig.isAdmin(jwt)
                ? orderStore.findAll().stream()
                        .filter(o -> !jwt.getSubject().equals(o.get("ownerId")))
                        .collect(java.util.stream.Collectors.toList())
                : orderStore.findByOwner(jwt.getSubject());
        list.sort((a, b) -> Long.compare(((Number) b.get("id")).longValue(), ((Number) a.get("id")).longValue())); // más recientes primero
        return ResponseEntity.ok(list);
    }

    // CANCELAR: solo pedidos sin pagar, del propio cliente (o cualquiera si es Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        orderStore.cancel(id, jwt.getSubject(), SecurityConfig.isAdmin(jwt));
        return ResponseEntity.noContent().build();
    }

    // CREATE ORDER (Checkout de Carrito)
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        String customerEmail = firstNonBlank(
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("upn"));
        if (customerEmail == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "El token de acceso no contiene un correo para enviar notificaciones"));
        }

        String userName = jwt.getClaimAsString("name");
        if (userName == null || userName.isBlank()) {
            userName = jwt.getSubject();
        }

        Map<String, Object> order = new HashMap<>(body);
        order.put("user", userName);
        order.put("customerEmail", customerEmail);
        order.put("ownerId", jwt.getSubject());
        order.put("status", "PENDIENTE_PAGO");
        order.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        Map<String, Object> savedOrder = orderStore.saveAndQueueOrderEvent(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedOrder);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        if (!SecurityConfig.isAdmin(jwt) && !orderStore.isOwnedBy(id, jwt.getSubject())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        Map<String, Object> order = orderStore.findById(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado"));
        }
        return ResponseEntity.ok(order);
    }
}
