package com.pedidos360.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.event.CompraPublisher;
import com.pedidos360.model.OrderEntity;
import com.pedidos360.model.OrderOutboxEntity;
import com.pedidos360.repository.OrderOutboxRepository;
import com.pedidos360.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderStore {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrderRepository orderRepository;
    private final OrderOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CompraPublisher compraPublisher;

    public OrderStore(OrderRepository orderRepository,
            OrderOutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            CompraPublisher compraPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.compraPublisher = compraPublisher;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return orderRepository.count() == 0;
    }

    @Transactional
    public Map<String, Object> save(Map<String, Object> order) {
        return toResponse(orderRepository.save(toEntity(order)));
    }

    @Transactional
    public Map<String, Object> saveAndQueueOrderEvent(Map<String, Object> order) {
        OrderEntity savedOrder = orderRepository.saveAndFlush(toEntity(order));
        Map<String, Object> response = toResponse(savedOrder);
        Map<String, Object> eventPayload = new java.util.LinkedHashMap<>(response);
        eventPayload.put("customerEmail", savedOrder.getCustomerEmail());

        OrderOutboxEntity outboxEvent = new OrderOutboxEntity();
        outboxEvent.setEventId("order-" + savedOrder.getId());
        outboxEvent.setOrderId(savedOrder.getId());
        outboxEvent.setEventType("ORDER_CREATED");
        outboxEvent.setPayload(eventPayload);
        outboxEvent.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outboxEvent);
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderOutboxEntity> findPendingOrderEvents() {
        return outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional
    public void markOrderEventPublished(String eventId) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.setPublishedAt(LocalDateTime.now());
            outboxRepository.save(event);
        });
    }

    /**
     * Cancela un pedido no pagado (lo conserva con estado CANCELADO para el Admin) y deja en el outbox
     * el evento ORDER_CANCELLED para que ms-pagos anule su pago pendiente.
     */
    @Transactional
    public void cancel(long orderId, String ownerId, boolean admin) {
        OrderEntity order = orderRepository.findById(Math.toIntExact(orderId))
                .filter(o -> admin || ownerId.equals(o.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if ("CONFIRMADO".equals(order.getStatus()) || "CANCELADO".equals(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden cancelar pedidos sin pagar");
        }
        order.setStatus("CANCELADO");
        orderRepository.save(order);

        OrderOutboxEntity outboxEvent = new OrderOutboxEntity();
        outboxEvent.setEventId("order-" + orderId + "-cancelled");
        outboxEvent.setOrderId(order.getId());
        outboxEvent.setEventType("ORDER_CANCELLED");
        outboxEvent.setPayload(Map.of("id", order.getId(), "orderNumber", order.getOrderNumber()));
        outboxEvent.setCreatedAt(LocalDateTime.now());
        outboxRepository.save(outboxEvent);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAll() {
        return orderRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findByOwner(String ownerId) {
        return orderRepository.findByOwnerId(ownerId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isOwnedBy(long orderId, String ownerId) {
        return orderRepository.findById(Math.toIntExact(orderId))
                .map(order -> ownerId != null && ownerId.equals(order.getOwnerId())).orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findById(long orderId) {
        return orderRepository.findById(Math.toIntExact(orderId)).map(this::toResponse).orElse(null);
    }

    @Transactional
    public boolean updatePayment(long orderId, String paymentStatus) {
        OrderEntity order = orderRepository.findById(Math.toIntExact(orderId)).orElse(null);
        if (order == null) {
            return false;
        }
        boolean firstApproval = "APPROVED".equals(paymentStatus) && !"APPROVED".equals(order.getPaymentStatus());
        order.setPaymentStatus(paymentStatus);
        order.setStatus("APPROVED".equals(paymentStatus) ? "CONFIRMADO" : "PAGO_RECHAZADO");
        orderRepository.save(order);
        if (firstApproval) {
            decrementStock(order.getItems());
            publicarCompras(order);
        }
        return true;
    }

    private void publicarCompras(OrderEntity order) {
        for (Map<String, Object> item : order.getItems()) {
            if (item.get("id") instanceof Number id) {
                String categoria = jdbcTemplate.query("SELECT category FROM products WHERE id = ?",
                        rs -> rs.next() ? rs.getString(1) : "Sin categoría", id.intValue());
                compraPublisher.publicar(order.getCustomerEmail(), id.longValue(), String.valueOf(item.get("name")), categoria);
            }
        }
    }

    // Descuenta el stock una sola vez, en la misma transaccion que confirma el pedido
    private void decrementStock(List<Map<String, Object>> items) {
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            Object id = item.get("id");
            Object qty = item.get("qty");
            if (id instanceof Number && qty instanceof Number) {
                jdbcTemplate.update("UPDATE products SET stock = GREATEST(stock - ?, 0) WHERE id = ?",
                        ((Number) qty).intValue(), ((Number) id).intValue());
            }
        }
    }

    private OrderEntity toEntity(Map<String, Object> order) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderNumber(valueOr(order.get("orderNumber"), newOrderNumber()));
        entity.setUsername(valueOr(order.get("user"), "unknown"));
        entity.setCustomerEmail((String) order.get("customerEmail"));
        entity.setOwnerId((String) order.get("ownerId"));
        entity.setItems(order.get("items") == null ? List.of()
                : objectMapper.convertValue(
                        order.get("items"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
        entity.setTotal(new BigDecimal(valueOr(order.get("total"), "0").toString()));
        entity.setPaymentMethod(valueOr(order.get("paymentMethod"), "Tarjeta de Crédito / Débito"));
        entity.setStatus(valueOr(order.get("status"), "PENDIENTE_PAGO"));
        entity.setPaymentStatus((String) order.get("paymentStatus"));
        entity.setCreatedAt(parseDate(order.get("createdAt")));
        return entity;
    }

    private Map<String, Object> toResponse(OrderEntity order) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("user", order.getUsername());
        response.put("customerEmail", order.getCustomerEmail());
        response.put("ownerId", order.getOwnerId());
        response.put("items", order.getItems());
        response.put("total", order.getTotal());
        response.put("paymentMethod", order.getPaymentMethod());
        response.put("status", order.getStatus());
        response.put("paymentStatus", order.getPaymentStatus());
        response.put("createdAt", order.getCreatedAt().format(DISPLAY_DATE));
        return response;
    }

    private LocalDateTime parseDate(Object value) {
        if (value instanceof LocalDateTime date) {
            return date;
        }
        if (value == null) {
            return LocalDateTime.now();
        }
        String date = value.toString();
        try {
            return LocalDateTime.parse(date, DISPLAY_DATE);
        } catch (RuntimeException ignored) {
            return LocalDateTime.parse(date);
        }
    }

    private String newOrderNumber() {
        return "PED-" + LocalDateTime.now().getYear() + "-" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String valueOr(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}