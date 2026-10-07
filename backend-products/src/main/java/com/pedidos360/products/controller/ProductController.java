package com.pedidos360.products.controller;

import com.pedidos360.products.model.ProductEntity;
import com.pedidos360.products.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "products-service",
                "message", "Catálogo público de productos disponible"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "products-service",
                "timestamp", Instant.now().toString(),
                "publicEndpoint", "/api/products/public"));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = new ArrayList<>(
                productRepository.findAll().stream().map(this::toResponse).toList());
        list.sort(Comparator.comparingLong(p -> ((Number) p.get("id")).longValue()));
        return ResponseEntity.ok(list);
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        ProductEntity product = productRepository.findById(Math.toIntExact(id)).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        return ResponseEntity.ok(toResponse(product));
    }

    // CREATE
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        ProductEntity product = new ProductEntity();
        product.setName((String) body.get("name"));
        product.setPrice(new BigDecimal(body.get("price").toString()));
        product.setStock(((Number) body.get("stock")).intValue());
        product.setCategory((String) body.get("category"));
        product.setIcon(body.get("icon") == null ? "📦" : body.get("icon").toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(productRepository.save(product)));
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ProductEntity product = productRepository.findById(Math.toIntExact(id)).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        if (body.containsKey("name"))
            product.setName((String) body.get("name"));
        if (body.containsKey("price"))
            product.setPrice(new BigDecimal(body.get("price").toString()));
        if (body.containsKey("stock"))
            product.setStock(((Number) body.get("stock")).intValue());
        if (body.containsKey("category"))
            product.setCategory((String) body.get("category"));
        if (body.containsKey("icon"))
            product.setIcon((String) body.get("icon"));
        return ResponseEntity.ok(toResponse(productRepository.save(product)));
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        ProductEntity product = productRepository.findById(Math.toIntExact(id)).orElse(null);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        productRepository.delete(product);
        return ResponseEntity.ok(Map.of("message", "Producto eliminado con éxito", "id", id));
    }

    private Map<String, Object> toResponse(ProductEntity product) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", product.getId());
        response.put("name", product.getName());
        response.put("price", product.getPrice());
        response.put("stock", product.getStock());
        response.put("category", product.getCategory());
        response.put("icon", product.getIcon());
        return response;
    }
}
