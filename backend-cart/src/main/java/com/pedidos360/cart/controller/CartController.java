package com.pedidos360.cart.controller;

import com.pedidos360.cart.event.InteraccionPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    public record ItemCarrito(Long productoId, String nombre, String categoria) {
    }

    private final InteraccionPublisher interaccionPublisher;

    public CartController(InteraccionPublisher interaccionPublisher) {
        this.interaccionPublisher = interaccionPublisher;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCart() {
        List<Map<String, Object>> sample = List.of(
                Map.of("productId", 1, "name", "Camiseta", "qty", 2),
                Map.of("productId", 2, "name", "Pantalon", "qty", 1));
        return ResponseEntity.ok(sample);
    }

    /** El carrito vive en el frontend; aquí solo se registra la interacción para recomendaciones. */
    @PostMapping("/items")
    public ResponseEntity<Void> agregar(@RequestBody ItemCarrito item, @AuthenticationPrincipal Jwt jwt) {
        interaccionPublisher.publicar(jwt, item.productoId(), item.nombre(), item.categoria(), "CARRITO");
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "cart-service",
                "timestamp", java.time.LocalDateTime.now().toString()));
    }
}
