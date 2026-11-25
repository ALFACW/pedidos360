package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.model.Pago;
import com.pedidos360.payments.repository.PagoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dominio pagos: registra el pago PENDIENTE de un pedido creado (idempotente vía payment_inbox). */
@Service
public class PaymentProcessingService {

    private final ObjectMapper objectMapper;
    private final PagoRepository pagoRepository;
    private final JdbcTemplate jdbcTemplate;

    public PaymentProcessingService(ObjectMapper objectMapper, PagoRepository pagoRepository, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.pagoRepository = pagoRepository;
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

        int inserted = jdbcTemplate.update(
                "INSERT INTO payment_inbox (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                orderEventId);
        if (inserted == 0) {
            return;
        }

        Pago pago = new Pago();
        pago.setPedidoId(order.path("id").asLong());
        pago.setNumeroPedido(order.path("orderNumber").asText());
        pago.setCliente(order.path("user").asText());
        pago.setCorreo(order.path("customerEmail").asText(""));
        pago.setMonto(order.path("total").decimalValue());
        pago.setEstado("PENDIENTE");
        pagoRepository.save(pago);
    }
}
