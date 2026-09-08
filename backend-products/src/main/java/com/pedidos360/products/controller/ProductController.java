package com.pedidos360.products.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "products-service",
                "message", "Catálogo público de productos disponible"));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt) {
        String userName = jwt.getClaimAsString("name");
        if (userName == null || userName.isBlank()) {
            userName = jwt.getSubject();
        }

        List<Map<String, Object>> products = List.of(
                Map.of("id", 101, "name", "Laptop Gamer Pro", "price", 1299.99, "stock", 15),
                Map.of("id", 102, "name", "Mouse Ergonómico Inalámbrico", "price", 49.99, "stock", 40),
                Map.of("id", 103, "name", "Teclado Mecánico RGB", "price", 89.99, "stock", 25),
                Map.of("id", 104, "name", "Monitor 27'' 4K UHD", "price", 349.99, "stock", 10)
        );

        return ResponseEntity.ok(Map.of(
                "consultedBy", userName,
                "totalProducts", products.size(),
                "products", products
        ));
    }
}
