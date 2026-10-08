package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.event.PaymentEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/** Dominio pagos: procesa un pedido creado (idempotente vía payment_inbox) y publica el resultado. */
@Service
public class PaymentProcessingService {

    private final ObjectMapper objectMapper;
    private final PaymentEventPublisher paymentEventPublisher;
    private final JdbcTemplate jdbcTemplate;

    public PaymentProcessingService(
            ObjectMapper objectMapper,
            PaymentEventPublisher paymentEventPublisher,
            JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.paymentEventPublisher = paymentEventPublisher;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void processOrderCreated(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        if (!"ORDER_CREATED".equals(event.path("eventType").asText())) {
            throw new IllegalArgumentException("Evento recibido no es ORDER_CREATED");
        }

        String orderEventId = event.path("eventId").asText();
        if (orderEventId.isBlank()) {
            throw new IllegalArgumentException("El evento no incluye eventId");
        }

        JsonNode order = event.path("order");
        if (order.path("id").isMissingNode() || order.path("total").isMissingNode()) {
            throw new IllegalArgumentException("El evento no incluye id y total del pedido");
        }

        String orderId = order.path("id").asText();
        BigDecimal amount = order.path("total").decimalValue();
        String status = amount.signum() > 0 ? "APPROVED" : "REJECTED";
        String eventId = "payment-" + orderId + "-" + status.toLowerCase(Locale.ROOT);
        Map<String, Object> payment = Map.of(
                "eventId", eventId,
                "orderId", orderId,
                "orderNumber", order.path("orderNumber").asText("unknown"),
                "customer", order.path("user").asText("customer"),
                "customerEmail", order.path("customerEmail").asText(""),
                "amount", amount,
                "status", status);

        int inserted = jdbcTemplate.update(
                "INSERT INTO payment_inbox (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                orderEventId);
        if (inserted == 0) {
            return;
        }

        paymentEventPublisher.publishResult(payment);
    }
}