package com.pedidos360.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.payments.model.Pago;
import com.pedidos360.payments.repository.PagoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dominio pagos: registra el pago PENDIENTE de un pedido creado y lo anula si el pedido se cancela (idempotente vía payment_inbox). */
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
    public void processOrderEvent(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String eventType = event.path("eventType").asText();
        if (!"ORDER_CREATED".equals(eventType) && !"ORDER_CANCELLED".equals(eventType)) {
            throw new IllegalArgumentException("Evento recibido no es ORDER_CREATED ni ORDER_CANCELLED");
        }

        String orderEventId = event.path("eventId").asText();
        if (orderEventId.isBlank()) {
            throw new IllegalArgumentException("El evento no incluye eventId");
        }

        JsonNode order = event.path("order");
        if (order.path("id").isMissingNode()) {
            throw new IllegalArgumentException("El evento no incluye el id del pedido");
        }

        int inserted = jdbcTemplate.update(
                "INSERT INTO payment_inbox (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                orderEventId);
        if (inserted == 0) {
            return;
        }

        if ("ORDER_CANCELLED".equals(eventType)) {
            pagoRepository.findById(order.path("id").asLong())
                    .filter(p -> "PENDIENTE".equals(p.getEstado()))
                    .ifPresent(p -> {
                        p.setEstado("CANCELADO");
                        pagoRepository.save(p);
                    });
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
