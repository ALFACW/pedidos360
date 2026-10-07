package com.pedidos360.cart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCart() {
        List<Map<String, Object>> sample = List.of(
                Map.of("productId", 1, "name", "Camiseta", "qty", 2),
                Map.of("productId", 2, "name", "Pantalon", "qty", 1));
        return ResponseEntity.ok(sample);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "cart-service",
                "timestamp", java.time.LocalDateTime.now().toString()));
    }
}
