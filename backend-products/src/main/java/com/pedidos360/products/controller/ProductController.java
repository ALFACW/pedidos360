package com.pedidos360.products.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final Map<Long, Map<String, Object>> productStore = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(100);

    public ProductController() {
        // Catálogo inicial de productos para la tienda
        seedProduct("Laptop Gamer Asus ROG Strix", 1299.99, 15, "Computación", "💻");
        seedProduct("Monitor LG UltraWide 34'' Curvo", 449.99, 8, "Monitores", "🖥️");
        seedProduct("Teclado Mecánico Keychron K2", 89.99, 25, "Accesorios", "⌨️");
        seedProduct("Mouse Inalámbrico Logitech MX Master 3", 99.99, 30, "Accesorios", "🖱️");
        seedProduct("Auriculares HyperX Cloud II Wireless", 79.99, 20, "Audio", "🎧");
        seedProduct("Silla Gamer Ergonómica ThunderX3", 219.99, 12, "Mobiliario", "🪑");
    }

    private void seedProduct(String name, double price, int stock, String category, String icon) {
        long id = idGenerator.incrementAndGet();
        Map<String, Object> p = new HashMap<>();
        p.put("id", id);
        p.put("name", name);
        p.put("price", price);
        p.put("stock", stock);
        p.put("category", category);
        p.put("icon", icon);
        productStore.put(id, p);
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> publicEndpoint() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "products-service",
                "message", "Catálogo público de productos disponible"));
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        List<Map<String, Object>> list = new ArrayList<>(productStore.values());
        list.sort(Comparator.comparingLong(p -> (Long) p.get("id")));
        return ResponseEntity.ok(list);
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Map<String, Object> product = productStore.get(id);
        if (product == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        return ResponseEntity.ok(product);
    }

    // CREATE
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal Jwt jwt) {
        long newId = idGenerator.incrementAndGet();
        Map<String, Object> newProduct = new HashMap<>(body);
        newProduct.put("id", newId);
        if (!newProduct.containsKey("icon") || newProduct.get("icon") == null) {
            newProduct.put("icon", "📦");
        }
        productStore.put(newId, newProduct);
        return ResponseEntity.status(HttpStatus.CREATED).body(newProduct);
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!productStore.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        Map<String, Object> product = productStore.get(id);
        if (body.containsKey("name")) product.put("name", body.get("name"));
        if (body.containsKey("price")) product.put("price", body.get("price"));
        if (body.containsKey("stock")) product.put("stock", body.get("stock"));
        if (body.containsKey("category")) product.put("category", body.get("category"));
        if (body.containsKey("icon")) product.put("icon", body.get("icon"));

        productStore.put(id, product);
        return ResponseEntity.ok(product);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Map<String, Object> removed = productStore.remove(id);
        if (removed == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Producto no encontrado"));
        }
        return ResponseEntity.ok(Map.of("message", "Producto eliminado con éxito", "id", id));
    }
}
